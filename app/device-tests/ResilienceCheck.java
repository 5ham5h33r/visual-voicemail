package org.visualvoicemail.checks;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.media.*;
import android.net.Uri;
import android.os.*;
import android.provider.DocumentsContract;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.*;
import java.lang.reflect.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;

/**
 * Emulator-only public fixtures: real SAF export, failed storage, focus loss, recreation and call.
 */
public final class ResilienceCheck extends DeviceCheckInstrumentation {
    private int checks;
    private Activity screen;
    private boolean expectCall;

    private Object field(Object o, String n) throws Exception {
        Field f = o.getClass().getDeclaredField(n);
        f.setAccessible(true);
        return f.get(o);
    }

    private Object call(Object o, String n, Class<?>[] types, Object... args) throws Exception {
        Class<?> type = o instanceof Class ? (Class<?>) o : o.getClass();
        Method m = type.getDeclaredMethod(n, types);
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

    private void invoke(String name, Class<?>[] types, Object... args) {
        ui(
                () -> {
                    try {
                        call(screen, name, types, args);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    private void grant(Context c, String mode) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        c.sendOrderedBroadcast(
                new Intent()
                        .setClassName(
                                "org.visualvoicemail.checks",
                                "org.visualvoicemail.checks.ExportGrantReceiver")
                        .putExtra("mode", mode)
                        .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES),
                null,
                new BroadcastReceiver() {
                    public void onReceive(Context context, Intent intent) {
                        done.countDown();
                    }
                },
                new Handler(Looper.getMainLooper()),
                0,
                null,
                null);
        check(done.await(5, TimeUnit.SECONDS));
        if (!mode.equals("read"))
            c.getContentResolver()
                    .takePersistableUriPermission(
                            tree(),
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
    }

    private Uri tree() {
        return DocumentsContract.buildTreeDocumentUri(ExportFixtureProvider.AUTHORITY, "root");
    }

    private List<String> documents(Context c) throws Exception {
        List<String> ids = new ArrayList<>();
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree(), "root");
        try (Cursor rows =
                c.getContentResolver()
                        .query(
                                children,
                                new String[] {DocumentsContract.Document.COLUMN_DOCUMENT_ID},
                                null,
                                null,
                                null)) {
            while (rows.moveToNext()) ids.add(rows.getString(0));
        }
        return ids;
    }

    private byte[] bytes(InputStream source) throws Exception {
        try (InputStream input = source;
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] b = new byte[8192];
            int n;
            while ((n = input.read(b)) != -1) output.write(b, 0, n);
            return output.toByteArray();
        }
    }

    private void export(JSONObject message) throws Exception {
        invoke("saveCopy", new Class<?>[] {JSONObject.class}, message);
        long end = SystemClock.uptimeMillis() + 20000;
        while ((Boolean) field(screen, "savingCopy") && SystemClock.uptimeMillis() < end)
            Thread.sleep(50);
        waitForIdleSync();
        check(!(Boolean) field(screen, "savingCopy"));
    }

    private void prepared() throws Exception {
        long end = SystemClock.uptimeMillis() + 8000;
        while (!(Boolean) field(screen, "prepared") && SystemClock.uptimeMillis() < end)
            Thread.sleep(50);
        check((Boolean) field(screen, "prepared"));
    }

    private Activity launch(Context c, String name, String id) {
        return startActivitySync(
                new Intent()
                        .setClassName(c, "org.visualvoicemail.app." + name)
                        .putExtra("id", id)
                        .putExtra("caller", "Sample caller")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
    }

    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);
        expectCall = "true".equals(b.getString("simulated_call"));
        start();
    }

    @Override
    public void onStart() {
        Bundle out = new Bundle();
        Context c = getTargetContext();
        SharedPreferences prefs = c.getSharedPreferences("settings", 0);
        Map<String, ?> before = prefs.getAll();
        File work = new File(c.getCacheDir(), "resilience-" + UUID.randomUUID()), cached = null;
        byte[] original = null;
        CountDownLatch release = new CountDownLatch(1),
                carrierHeld = new CountDownLatch(1),
                queueHeld = new CountDownLatch(1);
        Thread carrierThread = null;
        File callMarker = new File(c.getCacheDir(), "resilience-ready-for-call");
        callMarker.delete();
        try {
            check(Arrays.asList("ranchu", "goldfish").contains(Build.HARDWARE));
            check(!new File(c.getFilesDir(), "session.bin").exists());
            work.mkdirs();
            prefs.edit()
                    .putBoolean("refresh_on_open", false)
                    .putBoolean("auto_transcribe", false)
                    .putBoolean("read_on_play", false)
                    .putBoolean("guide_recording", false)
                    .putBoolean("contact_names", false)
                    .commit();
            Class<?> carrier = Class.forName("org.visualvoicemail.app.CarrierClient"),
                    folder = Class.forName("org.visualvoicemail.app.DownloadFolder"),
                    audio = Class.forName("org.visualvoicemail.app.GreetingAudio");
            Object client = call(carrier, "get", new Class<?>[] {Context.class}, c);
            List<?> inbox = (List<?>) call(client, "cachedInbox", new Class<?>[0]);
            for (Object item : inbox)
                check(((JSONObject) item).optString("objectid").startsWith("emulator-sample-"));
            JSONObject message = (JSONObject) inbox.get(0);
            String id = message.getString("objectid");
            cached = (File) call(client, "cachedAudio", new Class<?>[] {JSONObject.class}, message);
            check(
                    cached.getCanonicalPath()
                            .startsWith(
                                    new File(c.getFilesDir(), "audio").getCanonicalPath()
                                            + File.separator));
            original = Files.readAllBytes(cached.toPath());
            File wave = new File(work, "public.wav"),
                    encoded = new File(work, "public.amr"),
                    longAudio = new File(work, "long-public.amr");
            Files.write(wave.toPath(), bytes(getContext().getAssets().open("speech-fixture.wav")));
            call(audio, "encode", new Class<?>[] {File.class, File.class}, wave, encoded);
            byte[] amr = Files.readAllBytes(encoded.toPath());
            try (OutputStream output = new FileOutputStream(longAudio)) {
                output.write(amr, 0, 6);
                for (int i = 0; i < 40; i++) output.write(amr, 6, amr.length - 6);
            }
            check(longAudio.length() > 65536);
            Files.copy(
                    longAudio.toPath(),
                    cached.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            grant(c, "normal");
            prefs.edit().putString("download_folder", tree().toString()).commit();
            screen = launch(c, "MainActivity", id);
            Thread.sleep(350);
            carrierThread =
                    new Thread(
                            () -> {
                                synchronized (client) {
                                    carrierHeld.countDown();
                                    try {
                                        release.await(90, TimeUnit.SECONDS);
                                    } catch (InterruptedException e) {
                                        Thread.currentThread().interrupt();
                                    }
                                }
                            });
            carrierThread.start();
            check(carrierHeld.await(2, TimeUnit.SECONDS));
            ((ExecutorService) field(screen, "worker"))
                    .execute(
                            () -> {
                                queueHeld.countDown();
                                try {
                                    release.await(90, TimeUnit.SECONDS);
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                }
                            });
            check(queueHeld.await(2, TimeUnit.SECONDS));
            export(message);
            out.putString(
                    "export_status", ((TextView) field(screen, "status")).getText().toString());
            check(
                    ((TextView) field(screen, "status"))
                            .getText()
                            .toString()
                            .startsWith("Audio exported"));
            check(carrierThread.isAlive() && release.getCount() == 1);
            List<String> successful = documents(c);
            check(successful.size() == 1);
            Uri kept = DocumentsContract.buildDocumentUriUsingTree(tree(), successful.get(0));
            check(
                    Arrays.equals(
                            Files.readAllBytes(longAudio.toPath()),
                            bytes(c.getContentResolver().openInputStream(kept))));
            grant(c, "full");
            export(message);
            check(((TextView) field(screen, "status")).getText().toString().contains("free space"));
            check(documents(c).equals(successful));
            check(cached.length() == longAudio.length());
            c.getContentResolver()
                    .releasePersistableUriPermission(
                            tree(),
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            grant(c, "read");
            check(
                    !(Boolean)
                            call(
                                    folder,
                                    "accessible",
                                    new Class<?>[] {Context.class, Uri.class},
                                    c,
                                    tree()));
            boolean denied = false;
            try {
                call(
                        folder,
                        "save",
                        new Class<?>[] {Context.class, Uri.class, File.class, String.class},
                        c,
                        tree(),
                        longAudio,
                        "2026-10-27");
            } catch (InvocationTargetException expected) {
                denied = expected.getCause() instanceof IOException;
            }
            check(denied && documents(c).equals(successful));
            // Inject an AtomicFile write failure without consuming device storage or touching the
            // inbox.
            Context isolated =
                    new ContextWrapper(c) {
                        @Override
                        public Context getApplicationContext() {
                            return this;
                        }

                        @Override
                        public File getFilesDir() {
                            return work;
                        }
                    };
            Class<?> store = Class.forName("org.visualvoicemail.app.PrivateStore");
            Constructor<?> ctor = store.getDeclaredConstructor(Context.class);
            ctor.setAccessible(true);
            Object privateStore = ctor.newInstance(isolated);
            check(
                    (Boolean)
                            call(
                                    privateStore,
                                    "put",
                                    new Class<?>[] {String.class, JSONObject.class},
                                    "state.bin",
                                    new JSONObject().put("value", "old")));
            File blocked = new File(work, "state.bin.new");
            blocked.mkdir();
            Files.write(new File(blocked, "keep").toPath(), new byte[] {1});
            check(
                    !(Boolean)
                            call(
                                    privateStore,
                                    "put",
                                    new Class<?>[] {String.class, JSONObject.class},
                                    "state.bin",
                                    new JSONObject().put("value", "new")));
            check(
                    "old"
                            .equals(
                                    ((JSONObject)
                                                    call(
                                                            privateStore,
                                                            "get",
                                                            new Class<?>[] {String.class},
                                                            "state.bin"))
                                            .getString("value")));
            release.countDown();
            carrierThread.join(1000);
            screen = launch(c, "RecordingActivity", id);
            prepared();
            MediaPlayer player = (MediaPlayer) field(screen, "player");
            ui(() -> player.setVolume(0, 0));
            invoke("start", new Class<?>[0]);
            Thread.sleep(150);
            check(player.isPlaying());
            AudioManager manager = c.getSystemService(AudioManager.class);
            AudioFocusRequest interruption =
                    new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                            .setAudioAttributes(
                                    new AudioAttributes.Builder()
                                            .setUsage(AudioAttributes.USAGE_MEDIA)
                                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                            .build())
                            .setOnAudioFocusChangeListener(change -> {})
                            .build();
            check(
                    manager.requestAudioFocus(interruption)
                            == AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
            Thread.sleep(300);
            check(!player.isPlaying());
            int position = player.getCurrentPosition();
            manager.abandonAudioFocusRequest(interruption);
            Thread.sleep(100);
            check(!player.isPlaying());
            ActivityMonitor recreated =
                    addMonitor("org.visualvoicemail.app.RecordingActivity", null, false);
            ui(() -> screen.recreate());
            screen = waitForMonitorWithTimeout(recreated, 6000);
            check(screen != null);
            prepared();
            MediaPlayer restored = (MediaPlayer) field(screen, "player");
            check(!restored.isPlaying());
            Thread.sleep(250);
            check(Math.abs(restored.getCurrentPosition() - position) < 600);
            if (expectCall) {
                ui(() -> restored.setVolume(0, 0));
                invoke("start", new Class<?>[0]);
                Thread.sleep(100);
                check(restored.isPlaying());
                Files.write(callMarker.toPath(), new byte[] {1});
                long end = SystemClock.uptimeMillis() + 25000;
                while (restored.isPlaying() && SystemClock.uptimeMillis() < end) Thread.sleep(100);
                check(
                        !restored.isPlaying()
                                && restored.getCurrentPosition() < restored.getDuration() - 1000);
                out.putBoolean("simulated_call_paused_playback", true);
            }
            out.putBoolean("passed", true);
        } catch (Throwable error) {
            while (error.getCause() != null) error = error.getCause();
            out.putString("failure_class", error.getClass().getSimpleName());
            out.putString(
                    "failure",
                    error.getMessage() != null && error.getMessage().startsWith("check ")
                            ? error.getMessage()
                            : "resilience check failed");
        } finally {
            release.countDown();
            callMarker.delete();
            if (screen != null) ui(() -> screen.finish());
            if (cached != null && original != null)
                try {
                    Files.write(cached.toPath(), original);
                    if (!Arrays.equals(original, Files.readAllBytes(cached.toPath())))
                        throw new IOException();
                } catch (Exception failed) {
                    out.putBoolean("passed", false);
                    out.putString("failure", "Sample audio restoration failed");
                }
            try {
                c.getContentResolver()
                        .releasePersistableUriPermission(
                                tree(),
                                Intent.FLAG_GRANT_READ_URI_PERMISSION
                                        | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            } catch (SecurityException ignored) {
            }
            File[] files = work.listFiles();
            if (files != null)
                for (File file : files) {
                    if (file.isDirectory()) {
                        File[] nested = file.listFiles();
                        if (nested != null) for (File child : nested) child.delete();
                    }
                    file.delete();
                }
            work.delete();
            SharedPreferences.Editor restore = prefs.edit();
            for (String key :
                    new String[] {
                        "refresh_on_open",
                        "auto_transcribe",
                        "read_on_play",
                        "guide_recording",
                        "contact_names",
                        "download_folder"
                    }) {
                Object v = before.get(key);
                if (v instanceof Boolean) restore.putBoolean(key, (Boolean) v);
                else if (v instanceof String) restore.putString(key, (String) v);
                else restore.remove(key);
            }
            restore.commit();
        }
        out.putInt("checks", checks);
        finish(-1, out);
    }
}
