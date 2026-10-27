package org.visualvoicemail.app;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.transition.AutoTransition;
import android.transition.ChangeBounds;
import android.transition.Fade;
import android.transition.TransitionManager;
import android.transition.TransitionSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;

/** Brief, interruptible feedback; Android controls navigation direction and animation scaling. */
final class AppMotion {
    static boolean enabled(Context context) {
        return ValueAnimator.areAnimatorsEnabled()
                && !context.getSharedPreferences("settings", 0).getBoolean("reduce_motion", false);
    }

    static void configure(Activity activity) {
        android.content.res.TypedArray style =
                activity.obtainStyledAttributes(new int[] {android.R.attr.windowAnimationStyle});
        int nativeStyle = style.getResourceId(0, 0);
        style.recycle();
        activity.getWindow().setWindowAnimations(enabled(activity) ? nativeStyle : 0);
    }

    static void settle(View view) {
        view.animate().cancel();
        view.setAlpha(1);
        view.setTranslationY(0);
    }

    static void reveal(View view, int duration, int offsetDp) {
        settle(view);
        if (!enabled(view.getContext()) || !view.isAttachedToWindow()) return;
        view.setAlpha(.35f);
        view.setTranslationY(offsetDp * view.getResources().getDisplayMetrics().density);
        view.animate()
                .alpha(1)
                .translationY(0)
                .setDuration(duration)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    static void visibility(View view, boolean show) {
        int visibility = show ? View.VISIBLE : View.GONE;
        if (view.getVisibility() == visibility) return;
        ViewGroup parent =
                view.getParent() instanceof ViewGroup ? (ViewGroup) view.getParent() : null;
        if (parent != null) {
            TransitionManager.endTransitions(parent);
            if (enabled(view.getContext()) && parent.isLaidOut())
                TransitionManager.beginDelayedTransition(
                        parent, new AutoTransition().setDuration(160));
        }
        view.setVisibility(visibility);
    }

    /** Selection controls and their label space change together, without delaying their actions. */
    static void selectionLayout(ViewGroup parent, boolean animate) {
        TransitionManager.endTransitions(parent);
        if (animate && enabled(parent.getContext()) && parent.isLaidOut())
            TransitionManager.beginDelayedTransition(
                    parent,
                    new TransitionSet()
                            .setOrdering(TransitionSet.ORDERING_TOGETHER)
                            .addTransition(new ChangeBounds())
                            .addTransition(new Fade())
                            .setDuration(160)
                            .setInterpolator(new DecelerateInterpolator()));
    }

    static final class CheckBox extends android.widget.CheckBox {
        CheckBox(Context context) {
            super(context);
        }

        @Override
        public void setChecked(boolean checked) {
            super.setChecked(checked);
            if (!enabled(getContext())) jumpDrawablesToCurrentState();
        }
    }

    static void press(View view) {
        if (view == null) return;
        view.setOnTouchListener(
                (v, event) -> {
                    if (!v.isEnabled()) return false;
                    int action = event.getActionMasked();
                    if (action == MotionEvent.ACTION_DOWN
                            || action == MotionEvent.ACTION_UP
                            || action == MotionEvent.ACTION_CANCEL) {
                        settle(v);
                        if (!enabled(v.getContext())) {
                            v.setScaleX(1);
                            v.setScaleY(1);
                            return false;
                        }
                        boolean down = action == MotionEvent.ACTION_DOWN;
                        float scale =
                                down
                                        ? (v instanceof ViewGroup
                                                ? .99f
                                                : v instanceof android.widget.Button
                                                        ? .98f
                                                        : v.getWidth()
                                                                        > 160
                                                                                * v.getResources()
                                                                                        .getDisplayMetrics()
                                                                                        .density
                                                                ? .99f
                                                                : .96f)
                                        : 1;
                        v.animate()
                                .scaleX(scale)
                                .scaleY(scale)
                                .setDuration(down ? 70 : 110)
                                .setInterpolator(new DecelerateInterpolator())
                                .start();
                    }
                    return false; // Never consume the tap or defer its click action.
                });
    }

    static void buttons(android.app.AlertDialog dialog) {
        for (int which : new int[] {-1, -2, -3}) press(dialog.getButton(which));
    }

    static final class Switch extends android.widget.Switch {
        Switch(Context context) {
            super(context);
        }

        @Override
        public void setChecked(boolean checked) {
            super.setChecked(checked);
            if (!enabled(getContext())) jumpDrawablesToCurrentState();
        }
    }
}
