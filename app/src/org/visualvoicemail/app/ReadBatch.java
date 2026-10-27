package org.visualvoicemail.app;

import java.util.*;

/** Carrier writes can return ambiguous errors: only the fresh mailbox confirms the final state. */
final class ReadBatch {
    interface Backend {
        void update(String id, boolean read) throws Exception;

        Map<String, Boolean> refresh() throws Exception;
    }

    static int run(Collection<String> ids, boolean read, Backend backend) throws Exception {
        Set<String> snapshot = new LinkedHashSet<>(ids);
        if (snapshot.isEmpty() || snapshot.contains(null) || snapshot.contains(""))
            throw new IllegalArgumentException("Select messages with valid identifiers.");
        for (String id : snapshot) {
            try {
                backend.update(id, read);
            } catch (Exception uncertain) {
                /* Verify even after a timeout or an error response. */
            }
        }
        Map<String, Boolean> confirmed = backend.refresh();
        int count = 0;
        for (String id : snapshot) if (Boolean.valueOf(read).equals(confirmed.get(id))) count++;
        return count;
    }
}
