package org.visualvoicemail.app;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;

/** One sliding selection indicator; button state and accessibility change immediately. */
final class InboxTabs extends LinearLayout {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float position;
    private int target;
    private ValueAnimator animator;

    InboxTabs(Context context, int color) {
        super(context);
        paint.setColor(color);
    }

    void selected(int index) {
        if (target == index && AppMotion.enabled(getContext())) return;
        target = index;
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        if (!isLaidOut() || !AppMotion.enabled(getContext())) {
            position = index;
            invalidate();
            return;
        }
        ValueAnimator next = ValueAnimator.ofFloat(position, index);
        animator = next;
        next.setDuration(200);
        next.setInterpolator(new DecelerateInterpolator());
        next.addUpdateListener(
                value -> {
                    position = (Float) value.getAnimatedValue();
                    invalidate();
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

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (getChildCount() > 0) {
            int first = Math.max(0, Math.min(getChildCount() - 1, (int) position));
            int second = Math.min(getChildCount() - 1, first + 1);
            View a = getChildAt(first), b = getChildAt(second);
            float f = position - first;
            float left = a.getLeft() + (b.getLeft() - a.getLeft()) * f;
            float right = a.getRight() + (b.getRight() - a.getRight()) * f;
            float radius = 24 * getResources().getDisplayMetrics().density;
            canvas.drawRoundRect(left, a.getTop(), right, a.getBottom(), radius, radius, paint);
        }
        super.dispatchDraw(canvas);
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        position = target;
        super.onDetachedFromWindow();
    }
}
