package org.visualvoicemail.app;

import java.util.Locale;

/** Plain-Java matching logic, also exercised with synthetic data on the host. */
public final class InboxQuery {
    private InboxQuery() { }
    public static boolean matches(String query, String number, String displayCaller, String rawDate, String displayDate) {
        String needle = query.trim().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) return true;
        if (needle.matches("[+()\\d\\s./-]+")) {
            String digits = needle.replaceAll("\\D", "");
            if (digits.length() >= 3 && (number.replaceAll("\\D", "").contains(digits)
                || rawDate.replaceAll("\\D", "").contains(digits))) return true;
        }
        String text = (number + " " + displayCaller + " " + rawDate + " " + displayDate).toLowerCase(Locale.ROOT);
        for (String term : needle.split("\\s+")) if (!text.contains(term)) return false;
        return true;
    }
    public static boolean isRead(String flag) {
        return "Seen".equalsIgnoreCase(flag) || "1".equals(flag);
    }
}
