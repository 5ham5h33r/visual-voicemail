package org.visualvoicemail.app;

import android.content.Context;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

/** Display-only SIM branding; it does not select or enable a carrier backend. */
final class SimCarrier {
    private static final String FALLBACK = "Mint Mobile";
    static String name(Context context) {
        try {
            int subscription = SubscriptionManager.getDefaultDataSubscriptionId();
            if (!SubscriptionManager.isValidSubscriptionId(subscription)) return FALLBACK;
            TelephonyManager manager = context.getSystemService(TelephonyManager.class);
            if (manager == null) return FALLBACK;
            TelephonyManager sim = manager.createForSubscriptionId(subscription);
            if (sim.getSimState() != TelephonyManager.SIM_STATE_READY) return FALLBACK;
            String name = sim.getSimOperatorName();
            return name == null || name.trim().isEmpty() ? FALLBACK : name.trim();
        } catch (SecurityException | UnsupportedOperationException unavailable) {
            return FALLBACK;
        }
    }
}
