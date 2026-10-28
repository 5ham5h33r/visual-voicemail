package org.visualvoicemail.checks;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import android.widget.*;

import org.json.JSONObject;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

/** Public emulator only: grouped controls and visually reviewed native theme captures. */
public final class TranscriptionControlsCheck extends DeviceCheckInstrumentation {
    int checks;
    Activity screen;

    Object field(Object o, String name) throws Exception {
        Field f = o.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(o);
    }

    void check(boolean good) throws Exception {
        checks++;
        if (!good) throw new Exception("check " + checks);
    }

    void ui(Runnable action) {
        runOnMainSync(action);
        waitForIdleSync();
    }

    void capture(String name) throws Exception {
        View decor = screen.getWindow().getDecorView();
        long end = SystemClock.uptimeMillis() + 5000;
        while ((!screen.hasWindowFocus() || decor.getWidth() == 0)
                && SystemClock.uptimeMillis() < end) Thread.sleep(50);
        check(screen.hasWindowFocus());
        Thread.sleep(400);
        Bitmap image =
                Bitmap.createBitmap(decor.getWidth(), decor.getHeight(), Bitmap.Config.ARGB_8888);
        CountDownLatch done = new CountDownLatch(1);
        int[] status = {-1};
        ui(
                () ->
                        PixelCopy.request(
                                screen.getWindow(),
                                image,
                                value -> {
                                    status[0] = value;
                                    done.countDown();
                                },
                                new Handler(Looper.getMainLooper())));
        check(done.await(5, TimeUnit.SECONDS) && status[0] == PixelCopy.SUCCESS);
        try (OutputStream output =
                new FileOutputStream(
                        new File(getTargetContext().getExternalFilesDir(null), name + ".png"))) {
            image.compress(Bitmap.CompressFormat.PNG, 100, output);
        } finally {
            image.recycle();
        }
    }

    Activity launch(Context c, String name, String id) {
        return startActivitySync(
                new Intent()
                        .setClassName(c, "org.visualvoicemail.app." + name)
                        .putExtra("id", id)
                        .putExtra("caller", "Sample caller")
                        .putExtra("english", false)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
    }

    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);
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
            String id = ((JSONObject) inbox.get(0)).getString("objectid");
            prefs.edit()
                    .putBoolean("auto_transcribe", false)
                    .putBoolean("refresh_on_open", false)
                    .putBoolean("reduce_motion", true)
                    .putBoolean("guide_recording", false)
                    .commit();
            for (int mode = 1; mode <= 3; mode++) {
                String theme = mode == 1 ? "light" : mode == 2 ? "dark" : "amoled";
                prefs.edit()
                        .putInt("appearance", mode)
                        .putString("transcription_quality", "highest")
                        .putString("speech_language", "auto")
                        .putBoolean("speech_english", true)
                        .commit();
                screen = launch(c, "TranscriptionSettingsActivity", id);
                Button language = (Button) field(screen, "language"),
                        output = (Button) field(screen, "output");
                check(language.getParent() == output.getParent());
                check(language.getText().toString().startsWith("Recording language\n"));
                check(output.getText().toString().startsWith("Transcript output\n"));
                check(
                        language.getCompoundDrawablesRelative()[2] != null
                                && output.getCompoundDrawablesRelative()[2] != null);
                RadioButton[] markers = (RadioButton[]) field(screen, "indicators");
                check(markers[2].isChecked() && !markers[0].isChecked() && !markers[1].isChecked());
                check(language.isClickable() && output.isClickable());
                capture("controls-quality-" + theme);
                Activity done = screen;
                ui(done::finish);
                screen = launch(c, "RecordingActivity", id);
                ViewGroup card = (ViewGroup) field(screen, "transcriptCard");
                check(card.getBackground() != null);
                check(
                        ((View) field(screen, "transcriptBody")).getParent() == card
                                && ((View) field(screen, "transcriptStatus")).getParent() == card
                                && ((View) field(screen, "findBox")).getParent() == card);
                Button update = (Button) field(screen, "speechAction");
                check(
                        update.isClickable()
                                && update.getText().toString().equals("Update transcript"));
                check(((View) field(screen, "transcriptTools")).getVisibility() == View.VISIBLE);
                capture("controls-recording-" + theme);
                done = screen;
                ui(done::finish);
                screen = null;
            }
            out.putBoolean("passed", true);
        } catch (Throwable error) {
            while (error.getCause() != null) error = error.getCause();
            out.putString("failure_class", error.getClass().getSimpleName());
            out.putString(
                    "failure",
                    error.getMessage() != null && error.getMessage().startsWith("check ")
                            ? error.getMessage()
                            : "controls check failed");
        } finally {
            if (screen != null) ui(screen::finish);
            SharedPreferences.Editor restore = prefs.edit();
            for (String key :
                    new String[] {
                        "appearance",
                        "transcription_quality",
                        "speech_language",
                        "speech_english",
                        "auto_transcribe",
                        "refresh_on_open",
                        "reduce_motion",
                        "guide_recording"
                    }) {
                Object v = before.get(key);
                if (v instanceof Boolean) restore.putBoolean(key, (Boolean) v);
                else if (v instanceof String) restore.putString(key, (String) v);
                else if (v instanceof Integer) restore.putInt(key, (Integer) v);
                else restore.remove(key);
            }
            restore.commit();
        }
        out.putInt("checks", checks);
        finish(-1, out);
    }
}
