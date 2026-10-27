package org.visualvoicemail.app;

import android.app.Activity;
import android.app.TaskStackBuilder;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

/** Keep every app screen on the same native navigation/reduced-motion policy. */
public abstract class MotionActivity extends Activity {
    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        AppMotion.configure(this);
        if (Build.VERSION.SDK_INT >= 33 && isTaskRoot() && getParentActivityIntent() != null)
            getOnBackInvokedDispatcher()
                    .registerOnBackInvokedCallback(
                            android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                            this::navigateUp);
    }

    @Override
    protected void onResume() {
        super.onResume();
        AppMotion.configure(this);
    }

    protected final void navigateUp() {
        if (isTaskRoot()) {
            Intent parent = getParentActivityIntent();
            if (parent != null)
                TaskStackBuilder.create(this)
                        .addNextIntentWithParentStack(parent)
                        .startActivities();
        }
        finish();
    }

    @Override
    public void onBackPressed() {
        if (isTaskRoot() && getParentActivityIntent() != null) navigateUp();
        else super.onBackPressed();
    }
}
