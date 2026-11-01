/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

import android.view.View;
import java.lang.ref.WeakReference;

/** Lease the OEM outside percentage only while a proven module layout owns the value. */
final class ColorOsPercentIcon {
    private final WeakReference<View> view;
    private int nativeVisibility;
    private boolean owned;

    ColorOsPercentIcon(View view) {
        this.view = new WeakReference<>(view);
        nativeVisibility = view.getVisibility();
    }

    boolean isFor(View candidate) { return view.get() == candidate; }
    boolean owned() { return owned; }
    int nativeVisibility() { return nativeVisibility; }

    /** The exact OEM percent collector has just applied a non-null model. */
    void onNativeUpdate() {
        View current = view.get();
        if (current == null) return;
        nativeVisibility = current.getVisibility();
        if (owned && current.getVisibility() != View.GONE) current.setVisibility(View.GONE);
    }

    void commit(boolean ownsValue) {
        if (!ownsValue) { restore(); return; }
        View current = view.get();
        if (current == null) return;
        if (!owned) nativeVisibility = current.getVisibility();
        owned = true;
        if (current.getVisibility() != View.GONE) current.setVisibility(View.GONE);
    }

    void restore() {
        View current = view.get();
        boolean restore = owned;
        owned = false;
        if (restore && current != null && current.getVisibility() == View.GONE
                && nativeVisibility != View.GONE) current.setVisibility(nativeVisibility);
    }
}
