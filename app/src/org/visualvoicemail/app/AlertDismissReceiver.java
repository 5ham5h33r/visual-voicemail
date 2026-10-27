package org.visualvoicemail.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Dismissing an alert acknowledges only the notification, never carrier read flags. */
public final class AlertDismissReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (VoicemailAlerts.DISMISS.equals(intent.getAction())) VoicemailAlerts.dismiss(context);
    }
}
