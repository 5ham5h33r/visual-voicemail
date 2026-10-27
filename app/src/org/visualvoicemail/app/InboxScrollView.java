package org.visualvoicemail.app;

import android.content.Context;
import android.view.View;
import android.widget.ScrollView;

/** One native scroll surface with a stable header height during scrolling. */
final class InboxScrollView extends ScrollView {
    private View header, title, carrier, compact, searchAction, filterAction;
    private boolean selection, expandOnExit = true, addHeaderOnRestore;
    private int pendingOffset = -1;

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

    void selectionMode(boolean selected) {
        if (header == null || selected == selection) return;
        int y = getScrollY();
        if (selected) {
            expandOnExit = y < header.getHeight();
            pendingOffset = Math.max(0, y - header.getHeight());
            addHeaderOnRestore = false;
        } else {
            pendingOffset = y;
            addHeaderOnRestore = !expandOnExit || y > 0;
        }
        selection = selected;
        header.setVisibility(selected ? GONE : VISIBLE);
        requestLayout();
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

    boolean expandOnExit() {
        return expandOnExit;
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
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
