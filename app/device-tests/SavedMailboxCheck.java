package org.visualvoicemail.checks;

import android.content.*;
import android.media.MediaPlayer;
import android.os.Bundle;

import org.json.JSONObject;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;

/** Isolated files and public audio only. No CarrierClient instance or network writes. */
public final class SavedMailboxCheck extends DeviceCheckInstrumentation {
    private int checks;

    private void check(boolean ok) throws Exception {
        checks++;
        if (!ok) throw new Exception("check " + checks);
    }

    private Object call(Object target, String name, Class<?>[] types, Object... args)
            throws Exception {
        Method m = target.getClass().getDeclaredMethod(name, types);
        m.setAccessible(true);
        return m.invoke(target, args);
    }

    private Object create(Context c) throws Exception {
        Constructor<?> constructor =
                Class.forName("org.visualvoicemail.app.SavedMailbox")
                        .getDeclaredConstructor(Context.class);
        constructor.setAccessible(true);
        return constructor.newInstance(c);
    }

    private void copy(InputStream input, File file) throws Exception {
        try (InputStream source = input;
                FileOutputStream output = new FileOutputStream(file)) {
            byte[] bytes = new byte[8192];
            int n;
            while ((n = source.read(bytes)) != -1) output.write(bytes, 0, n);
        }
    }

