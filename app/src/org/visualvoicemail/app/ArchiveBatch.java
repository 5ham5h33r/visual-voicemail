package org.visualvoicemail.app;

import java.util.*;

/** Archive failures must prevent destructive requests. Only a fresh listing confirms removal. */
final class ArchiveBatch {
    interface Backend {
        void saveAndVerify(String id) throws Exception;

        void deleteCarrier(String id) throws Exception;

        Set<String> refreshCarrier() throws Exception;

        void confirmRemoved(String id) throws Exception;
    }

    static final class Result {
        final int requested, saved, removed;

        Result(int requested, int saved, int removed) {
            this.requested = requested;
            this.saved = saved;
            this.removed = removed;
        }
    }

    static Result run(Collection<String> ids, Backend backend) {
        Set<String> unique = new LinkedHashSet<>(ids);
        if (unique.isEmpty() || unique.contains(null) || unique.contains(""))
            throw new IllegalArgumentException("Select valid messages.");
        Set<String> prepared = new LinkedHashSet<>();
        for (String id : unique) {
            try {
                backend.saveAndVerify(id);
            } catch (Exception unsafe) {
                continue;
            }
            prepared.add(id);
            try {
                backend.deleteCarrier(id);
            } catch (Exception uncertain) {
                /* A timeout may still have removed the message. */
            }
        }
        int confirmed = 0;
        if (!prepared.isEmpty()) {
            try {
                Set<String> present = backend.refreshCarrier();
                for (String id : prepared)
                    if (!present.contains(id)) {
                        try {
                            backend.confirmRemoved(id);
                            confirmed++;
                        } catch (Exception notPersisted) {
                            /* Keep the saved copy and report uncertainty. */
                        }
                    }
            } catch (Exception offline) {
                /* Saved copies remain; carrier removal is unconfirmed. */
            }
        }
        return new Result(unique.size(), prepared.size(), confirmed);
    }
}
