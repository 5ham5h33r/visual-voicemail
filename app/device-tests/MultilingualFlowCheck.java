package org.visualvoicemail.checks;

import android.content.*;
import android.os.*;

import org.json.*;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;

/**
 * Public multilingual fixtures only. Existing carrier messages and transcripts are never changed.
 */
public final class MultilingualFlowCheck extends DeviceCheckInstrumentation {
    private int checks;
    private Context c;
    private Class<?> speech;
    private Object client;
    private final List<File> cleanup = new ArrayList<>();

    private Method method(Class<?> cls, String name, Class<?>... types) throws Exception {
        Method m = cls.getDeclaredMethod(name, types);
        m.setAccessible(true);
        return m;
    }

    private Object state(String name) throws Exception {
        Field f = speech.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(null);
    }

    private void check(boolean ok) throws Exception {
        checks++;
        if (!ok) throw new Exception("check " + checks);
    }

    private JSONObject fixture(String asset) throws Exception {
        String id = "language-check-" + UUID.randomUUID();
        JSONObject message =
                new JSONObject()
                        .put("objectid", id)
                        .put("returnnumber", "+15550000100")
                        .put("date", "2026-01-01T12:00:00Z")
                        .put("duration", 8)
                        .put("flag", "");
        File audio =
                (File)
                        method(client.getClass(), "audioFile", JSONObject.class)
                                .invoke(client, message);
        audio.getParentFile().mkdirs();
        cleanup.add(audio);
        try (InputStream in = getContext().getAssets().open(asset);
                FileOutputStream out = new FileOutputStream(audio)) {
            byte[] bytes = new byte[8192];
            int n;
            while ((n = in.read(bytes)) != -1) out.write(bytes, 0, n);
        }
        for (String name : new String[] {"key", "translationKey"}) {
            String key = (String) method(speech, name, String.class).invoke(null, id);
            for (String suffix : new String[] {"", ".bak", ".new"})
                cleanup.add(new File(c.getFilesDir(), key + suffix));
        }
        return message;
    }

    private JSONObject cached(JSONObject message, boolean english) throws Exception {
        Object value =
                method(speech, english ? "cachedEnglish" : "cached", Context.class, String.class)
                        .invoke(null, c, message.getString("objectid"));
        return value == null ? null : (JSONObject) method(value.getClass(), "json").invoke(value);
    }

    private String text(JSONObject transcript) throws Exception {
        StringBuilder result = new StringBuilder();
        JSONArray segments = transcript.getJSONArray("segments");
        for (int i = 0; i < segments.length(); i++)
            result.append(segments.getJSONObject(i).getString("text")).append(' ');
        return result.toString().trim();
    }

    private void waitSpeech() throws Exception {
        long limit = SystemClock.elapsedRealtime() + 600000;
        while ((Boolean) state("busy")) {
            if (SystemClock.elapsedRealtime() > limit)
                throw new Exception("fixture speech timeout");
            Thread.sleep(100);
        }
    }

    private void generate(JSONObject message, boolean english) throws Exception {
        method(speech, "generate", Context.class, JSONObject.class, boolean.class)
                .invoke(null, c, message, english);
        waitSpeech();
        check(((String) state("failure")).isEmpty());
        check(cached(message, english) != null);
    }

