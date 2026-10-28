package org.visualvoicemail.app;

import android.content.Context;

import com.k2fsa.sherpa.onnx.*;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Process-scoped, single-job local speech recognition. Never sends audio or text to a server. */
final class Transcription {
    private static final ExecutorService WORK = Executors.newSingleThreadExecutor();
    static volatile String job = "", status = "", failure = "";
    static volatile boolean busy, downloading, cancelled;
    static volatile int progress;

    static TranscriptionQuality selected(Context c) {
        return TranscriptionQuality.forKey(
                c.getSharedPreferences("settings", 0)
                        .getString("transcription_quality", "standard"));
    }

    static synchronized boolean select(Context c, String key) {
        if (!TranscriptionQuality.forKey(key).key.equals(key)) return false;
        if (busy && !selected(c).key.equals(key)) return false;
        if (!busy && !selected(c).key.equals(key)) {
            failure = "";
            status = "";
            job = "";
        }
        c.getSharedPreferences("settings", 0)
                .edit()
                .putString("transcription_quality", key)
                .apply();
        return true;
    }

    private static File directory(Context c, TranscriptionQuality quality) {
        File base = new File(c.getFilesDir(), "speech-model");
        File f = quality.directory.isEmpty() ? base : new File(base, quality.directory);
        f.mkdirs();
        return f;
    }

    static boolean ready(Context c) {
        return ready(c, selected(c));
    }

    static boolean ready(Context c, TranscriptionQuality quality) {
        String[] NAMES = quality.names();
        long[] SIZES = quality.sizes();
        File root = directory(c, quality);
        for (int i = 0; i < NAMES.length; i++)
            if (new File(root, NAMES[i]).length() != SIZES[i]) return false;
        return new File(root, "verified-v1").exists();
    }

