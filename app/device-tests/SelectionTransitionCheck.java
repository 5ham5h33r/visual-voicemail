package org.visualvoicemail.checks;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;

import org.json.*;

import java.lang.reflect.*;
import java.util.*;

/**
 * Sample actual rendered layout frames for a physical long press; fixtures never reach the carrier.
 */
public final class SelectionTransitionCheck extends DeviceCheckInstrumentation {
    private Activity screen;
    private int checks;
    private final List<JSONObject> fixtures = new ArrayList<>();

    private static Object field(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }

    private static Method method(Class<?> type, String name, Class<?>... args) throws Exception {
        Method m = type.getDeclaredMethod(name, args);
        m.setAccessible(true);
        return m;
    }

    private void ui(Runnable r) {
        runOnMainSync(r);
        waitForIdleSync();
    }

    private void check(boolean v) throws Exception {
        checks++;
        if (!v) throw new Exception("check " + checks);
    }

    private void call(String name) throws Exception {
        Method m = method(screen.getClass(), name);
        runOnMainSync(
                () -> {
                    try {
                        m.invoke(screen);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    private ScrollView scroll() throws Exception {
        return (ScrollView) field(screen, "inboxScroll");
    }

    private void jump(int y) throws Exception {
        ScrollView s = scroll();
        ui(
                () -> {
                    try {
                        method(s.getClass(), "jumpTo", int.class).invoke(s, y);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
        Thread.sleep(80);
    }

    private boolean running() throws Exception {
        return field(scroll(), "modeAnimator") != null;
    }

    private void tap(View view) {
        int[] at = new int[2];
        view.getLocationOnScreen(at);
        float x = at[0] + view.getWidth() / 2f, y = at[1] + view.getHeight() / 2f;
        long down = SystemClock.uptimeMillis();
        sendPointerSync(MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN, x, y, 0));
        sendPointerSync(
                MotionEvent.obtain(
                        down, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y, 0));
    }

    @Override
    public void callActivityOnResume(Activity a) {
        super.callActivityOnResume(a);
        if (!fixtures.isEmpty() && a.getClass().getName().endsWith(".MainActivity"))
            try {
                Field f = a.getClass().getDeclaredField("inbox");
                f.setAccessible(true);
                f.set(a, fixtures);
                method(a.getClass(), "render").invoke(a);
                ((TextView) field(a, "status")).setText("Sample inbox");
            } catch (Exception e) {
                throw new RuntimeException(e);
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
        SharedPreferences prefs = c.getSharedPreferences("settings", 0);
        Map<String, ?> before = prefs.getAll();
        try {
            prefs.edit()
                    .putBoolean("refresh_on_open", false)
                    .putBoolean("reduce_motion", false)
                    .putBoolean("auto_transcribe", false)
                    .putBoolean("contact_names",false)
                    .commit();
            for (int i = 0; i < 24; i++)
                fixtures.add(
                        new JSONObject()
                                .put("objectid", "transition-sample-" + i)
                                .put("returnnumber", "Sample caller")
                                .put("date", "2026-01-01T12:00:00Z")
                                .put("duration", 18));
            screen =
                    startActivitySync(
                            new Intent()
                                    .setClassName(c, "org.visualvoicemail.app.MainActivity")
                                    .addFlags(
                                            Intent.FLAG_ACTIVITY_NEW_TASK
                                                    | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            Thread.sleep(800);
            for(int i=0;i<50&&!screen.hasWindowFocus();i++)Thread.sleep(100);
            if(!screen.hasWindowFocus())throw new IllegalStateException("The test app needs foreground focus for physical gesture checks");
            jump(0);
            View header = (View) field(screen, "inboxHeader"),
                    selected = (View) field(screen, "selectionBar");
            LinearLayout messages = (LinearLayout) field(screen, "messages");
            View card = messages.getChildAt(0);
            int[] oldAt = new int[2];
            card.getLocationOnScreen(oldAt);
            int oldHeight = header.getHeight();
            List<int[]> frames = new ArrayList<>();
            ViewTreeObserver.OnPreDrawListener listener =
                    () -> {
                        try {
                            if ((Boolean) field(screen, "selecting")) {
                                int[] at = new int[2];
                                card.getLocationOnScreen(at);
                                frames.add(
                                        new int[] {
                                            at[1],
                                            header.getHeight(),
                                            Math.round(header.getAlpha() * 1000),
                                            Math.round(selected.getAlpha() * 1000)
                                        });
                            }
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                        return true;
                    };
            ui(
                    () ->
                            screen.getWindow()
                                    .getDecorView()
                                    .getViewTreeObserver()
                                    .addOnPreDrawListener(listener));
            int[] loc = new int[2];
            card.getLocationOnScreen(loc);
            float x = loc[0] + card.getWidth() * .4f, y = loc[1] + card.getHeight() * .5f;
            long down = SystemClock.uptimeMillis();
            sendPointerSync(MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN, x, y, 0));
            Thread.sleep(ViewConfiguration.getLongPressTimeout() + 400);
            sendPointerSync(
                    MotionEvent.obtain(
                            down, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y, 0));
            Thread.sleep(80);
            ui(
                    () ->
                            screen.getWindow()
                                    .getDecorView()
                                    .getViewTreeObserver()
                                    .removeOnPreDrawListener(listener));
            out.putBoolean("gesture_window_focus",screen.hasWindowFocus());
            out.putBoolean("gesture_card_attached",card.isAttachedToWindow());
            out.putBoolean("gesture_current_messages",messages==field(screen,"messages"));
            check((Boolean) field(screen, "selecting"));
            check(frames.size() >= 4);
            check(frames.get(0)[1] > oldHeight * .7f && frames.get(0)[2] > 700);
            boolean partial = false;
            int maxStep = 0, prev = oldAt[1];
            for (int[] f : frames) {
                if (f[1] > 0 && f[1] < oldHeight && f[2] > 0 && f[2] < 1000) partial = true;
                maxStep = Math.max(maxStep, Math.abs(f[0] - prev));
                prev = f[0];
            }
            check(partial);
            int[] finalAt = new int[2];
            card.getLocationOnScreen(finalAt);
            int travel = Math.abs(oldAt[1] - finalAt[1]);
            check(travel > 50 && maxStep < travel * .5f);
            check(header.getVisibility() == View.GONE && selected.getAlpha() == 1 && !running());
            check(((Set<?>) field(screen, "selectedIds")).size() == 1);
            // Reversing before completion must start from the current height/opacity, without a
            // jump.
            tap(((android.view.ViewGroup) selected).getChildAt(0));
            Thread.sleep(70);
            check(!(Boolean) field(screen, "selecting"));
            int partialHeight = header.getHeight();
            out.putInt("reverse_mid_header_px", partialHeight);
            out.putInt("full_header_px", oldHeight);
            out.putFloat("reverse_progress", (Float) field(scroll(), "selectionProgress"));
            check(partialHeight > 0 && partialHeight < oldHeight);
            Method apply = method(screen.getClass(), "applySelection", boolean.class);
            Field mode = screen.getClass().getDeclaredField("selecting");
            mode.setAccessible(true);
            runOnMainSync(
                    () -> {
                        try {
                            mode.setBoolean(screen, true);
                            apply.invoke(screen, true);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
            check(Math.abs(header.getHeight() - partialHeight) < Math.max(4, oldHeight * .2f));
            Thread.sleep(300);
            check(header.getVisibility() == View.GONE && !running());
            // A scrolled inbox needs only the toolbar change, with its list-relative position kept.
            call("clearSelection");
            Thread.sleep(300);
            jump(oldHeight + 300);
            int deep = scroll().getScrollY();
            call("enterSelection");
            Thread.sleep(300);
            check(Math.abs(scroll().getScrollY() - (deep - oldHeight)) < 5);
            call("clearSelection");
            Thread.sleep(300);
            check(Math.abs(scroll().getScrollY() - deep) < 5);
            // Reduced motion settles all of the geometry in one layout.
            prefs.edit().putBoolean("reduce_motion", true).commit();
            call("enterSelection");
            check(header.getVisibility() == View.GONE && !running());
            call("clearSelection");
            check(header.getVisibility() == View.VISIBLE && !running());
            // Snapshotting during a transition saves the final coordinate system, not a partial
            // height.
            prefs.edit().putBoolean("reduce_motion", false).commit();
            jump(0);
            call("enterSelection");
            Thread.sleep(60);
            Bundle state = new Bundle();
            Method save = method(screen.getClass(), "onSaveInstanceState", Bundle.class);
            ui(
                    () -> {
                        try {
                            save.invoke(screen, state);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
            check(state.getBoolean("selecting") && state.getInt("inbox_scroll") == 0);
            ActivityMonitor monitor =
                    addMonitor("org.visualvoicemail.app.MainActivity", null, false);
            ui(() -> screen.recreate());
            Activity restored = waitForMonitorWithTimeout(monitor, 5000);
            removeMonitor(monitor);
            check(restored != null);
            screen = restored;
            Thread.sleep(300);
            check(
                    (Boolean) field(screen, "selecting")
                            && scroll().getScrollY() == 0
                            && ((View) field(screen, "inboxHeader")).getVisibility() == View.GONE);
            call("clearSelection");
            Thread.sleep(300);
            ActivityMonitor settingsMonitor =
                    addMonitor("org.visualvoicemail.app.SettingsActivity", null, false);
            View settings = (View) field(screen, "settingsControl");
            tap(settings);
            Activity opened = waitForMonitorWithTimeout(settingsMonitor, 5000);
            removeMonitor(settingsMonitor);
            check(opened != null);
            if (opened != null) ui(() -> opened.finish());
            out.putBoolean("passed", true);
            out.putInt("sampled_frames", frames.size());
            out.putInt("largest_frame_step_px", maxStep);
        } catch (Throwable e) {
            out.putBoolean("passed", false);
            out.putString("failure_class", e.getClass().getSimpleName());
        } finally {
            SharedPreferences.Editor restore = prefs.edit();
            for (String key :
                    Arrays.asList("refresh_on_open", "reduce_motion", "auto_transcribe", "contact_names")) {
                Object v = before.get(key);
                if (v == null) restore.remove(key);
                else restore.putBoolean(key, (Boolean) v);
            }
            restore.commit();
            if (screen != null) ui(() -> screen.finish());
        }
        out.putInt("checks", checks);
        finish(-1, out);
    }
}
