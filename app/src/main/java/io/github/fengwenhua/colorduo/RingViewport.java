/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

/** Enlarge the allocated ring host, not a canvas that would overflow its old bounds. */
final class RingViewport {
    private RingViewport() {}
    static int[] target(int nativeWidth,int nativeHeight,int availableWidth,int availableHeight,int percent) {
        if(nativeWidth<=0||nativeHeight<=0||availableWidth<=0||availableHeight<=0)
            throw new IllegalArgumentException("bounds");
        int requested=(int)Math.round(nativeHeight*Math.max(100,Math.min(percent,Prefs.MAX_RING_GLYPH_SCALE))/100.0);
        int height=Math.max(1,Math.min(requested,Math.min(availableHeight,availableWidth)));
        // Native width can include OEM spacing. Keep it until the enlarged circle needs more.
        return new int[]{Math.min(availableWidth,Math.max(nativeWidth,height)),height};
    }
}
