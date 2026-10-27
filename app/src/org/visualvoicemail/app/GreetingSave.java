package org.visualvoicemail.app;

import java.io.IOException;
import java.util.*;

/** Stage a personal greeting, persist its identity, then activate and verify it. Never deletes. */
final class GreetingSave {
    static final class Rejected extends IOException {
        private static final long serialVersionUID = 1L;

        Rejected(String message) {
            super(message);
        }
    }

    static final class Entry {
        final String id, title;
        final boolean personal, active;

        Entry(String id, String title, boolean personal, boolean active) {
            this.id = id;
            this.title = title;
            this.personal = personal;
            this.active = active;
        }
    }

    interface Backend {
        Map<String, Entry> refresh() throws Exception;

        String uploadInactive(String title) throws Exception;

        void rememberUploaded(String id) throws Exception;

        void activate(String id) throws Exception;

        void deactivate(String id) throws Exception;

        void confirm(String id) throws Exception;
    }

    static String find(Map<String, Entry> entries, String title) throws IOException {
        String found = "";
        for (Entry entry : entries.values())
            if (entry.personal && title.equals(entry.title)) {
                if (!found.isEmpty())
                    throw new IOException(
                            "More than one uploaded draft was found. Call voicemail to check your"
                                    + " greeting.");
                found = entry.id;
            }
        return found;
    }

    static String run(String title, String pending, Backend backend) throws Exception {
        if (title == null || title.isEmpty())
            throw new IllegalArgumentException("A greeting title is required.");
        Map<String, Entry> listing = backend.refresh();
        Set<String> existing = new HashSet<>(listing.keySet());
        String id = pending == null ? "" : pending;
        if (id.isEmpty() || !listing.containsKey(id)) id = find(listing, title);
        if (id.isEmpty()) {
            try {
                id = backend.uploadInactive(title);
            } catch (Rejected unavailable) {
                throw unavailable;
            } catch (Exception uncertain) {
                id = "";
            }
            listing = backend.refresh();
            if (id == null || id.isEmpty()) id = find(listing, title);
        }
        Entry draft = listing.get(id);
        if (draft == null || !draft.personal || !title.equals(draft.title))
            throw new IOException(
                    "Couldn't confirm the uploaded greeting. Refresh before retrying. Your local"
                            + " draft is kept.");
        backend.rememberUploaded(id);
        if (!draft.active)
            try {
                backend.activate(id);
            } catch (Exception uncertain) {
                /* Read back after ambiguous errors. */
            }
        listing = backend.refresh();
        draft = listing.get(id);
        if (draft != null && draft.personal && draft.active) {
            for (Entry entry : listing.values())
                if (entry.personal
                        && entry.active
                        && !entry.id.equals(id)
                        && !existing.contains(entry.id))
                    throw new IOException(
                            "Greeting activation changed elsewhere. Refresh before saving again.");
            // Some carriers clear the previous active flag themselves; others need an explicit
            // flag update. Only change known personal entries after the new one is confirmed on.
            boolean changed = false;
            for (Entry entry : listing.values())
                if (entry.personal && entry.active && !entry.id.equals(id)) {
                    try {
                        backend.deactivate(entry.id);
                    } catch (Exception uncertain) {
                    }
                    changed = true;
                }
            if (changed) listing = backend.refresh();
        }
        int active = 0;
        for (Entry entry : listing.values()) if (entry.personal && entry.active) active++;
        draft = listing.get(id);
        if (draft == null || !draft.active || !draft.personal || active != 1)
            throw new IOException(
                    "Greeting activation isn't confirmed. Your draft is kept. Refresh or retry Save"
                            + " before calling voicemail to check.");
        backend.confirm(id);
        return id;
    }
}
