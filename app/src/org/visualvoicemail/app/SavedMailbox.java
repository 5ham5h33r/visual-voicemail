package org.visualvoicemail.app;

import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.util.AtomicFile;

import org.json.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Durable app-local recordings, separate from the disposable playback cache. */
final class SavedMailbox {
    private static SavedMailbox instance;

    static synchronized SavedMailbox get(Context context) {
        if (instance == null) instance = new SavedMailbox(context);
        return instance;
    }

    private static final String INDEX = "saved-mailbox.bin";
    private final Context context;
    private final PrivateStore store;
    private JSONObject index;
    private boolean unreadable;

    SavedMailbox(Context context) {
        this.context = context.getApplicationContext();
        store = new PrivateStore(context);
        index = store.get(INDEX);
        unreadable =
                index == null
                        && (new File(this.context.getFilesDir(), INDEX).exists()
                                || new File(this.context.getFilesDir(), INDEX + ".bak").exists());
        if (index == null) index = new JSONObject();
    }

    private static String key(String id) throws Exception {
        if (id == null || id.isEmpty()) throw new IOException("Missing message identifier.");
        StringBuilder value = new StringBuilder();
        for (byte b :
                MessageDigest.getInstance("SHA-256").digest(id.getBytes(StandardCharsets.UTF_8)))
            value.append(String.format(Locale.ROOT, "%02x", b & 255));
        return value.toString();
    }

    private File file(String id) throws Exception {
        return new File(new File(context.getFilesDir(), "saved-recordings"), key(id) + ".audio");
    }

