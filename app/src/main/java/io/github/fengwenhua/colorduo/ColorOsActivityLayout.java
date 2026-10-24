/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

import android.view.View;
import android.widget.ImageView;
import java.lang.ref.WeakReference;

/** Hide ordinary OEM data arrows with a represented signal, restoring the latest native state. */
final class ColorOsActivityLayout {
    private final WeakReference<ImageView> image;
    private boolean owned;
    private int nativeVisibility;

    ColorOsActivityLayout(ImageView image) { this.image = new WeakReference<>(image); }
    boolean owned() { return owned; }
    boolean isFor(ImageView view) { return image.get() == view; }

    static boolean supported(String name) {
        return "stat_signal_activity_default_public".equals(name) || "stat_signal_activity_in_public".equals(name)
                || "stat_signal_activity_out_public".equals(name) || "stat_signal_activity_inout_public".equals(name);
    }

    void sync(boolean signalOwned, String resource) {
        ImageView view = image.get();
        if (!signalOwned || !supported(resource) || view == null) { restore(); return; }
        if (!owned) {
            if (view.getVisibility() != View.VISIBLE) return;
            nativeVisibility = view.getVisibility(); owned = true;
        } else if (view.getVisibility() != View.GONE) {
            nativeVisibility = view.getVisibility();
        }
        if (view.getVisibility() != View.GONE) view.setVisibility(View.GONE);
    }

    /** The exact OEM collector has already updated both resource and visibility. */
    void onNativeUpdate(String resource) {
        ImageView view = image.get();
        if (!owned || view == null) return;
        nativeVisibility = view.getVisibility();
        if (!supported(resource)) { restore(); return; }
        if (view.getVisibility() != View.GONE) view.setVisibility(View.GONE);
    }

    void restore() {
        ImageView view = image.get();
        if (!owned || view == null) { owned = false; return; }
        if (view.getVisibility() == View.GONE && nativeVisibility != View.GONE) view.setVisibility(nativeVisibility);
        owned = false;
    }
}
