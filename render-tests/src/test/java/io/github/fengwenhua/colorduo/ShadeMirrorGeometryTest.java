/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

import android.graphics.Canvas;
import android.graphics.Paint;
import java.awt.image.BufferedImage;
import org.junit.Test;
import static org.junit.Assert.*;

/** Reproduce native mirror draw (which skips the source View matrix), then inspect actual ink. */
public final class ShadeMirrorGeometryTest {
    private static int[] ink(float scale,int kind,boolean rtl,boolean corrected) {
        int sourceWidth=148,offset=30;
        int width=kind==ShadeMirrorGeometry.CLOCK?ShadeMirrorGeometry.pixels(sourceWidth,scale):440;
        int height=ShadeMirrorGeometry.pixels(81,scale);
        BufferedImage image=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D graphics=image.createGraphics();
        try {
            Canvas canvas=new Canvas(graphics);Paint paint=new Paint();paint.setColor(0xffffffff);
            int save=corrected?ShadeMirrorGeometry.save(canvas,kind,rtl,width,height,sourceWidth,81,offset,scale):canvas.save();
            // SystemIconFakeFrame and NotificationFakeFrame's native translations.
            float x=kind==ShadeMirrorGeometry.SYSTEM?(rtl?0:width-sourceWidth)
                    :kind==ShadeMirrorGeometry.NOTIFICATIONS?(rtl?width-sourceWidth-offset:offset):0;
            canvas.translate(x,0);
            android.graphics.RectF box=new android.graphics.RectF();box.set(0,0,sourceWidth,81);
            canvas.drawRoundRect(box,0,0,paint);
            canvas.restoreToCount(save);
        } finally { graphics.dispose(); }
        int left=width,right=-1,top=height,bottom=-1;
        for(int y=0;y<height;y++)for(int x=0;x<width;x++)if((image.getRGB(x,y)>>>24)!=0) {
            left=Math.min(left,x);right=Math.max(right,x);top=Math.min(top,y);bottom=Math.max(bottom,y);
        }
        return new int[]{left,top,right+1,bottom+1};
    }
    @Test public void nativeMirrorReproducesSizeDropAndCorrectionMatchesCustomInk() {
        for(float scale:new float[]{1.05f,1.2f,1.5f}) {
            int[] nativeInk=ink(scale,ShadeMirrorGeometry.CLOCK,false,false);
            int[] custom=ink(scale,ShadeMirrorGeometry.CLOCK,false,true);
            assertEquals(148,nativeInk[2]-nativeInk[0]);
            assertEquals(148*scale,custom[2]-custom[0],1);
            assertEquals(81*scale,custom[3]-custom[1],1);
            assertTrue(custom[2]>nativeInk[2]);
        }
    }
    @Test public void bothDirectionsRetainSystemAndNotificationPhysicalEdges() {
        for(float scale:new float[]{1f,1.2f,1.5f})for(boolean rtl:new boolean[]{false,true})
            for(int kind:new int[]{ShadeMirrorGeometry.SYSTEM,ShadeMirrorGeometry.NOTIFICATIONS}) {
                int[] box=ink(scale,kind,rtl,true);
                int edge=kind==ShadeMirrorGeometry.NOTIFICATIONS?30:0;
                if(rtl==(kind==ShadeMirrorGeometry.SYSTEM))assertEquals(edge,box[0],1);
                else assertEquals(440-edge,box[2],1);
                assertEquals(148*scale,box[2]-box[0],1);
            }
    }
    @Test public void clockHandoffIncludesCustomStartAndNativeDestinationWithoutOvershoot() {
        for(float scale:new float[]{1.05f,1.2f,1.5f})for(int step=0;step<=100;step++) {
            float progress=step/100f,nativeScale=1+.6f*progress;
            float correction=ShadeMirrorGeometry.clockScale(scale,progress,nativeScale);
            assertEquals(scale+(1.6f-scale)*progress,nativeScale*correction,.00001f);
        }
        assertEquals(1,ShadeMirrorGeometry.clockScale(1.5f,1,1.6f),0);
    }
    @Test public void restoringDefaultLeavesNativeMirrorDimensionsAndOffsetsExact() {
        for(int kind:new int[]{0,1,2})for(boolean rtl:new boolean[]{false,true})
            assertArrayEquals(ink(1,kind,rtl,false),ink(1,kind,rtl,true));
        assertEquals(0,ShadeMirrorGeometry.top(81,81,1),0);
        assertEquals(81,ShadeMirrorGeometry.pixels(81,1));
    }
}
