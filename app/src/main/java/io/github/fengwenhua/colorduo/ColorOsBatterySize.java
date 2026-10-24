/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import java.lang.ref.WeakReference;

/** Own only width/height, restoring the latest OEM values before each native style update. */
final class ColorOsBatterySize {
    private final WeakReference<ImageView> image;
    private boolean owned;
    private int nativeWidth, nativeHeight, appliedWidth, appliedHeight;

    ColorOsBatterySize(ImageView image) { this.image = new WeakReference<>(image); }

    boolean resizeNeeded(View meter, TrioSettings settings) {
        return resizeNeeded(meter, settings, 0);
    }

    int[] baseSize(View meter, TrioSettings settings) {
        ImageView view = image.get();
        ViewGroup.LayoutParams params = view == null ? null : view.getLayoutParams();
        if (params == null) return new int[]{0, 0};
        int width = owned ? nativeWidth : params.width, height = owned ? nativeHeight : params.height;
        if (width <= 0 || height <= 0 || meter.getHeight() <= 0) return new int[]{0, 0};
        return settings.trioStyle == Prefs.STYLE_CAPSULE
                ? target(height, availableHeight(meter, params), meter.getResources().getDisplayMetrics().widthPixels / 3, settings.capsuleLength)
                : new int[]{width, height};
    }

    boolean resizeNeeded(View meter, TrioSettings settings, int extraWidth) {
        ImageView view = image.get();
        if (view == null) return false;
        if (settings.trioStyle != Prefs.STYLE_CAPSULE && extraWidth == 0) return owned;
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params == null) return false;
        if (owned && (params.width != appliedWidth || params.height != appliedHeight))
            throw new IllegalStateException("battery size changed by another owner");
        int[] target = baseSize(meter, settings);
        if (target[1] <= 0) return false;
        target[0] += extraWidth;
        return params.width != target[0] || params.height != target[1];
    }

    void apply(View meter, TrioSettings settings) {
        apply(meter, settings, 0);
    }

    void apply(View meter, TrioSettings settings, int extraWidth) {
        if (settings.trioStyle != Prefs.STYLE_CAPSULE && extraWidth == 0) { restore(); return; }
        ImageView view = image.get();
        if (view == null) return;
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params == null || params.height <= 0 || meter.getHeight() <= 0) return;
        int[] target = baseSize(meter, settings);
        if (!owned) { nativeWidth = params.width; nativeHeight = params.height; }
        target[0] += extraWidth;
        appliedWidth = target[0]; appliedHeight = target[1]; owned = true;
        if (params.width != appliedWidth || params.height != appliedHeight) {
            params.width = appliedWidth; params.height = appliedHeight; view.setLayoutParams(params);
        }
    }

    void restore() {
        if (!owned) return;
        ImageView view = image.get();
        ViewGroup.LayoutParams params = view == null ? null : view.getLayoutParams();
        if (params != null) {
            boolean changed = false;
            if (params.width == appliedWidth) { params.width = nativeWidth; changed = true; }
            if (params.height == appliedHeight) { params.height = nativeHeight; changed = true; }
            if (changed) view.setLayoutParams(params);
        }
        owned = false;
    }

    private static int availableHeight(View meter, ViewGroup.LayoutParams params) {
        int height = meter.getHeight() - meter.getPaddingTop() - meter.getPaddingBottom();
        if (params instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) params;
            height -= margins.topMargin + margins.bottomMargin;
        }
        return height;
    }

    static int[] target(int baseHeight, int availableHeight, int availableWidth, int length) {
        if (baseHeight <= 0 || availableHeight <= 0 || availableWidth <= 0) throw new IllegalArgumentException("bounds");
        TrioGeometry.setCapsuleLength(length);
        int body = baseHeight;
        int width = TrioGeometry.capHostWidthPx(body);
        int height = TrioGeometry.capHostHeightPx(body);
        float fit = Math.min(1f, Math.min(availableWidth / (float) width, availableHeight / (float) height));
        body = Math.max(1, (int) Math.floor(body * fit));
        return new int[]{Math.max(1, TrioGeometry.capHostWidthPx(body)),
                Math.max(1, TrioGeometry.capHostHeightPx(body))};
    }
}
