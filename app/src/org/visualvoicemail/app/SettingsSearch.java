package org.visualvoicemail.app;

import java.text.Normalizer;
import java.util.Locale;

/** Local, literal, all-word matching for settings names, current values, sections, and synonyms. */
final class SettingsSearch {
    static boolean active(String query) {
        return query != null && !query.trim().isEmpty();
    }

    static boolean matches(String query, String section, String title, String detail) {
        if (!active(query)) return true;
        String words = fold(query);
        if (words.isEmpty()) return false;
        String text = fold(section + " " + title + " " + detail + " " + keywords(title));
        for (String word : words.split(" +")) if (!text.contains(word)) return false;
        return true;
    }

    private static String fold(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim();
    }

    private static String keywords(String title) {
        switch (title) {
            case "Theme":
                return "appearance light dark mode amoled true black device night display";
            case "Reduce motion":
                return "animation animations movement effects accessibility";
            case "App tutorial":
                return "help onboarding setup tour guide first time welcome";
            case "Contact names":
                return "contacts caller address book names permission";
            case "Message order":
                return "sort newest oldest date inbox";
            case "Playback speed":
                return "audio rate faster slower 0.75 1 1.25 1.5 2";
            case "Skip interval":
                return "audio rewind forward seconds seek";
            case "Audio output":
                return "speaker earpiece headphones bluetooth sound route";
            case "Transcription quality":
                return "transcript transcripts transcription model whisper tiny base large v3"
                           + " english speech voice to text accuracy";
            case "Transcribe on open":
                return "automatic auto transcription transcript transcripts speech";
            case "Recording language":
                return "language multilingual detect automatic spanish french german arabic hindi urdu portuguese japanese korean chinese russian italian";
            case "Transcript output":
                return "translation translate english original language multilingual speech";
            case "Clear saved transcripts":
                return "remove delete erase transcript text storage";
            case "Refresh on open":
                return "automatic auto sync mailbox update";
            case "Refresh mailbox":
                return "sync fetch voicemail messages update";
            case "Reconnect mailbox":
                return "login sign in authentication carrier mint tmobile mobile data sim network";
            case "Connection help":
                return "support network offline mobile data sim forwarding default dialer";
            case "New voicemail alerts":
                return "notification notifications notify incoming messages";
            case "Check interval":
                return "notifications background frequency scheduled polling minutes hour";
            case "Sound, vibration & lock screen":
                return "notification channel ringtone silent vibration lockscreen";
            case "Send test notification":
                return "notification delivery preview example";
            case "Download location":
                return "download path folder directory storage save export recording recordings"
                           + " audio";
            case "Clear playback cache":
                return "storage downloaded recordings audio remove cleanup";
            case "App permissions":
                return "contacts android access allow permission";
            case "Privacy":
                return "security encryption local offline data analytics";
            case "Source code":
                return "github open source repository oss";
            case "Open-source license":
                return "mit licence license legal";
            case "Speech recognition licenses":
                return "transcription transcript model sherpa onnx whisper silero kotlin license"
                           + " licence";
            default:
                return "";
        }
    }
}