    private void anchors(JSONObject transcript) throws Exception {
        JSONArray segments = transcript.getJSONArray("segments");
        check(segments.length() > 0);
        for (int i = 0; i < segments.length(); i++) {
            JSONObject segment = segments.getJSONObject(i);
            check(segment.getInt("start") >= 0 && segment.getInt("end") > segment.getInt("start"));
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
        c = getTargetContext();
        SharedPreferences prefs = c.getSharedPreferences("settings", 0);
        Map<String, ?> before = prefs.getAll();
        try {
            speech = Class.forName("org.visualvoicemail.app.Transcription");
            check(!(Boolean) state("busy"));
            client =
                    method(
                                    Class.forName("org.visualvoicemail.app.CarrierClient"),
                                    "get",
                                    Context.class)
                            .invoke(null, c);
            prefs.edit()
                    .putBoolean("auto_transcribe", false)
                    .putBoolean("refresh_on_open", false)
                    .putString("transcription_quality", "highest")
                    .putString("speech_language", "auto")
                    .putBoolean("speech_english", false)
                    .commit();
            check((Boolean) method(speech, "ready", Context.class).invoke(null, c));
            // Legacy encrypted-cache JSON keeps its original words and anchors.
            Class<?> transcript = Class.forName("org.visualvoicemail.app.Transcript");
            Object legacy =
                    method(transcript, "parse", JSONObject.class)
                            .invoke(
                                    null,
                                    new JSONObject(
                                            "{\"version\":1,\"segments\":[{\"start\":100,\"end\":500,\"text\":\"Legacy"
                                                + " sample\"}]}"));
            check(
                    legacy != null
                            && "Legacy sample".equals(method(transcript, "text").invoke(legacy)));
            JSONObject spanish = fixture("es-spanish.wav");
            generate(spanish, false);
            JSONObject original = cached(spanish, false);
            String preserved = original.toString();
            anchors(original);
            generate(spanish, true);
            JSONObject translated = cached(spanish, true);
            anchors(translated);
            check(preserved.equals(cached(spanish, false).toString()));
            check(
                    "translate".equals(translated.getString("task"))
                            && "auto".equals(translated.getString("language")));
            check(!text(original).equalsIgnoreCase(text(translated)));
            out.putString("public_spanish_original", text(original));
            out.putString("public_spanish_english", text(translated));
            JSONObject french = fixture("fr-french.wav");
            prefs.edit().putString("speech_language", "fr").commit();
            generate(french, true);
            original = cached(french, false);
            translated = cached(french, true);
            check(original != null && "fr".equals(original.getString("language")));
            anchors(original);
            anchors(translated);
            out.putString("public_french_original", text(original));
            out.putString("public_french_english", text(translated));
            // Preserve the original while generating one missing translation and keeping another.
            preserved = cached(french, false).toString();
            String frenchKey =
                    (String)
                            method(speech, "translationKey", String.class)
                                    .invoke(null, french.getString("objectid"));
            new android.util.AtomicFile(new File(c.getFilesDir(), frenchKey)).delete();
            prefs.edit().putBoolean("speech_english", true).commit();
            check(
                    (Boolean)
                            method(
                                            speech,
                                            "generateBatch",
                                            Context.class,
                                            List.class,
                                            boolean.class)
                                    .invoke(null, c, Arrays.asList(french, spanish), false));
            waitSpeech();
            check(((String) state("batchStatus")).contains("1 saved · 1 kept"));
            check(preserved.equals(cached(french, false).toString()));
            prefs.edit().putString("speech_language", "auto").commit();
            JSONObject mixed = fixture("mixed-noisy.wav");
            generate(mixed, false);
            anchors(cached(mixed, false));
            out.putString("public_mixed_original", text(cached(mixed, false)));
            JSONObject silence = fixture("silence.wav");
            generate(silence, true);
            check(
                    cached(silence, false).getJSONArray("segments").length() == 0
                            && cached(silence, true).getJSONArray("segments").length() == 0);
            // Immediate cancellation leaves the previous original and translation untouched.
            String previousOriginal = cached(spanish, false).toString(),
                    previousEnglish = cached(spanish, true).toString();
            method(speech, "generate", Context.class, JSONObject.class, boolean.class)
                    .invoke(null, c, spanish, true);
            method(speech, "cancel").invoke(null);
            waitSpeech();
            check(
                    previousOriginal.equals(cached(spanish, false).toString())
                            && previousEnglish.equals(cached(spanish, true).toString()));
            prefs.edit().putString("speech_language", "unsupported-test-code").commit();
            Object highest =
                    Class.forName("org.visualvoicemail.app.TranscriptionQuality")
                            .getField("HIGHEST")
                            .get(null);
            check(
                    "auto"
                            .equals(
                                    method(
                                                    Class.forName(
                                                            "org.visualvoicemail.app.SpeechOptions"),
                                                    "language",
                                                    Context.class,
                                                    highest.getClass())
                                            .invoke(null, c, highest)));
            out.putBoolean("passed", true);
        } catch (Throwable error) {
            while (error.getCause() != null) error = error.getCause();
            out.putString("failure_class", error.getClass().getSimpleName());
            out.putString("failure", error.getMessage());
        } finally {
            try {
                if (speech != null && (Boolean) state("busy")) {
                    method(speech, "cancel").invoke(null);
                    waitSpeech();
                }
            } catch (Exception ignored) {
            }
            for (File file : cleanup) file.delete();
            SharedPreferences.Editor restore = prefs.edit();
            for (String key :
                    new String[] {
                        "auto_transcribe",
                        "refresh_on_open",
                        "transcription_quality",
                        "speech_language",
                        "speech_english"
                    }) {
                Object value = before.get(key);
                if (value == null) restore.remove(key);
                else if (value instanceof Boolean) restore.putBoolean(key, (Boolean) value);
                else restore.putString(key, (String) value);
            }
            restore.commit();
        }
        out.putInt("checks", checks);
        finish(-1, out);
    }
}