    @SuppressWarnings("unchecked")
    private List<JSONObject> merge(Object saved, List<JSONObject> carrier, boolean fresh)
            throws Exception {
        return (List<JSONObject>)
                call(saved, "merge", new Class<?>[] {List.class, boolean.class}, carrier, fresh);
    }

    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);
        start();
    }

    @Override
    public void onStart() {
        Bundle out = new Bundle();
        File root =
                new File(getTargetContext().getFilesDir(), "archive-check-" + UUID.randomUUID());
        root.mkdirs();
        Context isolated =
                new ContextWrapper(getTargetContext()) {
                    @Override
                    public Context getApplicationContext() {
                        return this;
                    }

                    @Override
                    public File getFilesDir() {
                        return root;
                    }
                };
        MediaPlayer player = null;
        try {
            Object saved = create(isolated);
            JSONObject message =
                    new JSONObject()
                            .put("objectid", "public-archive-fixture")
                            .put("flag", "")
                            .put("returnnumber", "+15550000100")
                            .put("date", "2026-01-01T12:00:00Z");
            File cache = new File(root, "audio");
            cache.mkdirs();
            File source = new File(cache, String.join("", Collections.nCopies(64, "a")) + ".audio");
            copy(getContext().getAssets().open("speech-fixture.wav"), source);
            Class<?>[] saveTypes = {JSONObject.class, File.class};
            call(saved, "saveAndVerify", saveTypes, message, source);
            check(
                    (Boolean)
                            call(
                                    saved,
                                    "contains",
                                    new Class<?>[] {String.class},
                                    message.getString("objectid")));
            check(
                    !(Boolean)
                            call(
                                    saved,
                                    "removed",
                                    new Class<?>[] {String.class},
                                    message.getString("objectid")));
            File local =
                    (File)
                            call(
                                    saved,
                                    "cachedAudio",
                                    new Class<?>[] {String.class},
                                    message.getString("objectid"));
            check(local != null && local.length() == source.length() && !local.equals(source));
            byte[] metadata =
                    java.nio.file.Files.readAllBytes(new File(root, "saved-mailbox.bin").toPath());
            check(
                    !new String(metadata, java.nio.charset.StandardCharsets.ISO_8859_1)
                            .contains("public-archive-fixture"));
            List<JSONObject> list = merge(saved, Arrays.asList(message), true);
            check(
                    list.size() == 1
                            && list.get(0).optBoolean("_saved")
                            && !list.get(0).optBoolean("_carrier_removed"));
            check(!message.has("_saved"));
            list = merge(saved, Collections.emptyList(), true);
            check(list.size() == 1 && list.get(0).optBoolean("_carrier_removed"));
            // Cold-start loading, including local read updates without a carrier connection.
            saved = create(isolated);
            check(
                    (Boolean)
                            call(
                                    saved,
                                    "removed",
                                    new Class<?>[] {String.class},
                                    message.getString("objectid")));
            call(
                    saved,
                    "setRead",
                    new Class<?>[] {String.class, boolean.class},
                    message.getString("objectid"),
                    true);
            saved = create(isolated);
            list = merge(saved, Collections.emptyList(), false);
            check("Seen".equals(list.get(0).optString("flag")));
            Method clear =
                    Class.forName("org.visualvoicemail.app.RecordingCache")
                            .getMethod("clear", File.class);
            clear.invoke(null, cache);
            check(!source.exists() && local.isFile());
            player = new MediaPlayer();
            player.setDataSource(local.getAbsolutePath());
            player.prepare();
            check(player.getDuration() > 0);
            player.start();
            Thread.sleep(300);
            check(player.isPlaying());
            player.release();
            player = null;
            // Reappearance must be reconciled, never hidden under a stale success label.
            message.put("flag", "Seen");
            list = merge(saved, Arrays.asList(message), true);
            check(!list.get(0).optBoolean("_carrier_removed"));
            check(
                    "Seen"
                            .equals(
                                    merge(create(isolated), Collections.emptyList(), false)
                                            .get(0)
                                            .optString("flag")));
            boolean blocked = false;
            try {
                call(
                        saved,
                        "removeLocal",
                        new Class<?>[] {String.class},
                        message.getString("objectid"));
            } catch (Exception expected) {
                blocked = true;
            }
            check(blocked && local.exists());
            // A corrupt recording cannot authorize removal.
            try (FileOutputStream output = new FileOutputStream(local, true)) {
                output.write(0);
            }
            blocked = false;
            try {
                call(saved, "saveAndVerify", saveTypes, message, local);
            } catch (Exception expected) {
                blocked = true;
            }
            check(blocked);
            File invalid = new File(root, "invalid.audio");
            try (FileOutputStream output = new FileOutputStream(invalid)) {
                output.write(new byte[] {1, 2, 3});
            }
            blocked = false;
            try {
                call(
                        saved,
                        "saveAndVerify",
                        saveTypes,
                        new JSONObject().put("objectid", "invalid"),
                        invalid);
            } catch (Exception expected) {
                blocked = true;
            }
            check(
                    blocked
                            && !(Boolean)
                                    call(
                                            saved,
                                            "contains",
                                            new Class<?>[] {String.class},
                                            "invalid"));
            call(
                    saved,
                    "confirmRemoved",
                    new Class<?>[] {String.class},
                    message.getString("objectid"));
            call(
                    saved,
                    "removeLocal",
                    new Class<?>[] {String.class},
                    message.getString("objectid"));
            check(
                    !local.exists()
                            && merge(create(isolated), Collections.emptyList(), false).isEmpty());
            // Unreadable encrypted metadata fails closed instead of replacing old saved data.
            try (FileOutputStream output =
                    new FileOutputStream(new File(root, "saved-mailbox.bin"))) {
                output.write(1);
            }
            saved = create(isolated);
            blocked = false;
            copy(getContext().getAssets().open("speech-fixture.wav"), source);
            try {
                call(saved, "saveAndVerify", saveTypes, message, source);
            } catch (Exception expected) {
                blocked = true;
            }
            check(blocked && new File(root, "saved-mailbox.bin").length() == 1);
            out.putBoolean("passed", true);
        } catch (Throwable error) {
            while (error.getCause() != null) error = error.getCause();
            out.putString("failure_class", error.getClass().getSimpleName());
            out.putString("failure", error.getMessage());
        } finally {
            if (player != null) player.release();
            // This random directory was created here and contains only disposable fixtures.
            remove(root);
        }
        out.putInt("checks", checks);
        finish(-1, out);
    }

    private void remove(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) remove(child);
        file.delete();
    }
}
