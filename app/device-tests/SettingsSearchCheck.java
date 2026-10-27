package org.visualvoicemail.checks;

import android.app.*;
import android.content.*;
import android.graphics.Rect;
import android.os.*;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;

import java.lang.reflect.*;
import java.util.*;

/** Real settings controls and navigation; no carrier writes, downloads, or personal audio. */
public final class SettingsSearchCheck extends DeviceCheckInstrumentation {
    private Activity screen;
    private int checks;

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

    private void check(boolean value) throws Exception {
        checks++;
        if (!value) throw new Exception("check " + checks);
    }

    private void ui(Runnable action) {
        runOnMainSync(action);
        waitForIdleSync();
    }

    private EditText search() throws Exception {
        return (EditText) field(screen, "settingsSearch");
    }

    private void query(String query) throws Exception {
        EditText s = search();
        ui(() -> s.setText(query));
        Thread.sleep(120);
    }

    private View find(View root, String prefix) {
        if (root.getVisibility() != View.VISIBLE) return null;
        if (root instanceof TextView && ((TextView) root).getText().toString().startsWith(prefix))
            return root;
        if (root instanceof ViewGroup)
            for (int i = 0; i < ((ViewGroup) root).getChildCount(); i++) {
                View found = find(((ViewGroup) root).getChildAt(i), prefix);
                if (found != null) return found;
            }
        return null;
    }

    private View row(String prefix) {
        return find(screen.getWindow().getDecorView(), prefix);
    }

    private View described(View root, String description) {
        if (description.equals(String.valueOf(root.getContentDescription()))) return root;
        if (root instanceof ViewGroup)
            for (int i = 0; i < ((ViewGroup) root).getChildCount(); i++) {
                View found = described(((ViewGroup) root).getChildAt(i), description);
                if (found != null) return found;
            }
        return null;
    }

    private int visibleEntries() throws Exception {
        int count = 0;
        for (Object section : (List<?>) field(screen, "sections"))
            for (Object entry : (List<?>) field(section, "entries"))
                if (((View) field(entry, "view")).isShown()) count++;
        return count;
    }

