package org.visualvoicemail.checks;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.os.*;
import android.view.*;
import android.widget.*;

import org.json.*;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;

/**
 * Public audio in isolated draft storage; live carrier access is read-only and Save is cancelled.
 */
public final class GreetingFlowCheck extends DeviceCheckInstrumentation {
    private int checks;
    private Activity screen;

    private Method method(Class<?> cls, String name, Class<?>... types) throws Exception {
        Method m = cls.getDeclaredMethod(name, types);
        m.setAccessible(true);
        return m;
    }

    private Object field(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }

    private void set(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    private void check(boolean value) throws Exception {
        checks++;
        if (!value) throw new Exception("check " + checks);
    }

    private void ui(Runnable action) {
        runOnMainSync(action);
        waitForIdleSync();
    }

    private void remove(File file) {
        File[] files = file.listFiles();
        if (files != null) for (File child : files) remove(child);
        file.delete();
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
        File root = new File(c.getFilesDir(), "greeting-check-" + UUID.randomUUID());
        File files = new File(root, "files"), cache = new File(root, "cache");
        files.mkdirs();
        cache.mkdirs();
        Context isolated =
                new ContextWrapper(c) {
                    @Override
                    public Context getApplicationContext() {
                        return this;
                    }

                    @Override
                    public File getFilesDir() {
                        return files;
                    }

                    @Override
                    public File getCacheDir() {
                        return cache;
                    }
                };
        MediaPlayer player = null;
        try {
            Class<?> audio = Class.forName("org.visualvoicemail.app.GreetingAudio");
            File converted =
                    (File)
                            method(audio, "importAudio", Context.class, InputStream.class)
                                    .invoke(
                                            null,
                                            isolated,
                                            getContext().getAssets().open("speech-fixture.wav"));
            check(converted.isFile() && converted.length() > 6);
            Object format =
                    method(
                                    Class.forName("org.visualvoicemail.app.ExportAudioFormat"),
                                    "detect",
                                    File.class)
                            .invoke(null, converted);
            check("amr".equals(format.getClass().getField("extension").get(format)));
            int duration = (Integer) method(audio, "duration", File.class).invoke(null, converted);
            check(duration >= 1000 && duration <= 60000);
            String hash = (String) method(audio, "hash", File.class).invoke(null, converted);
            check(
                    converted.equals(
                            method(audio, "selected", Context.class).invoke(null, isolated)));
            JSONObject meta =
                    (JSONObject) method(audio, "metadata", Context.class).invoke(null, isolated);
            check(hash.equals(meta.getString("hash")));
            player = new MediaPlayer();
            player.setVolume(0, 0);
            player.setDataSource(converted.getAbsolutePath());
            player.prepare();
            check(Math.abs(player.getDuration() - duration) < 200);
            player.start();
            Thread.sleep(250);
            check(player.isPlaying());
            player.release();
            player = null;
            float[] decoded =
                    (float[])
                            method(
                                            Class.forName("org.visualvoicemail.app.AudioSamples"),
                                            "read",
                                            File.class)
                                    .invoke(null, converted);
            double power = 0;
            for (float value : decoded) power += value * value;
            check(decoded.length > 16000 && power / decoded.length > 1e-7);
            File snapshot =
                    (File)
                            method(audio, "snapshot", Context.class, File.class, String.class)
                                    .invoke(null, isolated, converted, hash);
            check(hash.equals(method(audio, "hash", File.class).invoke(null, snapshot)));
            snapshot.delete();
            boolean blocked = false;
            try {
                method(audio, "snapshot", Context.class, File.class, String.class)
                        .invoke(null, isolated, converted, "wrong hash");
            } catch (Exception expected) {
                blocked = true;
            }
            check(blocked && converted.exists());
            blocked = false;
            try {
                method(audio, "importAudio", Context.class, InputStream.class)
                        .invoke(null, isolated, new ByteArrayInputStream(new byte[] {1, 2, 3}));
            } catch (Exception expected) {
                blocked = true;
            }
            check(
                    blocked
                            && converted.equals(
                                    method(audio, "selected", Context.class)
                                            .invoke(null, isolated)));
            Class<?> multipart = Class.forName("org.visualvoicemail.app.GreetingMultipart");
            Constructor<?> constructor =
                    multipart.getDeclaredConstructor(File.class, int.class, String.class);
            constructor.setAccessible(true);
            Object payload =
                    constructor.newInstance(converted, (duration + 999) / 1000, "Public greeting");
            String body =
                    new String(
                            (byte[]) field(payload, "bytes"),
                            java.nio.charset.StandardCharsets.ISO_8859_1);
            check(
                    body.contains("name=\"voicemailgreeting\"; filename=\"greeting.amr\"")
                            && body.contains("Content-Type: audio/amr"));
            check(body.contains("name=\"flags\"\r\n\r\n\r\n") && !body.contains("CNS-Greeting-On"));
            check(body.contains("name=\"type\"\r\n\r\npersonal"));
            for (int seconds : new int[] {0, 61}) {
                blocked = false;
                try {
                    constructor.newInstance(converted, seconds, "Public greeting");
                } catch (Exception expected) {
                    blocked = true;
                }
                check(blocked);
            }
            blocked = false;
            try {
                constructor.newInstance(converted, 2, "bad\r\ntitle");
            } catch (Exception expected) {
                blocked = true;
            }
            check(blocked);
            screen =
                    startActivitySync(
                            new Intent()
                                    .setClassName(c, "org.visualvoicemail.app.GreetingActivity")
                                    .addFlags(
                                            Intent.FLAG_ACTIVITY_NEW_TASK
                                                    | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            long wait = SystemClock.elapsedRealtime() + 60000;
            while (((Boolean) field(screen, "working") || (Boolean) field(screen, "checking"))
                    && SystemClock.elapsedRealtime() < wait) Thread.sleep(100);
            check(!(Boolean) field(screen, "working") && !(Boolean) field(screen, "checking"));
            ui(
                    () -> {
                        try {
                            set(screen, "checking", true);
                            method(screen.getClass(), "update").invoke(screen);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
            check(
                    ((View) field(screen, "record")).isEnabled()
                            && ((View) field(screen, "choose")).isEnabled());
            check(!((View) field(screen, "refresh")).isEnabled());
            ui(
                    () -> {
                        try {
                            set(screen, "checking", false);
                            method(screen.getClass(), "update").invoke(screen);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
            ui(
                    () -> {
                        try {
                            set(screen, "draft", converted);
                            set(screen, "draftHash", hash);
                            set(screen, "draftDuration", duration);
                            set(screen, "previewedHash", "");
                            method(screen.getClass(), "update").invoke(screen);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
            Button save = (Button) field(screen, "save");
            check(!save.isEnabled());
            ui(
                    () -> {
                        try {
                            method(
                                            screen.getClass(),
                                            "play",
                                            File.class,
                                            boolean.class,
                                            String.class)
                                    .invoke(screen, converted, true, hash);
                            ((MediaPlayer) field(screen, "player")).setVolume(0, 0);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
            wait = SystemClock.elapsedRealtime() + 5000;
            while (!hash.equals(field(screen, "previewedHash"))
                    && SystemClock.elapsedRealtime() < wait) Thread.sleep(50);
            check(hash.equals(field(screen, "previewedHash")));
            check(save.isEnabled());
            ui(
                    () -> {
                        save.performClick();
                        try {
                            ((AlertDialog) field(screen, "confirmation"))
                                    .getButton(-1)
                                    .setEnabled(false);
                        } catch (Exception error) {
                            throw new RuntimeException(error);
                        }
                    });
            AlertDialog confirm = (AlertDialog) field(screen, "confirmation");
            check(confirm.isShowing());
            check("Save greeting".contentEquals(confirm.getButton(-1).getText()));
            ui(() -> confirm.getButton(-2).performClick());
            Class<?> jobs = Class.forName("org.visualvoicemail.app.GreetingJobs");
            Field busy = jobs.getDeclaredField("busy");
            busy.setAccessible(true);
            check(!(Boolean) busy.get(null));
            IntentFilter picking = new IntentFilter(Intent.ACTION_OPEN_DOCUMENT);
            picking.addDataType("audio/*");
            picking.addCategory(Intent.CATEGORY_OPENABLE);
            ActivityMonitor picker =
                    addMonitor(picking, new ActivityResult(Activity.RESULT_CANCELED, null), true);
            Button choose = (Button) field(screen, "choose");
            ui(() -> choose.performClick());
            check(picker.getHits() == 1);
            removeMonitor(picker);
            check(converted.equals(field(screen, "draft")) && converted.exists());
            ui(
                    () ->
                            screen.onRequestPermissionsResult(
                                    51,
                                    new String[] {android.Manifest.permission.RECORD_AUDIO},
                                    new int[] {PackageManager.PERMISSION_DENIED}));
            check(((TextView) field(screen, "status")).getText().toString().contains("optional"));
            check(choose.isEnabled());
            Bundle state = new Bundle();
            ui(
                    () -> {
                        try {
                            method(screen.getClass(), "onSaveInstanceState", Bundle.class)
                                    .invoke(screen, state);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
            check(hash.equals(state.getString("previewed_hash")));
            IntentFilter calling = new IntentFilter(Intent.ACTION_DIAL);
            calling.addDataScheme("voicemail");
            ActivityMonitor dialer = addMonitor(calling, new ActivityResult(0, null), true);
            ui(
                    () -> {
                        try {
                            method(screen.getClass(), "callVoicemail").invoke(screen);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
            check(dialer.getHits() == 1);
            removeMonitor(dialer);
            ActivityMonitor settings =
                    addMonitor("org.visualvoicemail.app.SettingsActivity", null, false);
            ui(
                    () -> {
                        try {
                            method(screen.getClass().getSuperclass(), "navigateUp").invoke(screen);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
            Activity parent = waitForMonitorWithTimeout(settings, 5000);
            removeMonitor(settings);
            check(parent != null);
            if (parent != null) ui(() -> parent.finish());
            screen = null;
            method(audio, "discard", Context.class).invoke(null, isolated);
            check(
                    !converted.exists()
                            && method(audio, "selected", Context.class).invoke(null, isolated)
                                    == null);
            out.putBoolean("passed", true);
            out.putInt("public_audio_duration_ms", duration);
        } catch (Throwable error) {
            while (error.getCause() != null) error = error.getCause();
            out.putString("failure_class", error.getClass().getSimpleName());
            out.putString("failure", error.getMessage());
        } finally {
            if (player != null) player.release();
            if (screen != null) ui(() -> screen.finish());
            remove(root);
        }
        out.putInt("checks", checks);
        finish(-1, out);
    }
}
