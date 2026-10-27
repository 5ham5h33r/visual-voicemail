package org.visualvoicemail.app;

import android.content.Context;

import java.io.File;
import java.util.concurrent.*;

/** A user-started save may finish across screen recreation; it is never resumed automatically. */
final class GreetingJobs {
    private static final ExecutorService WORK = Executors.newSingleThreadExecutor();
    static volatile boolean busy;
    static volatile String status = "";
    static volatile long version;

    static synchronized boolean save(Context context, File draft, String expected) {
        if (busy || draft == null) return false;
        Context c = context.getApplicationContext();
        busy = true;
        status = "Uploading and checking your greeting…";
        WORK.execute(
                () -> {
                    File snapshot = null;
                    try {
                        snapshot = GreetingAudio.snapshot(c, draft, expected);
                        int seconds = (GreetingAudio.duration(snapshot) + 999) / 1000;
                        CarrierClient.get(c).saveGreeting(snapshot, seconds);
                        status = "Greeting saved · Confirmed by carrier";
                    } catch (Exception failure) {
                        String message = failure.getMessage();
                        if (message != null && message.startsWith("Turn on mobile data"))
                            status = message + " Your local draft is kept.";
                        else {
                            status =
                                    message != null
                                                    && (message.startsWith(
                                                                    "Couldn't confirm the uploaded")
                                                            || message.startsWith(
                                                                    "Greeting activation")
                                                            || message.startsWith(
                                                                    "Your carrier's greeting"))
                                            ? message
                                            : "Couldn't confirm the save. Your local draft is kept."
                                                  + " Refresh and check the current greeting before"
                                                  + " retrying.";
                        }
                    } finally {
                        if (snapshot != null) snapshot.delete();
                        version++;
                        busy = false;
                    }
                });
        return true;
    }
}
