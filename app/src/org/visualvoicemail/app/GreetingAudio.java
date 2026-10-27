package org.visualvoicemail.app;

import android.content.Context;
import android.media.*;

import org.json.JSONObject;

import java.io.*;
import java.nio.*;
import java.security.MessageDigest;
import java.util.*;

/** Local draft persistence and bounded conversion to the carrier's AMR narrowband format. */
final class GreetingAudio {
    static File create(Context c) throws IOException {
        File directory = new File(c.getFilesDir(), "greeting-draft");
        if (!directory.isDirectory() && !directory.mkdirs())
            throw new IOException("Draft storage unavailable.");
        return new File(directory, "greeting-" + UUID.randomUUID() + ".amr");
    }

    static String hash(File file) throws Exception {
        MessageDigest hash = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new FileInputStream(file)) {
            byte[] b = new byte[8192];
            int n;
            while ((n = input.read(b)) != -1) hash.update(b, 0, n);
        }
        StringBuilder result = new StringBuilder();
        for (byte b : hash.digest()) result.append(String.format(Locale.ROOT, "%02x", b & 255));
        return result.toString();
    }

    static int duration(File file) throws Exception {
        MediaMetadataRetriever media = new MediaMetadataRetriever();
        try {
            media.setDataSource(file.getAbsolutePath());
            long value =
                    Long.parseLong(
                            media.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
            if (value < 1000 || value > 60000)
                throw new IOException("Choose a greeting from 1 to 60 seconds.");
            return (int) value;
        } finally {
            media.release();
        }
    }

    static JSONObject metadata(Context c) {
        JSONObject value = new PrivateStore(c).get("greeting-draft.bin");
        return value == null ? new JSONObject() : value;
    }

    static File selected(Context c) {
        String name = metadata(c).optString("file");
        if (!name.matches("greeting-[0-9a-f-]{36}\\.amr")) return null;
        File file = new File(new File(c.getFilesDir(), "greeting-draft"), name);
        return file.isFile() ? file : null;
    }

    static synchronized void publish(Context c, File file) throws Exception {
        File directory = new File(c.getFilesDir(), "greeting-draft");
        if (!file.getParentFile().getCanonicalFile().equals(directory.getCanonicalFile())
                || !file.getName().matches("greeting-[0-9a-f-]{36}\\.amr"))
            throw new IOException("Invalid local greeting draft.");
        if (!"amr".equals(ExportAudioFormat.detect(file).extension))
            throw new IOException("Unsupported greeting audio.");
        int duration = duration(file);
        File old = selected(c);
        JSONObject meta =
                new JSONObject()
                        .put("file", file.getName())
                        .put("duration", duration)
                        .put("hash", hash(file));
        if (!new PrivateStore(c).put("greeting-draft.bin", meta))
            throw new IOException("Couldn't save your local draft.");
        if (old != null && !old.equals(file)) old.delete();
    }

    static synchronized void discard(Context c) throws Exception {
        File old = selected(c);
        if (!new PrivateStore(c).put("greeting-draft.bin", new JSONObject()))
            throw new IOException("Couldn't remove draft metadata.");
        if (old != null) old.delete();
    }

    static File snapshot(Context c, File source, String expected) throws Exception {
        File target = File.createTempFile("greeting-upload-", ".amr", c.getCacheDir());
        try {
            copy(new FileInputStream(source), target);
            if (!expected.equals(hash(target)))
                throw new IOException("Your draft changed. Preview it again before saving.");
            duration(target);
            return target;
        } catch (Exception error) {
            target.delete();
            throw error;
        }
    }

    static void copy(InputStream source, File target) throws Exception {
        try (InputStream input = source;
                FileOutputStream output = new FileOutputStream(target)) {
            byte[] b = new byte[8192];
            int n, total = 0;
            while ((n = input.read(b)) != -1) {
                total += n;
                if (total > 25 * 1024 * 1024) throw new IOException("Audio file too large.");
                output.write(b, 0, n);
            }
            output.getFD().sync();
        }
    }

    static File importAudio(Context c, InputStream input) throws Exception {
        File source = File.createTempFile("greeting-import-", ".audio", c.getCacheDir()),
                target = create(c);
        try {
            copy(input, source);
            duration(source);
            boolean narrowband = false;
            try {
                narrowband = "amr".equals(ExportAudioFormat.detect(source).extension);
            } catch (IOException other) {
            }
            if (narrowband) copy(new FileInputStream(source), target);
            else encode(source, target);
            publish(c, target);
            return target;
        } catch (Exception error) {
            target.delete();
            throw error;
        } finally {
            source.delete();
        }
    }

    private static void encode(File source, File target) throws Exception {
        float[] samples = AudioSamples.read(source);
        int count = samples.length / 2, position = 0;
        if (count < 8000 || count > 8000 * 60)
            throw new IOException("Choose a greeting from 1 to 60 seconds.");
        MediaCodec codec = MediaCodec.createEncoderByType("audio/3gpp");
        boolean started = false;
        try (FileOutputStream output = new FileOutputStream(target)) {
            MediaFormat format = MediaFormat.createAudioFormat("audio/3gpp", 8000, 1);
            format.setInteger(MediaFormat.KEY_BIT_RATE, 12200);
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            codec.start();
            started = true;
            output.write("#!AMR\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean inputDone = false, done = false;
            long limit = android.os.SystemClock.elapsedRealtime() + 120000;
            while (!done) {
                if (android.os.SystemClock.elapsedRealtime() > limit)
                    throw new IOException("Audio conversion timed out.");
                if (!inputDone) {
                    int index = codec.dequeueInputBuffer(10000);
                    if (index >= 0) {
                        ByteBuffer buffer =
                                codec.getInputBuffer(index).order(ByteOrder.LITTLE_ENDIAN);
                        buffer.clear();
                        int start = position;
                        while (position < count && buffer.remaining() >= 2) {
                            float value = (samples[position * 2] + samples[position * 2 + 1]) * .5f;
                            buffer.putShort(
                                    (short)
                                            Math.max(
                                                    -32768,
                                                    Math.min(32767, Math.round(value * 32767))));
                            position++;
                        }
                        inputDone = position == count;
                        codec.queueInputBuffer(
                                index,
                                0,
                                buffer.position(),
                                (long) start * 1000000 / 8000,
                                inputDone ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0);
                    }
                }
                int index = codec.dequeueOutputBuffer(info, 10000);
                if (index >= 0) {
                    ByteBuffer encoded = codec.getOutputBuffer(index);
                    encoded.position(info.offset);
                    encoded.limit(info.offset + info.size);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && info.size > 0) {
                        byte[] bytes = new byte[info.size];
                        encoded.get(bytes);
                        output.write(bytes);
                    }
                    done = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                    codec.releaseOutputBuffer(index, false);
                }
            }
            output.getFD().sync();
        } finally {
            if (started)
                try {
                    codec.stop();
                } catch (Exception ignored) {
                }
            codec.release();
        }
        duration(target);
    }
}
