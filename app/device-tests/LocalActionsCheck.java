package org.visualvoicemail.checks;

import android.app.*;
import android.content.*;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.*;
import android.view.View;

import org.json.JSONObject;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

/** Emulator-only public audio; verifies cached work and opt-in read updates without a carrier. */
public final class LocalActionsCheck extends DeviceCheckInstrumentation {
    private int checks;
    private Activity screen;

    private Object call(Object target, String name, Class<?>[] types, Object... args)
            throws Exception {
        Method m =
                (target instanceof Class ? (Class<?>) target : target.getClass())
                        .getDeclaredMethod(name, types);
        m.setAccessible(true);
        return m.invoke(target instanceof Class ? null : target, args);
    }

    private Object field(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }

    private void check(boolean good) throws Exception {
        checks++;
        if (!good) throw new Exception("check " + checks);
    }

    private void ui(Runnable work) {
        runOnMainSync(work);
        waitForIdleSync();
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
        CountDownLatch release = new CountDownLatch(1), held = new CountDownLatch(1);
        ExecutorService tasks = Executors.newSingleThreadExecutor();
        Thread blocker = null;
        File source = null, encoded = null, shared = null;
        Object saved = null;
        String id = "emulator-local-actions-" + UUID.randomUUID();
        try {
            check(Arrays.asList("ranchu", "goldfish").contains(Build.HARDWARE));
            check(!new File(c.getFilesDir(), "session.bin").exists());
            prefs.edit()
                    .putBoolean("read_on_play", false)
                    .putBoolean("auto_transcribe", false)
                    .putBoolean("guide_recording", false)
                    .putBoolean("refresh_on_open", false)
                    .commit();
            source = File.createTempFile("public-audio-", ".wav", c.getCacheDir());
            encoded = File.createTempFile("public-audio-", ".amr", c.getCacheDir());
            try (InputStream input = getContext().getAssets().open("speech-fixture.wav");
                    FileOutputStream output = new FileOutputStream(source)) {
                byte[] bytes = new byte[8192];
                int count;
                while ((count = input.read(bytes)) != -1) output.write(bytes, 0, count);
            }
            call(
                    Class.forName("org.visualvoicemail.app.GreetingAudio"),
                    "encode",
                    new Class<?>[] {File.class, File.class},
                    source,
                    encoded);
            JSONObject message =
                    new JSONObject()
                            .put("objectid", id)
                            .put("returnnumber", "+12025550199")
                            .put("flag", "")
                            .put("duration", 7)
                            .put("date", "2026-10-27T12:00:00Z");
            saved =
                    call(
                            Class.forName("org.visualvoicemail.app.SavedMailbox"),
                            "get",
                            new Class<?>[] {Context.class},
                            c);
            call(
                    saved,
                    "saveAndVerify",
                    new Class<?>[] {JSONObject.class, File.class},
                    message,
                    encoded);
            call(saved, "confirmRemoved", new Class<?>[] {String.class}, id);
            final Object client =
                    call(
                            Class.forName("org.visualvoicemail.app.CarrierClient"),
                            "get",
                            new Class<?>[] {Context.class},
                            c);
            blocker =
                    new Thread(
                            () -> {
                                synchronized (client) {
                                    held.countDown();
                                    try {
                                        release.await(30, TimeUnit.SECONDS);
                                    } catch (InterruptedException e) {
                                        Thread.currentThread().interrupt();
                                    }
                                }
                            });
            blocker.start();
            check(held.await(2, TimeUnit.SECONDS));
            Future<File> local =
                    tasks.submit(
                            () ->
                                    (File)
                                            call(
                                                    client,
                                                    "availableAudio",
                                                    new Class<?>[] {JSONObject.class},
                                                    message));
            File audio = local.get(2, TimeUnit.SECONDS);
            check(audio.isFile() && release.getCount() == 1);
            Uri uri =
                    tasks.submit(
                                    () ->
                                            (Uri)
                                                    call(
                                                            Class.forName(
                                                                    "org.visualvoicemail.app.ShareProvider"),
                                                            "prepare",
                                                            new Class<?>[] {
                                                                Context.class,
                                                                File.class,
                                                                String.class
                                                            },
                                                            c,
                                                            audio,
                                                            "2026-10-27"))
                            .get(3, TimeUnit.SECONDS);
            check("audio/amr".equals(c.getContentResolver().getType(uri)));
            try (InputStream input = c.getContentResolver().openInputStream(uri)) {
                check(input.read() == '#');
            }
            shared =
                    new File(
                            new File(c.getCacheDir(), "sharing"),
                            uri.getPathSegments().get(0) + ".amr");
            float[] samples =
                    tasks.submit(
                                    () ->
                                            (float[])
                                                    call(
                                                            Class.forName(
                                                                    "org.visualvoicemail.app.AudioSamples"),
                                                            "read",
                                                            new Class<?>[] {File.class},
                                                            audio))
                            .get(5, TimeUnit.SECONDS);
            check(samples.length > 16000 && release.getCount() == 1);
            screen =
                    startActivitySync(
                            new Intent()
                                    .setClassName(c, "org.visualvoicemail.app.RecordingActivity")
                                    .putExtra("id", id)
                                    .putExtra("caller", "Sample saved caller")
                                    .addFlags(
                                            Intent.FLAG_ACTIVITY_NEW_TASK
                                                    | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            long deadline = SystemClock.uptimeMillis() + 5000;
            while (!(Boolean) field(screen, "prepared") && SystemClock.uptimeMillis() < deadline)
                Thread.sleep(50);
            check((Boolean) field(screen, "prepared"));
            MediaPlayer player = (MediaPlayer) field(screen, "player");
            ui(() -> player.setVolume(0, 0));
            ui(() -> ((View) quietField("play")).performClick());
            Thread.sleep(250);
            check(player.isPlaying());
            check(!(Boolean) field(screen, "autoReadAttempted"));
            check(
                    !(Boolean)
                            call(
                                    saved,
                                    "removed",
                                    new Class<?>[] {String.class},
                                    "missing-sample"));
            ui(() -> ((View) quietField("play")).performClick());
            prefs.edit().putBoolean("read_on_play", true).commit();
            ui(() -> ((View) quietField("play")).performClick());
            Class<?> batch = Class.forName("org.visualvoicemail.app.MailboxBatch");
            Field busy = batch.getDeclaredField("busy");
            busy.setAccessible(true);
            deadline = SystemClock.uptimeMillis() + 4000;
            while (busy.getBoolean(null) && SystemClock.uptimeMillis() < deadline) Thread.sleep(50);
            check(!busy.getBoolean(null));
            check((Boolean) field(screen, "autoReadAttempted"));
            List<?> inbox = (List<?>) call(client, "cachedInbox", new Class<?>[0]);
            boolean read = false;
            for (Object item : inbox)
                if (id.equals(((JSONObject) item).optString("objectid")))
                    read = "Seen".equals(((JSONObject) item).optString("flag"));
            check(read && release.getCount() == 1);
            check(
                    (Integer)
                                    tasks.submit(
                                                    () ->
                                                            call(
                                                                    client,
                                                                    "setReadBatch",
                                                                    new Class<?>[] {
                                                                        List.class, boolean.class
                                                                    },
                                                                    Collections.singletonList(id),
                                                                    false))
                                            .get(2, TimeUnit.SECONDS)
                            == 1);
            ui(() -> ((View) quietField("play")).performClick());
            ui(() -> ((View) quietField("play")).performClick());
            Thread.sleep(150);
            boolean manualUnread = false;
            for (Object item : (List<?>) call(client, "cachedInbox", new Class<?>[0]))
                if (id.equals(((JSONObject)item).optString("objectid")))
                    manualUnread = !"Seen".equals(((JSONObject)item).optString("flag"));
            check(manualUnread && !busy.getBoolean(null));
            ui(() -> screen.finish()); screen = null;
            release.countDown();
            boolean missingKept = false;
            try { call(client, "availableAudio", new Class<?>[]{JSONObject.class},
                    new JSONObject().put("objectid", "missing-saved-fixture").put("_carrier_removed", true)); }
            catch (InvocationTargetException expected) { missingKept = expected.getCause() instanceof IOException; }
            check(missingKept);
            out.putBoolean("passed", true);
        } catch (Throwable error) {
            while (error.getCause() != null) error = error.getCause();
            out.putString("failure_class", error.getClass().getSimpleName());
            out.putString(
                    "failure",
                    error instanceof Exception
                                    && error.getMessage() != null
                                    && error.getMessage().startsWith("check ")
                            ? error.getMessage()
                            : "local action failure");
        } finally {
            release.countDown();
            tasks.shutdownNow();
            if (screen != null) ui(() -> screen.finish());
            if (saved != null)
                try {
                    call(saved, "removeLocal", new Class<?>[] {String.class}, id);
                } catch (Exception ignored) {
                }
            for (File file : new File[] {source, encoded, shared}) if (file != null) file.delete();
            SharedPreferences.Editor restore = prefs.edit();
            for (String key :
                    new String[] {
                        "read_on_play", "auto_transcribe", "guide_recording", "refresh_on_open"
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

    private Object quietField(String name) {
        try {
            return field(screen, name);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
