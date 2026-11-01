/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

import android.graphics.Canvas;
import android.graphics.RectF;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Method;
import java.util.Arrays;
import javax.imageio.ImageIO;
import org.junit.Test;
import static org.junit.Assert.*;

/** Raster and hit-testing of the actual production ring, including oversized Wi-Fi. */
public class UpperValueLayoutTest {
    private static final int SCALE=6, SIZE=720;
    static TrioSettings settings(boolean priority,int size) {
        TrioSettings s=RingOpeningTest.settings(Prefs.PART_VALUE,0);
        s.ringZones=Prefs.decodeLayout("4,3|5,1|7|");
        s.ringValuePriority=priority;s.valueSize=size;s.valueWeight=900;
        s.wifiScale=134;s.arcStroke=8;
        s.setTypeHideOnWifi(true);
        return s;
    }
    static BufferedImage render(TrioSettings s,int onlyZone,int selected) throws Exception {
        TrioAppearance a=TrioAppearance.of(s);
        TrioAppearance.Ring r=a.ring(100,false,3,"5GA",1,true);
        if(onlyZone>=0){Arrays.fill(r.parts,Prefs.PART_NONE);r.parts[onlyZone]=selected;}
        BufferedImage image=new BufferedImage(SIZE,SIZE,BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics=image.createGraphics();
        try {
            Canvas c=new Canvas(graphics);c.scale(SCALE,SCALE);
            Method draw=TrioRenderer.class.getDeclaredMethod("drawFreeRing",Canvas.class,int.class,boolean.class,
                    int.class,int.class,int[].class,String.class,int.class,int.class,TrioAppearance.class,TrioAppearance.Ring.class);
            draw.setAccessible(true);draw.invoke(null,c,100,false,3,4,new int[]{4,-1},"5GA",0xffff0000,0xffffffff,a,r);
        } finally {graphics.dispose();}
        return image;
    }
    static RectF whiteBounds(BufferedImage image) {
        int left=SIZE,top=SIZE,right=-1,bottom=-1;
        for(int y=0;y<SIZE;y++)for(int x=0;x<SIZE;x++)if(RingOpeningTest.white(image.getRGB(x,y))){
            left=Math.min(left,x);top=Math.min(top,y);right=Math.max(right,x);bottom=Math.max(bottom,y);
        }
        assertTrue("component must remain visible",right>=left);
        return new RectF(left/(float)SCALE,top/(float)SCALE,(right+1f)/SCALE,(bottom+1f)/SCALE);
    }

    @Test public void keepModeUsesMoreThanTheOldDotBoxWithoutMovingTheMiddle() {
        TrioSettings s=settings(false,56);TrioAppearance a=TrioAppearance.of(s);
        int max=TrioTextControls.maxValueSize(a);
        assertTrue("top has more room than the old ceiling of 30",max>30);
        assertTrue("keep mode must still respect actual neighbours",max<56);
        TrioRenderer.RingContent layout=TrioRenderer.ringContent(a);
        assertEquals(64,layout.middleCy,0);assertEquals(1,layout.middleScale,0);
        assertFalse(TrioSettings.defaults().ringValuePriority);
    }

    @Test public void yieldingMakesTheWholeRangeEffectiveWithoutShrinkingTheNumericEm() throws Exception {
        float previous=0;
        for(int size:new int[]{30,36,44,56}) {
            TrioSettings s=settings(true,size);TrioAppearance a=TrioAppearance.of(s);
            assertEquals(56,TrioTextControls.maxValueSize(a));
            assertEquals(size,TrioRenderer.fittedRingTextSize(a,0,Prefs.PART_VALUE,"100",size),.05f);
            RectF digits=whiteBounds(render(s,0,Prefs.PART_VALUE));
            assertTrue(digits.height()>previous);previous=digits.height();
            assertTrue("top ink stays inside the existing host",digits.top>=TrioGeometry.INK_Y-.2f);
            assertTrue(digits.left>=TrioGeometry.INK_X-.2f);
            assertTrue(digits.right<=TrioGeometry.INK_X+TrioGeometry.INK_W+.2f);
            float[] guide=TrioZones.highlightBoxes(TrioZones.Zone.TOP,a)[0];
            assertTrue("guide covers the enlarged numeric ink",digits.left>=guide[0]-.3f&&digits.right<=guide[2]+.3f);
        }
    }

    @Test public void wifiTypeAndBottomDotsStaySeparateAtMaximumSizeAndUseTheirOwnHitRegions() throws Exception {
        for(int wifiScale:new int[]{100,134,160})for(int stroke:new int[]{4,8,16}) {
            TrioSettings s=settings(true,56);s.wifiScale=wifiScale;s.ringStroke=stroke;
            TrioAppearance a=TrioAppearance.of(s);
            RectF top=whiteBounds(render(s,0,Prefs.PART_VALUE));
            RectF bottom=whiteBounds(render(s,2,Prefs.PART_SIM_1));
            for(int part:new int[]{Prefs.PART_WIFI,Prefs.PART_TYPE}) {
                BufferedImage middleImage=render(s,1,part);RectF middle=whiteBounds(middleImage);
                assertTrue("middle clears enlarged digits",middle.top-top.bottom>=1f);
                assertTrue("middle clears bottom dots",bottom.top-middle.bottom>=1f);
                assertInkHits(middleImage,a,TrioZones.Zone.CENTRE);
            }
            assertInkHits(render(s,0,Prefs.PART_VALUE),a,TrioZones.Zone.TOP);
            assertInkHits(render(s,2,Prefs.PART_SIM_1),a,TrioZones.Zone.BOTTOM);
            RectF ink=new RectF();TrioAppearance.Ring r=a.ring(100,false,3,"5GA",1,true);
            assertTrue(TrioRenderer.ringInkBounds(ink,a,r,0));
            RingOpeningTest.assertClearOutsideOpening(TrioRenderer.ringArc(a,r),ink,stroke,0);
        }
    }
    static void assertInkHits(BufferedImage image,TrioAppearance a,TrioZones.Zone expected) {
        float[] transform={1,0,0};
        for(int y=0;y<SIZE;y+=3)for(int x=0;x<SIZE;x+=3)if(RingOpeningTest.white(image.getRGB(x,y)))
            assertEquals("painted ink must select its component",expected,
                    TrioZones.zoneAt(transform,a,(x+.5f)/SCALE,(y+.5f)/SCALE));
    }

    @Test public void policyAndNetworkFallbackEditsRestoreTheExactPreviousGeometry() {
        TrioSettings s=settings(false,56);TrioAppearance a=TrioAppearance.of(s);
        TrioRenderer.RingContent keep=TrioRenderer.ringContent(a);
        s.ringValuePriority=true;a=TrioAppearance.of(s);
        TrioRenderer.RingContent yield=TrioRenderer.ringContent(a);
        assertTrue(yield.middleCy>keep.middleCy);assertTrue(yield.middleScale<1);
        for(boolean wifi:new boolean[]{false,true,false}) {
            a.ring(100,false,wifi?3:-1,"5G",1,wifi);
            a.ring(100,true,wifi?3:-1,"5G",1,wifi);
            assertSame("network/charging changes must not cause layout jumps",yield,TrioRenderer.ringContent(a));
        }
        s.ringValuePriority=false;a=TrioAppearance.of(s);
        TrioRenderer.RingContent restored=TrioRenderer.ringContent(a);
        assertEquals(keep.middleCy,restored.middleCy,0);assertEquals(keep.middleScale,restored.middleScale,0);
        assertEquals(keep.valueCy,restored.valueCy,0);
    }

    @Test public void policySurvivesCopiesBundlesPacketsAndSingleKeyUpdates() throws Exception {
        TrioSettings s=settings(true,56);
        assertTrue(s.copy().ringValuePriority);
        assertTrue(TrioSettings.fromBundle(s.toBundle()).ringValuePriority);
        assertTrue(ConfigPacket.decode(ConfigPacket.encode(java.util.UUID.randomUUID().toString(),1,s)).settings.ringValuePriority);
        TrioSettings target=TrioSettings.defaults();
        assertTrue(target.applyKeyFrom(s,Prefs.KEY_RING_VALUE_PRIORITY));assertTrue(target.ringValuePriority);
        assertFalse(s.equals(settings(false,56)));
    }

    public static void main(String[] args) throws Exception {
        File dir=new File(args[0]);dir.mkdirs();
        for(boolean priority:new boolean[]{false,true})for(int size:new int[]{30,44,56})
            ImageIO.write(render(settings(priority,size),-1,0),"png",new File(dir,(priority?"yield":"keep")+"-"+size+".png"));
    }
}
