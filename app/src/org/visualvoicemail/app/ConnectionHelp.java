package org.visualvoicemail.app;

import android.content.Context;
import android.os.Build;
import android.provider.Settings;
import android.telephony.TelephonyManager;

/** Read-only prerequisites. Unknown or dormant mobile networks must not become false blockers. */
final class ConnectionHelp {
    static String blocker(Context context) {
        try {
            if (Settings.Global.getInt(context.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0) != 0)
                return reason(true, false);
        } catch (SecurityException unknown) { }
        try {
            TelephonyManager phone = context.getSystemService(TelephonyManager.class);
            if (phone == null) return null;
            int slots =
                    Build.VERSION.SDK_INT >= 30
                            ? phone.getActiveModemCount()
                            : phone.getPhoneCount();
            if (slots <= 0) return null;
            for (int i = 0; i < slots; i++)
                if (phone.getSimState(i) != TelephonyManager.SIM_STATE_ABSENT) return null;
            return reason(false, true);
        } catch (RuntimeException unavailable) {
            return null;
        }
    }

    static String reason(boolean airplane, boolean allSlotsAbsent) {
        if (airplane)
            return "Turn off Airplane mode and turn on Mint mobile data, then connect. Saved"
                       + " recordings remain available.";
        if (allSlotsAbsent)
            return "Add or enable your Mint SIM, select it for mobile data, then connect. Saved"
                       + " recordings remain available.";
        return null;
    }
}
