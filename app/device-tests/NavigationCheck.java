package org.visualvoicemail.checks;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;

import org.json.*;

import java.lang.reflect.*;
import java.util.*;

/** Back returns within the app in normal flows and reconstructs parents for isolated pages. */
public final class NavigationCheck extends DeviceCheckInstrumentation {
    private int checks;
    private Context context;
    private Activity screen;

    private void check(boolean value) throws Exception {
        checks++;
        if (!value) throw new Exception("check " + checks);
    }

    private Object field(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }

    private View find(View root, String description) {
        if (description.contentEquals(
                root.getContentDescription() == null ? "" : root.getContentDescription()))
            return root;
        if (root instanceof ViewGroup)
            for (int i = 0; i < ((ViewGroup) root).getChildCount(); i++) {
                View found = find(((ViewGroup) root).getChildAt(i), description);
                if (found != null) return found;
            }
        return null;
    }

    private void click(View view) throws Exception {
        check(view != null);
        runOnMainSync(() -> view.performClick());
        Thread.sleep(450);
    }

    private ActivityMonitor monitor(String name) {
        return addMonitor("org.visualvoicemail.app." + name, null, false);
    }

    private Activity await(ActivityMonitor monitor) throws Exception {
        Activity a = waitForMonitorWithTimeout(monitor, 5000);
        check(a != null);
        Thread.sleep(450);
        return a;
    }

    private Activity root(String name, String id) {
        Intent i =
                new Intent()
                        .setClassName(context, "org.visualvoicemail.app." + name)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        if (id != null) i.putExtra("id", id).putExtra("caller", "Navigation check");
        Activity a = startActivitySync(i);
        return a;
    }

    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);
        start();
    }

    @Override
    public void onStart() {
        Bundle out = new Bundle();
        context = getTargetContext();
        try {
            Class<?> carrier = Class.forName("org.visualvoicemail.app.CarrierClient");
            Method get = carrier.getDeclaredMethod("get", Context.class);
            get.setAccessible(true);
            Object client = get.invoke(null, context);
            Method cached = carrier.getDeclaredMethod("cachedInbox");
            cached.setAccessible(true);
            List<?> inbox = (List<?>) cached.invoke(client);
            String id = ((JSONObject) inbox.get(0)).getString("objectid");
            Activity main = root("MainActivity", null);
            Thread.sleep(600);
            ActivityMonitor settings = monitor("SettingsActivity");
            click((View) field(main, "settingsControl"));
            screen = await(settings);
            check(!screen.isTaskRoot());
            click(find(screen.getWindow().getDecorView(), "Back to voicemail"));
            check(main.hasWindowFocus());
            ActivityMonitor recording = monitor("RecordingActivity");
            runOnMainSync(
                    () ->
                            main.startActivity(
                                    new Intent()
                                            .setClassName(
                                                    context,
                                                    "org.visualvoicemail.app.RecordingActivity")
                                            .putExtra("id", id)
                                            .putExtra("caller", "Navigation check")));
            screen = await(recording);
            Activity detail = screen;
            ActivityMonitor quality = monitor("TranscriptionSettingsActivity");
            click((View) field(screen, "qualityControl"));
            screen = await(quality);
            click(find(screen.getWindow().getDecorView(), "Back to Settings"));
            check(detail.hasWindowFocus());
            click(find(detail.getWindow().getDecorView(), "Back to voicemail"));
            check(main.hasWindowFocus());
            for (String name : new String[] {"SettingsActivity", "RecordingActivity"}) {
                screen = root(name, name.equals("RecordingActivity") ? id : null);
                Thread.sleep(500);
                check(screen.isTaskRoot());
                ActivityMonitor home = monitor("MainActivity");
                click(find(screen.getWindow().getDecorView(), "Back to voicemail"));
                Activity restored = await(home);
                check(restored.hasWindowFocus());
            }
            for (boolean systemBack : new boolean[] {false, true}) {
                screen = root("TranscriptionSettingsActivity", null);
                Thread.sleep(500);
                check(screen.isTaskRoot());
                ActivityMonitor parent = monitor("SettingsActivity"),
                        home = monitor("MainActivity");
                if (systemBack) sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
                else click(find(screen.getWindow().getDecorView(), "Back to Settings"));
                screen = await(parent);
                check(!screen.isTaskRoot());
                click(find(screen.getWindow().getDecorView(), "Back to voicemail"));
                Activity restoredHome = await(home);
                check(restoredHome.hasWindowFocus());
            }
            out.putBoolean("passed", true);
        } catch (Throwable e) {
            out.putBoolean("passed", false);
            out.putString("failure_class", e.getClass().getSimpleName());
        }
        out.putInt("checks", checks);
        finish(-1, out);
    }
}
