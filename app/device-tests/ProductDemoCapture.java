package org.visualvoicemail.checks;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.media.MediaPlayer;
import android.os.*;
import android.view.*;
import android.widget.*;

import org.json.*;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

/** Opt-in filming on a disconnected sample emulator. Never reads a personal mailbox. */
public final class ProductDemoCapture extends DeviceCheckInstrumentation {
    Activity screen;
    Context context;
    Object store;
    final Map<String, JSONObject> backups = new LinkedHashMap<>();
    final Map<String, String> names = new HashMap<>();
    boolean prepared;
    boolean playbackOnly;
    String captureToken;
    int clips;
    private volatile boolean drawing;
    private final Choreographer.FrameCallback drawFrame =
            new Choreographer.FrameCallback() {
                @Override
                public void doFrame(long time) {
                    if (!drawing || screen == null) return;
                    screen.getWindow().getDecorView().invalidate();
                    Choreographer.getInstance().postFrameCallback(this);
                }
            };

    Object call(Object target, String name, Class<?>[] types, Object... args) throws Exception {
        Class<?> type = target instanceof Class ? (Class<?>) target : target.getClass();
        Method m = type.getDeclaredMethod(name, types);
        m.setAccessible(true);
        return m.invoke(target instanceof Class ? null : target, args);
    }

    Object field(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }

    void ui(Runnable action) {
        runOnMainSync(action);
        if (!drawing) waitForIdleSync();
    }

