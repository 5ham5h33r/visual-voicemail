package org.visualvoicemail.app;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;

/** Deterministic HTTP responses; no external network, model download, or private data. */
public final class ResumableDownloadTest {
    private static int checks;
    private static final String URL = "https://models.example.test/pinned.bin";
    private static byte[] bytes = new byte[212345];
    private static String sha;
    private static Path root;

    private static void check(boolean yes) {
        checks++;
        if (!yes) throw new AssertionError("check " + checks);
    }

    private static File target(String name) throws Exception {
        Path p = Files.createDirectory(root.resolve(name));
        return p.resolve("model.bin").toFile();
    }

    private static File part(File target) {
        return new File(target + ".part");
    }

    private static class Control implements ResumableDownload.Control {
        long stopAt, progress;
        boolean stopped;

        Control(long stopAt) {
            this.stopAt = stopAt;
        }

        public boolean stopped() {
            return stopped;
        }

        public void progress(long n) {
            progress = n;
            if (stopAt > 0 && n >= stopAt) stopped = true;
        }
    }

    private static class Response extends HttpURLConnection {
        int code;
        byte[] body;
        Map<String, String> headers = new HashMap<>();
        boolean closed;

        Response(int code, int offset, byte[] data) throws IOException {
            super(new URL(URL));
            this.code = code;
            body = Arrays.copyOfRange(data, Math.min(offset, data.length), data.length);
            if (code == 206)
                headers.put(
                        "Content-Range",
                        "bytes " + offset + "-" + (bytes.length - 1) + "/" + bytes.length);
            headers.put("ETag", "\"release-one\"");
        }

        public void disconnect() {
            closed = true;
        }

        public boolean usingProxy() {
            return false;
        }

        public void connect() {}

        public int getResponseCode() {
            return code;
        }

        public InputStream getInputStream() {
            return new ByteArrayInputStream(body);
        }

        public String getHeaderField(String name) {
            return headers.get(name);
        }

        public String getContentEncoding() {
            return headers.get("Content-Encoding");
        }
    }

    private static Exception failed(ResumableDownload download, File target, Control c)
            throws Exception {
        try {
            download.fetch(target, URL, bytes.length, sha, c);
            throw new AssertionError("Expected transfer failure");
        } catch (Exception expected) {
            checks++;
            return expected;
        }
    }

    private static void pause(File target) throws Exception {
        Response first = new Response(200, 0, bytes);
        Exception error = failed(new ResumableDownload(url -> first), target, new Control(65536));
        check(error instanceof InterruptedIOException);
        check(part(target).length() == 65536 && !target.exists());
        check(ResumableDownload.retained(target, URL, bytes.length, sha) == 65536);
        check(first.closed);
    }

