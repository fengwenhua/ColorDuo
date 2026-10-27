/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

/** Scale native dimensions, preserving the ROM's per-display cutout minimum. */
final class StatusBarHeightPolicy {
    static int percent(TrioSettings settings) {
        return settings != null && settings.enabled
                ? Math.max(Prefs.MIN_STATUS_BAR_HEIGHT, Math.min(Prefs.MAX_STATUS_BAR_HEIGHT, settings.statusBarHeightPercent))
                : Prefs.DEF_STATUS_BAR_HEIGHT;
    }
    static int scale(int nativePixels, int percent) {
        if (nativePixels <= 0) return nativePixels;
        int bounded=Math.max(Prefs.MIN_STATUS_BAR_HEIGHT, Math.min(Prefs.MAX_STATUS_BAR_HEIGHT, percent));
        return (int)Math.min(Integer.MAX_VALUE, ((long)nativePixels * bounded + 50L) / 100L);
    }
}
