package org.visualvoicemail.app;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;

import java.util.ArrayList;
import java.util.List;

/** Reuse native message views; direct drags never capture bitmaps or write diagnostics. */
final class InboxNavigation extends FrameLayout {
    interface Listener {
        int current();

        default int last() {
            return 2;
        }

        default boolean fillViewport() {
            return false;
        }

        boolean available();

        void navigate(int page);

        View prepare(int page);
    }

    private Listener listener;
    private View messages, filters, preview, outgoing;
    private final List<Leaving> leaving = new ArrayList<>();
    private ValueAnimator animator;
    private float progress = 1, downX, downY, drag;
    private boolean tracking, horizontal, vertical;
    private int previewPage = -1;
    private VelocityTracker velocity;
    private final int slop;
    private final int[] hostAt = new int[2], childAt = new int[2];

    private static final class Leaving {
        final View view;
        float from, to;

        Leaving(View view) {
            this.view = view;
        }
    }

    InboxNavigation(Context context) {
        super(context);
        slop = ViewConfiguration.get(context).getScaledTouchSlop();
        setClipChildren(true);
    }

    void bind(View messages, View filters, Listener listener) {
        this.messages = messages;
        this.filters = filters;
        this.listener = listener;
    }

    void messages(View next) {
        messages = next;
    }

    void contentChanged() {
        settle();
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private float pageDistance() {
        return getWidth() + dp(16);
    }

    private boolean motion() {
        return isLaidOut() && AppMotion.enabled(getContext());
    }

    private void cancelAnimator() {
        ValueAnimator old = animator;
        animator = null;
        if (old != null) old.cancel();
    }

    private void removePreview() {
        if (preview != null) {
            getOverlay().remove(preview);
            preview.setTranslationX(0);
            preview = null;
        }
        previewPage = -1;
    }

    private void settle() {
        cancelAnimator();
        removePreview();
        for (Leaving layer : leaving)
            if (layer.view != messages) {
                getOverlay().remove(layer.view);
                layer.view.setTranslationX(0);
            }
        leaving.clear();
        outgoing = null;
        progress = 1;
        if (messages != null) {
            messages.setTranslationX(0);
            messages.setAlpha(1);
        }
    }

    private int bodyTop() {
        getLocationOnScreen(hostAt);
        messages.getLocationOnScreen(childAt);
        return Math.max(0, childAt[1] - hostAt[1]);
    }

    private void mount(View view, int top, boolean measure) {
        if (measure)
            view.measure(
                    MeasureSpec.makeMeasureSpec(getWidth(), MeasureSpec.EXACTLY),
                    listener.fillViewport()
                            ? MeasureSpec.makeMeasureSpec(getHeight(), MeasureSpec.EXACTLY)
                            : MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        int height = measure ? view.getMeasuredHeight() : view.getHeight();
        getOverlay().add(view);
        view.layout(0, top, getWidth(), top + height);
        view.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
    }

    void change(int direction, View next, Runnable update) {
        if (!motion()) {
            settle();
            update.run();
            messages.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_AUTO);
            messages.setTranslationX(0);
            messages.setAlpha(1);
            return;
        }
        cancelAnimator();
        View previous = messages;
        float from =
                next == preview
                        ? next.getTranslationX()
                        : direction * pageDistance() + previous.getTranslationX();
        for (int i = leaving.size() - 1; i >= 0; i--)
            if (leaving.get(i).view == next) {
                from = next.getTranslationX();
                getOverlay().remove(next);
                leaving.remove(i);
            }
        if (preview != null) {
            getOverlay().remove(preview);
            if (preview != next) preview.setTranslationX(0);
            preview = null;
            previewPage = -1;
        }
        getLocationOnScreen(hostAt);
        previous.getLocationOnScreen(childAt);
        int top = childAt[1] - hostAt[1];
        float previousX = previous.getTranslationX();
        mount(previous, top, false);
        previous.setTranslationX(previousX);
        Leaving newest = new Leaving(previous);
        leaving.add(newest);
        outgoing = previous;
        for (Leaving layer : leaving) {
            layer.from = layer.view.getTranslationX();
            layer.to =
                    layer == newest
                            ? -direction * pageDistance()
                            : layer.from < 0 ? -pageDistance() : pageDistance();
        }
        update.run();
        messages.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_AUTO);
        messages.setAlpha(1);
        messages.setTranslationX(from);
        final float incoming = from;
        float remaining = Math.min(1, Math.abs(from) / Math.max(1, getWidth()));
        animate(
                Math.round(Math.max(70, 140 * remaining)),
                f -> {
                    messages.setTranslationX(incoming * (1 - f));
                    for (Leaving layer : leaving)
                        layer.view.setTranslationX(layer.from + (layer.to - layer.from) * f);
                },
                this::settle);
    }

