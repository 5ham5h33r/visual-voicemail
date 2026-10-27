package org.visualvoicemail.app;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ScrollView;

/** One native scroll surface with a stable header height during scrolling. */
final class InboxScrollView extends ScrollView {
    private View header, title, carrier, compact, searchAction, filterAction;
    private boolean selection, expandOnExit = true, addHeaderOnRestore;
    private int pendingOffset = -1;
    private SelectionChrome.Toolbar toolbar;
    private View normalBar, selectedBar, bulkBar;
    private ValueAnimator modeAnimator;
    private int frameScroll = -1, modeTargetY;
    private float selectionProgress;

    InboxScrollView(Context context) {
        super(context);
    }

    void bind(
            View header,
            View title,
            View carrier,
            View compact,
            View searchAction,
            View filterAction) {
        this.header = header;
        this.title = title;
        this.carrier = carrier;
        this.compact = compact;
        this.searchAction = searchAction;
        this.filterAction = filterAction;
        // MainActivity saves the offset with selection mode, across new view IDs.
        setSaveEnabled(false);
        updateChrome();
    }

    void bindSelection(
            SelectionChrome.Toolbar toolbar, View normalBar, View selectedBar, View bulkBar) {
        this.toolbar = toolbar;
        this.normalBar = normalBar;
        this.selectedBar = selectedBar;
        this.bulkBar = bulkBar;
        finishMode();
    }

    void selectionMode(boolean selected) {
        selectionMode(selected, false);
    }

    void selectionMode(boolean selected, boolean animate) {
        if (header == null) return;
        if (selected == selection) {
            if (modeAnimator != null && !AppMotion.enabled(getContext())) settleSelection();
            return;
        }
        int startHeader = header.getVisibility() == GONE ? 0 : header.getHeight();
        int startBulk =
                bulkBar == null || bulkBar.getVisibility() == GONE ? 0 : bulkBar.getHeight();
        int startY = getScrollY();
        jumpTo(startY);
        int fullHeader = naturalHeight(header);
        int fullBulk = bulkBar == null ? 0 : naturalHeight(bulkBar);
        if (selected && selectionProgress == 0) expandOnExit = startY < fullHeader;
        int listOffset = Math.max(0, startY - startHeader);
        int targetY =
                selected
                        ? listOffset
                        : expandOnExit && startY <= startHeader ? 0 : listOffset + fullHeader;
        cancelMode();
        modeTargetY = targetY;
        selection = selected;
        if (header instanceof SelectionChrome.Header)
            ((SelectionChrome.Header) header).interactive(!selected);
        if (toolbar != null) toolbar.active(selected ? selectedBar : normalBar);
        if (normalBar != null) {
            normalBar.setImportantForAccessibility(
                    selected
                            ? IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                            : IMPORTANT_FOR_ACCESSIBILITY_AUTO);
            selectedBar.setImportantForAccessibility(
                    selected
                            ? IMPORTANT_FOR_ACCESSIBILITY_AUTO
                            : IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            ((ViewGroup) normalBar)
                    .setDescendantFocusability(
                            selected
                                    ? ViewGroup.FOCUS_BLOCK_DESCENDANTS
                                    : ViewGroup.FOCUS_AFTER_DESCENDANTS);
            ((ViewGroup) selectedBar)
                    .setDescendantFocusability(
                            selected
                                    ? ViewGroup.FOCUS_AFTER_DESCENDANTS
                                    : ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        }
        boolean motion =
                animate && toolbar != null && isLaidOut() && AppMotion.enabled(getContext());
        if (!motion) {
            selectionProgress = selected ? 1 : 0;
            finishMode();
            pendingOffset = targetY;
            addHeaderOnRestore = false;
            requestLayout();
            return;
        }
        float from = selectionProgress, to = selected ? 1 : 0;
        header.setVisibility(VISIBLE);
        bulkBar.setVisibility(VISIBLE);
        normalBar.setVisibility(VISIBLE);
        selectedBar.setVisibility(VISIBLE);
        ValueAnimator animator = ValueAnimator.ofFloat(0, 1);
        modeAnimator = animator;
        animator.setDuration(220);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(
                value -> {
                    float f = (Float) value.getAnimatedValue();
                    selectionProgress = from + (to - from) * f;
                    height(
                            header,
                            Math.round(
                                    startHeader + ((selected ? 0 : fullHeader) - startHeader) * f));
                    height(
                            bulkBar,
                            Math.round(startBulk + ((selected ? fullBulk : 0) - startBulk) * f));
                    header.setAlpha(1 - selectionProgress);
                    normalBar.setAlpha(1 - selectionProgress);
                    selectedBar.setAlpha(selectionProgress);
                    bulkBar.setAlpha(selectionProgress);
                    frameScroll = Math.round(startY + (targetY - startY) * f);
                    requestLayout();
                });
        animator.addListener(
                new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (modeAnimator != animation) return;
                        modeAnimator = null;
                        selectionProgress = to;
                        finishMode();
                        pendingOffset = targetY;
                        addHeaderOnRestore = false;
                        requestLayout();
                    }
                });
        animator.start();
    }

