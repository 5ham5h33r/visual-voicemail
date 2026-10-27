package org.visualvoicemail.app;

import android.content.*;
import android.net.Uri;

/** Build explicit user-initiated Android shares; no destination is selected by this app. */
final class ShareIntents {
    static Intent transcript(Transcript value) {
        StringBuilder text =
                new StringBuilder(
                        "Voicemail · "
                                + ("translate".equals(value.task)
                                        ? "English translation"
                                        : "Original transcript")
                                + "\n\n");
        for (Transcript.Segment segment : value.segments) {
            if (segment.start >= 0)
                text.append(
                        String.format(
                                java.util.Locale.ROOT,
                                "%d:%02d  ",
                                segment.start / 60000,
                                segment.start / 1000 % 60));
            text.append(segment.text).append("\n");
        }
        return new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, "Voicemail transcript")
                .putExtra(Intent.EXTRA_TEXT, text.toString().trim());
    }

    static Intent audio(Uri uri, String type) {
        Intent intent =
                new Intent(Intent.ACTION_SEND).setType(type).putExtra(Intent.EXTRA_STREAM, uri);
        intent.setClipData(ClipData.newRawUri("Voice recording", uri));
        return intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    }
}
