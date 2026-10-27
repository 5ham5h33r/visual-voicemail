package org.visualvoicemail.app;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Shared native layout rhythm and vector controls. Text sizes follow the user's font scale. */
final class Ui {
    final Activity a;
    final int bg, surface, text, secondary, accent, tint, divider;

    Ui(Activity a) {
        this.a = a;
        bg = AppAppearance.background(a);
        surface = a.getColor(R.color.surface);
        text = a.getColor(R.color.primary_text);
        secondary = a.getColor(R.color.secondary_text);
        accent = a.getColor(R.color.accent);
        tint = a.getColor(R.color.accent_surface);
        divider = a.getColor(R.color.divider);
    }

    int dp(float n) {
        return (int) (n * a.getResources().getDisplayMetrics().density + .5f);
    }

    LinearLayout column() {
        LinearLayout v = new LinearLayout(a);
        v.setOrientation(1);
        return v;
    }

    LinearLayout row() {
        LinearLayout v = new LinearLayout(a);
        v.setGravity(Gravity.CENTER_VERTICAL);
        return v;
    }

    GradientDrawable shape(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

    TextView label(String s, int size, int color, boolean bold) {
        TextView t = new TextView(a);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif", 0));
        t.setIncludeFontPadding(false);
        return t;
    }

    void add(LinearLayout root, View v, int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(top);
        root.addView(v, p);
    }

    Button button(String s, boolean primary, Runnable action) {
        Button b = new Button(a);
        b.setText(s);
        b.setAllCaps(false);
        b.setStateListAnimator(null);
        b.setElevation(0);
        b.setTextSize(16);
        b.setTextColor(primary ? bg : accent);
        b.setTypeface(Typeface.create("sans-serif-medium", 0));
        b.setMinHeight(dp(52));
        b.setPadding(dp(20), dp(12), dp(20), dp(12));
        b.setBackground(
                new RippleDrawable(
                        ColorStateList.valueOf(divider), shape(primary ? accent : tint, 16), null));
        b.setOnClickListener(v -> action.run());
        AppMotion.press(b);
        return b;
    }

    void show(android.app.AlertDialog dialog) {
        if (!AppMotion.enabled(a)) dialog.getWindow().setWindowAnimations(0);
        dialog.show();
        AppMotion.buttons(dialog);
        dialog.getWindow().setBackgroundDrawable(shape(surface, 24));
        for (int which : new int[] {-1, -2, -3}) {
            Button b = dialog.getButton(which);
            if (b != null) {
                b.setAllCaps(false);
                b.setTextColor(accent);
                b.setMinHeight(dp(48));
            }
        }
    }

    Icon icon(String name, String description, boolean solid, Runnable action) {
        Icon v = new Icon(name, solid);
        v.setContentDescription(description);
        v.setOnClickListener(w -> action.run());
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(solid ? 64 : 48), dp(solid ? 64 : 48)));
        return v;
    }

    void enter(View v) {
        v.post(() -> AppMotion.reveal(v, 180, 8));
    }

    final class Icon extends View {
        String name;
        final boolean solid;
        private final Paint p = new Paint(3);

        Icon(String name, boolean solid) {
            super(a);
            this.name = name;
            this.solid = solid;
            AppMotion.press(this);
            setFocusable(true);
            setClickable(true);
            setBackground(
                    new RippleDrawable(
                            ColorStateList.valueOf(divider), shape(solid ? accent : 0, 40), null));
        }

        @Override
        public CharSequence getAccessibilityClassName() {
            return Button.class.getName();
        }

        @Override
        protected void onDraw(Canvas c) {
            super.onDraw(c);
            c.save();
            c.translate(getWidth() / 2f, getHeight() / 2f);
            c.scale(dp(1), dp(1));
            p.setColor(solid ? bg : accent);
            p.setStrokeWidth(1.8f);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
            p.setStyle(Paint.Style.STROKE);
            p.setAlpha(isEnabled() ? 255 : 90);
            if (name.equals("play")) {
                p.setStyle(Paint.Style.FILL);
                Path t = new Path();
                t.moveTo(-5, -10);
                t.lineTo(10, 0);
                t.lineTo(-5, 10);
                t.close();
                c.drawPath(t, p);
            } else if (name.equals("pause")) {
                p.setStyle(Paint.Style.FILL);
                c.drawRoundRect(-7, -10, -2, 10, 1, 1, p);
                c.drawRoundRect(2, -10, 7, 10, 1, 1, p);
            } else if (name.startsWith("rewind") || name.startsWith("forward")) {
                boolean back = name.startsWith("rewind");
                if (!back) c.scale(-1, 1);
                c.drawArc(-10, -9, 10, 11, -150, 295, false, p);
                c.drawLine(-8, -10, -8, -3, p);
                c.drawLine(-8, -3, -1, -3, p);
                if (!back) c.scale(-1, 1);
                p.setStyle(Paint.Style.FILL);
                p.setTextAlign(Paint.Align.CENTER);
                p.setTypeface(Typeface.create("sans-serif-medium", 0));
                p.setTextSize(8);
                c.drawText(name.replaceAll("\\D", ""), 0, 5, p);
            } else if (name.equals("back") || name.equals("next")) {
                if (name.equals("next")) c.scale(-1, 1);
                c.drawLine(4, -7, -3, 0, p);
                c.drawLine(-3, 0, 4, 7, p);
            } else if (name.equals("search")) {
                c.drawCircle(-2, -2, 7, p);
                c.drawLine(3, 3, 9, 9, p);
            } else if (name.equals("more")) {
                p.setStyle(Paint.Style.FILL);
                for (int x = -6; x <= 6; x += 6) c.drawCircle(x, 0, 1.5f, p);
            } else if (name.equals("close")) {
                c.drawLine(-6, -6, 6, 6, p);
                c.drawLine(-6, 6, 6, -6, p);
            } else if (name.equals("sound")) {
                Path t = new Path();
                t.moveTo(-10, -3);
                t.lineTo(-5, -3);
                t.lineTo(1, -8);
                t.lineTo(1, 8);
                t.lineTo(-5, 3);
                t.lineTo(-10, 3);
                t.close();
                c.drawPath(t, p);
                c.drawArc(0, -10, 13, 10, -65, 130, false, p);
            } else if (name.equals("bell")) {
                c.drawArc(-8, -9, 8, 8, 180, 180, false, p);
                c.drawLine(-8, -1, -10, 7, p);
                c.drawLine(8, -1, 10, 7, p);
                c.drawLine(-10, 7, 10, 7, p);
                c.drawArc(-3, 8, 3, 12, 0, 180, false, p);
            } else {
                c.drawRoundRect(-10, -10, 10, 10, 4, 4, p);
                c.drawLine(-5, -3, 5, -3, p);
                c.drawLine(-5, 2, 5, 2, p);
                c.drawLine(-5, 7, 1, 7, p);
            }
            c.restore();
        }
    }
}