    static String key(String id) {
        try {
            return "transcript-"
                    + hex(MessageDigest.getInstance("SHA-256").digest(id.getBytes("UTF-8")))
                    + ".enc";
        } catch (Exception impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    static Transcript cached(Context c, String id) {
        return Transcript.parse(new PrivateStore(c).get(key(id)));
    }

    static String translationKey(String id) {
        return key(id).replace("transcript-", "translation-");
    }

    static Transcript cachedEnglish(Context c, String id) {
        return Transcript.parse(new PrivateStore(c).get(translationKey(id)));
    }

    static synchronized void download(Context context) {
        if (busy) return;
        final Context c = context.getApplicationContext();
        final TranscriptionQuality quality = selected(c);
        final String[] NAMES = quality.names(), HASHES = quality.hashes();
        final long[] SIZES = quality.sizes();
        busy = true;
        downloading = true;
        cancelled = false;
        job = "model";
        failure = "";
        progress = 0;
        status = "Downloading " + quality.title + "…";
        WORK.execute(
                () -> {
                    File temporary = null;
                    try {
                        File root = directory(c, quality);
                        if (root.getUsableSpace() < quality.bytes() * 2 + 20000000)
                            throw new java.io.IOException("storage");
                        long completed = 0, total = 0;
                        for (long size : SIZES) total += size;
                        for (int i = 0; i < NAMES.length; i++) {
                            File target = new File(root, NAMES[i]);
                            if (target.length() == SIZES[i] && digest(target).equals(HASHES[i])) {
                                completed += SIZES[i];
                                continue;
                            }
                            URL url = new URL(quality.url(i));
                            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                            connection.setConnectTimeout(20000);
                            connection.setReadTimeout(20000);
                            temporary = new File(root, NAMES[i] + ".part");
                            long received = 0;
                            MessageDigest hash = MessageDigest.getInstance("SHA-256");
                            try {
                                if (connection.getResponseCode() != 200)
                                    throw new java.io.IOException("download");
                                try (java.io.InputStream in = connection.getInputStream();
                                        FileOutputStream out = new FileOutputStream(temporary)) {
                                    byte[] buffer = new byte[65536];
                                    int n;
                                    while ((n = in.read(buffer)) != -1) {
                                        if (cancelled) throw new java.io.InterruptedIOException();
                                        received += n;
                                        if (received > SIZES[i])
                                            throw new java.io.IOException("size");
                                        out.write(buffer, 0, n);
                                        hash.update(buffer, 0, n);
                                        progress = (int) ((completed + received) * 100 / total);
                                        status =
                                                "Downloading "
                                                        + quality.title
                                                        + " · "
                                                        + progress
                                                        + "%";
                                    }
                                    out.getFD().sync();
                                }
                                if (received != SIZES[i] || !hex(hash.digest()).equals(HASHES[i]))
                                    throw new java.io.IOException("verification");
                                if (!temporary.renameTo(target))
                                    throw new java.io.IOException("storage");
                                temporary = null;
                                completed += received;
                            } finally {
                                connection.disconnect();
                            }
                        }
                        if (cancelled) throw new java.io.InterruptedIOException();
                        try (FileOutputStream marker =
                                new FileOutputStream(new File(root, "verified-v1"))) {
                            marker.write(1);
                        }
                        status = quality.title + " ready";
                    } catch (Exception e) {
                        failure =
                                cancelled
                                        ? "Download cancelled. You can resume when ready."
                                        : "Model download didn't finish. Check your connection and"
                                                + " free space, then retry.";
                    } finally {
                        if (temporary != null) temporary.delete();
                        downloading = false;
                        busy = false;
                    }
                });
    }

    static volatile boolean batch;
    static volatile String batchStatus = "";
    static volatile long batchVersion;

    static synchronized void generate(Context context, JSONObject message) {
        generate(context, message, SpeechOptions.english(context));
    }

    static synchronized void generate(Context context, JSONObject message, boolean english) {
        if (busy || MailboxBatch.removingSaved || !ready(context)) return;
        final Context c = context.getApplicationContext();
        final TranscriptionQuality quality = selected(c);
        if (english && quality != TranscriptionQuality.HIGHEST) return;
        final String language = SpeechOptions.language(c, quality);
        begin(message.optString("objectid"));
        WORK.execute(
                () -> {
                    try {
                        if (english && cached(c, message.optString("objectid")) == null)
                            transcribe(c, quality, language, message, false);
                        transcribe(c, quality, language, message, english);
                    } catch (Exception | LinkageError | OutOfMemoryError e) {
                        failure = failureMessage(e);
                    } finally {
                        busy = false;
                    }
                });
    }

    /**
     * A snapshot queue, serialized with single-message work, and independent of Activity lifetime.
     */
    static synchronized boolean generateBatch(
            Context context, List<JSONObject> messages, boolean replace) {
        if (busy || MailboxBatch.removingSaved || !ready(context) || messages.isEmpty())
            return false;
        final Context c = context.getApplicationContext();
        final TranscriptionQuality quality = selected(c);
        final String language = SpeechOptions.language(c, quality);
        final boolean english = SpeechOptions.english(c);
        final List<JSONObject> snapshot = new ArrayList<>(messages);
        begin("batch");
        batch = true;
        batchStatus = "Preparing " + snapshot.size() + " transcripts…";
        WORK.execute(
                () -> {
                    int saved = 0, skipped = 0, failed = 0;
                    try {
                        for (int i = 0; i < snapshot.size(); i++) {
                            if (cancelled) break;
                            JSONObject message = snapshot.get(i);
                            job = message.optString("objectid");
                            batchStatus =
                                    (english ? "Translating " : "Transcribing ")
                                            + (i + 1)
                                            + " of "
                                            + snapshot.size()
                                            + " · "
                                            + quality.title;
                            if (!replace
                                    && (english ? cachedEnglish(c, job) : cached(c, job)) != null) {
                                skipped++;
                                continue;
                            }
                            try {
                                if (english && cached(c, job) == null)
                                    transcribe(c, quality, language, message, false);
                                transcribe(c, quality, language, message, english);
                                saved++;
                            } catch (Exception | LinkageError | OutOfMemoryError e) {
                                if (cancelled) break;
                                failed++;
                                // Memory failures are unlikely to improve on the next message.
                                if (e instanceof InsufficientModelMemory
                                        || e instanceof OutOfMemoryError) {
                                    failure = failureMessage(e);
                                    break;
                                }
                            }
                        }
                    } finally {
                        batchStatus =
                                (cancelled ? "Queue cancelled · " : "Transcripts finished · ")
                                        + saved
                                        + " saved · "
                                        + skipped
                                        + " kept · "
                                        + failed
                                        + " failed"
                                        + (snapshot.size() > saved + skipped + failed
                                                ? " · "
                                                        + (snapshot.size()
                                                                - saved
                                                                - skipped
                                                                - failed)
                                                        + " not finished"
                                                : "");
                        if (!failure.isEmpty()) batchStatus += "\n" + failure;
                        batchVersion++;
                        batch = false;
                        busy = false;
                    }
                });
        return true;
    }

    private static void begin(String id) {
        busy = true;
        downloading = false;
        cancelled = false;
        job = id;
        failure = "";
        status = "Opening recording…";
    }

    static synchronized void dismissBatch() {
        if (!batch) batchStatus = "";
    }

    private static String failureMessage(Throwable e) {
        return cancelled
                ? "Transcription cancelled. Your recording is unchanged."
                : e instanceof InsufficientModelMemory || e instanceof OutOfMemoryError
                        ? "Not enough available memory for this model. Close other apps and retry."
                              + " Standard and Enhanced offer English-only transcription. Your"
                              + " saved text is kept."
                        : "Couldn't transcribe this recording. You can still listen, or try again.";
    }

    private static void transcribe(
            Context c,
            TranscriptionQuality quality,
            String language,
            JSONObject message,
            boolean english)
            throws Exception {
        final String[] NAMES = quality.names();
        final String id = message.optString("objectid");
        OfflineRecognizer recognizer = null;
        Vad vad = null;
        try {
            File audio = CarrierClient.get(c).availableAudio(message);
            if (cancelled) throw new java.io.InterruptedIOException();
            status = "Reading audio on this phone…";
            float[] samples = AudioSamples.read(audio);
            File root = directory(c, quality);
            SileroVadModelConfig silero = new SileroVadModelConfig();
            silero.setModel(new File(root, NAMES[3]).getAbsolutePath());
            silero.setMinSilenceDuration(.45f);
            silero.setMinSpeechDuration(.2f);
            silero.setMaxSpeechDuration(12f);
            VadModelConfig vc = new VadModelConfig();
            vc.setSileroVadModelConfig(silero);
            vad = new Vad(null, vc);
            List<int[]> ranges = new ArrayList<>();
            for (int offset = 0; offset < samples.length; offset += 512) {
                if (cancelled) throw new java.io.InterruptedIOException();
                vad.acceptWaveform(
                        Arrays.copyOfRange(
                                samples, offset, Math.min(offset + 512, samples.length)));
                drain(vad, ranges, samples.length);
            }
            vad.flush();
            drain(vad, ranges, samples.length);
            vad.release();
            vad = null;
            Transcript result = new Transcript();
            result.quality = quality.key;
            result.language = language;
            result.task = english ? "translate" : "transcribe";
            if (!ranges.isEmpty()) {
                if (quality == TranscriptionQuality.HIGHEST) {
                    android.app.ActivityManager manager =
                            (android.app.ActivityManager)
                                    c.getSystemService(Context.ACTIVITY_SERVICE);
                    android.app.ActivityManager.MemoryInfo memory =
                            new android.app.ActivityManager.MemoryInfo();
                    manager.getMemoryInfo(memory);
                    if (memory.totalMem < 8_000_000_000L
                            || memory.lowMemory
                            || memory.availMem < 2L * 1024 * 1024 * 1024)
                        throw new InsufficientModelMemory();
                }
                OfflineWhisperModelConfig whisper = new OfflineWhisperModelConfig();
                whisper.setEncoder(new File(root, NAMES[0]).getAbsolutePath());
                whisper.setDecoder(new File(root, NAMES[1]).getAbsolutePath());
                whisper.setLanguage("auto".equals(language) ? "" : language);
                whisper.setTask(english ? "translate" : "transcribe");
                whisper.setTailPaddings(quality == TranscriptionQuality.HIGHEST ? 300 : 50);
                OfflineModelConfig model = new OfflineModelConfig();
                model.setWhisper(whisper);
                model.setTokens(new File(root, NAMES[2]).getAbsolutePath());
                model.setNumThreads(4);
                OfflineRecognizerConfig config = new OfflineRecognizerConfig();
                config.setModelConfig(model);
                status = "Loading " + quality.label() + " on this phone…";
                recognizer = new OfflineRecognizer(null, config);
                for (int i = 0; i < ranges.size(); i++) {
                    if (cancelled) throw new java.io.InterruptedIOException();
                    status =
                            (english
                                            ? "Translating to English on this phone · "
                                            : "Transcribing on this phone · ")
                                    + (i + 1)
                                    + " of "
                                    + ranges.size();
                    int[] range = ranges.get(i);
                    OfflineStream stream = recognizer.createStream();
                    try {
                        stream.acceptWaveform(
                                Arrays.copyOfRange(samples, range[0], range[1]), 16000);
                        recognizer.decode(stream);
                        OfflineRecognizerResult decoded = recognizer.getResult(stream);
                        String text = decoded.getText().trim();
                        String reported = decoded.getLang();
                        if (reported != null && !reported.isEmpty())
                            result.reportedLanguage =
                                    result.reportedLanguage.isEmpty()
                                                    || result.reportedLanguage.equals(reported)
                                            ? reported
                                            : "mul";
                        if (!text.isEmpty())
                            result.segments.add(
                                    new Transcript.Segment(range[0] / 16, range[1] / 16, text));
                    } finally {
                        stream.release();
                    }
                }
            }
            if (cancelled) throw new java.io.InterruptedIOException();
            if (!new PrivateStore(c).put(english ? translationKey(id) : key(id), result.json()))
                throw new java.io.IOException("storage");
            status =
                    result.segments.isEmpty()
                            ? "No speech detected"
                            : english
                                    ? "English translation saved on this phone"
                                    : "Transcript saved on this phone";
        } finally {
            if (vad != null) vad.release();
            if (recognizer != null) recognizer.release();
        }
    }

    private static final class InsufficientModelMemory extends Exception {
        private static final long serialVersionUID = 1L;
    }

    private static void drain(Vad vad, List<int[]> ranges, int count) {
        while (!vad.empty()) {
            SpeechSegment s = vad.front();
            int start = Math.max(0, (int) s.getStart() - 2400),
                    end = Math.min(count, (int) s.getStart() + s.getSamples().length + 1600);
            if (end > start) {
                if (!ranges.isEmpty()) {
                    int[] last = ranges.get(ranges.size() - 1);
                    start = Math.max(start, last[1]);
                }
                if (end > start) ranges.add(new int[] {start, end});
            }
            vad.pop();
        }
    }

    static void cancel() {
        cancelled = true;
    }

    static synchronized boolean clear(Context c, boolean model) {
        if (busy) return false;
        TranscriptionQuality quality = selected(c);
        String[] NAMES = quality.names();
        boolean okay = true;
        File[] files = model ? directory(c, quality).listFiles() : c.getFilesDir().listFiles();
        if (files == null) return false;
        for (File file : files) {
            boolean selected =
                    model
                            ? file.getName().equals("verified-v1")
                            : file.getName()
                                    .matches(
                                            "(?:transcript|translation)-[0-9a-f]{64}\\.enc(?:\\.bak|\\.new)?");
            if (model)
                for (String name : NAMES)
                    if (file.getName().equals(name) || file.getName().equals(name + ".part"))
                        selected = true;
            if (selected && !file.delete()) okay = false;
        }
        return okay;
    }

    private static String digest(File file) throws Exception {
        MessageDigest hash = MessageDigest.getInstance("SHA-256");
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] b = new byte[65536];
            int n;
            while ((n = in.read(b)) != -1) hash.update(b, 0, n);
        }
        return hex(hash.digest());
    }

    private static String hex(byte[] bytes) {
        StringBuilder s = new StringBuilder();
        for (byte b : bytes) s.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
        return s.toString();
    }
}
