package org.visualvoicemail.app;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** System-managed mailbox checks, with no continuous service or audio downloads. */
public final class MailboxCheckService extends JobService {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<JobParameters, CancellationSignal> running = new IdentityHashMap<>();
    @Override public boolean onStartJob(JobParameters params) {
        if (!VoicemailAlerts.enabled(this) || !VoicemailAlerts.allowed(this)) return false;
        CancellationSignal cancellation = new CancellationSignal(); running.put(params, cancellation);
        worker.execute(() -> {
            boolean success = false;
            try {
                cancellation.throwIfCanceled();
                CarrierClient client = CarrierClient.get(this);
                if (!client.hasSession()) return;
                client.sync(cancellation); success = true;
            } catch (Exception ignored) { /* Retry on the next scheduled check; never repeat failure alerts. */ }
            finally {
                final boolean checked = success;
                main.post(() -> {
                    if (running.remove(params) != cancellation || cancellation.isCanceled()) return;
                    getSharedPreferences("settings", MODE_PRIVATE).edit()
                        .putLong("background_checked_at", System.currentTimeMillis()).putBoolean("background_check_ok", checked).apply();
                    jobFinished(params, false);
                });
            }
        });
        return true;
    }
    @Override public boolean onStopJob(JobParameters params) {
        CancellationSignal cancellation = running.remove(params);
        if (cancellation != null) cancellation.cancel();
        return false;
    }
    @Override public void onDestroy() {
        for (CancellationSignal cancellation : running.values()) cancellation.cancel();
        running.clear(); worker.shutdownNow(); super.onDestroy();
    }
}
