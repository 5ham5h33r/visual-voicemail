package org.visualvoicemail.app;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.view.animation.DecelerateInterpolator;

/** Rounded-square appearance for a native CheckBox, with an interruptible drawn checkmark. */
final class RoundedCheckDrawable extends Drawable implements Animatable {
    private final Context context;
    private final Paint box = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mark = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF shape = new RectF();
    private final Path check = new Path(), drawn = new Path();
    private final PathMeasure measure = new PathMeasure();
    private ColorStateList tint;
    private ValueAnimator animator;
    private float progress;
    private boolean checked, enabled = true;
    private int alpha = 255;

    RoundedCheckDrawable(Context context) {
        this.context = context;
        tint = ColorStateList.valueOf(context.getColor(R.color.accent));
        box.setStrokeWidth(dp(1.8f));
        mark.setStyle(Paint.Style.STROKE);
        mark.setStrokeWidth(dp(2.1f));
        mark.setStrokeCap(Paint.Cap.ROUND);
        mark.setStrokeJoin(Paint.Join.ROUND);
        // The check is a cutout, so it always matches the actual card beneath it.
        mark.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
    }

    private float dp(float value) {
        return value * context.getResources().getDisplayMetrics().density;
    }

    @Override
    public int getIntrinsicWidth() {
        return Math.round(dp(32));
    }

    @Override
    public int getIntrinsicHeight() {
        return Math.round(dp(32));
    }

    @Override
    public boolean isStateful() {
        return true;
    }

    @Override
    protected boolean onStateChange(int[] state) {
        boolean nextChecked = false, nextEnabled = false;
        for (int value : state) {
            if (value == android.R.attr.state_checked) nextChecked = true;
            if (value == android.R.attr.state_enabled) nextEnabled = true;
        }
        boolean changed = checked != nextChecked || enabled != nextEnabled;
        enabled = nextEnabled;
        if (checked != nextChecked) {
            checked = nextChecked;
            start();
        }
        if (!AppMotion.enabled(context)) jumpToCurrentState();
        invalidateSelf();
        return changed || tint.isStateful();
    }

    @Override
    public void start() {
        float target = checked ? 1 : 0;
        cancel();
        if (progress == target) return;
        if (!isVisible() || !AppMotion.enabled(context)) {
            progress = target;
            invalidateSelf();
            return;
        }
        ValueAnimator next = ValueAnimator.ofFloat(progress, target);
        animator = next;
        next.setDuration(160);
        next.setInterpolator(new DecelerateInterpolator());
        next.addUpdateListener(
                value -> {
                    progress = (Float) value.getAnimatedValue();
                    invalidateSelf();
                });
        next.addListener(
                new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (animator == animation) animator = null;
                    }
                });
        next.start();
    }

    private void cancel() {
        ValueAnimator old = animator;
        animator = null;
        if (old != null) old.cancel();
    }

    @Override
    public void stop() {
        jumpToCurrentState();
    }

    @Override
    public boolean isRunning() {
        return animator != null;
    }

    @Override
    public void jumpToCurrentState() {
        cancel();
        progress = checked ? 1 : 0;
        invalidateSelf();
    }

    @Override
    public boolean setVisible(boolean visible, boolean restart) {
        boolean changed = super.setVisible(visible, restart);
        if (!visible) jumpToCurrentState();
        return changed;
    }

    @Override
    public void draw(Canvas canvas) {
        float cx = getBounds().exactCenterX(), cy = getBounds().exactCenterY(), half = dp(10);
        shape.set(cx - half, cy - half, cx + half, cy + half);
        float radius = dp(5.5f);
        box.setColor(tint.getColorForState(getState(), tint.getDefaultColor()));
        int opacity = Math.round(alpha * (enabled ? 1 : .38f));
        int layer =
                canvas.saveLayer(
                        shape.left - dp(2),
                        shape.top - dp(2),
                        shape.right + dp(2),
                        shape.bottom + dp(2),
                        null);
        box.setStyle(Paint.Style.FILL);
        box.setAlpha(Math.round(opacity * progress));
        canvas.drawRoundRect(shape, radius, radius, box);
        box.setStyle(Paint.Style.STROKE);
        box.setAlpha(opacity);
        canvas.drawRoundRect(shape, radius, radius, box);
        float fraction = Math.max(0, Math.min(1, (progress - .20f) / .80f));
        if (fraction > 0) {
            check.reset();
            check.moveTo(cx - dp(5), cy);
            check.lineTo(cx - dp(1.2f), cy + dp(3.8f));
            check.lineTo(cx + dp(5.4f), cy - dp(3.4f));
            measure.setPath(check, false);
            drawn.reset();
            measure.getSegment(0, measure.getLength() * fraction, drawn, true);
            canvas.drawPath(drawn, mark);
        }
        canvas.restoreToCount(layer);
    }

    @Override
    public void setTintList(ColorStateList tint) {
        if (tint != null) {
            this.tint = tint;
            invalidateSelf();
        }
    }

    @Override
    public void setAlpha(int alpha) {
        this.alpha = alpha;
        invalidateSelf();
    }

    @Override
    public int getAlpha() {
        return alpha;
    }

    @Override
    public void setColorFilter(ColorFilter filter) {
        box.setColorFilter(filter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
