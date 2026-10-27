package org.visualvoicemail.app;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Pure mailbox-diff policy. Read-state changes alone are never new arrivals. */
public final class NotificationLedger {
    public static final int MAX_KNOWN = 20000;
    public static final class Message {
        final String id; final boolean unread;
        public Message(String id, boolean unread) { this.id = id; this.unread = unread; }
    }
    public static final class Result {
        public final Set<String> known, pending;
        public final boolean hasNew;
        Result(Set<String> known, Set<String> pending, boolean hasNew) {
            this.known = known; this.pending = pending; this.hasNew = hasNew;
        }
    }
    public static Result observe(Collection<String> known, Collection<String> pending,
            Collection<Message> messages, boolean initialized, boolean eligible) {
        Map<String, Boolean> current = new LinkedHashMap<>();
        for (Message message : messages)
            if (message.id != null && !message.id.isEmpty()) current.put(message.id, message.unread);
        Set<String> remembered = new LinkedHashSet<>(known);
        Set<String> waiting = new LinkedHashSet<>();
        boolean added = false;
        if (initialized && eligible) {
            for (Map.Entry<String, Boolean> message : current.entrySet()) {
                if (!message.getValue()) continue;
                String id = message.getKey();
                if (!remembered.contains(id)) { waiting.add(id); added = true; }
                else if (pending.contains(id)) waiting.add(id);
            }
        }
        for (String id : current.keySet()) { remembered.remove(id); remembered.add(id); }
        while (remembered.size() > MAX_KNOWN) remembered.remove(remembered.iterator().next());
        return new Result(remembered, waiting, added);
    }
}
