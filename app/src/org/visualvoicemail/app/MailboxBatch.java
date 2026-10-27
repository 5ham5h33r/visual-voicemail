package org.visualvoicemail.app;

import android.content.Context;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** One process-scoped bulk flag update; the carrier remains the authority for confirmed state. */
final class MailboxBatch {
    private static final ExecutorService WORK = Executors.newSingleThreadExecutor();
    static volatile boolean busy;
    static volatile boolean removingSaved;
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
                                        + " · Updated"
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

    static synchronized boolean archive(Context context, List<JSONObject> messages) {
        if (busy || messages.isEmpty()) return false;
        Context c = context.getApplicationContext();
        List<JSONObject> snapshot = new ArrayList<>(messages);
        busy = true;
        status = "Saving recordings before carrier removal…";
        WORK.execute(
                () -> {
                    try {
                        ArchiveBatch.Result result = CarrierClient.get(c).archive(snapshot);
                        status =
                                result.removed
                                        + " removed from carrier · "
                                        + result.saved
                                        + " saved on this phone";
                        if (result.saved < result.requested)
                            status +=
                                    " · "
                                            + (result.requested - result.saved)
                                            + " couldn't be saved; no deletion requested for those"
                                            + " messages";
                        if (result.removed < result.saved)
                            status +=
                                    " · "
                                            + (result.saved - result.removed)
                                            + " carrier removals unconfirmed; refresh or retry from"
                                            + " Saved";
                    } catch (Exception failure) {
                        status =
                                "Couldn't complete carrier removal. Saved copies are kept; refresh"
                                    + " to check before retrying.";
                    } finally {
                        version++;
                        busy = false;
                    }
                });
        return true;
    }

    static synchronized boolean removeSaved(Context context, List<String> ids) {
        if (busy || ids.isEmpty()) return false;
        Context c = context.getApplicationContext();
        List<String> snapshot = new ArrayList<>(ids);
        synchronized (Transcription.class) {
            if (Transcription.busy) return false;
            busy = true;
            removingSaved = true;
        }
        status = "Removing saved copies…";
        WORK.execute(
                () -> {
                    int removed = 0;
                    for (String id : snapshot)
                        try {
                            SavedMailbox.get(c).removeLocal(id);
                            new android.util.AtomicFile(
                                            new java.io.File(
                                                    c.getFilesDir(), Transcription.key(id)))
                                    .delete();
                            removed++;
                        } catch (Exception kept) {
                        }
                    status =
                            removed
                                    + " saved copies removed"
                                    + (removed == snapshot.size()
                                            ? ""
                                            : " · "
                                                    + (snapshot.size() - removed)
                                                    + " couldn't be fully removed; check storage and"
                                                    + " carrier status, then retry");
                    version++;
                    removingSaved = false;
                    busy = false;
                });
        return true;
    }

    static synchronized void dismiss() {
        if (!busy) status = "";
    }
}
