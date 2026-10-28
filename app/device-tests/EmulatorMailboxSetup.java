package org.visualvoicemail.checks;

import android.content.*;
import android.media.MediaPlayer;
import android.os.*;

import org.json.*;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;

/** Opt-in sample inbox for an emulator only; refuses sessions or non-sample inbox data. */
public final class EmulatorMailboxSetup extends DeviceCheckInstrumentation {
    private Object call(Object target, String name, Class<?>[] types, Object... values)
            throws Exception {
        Method m = target.getClass().getDeclaredMethod(name, types);
        m.setAccessible(true);
        return m.invoke(target, values);
    }

    @Override
    public void onCreate(Bundle args) {
        super.onCreate(args);
        start();
    }

    @Override
    public void onStart() {
        Bundle out = new Bundle();
        try {
            if (!Arrays.asList("ranchu", "goldfish").contains(Build.HARDWARE))
                throw new IllegalStateException("Sample setup requires an Android emulator");
            Context context = getTargetContext();
            Class<?> type = Class.forName("org.visualvoicemail.app.PrivateStore");
            Constructor<?> ctor = type.getDeclaredConstructor(Context.class);
            ctor.setAccessible(true);
            Object store = ctor.newInstance(context);
            if (new File(context.getFilesDir(), "session.bin").exists())
                throw new IllegalStateException("Refusing to modify a connected mailbox");
            Class<?> carrier = Class.forName("org.visualvoicemail.app.CarrierClient");
            Object client = carrier.getMethod("get", Context.class).invoke(null, context);
            List<?> existing = (List<?>) carrier.getMethod("cachedInbox").invoke(client);
            for (Object item : existing)
                if (!((JSONObject) item).optString("objectid").startsWith("emulator-sample-"))
                    throw new IllegalStateException("Refusing to modify non-sample messages");
            JSONArray messages = new JSONArray();
            Class<?> speech = Class.forName("org.visualvoicemail.app.Transcription");
            Method key = speech.getDeclaredMethod("key", String.class);
            key.setAccessible(true);
            for (int i = 0; i < 8; i++) {
                String id = "emulator-sample-" + i;
                JSONObject message =
                        new JSONObject()
                                .put("objectid", id)
                                .put("returnnumber", "+1202555010" + i)
                                .put("date", "2026-10-" + (20 + i) + "T12:00:00Z")
                                .put("flag", i % 2 == 0 ? "" : "Seen");
                File audio =
                        (File)
                                call(
                                        client,
                                        "audioFile",
                                        new Class<?>[] {JSONObject.class},
                                        message);
                audio.getParentFile().mkdirs();
                try (InputStream input = getContext().getAssets().open("speech-fixture.wav");
                        FileOutputStream output = new FileOutputStream(audio)) {
                    byte[] bytes = new byte[8192];
                    int count;
                    while ((count = input.read(bytes)) != -1) output.write(bytes, 0, count);
                }
                MediaPlayer player = new MediaPlayer();
                try {
                    player.setDataSource(audio.getAbsolutePath());
                    player.prepare();
                    message.put("duration", player.getDuration() / 1000.0);
                } finally {
                    player.release();
                }
                messages.put(message);
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
                                                                .put("end", 900)
                                                                .put(
                                                                        "text",
                                                                        "Sample voicemail: let's"
                                                                            + " discuss the project"
                                                                            + " tomorrow."))
                                                .put(
                                                        new JSONObject()
                                                                .put("start", 1000)
                                                                .put("end", 2500)
                                                                .put(
                                                                        "text",
                                                                        "Sample: call me when you"
                                                                            + " have a moment.")));
                if (!(Boolean)
                        call(
                                store,
                                "put",
                                new Class<?>[] {String.class, JSONObject.class},
                                key.invoke(null, id),
                                transcript))
                    throw new IOException("Could not save sample transcript");
            }
            if (!(Boolean)
                    call(
                            store,
                            "put",
                            new Class<?>[] {String.class, JSONObject.class},
                            "inbox.bin",
                            new JSONObject()
                                    .put("messages", messages)
                                    .put("synced_at", System.currentTimeMillis())))
                throw new IOException("Could not save sample inbox");
            context.getSharedPreferences("settings", 0)
                    .edit()
                    .putBoolean("tutorial_seen", true)
                    .putBoolean("guide_inbox", false)
                    .putBoolean("guide_recording", false)
                    .putBoolean("auto_transcribe", false)
                    .putBoolean("refresh_on_open", false)
                    .putBoolean("alerts_enabled", false)
                    .putString("transcription_quality", "standard")
                    .commit();
            out.putBoolean("passed", true);
            out.putInt("sample_messages", messages.length());
        } catch (Throwable failure) {
            while (failure.getCause() != null) failure = failure.getCause();
            out.putString("failure_class", failure.getClass().getSimpleName());
            out.putString("failure", failure.getMessage());
        }
        finish(-1, out);
    }
}
