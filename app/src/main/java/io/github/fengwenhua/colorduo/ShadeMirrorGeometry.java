/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

import android.graphics.Canvas;

/** Geometry omitted when ColorOS calls realView.draw() outside its scaled ancestors. */
final class ShadeMirrorGeometry {
    static final int CLOCK=0, NOTIFICATIONS=1, SYSTEM=2;
    static int pixels(int nativePixels,float scale) { return Math.max(0,Math.round(nativePixels*scale)); }
    static float clockScale(float scale,float progress,float nativeScale) {
        // The expanded panel's large clock remains the native animation destination.
        if(nativeScale<=0)return 1;
        return (nativeScale+(scale-1)*(1-Math.max(0f,Math.min(1f,progress))))/nativeScale;
    }
    static float anchor(int kind,boolean rtl,int width,int offset) {
        if(kind==NOTIFICATIONS)return rtl?width-offset:offset;
        return kind==SYSTEM&&!rtl?width:0;
    }
    static float top(int frameHeight,int sourceHeight,float scale) {
        return (frameHeight-sourceHeight*scale)/2f;
    }
    static int save(Canvas canvas,int kind,boolean rtl,int frameWidth,int frameHeight,int sourceWidth,int sourceHeight,int offset,float scale) {
        int saved=canvas.save();float x=anchor(kind,rtl,frameWidth,offset);
        float extra=kind==CLOCK&&rtl?frameWidth-sourceWidth*scale:0;
        canvas.translate(x+extra,top(frameHeight,sourceHeight,scale));
        canvas.scale(scale,scale);canvas.translate(-x,0);
        return saved;
    }
}
