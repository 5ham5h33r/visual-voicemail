package org.visualvoicemail.app;

import android.app.Activity;
import android.graphics.*;
import android.view.*;
import android.widget.*;

import java.util.List;
import java.util.function.Supplier;

/**
 * Accessible, skippable tips anchored to real controls. Highlight taps invoke the actual control.
 */
final class GuideOverlay extends FrameLayout {
    static final class Tip {
        final Supplier<View> target;
        final String title, body;
        final boolean activate;

        Tip(View target, String title, String body, boolean activate) {
            this(() -> target, title, body, activate);
        }

        Tip(Supplier<View> target, String title, String body, boolean activate) {
            this.target = target;
            this.title = title;
            this.body = body;
            this.activate = activate;
        }
    }

    private final Activity activity;
    private final Ui ui;
    private final List<Tip> tips;
    private final Runnable complete;
    private final Runnable skipped;
    private final Paint paint = new Paint(3);
    private final RectF hole = new RectF();
    private final LinearLayout card;
    private final TextView title, body, step;
    private final Button next;
    private int index;
    private View content;
    private int previousAccessibility;
    private android.window.OnBackInvokedCallback backCallback;

    static void show(Activity a, List<Tip> tips, Runnable done) {
        show(a, tips, done, done);
    }

    static void show(Activity a, List<Tip> tips, Runnable done, Runnable skip) {
        if (tips.isEmpty()) {
            done.run();
            return;
        }
        FrameLayout host = (FrameLayout) a.getWindow().getDecorView();
        GuideOverlay guide = new GuideOverlay(a, tips, done, skip);
        host.addView(guide, new FrameLayout.LayoutParams(-1, -1));
        guide.post(guide::position);
    }

    private GuideOverlay(Activity a, List<Tip> tips, Runnable done, Runnable skipAction) {
        super(a);
        activity = a;
        ui = new Ui(a);
        this.tips = tips;
        complete = done;
        skipped = skipAction;
        setWillNotDraw(false);
        setClickable(true);
        setFocusable(true);
        content = a.findViewById(android.R.id.content);
        previousAccessibility = content.getImportantForAccessibility();
        content.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        card = ui.column();
        card.setPadding(ui.dp(18), ui.dp(16), ui.dp(18), ui.dp(14));
        card.setBackground(ui.shape(ui.surface, 24));
        card.setElevation(ui.dp(16));
        step = ui.label("", 12, ui.secondary, true);
        card.addView(step);
        title = ui.label("", 19, ui.text, true);
        ui.add(card, title, 10);
        body = ui.label("", 14, ui.secondary, false);
        body.setLineSpacing(ui.dp(4), 1);
        ui.add(card, body, 10);
        LinearLayout actions = ui.row();
        Button skip = ui.button("Skip tour", false, this::skip);
        skip.setBackgroundColor(0);
        actions.addView(skip, new LinearLayout.LayoutParams(0, -2, 1));
        next = ui.button("Next", true, this::advance);
        LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(0, -2, 1);
        gap.setMarginStart(ui.dp(12));
        actions.addView(next, gap);
        ui.add(card, actions, 12);
        addView(card, new FrameLayout.LayoutParams(-1, -2));
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            backCallback = this::skip;
            a.getOnBackInvokedDispatcher()
                    .registerOnBackInvokedCallback(
                            android.window.OnBackInvokedDispatcher.PRIORITY_OVERLAY, backCallback);
        }
        setOnTouchListener(
                (v, event) -> {
                    if (event.getAction() == MotionEvent.ACTION_UP) {
                        if (hole.contains(event.getX(), event.getY()) && tips.get(index).activate) {
                            View target = tips.get(index).target.get();
                            finish();
                            if (target != null) target.performClick();
                        }
                        return true;
                    }
                    return true;
                });
        setOnKeyListener(
                (v, key, event) -> {
                    if (key == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
                        skip();
                        return true;
                    }
                    return false;
                });
        addOnLayoutChangeListener(
                (v, l, t, r, b, ol, ot, or, ob) -> {
                    if (r - l != or - ol || b - t != ob - ot) post(this::position);
                });
    }

    private void position() {
        if (getParent() == null) return;
        Tip tip = tips.get(index);
        View target = tip.target.get();
        if (target == null || !target.isShown()) {
            advance();
            return;
        }
        if (target.getWidth() == 0 || target.getHeight() == 0) {
            postOnAnimation(this::position);
            return;
        }
        target.requestRectangleOnScreen(
                new Rect(0, 0, target.getWidth(), target.getHeight()), true);
        int[] at = new int[2], here = new int[2];
        target.getLocationInWindow(at);
        getLocationInWindow(here);
        hole.set(
                at[0] - here[0] - ui.dp(4),
                at[1] - here[1] - ui.dp(4),
                at[0] - here[0] + target.getWidth() + ui.dp(4),
                at[1] - here[1] + target.getHeight() + ui.dp(4));
        step.setText((index + 1) + " OF " + tips.size());
        title.setText(tip.title);
        body.setText(tip.body);
        next.setText(index == tips.size() - 1 ? "Done" : "Next");
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) card.getLayoutParams();
        params.width = getWidth() - ui.dp(40);
        params.leftMargin = ui.dp(20);
        card.measure(
                MeasureSpec.makeMeasureSpec(params.width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(getHeight(), MeasureSpec.AT_MOST));
        int h = card.getMeasuredHeight();
        int below = (int) hole.bottom + ui.dp(16), above = (int) hole.top - h - ui.dp(16);
        params.topMargin = below + h < getHeight() - ui.dp(32) ? below : Math.max(ui.dp(48), above);
        card.setLayoutParams(params);
        invalidate();
        ui.enter(card);
        title.sendAccessibilityEvent(
                android.view.accessibility.AccessibilityEvent.TYPE_VIEW_FOCUSED);
    }

    private void advance() {
        if (++index >= tips.size()) {
            finish();
            return;
        }
        position();
    }

    private void finish() {
        dismiss(complete);
    }

    private void skip() {
        dismiss(skipped);
    }

    private void dismiss(Runnable action) {
        if (getParent() == null) return;
        if (android.os.Build.VERSION.SDK_INT >= 33 && backCallback != null)
            activity.getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backCallback);
        content.setImportantForAccessibility(previousAccessibility);
        ((android.view.ViewGroup) getParent()).removeView(this);
        action.run();
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        Path p = new Path();
        p.setFillType(Path.FillType.EVEN_ODD);
        p.addRect(0, 0, getWidth(), getHeight(), Path.Direction.CW);
        p.addRoundRect(hole, ui.dp(18), ui.dp(18), Path.Direction.CW);
        paint.setColor(0xB8000000);
        paint.setStyle(Paint.Style.FILL);
        c.drawPath(p, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(ui.dp(2));
        paint.setColor(ui.accent);
        c.drawRoundRect(hole, ui.dp(18), ui.dp(18), paint);
    }
}
