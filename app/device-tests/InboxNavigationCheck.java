package org.visualvoicemail.checks;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;

import org.json.*;

import java.lang.reflect.*;
import java.util.*;

/** Real filter gestures over view-only fixtures. Never changes carrier flags or recording data. */
public final class InboxNavigationCheck extends DeviceCheckInstrumentation {
    private Activity screen;
    private int checks;
    private boolean sampledDrag;
    private float dragGap;
    private final List<JSONObject> fixtures = new ArrayList<>();

    private static Object field(Object o, String n) throws Exception {
        Field f = o.getClass().getDeclaredField(n);
        f.setAccessible(true);
        return f.get(o);
    }

    private void call(String n) throws Exception {
        Method m = screen.getClass().getDeclaredMethod(n);
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

    private void check(boolean ok) throws Exception {
        checks++;
        if (!ok) throw new Exception("check " + checks);
    }

    private void ui(Runnable r) {
        runOnMainSync(r);
        waitForIdleSync();
    }

    private View find(View v, String name) {
        if (name.equals(v.getContentDescription())
                || v instanceof TextView && name.contentEquals(((TextView) v).getText())) return v;
        if (v instanceof ViewGroup)
            for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) {
                View hit = find(((ViewGroup) v).getChildAt(i), name);
                if (hit != null) return hit;
            }
        return null;
    }

    private View named(String n) {
        return find(screen.getWindow().getDecorView(), n);
    }

    private int page() throws Exception {
        return (Integer) field(screen, "filter");
    }

    private void tap(View v) {
        int[] p = new int[2];
        v.getLocationOnScreen(p);
        float x = p[0] + v.getWidth() / 2f, y = p[1] + v.getHeight() / 2f;
        long t = SystemClock.uptimeMillis();
        sendPointerSync(MotionEvent.obtain(t, t, 0, x, y, 0));
        sendPointerSync(MotionEvent.obtain(t, SystemClock.uptimeMillis(), 1, x, y, 0));
    }

