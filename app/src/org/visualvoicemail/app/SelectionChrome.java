package org.visualvoicemail.app;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

/** Keep outgoing chrome visible during a fade without allowing its old controls to take input. */
final class SelectionChrome {
    static final class Toolbar extends FrameLayout {
        private View active;

        Toolbar(Context context) {
            super(context);
        }

        void active(View view) {
            active = view;
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent event) {
            if (active == null) return super.dispatchTouchEvent(event);
            MotionEvent local = MotionEvent.obtain(event);
            local.offsetLocation(-active.getLeft(), -active.getTop());
            boolean handled = active.dispatchTouchEvent(local);
            local.recycle();
            return handled;
        }
    }

    static final class Header extends LinearLayout {
        Header(Context context) {
            super(context);
            setOrientation(VERTICAL);
        }

        void interactive(boolean enabled) {
            setEnabled(enabled);
            setDescendantFocusability(enabled ? FOCUS_AFTER_DESCENDANTS : FOCUS_BLOCK_DESCENDANTS);
            setImportantForAccessibility(
                    enabled
                            ? IMPORTANT_FOR_ACCESSIBILITY_AUTO
                            : IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent event) {
            return !isEnabled() || super.dispatchTouchEvent(event);
        }
    }
}