    private interface Frame {
        void apply(float fraction);
    }

    private void animate(long duration, Frame frame, Runnable complete) {
        ValueAnimator next = ValueAnimator.ofFloat(0, 1);
        animator = next;
        progress = 0;
        next.setDuration(duration);
        next.setInterpolator(new DecelerateInterpolator());
        next.addUpdateListener(
                value -> {
                    progress = (Float) value.getAnimatedValue();
                    frame.apply(progress);
                });
        next.addListener(
                new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (animator == animation) {
                            animator = null;
                            complete.run();
                        }
                    }
                });
        next.start();
    }

    private void dragTo(float dx) {
        int delta = dx < 0 ? 1 : -1;
        if (getLayoutDirection() == LAYOUT_DIRECTION_RTL) delta = -delta;
        int target = listener.current() + delta;
        if (!motion()) return;
        if (target < 0 || target > listener.last()) {
            removePreview();
            messages.setTranslationX(dx * .16f);
            return;
        }
        if (previewPage != target) {
            removePreview();
            preview = listener.prepare(target);
            previewPage = target;
            mount(preview, bodyTop(), true);
        }
        messages.setTranslationX(dx);
        int physical = dx < 0 ? 1 : -1;
        preview.setTranslationX(physical * pageDistance() + dx);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_POINTER_DOWN
                || action != MotionEvent.ACTION_DOWN && tracking && !listener.available()) {
            tracking = false;
            releaseVelocity();
            return false;
        }
        if (action == MotionEvent.ACTION_DOWN) {
            releaseVelocity();
            downX = event.getX();
            downY = event.getY();
            drag = 0;
            horizontal = vertical = false;
            getLocationOnScreen(hostAt);
            if (filters != null) filters.getLocationOnScreen(childAt);
            tracking =
                    listener != null
                            && listener.available()
                            && downY >= (filters == null ? 0 : Math.max(0, childAt[1] - hostAt[1]))
                            && downX > dp(16)
                            && downX < getWidth() - dp(16);
            if (tracking) {
                velocity = VelocityTracker.obtain();
                velocity.addMovement(event);
            }
        } else if (tracking && action == MotionEvent.ACTION_MOVE && !vertical) {
            float dx = event.getX() - downX, dy = event.getY() - downY;
            if (Math.abs(dy) > slop && Math.abs(dy) >= Math.abs(dx)) vertical = true;
            else if (Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy) * 1.25f) {
                settle();
                horizontal = true;
                getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            }
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            tracking = false;
            releaseVelocity();
        }
        return horizontal;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!horizontal) return super.onTouchEvent(event);
        if (velocity != null) velocity.addMovement(event);
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_MOVE) {
            drag = event.getX() - downX;
            dragTo(drag);
        } else if (action == MotionEvent.ACTION_UP
                || action == MotionEvent.ACTION_CANCEL
                || action == MotionEvent.ACTION_POINTER_DOWN) {
            float dx = event.getX() - downX, speed = 0;
            if (velocity != null) {
                velocity.computeCurrentVelocity(1000);
                speed = velocity.getXVelocity();
            }
            int delta = dx < 0 ? 1 : -1;
            if (getLayoutDirection() == LAYOUT_DIRECTION_RTL) delta = -delta;
            int target = listener.current() + delta;
            boolean commit =
                    action == MotionEvent.ACTION_UP
                            && listener.available()
                            && target >= 0
                            && target <= listener.last()
                            && (Math.abs(dx) > getWidth() * .20f
                                    || Math.abs(dx) > slop * 2
                                            && Math.abs(speed) > dp(650)
                                            && speed * dx > 0);
            horizontal = tracking = false;
            releaseVelocity();
            if (commit) listener.navigate(target);
            else if (motion()) {
                final float start = messages.getTranslationX();
                final View other = preview;
                final float otherStart = other == null ? 0 : other.getTranslationX();
                final float end = dx < 0 ? pageDistance() : -pageDistance();
                animate(
                        110,
                        f -> {
                            messages.setTranslationX(start * (1 - f));
                            if (other != null)
                                other.setTranslationX(otherStart + (end - otherStart) * f);
                        },
                        this::settle);
            } else settle();
        }
        return true;
    }

    private void releaseVelocity() {
        if (velocity != null) {
            velocity.recycle();
            velocity = null;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        settle();
        releaseVelocity();
        super.onDetachedFromWindow();
    }
}
