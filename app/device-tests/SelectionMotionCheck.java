package org.visualvoicemail.checks;

import android.app.*;
import android.content.*;
import android.graphics.drawable.*;
import android.os.*;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;

import org.json.*;

import java.lang.reflect.*;
import java.util.*;

/** Exercise the native checkbox and retained row, including interruption and reduced motion. */
public final class SelectionMotionCheck extends Instrumentation {
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

    private void check(boolean ok) throws Exception {
        checks++;
        if (!ok) throw new Exception("check " + checks);
    }

    private void ui(Runnable r) {
        runOnMainSync(r);
        waitForIdleSync();
    }

    private Object row(String id) throws Exception {
        return ((Map<?, ?>) field(screen, "rows")).get(id);
    }

    private Set<?> selected() throws Exception {
        return (Set<?>) field(screen, "selectedIds");
    }

    private void invoke(String name) throws Exception {
        Method m = method(screen.getClass(), name);
        ui(
                () -> {
                    try {
                        m.invoke(screen);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    private boolean running(Object row) throws Exception {
        android.animation.ValueAnimator a =
                (android.animation.ValueAnimator) field(row, "selectionFill");
        return a != null && a.isRunning();
    }

    private boolean nativeRunning(Drawable drawable) {
        if (drawable == null) return false;
        if (drawable instanceof Animatable && ((Animatable) drawable).isRunning()) return true;
        Drawable current = drawable.getCurrent();
        if (current != drawable) return nativeRunning(current);
        if (drawable instanceof DrawableWrapper)
            return nativeRunning(((DrawableWrapper) drawable).getDrawable());
        return false;
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
                    .putBoolean("reduce_motion", false)
                    .commit();
            screen =
                    startActivitySync(
                            new Intent()
                                    .setClassName(c, "org.visualvoicemail.app.MainActivity")
                                    .addFlags(
                                            Intent.FLAG_ACTIVITY_NEW_TASK
                                                    | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            Thread.sleep(600);
            List<JSONObject> samples = new ArrayList<>();
            for (int i = 0; i < 3; i++)
                samples.add(
                        new JSONObject()
                                .put("objectid", "motion-sample-" + i)
                                .put("returnnumber", "Sample caller")
                                .put("date", "2026-01-01T12:00:00Z")
                                .put("duration", 18));
            Field inbox = screen.getClass().getDeclaredField("inbox");
            inbox.setAccessible(true);
            Method render = method(screen.getClass(), "render");
            ui(
                    () -> {
                        try {
                            inbox.set(screen, samples);
                            render.invoke(screen);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
            Thread.sleep(100);
            Object first = row("motion-sample-0");
            View card = (View) field(first, "root");
            CheckBox checkbox = (CheckBox) field(first, "check");
            check(checkbox.getVisibility() == View.GONE);
            ui(() -> card.performLongClick());
            check(
                    first == row("motion-sample-0")
                            && card == (View) field(row("motion-sample-0"), "root"));
            check(selected().size() == 1 && checkbox.isChecked() && card.isSelected());
            check(
                    ((TextView) field(screen, "selectionCount"))
                            .getText()
                            .toString()
                            .startsWith("1 selected"));
            Thread.sleep(220);
            check(!running(first));
            check(
                    (Integer) field(first, "selectionColor")
                            == screen.getColor(
                                    c.getResources()
                                            .getIdentifier(
                                                    "accent_surface",
                                                    "color",
                                                    c.getPackageName())));
            boolean[] observed = {false};
            ui(
                    () -> {
                        checkbox.performClick();
                        try {
                            observed[0] = running(first);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
            check(selected().isEmpty() && !checkbox.isChecked() && !card.isSelected());
            check(observed[0]);
            Thread.sleep(30);
            out.putBoolean(
                    "native_checkbox_animation_observed",
                    nativeRunning(checkbox.getButtonDrawable()));
            check(first == row("motion-sample-0"));
            Thread.sleep(200);
            check(!running(first));
            check(
                    (Integer) field(first, "selectionColor")
                            == screen.getColor(
                                    c.getResources()
                                            .getIdentifier(
                                                    "surface", "color", c.getPackageName())));
            ui(() -> card.performClick());
            check(selected().size() == 1 && checkbox.isChecked());
            // Rapid reversals must finish at the latest requested state and color.
            ui(
                    () -> {
                        for (int i = 0; i < 7; i++) card.performClick();
                    });
            Thread.sleep(230);
            check(selected().isEmpty() && !checkbox.isChecked() && !running(first));
            check(
                    (Integer) field(first, "selectionColor")
                            == screen.getColor(
                                    c.getResources()
                                            .getIdentifier(
                                                    "surface", "color", c.getPackageName())));
            View all = (View) field(screen, "selectAll");
            ui(() -> all.performClick());
            check(selected().size() == 3);
            for (int i = 0; i < 3; i++)
                check(((CheckBox) field(row("motion-sample-" + i), "check")).isChecked());
            ui(() -> all.performClick());
            check(selected().isEmpty());
            // Exit/re-enter while a layout transition is unfinished must not leave hidden controls.
            invoke("clearSelection");
            View select = (View) field(screen, "selectControl");
            ui(() -> select.performClick());
            invoke("clearSelection");
            ui(() -> select.performClick());
            Thread.sleep(250);
            check(checkbox.getVisibility() == View.VISIBLE && checkbox.isEnabled());
            check(first == row("motion-sample-0"));
            AccessibilityNodeInfo node = card.createAccessibilityNodeInfo();
            check(node.isCheckable() && !node.isChecked());
            node.recycle();
            p.edit().putBoolean("reduce_motion", true).commit();
            ui(() -> checkbox.performClick());
            check(checkbox.isChecked() && selected().size() == 1 && !running(first));
            check(
                    (Integer) field(first, "selectionColor")
                            == screen.getColor(
                                    c.getResources()
                                            .getIdentifier(
                                                    "accent_surface",
                                                    "color",
                                                    c.getPackageName())));
            check(!nativeRunning(checkbox.getButtonDrawable()));
            node = card.createAccessibilityNodeInfo();
            check(node.isChecked());
            node.recycle();
            invoke("clearSelection");
            check(
                    checkbox.getVisibility() == View.GONE
                            && !checkbox.isEnabled()
                            && !card.isSelected());
            check(!running(first));
            check(
                    ((View) field(screen, "headerBrand")).getAlpha() == 1
                            && ((View) field(screen, "bulkBar")).getAlpha() == 1);
            node = card.createAccessibilityNodeInfo();
            check(!node.isCheckable());
            node.recycle();
            out.putBoolean("passed", true);
        } catch (Throwable e) {
            out.putBoolean("passed", false);
            out.putString("failure_class", e.getClass().getSimpleName());
        } finally {
            SharedPreferences.Editor restore = p.edit();
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
