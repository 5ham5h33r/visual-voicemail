package org.visualvoicemail.checks;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;

import org.json.*;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;

/** Observe real pre-draw coordinates with synthetic short/long inboxes. No mailbox writes. */
public final class SelectionFrameCheck extends DeviceCheckInstrumentation {
    Activity screen;
    int checks;
    final List<JSONObject> fixtures = new ArrayList<>();

    Object field(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }

    void call(String name) throws Exception {
        Method method = screen.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        runOnMainSync(
                () -> {
                    try {
                        method.invoke(screen);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
        waitForIdleSync();
    }

    void check(boolean good) throws Exception {
        checks++;
        if (!good) throw new Exception("check " + checks);
    }

    void ui(Runnable action) {
        runOnMainSync(action);
        waitForIdleSync();
    }

    @Override
    public void callActivityOnResume(Activity activity) {
        super.callActivityOnResume(activity);
        if (!fixtures.isEmpty() && activity.getClass().getName().endsWith(".MainActivity"))
            try {
                Field inbox = activity.getClass().getDeclaredField("inbox");
                inbox.setAccessible(true);
                inbox.set(activity, fixtures);
                Method render = activity.getClass().getDeclaredMethod("render");
                render.setAccessible(true);
                render.invoke(activity);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
    }

    int[] sample(View first, View second, View header, View bulk, ScrollView scroll) {
        int[] a = new int[2], b = new int[2];
        first.getLocationOnScreen(a);
        second.getLocationOnScreen(b);
        return new int[] {
            (int) (SystemClock.uptimeMillis() % 10000000),
            a[1],
            b[1],
            first.getHeight(),
            second.getHeight(),
            header.getHeight(),
            bulk.getVisibility() == View.GONE ? 0 : bulk.getHeight(),
            scroll.getScrollY(),
            ((View) first.getParent()).getTop(),
            header.getVisibility() == View.GONE ? 0 : header.getHeight(),
            ((ViewGroup) first.getParent()).isLayoutSuppressed() ? 1 : 0
        };
    }

    JSONArray trace(boolean enter) throws Exception {
        ScrollView scroll = (ScrollView) field(screen, "inboxScroll");
        View header = (View) field(screen, "inboxHeader"), bulk = (View) field(screen, "bulkBar");
        LinearLayout messages = (LinearLayout) field(screen, "messages");
        View first = messages.getChildAt(0), second = messages.getChildAt(1);
        List<int[]> frames = new ArrayList<>();
        frames.add(sample(first, second, header, bulk, scroll));
        ViewTreeObserver.OnPreDrawListener listener =
                () -> {
                    frames.add(sample(first, second, header, bulk, scroll));
                    return true;
                };
        ui(
                () ->
                        screen.getWindow()
                                .getDecorView()
                                .getViewTreeObserver()
                                .addOnPreDrawListener(listener));
        if (enter) {
            // Isolate layout invariants from host input latency; SelectionTransitionCheck covers
            // the physical long-press path separately on a real device.
            runOnMainSync(() -> first.performLongClick());
        } else call("clearSelection");
        Thread.sleep(450);
        waitForIdleSync();
        ui(
                () ->
                        screen.getWindow()
                                .getDecorView()
                                .getViewTreeObserver()
                                .removeOnPreDrawListener(listener));
        frames.add(sample(first, second, header, bulk, scroll));
        check(frames.size() >= 5);
        check((Boolean) field(screen, "selecting") == enter);
        for (int[] frame : frames) {
            check(frame[frame.length - 1] == 0);
            check(frame[3] == frames.get(0)[3] && frame[4] == frames.get(0)[4]);
            check(Math.abs(frame[8] - frame[9]) <= 1);
        }
        JSONArray result = new JSONArray();
        for (int[] frame : frames) {
            JSONArray values = new JSONArray();
            for (int value : frame) values.put(value);
            result.put(values);
        }
        return result;
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
        JSONObject traces = new JSONObject();
        try {
            check(Arrays.asList("ranchu", "goldfish").contains(Build.HARDWARE));
            check(!new File(c.getFilesDir(), "session.bin").exists());
            prefs.edit()
                    .putBoolean("refresh_on_open", false)
                    .putBoolean("auto_transcribe", false)
                    .putBoolean("contact_names", false)
                    .putBoolean("reduce_motion", false)
                    .commit();
            for (int count : new int[] {2, 24}) {
                fixtures.clear();
                for (int i = 0; i < count; i++)
                    fixtures.add(
                            new JSONObject()
                                    .put("objectid", "frame-sample-" + i)
                                    .put("returnnumber", "Sample caller")
                                    .put("date", "2026-10-27T12:00:00Z")
                                    .put("duration", 18)
                                    .put("_saved", i == 1)
                                    .put("_carrier_removed", i == 1));
                screen =
                        startActivitySync(
                                new Intent()
                                        .setClassName(c, "org.visualvoicemail.app.MainActivity")
                                        .addFlags(
                                                Intent.FLAG_ACTIVITY_NEW_TASK
                                                        | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                long end = SystemClock.uptimeMillis() + 5000;
                while (!screen.hasWindowFocus() && SystemClock.uptimeMillis() < end)
                    Thread.sleep(50);
                check(screen.hasWindowFocus());
                Thread.sleep(800);
                Class<?> type = Class.forName("org.visualvoicemail.app.Transcript");
                Method parse = type.getDeclaredMethod("parse", JSONObject.class);
                parse.setAccessible(true);
                Map<String, Object> transcript = (Map<String, Object>) field(screen, "transcripts");
                Object text =
                        parse.invoke(
                                null,
                                new JSONObject(
                                        "{\"version\":2,\"segments\":[{\"start\":0,\"end\":1000,\"text\":\"Sample"
                                            + " transcript for layout measurement.\"}]}"));
                ui(
                        () -> {
                            for (JSONObject item : fixtures)
                                transcript.put(item.optString("objectid"), text);
                        });
                call("render");
                Thread.sleep(250);
                traces.put("rows" + count + "_enter", trace(true));
                traces.put("rows" + count + "_exit", trace(false));
                Activity done = screen;
                ui(done::finish);
                screen = null;
            }
            traces.put(
                    "columns",
                    new JSONArray(
                            Arrays.asList(
                                    "time_ms",
                                    "first_y",
                                    "second_y",
                                    "first_height",
                                    "second_height",
                                    "header_height",
                                    "bulk_height",
                                    "scroll_y",
                                    "messages_top",
                                    "expected_messages_top",
                                    "layout_suppressed")));
            try (OutputStream output =
                    new FileOutputStream(
                            new File(c.getExternalFilesDir(null), "selection-frame-trace.json"))) {
                output.write(traces.toString().getBytes("UTF-8"));
            }
            out.putBoolean("passed", true);
        } catch (Throwable e) {
            while (e.getCause() != null) e = e.getCause();
            out.putString("failure_class", e.getClass().getSimpleName());
            out.putString(
                    "failure",
                    e.getMessage() != null && e.getMessage().startsWith("check ")
                            ? e.getMessage()
                            : "frame observation failed");
        } finally {
            if (screen != null) ui(screen::finish);
            SharedPreferences.Editor restore = prefs.edit();
            for (String key :
                    new String[] {
                        "refresh_on_open", "auto_transcribe", "contact_names", "reduce_motion"
                    }) {
                Object value = before.get(key);
                if (value instanceof Boolean) restore.putBoolean(key, (Boolean) value);
                else restore.remove(key);
            }
            restore.commit();
        }
        out.putInt("checks", checks);
        finish(-1, out);
    }
}
