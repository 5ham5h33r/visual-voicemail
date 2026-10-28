package org.visualvoicemail.checks;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.media.MediaPlayer;
import android.os.*;
import android.view.*;
import android.widget.*;

import org.json.JSONObject;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Public emulator inbox only: quick start, connection guidance, search, and header alternatives.
 */
public final class FirstUseDiscoveryCheck extends DeviceCheckInstrumentation {
    private int checks;
    private Activity screen;

    private Object field(Object o, String name) throws Exception {
        Field f = o.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(o);
    }

    private Object call(Object o, String name, Class<?>[] types, Object... args) throws Exception {
        Class<?> type = o instanceof Class ? (Class<?>) o : o.getClass();
        Method m = type.getDeclaredMethod(name, types);
        m.setAccessible(true);
        return m.invoke(o instanceof Class ? null : o, args);
    }

    private void check(boolean good) throws Exception {
        checks++;
        if (!good) throw new Exception("check " + checks);
    }

    private void ui(Runnable work) {
        runOnMainSync(work);
        waitForIdleSync();
    }

    private View find(View root, String text) {
        if (root instanceof TextView && ((TextView) root).getText().toString().startsWith(text))
            return root;
        if (root instanceof ViewGroup)
            for (int i = 0; i < ((ViewGroup) root).getChildCount(); i++) {
                View found = find(((ViewGroup) root).getChildAt(i), text);
                if (found != null) return found;
            }
        return null;
    }

