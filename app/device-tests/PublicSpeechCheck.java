package org.visualvoicemail.checks;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.*;

import org.json.JSONObject;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

/** Full x86_64 inference on public English audio, with no model download or carrier access. */
public final class PublicSpeechCheck extends DeviceCheckInstrumentation {
    private int checks;

    private Object call(Object o, String name, Class<?>[] types, Object... args) throws Exception {
        Class<?> type = o instanceof Class ? (Class<?>) o : o.getClass();
        Method m = type.getDeclaredMethod(name, types);
        m.setAccessible(true);
        return m.invoke(o instanceof Class ? null : o, args);
    }

    private boolean busy(Class<?> speech) throws Exception {
        Field f = speech.getDeclaredField("busy");
        f.setAccessible(true);
        return f.getBoolean(null);
    }

    private void check(boolean good) throws Exception {
        checks++;
        if (!good) throw new Exception("check " + checks);
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
        String id = "emulator-public-speech-" + UUID.randomUUID();
        Object saved = null;
        File source = null;
        Class<?> speech = null;
        CountDownLatch release = new CountDownLatch(1), held = new CountDownLatch(1);
        Thread blocker = null;
        try {
            check(Arrays.asList("ranchu", "goldfish").contains(Build.HARDWARE));
            check(!new File(c.getFilesDir(), "session.bin").exists());
            speech = Class.forName("org.visualvoicemail.app.Transcription");
            check(
                    (Boolean)
                            call(
                                    speech,
                                    "select",
                                    new Class<?>[] {Context.class, String.class},
                                    c,
                                    "standard"));
            check((Boolean) call(speech, "ready", new Class<?>[] {Context.class}, c));
            prefs.edit()
                    .putString("speech_language", "en")
                    .putBoolean("speech_english", false)
                    .commit();
            source = File.createTempFile("public-speech-", ".wav", c.getCacheDir());
            try (InputStream input = getContext().getAssets().open("speech-fixture.wav");
                    OutputStream output = new FileOutputStream(source)) {
                byte[] b = new byte[8192];
                int n;
                while ((n = input.read(b)) != -1) output.write(b, 0, n);
            }
            JSONObject message =
                    new JSONObject()
                            .put("objectid", id)
                            .put("returnnumber", "+12025550199")
                            .put("flag", "")
                            .put("date", "2026-10-27T12:00:00Z")
                            .put("_carrier_removed", true);
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
                    source);
            call(saved, "confirmRemoved", new Class<?>[] {String.class}, id);
            Object client =
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
                                        release.await(120, TimeUnit.SECONDS);
                                    } catch (InterruptedException e) {
                                        Thread.currentThread().interrupt();
                                    }
                                }
                            });
            blocker.start();
            check(held.await(2, TimeUnit.SECONDS));
            call(
                    speech,
                    "generate",
                    new Class<?>[] {Context.class, JSONObject.class, boolean.class},
                    c,
                    message,
                    false);
            long deadline = SystemClock.elapsedRealtime() + 90000;
            while (busy(speech) && SystemClock.elapsedRealtime() < deadline) Thread.sleep(100);
            check(!busy(speech));
            check(blocker.isAlive() && release.getCount() == 1);
            Object transcript =
                    call(speech, "cached", new Class<?>[] {Context.class, String.class}, c, id);
            check(transcript != null);
            JSONObject data = (JSONObject) call(transcript, "json", new Class<?>[0]);
            check(
                    "standard".equals(data.optString("quality"))
                            && "transcribe".equals(data.optString("task")));
            check(data.getJSONArray("segments").length() > 0);
            check(((String) call(transcript, "text", new Class<?>[0])).trim().length() > 5);
            out.putBoolean("passed", true);
            out.putBoolean("local_inference_while_carrier_blocked", true);
        } catch (Throwable error) {
            while (error.getCause() != null) error = error.getCause();
            out.putString("failure_class", error.getClass().getSimpleName());
            out.putString(
                    "failure",
                    error.getMessage() != null && error.getMessage().startsWith("check ")
                            ? error.getMessage()
                            : "public inference failed");
        } finally {
            release.countDown();
            if (speech != null)
                try {
                    if (busy(speech)) call(speech, "cancel", new Class<?>[0]);
                } catch (Exception ignored) {
                }
            if (saved != null)
                try {
                    call(saved, "removeLocal", new Class<?>[] {String.class}, id);
                } catch (Exception ignored) {
                }
            if (speech != null)
                for (String method : new String[] {"key", "translationKey"})
                    try {
                        new android.util.AtomicFile(
                                        new File(
                                                c.getFilesDir(),
                                                (String)
                                                        call(
                                                                speech,
                                                                method,
                                                                new Class<?>[] {String.class},
                                                                id)))
                                .delete();
                    } catch (Exception ignored) {
                    }
            if (source != null) source.delete();
            SharedPreferences.Editor restore = prefs.edit();
            for (String key :
                    new String[] {"transcription_quality", "speech_language", "speech_english"}) {
                Object v = before.get(key);
                if (v instanceof String) restore.putString(key, (String) v);
                else if (v instanceof Boolean) restore.putBoolean(key, (Boolean) v);
                else restore.remove(key);
            }
            restore.commit();
        }
        out.putInt("checks", checks);
        finish(-1, out);
    }
}
