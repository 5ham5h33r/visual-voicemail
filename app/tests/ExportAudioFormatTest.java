package org.visualvoicemail.app;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public final class ExportAudioFormatTest {
    private static int checks;
    private static void expect(boolean condition,String detail) { checks++; if(!condition)throw new AssertionError(detail); }
    public static void main(String[] args) throws Exception {
        File file=Files.createTempFile("voicemail-format-check",".audio").toFile();
        try {
            Files.write(file.toPath(),"#!AMR\nsynthetic".getBytes(StandardCharsets.US_ASCII));
            ExportAudioFormat narrow=ExportAudioFormat.detect(file);
            expect(narrow.mime.equals("audio/amr"),"Narrowband MIME matches the header");
            expect(narrow.extension.equals("amr"),"Narrowband extension is AMR");
            expect(narrow.filename("2026-09-12T12:00:00Z").matches("Voicemail_2026-09-\\d{2}_\\d{2}-\\d{2}-\\d{2}_[a-f0-9]{8}\\.amr"),"Filename uses date and a unique suffix");
            expect(!narrow.filename("../../unsafe").contains("/"),"Invalid dates cannot introduce path components");
            Files.write(file.toPath(),"#!AMR-WB\nsynthetic".getBytes(StandardCharsets.US_ASCII));
            ExportAudioFormat wide=ExportAudioFormat.detect(file);
            expect(wide.mime.equals("audio/amr-wb")&&wide.extension.equals("awb"),"Wideband has its own format and extension");
            Files.write(file.toPath(),new byte[0]);
            try { ExportAudioFormat.detect(file); throw new AssertionError("Empty file accepted"); } catch(IOException expected) { checks++; }
            Files.write(file.toPath(),"#!AMR".getBytes(StandardCharsets.US_ASCII));
            try { ExportAudioFormat.detect(file); throw new AssertionError("Truncated header accepted"); } catch(IOException expected) { checks++; }
            Files.write(file.toPath(),"unsupported".getBytes(StandardCharsets.US_ASCII));
            try { ExportAudioFormat.detect(file); throw new AssertionError("Unsupported format mislabeled"); } catch(IOException expected) { checks++; }
            System.out.println("Passed "+checks+" audio export format checks.");
        } finally { Files.deleteIfExists(file.toPath()); }
    }
}
