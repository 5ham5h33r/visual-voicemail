package org.visualvoicemail.app;

import java.io.File;
import java.io.IOException;

/** Restricts cache operations to known recording files, never inbox/session records. */
public final class RecordingCache {
    private RecordingCache() { }
    private static boolean recording(File directory, File file) throws IOException {
        return file.isFile() && file.getName().matches("[0-9a-f]{64}\\.audio(?:\\.(?:new|bak))?")
            && directory.getCanonicalFile().equals(file.getCanonicalFile().getParentFile());
    }
    public static long[] stats(File directory) throws IOException {
        long count = 0, bytes = 0;
        File[] files = directory.listFiles();
        if (files == null && directory.exists()) throw new IOException("Downloaded audio storage couldn't be opened.");
        if (files != null) for (File file : files) if (recording(directory, file)) {
            bytes += file.length(); if (file.getName().endsWith(".audio")) count++;
        }
        return new long[]{count, bytes};
    }
    public static void clear(File directory) throws IOException {
        File[] files = directory.listFiles();
        if (files == null) {
            if (directory.exists()) throw new IOException("Downloaded audio storage couldn't be opened.");
            return;
        }
        for (File file : files) if (recording(directory, file) && !file.delete())
            throw new IOException("Some downloads couldn't be cleared. Try again.");
    }
}
