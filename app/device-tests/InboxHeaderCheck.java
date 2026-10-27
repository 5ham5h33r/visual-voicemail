package org.visualvoicemail.checks;

import android.app.*;
import android.content.*;
import android.graphics.Rect;
import android.os.*;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;

import org.json.*;

import java.lang.reflect.*;
import java.util.*;

/**
 * Synthetic long/short inboxes exercise native gestures without carrier writes or personal audio.
 */
public final class InboxHeaderCheck extends DeviceCheckInstrumentation {
    private Activity screen;
    private List<JSONObject> fixtures;
    private int checks;

    private static Object field(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    private static Method method(Class<?> type, String name, Class<?>... args) throws Exception {
        Method m = type.getDeclaredMethod(name, args);
        m.setAccessible(true);
        return m;
    }

    private void check(boolean ok) throws Exception {
        checks++;
        if (!ok) throw new Exception("check " + checks);
    }

    private void ui(Runnable r) {
        runOnMainSync(r);
        waitForIdleSync();
    }

    private void render() throws Exception {
        ui(
                () -> {
                    try {
                        set(screen, "inbox", fixtures);
                        method(screen.getClass(), "render").invoke(screen);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
        Thread.sleep(120);
    }

    @Override
    public void callActivityOnResume(Activity a) {
        super.callActivityOnResume(a);
        if (fixtures != null && a.getClass().getName().endsWith(".MainActivity"))
            try {
                set(a, "inbox", fixtures);
                method(a.getClass(), "render").invoke(a);
                ((TextView) field(a, "status")).setText(fixtures.size() + " sample voicemails");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
    }

    private View find(View view, String description) {
        if (description.equals(String.valueOf(view.getContentDescription()))) return view;
        if (view instanceof ViewGroup)
            for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
                View found = find(((ViewGroup) view).getChildAt(i), description);
                if (found != null) return found;
            }
        return null;
    }

    private boolean contains(View view, String words) {
        if (view instanceof TextView && ((TextView) view).getText().toString().contains(words))
            return true;
        if (view instanceof ViewGroup)
            for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++)
                if (contains(((ViewGroup) view).getChildAt(i), words)) return true;
        return false;
    }

    private ScrollView scroll() throws Exception {
        return (ScrollView) field(screen, "inboxScroll");
    }

    private View header() throws Exception {
        return (View) field(screen, "inboxHeader");
    }

    private float compactAlpha() throws Exception {
        return ((View) ((View) field(screen, "compactTitle")).getParent()).getAlpha();
    }

    private void move(int y) throws Exception {
        ScrollView v = scroll();
        ui(() -> { try { method(v.getClass(), "jumpTo", int.class).invoke(v, y); }
            catch (Exception e) { throw new RuntimeException(e); } });
        Thread.sleep(100);
    }

    private void menu(int id) throws Exception {
        View action =
                find(screen.getWindow().getDecorView(), "Voicemail filters and inbox actions");
        if (action == null || !action.isShown() || !action.isEnabled())
            action = find(screen.getWindow().getDecorView(), "Inbox actions");
        check(action != null && action.isEnabled());
        View menuControl = action;
        ui(() -> menuControl.performClick());
        PopupMenu popup = (PopupMenu) field(screen, "inboxMenu");
        ui(
                () -> {
                    popup.getMenu().performIdentifierAction(id, 0);
                    popup.dismiss();
                });
        Thread.sleep(350);
    }

    private void swipe(float x, float from, float to) throws Exception {
        long down = SystemClock.uptimeMillis();
        sendPointerSync(MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN, x, from, 0));
        for (int i = 1; i <= 14; i++) {
            SystemClock.sleep(16);
            long now = SystemClock.uptimeMillis();
            sendPointerSync(
                    MotionEvent.obtain(
                            down,
                            now,
                            MotionEvent.ACTION_MOVE,
                            x,
                            from + (to - from) * i / 14f,
                            0));
        }
        sendPointerSync(
                MotionEvent.obtain(
                        down, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, to, 0));
        Thread.sleep(500);
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
            prefs.edit()
                    .putBoolean("refresh_on_open", false)
                    .putBoolean("auto_transcribe", false)
                    .putBoolean("reduce_motion", false)
                    .commit();
            fixtures = new ArrayList<>();
            for (int i = 0; i < 40; i++)
                fixtures.add(
                        new JSONObject()
                                .put("objectid", "header-sample-" + i)
                                .put(
                                        "returnnumber",
                                        String.format(java.util.Locale.ROOT, "+1555000%04d", i))
                                .put(
                                        "date",
                                        String.format(
                                                java.util.Locale.ROOT,
                                                "2026-01-%02dT12:00:00Z",
                                                i % 28 + 1))
                                .put("duration", 18)
                                .put("flag", i % 2 == 0 ? "Seen" : ""));
            screen =
                    startActivitySync(
                            new Intent()
                                    .setClassName(c, "org.visualvoicemail.app.MainActivity")
                                    .addFlags(
                                            Intent.FLAG_ACTIVITY_NEW_TASK
                                                    | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            Thread.sleep(700);
            render();
            move(0);
            check(
                    !contains(
                            screen.getWindow().getDecorView(),
                            "Saved recordings and transcripts are available offline"));
            check(scroll().getScrollY() == 0 && compactAlpha() == 0);
            View title = (View) field(screen, "headerTitle");
            int range =
                    Math.max(
                            (int) (48 * c.getResources().getDisplayMetrics().density),
                            title.getBottom());
            int measured = header().getHeight();
            check(measured > range);
            int[] toolbarAt = new int[2];
            ((View) field(screen, "headerBrand")).getLocationOnScreen(toolbarAt);
            move(range / 2);
            check(compactAlpha() > .1f && compactAlpha() < .9f);
            check(header().getHeight() == measured);
            move(measured + 300);
            check(compactAlpha() == 1);
            check(header().getHeight() == measured);
            int[] after = new int[2];
            ((View) field(screen, "headerBrand")).getLocationOnScreen(after);
            check(after[1] == toolbarAt[1]);
            View action = find(screen.getWindow().getDecorView(), "Search voicemail");
            check(action != null && action.isEnabled());
            // Native touch/fling, starting from the list, collapses the header with one gesture.
            move(0);
            Rect bounds = new Rect();
            scroll().getGlobalVisibleRect(bounds);
            swipe(
                    bounds.centerX(),
                    bounds.bottom - 40,
                    Math.max(bounds.top + 40, bounds.bottom - bounds.height() * .68f));
            check(scroll().getScrollY() > range && compactAlpha() == 1);
            check(!screen.isFinishing());
            move(0);
            check(compactAlpha() == 0);
            move(measured + 300);
            menu(101);
            check((Integer) field(screen, "filter") == 1);
            check(
                    ((TextView) field(screen, "compactTitle"))
                            .getText()
                            .toString()
                            .equals("Unread voicemail"));
            check(((List<?>) field(screen, "shownMessages")).size() == 20);
            menu(100);
            check((Integer) field(screen, "filter") == 0);
            // Selection exposes its actions without the expanded header, and restores list offset.
            move(measured + 300);
            int old = scroll().getScrollY();
            menu(103);
            check((Boolean) field(screen, "selecting") && header().getVisibility() == View.GONE);
            check(((View) field(screen, "bulkBar")).getVisibility() == View.VISIBLE);
            check(Math.abs(scroll().getScrollY() - (old - measured)) < 5);
            ui(() -> screen.onBackPressed());
            Thread.sleep(200);
            check(!(Boolean) field(screen, "selecting") && compactAlpha() == 1);
            check(Math.abs(scroll().getScrollY() - old) < 5);
            // Back from a recording and activity recreation keep the scrolled list/header state.
            ActivityMonitor monitor =
                    addMonitor("org.visualvoicemail.app.MainActivity", null, false);
            ui(() -> screen.recreate());
            Activity restored = waitForMonitorWithTimeout(monitor, 5000);
            removeMonitor(monitor);
            check(restored != null);
            screen = restored;
            Thread.sleep(550);
            check(Math.abs(scroll().getScrollY() - old) < 5 && compactAlpha() == 1);
            ActivityMonitor settingsMonitor = addMonitor("org.visualvoicemail.app.SettingsActivity", null, false);
            View gear = find(screen.getWindow().getDecorView(), "Settings");
            ui(() -> gear.performClick());
            Activity settings = waitForMonitorWithTimeout(settingsMonitor, 5000);removeMonitor(settingsMonitor);
            check(settings != null);
            ui(() -> settings.finish());Thread.sleep(500);
            check(Math.abs(scroll().getScrollY() - old) < 5 && compactAlpha() == 1);
            // Search stays reachable from the compact bar and focuses a visible native text field.
            action = find(screen.getWindow().getDecorView(), "Search voicemail");
            View searchAction = action;
            ui(() -> searchAction.performClick());
            Thread.sleep(700);
            EditText search = (EditText) field(screen, "search");
            Rect searchBounds = new Rect();
            check(search.hasFocus() && search.getGlobalVisibleRect(searchBounds));
            check(searchBounds.height() >= 40 * c.getResources().getDisplayMetrics().density);
            ui(
                    () -> {
                        screen.getSystemService(InputMethodManager.class)
                                .hideSoftInputFromWindow(search.getWindowToken(), 0);
                        search.clearFocus();
                    });
            Thread.sleep(500);
            title = (View) field(screen, "headerTitle");
            // Entering selection from search must dismiss the keyboard and keep Back in the inbox.
            move(measured + 10);
            View compactSearch = find(screen.getWindow().getDecorView(), "Search voicemail");
            ui(() -> compactSearch.performClick());Thread.sleep(550);
            menu(103);Thread.sleep(600);
            check(!search.hasFocus() && header().getVisibility() == View.GONE);
            if (Build.VERSION.SDK_INT >= 30)
                check(!screen.getWindow().getDecorView().getRootWindowInsets().isVisible(WindowInsets.Type.ime()));
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);Thread.sleep(300);
            check(!(Boolean)field(screen, "selecting") && !screen.isFinishing());
            // Reduced motion keeps direct native scrolling but switches titles without a fade.
            prefs.edit().putBoolean("reduce_motion", true).commit();
            move(range / 2);
            check(compactAlpha() == 0);
            check(title.getAlpha() == 1);
            move(measured + 10);
            check(compactAlpha() == 1);
            prefs.edit().putBoolean("reduce_motion", false).commit();
            // A small inbox has no artificial scroll area or collapsed title.
            fixtures = new ArrayList<>(fixtures.subList(0, 1));
            render();
            move(100000);
            check(scroll().getScrollY() == 0 && compactAlpha() == 0);
            check(!scroll().canScrollVertically(1));
            out.putBoolean("passed", true);
        } catch (Throwable e) {
            out.putBoolean("passed", false);
            out.putString("failure_class", e.getClass().getSimpleName());
        } finally {
            SharedPreferences.Editor edit = prefs.edit();
            for (String key :
                    Arrays.asList("refresh_on_open", "auto_transcribe", "reduce_motion")) {
                Object v = before.get(key);
                if (v == null) edit.remove(key);
                else edit.putBoolean(key, (Boolean) v);
            }
            edit.commit();
            if (screen != null) ui(() -> screen.finish());
        }
        out.putInt("checks", checks);
        finish(-1, out);
    }
}