    private void swipe(float from, float to, float dy, int end) throws Exception {
        View host = (View) field(screen, "inboxNavigation");
        int[] p = new int[2];
        host.getLocationOnScreen(p);
        float y = p[1] + host.getHeight() * .80f;
        long down = SystemClock.uptimeMillis();
        sendPointerSync(MotionEvent.obtain(down, down, 0, p[0] + host.getWidth() * from, y, 0));
        for (int i = 1; i <= 14; i++) {
            Thread.sleep(14);
            sendPointerSync(
                    MotionEvent.obtain(
                            down,
                            SystemClock.uptimeMillis(),
                            2,
                            p[0] + host.getWidth() * (from + (to - from) * i / 14),
                            y + dy * i / 14,
                            0));
            if (i == 7 && !sampledDrag && dy == 0 && page() == 1 && from > to) {
                float[] measured = {Float.NaN, Float.NaN};
                runOnMainSync(
                        () -> {
                            try {
                                View active = (View) field(screen, "messages"),
                                        neighbor = (View) field(host, "preview");
                                measured[0] = active.getTranslationX();
                                if (neighbor != null) {
                                    int[] a = new int[2], origin = new int[2];
                                    active.getLocationOnScreen(a);
                                    host.getLocationOnScreen(origin);
                                    // ViewOverlay has host-local coordinates and no ordinary window
                                    // parent.
                                    measured[1] =
                                            neighbor.getLeft()
                                                    + neighbor.getTranslationX()
                                                    - (a[0] - origin[0])
                                                    - active.getWidth();
                                }
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        });
                float density = screen.getResources().getDisplayMetrics().density;
                check(Math.abs(measured[0] - host.getWidth() * (to - from) * .5f) < density * 3);
                dragGap = measured[1];
                check(measured[1] >= density * 12 && measured[1] <= density * 24);
                sampledDrag = true;
                dragGap = measured[1];
            }
        }
        sendPointerSync(
                MotionEvent.obtain(
                        down,
                        SystemClock.uptimeMillis(),
                        end,
                        p[0] + host.getWidth() * to,
                        y + dy,
                        0));
        Thread.sleep(300);
        waitForIdleSync();
    }

    @Override
    public void callActivityOnResume(Activity a) {
        super.callActivityOnResume(a);
        a.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (!fixtures.isEmpty() && a.getClass().getName().endsWith(".MainActivity"))
            try {
                Field f = a.getClass().getDeclaredField("inbox");
                f.setAccessible(true);
                f.set(a, fixtures);
                Method m = a.getClass().getDeclaredMethod("render");
                m.setAccessible(true);
                m.invoke(a);
                ((TextView) field(a, "status")).setText("Sample inbox");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
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
        SharedPreferences p = c.getSharedPreferences("settings", 0);
        Map<String, ?> before = p.getAll();
        try {
            p.edit()
                    .putBoolean("refresh_on_open", false)
                    .putBoolean("auto_transcribe", false)
                    .putBoolean("reduce_motion", false)
                    .commit();
            for (int i = 0; i < 24; i++)
                fixtures.add(
                        new JSONObject()
                                .put("objectid", "navigation-sample-" + i)
                                .put("returnnumber", "Sample caller")
                                .put("date", "2026-01-01T12:00:00Z")
                                .put("duration", 18)
                                .put("flag", i % 2 == 0 ? "" : "Seen"));
            screen =
                    startActivitySync(
                            new Intent()
                                    .setClassName(c, "org.visualvoicemail.app.MainActivity")
                                    .addFlags(
                                            Intent.FLAG_ACTIVITY_NEW_TASK
                                                    | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            Thread.sleep(600);
            for (int i = 0; i < 50 && !screen.hasWindowFocus(); i++) Thread.sleep(100);
            if (!screen.hasWindowFocus())
                throw new IllegalStateException("The test app needs foreground focus for physical gesture checks");
            View originalAll = (View) field(screen, "messages");
            check(named("Select") == null && named("Refresh") == null);
            View refresh = (View) field(screen, "refresh");
            check("Refresh mailbox".contentEquals(refresh.getContentDescription())
                    || "Connect mailbox".contentEquals(refresh.getContentDescription()));
            check(refresh.getVisibility() == View.GONE
                    || refresh.getWidth() >= 48 * c.getResources().getDisplayMetrics().density);
            View host = (View) field(screen, "inboxNavigation");
            Button[] tabs = (Button[]) field(screen, "filters");
            List<Float> frames = new ArrayList<>();
            ViewTreeObserver.OnPreDrawListener sample =
                    () -> {
                        try {
                            if (field(host, "animator") != null)
                                frames.add((Float) field(host, "progress"));
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                        return true;
                    };
            ui(() -> host.getViewTreeObserver().addOnPreDrawListener(sample));
            tap(tabs[1]);
            Thread.sleep(300);
            out.putInt("page_after_first_tap", page());
            out.putInt("shown_after_first_tap", ((List<?>) field(screen, "shownMessages")).size());
            out.putBoolean("window_focus", screen.hasWindowFocus());
            check(page() == 1 && ((List<?>) field(screen, "shownMessages")).size() == 12);
            check(frames.size() >= 3 && frames.stream().anyMatch(v -> v > 0 && v < 1));
            check(tabs[1].isSelected() && !tabs[0].isSelected());
            swipe(.80f, .20f, 0, MotionEvent.ACTION_UP);
            check(page() == 2 && ((List<?>) field(screen, "shownMessages")).isEmpty());
            check(named("No saved voicemails") != null);
            check(named("View all voicemails") != null);
            swipe(.80f, .20f, 0, MotionEvent.ACTION_UP);
            check(page() == 2); // No wrapping at the last tab.
            swipe(.20f, .80f, 0, MotionEvent.ACTION_UP);
            check(page() == 1);
            swipe(.20f, .80f, 0, MotionEvent.ACTION_UP);
            check(page() == 0);
            check(originalAll == field(screen, "messages"));
            swipe(.20f, .80f, 0, MotionEvent.ACTION_UP);
            check(page() == 0);
            swipe(.80f, .20f, 0, MotionEvent.ACTION_CANCEL);
            check(page() == 0);
            View messages = (View) field(screen, "messages");
            check(Math.abs(messages.getTranslationX()) < 1 && messages.getAlpha() == 1);
            swipe(.50f, .52f, -host.getHeight() * .45f, MotionEvent.ACTION_UP);
            check(page() == 0 && ((ScrollView) field(screen, "inboxScroll")).getScrollY() > 0);
            // Rapid tab taps must finish at the final request and release snapshots.
            ui(
                    () -> {
                        tabs[2].performClick();
                        tabs[1].performClick();
                        tabs[0].performClick();
                    });
            Thread.sleep(300);
            check(page() == 0 && field(host, "outgoing") == null && messages.getAlpha() == 1);
            // Query text survives swipe navigation and each search scope is explicit.
            EditText search = (EditText) field(screen, "search");
            ui(() -> search.setText("nonexistent-phrase"));
            ui(() -> tabs[1].performClick());
            Thread.sleep(300);
            check(named("No unread matches") != null);
            swipe(.80f, .20f, 0, MotionEvent.ACTION_UP);
            check(page() == 2 && named("No saved matches") != null);
            check("nonexistent-phrase".contentEquals(search.getText()));
            tap(named("Clear search"));
            Thread.sleep(100);
            check(search.length() == 0 && named("No saved voicemails") != null);
            tap(named("View all voicemails"));
            Thread.sleep(300);
            check(page() == 0);
            // Read-only fixtures allow honest empty Unread and All states.
            Class<?> transcript=Class.forName("org.visualvoicemail.app.Transcript");
            Method parse=transcript.getDeclaredMethod("parse",JSONObject.class);parse.setAccessible(true);
            Object translated=parse.invoke(null,new JSONObject("{\"version\":2,\"task\":\"translate\",\"segments\":[{\"start\":100,\"end\":900,\"text\":\"translationfixtureunique\"}]}"));
            ((Map)field(screen,"translations")).put("navigation-sample-0",translated);
            ui(()->search.setText("translationfixtureunique"));
            check(((List<?>)field(screen,"shownMessages")).size()==1);
            check(named("English · translationfixtureunique")!=null);
            ((Map)field(screen,"translations")).clear();ui(()->search.setText(""));
            fixtures.clear();
            fixtures.add(
                    new JSONObject()
                            .put("objectid", "read-only-sample")
                            .put("returnnumber", "Sample caller")
                            .put("flag", "Seen"));
            call("render");
            ui(() -> tabs[1].performClick());
            Thread.sleep(300);
            check(named("You're all caught up") != null);
            fixtures.clear();
            call("render");
            ui(() -> tabs[0].performClick());
            Thread.sleep(300);
            check(named("No voicemails yet") != null || named("Your voicemail, here") != null);
            // The explicit menu route replaces the removed standalone Select button.
            fixtures.add(
                    new JSONObject()
                            .put("objectid", "selection-sample")
                            .put("returnnumber", "Sample caller"));
            call("render");
            tap(named("Inbox actions"));
            Thread.sleep(180);
            PopupMenu menu = (PopupMenu) field(screen, "inboxMenu");
            check(menu.getMenu().findItem(103).isEnabled());
            PopupWindow popup = (PopupWindow) field(menu, "window");
            View explicitSelect = find(popup.getContentView(), "Select messages");
            check(explicitSelect != null);
            tap(explicitSelect);
            Thread.sleep(300);
            check((Boolean) field(screen, "selecting"));
            swipe(.80f, .20f, 0, MotionEvent.ACTION_UP);
            check(page() == 0 && (Boolean) field(screen, "selecting"));
            call("clearSelection");
            Thread.sleep(300);
            p.edit().putBoolean("reduce_motion", true).commit();
            ui(() -> tabs[2].performClick());
            check(
                    page() == 2
                            && field(host, "animator") == null
                            && field(host, "outgoing") == null);
            swipe(.20f, .80f, 0, MotionEvent.ACTION_UP);
            check(page() == 1);
            check(messages.getTranslationX() == 0 && messages.getAlpha() == 1);
            ui(() -> host.getViewTreeObserver().removeOnPreDrawListener(sample));
            out.putInt("sampled_transition_frames", frames.size());
            out.putFloat("page_gap_px", dragGap);
            out.putBoolean("direct_drag_tracking", sampledDrag);
            out.putBoolean("passed", true);
        } catch (Throwable e) {
            out.putBoolean("passed", false);
            out.putString("failure_class", e.getClass().getSimpleName());
        } finally {
            SharedPreferences.Editor restore = p.edit();
            for (String key :
                    Arrays.asList("refresh_on_open", "auto_transcribe", "reduce_motion")) {
                Object v = before.get(key);
                if (v == null) restore.remove(key);
                else restore.putBoolean(key, (Boolean) v);
            }
            restore.commit();
            if (screen != null) ui(() -> screen.finish());
        }
        out.putFloat("page_gap_px", dragGap);
        out.putInt("checks", checks);
        finish(-1, out);
    }
}
