package org.visualvoicemail.checks;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;
import android.view.WindowManager;

/** Keep only temporary test activities awake; never change the user's display preferences. */
public abstract class DeviceCheckInstrumentation extends Instrumentation {
    @Override
    public void callActivityOnCreate(Activity activity, Bundle state) {
        super.callActivityOnCreate(activity, state);
        activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
}
