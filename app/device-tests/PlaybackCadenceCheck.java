package org.visualvoicemail.checks;

import android.app.*;
import android.content.*;
import android.media.MediaPlayer;
import android.os.*;
import android.view.*;
import android.widget.*;

import org.json.JSONObject;

import java.io.File;
import java.lang.reflect.*;
import java.util.*;

/** Real sample audio: progress follows display frames and stops on pause, drag, and background. */
public final class PlaybackCadenceCheck extends DeviceCheckInstrumentation {
    Activity screen;
    int checks;

    Object field(String name) throws Exception {
        Field f = screen.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(screen);
    }

    void check(boolean value) throws Exception {
        checks++;
        if (!value) throw new Exception("check " + checks);
    }

    void call(String name) throws Exception {
        Method m = screen.getClass().getDeclaredMethod(name);
        m.setAccessible(true);
        runOnMainSync(
                () -> {
                    try {
                        m.invoke(screen);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    void dragging(boolean value) throws Exception {
        Field f = screen.getClass().getDeclaredField("seeking");
        f.setAccessible(true);
        runOnMainSync(
                () -> {
                    try {
                        f.setBoolean(screen, value);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    @Override
    public void onCreate(Bundle args) {
        super.onCreate(args);
        start();
    }

    @Override
    public void onStart() {
        Bundle out = new Bundle();
        Context c = getTargetContext();
        SharedPreferences prefs = c.getSharedPreferences("settings", 0);
        Map<String, ?> before = prefs.getAll();
        try {
            check(Arrays.asList("ranchu", "goldfish").contains(Build.HARDWARE));
            check(!new File(c.getFilesDir(), "session.bin").exists());
            Class<?> carrier = Class.forName("org.visualvoicemail.app.CarrierClient");
            Object client = carrier.getMethod("get", Context.class).invoke(null, c);
            List<?> inbox = (List<?>) carrier.getMethod("cachedInbox").invoke(client);
            check(!inbox.isEmpty());
            for (Object item : inbox)
                check(((JSONObject) item).optString("objectid").startsWith("emulator-sample-"));
            prefs.edit()
                    .putBoolean("auto_transcribe", false)
                    .putBoolean("read_on_play", false)
                    .putBoolean("guide_recording", false)
                    .putInt("playback_speed", 1)
                    .commit();
            screen =
                    startActivitySync(
                            new Intent()
                                    .setClassName(c, "org.visualvoicemail.app.RecordingActivity")
                                    .putExtra(
                                            "id", ((JSONObject) inbox.get(0)).getString("objectid"))
                                    .putExtra("caller", "Sample caller")
                                    .putExtra("english", false)
                                    .addFlags(
                                            Intent.FLAG_ACTIVITY_NEW_TASK
                                                    | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            long end = SystemClock.uptimeMillis() + 7000;
            while ((!screen.hasWindowFocus() || !(Boolean) field("prepared"))
                    && SystemClock.uptimeMillis() < end) Thread.sleep(40);
            check(screen.hasWindowFocus() && (Boolean) field("prepared"));
            MediaPlayer player = (MediaPlayer) field("player");
            player.setVolume(0, 0);
            SeekBar seek = (SeekBar) field("seek");
            List<Integer> values = new ArrayList<>();
            ViewTreeObserver.OnPreDrawListener listener =
                    () -> {
                        values.add(seek.getProgress());
                        return true;
                    };
            runOnMainSync(() -> seek.getViewTreeObserver().addOnPreDrawListener(listener));
            call("toggle");
            Thread.sleep(700);
            runOnMainSync(() -> seek.getViewTreeObserver().removeOnPreDrawListener(listener));
            check(player.isPlaying());
            check(values.size() >= 12);
            check(new HashSet<Integer>(values).size() >= 9);
            check(Math.abs(player.getCurrentPosition() - seek.getProgress()) < 160);
            dragging(true);
            int held = seek.getProgress();
            Thread.sleep(180);
            check(seek.getProgress() == held);
            dragging(false);
            Thread.sleep(100);
            check(seek.getProgress() > held);
            call("pause");
            check(!(Boolean) field("playbackFramePosted"));
            held = seek.getProgress();
            Thread.sleep(180);
            check(seek.getProgress() == held);
            call("toggle");
            Thread.sleep(120);
            check((Boolean) field("playbackFramePosted"));
            Activity detail = screen;
            runOnMainSync(
                    () ->
                            detail.startActivity(
                                    new Intent()
                                            .setClassName(
                                                    c,
                                                    "org.visualvoicemail.app.SettingsActivity")));
            Thread.sleep(400);
            check(!player.isPlaying() && !(Boolean) field("playbackFramePosted"));
            out.putInt("observed_frames", values.size());
            out.putInt("distinct_progress_values", new HashSet<Integer>(values).size());
            out.putBoolean("passed", true);
        } catch (Throwable e) {
            out.putString("failure_class", e.getClass().getSimpleName());
            if (e.getMessage() != null && e.getMessage().startsWith("check "))
                out.putString("failure", e.getMessage());
        } finally {
            if (screen != null) runOnMainSync(() -> screen.finish());
            SharedPreferences.Editor edit = prefs.edit();
            for (String key :
                    new String[] {
                        "auto_transcribe", "read_on_play", "guide_recording", "playback_speed"
                    }) {
                Object v = before.get(key);
                if (v instanceof Boolean) edit.putBoolean(key, (Boolean) v);
                else if (v instanceof Integer) edit.putInt(key, (Integer) v);
                else edit.remove(key);
            }
            edit.commit();
        }
        out.putInt("checks", checks);
        finish(-1, out);
    }
}
