/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

/** Window-local sizing; screen configuration may describe a different fold/display. */
public final class WindowSheetPolicy {
    private WindowSheetPolicy() {}
    public static float heightDp(int windowHeightPx, float density) {
        if (!Float.isFinite(density) || density <= 0) throw new IllegalArgumentException("density");
        return Math.max(1, windowHeightPx) / density;
    }
    public static boolean compact(int windowHeightPx, float density, float fontScale) {
        return heightDp(windowHeightPx, density) < 600 || fontScale > 1.3f;
    }
    public static float maxHeightDp(int windowHeightPx, float density) {
        return heightDp(windowHeightPx, density) * 0.72f;
    }
}
