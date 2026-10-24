/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

import android.view.View;
import java.lang.ref.WeakReference;

/** The inactive native overflow dot still measures one icon wide inside an empty FrameLayout. */
final class ColorOsMobileGap {
    private final WeakReference<View> dot;
    private boolean owned;
    private int nativeVisibility;

    ColorOsMobileGap(View dot) { this.dot = new WeakReference<>(dot); }

    private boolean wants(int nativeState, boolean emptyGroup) {
        View view = dot.get();
        return view != null && nativeState == 0 && emptyGroup
                && (view.getVisibility() == View.VISIBLE || view.getVisibility() == View.INVISIBLE
                    || (owned && view.getVisibility() == View.GONE));
    }

    boolean needsChange(int nativeState, boolean emptyGroup) {
        View view = dot.get();
        if (!wants(nativeState, emptyGroup)) return owned;
        return !owned || view.getVisibility() != View.GONE;
    }

    void sync(int nativeState, boolean emptyGroup) {
        View view = dot.get();
        if (!wants(nativeState, emptyGroup)) { restore(); return; }
        if (!owned) { nativeVisibility = view.getVisibility(); owned = true; }
        if (view.getVisibility() != View.GONE) view.setVisibility(View.GONE);
    }

    void restore() {
        View view = dot.get();
        if (owned && view != null && view.getVisibility() == View.GONE) view.setVisibility(nativeVisibility);
        owned = false;
    }
}
