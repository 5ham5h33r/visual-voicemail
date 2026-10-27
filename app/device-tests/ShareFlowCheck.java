package org.visualvoicemail.checks;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import android.provider.OpenableColumns;

import org.json.JSONObject;

import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Public synthetic data only. URI grants are tested from the separate shell UID and revoked. */
public final class ShareFlowCheck extends DeviceCheckInstrumentation {
    private int checks;

    private void check(boolean value) throws Exception {
        checks++;
        if (!value) throw new Exception("check " + checks);
    }

    private Method method(Class<?> cls, String name, Class<?>... types) throws Exception {
        Method m = cls.getDeclaredMethod(name, types);
        m.setAccessible(true);
        return m;
    }

    private byte[] read(InputStream input) throws Exception {
        try (InputStream in = input;
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] bytes = new byte[1024];
            int n;
            while ((n = in.read(bytes)) != -1) out.write(bytes, 0, n);
            return out.toByteArray();
        }
    }

    private byte[] shellRead(Uri uri) throws Exception {
        ParcelFileDescriptor[] pipes =
                getUiAutomation().executeShellCommandRwe("content read --uri " + uri.toString());
        pipes[1].close();
        byte[] output = read(new ParcelFileDescriptor.AutoCloseInputStream(pipes[0]));
        byte[] error = read(new ParcelFileDescriptor.AutoCloseInputStream(pipes[2]));
        return error.length == 0 ? output : error;
    }

    private boolean denied(byte[] result) {
        String message = new String(result, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
        return message.contains("permission denial")
                || message.contains("securityexception")
                || message.contains("not exported");
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
        Uri uri = null;
        File shared = null;
        File fixture = new File(c.getCacheDir(), "share-check-" + UUID.randomUUID() + ".amr");
        try {
            byte[] bytes =
                    "#!AMR\nPublic synthetic share fixture, no personal audio."
                            .getBytes(StandardCharsets.UTF_8);
            try (FileOutputStream file = new FileOutputStream(fixture)) {
                file.write(bytes);
            }
            Class<?> provider = Class.forName("org.visualvoicemail.app.ShareProvider");
            uri =
                    (Uri)
                            method(provider, "prepare", Context.class, File.class, String.class)
                                    .invoke(null, c, fixture, "2026-01-01T12:00:00Z");
            shared =
                    new File(
                            new File(c.getCacheDir(), "sharing"),
                            uri.getPathSegments().get(0) + ".amr");
            check("content".equals(uri.getScheme()));
            check("audio/amr".equals(c.getContentResolver().getType(uri)));
            try (Cursor cursor = c.getContentResolver().query(uri, null, null, null, null)) {
                check(cursor != null && cursor.moveToFirst());
                check(
                        cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                                .startsWith("Voicemail_"));
                check(
                        cursor.getLong(cursor.getColumnIndexOrThrow(OpenableColumns.SIZE))
                                == bytes.length);
            }
            check(Arrays.equals(bytes, read(c.getContentResolver().openInputStream(uri))));
            boolean blocked = false;
            try {
                c.getContentResolver().openFileDescriptor(uri, "rw");
            } catch (Exception expected) {
                blocked = true;
            }
            check(
                    blocked
                            && Arrays.equals(
                                    bytes, java.nio.file.Files.readAllBytes(shared.toPath())));
            Uri traversal =
                    Uri.parse("content://" + c.getPackageName() + ".sharing/../session.bin");
            blocked = false;
            try {
                c.getContentResolver().openInputStream(traversal);
            } catch (Exception expected) {
                blocked = true;
            }
            check(blocked);
            check(denied(shellRead(uri)));
            c.grantUriPermission("com.android.shell", uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            check(Arrays.equals(bytes, shellRead(uri)));
            c.revokeUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            check(denied(shellRead(uri)));
            Class<?> intents = Class.forName("org.visualvoicemail.app.ShareIntents");
            Intent audio =
                    (Intent)
                            method(intents, "audio", Uri.class, String.class)
                                    .invoke(null, uri, "audio/amr");
            check(
                    Intent.ACTION_SEND.equals(audio.getAction())
                            && "audio/amr".equals(audio.getType()));
            check(
                    (audio.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0
                            && (audio.getFlags() & Intent.FLAG_GRANT_WRITE_URI_PERMISSION) == 0);
            check(
                    uri.equals(audio.getParcelableExtra(Intent.EXTRA_STREAM))
                            && uri.equals(audio.getClipData().getItemAt(0).getUri()));
            Class<?> transcript = Class.forName("org.visualvoicemail.app.Transcript");
            Object sample =
                    method(transcript, "parse", JSONObject.class)
                            .invoke(
                                    null,
                                    new JSONObject(
                                            "{\"version\":2,\"task\":\"translate\",\"segments\":[{\"start\":61000,\"end\":62000,\"text\":\"Public"
                                                + " text fixture\"}]}"));
            Intent text = (Intent) method(intents, "transcript", transcript).invoke(null, sample);
            check(
                    "text/plain".equals(text.getType())
                            && Intent.ACTION_SEND.equals(text.getAction()));
            check(
                    text.getStringExtra(Intent.EXTRA_TEXT)
                            .contains("English translation\n\n1:01  Public text fixture"));
            check(
                    text.getComponent() == null
                            && text.getPackage() == null
                            && !text.hasExtra(Intent.EXTRA_EMAIL));
            Intent chooser = Intent.createChooser(text, "Share voicemail");
            check(Intent.ACTION_CHOOSER.equals(chooser.getAction()));
            check(shared.setLastModified(System.currentTimeMillis() - 25 * 60 * 60 * 1000L));
            blocked = false;
            try {
                c.getContentResolver().openInputStream(uri);
            } catch (Exception expected) {
                blocked = true;
            }
            check(blocked && fixture.exists());
            out.putBoolean("passed", true);
        } catch (Throwable error) {
            while (error.getCause() != null) error = error.getCause();
            out.putString("failure_class", error.getClass().getSimpleName());
            out.putString("failure", error.getMessage());
        } finally {
            if (uri != null) c.revokeUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            if (shared != null) shared.delete();
            fixture.delete();
        }
        out.putInt("checks", checks);
        finish(-1, out);
    }
}