    public static void main(String[] args) throws Exception {
        root = Files.createTempDirectory("model-transfer-check-").toAbsolutePath().normalize();
        try {
            new Random(73).nextBytes(bytes);
            File fixture = root.resolve("fixture.bin").toFile();
            Files.write(fixture.toPath(), bytes);
            sha = ResumableDownload.digest(fixture);
            File resumed = target("resume");
            pause(resumed);
            Response range = new Response(206, 65536, bytes);
            Control progress = new Control(0);
            new ResumableDownload(url -> range).fetch(resumed, URL, bytes.length, sha, progress);
            check(Arrays.equals(bytes, Files.readAllBytes(resumed.toPath())));
            check("bytes=65536-".equals(range.getRequestProperty("Range")));
            check("\"release-one\"".equals(range.getRequestProperty("If-Range")));
            check(
                    progress.progress == bytes.length
                            && !part(resumed).exists()
                            && !new File(resumed + ".part.meta").exists());
            new ResumableDownload(
                            url -> {
                                throw new AssertionError(
                                        "Verified target should not request network");
                            })
                    .fetch(resumed, URL, bytes.length, sha, new Control(0));
            check(resumed.isFile());

            File ignored = target("range-ignored");
            pause(ignored);
            Response full = new Response(200, 0, bytes);
            new ResumableDownload(url -> full)
                    .fetch(ignored, URL, bytes.length, sha, new Control(0));
            check(sha.equals(ResumableDownload.digest(ignored)));

            File reset = target("range-expired");
            pause(reset);
            Response noRange = new Response(416, 0, new byte[0]),
                    again = new Response(200, 0, bytes);
            int[] calls = {0};
            new ResumableDownload(url -> calls[0]++ == 0 ? noRange : again)
                    .fetch(reset, URL, bytes.length, sha, new Control(0));
            check(calls[0] == 2 && again.getRequestProperty("Range") == null);
            check(sha.equals(ResumableDownload.digest(reset)));

            File wrong = target("wrong-range");
            pause(wrong);
            Response badRange = new Response(206, 65536, bytes);
            badRange.headers.put("Content-Range", "bytes 0-212344/212345");
            failed(new ResumableDownload(url -> badRange), wrong, new Control(0));
            check(!part(wrong).exists() && !wrong.exists());

            File corrupt = target("corrupt");
            byte[] changed = bytes.clone();
            changed[100] ^= 1;
            Response corruptBody = new Response(200, 0, changed);
            failed(new ResumableDownload(url -> corruptBody), corrupt, new Control(0));
            check(!part(corrupt).exists() && !corrupt.exists());

            File tampered = target("tampered-prefix");
            pause(tampered);
            try (RandomAccessFile file = new RandomAccessFile(part(tampered), "rw")) {
                file.writeByte(9);
            }
            failed(
                    new ResumableDownload(url -> new Response(206, 65536, bytes)),
                    tampered,
                    new Control(0));
            check(!tampered.exists() && !part(tampered).exists());

            File truncated = target("truncated");
            Response shortBody = new Response(200, 0, Arrays.copyOf(bytes, 10000));
            failed(new ResumableDownload(url -> shortBody), truncated, new Control(0));
            check(part(truncated).length() == 10000);
            new ResumableDownload(url -> new Response(206, 10000, bytes))
                    .fetch(truncated, URL, bytes.length, sha, new Control(0));
            check(sha.equals(ResumableDownload.digest(truncated)));

            File unavailable = target("unavailable");
            pause(unavailable);
            failed(
                    new ResumableDownload(url -> new Response(503, 0, new byte[0])),
                    unavailable,
                    new Control(0));
            check(part(unavailable).length() == 65536 && !unavailable.exists());

            File orphan = target("orphan");
            Files.write(part(orphan).toPath(), new byte[] {3, 2, 1});
            Response fresh = new Response(200, 0, bytes);
            new ResumableDownload(url -> fresh)
                    .fetch(orphan, URL, bytes.length, sha, new Control(0));
            check(
                    fresh.getRequestProperty("Range") == null
                            && sha.equals(ResumableDownload.digest(orphan)));

            File identity = target("identity");
            pause(identity);
            check(ResumableDownload.retained(identity, URL + "?different", bytes.length, sha) == 0);
            Response replacement = new Response(200, 0, bytes);
            new ResumableDownload(url -> replacement)
                    .fetch(identity, URL + "?different", bytes.length, sha, new Control(0));
            check(replacement.getRequestProperty("Range") == null);

            File alreadyPaused = target("paused-before-open");
            Control stopped = new Control(0);
            stopped.stopped = true;
            check(
                    failed(
                                    new ResumableDownload(
                                            url -> {
                                                throw new AssertionError(
                                                        "Paused request must not connect");
                                            }),
                                    alreadyPaused,
                                    stopped)
                            instanceof InterruptedIOException);

            File complete = target("full-part");
            Response last = new Response(200, 0, bytes);
            failed(new ResumableDownload(url -> last), complete, new Control(bytes.length));
            check(part(complete).length() == bytes.length && !complete.exists());
            new ResumableDownload(
                            url -> {
                                throw new AssertionError("Full partial needs verification only");
                            })
                    .fetch(complete, URL, bytes.length, sha, new Control(0));
            check(complete.exists());
            System.out.println("Passed " + checks + " resumable download checks.");
        } finally {
            try (java.util.stream.Stream<Path> paths = Files.walk(root)) {
                Path[] list = paths.sorted(Comparator.reverseOrder()).toArray(Path[]::new);
                for (Path path : list) {
                    if (!path.toAbsolutePath().normalize().startsWith(root))
                        throw new IOException("Invalid test cleanup path");
                    Files.deleteIfExists(path);
                }
            }
        }
    }
}
