package org.visualvoicemail.app;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;

/** The native Menu model, presented with the same surfaces and type as the rest of the app. */
final class AppMenu extends PopupMenu {
    private final Activity activity;
    private final View anchor;
    private PopupWindow window;

    AppMenu(Activity activity, View anchor) {
        super(activity, anchor);
        this.activity = activity;
        this.anchor = anchor;
    }

    @Override
    public void show() {
        dismiss();
        Ui ui = new Ui(activity);
        LinearLayout items = ui.column();
        items.setPadding(ui.dp(6), ui.dp(6), ui.dp(6), ui.dp(6));
        int group = -1;
        for (int i = 0; i < getMenu().size(); i++) {
            MenuItem item = getMenu().getItem(i);
            if (!item.isVisible()) continue;
            if (group != -1 && group != item.getGroupId()) {
                View divider = new View(activity);
                divider.setBackgroundColor(ui.divider);
                LinearLayout.LayoutParams line = new LinearLayout.LayoutParams(-1, ui.dp(1));
                line.setMargins(ui.dp(12), ui.dp(4), ui.dp(12), ui.dp(4));
                items.addView(divider, line);
            }
            group = item.getGroupId();
            LinearLayout row = ui.row();
            row.setMinimumHeight(ui.dp(48));
            row.setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(10));
            row.setBackground(
                    new RippleDrawable(
                            ColorStateList.valueOf(ui.divider),
                            ui.shape(item.isChecked() ? ui.tint : 0, 12),
                            null));
            TextView title =
                    ui.label(
                            item.getTitle().toString(),
                            15,
                            item.isChecked() ? ui.accent : ui.text,
                            false);
            row.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
            if (item.isChecked()) {
                TextView tick = ui.label("✓", 16, ui.accent, true);
                tick.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(ui.dp(28), -2);
                tick.setGravity(Gravity.END);
                row.addView(tick, size);
            }
            row.setEnabled(item.isEnabled());
            row.setAlpha(item.isEnabled() ? 1 : .4f);
            row.setFocusable(item.isEnabled());
            row.setContentDescription(item.getTitle() + (item.isChecked() ? ", selected" : ""));
            row.setAccessibilityDelegate(
                    new View.AccessibilityDelegate() {
                        @Override
                        public void onInitializeAccessibilityNodeInfo(
                                View host, AccessibilityNodeInfo info) {
                            super.onInitializeAccessibilityNodeInfo(host, info);
                            info.setClassName(Button.class.getName());
                            info.setCheckable(item.isCheckable());
                            info.setChecked(item.isChecked());
                        }
                    });
            row.setOnClickListener(
                    v -> {
                        dismiss();
                        getMenu().performIdentifierAction(item.getItemId(), 0);
                    });
            AppMotion.press(row);
            items.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }
        ScrollView scroll = new ScrollView(activity);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(items);
        int width =
                Math.min(
                        ui.dp(280),
                        activity.getResources().getDisplayMetrics().widthPixels - ui.dp(32));
        scroll.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int height =
                Math.min(
                        scroll.getMeasuredHeight(),
                        activity.getResources().getDisplayMetrics().heightPixels * 2 / 3);
        window = new PopupWindow(scroll, width, height, true);
        window.setBackgroundDrawable(ui.shape(ui.surface, 18));
        window.setElevation(ui.dp(8));
        final PopupWindow shown = window;
        shown.setOnDismissListener(
                () -> {
                    if (window == shown) window = null;
                });
        window.setOutsideTouchable(true);
        window.setClippingEnabled(true);
        window.setAnimationStyle(0);
        window.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        window.showAsDropDown(anchor, 0, ui.dp(4), Gravity.END);
        AppMotion.reveal(items, 100, 2);
    }

    @Override
    public void dismiss() {
        if (window != null) {
            PopupWindow old = window;
            window = null;
            old.dismiss();
        }
        super.dismiss();
    }
}
