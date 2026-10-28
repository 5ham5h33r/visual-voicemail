package org.visualvoicemail.checks;

import android.app.*;
import android.content.*;
import android.os.*;
import android.widget.*;

import org.json.JSONObject;

import java.io.*;
import java.lang.reflect.*;
import java.nio.file.Files;
import java.util.*;

/** Explicit opt-in public Tiny download on an emulator. Two runs exercise process restart. */
public final class DownloadRecoveryCheck extends DeviceCheckInstrumentation {
    private String stage;
    private int checks;
    private Activity screen;

    private Object call(Class<?> type, String name, Class<?>[] types, Object... args)
            throws Exception {
        Method m = type.getDeclaredMethod(name, types);
        m.setAccessible(true);
        return m.invoke(null, args);
    }

    private Object field(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }

    private boolean flag(Class<?> type, String name) throws Exception {
        Field f = type.getDeclaredField(name);
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
        stage = b.getString("stage", "pause");
        start();
    }

    @Override
    public void onStart() {
        Bundle out = new Bundle();
        Context c = getTargetContext();
        SharedPreferences prefs = c.getSharedPreferences("settings", 0);
        String previous = prefs.getString("transcription_quality", "standard");
        Class<?> speech = null;
        try {
            check(Arrays.asList("ranchu", "goldfish").contains(Build.HARDWARE));
            speech = Class.forName("org.visualvoicemail.app.Transcription");
            Class<?> quality = Class.forName("org.visualvoicemail.app.TranscriptionQuality");
            Object standard = quality.getField("STANDARD").get(null);
            check(
                    (Boolean)
                            call(
                                    speech,
                                    "select",
                                    new Class<?>[] {Context.class, String.class},
                                    c,
                                    "standard"));
            File root = new File(c.getFilesDir(), "speech-model"),
                    checkpoint = new File(c.getCacheDir(), "public-model-check.json");
            Class<?> transfer = Class.forName("org.visualvoicemail.app.ResumableDownload");
            if ("pause".equals(stage)) {
                check(!(Boolean) call(speech, "ready", new Class<?>[] {Context.class}, c));
                call(speech, "download", new Class<?>[] {Context.class}, c);
                File partial = null;
                long deadline = SystemClock.uptimeMillis() + 45000;
                while (SystemClock.uptimeMillis() < deadline && flag(speech, "busy")) {
                    File[] files = root.listFiles((dir, name) -> name.endsWith(".part"));
                    if (files != null)
                        for (File file : files)
                            if (file.length() >= 65536) {
                                partial = file;
                                break;
                            }
                    if (partial != null) break;
                    Thread.sleep(10);
                }
                call(speech, "cancel", new Class<?>[0]);
                deadline = SystemClock.uptimeMillis() + 25000;
                while (flag(speech, "busy") && SystemClock.uptimeMillis() < deadline)
                    Thread.sleep(50);
                check(!flag(speech, "busy"));
                check(partial != null && partial.isFile() && partial.length() > 0);
                check(new File(partial + ".meta").isFile());
                check(!(Boolean) call(speech, "ready", new Class<?>[] {Context.class}, c));
                JSONObject state =
                        new JSONObject()
                                .put("file", partial.getName())
                                .put("length", partial.length())
                                .put(
                                        "hash",
                                        call(
                                                transfer,
                                                "digest",
                                                new Class<?>[] {File.class},
                                                partial));
                Files.write(checkpoint.toPath(), state.toString().getBytes("UTF-8"));
                screen =
                        startActivitySync(
                                new Intent()
                                        .setClassName(
                                                c,
                                                "org.visualvoicemail.app.TranscriptionSettingsActivity")
                                        .addFlags(
                                                Intent.FLAG_ACTIVITY_NEW_TASK
                                                        | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                Thread.sleep(500);
                check(
                        ((Button) field(screen, "download"))
                                .getText()
                                .toString()
                                .startsWith("Resume download"));
                check(
                        ((android.view.View) field(screen, "remove")).getVisibility()
                                == android.view.View.VISIBLE);
                out.putBoolean("partial_kept", true);
            } else {
                check(checkpoint.isFile());
                JSONObject state =
                        new JSONObject(
                                new String(Files.readAllBytes(checkpoint.toPath()), "UTF-8"));
                check(state.getString("file").matches("[a-zA-Z0-9._-]+\\.part"));
                File partial = new File(root, state.getString("file"));
                check(partial.length() == state.getLong("length"));
                check(
                        state.getString("hash")
                                .equals(
                                        call(
                                                transfer,
                                                "digest",
                                                new Class<?>[] {File.class},
                                                partial)));
                check(
                        ((String) call(speech, "downloadAction", new Class<?>[] {Context.class}, c))
                                .startsWith("Resume"));
                call(speech, "download", new Class<?>[] {Context.class}, c);
                long deadline = SystemClock.uptimeMillis() + 180000;
                while (flag(speech, "busy") && SystemClock.uptimeMillis() < deadline)
                    Thread.sleep(100);
                check(!flag(speech, "busy"));
                check((Boolean) call(speech, "ready", new Class<?>[] {Context.class}, c));
                String[] names = (String[]) quality.getMethod("names").invoke(standard),
                        hashes = (String[]) quality.getMethod("hashes").invoke(standard);
                for (int i = 0; i < names.length; i++)
                    check(
                            hashes[i].equals(
                                    call(
                                            transfer,
                                            "digest",
                                            new Class<?>[] {File.class},
                                            new File(root, names[i]))));
                check(!partial.exists() && !new File(partial + ".meta").exists());
                checkpoint.delete();
                out.putBoolean("restart_resume_verified", true);
            }
            out.putBoolean("passed", true);
        } catch (Throwable error) {
            while (error.getCause() != null) error = error.getCause();
            out.putString("failure_class", error.getClass().getSimpleName());
            out.putString(
                    "failure",
                    error.getMessage() != null && error.getMessage().startsWith("check ")
                            ? error.getMessage()
                            : "public model recovery check failed");
        } finally {
            if (speech != null)
                try {
                    if (flag(speech, "busy")) call(speech, "cancel", new Class<?>[0]);
                } catch (Exception ignored) {
                }
            if (screen != null) runOnMainSync(() -> screen.finish());
            prefs.edit().putString("transcription_quality", previous).commit();
        }
        out.putInt("checks", checks);
        finish(-1, out);
    }
}
