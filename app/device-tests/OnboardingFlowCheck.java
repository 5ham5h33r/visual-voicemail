package org.visualvoicemail.checks;

import android.app.*;
import android.content.*;
import android.graphics.RectF;
import android.os.*;
import android.view.*;
import android.widget.*;

import java.lang.reflect.*;
import java.util.*;

public class OnboardingFlowCheck extends Instrumentation {
    int checks;
    Context c;
    Activity a;
    SharedPreferences prefs;
    Map<String, ?> before;

    void check(boolean yes) throws Exception {
        checks++;
        if (!yes) throw new Exception("check " + checks);
    }

    Object get(Object o, String n) throws Exception {
        Field f = o.getClass().getDeclaredField(n);
        f.setAccessible(true);
        return f.get(o);
    }

    View find(View v, String text) {
        if (v instanceof TextView && ((TextView) v).getText().toString().startsWith(text)) return v;
        if (v instanceof ViewGroup)
            for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) {
                View got = find(((ViewGroup) v).getChildAt(i), text);
                if (got != null) return got;
            }
        return null;
    }

    View guide(View v) {
        if (v.getClass().getName().endsWith("GuideOverlay")) return v;
        if (v instanceof ViewGroup)
            for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) {
                View got = guide(((ViewGroup) v).getChildAt(i));
                if (got != null) return got;
            }
        return null;
    }

    void tap(String text) throws Exception {
        View v = find(a.getWindow().getDecorView(), text);
        check(v != null);
        runOnMainSync(() -> v.performClick());
        Thread.sleep(260);
    }

    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);
        start();
    }

    @Override
    public void onStart() {
        Bundle out = new Bundle();
        c = getTargetContext();
        prefs = c.getSharedPreferences("settings", 0);
        before = prefs.getAll();
        try {
            a =
                    startActivitySync(
                            new Intent()
                                    .setClassName(c, "org.visualvoicemail.app.TutorialActivity")
                                    .putExtra("replay", true)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            Thread.sleep(300);
            check((Integer) get(a, "page") == 0);
            tap("Continue");
            check((Integer) get(a, "page") == 1);
            tap("Try marking as read");
            check(find(a.getWindow().getDecorView(), "Sample marked read") != null);
            tap("Continue");
            check((Integer) get(a, "page") == 2);
            tap("0:12");
            check(find(a.getWindow().getDecorView(), "0:12  ") != null);
            tap("Continue");
            check((Integer) get(a, "page") == 3);
            check(find(a.getWindow().getDecorView(), "Recognize your callers") != null);
            check(find(a.getWindow().getDecorView(), "Know when") != null);
            ActivityMonitor main = addMonitor("org.visualvoicemail.app.MainActivity", null, false);
            tap("Get Started");
            a = waitForMonitorWithTimeout(main, 5000);
            check(a != null);
            Thread.sleep(1000);
            check(guide(a.getWindow().getDecorView()) != null);
            for (int i = 0; i < 3; i++) tap("Next");
            View overlay = guide(a.getWindow().getDecorView());
            check(overlay != null);
            check(find(overlay, "Open a recording") != null);
            ActivityMonitor recording =
                    addMonitor("org.visualvoicemail.app.RecordingActivity", null, false);
            RectF hole = (RectF) get(overlay, "hole");
            runOnMainSync(
                    () -> {
                        long now = SystemClock.uptimeMillis();
                        MotionEvent down =
                                MotionEvent.obtain(now, now, 0, hole.centerX(), hole.centerY(), 0);
                        overlay.dispatchTouchEvent(down);
                        down.recycle();
                        MotionEvent up =
                                MotionEvent.obtain(
                                        now, now + 20, 1, hole.centerX(), hole.centerY(), 0);
                        overlay.dispatchTouchEvent(up);
                        up.recycle();
                    });
            a = waitForMonitorWithTimeout(recording, 5000);
            check(a != null);
            Thread.sleep(900);
            check(guide(a.getWindow().getDecorView()) != null);
            tap("Next");
            tap("Next");
            tap("Done");
            check(guide(a.getWindow().getDecorView()) == null);
            check(!prefs.getBoolean("guide_recording", true));
            out.putBoolean("passed", true);
        } catch (Throwable e) {
            out.putString("failure_class", e.getClass().getSimpleName());
            out.putBoolean("passed", false);
        } finally {
            SharedPreferences.Editor edit = prefs.edit();
            for (String key :
                    new String[] {
                        "tutorial_seen", "tutorial_page", "guide_recording", "alerts_prompted"
                    }) {
                Object v = before.get(key);
                if (v instanceof Boolean) edit.putBoolean(key, (Boolean) v);
                else if (v instanceof Integer) edit.putInt(key, (Integer) v);
                else edit.remove(key);
            }
            edit.commit();
            out.putInt("checks", checks);
            finish(-1, out);
        }
    }
}