    void invoke(String name) throws Exception {
        Activity target = screen;
        ui(
                () -> {
                    try {
                        call(target, name, new Class<?>[0]);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    void save(String key, JSONObject value) throws Exception {
        if (!backups.containsKey(key))
            backups.put(key, (JSONObject) call(store, "get", new Class<?>[] {String.class}, key));
        if (!(Boolean)
                call(store, "put", new Class<?>[] {String.class, JSONObject.class}, key, value))
            throw new IOException("Demo fixture could not be written");
    }

    void hideNumbers(View v) {
        if (v instanceof TextView) {
            TextView t = (TextView) v;
            String s = t.getText().toString();
            if (s.matches(".*(?:202[) .-]*555|1202555).*")) t.setText("Mobile");
        }
        if (v instanceof ViewGroup)
            for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++)
                hideNumbers(((ViewGroup) v).getChildAt(i));
    }

    @Override
    public void callActivityOnResume(Activity a) {
        super.callActivityOnResume(a);
        screen = a;
        if (!prepared) return;
        a.getWindow().getInsetsController().hide(WindowInsets.Type.systemBars());
        a.getWindow()
                .getDecorView()
                .getViewTreeObserver()
                .addOnPreDrawListener(
                        () -> {
                            hideNumbers(a.getWindow().getDecorView());
                            if (a.getClass().getSimpleName().equals("MainActivity"))
                                try {
                                    for (Object page : (Object[]) field(a, "filterPages"))
                                        if (page != null)
                                            hideNumbers((View) field(page, "content"));
                                } catch (Exception failure) {
                                    throw new RuntimeException(failure);
                                }
                            return true;
                        });
        try {
            if (a.getClass().getSimpleName().equals("MainActivity")) {
                ((Map<String, String>) field(a, "contactNames")).putAll(names);
                call(a, "render", new Class<?>[0]);
                ((TextView) field(a, "status")).setText("Demo messages");
                ((TextView) field(a, "carrierName")).setText("MINT MOBILE");
            }
            if (a.getClass().getSimpleName().equals("RecordingActivity")) {
                MediaPlayer p = (MediaPlayer) field(a, "player");
                if (p != null) p.setVolume(0, 0);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    Activity launch(String page, String id, String caller) throws Exception {
        screen =
                startActivitySync(
                        new Intent()
                                .setClassName(context, "org.visualvoicemail.app." + page)
                                .putExtra("id", id)
                                .putExtra("caller", caller)
                                .putExtra("english", false)
                                .addFlags(
                                        Intent.FLAG_ACTIVITY_NEW_TASK
                                                | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        long until = SystemClock.uptimeMillis() + 8000;
        while (!screen.hasWindowFocus() && SystemClock.uptimeMillis() < until) Thread.sleep(50);
        if (!screen.hasWindowFocus()) throw new IllegalStateException("Demo window needs focus");
        Thread.sleep(600);
        dismissSystemTip();
        return screen;
    }

    void dismissSystemTip() throws Exception {
        android.accessibilityservice.AccessibilityServiceInfo info =
                getUiAutomation().getServiceInfo();
        info.flags |=
                android.accessibilityservice.AccessibilityServiceInfo
                        .FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        getUiAutomation().setServiceInfo(info);
        long until = SystemClock.uptimeMillis() + 1800;
        do {
            for (android.view.accessibility.AccessibilityWindowInfo window :
                    getUiAutomation().getWindows()) {
                android.view.accessibility.AccessibilityNodeInfo root = window.getRoot();
                if (root == null) continue;
                for (android.view.accessibility.AccessibilityNodeInfo node :
                        root.findAccessibilityNodeInfosByText("Got it")) {
                    if (node.isVisibleToUser()) {
                        node.performAction(
                                android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK);
                        Thread.sleep(450);
                        return;
                    }
                }
            }
            Thread.sleep(100);
        } while (SystemClock.uptimeMillis() < until);
    }

    void click(View target) throws Exception {
        ui(() -> target.performClick());
        Thread.sleep(220);
    }

    void type(EditText input, String value) throws Exception {
        ui(() -> input.setText(""));
        for (int i = 1; i <= value.length(); i++) {
            String part = value.substring(0, i);
            ui(() -> input.setText(part));
            Thread.sleep(95);
        }
    }

    void swipe(boolean left) throws Exception {
        View nav = (View) field(screen, "inboxNavigation");
        int[] at = new int[2];
        nav.getLocationOnScreen(at);
        float y = at[1] + Math.min(nav.getHeight() * .58f, nav.getHeight() - 100);
        float from = at[0] + nav.getWidth() * (left ? .84f : .16f),
                to = at[0] + nav.getWidth() * (left ? .16f : .84f);
        long down = SystemClock.uptimeMillis();
        View decor = screen.getWindow().getDecorView();
        for (int i = 0; i <= 20; i++) {
            int action = i == 0 ? MotionEvent.ACTION_DOWN : MotionEvent.ACTION_MOVE;
            float x = from + (to - from) * i / 20f;
            long now = SystemClock.uptimeMillis();
            runOnMainSync(
                    () -> {
                        MotionEvent e = MotionEvent.obtain(down, now, action, x, y, 0);
                        decor.dispatchTouchEvent(e);
                        e.recycle();
                    });
            Thread.sleep(16);
        }
        runOnMainSync(
                () -> {
                    MotionEvent e =
                            MotionEvent.obtain(
                                    down,
                                    SystemClock.uptimeMillis(),
                                    MotionEvent.ACTION_UP,
                                    to,
                                    y,
                                    0);
                    decor.dispatchTouchEvent(e);
                    e.recycle();
                });
        Thread.sleep(700);
    }

    interface Scene {
        void run() throws Exception;
    }

    void record(String name, int seconds, Scene action) throws Exception {
        ui(
                () -> {
                    drawing = true;
                    Choreographer.getInstance().postFrameCallback(drawFrame);
                });
        try {
            if (!captureToken.isEmpty()) {
                long start = SystemClock.uptimeMillis();
                android.util.Log.i(
                        "VoiceMailDemo", captureToken + " START " + name + " " + seconds);
                Thread.sleep(800);
                action.run();
                long remaining = seconds * 1000L - (SystemClock.uptimeMillis() - start);
                if (remaining > 0) Thread.sleep(remaining);
            } else {
                ParcelFileDescriptor fd =
                        getUiAutomation()
                                .executeShellCommand(
                                        "screenrecord --size 720x1560 --bit-rate 12000000"
                                            + " --time-limit "
                                                + seconds
                                                + " /data/local/tmp/voice-mail-demo-"
                                                + name
                                                + ".mp4");
                try (InputStream stream = new ParcelFileDescriptor.AutoCloseInputStream(fd)) {
                    Thread.sleep(800);
                    action.run();
                    byte[] bytes = new byte[1024];
                    while (stream.read(bytes) != -1) {}
                }
            }
        } finally {
            if (!captureToken.isEmpty())
                android.util.Log.i("VoiceMailDemo", captureToken + " END " + name);
            ui(
                    () -> {
                        drawing = false;
                        Choreographer.getInstance().removeFrameCallback(drawFrame);
                    });
        }
        clips++;
        sendStatus(0, new Bundle());
    }

    void capture(String name) throws Exception {
        View decor = screen.getWindow().getDecorView();
        Thread.sleep(300);
        Bitmap image =
                Bitmap.createBitmap(decor.getWidth(), decor.getHeight(), Bitmap.Config.ARGB_8888);
        CountDownLatch latch = new CountDownLatch(1);
        int[] result = {-1};
        ui(
                () ->
                        PixelCopy.request(
                                screen.getWindow(),
                                image,
                                v -> {
                                    result[0] = v;
                                    latch.countDown();
                                },
                                new Handler(Looper.getMainLooper())));
        if (!latch.await(5, TimeUnit.SECONDS) || result[0] != PixelCopy.SUCCESS)
            throw new IOException("Demo screenshot failed");
        try (OutputStream out =
                new FileOutputStream(
                        new File(context.getExternalFilesDir(null), "demo-" + name + ".png"))) {
            image.compress(Bitmap.CompressFormat.PNG, 100, out);
        } finally {
            image.recycle();
        }
    }

    @Override
    public void onCreate(Bundle args) {
        super.onCreate(args);
        playbackOnly = "playback".equals(args.getString("demo_scene", "all"));
        captureToken = args.getString("demo_capture_token", "");
        if (!captureToken.isEmpty() && !captureToken.matches("[a-f0-9]{32}"))
            throw new IllegalArgumentException("Invalid local capture token");
        start();
    }

    @Override
    public void onStart() {
        Bundle out = new Bundle();
        context = getTargetContext();
        SharedPreferences prefs = context.getSharedPreferences("settings", 0);
        Map<String, ?> before = prefs.getAll();
        try {
            if (!Arrays.asList("ranchu", "goldfish").contains(Build.HARDWARE)
                    || new File(context.getFilesDir(), "session.bin").exists())
                throw new IllegalStateException("Demo requires a disconnected emulator");
            Class<?> carrier = Class.forName("org.visualvoicemail.app.CarrierClient"),
                    speech = Class.forName("org.visualvoicemail.app.Transcription");
            Object client = call(carrier, "get", new Class<?>[] {Context.class}, context);
            List<?> inbox = (List<?>) call(client, "cachedInbox", new Class<?>[0]);
            if (inbox.size() != 8)
                throw new IllegalStateException("Demo requires the eight public sample messages");
            for (Object item : inbox)
                if (!((JSONObject) item).optString("objectid").startsWith("emulator-sample-")
                        || !((JSONObject) item)
                                .optString("returnnumber")
                                .matches("\\+1202555010[0-7]"))
                    throw new IllegalStateException("Refusing non-demo messages");
            Class<?> storeClass = Class.forName("org.visualvoicemail.app.PrivateStore");
            Constructor<?> ctor = storeClass.getDeclaredConstructor(Context.class);
            ctor.setAccessible(true);
            store = ctor.newInstance(context);
            JSONArray messages = new JSONArray();
            String[] callers = {
                "Alex Morgan",
                "Jordan Lee",
                "Maya Chen",
                "Sam Rivera",
                "Taylor Brooks",
                "Casey Park",
                "Drew Carter",
                "Riley Bennett"
            };
            String[][] text = {
                {
                    "Hi, the design review is tomorrow at ten.",
                    "Bring the latest prototype. See you there."
                },
                {
                    "Your table is confirmed for Friday evening.",
                    "Call us if you need to change the booking."
                },
                {
                    "The release build is ready to review.",
                    "I left the test notes in the project folder."
                },
                {"The package arrived this morning.", "You can collect it from the front desk."},
                {
                    "Can we move our design meeting to Thursday?",
                    "Send me a time that works for you."
                },
                {
                    "Thanks for the update on the app.",
                    "The new playback screen looks much clearer."
                },
                {"Just checking in about our weekend plans.", "Call me when you have a moment."},
                {"Your appointment is confirmed for Monday.", "Please arrive ten minutes early."}
            };
            for (int i = 0; i < 8; i++) {
                JSONObject m = new JSONObject(inbox.get(i).toString());
                String id = m.getString("objectid");
                if (call(client, "cachedAudio", new Class<?>[] {JSONObject.class}, m) == null)
                    throw new IOException("Public sample audio is missing");
                names.put(m.getString("returnnumber"), callers[i]);
                m.put("date", "2026-10-" + (27 - i) + "T15:30:00Z")
                        .put("flag", i % 2 == 0 ? "" : "Seen");
                if (i == 7) m.put("_saved", true).put("_carrier_removed", true);
                messages.put(m);
                JSONObject transcript =
                        new JSONObject()
                                .put("version", 2)
                                .put("quality", "standard")
                                .put("language", "en")
                                .put(
                                        "segments",
                                        new JSONArray()
                                                .put(
                                                        new JSONObject()
                                                                .put("start", 100)
                                                                .put("end", 2800)
                                                                .put("text", text[i][0]))
                                                .put(
                                                        new JSONObject()
                                                                .put("start", 3000)
                                                                .put("end", 6500)
                                                                .put("text", text[i][1])));
                save((String) call(speech, "key", new Class<?>[] {String.class}, id), transcript);
            }
            save(
                    "inbox.bin",
                    new JSONObject()
                            .put("messages", messages)
                            .put("synced_at", System.currentTimeMillis()));
            prefs.edit()
                    .putInt("appearance", 2)
                    .putBoolean("tutorial_seen", true)
                    .putBoolean("guide_inbox", false)
                    .putBoolean("guide_recording", false)
                    .putBoolean("contact_names", false)
                    .putBoolean("refresh_on_open", false)
                    .putBoolean("auto_transcribe", false)
                    .putBoolean("read_on_play", false)
                    .putBoolean("alerts_enabled", false)
                    .putBoolean("reduce_motion", false)
                    .putString("transcription_quality", "standard")
                    .putString("speech_language", "en")
                    .putBoolean("speech_english", false)
                    .commit();
            prepared = true;
            String first = messages.getJSONObject(0).getString("objectid");
            if (!playbackOnly) {
                launch("MainActivity", first, callers[0]);
                record(
                        "inbox",
                        12,
                        () -> {
                            Thread.sleep(700);
                            swipe(true);
                            Thread.sleep(900);
                            swipe(true);
                            Thread.sleep(900);
                            swipe(false);
                            swipe(false);
                        });
                record(
                        "search",
                        10,
                        () -> {
                            Thread.sleep(600);
                            type((EditText) field(screen, "search"), "design");
                            Thread.sleep(1800);
                        });
            }
            launch("RecordingActivity", first, callers[0]);
            long ready = SystemClock.uptimeMillis() + 5000;
            while (!(Boolean) field(screen, "prepared") && SystemClock.uptimeMillis() < ready)
                Thread.sleep(50);
            MediaPlayer player = (MediaPlayer) field(screen, "player");
            player.setVolume(0, 0);
            record(
                    "playback",
                    12,
                    () -> {
                        List<?> lines = (List<?>) field(screen, "lines");
                        click((View) lines.get(0));
                        Thread.sleep(1800);
                        click((View) lines.get(1));
                        Thread.sleep(1200);
                        invoke("pause");
                        Thread.sleep(2000);
                        click((View) field(screen, "findControl"));
                        type((EditText) field(screen, "search"), "design");
                        EditText input = (EditText) field(screen, "search");
                        View find = (View) field(screen, "findControl");
                        ui(
                                () -> {
                                    input.clearFocus();
                                    find.requestFocus();
                                    screen.getSystemService(
                                                    android.view.inputmethod.InputMethodManager
                                                            .class)
                                            .hideSoftInputFromWindow(input.getWindowToken(), 0);
                                });
                        Thread.sleep(1000);
                    });
            if (!playbackOnly) {
                launch("TranscriptionSettingsActivity", first, callers[0]);
                record(
                        "quality",
                        9,
                        () -> {
                            View[] choices = (View[]) field(screen, "choices");
                            Thread.sleep(650);
                            click(choices[1]);
                            Thread.sleep(850);
                            click(choices[2]);
                            Thread.sleep(1500);
                        });
                prefs.edit().putString("transcription_quality", "standard").commit();
                launch("MainActivity", first, callers[0]);
                record(
                        "selection",
                        10,
                        () -> {
                            LinearLayout list = (LinearLayout) field(screen, "messages");
                            Thread.sleep(500);
                            ui(() -> list.getChildAt(0).performLongClick());
                            Thread.sleep(1000);
                            Map<?, ?> rows = (Map<?, ?>) field(screen, "rows");
                            Object second =
                                    rows.get(messages.getJSONObject(1).getString("objectid"));
                            click((View) field(second, "check"));
                            Thread.sleep(1000);
                            click((View) field(screen, "selectAll"));
                            Thread.sleep(3000);
                            invoke("clearSelection");
                        });
                for (int mode = 1; mode <= 3; mode++) {
                    prefs.edit().putInt("appearance", mode).commit();
                    launch("RecordingActivity", first, callers[0]);
                    capture("theme-" + mode);
                }
            }
            out.putBoolean("passed", true);
            out.putInt("demo_clips", clips);
        } catch (Throwable e) {
            while (e.getCause() != null) e = e.getCause();
            out.putString("failure_class", e.getClass().getSimpleName());
            out.putString("failure", e.getMessage());
        } finally {
            prepared = false;
            if (screen != null)
                try {
                    ui(screen::finish);
                } catch (Exception ignored) {
                }
            if (store != null)
                for (Map.Entry<String, JSONObject> item : backups.entrySet())
                    try {
                        if (item.getValue() != null) {
                            if (!(Boolean)
                                    call(
                                            store,
                                            "put",
                                            new Class<?>[] {String.class, JSONObject.class},
                                            item.getKey(),
                                            item.getValue()))
                                throw new IOException("Sample restoration failed");
                        } else {
                            File file = new File(context.getFilesDir(), item.getKey());
                            if (file.exists() && !file.delete())
                                throw new IOException("Sample cleanup failed");
                        }
                    } catch (Exception failed) {
                        out.putBoolean("passed", false);
                        out.putString("failure", "Demo sample restoration failed");
                    }
            SharedPreferences.Editor restore = prefs.edit();
            for (String key :
                    new String[] {
                        "appearance",
                        "tutorial_seen",
                        "guide_inbox",
                        "guide_recording",
                        "contact_names",
                        "refresh_on_open",
                        "auto_transcribe",
                        "read_on_play",
                        "alerts_enabled",
                        "reduce_motion",
                        "transcription_quality",
                        "speech_language",
                        "speech_english"
                    }) {
                Object v = before.get(key);
                if (v instanceof String) restore.putString(key, (String) v);
                else if (v instanceof Integer) restore.putInt(key, (Integer) v);
                else if (v instanceof Boolean) restore.putBoolean(key, (Boolean) v);
                else restore.remove(key);
            }
            restore.commit();
        }
        finish(-1, out);
    }
}
