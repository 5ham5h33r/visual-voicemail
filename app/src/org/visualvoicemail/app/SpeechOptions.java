package org.visualvoicemail.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.widget.Toast;

/** Language and output choices shared by Settings, recording detail, and queued speech jobs. */
final class SpeechOptions {
    static final String[] CODES = {
        "auto", "en", "es", "fr", "de", "ar", "hi", "ur", "pt", "ja", "ko", "zh", "ru", "it"
    };
    static final String[] LABELS = {
        "Detect automatically",
        "English",
        "Spanish",
        "French",
        "German",
        "Arabic",
        "Hindi",
        "Urdu",
        "Portuguese",
        "Japanese",
        "Korean",
        "Chinese",
        "Russian",
        "Italian"
    };

    static String language(Context c, TranscriptionQuality quality) {
        if (quality != TranscriptionQuality.HIGHEST) return "en";
        String value = c.getSharedPreferences("settings", 0).getString("speech_language", "auto");
        for (String code : CODES) if (code.equals(value)) return code;
        return "auto";
    }

    static String label(String code) {
        for (int i = 0; i < CODES.length; i++) if (CODES[i].equals(code)) return LABELS[i];
        return code.isEmpty() ? "Automatic language" : code;
    }

    static boolean english(Context c) {
        return Transcription.selected(c) == TranscriptionQuality.HIGHEST
                && c.getSharedPreferences("settings", 0).getBoolean("speech_english", false);
    }

    static void requireMultilingual(Activity c, Runnable action) {
        if (Transcription.selected(c) == TranscriptionQuality.HIGHEST) {
            action.run();
            return;
        }
        Ui ui = new Ui(c);
        ui.show(
                new AlertDialog.Builder(c)
                        .setTitle("Use multilingual recognition?")
                        .setMessage(
                                "Whisper Large v3 supports other languages and English translation"
                                    + " on this phone. It needs a 1.78 GB download, several GB of"
                                    + " memory, and can take minutes per message. Tiny and Base in"
                                    + " this app are English-only. Saved transcripts are kept.")
                        .setPositiveButton(
                                "Use Large v3",
                                (d, w) -> {
                                    if (Transcription.select(c, "highest")) action.run();
                                    else
                                        Toast.makeText(
                                                        c,
                                                        "Finish or cancel the current speech"
                                                                + " operation first.",
                                                        Toast.LENGTH_LONG)
                                                .show();
                                })
                        .setNegativeButton("Cancel", null)
                        .create());
    }

    static void chooseLanguage(Activity c, Runnable changed) {
        int selected = 0;
        String current = language(c, Transcription.selected(c));
        for (int i = 0; i < CODES.length; i++) if (CODES[i].equals(current)) selected = i;
        new Ui(c)
                .show(
                        new AlertDialog.Builder(c)
                                .setTitle("Recording language")
                                .setSingleChoiceItems(
                                        LABELS,
                                        selected,
                                        (dialog, which) -> {
                                            dialog.dismiss();
                                            Runnable apply =
                                                    () -> {
                                                        c.getSharedPreferences("settings", 0)
                                                                .edit()
                                                                .putString(
                                                                        "speech_language",
                                                                        CODES[which])
                                                                .apply();
                                                        changed.run();
                                                    };
                                            if ("en".equals(CODES[which])) apply.run();
                                            else requireMultilingual(c, apply);
                                        })
                                .setNegativeButton("Cancel", null)
                                .create());
    }

    static void chooseOutput(Activity c, Runnable changed) {
        new Ui(c)
                .show(
                        new AlertDialog.Builder(c)
                                .setTitle("Transcript output")
                                .setSingleChoiceItems(
                                        new String[] {
                                            "Original language", "Original + English translation"
                                        },
                                        english(c) ? 1 : 0,
                                        (dialog, which) -> {
                                            dialog.dismiss();
                                            Runnable apply =
                                                    () -> {
                                                        c.getSharedPreferences("settings", 0)
                                                                .edit()
                                                                .putBoolean(
                                                                        "speech_english",
                                                                        which == 1)
                                                                .apply();
                                                        changed.run();
                                                    };
                                            if (which == 0) apply.run();
                                            else requireMultilingual(c, apply);
                                        })
                                .setNegativeButton("Cancel", null)
                                .create());
    }
}
