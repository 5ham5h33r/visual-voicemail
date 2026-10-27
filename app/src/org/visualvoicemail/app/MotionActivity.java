package org.visualvoicemail.app;

import android.app.Activity;
import android.os.Bundle;

/** Keep every app screen on the same native navigation/reduced-motion policy. */
public abstract class MotionActivity extends Activity {
    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        AppMotion.configure(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        AppMotion.configure(this);
    }
}
