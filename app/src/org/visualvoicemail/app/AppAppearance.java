package org.visualvoicemail.app;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;

final class AppAppearance {
    static int mode(Context context) { return context.getSharedPreferences("settings", Context.MODE_PRIVATE).getInt("appearance", 0); }
    static Context context(Context base) {
        int mode = mode(base);
        if (mode == 0) return base;
        Configuration config = new Configuration(base.getResources().getConfiguration());
        config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
            | (mode >= 2 ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO);
        return base.createConfigurationContext(config);
    }
    static void apply(Activity activity) { activity.setTheme(mode(activity) == 3 ? R.style.AppTheme_Amoled : R.style.AppTheme); }
    static int background(Context context) {
        return context.getResources().getColor(mode(context) == 3 ? R.color.amoled_background : R.color.background, context.getTheme());
    }
}
