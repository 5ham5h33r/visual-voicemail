package org.visualvoicemail.app;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/** Carrier requests use AMR. Detect its storage header rather than mislabeling bytes. */
public final class ExportAudioFormat {
    public final String mime, extension;
    private ExportAudioFormat(String mime,String extension) { this.mime=mime; this.extension=extension; }
    public static ExportAudioFormat detect(File file) throws IOException {
        byte[] header=new byte[9]; int length;
        try(FileInputStream input=new FileInputStream(file)) { length=input.read(header); }
        String signature=length<0?"":new String(header,0,length,StandardCharsets.US_ASCII);
        if(signature.startsWith("#!AMR-WB\n")) return new ExportAudioFormat("audio/amr-wb","awb");
        if(signature.startsWith("#!AMR\n")) return new ExportAudioFormat("audio/amr","amr");
        throw new IOException("This recording format cannot be saved yet.");
    }
    public String filename(String date) {
        ZonedDateTime time;
        try { time=OffsetDateTime.parse(date).atZoneSameInstant(java.time.ZoneId.systemDefault()); }
        catch(Exception ignored) { time=ZonedDateTime.now(); }
        return "Voicemail_"+time.format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss",Locale.ROOT))
            +"_"+UUID.randomUUID().toString().substring(0,8)+"."+extension;
    }
}
