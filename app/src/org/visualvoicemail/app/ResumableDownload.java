package org.visualvoicemail.app;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.Properties;
import java.util.regex.*;

/** Pinned public model files only. Incomplete files are never promoted before full verification. */
final class ResumableDownload {
    interface Control {
        boolean stopped();

        void progress(long bytes);
    }

    interface Source {
        HttpURLConnection open(String url) throws IOException;
    }

    private final Source source;
    private volatile HttpURLConnection active;
    private volatile boolean stopped;

    ResumableDownload() {
        this(url -> (HttpURLConnection) new URL(url).openConnection());
    }

    ResumableDownload(Source source) {
        this.source = source;
    }

    void stop() {
        stopped = true;
        HttpURLConnection connection = active;
        if (connection != null) {
            Thread close = new Thread(connection::disconnect, "pause-model-transfer");
            close.setDaemon(true);
            close.start();
        }
    }

    private void check(Control control) throws InterruptedIOException {
        if (stopped || control.stopped() || Thread.currentThread().isInterrupted())
            throw new InterruptedIOException("Download paused");
    }

    static long retained(File target, String url, long size, String sha) {
        File part = new File(target + ".part"), meta = new File(target + ".part.meta");
        try {
            Properties p = new Properties();
            try (InputStream input = new FileInputStream(meta)) {
                p.load(input);
            }
            if (url.equals(p.getProperty("url"))
                    && sha.equals(p.getProperty("sha"))
                    && String.valueOf(size).equals(p.getProperty("size"))
                    && part.isFile()
                    && part.length() <= size) return part.length();
        } catch (Exception ignored) {
        }
        return 0;
    }

    private static void metadata(File meta, String url, long size, String sha, String etag)
            throws IOException {
        Properties p = new Properties();
        p.setProperty("url", url);
        p.setProperty("sha", sha);
        p.setProperty("size", String.valueOf(size));
        p.setProperty("etag", etag == null ? "" : etag);
        File temporary = new File(meta + ".new");
        try (FileOutputStream output = new FileOutputStream(temporary)) {
            p.store(output, "Public model transfer");
            output.getFD().sync();
        }
        Files.move(temporary.toPath(), meta.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    private static String etag(File meta) {
        Properties p = new Properties();
        try (InputStream input = new FileInputStream(meta)) {
            p.load(input);
            String tag = p.getProperty("etag", "");
            return tag.startsWith("\"") && tag.endsWith("\"") ? tag : "";
        } catch (Exception ignored) {
            return "";
        }
    }

    static String digest(File file) throws Exception {
        MessageDigest hash = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[65536];
            int n;
            while ((n = input.read(buffer)) != -1) hash.update(buffer, 0, n);
        }
        StringBuilder result = new StringBuilder();
        for (byte b : hash.digest())
            result.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
        return result.toString();
    }

    private static void discard(File part, File meta) throws IOException {
        Files.deleteIfExists(part.toPath());
        Files.deleteIfExists(meta.toPath());
        Files.deleteIfExists(new File(meta + ".new").toPath());
    }

    void fetch(File target, String url, long size, String sha, Control control) throws Exception {
        if (size <= 0 || !sha.matches("[0-9a-f]{64}"))
            throw new IOException("Invalid model manifest");
        File part = new File(target + ".part"), meta = new File(target + ".part.meta");
        check(control);
        if (target.isFile() && target.length() == size && sha.equals(digest(target))) {
            discard(part, meta);
            control.progress(size);
            return;
        }
        long offset = retained(target, url, size, sha);
        if (offset == 0) discard(part, meta);
        if (offset == size) {
            if (!sha.equals(digest(part))) {
                discard(part, meta);
                throw new IOException("Download verification failed; retry this file");
            }
            promote(part, meta, target);
            control.progress(size);
            return;
        }
        // One full retry is permitted when an old range is no longer satisfiable.
        for (int attempt = 0; attempt < 2; attempt++) {
            check(control);
            HttpURLConnection connection = source.open(url);
            active = connection;
            connection.setConnectTimeout(20000);
            connection.setReadTimeout(20000);
            connection.setRequestProperty("Accept-Encoding", "identity");
            if (offset > 0) {
                connection.setRequestProperty("Range", "bytes=" + offset + "-");
                String tag = etag(meta);
                if (!tag.isEmpty()) connection.setRequestProperty("If-Range", tag);
            }
            try {
                int code = connection.getResponseCode();
                check(control);
                if (code == 416 && offset > 0 && attempt == 0) {
                    discard(part, meta);
                    offset = 0;
                    continue;
                }
                if (code != 200 && code != 206)
                    throw new IOException("Model host unavailable; retry later");
                String encoding = connection.getContentEncoding();
                if (encoding != null
                        && !encoding.isEmpty()
                        && !"identity".equalsIgnoreCase(encoding))
                    throw new IOException("Unexpected model encoding");
                if (code == 206) {
                    String range = connection.getHeaderField("Content-Range");
                    Matcher match =
                            Pattern.compile("bytes ([0-9]+)-([0-9]+)/([0-9]+)")
                                    .matcher(range == null ? "" : range);
                    if (!match.matches()
                            || Long.parseLong(match.group(1)) != offset
                            || Long.parseLong(match.group(2)) < offset
                            || Long.parseLong(match.group(2)) >= size
                            || Long.parseLong(match.group(3)) != size) {
                        discard(part, meta);
                        throw new IOException("Invalid download continuation; retry this file");
                    }
                } else offset = 0; // The host may ignore Range or return a changed representation.
                metadata(meta, url, size, sha, connection.getHeaderField("ETag"));
                long received = offset;
                control.progress(received);
                try (InputStream input = connection.getInputStream();
                        FileOutputStream output = new FileOutputStream(part, offset > 0)) {
                    try {
                        byte[] buffer = new byte[65536];
                        int n;
                        while (true) {
                            check(control);
                            n = input.read(buffer);
                            if (n < 0) break;
                            if (n > size - received)
                                throw new IOException("Download exceeded expected size");
                            output.write(buffer, 0, n);
                            received += n;
                            control.progress(received);
                        }
                    } finally {
                        output.getFD().sync();
                    }
                }
                check(control);
                if (received != size)
                    throw new IOException("Download interrupted; saved progress is kept");
                if (!sha.equals(digest(part))) {
                    discard(part, meta);
                    throw new IOException("Download verification failed; retry this file");
                }
                check(control);
                promote(part, meta, target);
                control.progress(size);
                return;
            } finally {
                active = null;
                connection.disconnect();
            }
        }
        throw new IOException("Download continuation unavailable; retry");
    }

    private static void promote(File part, File meta, File target) throws IOException {
        Files.move(part.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        Files.deleteIfExists(meta.toPath());
    }
}
