package org.visualvoicemail.app;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Original encoding of the observed multipart greeting fields. Upload starts inactive. */
final class GreetingMultipart {
    final String type;
    final byte[] bytes;

    GreetingMultipart(File audio, int seconds, String title) throws Exception {
        if (seconds < 1
                || seconds > 60
                || title == null
                || title.isEmpty()
                || title.contains("\r")
                || title.contains("\n"))
            throw new IOException("Choose a greeting from 1 to 60 seconds.");
        if (audio.length() > 1024 * 1024
                || !"amr".equals(ExportAudioFormat.detect(audio).extension))
            throw new IOException("The greeting must be AMR narrowband audio.");
        String boundary = "Greeting-" + UUID.randomUUID();
        type = "multipart/form-data; boundary=" + boundary;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        String[][] fields = {
            {"title", title},
            {"type", "personal"},
            {"duration", String.valueOf(seconds)},
            {"flags", ""},
            {"message-id", UUID.randomUUID().toString()},
            {"mime-version", "1.0"}
        };
        for (String[] field : fields)
            write(
                    output,
                    "--"
                            + boundary
                            + "\r\nContent-Disposition: form-data; name=\""
                            + field[0]
                            + "\"\r\n\r\n"
                            + field[1]
                            + "\r\n");
        write(
                output,
                "--"
                        + boundary
                        + "\r\n"
                        + "Content-Disposition: form-data; name=\"voicemailgreeting\";"
                        + " filename=\"greeting.amr\"\r\n"
                        + "Content-Type: audio/amr\r\n\r\n");
        try (InputStream input = new FileInputStream(audio)) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = input.read(buffer)) != -1) output.write(buffer, 0, n);
        }
        write(output, "\r\n--" + boundary + "--\r\n");
        bytes = output.toByteArray();
    }

    private static void write(OutputStream output, String text) throws IOException {
        output.write(text.getBytes(StandardCharsets.UTF_8));
    }
}
