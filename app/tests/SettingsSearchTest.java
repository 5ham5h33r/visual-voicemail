package org.visualvoicemail.app;

public final class SettingsSearchTest {
    static int checks;

    static void check(boolean value) {
        checks++;
        if (!value) throw new AssertionError("check " + checks);
    }

    public static void main(String[] args) {
        check(SettingsSearch.matches("", "PLAYBACK", "Playback speed", "1.5×"));
        check(SettingsSearch.matches(" \t\n", "PLAYBACK", "Playback speed", "1.5×"));
        check(SettingsSearch.matches("PLAYBACK 1.5", "PLAYBACK", "Playback speed", "1.5×"));
        check(SettingsSearch.matches("1.5 playback", "PLAYBACK", "Playback speed", "1.5×"));
        check(!SettingsSearch.matches("playback 99", "PLAYBACK", "Playback speed", "1.5×"));
        check(SettingsSearch.matches("Thème", "APPEARANCE", "Theme", "Use device setting"));
        check(SettingsSearch.matches("dark mode", "APPEARANCE", "Theme", "Use device setting"));
        check(
                SettingsSearch.matches(
                        "download path", "STORAGE", "Download location", "No folder selected"));
        check(
                SettingsSearch.matches(
                        "whisper large-v3",
                        "TRANSCRIPTION",
                        "Transcription quality",
                        "Standard (Whisper Tiny)"));
        check(
                SettingsSearch.matches(
                        "transcripts",
                        "TRANSCRIPTION",
                        "Transcription quality",
                        "Standard (Whisper Tiny)"));
        check(
                !SettingsSearch.matches(
                        "whisper large-v3", "PLAYBACK", "Skip interval", "10 seconds"));
        check(
                SettingsSearch.matches(
                        "privacy local", "PRIVACY & PERMISSIONS", "Privacy", "Data handling"));
        check(
                SettingsSearch.matches(
                        "storage", "STORAGE", "Download location", "No folder selected"));
        check(!SettingsSearch.matches("storage", "APPEARANCE", "Theme", "Light"));
        check(!SettingsSearch.matches(".*", "APPEARANCE", "Theme", "Light"));
        check(!SettingsSearch.matches("[", "APPEARANCE", "Theme", "Light"));
        check(SettingsSearch.matches("licence", "ABOUT", "Open-source license", "MIT"));
        check(SettingsSearch.matches("bluetooth", "PLAYBACK", "Audio output", "Follow device"));
        check(SettingsSearch.matches("folder", "STORAGE", "Download location", "Chosen folder"));
        check(
                !SettingsSearch.matches(
                        "foldersettingmissing", "STORAGE", "Download location", "Chosen folder"));
        check(SettingsSearch.matches("隐私", "隐私", "Privacy", "Data handling"));
        check(!SettingsSearch.active(" \t"));
        check(SettingsSearch.matches("translate English","TRANSCRIPTION","Transcript output","Original language"));
        check(SettingsSearch.matches("Spanish","TRANSCRIPTION","Recording language","English"));
        check(!SettingsSearch.matches("Spanish","PLAYBACK","Audio output","Follow device"));
        check(SettingsSearch.matches("outgoing message","MAILBOX & SYNC","Voicemail greeting","Record or choose audio"));
        check(SettingsSearch.matches("automatic unread", "PLAYBACK", "Mark as read when playback starts", "Off"));
        System.out.println("Passed " + checks + " settings search checks.");
    }
}