    private int naturalHeight(View view) {
        int width = Math.max(1, view.getWidth() > 0 ? view.getWidth() : getWidth());
        view.measure(
                MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        return view.getMeasuredHeight();
    }

    private void height(View view, int value) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params.height != value) {
            params.height = value;
            view.setLayoutParams(params);
        }
    }

    private void cancelMode() {
        ValueAnimator running = modeAnimator;
        modeAnimator = null;
        if (running != null) running.cancel();
        frameScroll = -1;
    }

    private void finishMode() {
        if (header == null) return;
        header.setVisibility(selection ? GONE : VISIBLE);
        header.setAlpha(1);
        height(header, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (toolbar == null) return;
        toolbar.active(selection ? selectedBar : normalBar);
        normalBar.setVisibility(selection ? INVISIBLE : VISIBLE);
        normalBar.setAlpha(selection ? 0 : 1);
        selectedBar.setVisibility(selection ? VISIBLE : INVISIBLE);
        selectedBar.setAlpha(selection ? 1 : 0);
        bulkBar.setVisibility(selection ? VISIBLE : GONE);
        bulkBar.setAlpha(1);
        height(bulkBar, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    void settleSelection() {
        if (modeAnimator == null) return;
        int target = modeTargetY;
        cancelMode();
        selectionProgress = selection ? 1 : 0;
        finishMode();
        pendingOffset = target;
        addHeaderOnRestore = false;
        requestLayout();
    }

    @Override
    protected void onDetachedFromWindow() {
        settleSelection();
        cancelMode();
        super.onDetachedFromWindow();
    }

    void restoreOffset(int offset, boolean expandOnExit) {
        pendingOffset = Math.max(0, offset);
        addHeaderOnRestore = false;
        this.expandOnExit = expandOnExit;
        requestLayout();
    }

    /** End native fling momentum before an explicit navigation/layout offset. */
    void jumpTo(int y) {
        super.fling(0);
        super.computeScroll();
        super.scrollTo(0, y);
    }

    int savedOffset() {
        return modeAnimator != null ? modeTargetY : pendingOffset >= 0 ? pendingOffset : getScrollY();
    }

    boolean expandOnExit() {
        return expandOnExit;
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (modeAnimator != null && frameScroll >= 0) super.scrollTo(0, frameScroll);
        if (pendingOffset >= 0 && header != null) {
            int target = pendingOffset + (addHeaderOnRestore ? header.getHeight() : 0);
            pendingOffset = -1;
            addHeaderOnRestore = false;
            jumpTo(target);
        }
        updateChrome();
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);
        updateChrome();
    }

    void updateChrome() {
        if (title == null) return;
        int range =
                Math.max(
                        (int) (48 * getResources().getDisplayMetrics().density), title.getBottom());
        float progress = Math.max(0, Math.min(1, getScrollY() / (float) range));
        if (!AppMotion.enabled(getContext())) progress = progress >= 1 ? 1 : 0;
        carrier.setAlpha(1 - progress);
        carrier.setVisibility(progress < 1 ? VISIBLE : INVISIBLE);
        carrier.setImportantForAccessibility(
                progress < .5f
                        ? IMPORTANT_FOR_ACCESSIBILITY_YES
                        : IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        for (View view : new View[] {compact, searchAction, filterAction}) {
            view.setAlpha(progress);
            view.setVisibility(progress > 0 ? VISIBLE : INVISIBLE);
            view.setEnabled(progress >= .5f);
            view.setImportantForAccessibility(
                    progress >= .5f
                            ? IMPORTANT_FOR_ACCESSIBILITY_YES
                            : IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        }
        // With Reduce motion the large title simply scrolls; there is no crossfade.
        title.setAlpha(AppMotion.enabled(getContext()) ? 1 - progress : 1);
    }
}
