package org.visualvoicemail.app;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** One process-scoped bulk flag update; the carrier remains the authority for confirmed state. */
final class MailboxBatch {
    private static final ExecutorService WORK = Executors.newSingleThreadExecutor();
    static volatile boolean busy;
    static volatile String status = "";
    static volatile long version;

    static synchronized boolean read(Context context, List<String> ids, boolean read) {
        if (busy || ids.isEmpty()) return false;
        Context c = context.getApplicationContext();
        List<String> snapshot = new ArrayList<>(new java.util.LinkedHashSet<>(ids));
        busy = true;
        status = "Marking " + snapshot.size() + (read ? " as read…" : " as unread…");
        WORK.execute(
                () -> {
                    try {
                        int done = CarrierClient.get(c).setReadBatch(snapshot, read);
                        status =
                                done
                                        + (read ? " marked read" : " marked unread")
                                        + " · Synced with carrier"
                                        + (done == snapshot.size()
                                                ? ""
                                                : " · "
                                                        + (snapshot.size() - done)
                                                        + " not confirmed; refresh and retry");
                    } catch (Exception unavailable) {
                        status =
                                "Couldn't confirm read status. Refresh to check the mailbox, then"
                                    + " retry.";
                    } finally {
                        version++;
                        busy = false;
                    }
                });
        return true;
    }

    static synchronized void dismiss() {
        if (!busy) status = "";
    }
}
