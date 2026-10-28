package org.visualvoicemail.checks;

import android.app.*;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.*;
import android.widget.*;

import org.json.JSONObject;

import java.lang.reflect.*;
import java.util.*;

/** Per-activity configuration and bounds only; never changes device display or font settings. */
public final class LayoutAccessibilityCheck extends DeviceCheckInstrumentation {
    private float scale = 1.6f;
    private int checks, controls;
    private Activity screen;
    private String page;
    private final Rect visible = new Rect();
    private final List<String> failures = new ArrayList<>();

    @Override
    public Activity newActivity(ClassLoader loader, String name, Intent intent)
            throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        Activity activity = super.newActivity(loader, name, intent);
        Configuration config = new Configuration();
        config.fontScale = scale;
        config.screenWidthDp = 320;
        config.smallestScreenWidthDp = 320;
        activity.applyOverrideConfiguration(config);
        return activity;
    }

    @Override
    public void callActivityOnCreate(Activity a, Bundle state) {
        super.callActivityOnCreate(a, state);
        ViewGroup content = a.findViewById(android.R.id.content);
        if (content.getChildCount() > 0) {
            View root = content.getChildAt(0);
            ViewGroup.LayoutParams params = root.getLayoutParams();
            float density = a.getResources().getDisplayMetrics().density;
            params.width = (int) (320 * density);
            params.height = (int) (480 * density);
            if (params instanceof FrameLayout.LayoutParams)
                ((FrameLayout.LayoutParams) params).gravity =
                        Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            root.setLayoutParams(params);
        }
    }

    private void check(boolean ok, String detail) throws Exception {
        checks++;
        if (!ok && failures.size() < 40) failures.add(page + " " + scale + " " + detail);
    }

    private void ui(Runnable r) {
        runOnMainSync(r);
        waitForIdleSync();
    }

    private void invoke(String name) throws Exception {
        Method m = screen.getClass().getDeclaredMethod(name);
        m.setAccessible(true);
        ui(
                () -> {
                    try {
                        m.invoke(screen);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    private boolean parentTarget(View v, float minimum) {
        ViewParent p = v.getParent();
        while (p instanceof View) {
            View parent = (View) p;
            if (parent.isClickable()
                    && parent.getWidth() + 1 >= minimum
                    && parent.getHeight() + 1 >= minimum) return true;
            p = parent.getParent();
        }
        return false;
    }

    private String label(View v, String path) {
        if (v instanceof TextView) {
            String s = ((TextView) v).getText().toString();
            if (Arrays.asList(
                            "All",
                            "Unread",
                            "Saved (1)",
                            "Select all",
                            "Deselect all",
                            "Transcribe",
                            "Mark read",
                            "Delete",
                            "Record",
                            "Choose audio",
                            "Save greeting",
                            "Original",
                            "English")
                    .contains(s)) return s;
        }
        return v.getClass().getSimpleName() + " " + path;
    }

    private void inspect(View v, String path) throws Exception {
        if (!v.isShown() || !v.getGlobalVisibleRect(visible)) return;
        if (v.isClickable() && v.isEnabled()) {
            controls++;
            float min = 48 * v.getResources().getDisplayMetrics().density;
            check(
                    (v.getWidth() + 1 >= min && v.getHeight() + 1 >= min) || parentTarget(v, min),
                    "small target " + label(v, path) + " " + v.getWidth() + "x" + v.getHeight());
            check(visible.width() + 2 >= v.getWidth(), "horizontal clip " + label(v, path));
            if (!(v instanceof TextView))
                check(
                        v.getContentDescription() != null || v instanceof ViewGroup,
                        "missing accessible label " + label(v, path));
            if (v instanceof Button && !(v instanceof CompoundButton)) {
                TextView text = (TextView) v;
                if (text.getLayout() != null) {
                    for (int i = 0; i < text.getLayout().getLineCount(); i++)
                        check(
                                text.getLayout().getEllipsisCount(i) == 0,
                                "truncated button " + label(v, path));
                    check(
                            text.getLayout().getHeight()
                                    <= v.getHeight() - v.getPaddingTop() - v.getPaddingBottom() + 3,
                            "vertical text clip " + label(v, path));
                }
            }
        }
        if (v instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) v;
            for (int i = 0; i < group.getChildCount(); i++)
                inspect(group.getChildAt(i), path + "/" + i);
        }
    }

    private void scan() throws Exception {
        View root = ((ViewGroup) screen.findViewById(android.R.id.content)).getChildAt(0);
        check(
                Math.abs(screen.getResources().getConfiguration().fontScale - scale) < .01,
                "font override");
        check(screen.getResources().getConfiguration().screenWidthDp == 320, "width override");
        inspect(root, "root");
    }

    private void scrollAll(View v) {
        if (v instanceof ScrollView) ((ScrollView) v).scrollTo(0, 100000);
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) scrollAll(g.getChildAt(i));
        }
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
        android.content.SharedPreferences prefs = c.getSharedPreferences("settings", 0);
        Map<String, ?> before = prefs.getAll();
        try {
            prefs.edit()
                    .putBoolean("refresh_on_open", false)
                    .putBoolean("auto_transcribe", false)
                    .putBoolean("reduce_motion", true)
                    .putBoolean("guide_recording", false)
                    .commit();
            Class<?> carrier = Class.forName("org.visualvoicemail.app.CarrierClient");
            Object client = carrier.getMethod("get", Context.class).invoke(null, c);
            List<?> inbox = (List<?>) carrier.getMethod("cachedInbox").invoke(client);
            String id = ((JSONObject) inbox.get(0)).getString("objectid");
            for (float font : new float[] {1.6f, 2f}) {
                scale = font;
                for (String name :
                        new String[] {
                            "MainActivity",
                            "SettingsActivity",
                            "RecordingActivity",
                            "TranscriptionSettingsActivity",
                            "GreetingActivity",
                            "TutorialActivity"
                        }) {
                    page = name;
                    if (name.equals("RecordingActivity"))
                        prefs.edit().putString("transcription_quality", "enhanced").commit();
                    Intent intent =
                            new Intent()
                                    .setClassName(c, "org.visualvoicemail.app." + name)
                                    .putExtra("id", id)
                                    .putExtra("caller", "Sample caller")
                                    .putExtra("replay", true)
                                    .addFlags(
                                            Intent.FLAG_ACTIVITY_NEW_TASK
                                                    | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    screen = startActivitySync(intent);
                    Thread.sleep(550);
                    waitForIdleSync();
                    scan();
                    if (name.equals("MainActivity")) {
                        invoke("enterSelection");
                        Thread.sleep(100);
                        scan();
                        invoke("clearSelection");
                    }
                    ui(() -> scrollAll(screen.getWindow().getDecorView()));
                    Thread.sleep(100);
                    scan();
                    if (name.equals("TutorialActivity")) {
                        Field replay = screen.getClass().getDeclaredField("replay"); replay.setAccessible(true);
                        Method build = screen.getClass().getDeclaredMethod("build", boolean.class); build.setAccessible(true);
                        ui(() -> { try { replay.setBoolean(screen, false); build.invoke(screen, false); }
                            catch (Exception error) { throw new RuntimeException(error); } });
                        scan();
                        ui(() -> scrollAll(screen.getWindow().getDecorView())); scan();
                    }
                    Activity done = screen;
                    ui(() -> done.finish());
                    screen = null;
                }
            }
            check(controls > 100, "control coverage");
            out.putBoolean("passed", failures.isEmpty());
            if (!failures.isEmpty()) out.putString("failures", String.join("; ", failures));
        } catch (Throwable error) {
            while (error.getCause() != null) error = error.getCause();
            out.putString("failure_class", error.getClass().getSimpleName());
            out.putString("failure", error.getMessage());
        } finally {
            if (screen != null) ui(() -> screen.finish());
            SharedPreferences.Editor restore = prefs.edit();
            for (String key :
                    new String[] {
                        "refresh_on_open", "auto_transcribe", "reduce_motion", "guide_recording", "transcription_quality"
                    }) {
                Object value = before.get(key);
                if (value instanceof Boolean) restore.putBoolean(key, (Boolean) value);
                else if (value instanceof String) restore.putString(key, (String) value);
                else restore.remove(key);
            }
            restore.commit();
        }
        out.putInt("checks", checks);
        out.putInt("controls", controls);
        finish(-1, out);
    }
}
