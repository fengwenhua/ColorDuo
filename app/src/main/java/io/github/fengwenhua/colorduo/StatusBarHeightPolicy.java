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

    /** Content stays centred on the physical camera while its band grows inside the new bar. */
    static int[] padding(int nativeHeight, int top, int bottom, int holeTop, int holeBottom, int percent) {
        if(percent<=Prefs.DEF_STATUS_BAR_HEIGHT)return new int[]{top,bottom};
        int height=scale(nativeHeight,percent);
        int centreTwice=holeBottom>holeTop ? holeTop+holeBottom : height;
        centreTwice=Math.max(0,Math.min(height*2,centreTwice));
        int room=Math.max(0,Math.min(centreTwice,height*2-centreTwice));
        int content=Math.min(room,scale(Math.max(0,nativeHeight-top-bottom),percent));
        int newTop=(centreTwice-content)/2;
        return new int[]{newTop,height-newTop-content};
    }
}
