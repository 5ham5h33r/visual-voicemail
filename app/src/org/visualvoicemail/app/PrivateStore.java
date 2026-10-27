package org.visualvoicemail.app;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import org.json.JSONObject;

/** Authenticated local JSON storage; its encryption key never leaves Android Keystore. */
final class PrivateStore {
    // AtomicFile and key creation need one process-wide lock across UI and background clients.
    private static final Object LOCK = new Object();
    private static final String ALIAS = "voice-mail-private-storage";
    private final Context context;
    PrivateStore(Context context) { this.context = context.getApplicationContext(); }

    private SecretKey key() throws Exception {
        KeyStore keys = KeyStore.getInstance("AndroidKeyStore");
        keys.load(null);
        if (keys.containsAlias(ALIAS)) return (SecretKey) keys.getKey(ALIAS, null);
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,
            KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return generator.generateKey();
    }

    boolean put(String name, JSONObject value) {
        synchronized (LOCK) {
            AtomicFile file = new AtomicFile(new File(context.getFilesDir(), name));
            FileOutputStream output = null;
            try {
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.ENCRYPT_MODE, key());
                byte[] iv = cipher.getIV();
                byte[] encrypted = cipher.doFinal(value.toString().getBytes(StandardCharsets.UTF_8));
                byte[] record = ByteBuffer.allocate(2 + iv.length + encrypted.length)
                    .put((byte) 1).put((byte) iv.length).put(iv).put(encrypted).array();
                output = file.startWrite(); output.write(record); file.finishWrite(output);
                return true;
            } catch (Exception ignored) {
                if (output != null) file.failWrite(output);
                return false;
            }
        }
    }

    JSONObject get(String name) {
        synchronized (LOCK) {
            try {
                byte[] record = new AtomicFile(new File(context.getFilesDir(), name)).readFully();
                if (record.length < 30 || record[0] != 1 || record[1] != 12) return null;
                ByteBuffer bytes = ByteBuffer.wrap(record); bytes.position(2);
                byte[] iv = new byte[12]; bytes.get(iv);
                byte[] encrypted = new byte[bytes.remaining()]; bytes.get(encrypted);
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
                return new JSONObject(new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8));
            } catch (Exception ignored) { return null; }
        }
    }
}
