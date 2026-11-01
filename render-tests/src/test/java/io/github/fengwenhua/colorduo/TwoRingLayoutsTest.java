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

/** Both user layouts, using the same production paths as preview and SystemUI. */
public class TwoRingLayoutsTest {
    static TrioSettings settings(boolean networkAbove) {
        TrioSettings s=UpperValueLayoutTest.settings(true,56);
        s.ringValuePriority=false;s.ringStroke=10;s.wifiScale=132;s.typeSize=64;s.typeSuffixScale=69;
        s.ringZones=Prefs.decodeLayout(networkAbove?"5,1|3|7|":"4,3|5,1|7|");
        return s;
    }
    static BufferedImage render(TrioSettings s,int zone,int part,int fg) throws Exception {
        return render(s,zone,part,fg,"5GA");
    }
    static BufferedImage render(TrioSettings s,int zone,int part,int fg,String label) throws Exception {
        TrioAppearance a=TrioAppearance.of(s);TrioAppearance.Ring r=a.ring(100,false,3,label,1,true);
        if(zone>=0){Arrays.fill(r.parts,Prefs.PART_NONE);r.parts[zone]=part;}
        BufferedImage image=new BufferedImage(720,720,BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics=image.createGraphics();
        try {
            Canvas c=new Canvas(graphics);c.scale(6,6);
            Method draw=TrioRenderer.class.getDeclaredMethod("drawFreeRing",Canvas.class,int.class,boolean.class,
                    int.class,int.class,int[].class,String.class,int.class,int.class,TrioAppearance.class,TrioAppearance.Ring.class);
            draw.setAccessible(true);draw.invoke(null,c,100,false,3,4,new int[]{4,-1},label,0xffff0000,fg,a,r);
        }finally{graphics.dispose();}
        return image;
    }
    @Test public void networkAboveBatteryUsesRealTopSpaceAndFullGlyphContours() throws Exception {
        TrioSettings s=settings(true);s.typeSize=44;s.valueSize=45;
        TrioAppearance a=TrioAppearance.of(s);
        assertEquals("44 must not shrink back into the old 23-unit text strip",44,
                TrioRenderer.fittedRingTextSize(a,0,Prefs.PART_TYPE,"5G",44),.05f);
        float numeric=TrioRenderer.fittedRingTextSize(a,1,Prefs.PART_VALUE,"100",56);
        assertTrue("actual glyphs allow a larger reading than the old rectangle chord",numeric>44);
        RectF wifi=UpperValueLayoutTest.whiteBounds(render(s,0,Prefs.PART_WIFI,0xffffffff));
        RectF battery=UpperValueLayoutTest.whiteBounds(render(s,1,Prefs.PART_VALUE,0xffffffff));
        assertTrue(wifi.top>=TrioGeometry.INK_Y-.2f);assertTrue(battery.top-wifi.bottom>=1f);
    }
    @Test public void batteryAboveNetworkMakesTheTypeGenuinelyLarger() throws Exception {
        TrioSettings s=settings(false);s.ringValuePriority=true;
        s.typeSize=44;TrioAppearance before=TrioAppearance.of(s);
        float legacyEmCeiling=44*TrioRenderer.ringContent(before).middleScale;
        RectF old=UpperValueLayoutTest.whiteBounds(render(s,1,Prefs.PART_TYPE,0xffffffff,"5G"));
        s.typeSize=64;RectF larger=UpperValueLayoutTest.whiteBounds(render(s,1,Prefs.PART_TYPE,0xffffffff,"5G"));
        assertTrue("64 should paint taller rather than be cancelled by a rectangular fit",larger.height()>old.height()+2f);
        assertTrue("even the longer 5GA label exceeds the old shared Wi-Fi scale ceiling",
                TrioRenderer.fittedRingTextSize(TrioAppearance.of(s),1,Prefs.PART_TYPE,"5GA",64)>legacyEmCeiling+2f);
    }
    @Test public void bothLayoutsKeepAllPaintedComponentsSeparateAndInsideTheirHitRegions() throws Exception {
        for(boolean networkAbove:new boolean[]{true,false})for(boolean priority:new boolean[]{false,true}) {
            TrioSettings s=settings(networkAbove);s.ringValuePriority=priority;
            TrioAppearance a=TrioAppearance.of(s);
            int top=networkAbove?Prefs.PART_WIFI:Prefs.PART_VALUE;
            int middle=networkAbove?Prefs.PART_VALUE:Prefs.PART_WIFI;
            BufferedImage topImage=render(s,0,top,0xffffffff),middleImage=render(s,1,middle,0xffffffff),bottomImage=render(s,2,Prefs.PART_SIM_1,0xffffffff);
            RectF topInk=UpperValueLayoutTest.whiteBounds(topImage),midInk=UpperValueLayoutTest.whiteBounds(middleImage),bottom=UpperValueLayoutTest.whiteBounds(bottomImage);
            assertTrue("top/middle gap",midInk.top-topInk.bottom>=.8f);
            assertTrue("middle/bottom gap",bottom.top-midInk.bottom>=.8f);
            UpperValueLayoutTest.assertInkHits(topImage,a,TrioZones.Zone.TOP);
            UpperValueLayoutTest.assertInkHits(middleImage,a,TrioZones.Zone.CENTRE);
            UpperValueLayoutTest.assertInkHits(bottomImage,a,TrioZones.Zone.BOTTOM);
        }
    }
    @Test public void enlargedInteriorTextCannotPaintAcrossTheBatteryArc() throws Exception {
        for(boolean networkAbove:new boolean[]{true,false}) {
            TrioSettings s=settings(networkAbove);s.ringValuePriority=true;
            int part=networkAbove?Prefs.PART_VALUE:Prefs.PART_TYPE;
            BufferedImage ink=render(s,1,part,0xffffffff),arc=render(s,1,part,0);
            for(int y=0;y<720;y++)for(int x=0;x<720;x++)
                assertFalse("interior ink must clear the ring",RingOpeningTest.white(ink.getRGB(x,y))&&RingOpeningTest.red(arc.getRGB(x,y)));
        }
    }
    @Test public void enlargingAndRestoringEachLayoutDoesNotReuseTheOtherLayoutsPlan() {
        TrioSettings s=settings(true);TrioAppearance a=TrioAppearance.of(s);
        float initial=TrioRenderer.ringContent(a).middleCy;
        s.ringZones=settings(false).ringZones;s.ringValuePriority=true;
        assertTrue(TrioRenderer.ringContent(TrioAppearance.of(s)).middleCy>initial);
        s.ringZones=settings(true).ringZones;s.ringValuePriority=false;
        assertEquals(initial,TrioRenderer.ringContent(TrioAppearance.of(s)).middleCy,0);
    }
    public static void main(String[] args) throws Exception {
        File folder=new File(args[0]);folder.mkdirs();
        for(boolean above:new boolean[]{false,true}) {
            TrioSettings s=settings(above);s.ringValuePriority=true;
            float size=TrioRenderer.fittedRingTextSize(TrioAppearance.of(s),above?1:0,Prefs.PART_VALUE,"100",56);
            System.out.println((above?"network-above":"battery-above")+" numeric fit="+size);
            ImageIO.write(render(s,-1,0,0xffffffff),"png",new File(folder,(above?"network-above":"battery-above")+".png"));
        }
    }
}
