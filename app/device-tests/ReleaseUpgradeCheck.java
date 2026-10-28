package org.visualvoicemail.checks;

import android.content.*;
import android.content.pm.*;
import android.os.*;

import org.json.*;

import java.io.*;
import java.lang.reflect.*;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;

/** Emulator-only witness for an in-place development-to-release signing upgrade. */
public final class ReleaseUpgradeCheck extends DeviceCheckInstrumentation {
    private String stage;
    private int checks;

    private void check(boolean good) throws Exception {
        checks++;
        if (!good) throw new Exception("check " + checks);
    }

    @Override
    public void onCreate(Bundle args) {
        super.onCreate(args);
        stage = args.getString("release_stage", "");
        start();
    }

    private void hashFiles(File root, File item, MessageDigest digest) throws Exception {
        if (item.isDirectory()) {
            File[] children = item.listFiles();
            if (children == null) throw new IOException();
            Arrays.sort(children, Comparator.comparing(File::getName));
            for (File child : children) hashFiles(root, child, digest);
        } else {
            digest.update(root.toPath().relativize(item.toPath()).toString().getBytes("UTF-8"));
            try (InputStream input = new FileInputStream(item)) {
                byte[] bytes = new byte[32768];
                int count;
                while ((count = input.read(bytes)) != -1) digest.update(bytes, 0, count);
            }
        }
    }

    private String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte value : bytes) result.append(String.format(Locale.ROOT, "%02x", value & 255));
        return result.toString();
    }

    @Override
    public void onStart() {
        Bundle out = new Bundle();
        Context c = getTargetContext();
        File witness = new File(c.getCacheDir(), "release-upgrade-witness.json");
        try {
            check(Arrays.asList("ranchu", "goldfish").contains(Build.HARDWARE));
            check(!new File(c.getFilesDir(), "session.bin").exists());
            check(stage.equals("before") || stage.equals("after"));
            Class<?> storage = Class.forName("org.visualvoicemail.app.PrivateStore");
            Constructor<?> constructor = storage.getDeclaredConstructor(Context.class);
            constructor.setAccessible(true);
            Method get = storage.getDeclaredMethod("get", String.class);
            get.setAccessible(true);
            JSONObject inbox = (JSONObject) get.invoke(constructor.newInstance(c), "inbox.bin");
            check(inbox != null);
            JSONArray messages = inbox.getJSONArray("messages");
            check(messages.length() > 0);
            for (int i = 0; i < messages.length(); i++)
                check(
                        messages.getJSONObject(i)
                                .optString("objectid")
                                .startsWith("emulator-sample-"));
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            hashFiles(c.getFilesDir(), c.getFilesDir(), digest);
            JSONObject current =
                    new JSONObject()
                            .put("files", hex(digest.digest()))
                            .put(
                                    "preferences",
                                    hex(
                                            digest.digest(
                                                    new TreeMap<String, Object>(
                                                                    c.getSharedPreferences(
                                                                                    "settings", 0)
                                                                            .getAll())
                                                            .toString()
                                                            .getBytes("UTF-8"))));
            boolean debuggable =
                    (c.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
            if (stage.equals("before")) {
                check(debuggable);
                check(!witness.exists());
                try (FileOutputStream output = new FileOutputStream(witness)) {
                    output.write(current.toString().getBytes("UTF-8"));
                    output.getFD().sync();
                }
            } else {
                check(!debuggable);
                check(witness.isFile());
                JSONObject previous =
                        new JSONObject(new String(Files.readAllBytes(witness.toPath()), "UTF-8"));
                check(previous.getString("files").equals(current.getString("files")));
                check(previous.getString("preferences").equals(current.getString("preferences")));
                PackageInfo info =
                        c.getPackageManager()
                                .getPackageInfo(
                                        c.getPackageName(),
                                        PackageManager.GET_SIGNING_CERTIFICATES);
                check(info.getLongVersionCode() >= 32);
                check(
                        !info.signingInfo.hasMultipleSigners()
                                && info.signingInfo.getSigningCertificateHistory().length >= 2);
                check(witness.delete());
                out.putBoolean("encrypted_data_and_preferences_preserved", true);
                out.putBoolean("non_debuggable", true);
            }
            out.putBoolean("passed", true);
        } catch (Throwable error) {
            while (error.getCause() != null) error = error.getCause();
            out.putString("failure_class", error.getClass().getSimpleName());
            out.putString(
                    "failure",
                    error.getMessage() != null && error.getMessage().startsWith("check ")
                            ? error.getMessage()
                            : "release witness failed");
        }
        out.putInt("checks", checks);
        finish(-1, out);
    }
}