    private Activity launch(Context c, String name, String id) {
        return startActivitySync(
                new Intent()
                        .setClassName(c, "org.visualvoicemail.app." + name)
                        .putExtra("id", id)
                        .putExtra("caller", "Sample caller")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
    }

    private void capture(String name) throws Exception {
        View decor = screen.getWindow().getDecorView();
        CountDownLatch done = new CountDownLatch(1);
        int[] status = {-1};
        Bitmap image =
                Bitmap.createBitmap(decor.getWidth(), decor.getHeight(), Bitmap.Config.ARGB_8888);
        ui(
                () ->
                        PixelCopy.request(
                                screen.getWindow(),
                                image,
                                value -> {
                                    status[0] = value;
                                    done.countDown();
                                },
                                new Handler(Looper.getMainLooper())));
        check(done.await(5, TimeUnit.SECONDS) && status[0] == PixelCopy.SUCCESS);
        try (OutputStream output =
                new FileOutputStream(new File(getTargetContext().getCacheDir(), name + ".png"))) {
            image.compress(Bitmap.CompressFormat.PNG, 100, output);
        } finally {
            image.recycle();
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
        Object store = null;
        JSONObject originalInbox = null;
        try {
            check(Arrays.asList("ranchu", "goldfish").contains(Build.HARDWARE));
            check(!new File(c.getFilesDir(), "session.bin").exists());
            Class<?> carrier = Class.forName("org.visualvoicemail.app.CarrierClient"),
                    speech = Class.forName("org.visualvoicemail.app.Transcription");
            Object client = call(carrier, "get", new Class<?>[] {Context.class}, c);
            List<?> inbox = (List<?>) call(client, "cachedInbox", new Class<?>[0]);
            check(!inbox.isEmpty());
            for (Object item : inbox)
                check(((JSONObject) item).optString("objectid").startsWith("emulator-sample-"));
            String id = ((JSONObject) inbox.get(0)).getString("objectid");
            Class<?> privateStore = Class.forName("org.visualvoicemail.app.PrivateStore");
            Constructor<?> ctor = privateStore.getDeclaredConstructor(Context.class);
            ctor.setAccessible(true);
            store = ctor.newInstance(c);
            originalInbox =
                    (JSONObject) call(store, "get", new Class<?>[] {String.class}, "inbox.bin");
            check(
                    (Boolean)
                            call(
                                    store,
                                    "put",
                                    new Class<?>[] {String.class, JSONObject.class},
                                    "inbox.bin",
                                    new JSONObject().put("messages", new org.json.JSONArray())));
            prefs.edit()
                    .putBoolean("tutorial_seen", false)
                    .putInt("tutorial_page", 0)
                    .putBoolean("auto_transcribe", false)
                    .putBoolean("refresh_on_open", false)
                    .putBoolean("reduce_motion", true)
                    .commit();
            int contactsBefore = c.checkSelfPermission(android.Manifest.permission.READ_CONTACTS);
            screen = launch(c, "TutorialActivity", id);
            Thread.sleep(300);
            View start = find(screen.getWindow().getDecorView(), "Get Started");
            check(start != null);
            check(find(screen.getWindow().getDecorView(), "Show me around") != null);
            ActivityMonitor main = addMonitor("org.visualvoicemail.app.MainActivity", null, false);
            ui(() -> start.performClick());
            screen = waitForMonitorWithTimeout(main, 5000);
            check(screen != null);
            Thread.sleep(400);
            check(
                    prefs.getBoolean("tutorial_seen", false)
                            && !prefs.getBoolean("guide_recording", true));
            check(
                    c.checkSelfPermission(android.Manifest.permission.READ_CONTACTS)
                            == contactsBefore);
            check(find(screen.getWindow().getDecorView(), "Connect mailbox") != null);
            check(find(screen.getWindow().getDecorView(), "Choose Mint for mobile data") != null);
            Class<?> help = Class.forName("org.visualvoicemail.app.ConnectionHelp");
            check(
                    ((String)
                                    call(
                                            help,
                                            "reason",
                                            new Class<?>[] {boolean.class, boolean.class},
                                            true,
                                            false))
                            .contains("Airplane"));
            check(
                    ((String)
                                    call(
                                            help,
                                            "reason",
                                            new Class<?>[] {boolean.class, boolean.class},
                                            false,
                                            true))
                            .contains("SIM"));
            check(
                    call(
                                    help,
                                    "reason",
                                    new Class<?>[] {boolean.class, boolean.class},
                                    false,
                                    false)
                            == null);
            check(
                    (Boolean)
                            call(
                                    store,
                                    "put",
                                    new Class<?>[] {String.class, JSONObject.class},
                                    "inbox.bin",
                                    originalInbox));
            screen = launch(c, "RecordingActivity", id);
            Thread.sleep(700);
            View searchControl = (View) field(screen, "findControl");
            check(searchControl.getContentDescription().toString().equals("Search transcript"));
            ui(() -> searchControl.performClick());
            Thread.sleep(200);
            check(((View) field(screen, "findBox")).getVisibility() == View.VISIBLE);
            check(((View) field(screen, "clearFind")).getVisibility() == View.GONE);
            EditText search = (EditText) field(screen, "search");
            ui(() -> search.setText("project"));
            check(!((List<?>) field(screen, "matches")).isEmpty());
            check(!((MediaPlayer) field(screen, "player")).isPlaying());
            ui(
                    () -> {
                        try {
                            call(screen, "closeFind", new Class<?>[0]);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
            check(
                    ((View) field(screen, "findBox")).getVisibility() == View.GONE
                            && !screen.isFinishing());
            Button quality = (Button) field(screen, "qualityControl");
            LinearLayout heading = (LinearLayout) quality.getParent();
            TextView title = (TextView) field(screen, "transcriptHeading");
            int inline = heading.getHeight();
            for (String option : new String[] {"standard", "enhanced", "highest"}) {
                call(speech, "select", new Class<?>[] {Context.class, String.class}, c, option);
                ui(
                        () -> {
                            try {
                                call(screen, "updateSpeech", new Class<?>[0]);
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        });
                Thread.sleep(100);
                check(title.getLayout().getLineCount() == 1);
                check(
                        quality.getLeft() >= title.getRight()
                                && searchControl.getLeft() >= quality.getRight());
                check(heading.getHeight() == inline);
            }
            call(speech, "select", new Class<?>[] {Context.class, String.class}, c, "standard");
            ui(
                    () -> {
                        try {
                            call(screen, "updateSpeech", new Class<?>[0]);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
            capture("transcript-header-inline");
            LinearLayout body = (LinearLayout) heading.getParent();
            int at = body.indexOfChild(heading) + 1;
            ui(
                    () -> {
                        heading.removeView(quality);
                        body.addView(quality, at, new LinearLayout.LayoutParams(-2, -2));
                    });
            Thread.sleep(120);
            check(heading.getHeight() + quality.getHeight() > inline);
            capture("transcript-header-stacked");
            ui(
                    () -> {
                        body.removeView(quality);
                        heading.addView(
                                quality,
                                1,
                                new LinearLayout.LayoutParams(
                                        -2,
                                        (int) (48 * c.getResources().getDisplayMetrics().density)));
                    });
            out.putBoolean("passed", true);
        } catch (Throwable error) {
            while (error.getCause() != null) error = error.getCause();
            out.putString("failure_class", error.getClass().getSimpleName());
            out.putString(
                    "failure",
                    error.getMessage() != null && error.getMessage().startsWith("check ")
                            ? error.getMessage()
                            : "first-use check failed");
        } finally {
            if (screen != null) ui(() -> screen.finish());
            if (store != null && originalInbox != null)
                try {
                    call(
                            store,
                            "put",
                            new Class<?>[] {String.class, JSONObject.class},
                            "inbox.bin",
                            originalInbox);
                } catch (Exception ignored) {
                }
            SharedPreferences.Editor restore = prefs.edit();
            for (String key :
                    new String[] {
                        "tutorial_seen",
                        "tutorial_page",
                        "guide_recording",
                        "auto_transcribe",
                        "refresh_on_open",
                        "reduce_motion",
                        "transcription_quality"
                    }) {
                Object value = before.get(key);
                if (value instanceof Boolean) restore.putBoolean(key, (Boolean) value);
                else if (value instanceof Integer) restore.putInt(key, (Integer) value);
                else if (value instanceof String) restore.putString(key, (String) value);
                else restore.remove(key);
            }
            restore.commit();
        }
        out.putInt("checks", checks);
        finish(-1, out);
    }
}
