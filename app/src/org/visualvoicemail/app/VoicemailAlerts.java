package org.visualvoicemail.app;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/** Local notifications from mailbox diffs; no push relay or caller details. */
final class VoicemailAlerts {
    static final String CHANNEL = "new_voicemail";
    static final String OPEN = "org.visualvoicemail.app.OPEN_NEW_VOICEMAIL";
    static final String DISMISS = "org.visualvoicemail.app.DISMISS_NEW_VOICEMAIL";
    static final int NOTIFICATION = 1001, TEST = 1002, PERIODIC = 9001, CHECK_SOON = 9002;
    private static final String STATE = "alerts.bin";
    private static SharedPreferences preferences(Context context) { return context.getSharedPreferences("settings", Context.MODE_PRIVATE); }
    static boolean enabled(Context context) { return preferences(context).getBoolean("alerts_enabled", false); }
    static int interval(Context context) {
        int value = preferences(context).getInt("alert_interval", 15);
        return value == 30 || value == 60 ? value : 15;
    }
    static void channel(Context context) {
        NotificationChannel channel = new NotificationChannel(CHANNEL, "New voicemail", NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("Alerts when a mailbox check discovers new unread voicemail.");
        channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        context.getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }
    static boolean allowed(Context context) {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        NotificationChannel channel = manager.getNotificationChannel(CHANNEL);
        return manager.areNotificationsEnabled() && (channel == null || channel.getImportance() != NotificationManager.IMPORTANCE_NONE);
    }
    static String status(Context context) {
        if (!enabled(context)) return "Off";
        if (!allowed(context)) return "Blocked in Android settings";
        if (!CarrierClient.get(context).hasSession()) return "Connect your mailbox to start checks";
        JobInfo job = context.getSystemService(JobScheduler.class).getPendingJob(PERIODIC);
        return job == null ? "Background checks could not be scheduled · Open to retry" : "On · Checks about every " + interval(context) + " minutes";
    }
    static void enable(Activity activity, int request) {
        preferences(activity).edit().putBoolean("alerts_enabled", true).putBoolean("alerts_prompted", true).apply();
        channel(activity);
        baseline(activity, CarrierClient.get(activity).cachedInbox());
        if (Build.VERSION.SDK_INT >= 33 && activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            activity.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, request);
        } else {
            ensureScheduled(activity); checkSoon(activity);
            if (!allowed(activity)) activity.startActivity(systemSettings(activity));
        }
    }
    static void disable(Context context) {
        preferences(context).edit().putBoolean("alerts_enabled", false).putBoolean("alerts_prompted", true).apply();
        ensureScheduled(context); dismiss(context);
        context.getSystemService(NotificationManager.class).cancel(TEST);
    }
    static Intent systemSettings(Context context) {
        if(!context.getSystemService(NotificationManager.class).areNotificationsEnabled())
            return new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.getPackageName());
        return new Intent(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.getPackageName())
            .putExtra(android.provider.Settings.EXTRA_CHANNEL_ID, CHANNEL);
    }
    static void ensureScheduled(Context context) {
        channel(context);
        JobScheduler scheduler = context.getSystemService(JobScheduler.class);
        if (!enabled(context) || !allowed(context) || !CarrierClient.get(context).hasSession()) {
            scheduler.cancel(PERIODIC); scheduler.cancel(CHECK_SOON);
            dismiss(context); return;
        }
        long period = interval(context) * 60000L;
        JobInfo existing = scheduler.getPendingJob(PERIODIC);
        if (existing == null || existing.getIntervalMillis() != period) {
            scheduler.schedule(new JobInfo.Builder(PERIODIC, new ComponentName(context, MailboxCheckService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPeriodic(period).setPersisted(true).build());
        }
    }
    static void checkSoon(Context context) {
        if (!enabled(context) || !allowed(context) || !CarrierClient.get(context).hasSession()) return;
        JobScheduler scheduler = context.getSystemService(JobScheduler.class);
        if (scheduler.getPendingJob(CHECK_SOON) == null)
            scheduler.schedule(new JobInfo.Builder(CHECK_SOON, new ComponentName(context, MailboxCheckService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).build());
    }
    private static Set<String> strings(JSONArray array) {
        Set<String> result = new LinkedHashSet<>();
        if (array != null) for (int i = 0; i < array.length(); i++) {
            String id = array.optString(i, ""); if (!id.isEmpty()) result.add(id);
        }
        return result;
    }
    private static JSONObject state(NotificationLedger.Result result) throws Exception {
        return new JSONObject().put("known", new JSONArray(result.known)).put("pending", new JSONArray(result.pending));
    }
    static synchronized void baseline(Context context, List<JSONObject> messages) { observe(context, messages, true); }
    static synchronized void onMailbox(Context context, List<JSONObject> messages) { observe(context, messages, false); }
    private static void observe(Context context, List<JSONObject> messages, boolean baseline) {
        try {
            PrivateStore store = new PrivateStore(context);
            JSONObject previous = store.get(STATE);
            Set<String> pending = strings(previous == null ? null : previous.optJSONArray("pending"));
            List<NotificationLedger.Message> current = new ArrayList<>();
            for (JSONObject message : messages) current.add(new NotificationLedger.Message(message.optString("objectid"), !InboxQuery.isRead(message.optString("flag"))));
            NotificationLedger.Result result = NotificationLedger.observe(strings(previous == null ? null : previous.optJSONArray("known")),
                pending, current, previous != null,
                !baseline && enabled(context) && allowed(context));
            // Commit deduplication state before posting so process restarts do not replay alerts.
            if (!store.put(STATE, state(result))) return;
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (result.pending.isEmpty()) manager.cancel(NOTIFICATION);
            else if (result.hasNew || (!pending.equals(result.pending) && active(manager))) {
                manager.notify(NOTIFICATION, notification(context, result.pending.size(), false, !result.hasNew));
            }
        } catch (Exception ignored) { /* A notification failure must not discard a successful inbox sync. */ }
    }
    private static boolean active(NotificationManager manager) {
        for(android.service.notification.StatusBarNotification notification:manager.getActiveNotifications())
            if(notification.getId()==NOTIFICATION) return true;
        return false;
    }
    static synchronized void dismiss(Context context) {
        PrivateStore store = new PrivateStore(context); JSONObject saved = store.get(STATE);
        if (saved != null) try { saved.put("pending", new JSONArray()); store.put(STATE, saved); } catch (Exception ignored) { }
        context.getSystemService(NotificationManager.class).cancel(NOTIFICATION);
    }
    private static PendingIntent open(Context context) {
        return PendingIntent.getActivity(context, 0, new Intent(context, MainActivity.class).setAction(OPEN)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    private static Notification notification(Context context, int count, boolean test, boolean quiet) {
        String title = test ? "Test notification" : count == 1 ? "New voicemail" : count + " new voicemails";
        String text = test ? "Voicemail alerts are allowed. Tap to open your inbox." : "Tap to open your voicemail inbox.";
        Notification.Builder builder = new Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification_voicemail).setContentTitle(title).setContentText(text)
            .setCategory(Notification.CATEGORY_MESSAGE).setVisibility(Notification.VISIBILITY_PRIVATE)
            .setAutoCancel(true).setOnlyAlertOnce(quiet).setContentIntent(open(context))
            .setPublicVersion(new Notification.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_notification_voicemail)
                .setContentTitle("Voice Mail").setContentText("Open the app to view voicemail.").build());
        if (!test) builder.setNumber(count).setDeleteIntent(PendingIntent.getBroadcast(context, 0,
            new Intent(context, AlertDismissReceiver.class).setAction(DISMISS), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        return builder.build();
    }
    static boolean test(Context context) {
        channel(context);
        if (!allowed(context)) return false;
        try { context.getSystemService(NotificationManager.class).notify(TEST, notification(context, 0, true, false)); return true; }
        catch (SecurityException denied) { return false; }
    }
}
