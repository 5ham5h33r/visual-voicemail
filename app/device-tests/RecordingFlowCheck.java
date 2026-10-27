package org.visualvoicemail.checks;

import android.app.*;
import android.content.*;
import android.media.*;
import android.os.*;
import android.view.*;
import android.widget.*;

import org.json.*;

import java.lang.reflect.*;
import java.util.*;

public class RecordingFlowCheck extends DeviceCheckInstrumentation {
    int checks = 0;
    Activity screen;
    Context c;

    static Method method(Class<?> c, String n, Class<?>... t) throws Exception {
        Method m = c.getDeclaredMethod(n, t);
        m.setAccessible(true);
        return m;
    }

    static Object get(Object o, String n) throws Exception {
        Field f = o.getClass().getDeclaredField(n);
        f.setAccessible(true);
        return f.get(o);
    }

    void check(boolean v) throws Exception {
        checks++;
        if (!v) throw new Exception("check " + checks);
    }

    void call(String n, Class<?>[] t, Object... v) throws Exception {
        method(screen.getClass(), n, t).invoke(screen, v);
    }

    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);
        start();
    }

    @Override
    public void onStart() {
        Bundle result = new Bundle();
        try {
            c = getTargetContext();
            Class<?> carrier = Class.forName("org.visualvoicemail.app.CarrierClient");
            Object client = method(carrier, "get", Context.class).invoke(null, c);
            List<?> inbox = (List<?>) method(carrier, "cachedInbox").invoke(client);
            JSONObject message = (JSONObject) inbox.get(0);
            String id = message.getString("objectid"), flag = message.optString("flag");
            Intent intent =
                    new Intent()
                            .setClassName(c, "org.visualvoicemail.app.RecordingActivity")
                            .putExtra("id", id)
                            .putExtra("caller", "Private test caller")
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            screen = startActivitySync(intent);
            Thread.sleep(1500);
            check((Boolean) get(screen, "prepared"));
            View playButton=(View)get(screen,"play");ViewGroup controls=(ViewGroup)playButton.getParent();
            ViewGroup mediaCard=(ViewGroup)controls.getParent();
            check(controls.getPaddingTop()==controls.getPaddingBottom()&&controls.getPaddingBottom()>=8*c.getResources().getDisplayMetrics().density);
            check(mediaCard.getPaddingTop()==mediaCard.getPaddingBottom());
            check(Math.abs(playButton.getLeft()+playButton.getWidth()/2f-controls.getWidth()/2f)<=c.getResources().getDisplayMetrics().density);
            View call = (View) get(screen, "callControl");
            check(call.getVisibility() == View.VISIBLE);
            Intent dial =
                    (Intent)
                            method(screen.getClass(), "callIntent", JSONObject.class)
                                    .invoke(null, message);
            check(
                    Intent.ACTION_DIAL.equals(dial.getAction())
                            && "tel".equals(dial.getData().getScheme()));
            IntentFilter dialing = new IntentFilter(Intent.ACTION_DIAL);
            dialing.addDataScheme("tel");
            ActivityMonitor dialer = addMonitor(dialing, new ActivityResult(0, null), true);
            runOnMainSync(() -> call.performClick());
            waitForIdleSync();
            check(dialer.getHits() == 1);
            removeMonitor(dialer);
            check(
                    method(screen.getClass(), "callIntent", JSONObject.class)
                                    .invoke(null, new JSONObject().put("returnnumber", "Unknown"))
                            == null);
            check(
                    method(screen.getClass(), "callIntent", JSONObject.class)
                                    .invoke(null, new JSONObject().put("returnnumber", "*#21#"))
                            == null);
            MediaPlayer player = (MediaPlayer) get(screen, "player");
            player.setVolume(0, 0);
            check(!player.isPlaying());
            List<?> lines = (List<?>) get(screen, "lines");
            check(lines.size() > 0);
            Object transcript = get(screen, "transcript");
            JSONObject data = (JSONObject) method(transcript.getClass(), "json").invoke(transcript);
            JSONArray segments = data.getJSONArray("segments");
            int first = segments.getJSONObject(0).getInt("start");
            runOnMainSync(() -> ((View) lines.get(0)).performClick());
            Thread.sleep(350);
            check(player.isPlaying());
            check(Math.abs(player.getCurrentPosition() - first) < 1000);
            runOnMainSync(
                    () -> {
                        try {
                            call("pause", new Class<?>[0]);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
            check(!player.isPlaying());
            String phrase = segments.getJSONObject(0).getString("text");
            String needle = phrase.split("\\s+")[0];
            EditText search = (EditText) get(screen, "search");
            runOnMainSync(() -> search.setText(needle));
            Thread.sleep(200);
            check(!((List<?>) get(screen, "matches")).isEmpty());
            check(!player.isPlaying());
            int position = player.getCurrentPosition();
            runOnMainSync(
                    () -> {
                        try {
                            call("navigate", new Class<?>[] {int.class}, 1);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
            check(!player.isPlaying());
            check(Math.abs(player.getCurrentPosition() - position) < 100);
            if (lines.size() > 1) {
                int last = segments.getJSONObject(segments.length() - 1).getInt("start");
                runOnMainSync(
                        () -> {
                            ((View) lines.get(0)).performClick();
                            ((View) lines.get(lines.size() - 1)).performClick();
                        });
                Thread.sleep(400);
                check(player.isPlaying());
                check(Math.abs(player.getCurrentPosition() - last) < 1000);
            }
            Object sampleEnglish =
                    method(transcript.getClass(), "parse", JSONObject.class)
                            .invoke(
                                    null,
                                    new JSONObject(
                                            "{\"version\":2,\"quality\":\"highest\",\"language\":\"es\",\"task\":\"translate\",\"segments\":[{\"start\":100,\"end\":900,\"text\":\"translationfixtureunique\"}]}"));
            Field english = screen.getClass().getDeclaredField("english");
            english.setAccessible(true);
            english.set(screen, sampleEnglish);
            runOnMainSync(
                    () -> {
                        try {
                            call("switchOutput", new Class<?>[] {boolean.class}, true);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
            check(
                    (Boolean) get(screen, "englishVisible")
                            && ((View) get(screen, "englishTab")).isSelected());
            runOnMainSync(
                    () -> {
                        try {
                            call("showShareOptions", new Class<?>[0]);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
            PopupMenu sharing = (PopupMenu) get(screen, "shareMenu");
            check(
                    sharing.getMenu().size() == 2
                            && "Transcript".contentEquals(sharing.getMenu().getItem(0).getTitle())
                            && "Voice recording"
                                    .contentEquals(sharing.getMenu().getItem(1).getTitle()));
            check(
                    sharing.getMenu().getItem(0).isEnabled()
                            && sharing.getMenu().getItem(1).isEnabled());
            runOnMainSync(() -> sharing.dismiss());
            Intent publicShare =
                    (Intent)
                            method(
                                            Class.forName("org.visualvoicemail.app.ShareIntents"),
                                            "transcript",
                                            sampleEnglish.getClass())
                                    .invoke(null, sampleEnglish);
            ActivityMonitor sharesheet =
                    addMonitor(
                            new IntentFilter(Intent.ACTION_CHOOSER),
                            new ActivityResult(0, null),
                            true);
            runOnMainSync(
                    () -> {
                        try {
                            call("startShare", new Class<?>[] {Intent.class}, publicShare);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
            check(sharesheet.getHits() == 1);
            removeMonitor(sharesheet);
            runOnMainSync(() -> search.setText("translationfixtureunique"));
            check(!((List<?>) get(screen, "matches")).isEmpty());
            Button originalButton = (Button) get(screen, "originalTab");
            runOnMainSync(() -> originalButton.performClick());
            check(
                    !(Boolean) get(screen, "englishVisible")
                            && ((List<?>) get(screen, "matches")).isEmpty());
            runOnMainSync(() -> search.setText(""));
            check(((View) get(screen, "clearFind")).getVisibility() == View.GONE);
            runOnMainSync(
                    () ->
                            screen.startActivity(
                                    new Intent()
                                            .setClassName(
                                                    c,
                                                    "org.visualvoicemail.app.SettingsActivity")));
            Thread.sleep(1200);
            check(!player.isPlaying());
            List<?> after = (List<?>) method(carrier, "cachedInbox").invoke(client);
            check(((JSONObject) after.get(0)).optString("flag").equals(flag));
            result.putInt("checks", checks);
            result.putBoolean("passed", true);
        } catch (Throwable e) {
            result.putInt("checks", checks);
            result.putString("failure_class", e.getClass().getSimpleName());
            result.putBoolean("passed", false);
        } finally {
            finish(-1, result);
        }
    }
}
