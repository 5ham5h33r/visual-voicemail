package org.visualvoicemail.app;

import java.util.ArrayList;
import java.util.List;

/** UTF-16 offsets for literal, Unicode-aware, case-insensitive phrase matches. */
public final class TranscriptSearch {
    private TranscriptSearch() {}

    public static List<int[]> find(String text, String query) {
        List<int[]> found = new ArrayList<>();
        String needle = query.trim();
        if (needle.isEmpty()) return found;
        for (int i = 0; i <= text.length() - needle.length(); i++)
            if (text.regionMatches(true, i, needle, 0, needle.length())) {
                found.add(new int[] {i, i + needle.length()});
                i += needle.length() - 1;
            }
        return found;
    }
}
