/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

import android.view.View;
import android.widget.ImageView;
import java.lang.ref.WeakReference;

/** Lease the ordinary OEM charging icon only after the replacement battery has been drawn. */
final class ColorOsChargeIcon {
    private final WeakReference<ImageView> image;
    private int nativeVisibility;
    private boolean owned;

    ColorOsChargeIcon(ImageView image) {
        this.image = new WeakReference<>(image);
        nativeVisibility = image.getVisibility();
    }

    boolean isFor(ImageView view) { return image.get() == view; }
    boolean owned() { return owned; }
    int nativeVisibility() { return nativeVisibility; }

    /** Called after the exact OEM collector has applied its latest model. */
    void onNativeUpdate() {
        ImageView view = image.get();
        if (view == null) return;
        nativeVisibility = view.getVisibility();
        if (owned && view.getVisibility() != View.GONE) view.setVisibility(View.GONE);
    }

    void commit(boolean poweredFrameDrawn) {
        if (!poweredFrameDrawn) { restore(); return; }
        ImageView view = image.get();
        if (view == null) return;
        if (!owned) nativeVisibility = view.getVisibility();
        owned = true;
        if (view.getVisibility() != View.GONE) view.setVisibility(View.GONE);
    }

    void restore() {
        ImageView view = image.get();
        boolean restore = owned;
        owned = false;
        if (restore && view != null && view.getVisibility() == View.GONE
                && nativeVisibility != View.GONE) view.setVisibility(nativeVisibility);
    }
}