    private void hideKeyboard() throws Exception {
        Method hide = method(screen.getClass(), "hideSearchKeyboard");
        ui(
                () -> {
                    try {
                        hide.invoke(screen);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
        Thread.sleep(400);
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
            prefs.edit().putBoolean("refresh_on_open", false).commit();
            screen =
                    startActivitySync(
                            new Intent()
                                    .setClassName(c, "org.visualvoicemail.app.SettingsActivity")
                                    .addFlags(
                                            Intent.FLAG_ACTIVITY_NEW_TASK
                                                    | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            Thread.sleep(500);
            View back = described(screen.getWindow().getDecorView(), "Back to voicemail");
            View title = row("Settings");
            Rect a = new Rect(), b = new Rect();
            back.getGlobalVisibleRect(a);
            title.getGlobalVisibleRect(b);
            check(
                    Math.abs(a.centerY() - b.centerY())
                                    <= 3 * c.getResources().getDisplayMetrics().density
                            && b.left >= a.right);
            check(((View) field(screen, "searchClear")).getVisibility() == View.GONE);
            check(((View) field(screen, "settingsResultCount")).getVisibility() == View.GONE);
            int total = visibleEntries();
            check(total > 20);
            query("dark mode");
            check(visibleEntries() == 1 && row("Theme\n") != null);
            check(((View) field(screen, "searchClear")).getVisibility() == View.VISIBLE);
            query("download path");
            check(visibleEntries() == 1 && row("Download location\n") != null);
            check(row("STORAGE") != null && row("APPEARANCE") == null);
            for (Object section : (List<?>) field(screen, "sections"))
                for (Object note : (List<?>) field(section, "notes"))
                    if (((View) note).getVisibility() != View.GONE)
                        throw new Exception("notes not filtered");
            check(true);
            query("whisper large-v3");
            View quality = row("Transcription quality\n");
            check(quality != null);
            Activity settings = screen;
            ActivityMonitor picker =
                    addMonitor(
                            "org.visualvoicemail.app.TranscriptionSettingsActivity", null, false);
            ui(() -> quality.performClick());
            Activity model = waitForMonitorWithTimeout(picker, 5000);
            removeMonitor(picker);
            check(model != null);
            ui(() -> model.finish());
            Thread.sleep(500);
            screen = settings;
            check(
                    search().getText().toString().equals("whisper large-v3")
                            && row("Transcription quality\n") != null);
            query("no-such-setting-public-example");
            check(visibleEntries() == 0 && ((View) field(screen, "noSettings")).isShown());
            query(".*");
            check(visibleEntries() == 0);
            query("animations");
            Switch motion = (Switch) row("Reduce motion\n");
            check(motion != null);
            EditText initialInput = search();
            ui(
                    () -> {
                        initialInput.requestFocus();
                        screen.getSystemService(InputMethodManager.class)
                                .showSoftInput(initialInput, InputMethodManager.SHOW_IMPLICIT);
                    });
            Thread.sleep(500);
            boolean original = motion.isChecked();
            ui(() -> motion.performClick());
            Thread.sleep(500);
            check(prefs.getBoolean("reduce_motion", false) != original);
            check(!search().hasFocus());
            if (Build.VERSION.SDK_INT >= 30)
                check(
                        !screen.getWindow()
                                .getDecorView()
                                .getRootWindowInsets()
                                .isVisible(WindowInsets.Type.ime()));
            ui(() -> motion.performClick());
            check(prefs.getBoolean("reduce_motion", false) == original);
            query("download path");
            EditText edit = search();
            ui(
                    () -> {
                        edit.requestFocus();
                        edit.setSelection(4);
                    });
            ActivityMonitor restoredMonitor =
                    addMonitor("org.visualvoicemail.app.SettingsActivity", null, false);
            ui(() -> screen.recreate());
            Activity restored = waitForMonitorWithTimeout(restoredMonitor, 5000);
            removeMonitor(restoredMonitor);
            check(restored != null);
            screen = restored;
            Thread.sleep(450);
            check(search().getText().toString().equals("download path") && visibleEntries() == 1);
            check(search().getSelectionStart() == 4);
            View clearInitial = (View) field(screen, "searchClear");
            ui(() -> clearInitial.performClick());
            Thread.sleep(150);
            check(search().length() == 0 && search().hasFocus());
            check(
                    ((View) field(screen, "searchClear")).getVisibility() == View.GONE
                            && visibleEntries() == total);
            hideKeyboard();
            ScrollView scroll = (ScrollView) field(screen, "settingsScroll");
            ui(
                    () -> {
                        scroll.fling(0);
                        scroll.computeScroll();
                        scroll.scrollTo(0, 600);
                    });
            Thread.sleep(100);
            int position = scroll.getScrollY();
            check(position > 0);
            EditText rapid = search();
            ui(
                    () -> {
                        scroll.fling(5000);
                        rapid.setText("dark");
                        rapid.setText("download path");
                    });
            Thread.sleep(200);
            check(scroll.getScrollY() == 0 && visibleEntries() == 1);
            View clearAgain = (View) field(screen, "searchClear");
            ui(() -> clearAgain.performClick());
            Thread.sleep(200);
            check(Math.abs(scroll.getScrollY() - position) < 4 && visibleEntries() == total);
            query(" ");
            check(
                    visibleEntries() == total
                            && ((View) field(screen, "settingsResultCount")).getVisibility()
                                    == View.GONE);
            query("");
            check(((View) field(screen, "searchClear")).getVisibility() == View.GONE);
            ActivityMonitor home = addMonitor("org.visualvoicemail.app.MainActivity", null, false);
            back = described(screen.getWindow().getDecorView(), "Back to voicemail");
            View arrow = back;
            ui(() -> arrow.performClick());
            Activity main = waitForMonitorWithTimeout(home, 5000);
            removeMonitor(home);
            check(main != null);
            screen = main;
            out.putBoolean("passed", true);
        } catch (Throwable e) {
            out.putBoolean("passed", false);
            out.putString("failure_class", e.getClass().getSimpleName());
        } finally {
            SharedPreferences.Editor restore = prefs.edit();
            for (String key : Arrays.asList("refresh_on_open", "reduce_motion")) {
                Object value = before.get(key);
                if (value == null) restore.remove(key);
                else restore.putBoolean(key, (Boolean) value);
            }
            restore.commit();
            if (screen != null) ui(() -> screen.finish());
        }
        out.putInt("checks", checks);
        finish(-1, out);
    }
}
