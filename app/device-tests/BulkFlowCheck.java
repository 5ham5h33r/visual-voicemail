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

/**
 * Selection uses synthetic rows; speech uses a supplied public fixture. No personal flags are
 * changed.
 */
public final class BulkFlowCheck extends Instrumentation {
    private int checks;
    private Activity screen;
    private Context context;
    private final List<File> cleanup = new ArrayList<>();
    private Class<?> speech;

    private static Method method(Class<?> c, String name, Class<?>... types) throws Exception {
        Method m = c.getDeclaredMethod(name, types);
        m.setAccessible(true);
        return m;
    }

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

    private static Object staticField(Class<?> c, String name) throws Exception {
        Field f = c.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(null);
    }

    private void check(boolean ok) throws Exception {
        checks++;
        if (!ok) throw new Exception("check " + checks);
    }

    private void ui(Runnable r) {
        runOnMainSync(r);
        waitForIdleSync();
    }

    private void invokeUi(String name) throws Exception {
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

    @SuppressWarnings("unchecked")
    private Set<String> selected() throws Exception {
        return (Set<String>) field(screen, "selectedIds");
    }

    private View row(String id) throws Exception {
        return (View) field(((Map<?, ?>) field(screen, "rows")).get(id), "root");
    }

    private void clickButton(String name) throws Exception {
        View v = (View) field(screen, name);
        ui(() -> v.performClick());
    }

    private void accept() throws Exception {
        AlertDialog d = (AlertDialog) field(screen, "bulkDialog");
        ui(() -> d.getButton(-1).performClick());
    }

    private void cancelDialog() throws Exception {
        AlertDialog d = (AlertDialog) field(screen, "bulkDialog");
        ui(() -> d.getButton(-2).performClick());
    }

    private void chooseAll() throws Exception {
        clickButton("selectControl");
        clickButton("selectAll");
    }

    private void waitSpeech() throws Exception {
        long until = SystemClock.elapsedRealtime() + 180000;
        while ((Boolean) staticField(speech, "busy")) {
            if (SystemClock.elapsedRealtime() > until) throw new Exception("speech timeout");
            Thread.sleep(100);
        }
    }

    private Object cached(String id) throws Exception {
        return method(speech, "cached", Context.class, String.class).invoke(null, context, id);
    }

    private String json(Object transcript) throws Exception {
        return method(transcript.getClass(), "json").invoke(transcript).toString();
    }

    private void queue(List<JSONObject> messages, boolean replace) throws Exception {
        check(
                (Boolean)
                        method(speech, "generateBatch", Context.class, List.class, boolean.class)
                                .invoke(null, context, messages, replace));
    }

    private void recreateInbox() throws Exception {
        ActivityMonitor monitor = addMonitor("org.visualvoicemail.app.MainActivity", null, false);
        ui(() -> screen.recreate());
        Activity restored = waitForMonitorWithTimeout(monitor, 5000);
        removeMonitor(monitor);
        check(restored != null);
        screen = restored;
        Thread.sleep(400);
    }

    @Override
    public void onCreate(Bundle args) {
        super.onCreate(args);
        start();
    }

    @Override
    public void onStart() {
        Bundle result = new Bundle();
        context = getTargetContext();
        SharedPreferences prefs = context.getSharedPreferences("settings", 0);
        Map<String, ?> before = prefs.getAll();
        Object store = null;
        JSONObject marksBefore = null;
        try {
            speech = Class.forName("org.visualvoicemail.app.Transcription");
            check(!(Boolean) staticField(speech, "busy"));
            Class<?> storage = Class.forName("org.visualvoicemail.app.PrivateStore");
            Constructor<?> constructor = storage.getDeclaredConstructor(Context.class);
            constructor.setAccessible(true);
            store = constructor.newInstance(context);
            marksBefore =
                    (JSONObject)
                            method(storage, "get", String.class)
                                    .invoke(store, "deletion-marks.bin");
            if (marksBefore == null) marksBefore = new JSONObject();
            prefs.edit()
                    .putBoolean("refresh_on_open", false)
                    .putBoolean("auto_transcribe", false)
                    .putString("transcription_quality", "standard")
                    .commit();
            check((Boolean) method(speech, "ready", Context.class).invoke(null, context));
            screen =
                    startActivitySync(
                            new Intent()
                                    .setClassName(context, "org.visualvoicemail.app.MainActivity")
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            Thread.sleep(1000);
            List<JSONObject> fixtures = new ArrayList<>();
            for (int i = 0; i < 3; i++)
                fixtures.add(
                        new JSONObject()
                                .put("objectid", "bulk-check-" + i)
                                .put("returnnumber", "+1555000010" + i)
                                .put("date", "2026-01-01T12:00:00Z")
                                .put("duration", 8)
                                .put("flag", i == 1 ? "Seen" : ""));
            Method render = method(screen.getClass(), "render");
            ui(
                    () -> {
                        try {
                            set(screen, "inbox", fixtures);
                            render.invoke(screen);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
            View first = row("bulk-check-0");
            ui(() -> first.performLongClick());
            check((Boolean) field(screen, "selecting") && selected().size() == 1);
            check(((View) field(screen, "selectionBar")).getVisibility() == View.VISIBLE);
            clickButton("selectAll");
            check(selected().size() == 3);
            LinearLayout heading =
                    (LinearLayout) ((LinearLayout) row("bulk-check-0")).getChildAt(0);
            check(heading.getChildAt(0) instanceof CheckBox);
            View checkbox = heading.getChildAt(0);
            ui(() -> checkbox.performClick());
            check(selected().size() == 2);
            clickButton("selectAll");
            check(selected().size() == 3);
            clickButton("selectAll");
            check(selected().isEmpty());
            clickButton("selectAll");
            check(selected().size() == 3);
            EditText search = (EditText) field(screen, "search");
            ui(() -> search.setText("+15550000101"));
            check(selected().size() == 1 && selected().contains("bulk-check-1"));
            ui(() -> search.setText("no-matching-test-fixture"));
            check(selected().isEmpty() && !(Boolean) field(screen, "selecting"));
            ui(() -> search.setText(""));
            chooseAll();
            Method marks = method(screen.getClass(), "confirmMarks", boolean.class);
            ui(
                    () -> {
                        try {
                            marks.invoke(screen, true);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
            accept();
            JSONObject saved =
                    (JSONObject)
                            method(storage, "get", String.class)
                                    .invoke(store, "deletion-marks.bin");
            for (int i = 0; i < 3; i++) check(saved.optBoolean("bulk-check-" + i));
            check(!(Boolean) field(screen, "selecting"));
            chooseAll();
            ui(
                    () -> {
                        try {
                            marks.invoke(screen, false);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
            accept();
            saved =
                    (JSONObject)
                            method(storage, "get", String.class)
                                    .invoke(store, "deletion-marks.bin");
            for (int i = 0; i < 3; i++) check(!saved.optBoolean("bulk-check-" + i));
            chooseAll();
            LinearLayout actions = (LinearLayout) field(screen, "bulkBar");
            check(actions.getChildAt(1).isEnabled());
            check(!(Boolean) staticField(Class.forName("org.visualvoicemail.app.MailboxBatch"), "busy"));
            invokeUi("confirmTranscripts");
            check(((AlertDialog) field(screen, "bulkDialog")).isShowing());
            cancelDialog();
            check(!(Boolean) staticField(speech, "busy"));
            // System Back must leave selection rather than close the inbox.
            boolean[] focused = {false};
            long focusLimit = SystemClock.elapsedRealtime() + 3000;
            do {
                runOnMainSync(() -> focused[0] = screen.hasWindowFocus());
                if (!focused[0]) Thread.sleep(25);
            } while (!focused[0] && SystemClock.elapsedRealtime() < focusLimit);
            check(focused[0]);
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            Thread.sleep(250);
            check(!(Boolean) field(screen, "selecting") && !screen.isFinishing());
            chooseAll();
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
            check(
                    state.getBoolean("selecting")
                            && state.getStringArrayList("selected_ids").size() == 3);
            invokeUi("clearSelection");
            // Public audio + a damaged cached file exercise mixed outcomes without carrier
            // requests.
            Class<?> carrier = Class.forName("org.visualvoicemail.app.CarrierClient");
            Object client = method(carrier, "get", Context.class).invoke(null, context);
            for (int i = 0; i < 3; i++) {
                JSONObject message = fixtures.get(i);
                File file =
                        (File)
                                method(carrier, "audioFile", JSONObject.class)
                                        .invoke(client, message);
                file.getParentFile().mkdirs();
                cleanup.add(file);
                String key =
                        (String)
                                method(speech, "key", String.class)
                                        .invoke(null, message.getString("objectid"));
                cleanup.add(new File(context.getFilesDir(), key));
                try (FileOutputStream output = new FileOutputStream(file)) {
                    if (i == 1) output.write(new byte[] {1, 2, 3});
                    else
                        try (InputStream input =
                                getContext().getAssets().open("speech-fixture.wav")) {
                            byte[] bytes = new byte[8192];
                            int n;
                            while ((n = input.read(bytes)) != -1) output.write(bytes, 0, n);
                        }
                }
            }
            queue(fixtures, true);
            waitSpeech();
            check(
                    cached("bulk-check-0") != null
                            && cached("bulk-check-1") == null
                            && cached("bulk-check-2") != null);
            String report = (String) staticField(speech, "batchStatus");
            check(report.contains("2 saved") && report.contains("1 failed"));
            String original = json(cached("bulk-check-0"));
            queue(Arrays.asList(fixtures.get(0), fixtures.get(2)), false);
            waitSpeech();
            check(original.equals(json(cached("bulk-check-0"))));
            check(((String) staticField(speech, "batchStatus")).contains("2 kept"));
            // Updating a damaged recording must leave its previous successful transcript intact.
            File file =
                    (File)
                            method(carrier, "audioFile", JSONObject.class)
                                    .invoke(client, fixtures.get(0));
            try (FileOutputStream output = new FileOutputStream(file)) {
                output.write(new byte[] {1, 2, 3});
            }
            queue(Arrays.asList(fixtures.get(0)), true);
            waitSpeech();
            check(original.equals(json(cached("bulk-check-0"))));
            check(((String) staticField(speech, "batchStatus")).contains("1 failed"));
            // Recreate against the real cached inbox without changing any message or read flag.
            List<?> actual = (List<?>) method(carrier, "cachedInbox").invoke(client);
            ui(() -> { try { set(screen, "inbox", actual); method(screen.getClass(), "render").invoke(screen); }
                catch (Exception e) { throw new RuntimeException(e); } });
            if (!actual.isEmpty()) {
                chooseAll();
                Set<String> retained = new HashSet<>(selected());
                recreateInbox();
                check(retained.equals(selected()) && (Boolean)field(screen, "selecting"));
                invokeUi("clearSelection");
            }
            List<JSONObject> longQueue = new ArrayList<>();
            for (int i = 0; i < 20; i++) longQueue.add(fixtures.get(2));
            queue(longQueue, true);
            check(
                    !(Boolean)
                            method(
                                            speech,
                                            "generateBatch",
                                            Context.class,
                                            List.class,
                                            boolean.class)
                                    .invoke(null, context, fixtures, true));
            recreateInbox();
            check((Boolean)staticField(speech, "batch"));
            method(speech, "cancel").invoke(null);
            waitSpeech();
            check(((String) staticField(speech, "batchStatus")).startsWith("Queue cancelled"));
            check(
                    (Boolean)
                            method(speech, "select", Context.class, String.class)
                                    .invoke(null, context, "enhanced"));
            result.putBoolean("passed", true);
        } catch (Throwable e) {
            result.putString("failure_class", e.getClass().getSimpleName());
            if (screen != null) {
                result.putBoolean("screen_finishing", screen.isFinishing());
                result.putBoolean("window_focused", screen.hasWindowFocus());
                try {
                    result.putBoolean("selection_active", (Boolean) field(screen, "selecting"));
                    result.putBoolean("selection_back_registered", (Boolean) field(screen, "selectionBackRegistered"));
                } catch (Exception ignored) {}
            }
            result.putBoolean("passed", false);
        } finally {
            try {
                if (speech != null) {
                    method(speech, "cancel").invoke(null);
                    waitSpeech();
                    method(speech, "dismissBatch").invoke(null);
                }
                for (File file : cleanup) file.delete();
                if (store != null && marksBefore != null)
                    method(store.getClass(), "put", String.class, JSONObject.class)
                            .invoke(store, "deletion-marks.bin", marksBefore);
                SharedPreferences.Editor edit = prefs.edit();
                for (String key :
                        Arrays.asList(
                                "refresh_on_open", "auto_transcribe", "transcription_quality")) {
                    Object value = before.get(key);
                    if (value == null) edit.remove(key);
                    else if (value instanceof Boolean) edit.putBoolean(key, (Boolean) value);
                    else edit.putString(key, (String) value);
                }
                edit.commit();
                if (screen != null) ui(() -> screen.finish());
            } catch (Throwable cleanupFailure) {
                result.putBoolean("passed", false);
                result.putBoolean("cleanup_failed", true);
            }
        }
        result.putInt("checks", checks);
        finish(-1, result);
    }
}
