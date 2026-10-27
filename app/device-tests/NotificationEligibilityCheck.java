package org.visualvoicemail.checks;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.telecom.TelecomManager;
import android.telephony.TelephonyManager;

/** Read only. No SMS, registration, provisioning, or permission changes. */
public final class NotificationEligibilityCheck extends DeviceCheckInstrumentation {
    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);
        start();
    }

    @Override
    public void onStart() {
        Bundle out = new Bundle();
        try {
            Context c = getTargetContext();
            TelecomManager calls = (TelecomManager) c.getSystemService(Context.TELECOM_SERVICE);
            TelephonyManager phone =
                    (TelephonyManager) c.getSystemService(Context.TELEPHONY_SERVICE);
            out.putBoolean(
                    "is_default_dialer",
                    c.getPackageName().equals(calls.getDefaultDialerPackage()));
            out.putBoolean("has_carrier_privileges", phone.hasCarrierPrivileges());
            out.putBoolean(
                    "has_modify_phone_state",
                    c.checkSelfPermission("android.permission.MODIFY_PHONE_STATE")
                            == PackageManager.PERMISSION_GRANTED);
            out.putBoolean(
                    "has_read_phone_state",
                    c.checkSelfPermission("android.permission.READ_PHONE_STATE")
                            == PackageManager.PERMISSION_GRANTED);
            out.putBoolean(
                    "has_receive_sms",
                    c.checkSelfPermission("android.permission.RECEIVE_SMS")
                            == PackageManager.PERMISSION_GRANTED);
            out.putBoolean(
                    "is_system_app",
                    (c.getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM)
                            != 0);
            out.putInt("api_level", android.os.Build.VERSION.SDK_INT);
            out.putBoolean("research_complete", true);
            out.putBoolean("passed", true);
            out.putInt("checks", 6);
        } catch (Throwable e) {
            out.putString("failure_class", e.getClass().getSimpleName());
        }
        finish(-1, out);
    }
}
