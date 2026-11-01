/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

import android.graphics.Canvas;
import android.graphics.RectF;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Method;
import javax.imageio.ImageIO;
import org.junit.Test;
import static org.junit.Assert.*;

/** Production Canvas pixels: small content must not inherit a clock-sized notch. */
public final class RingOpeningTest {
    static final int SCALE=6,SIZE=720;
    static TrioSettings settings(int part,int zone) {
        TrioSettings s=FreeRingTest.base();s.trackAlpha=0;
        s.setChargingShowsValue(false);
        s.setShowSim1(true);s.setSignalMode(Prefs.SIGNAL_IN_RING);
        s.ringZones=Prefs.decodeLayout(zone==0?part+"||7|":"||"+part+"|");
        return s;
    }
    static BufferedImage draw(TrioSettings s,int level,boolean charging) throws Exception {
        TrioAppearance a=TrioAppearance.of(s);
        TrioAppearance.Ring ring=a.ring(level,charging,3,"5GA",1);
        BufferedImage image=new BufferedImage(SIZE,SIZE,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=image.createGraphics();
        try {
            Canvas canvas=new Canvas(g);canvas.scale(SCALE,SCALE);
            Method method=TrioRenderer.class.getDeclaredMethod("drawFreeRing",Canvas.class,int.class,
                    boolean.class,int.class,int.class,int[].class,String.class,int.class,int.class,
                    TrioAppearance.class,TrioAppearance.Ring.class);
            method.setAccessible(true);method.invoke(null,canvas,level,false,3,4,new int[]{4,-1},"5GA",
                    0xffff0000,0xffffffff,a,ring);
        } finally { g.dispose(); }
        return image;
    }
    static boolean white(int rgb) {
        return (rgb>>>24)>180&&(rgb>>16&255)>225&&(rgb>>8&255)>225&&(rgb&255)>225;
    }
    static boolean red(int rgb) {
        return (rgb>>>24)>180&&(rgb>>16&255)>180&&(rgb>>8&255)<65&&(rgb&255)<65;
    }
    static float clearance(BufferedImage image,int zone) {
        int from=zone==0?0:Math.round(TrioGeometry.ZONE_CUT_BOTTOM*SCALE);
        int to=zone==0?Math.round(TrioGeometry.ZONE_CUT_TOP*SCALE):SIZE;
        int l=SIZE,t=SIZE,r=-1,b=-1;
        for(int y=from;y<to;y++)for(int x=0;x<SIZE;x++)if(white(image.getRGB(x,y))){
            l=Math.min(l,x);r=Math.max(r,x);t=Math.min(t,y);b=Math.max(b,y);
        }
        assertTrue("component must paint",r>=l);
        float closest=Float.POSITIVE_INFINITY;
        // Compare the actual red ring raster to the actual white component ink box.
        for(int y=from;y<to;y++)for(int x=0;x<SIZE;x++)if(red(image.getRGB(x,y))){
            float dx=Math.max(Math.max(l-x,x-r),0),dy=Math.max(Math.max(t-y,y-b),0);
            closest=Math.min(closest,(float)Math.hypot(dx,dy)/SCALE);
        }
        return closest;
    }
    @Test public void digitSizeAndWeightKeepCapsCloseWithoutTouching() throws Exception {
        for(int size:new int[]{16,24,44,100})for(int weight:new int[]{100,700,900})for(int stroke:new int[]{4,8,16}){
            TrioSettings s=settings(Prefs.PART_VALUE,0);s.valueSize=size;s.valueWeight=weight;s.ringStroke=stroke;
            float distance=clearance(draw(s,100,false),0);
            // The Android bounds are integer design units: rounding a corner plus raster
            // AA can add up to sqrt(2)+2/SCALE beyond the intended two-unit clearance.
            assertTrue("digits size="+size+" weight="+weight+" stroke="+stroke+" gap="+distance,distance>=1.0f&&distance<=3.8f);
        }
    }
    @Test public void oneTwoAndThreeDigitsUseTheirOwnMeasuredOpening() {
        TrioSettings s=settings(Prefs.PART_VALUE,0);s.valueSize=24;
        TrioAppearance a=TrioAppearance.of(s);
        float previous=0;
        for(int level:new int[]{9,64,100}) {
            TrioGeometry.RingArc arc=TrioRenderer.ringArc(a,a.ring(level,false,3,"5G",1));
            float width=(arc.gapEnd-arc.gapStart)*arc.sweep;
            assertTrue("more digit ink must widen the notch",width>previous);previous=width;
        }
    }
    @Test public void weightAndRepeatedLiveEditsChangeTheOpeningWithoutMovingBottomDots() {
        TrioSettings s=settings(Prefs.PART_VALUE,0);s.valueSize=22;s.valueWeight=100;
        TrioAppearance a=TrioAppearance.of(s);TrioGeometry.RingArc thin=TrioRenderer.ringArc(a,a.ring(64,false,3,"5G",1));
        s.valueWeight=900;a=TrioAppearance.of(s);TrioGeometry.RingArc bold=TrioRenderer.ringArc(a,a.ring(64,false,3,"5G",1));
        assertTrue(bold.gapEnd-bold.gapStart>thin.gapEnd-thin.gapStart);
        assertEquals(thin.sweep,bold.sweep,0);assertEquals(thin.start,bold.start,0);
        s.valueWeight=100;a=TrioAppearance.of(s);TrioGeometry.RingArc restored=TrioRenderer.ringArc(a,a.ring(64,false,3,"5G",1));
        assertEquals(thin.gapStart,restored.gapStart,.0001);assertEquals(thin.gapEnd,restored.gapEnd,.0001);
    }
    @Test public void boltScaleWidensOpeningAndCannotRetainTheSmallNotch() throws Exception {
        TrioSettings s=settings(Prefs.PART_BOLT,0);float previous=-1;
        for(int scale:new int[]{60,100,130,160}){
            s.boltScale=scale;TrioAppearance a=TrioAppearance.of(s);
            TrioAppearance.Ring ring=a.ring(100,true,3,"5G",1);
            TrioGeometry.RingArc arc=TrioRenderer.ringArc(a,ring);
            float width=(arc.gapEnd-arc.gapStart)*arc.sweep;
            assertTrue("bolt opening follows scale="+scale,width>previous);previous=width;
            RectF ink=new RectF();assertTrue(TrioRenderer.ringInkBounds(ink,a,ring,0));
            assertClearOutsideOpening(arc,ink,s.ringStroke,0);
            if(scale==100)assertTrue("default bolt must not keep the clock notch",width<TrioGeometry.GAP_WIDTH_DEG-15);
        }
    }
    static void assertClearOutsideOpening(TrioGeometry.RingArc arc,RectF ink,int stroke,int zone) {
        float half=zone==0?(arc.gapEnd-arc.gapStart)*arc.sweep*.5f:(360-arc.sweep)*.5f;
        for(float angle=half;angle<90;angle+=.25f)for(int side:new int[]{-1,1}){
            float x=TrioGeometry.B_CX+side*TrioGeometry.B_R*(float)Math.sin(Math.toRadians(angle));
            float y=TrioGeometry.B_CY+(zone==0?-1:1)*TrioGeometry.B_R*(float)Math.cos(Math.toRadians(angle));
            float dx=Math.max(Math.max(ink.left-x,x-ink.right),0),dy=Math.max(Math.max(ink.top-y,y-ink.bottom),0);
            assertTrue("round cap cannot overlap component",Math.hypot(dx,dy)>=stroke*.5+1.95);
        }
    }
    @Test public void bottomTextUsesSameClearanceAndEmptyBandsClose() throws Exception {
        TrioSettings s=settings(Prefs.PART_VALUE,2);s.valueSize=18;
        float gap=clearance(draw(s,100,false),2);assertTrue("bottom gap="+gap,gap>=1&&gap<=3.2);
        TrioAppearance a=TrioAppearance.of(s);TrioGeometry.RingArc arc=TrioRenderer.ringArc(a,a.ring(100,false,3,"5G",1));
        assertEquals(0,arc.gapStart,0);assertTrue(arc.sweep>TrioGeometry.B_SWEEP);
        s.ringZones=Prefs.decodeLayout(Prefs.EMPTY_LAYOUT);a=TrioAppearance.of(s);arc=TrioRenderer.ringArc(a,a.ring(100,false,3,"5G",1));
        assertEquals(360,arc.sweep,0);assertEquals(arc.gapStart,arc.gapEnd,0);
    }
    @Test public void hitTestingRecognizesTheNewlyExtendedRing() {
        TrioSettings s=settings(Prefs.PART_VALUE,0);s.valueSize=14;
        TrioAppearance a=TrioAppearance.of(s);TrioAppearance.Ring ring=a.ring(100,false,3,"5G",1);
        TrioGeometry.RingArc arc=TrioRenderer.ringArc(a,ring);
        float half=(arc.gapEnd-arc.gapStart)*arc.sweep*.5f;
        assertTrue(half<TrioGeometry.GAP_WIDTH_DEG*.5f-5);
        double theta=Math.toRadians((half+TrioGeometry.GAP_WIDTH_DEG*.5f)*.5f);
        assertTrue(TrioZones.batteryRingAt(new float[]{1,0,0},a,ring,
                TrioGeometry.B_CX+TrioGeometry.B_R*(float)Math.sin(theta),
                TrioGeometry.B_CY-TrioGeometry.B_R*(float)Math.cos(theta)));
        assertFalse(TrioZones.batteryRingAt(new float[]{1,0,0},a,ring,TrioGeometry.B_CX,TrioGeometry.B_CY-TrioGeometry.B_R));
    }
    @Test public void clockTypeAndWifiFallbackRemainDrawable() throws Exception {
        TrioState.sClockText="19:41";
        try {
            for(int part:new int[]{Prefs.PART_CLOCK,Prefs.PART_TYPE}){
                TrioSettings s=settings(part,0);s.typeSize=s.clockSize=28;
                float gap=clearance(draw(s,100,false),0);assertTrue("part="+part+" gap="+gap,gap>=1&&gap<=3.2);
            }
            TrioSettings s=settings(Prefs.PART_WIFI,0);TrioAppearance a=TrioAppearance.of(s);TrioAppearance.Ring r=a.ring(100,false,3,"5G",1);
            TrioGeometry.RingArc arc=TrioRenderer.ringArc(a,r);
            assertEquals(r.gapStart(),arc.gapStart,0.0001);assertEquals(r.gapEnd(),arc.gapEnd,0.0001);
        } finally { TrioState.sClockText=""; }
    }

    /** Optional review artifact; uses the same production draw path as these tests. */
    public static void main(String[] args) throws Exception {
        File folder=new File(args[0]);folder.mkdirs();TrioState.sClockText="19:41";
        for(int part:new int[]{Prefs.PART_VALUE,Prefs.PART_BOLT,Prefs.PART_CLOCK}){
            TrioSettings s=settings(part,0);s.valueSize=32;s.clockSize=32;s.trackAlpha=56;
            ImageIO.write(draw(s,64,part==Prefs.PART_BOLT),"png",new File(folder,part+".png"));
        }
    }
}