    private static String digest(File file) throws Exception {
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = input.read(buffer)) != -1) sha.update(buffer, 0, n);
        }
        StringBuilder value = new StringBuilder();
        for (byte b : sha.digest()) value.append(String.format(Locale.ROOT, "%02x", b & 255));
        return value.toString();
    }

    private void commit(JSONObject updated) throws Exception {
        if (unreadable)
            throw new IOException(
                    "Saved voicemail metadata couldn't be read. Carrier messages have been kept.");
        if (!store.put(INDEX, updated))
            throw new IOException(
                    "Couldn't save voicemail metadata. Carrier messages have been kept.");
        JSONObject verified = store.get(INDEX);
        if (verified == null || !updated.toString().equals(verified.toString()))
            throw new IOException("Couldn't verify saved voicemail metadata.");
        index = verified;
    }

    synchronized boolean contains(String id) {
        return index.has(id);
    }

    synchronized boolean removed(String id) {
        JSONObject entry = index.optJSONObject(id);
        return entry != null && entry.optBoolean("removed");
    }

    synchronized File cachedAudio(String id) {
        try {
            File local = file(id);
            return contains(id) && local.isFile() && local.length() > 0 ? local : null;
        } catch (Exception unavailable) {
            return null;
        }
    }

    synchronized void saveAndVerify(JSONObject message, File source) throws Exception {
        if (unreadable)
            throw new IOException(
                    "Saved voicemail metadata couldn't be read. No carrier deletion was"
                            + " requested.");
        String id = message.getString("objectid");
        File target = file(id);
        if (source == null || !source.isFile() || source.length() <= 0)
            throw new IOException("The recording isn't available to save.");
        if (contains(id) && source.getCanonicalFile().equals(target.getCanonicalFile())) {
            JSONObject previous = index.getJSONObject(id);
            if (previous.getLong("bytes") != target.length()
                    || !previous.getString("sha256").equals(digest(target)))
                throw new IOException(
                        "The saved recording has changed. No carrier deletion was requested.");
        }
        // A file hash alone cannot distinguish an audio download from a carrier error body.
        MediaMetadataRetriever media = new MediaMetadataRetriever();
        try {
            media.setDataSource(source.getAbsolutePath());
            if (Long.parseLong(media.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION))
                    <= 0) throw new IOException("The recording couldn't be verified as playable.");
        } finally {
            media.release();
        }
        String expected = digest(source);
        long length = source.length();
        if (!source.getCanonicalFile().equals(target.getCanonicalFile())) {
            File directory = target.getParentFile();
            if (!directory.isDirectory() && !directory.mkdirs())
                throw new IOException("Couldn't create saved recording storage.");
            AtomicFile atomic = new AtomicFile(target);
            FileOutputStream output = atomic.startWrite();
            try (InputStream input = new FileInputStream(source)) {
                byte[] buffer = new byte[8192];
                int n;
                while ((n = input.read(buffer)) != -1) output.write(buffer, 0, n);
                atomic.finishWrite(output);
            } catch (Exception failure) {
                atomic.failWrite(output);
                throw failure;
            }
        }
        if (target.length() != length || !expected.equals(digest(target)))
            throw new IOException("The saved recording didn't pass verification.");
        JSONObject updated = new JSONObject(index.toString());
        JSONObject copy = new JSONObject(message.toString());
        copy.remove("_saved");
        copy.remove("_carrier_removed");
        updated.put(
                id,
                new JSONObject()
                        .put("message", copy)
                        .put("sha256", expected)
                        .put("bytes", length)
                        .put("removed", removed(id))
                        .put("saved_at", System.currentTimeMillis()));
        commit(updated);
        JSONObject entry = index.getJSONObject(id);
        if (entry.getLong("bytes") != target.length()
                || !entry.getString("sha256").equals(digest(target)))
            throw new IOException("The saved recording couldn't be verified after saving.");
    }

    synchronized List<JSONObject> merge(List<JSONObject> carrier, boolean fresh) throws Exception {
        LinkedHashMap<String, JSONObject> merged = new LinkedHashMap<>();
        Set<String> present = new HashSet<>();
        for (JSONObject message : carrier) {
            String id = message.optString("objectid");
            merged.put(id, new JSONObject(message.toString()));
            present.add(id);
        }
        JSONObject updated = new JSONObject(index.toString());
        boolean changed = false;
        Iterator<String> ids = updated.keys();
        while (ids.hasNext()) {
            String id = ids.next();
            JSONObject entry = updated.getJSONObject(id);
            boolean removed = fresh ? !present.contains(id) : entry.optBoolean("removed");
            if (fresh && removed != entry.optBoolean("removed")) {
                entry.put("removed", removed);
                changed = true;
            }
            JSONObject message = merged.get(id);
            if (fresh
                    && message != null
                    && !message.optString("flag")
                            .equals(entry.getJSONObject("message").optString("flag"))) {
                entry.put("message", new JSONObject(message.toString()));
                changed = true;
            }
            if (message == null)
                message = new JSONObject(entry.getJSONObject("message").toString());
            message.put("_saved", true).put("_carrier_removed", removed);
            merged.put(id, message);
        }
        if (changed) commit(updated);
        return new ArrayList<>(merged.values());
    }

    synchronized void confirmRemoved(String id) throws Exception {
        JSONObject updated = new JSONObject(index.toString());
        updated.getJSONObject(id).put("removed", true);
        commit(updated);
    }

    synchronized void setRead(String id, boolean read) throws Exception {
        JSONObject updated = new JSONObject(index.toString());
        updated.getJSONObject(id).getJSONObject("message").put("flag", read ? "Seen" : "");
        commit(updated);
    }

    synchronized void removeLocal(String id) throws Exception {
        if (!removed(id))
            throw new IOException(
                    "Carrier removal must be confirmed before removing this saved copy.");
        JSONObject updated = new JSONObject(index.toString());
        updated.remove(id);
        File audio = file(id);
        new AtomicFile(audio).delete();
        if (audio.exists()
                || new File(audio.getPath() + ".bak").exists()
                || new File(audio.getPath() + ".new").exists())
            throw new IOException(
                    "Couldn't remove this saved recording. Retry after checking storage.");
        commit(updated);
    }
}
