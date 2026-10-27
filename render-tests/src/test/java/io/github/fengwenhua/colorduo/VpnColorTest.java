/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

import android.graphics.Canvas;
import android.graphics.Paint;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public final class VpnColorTest {
    private static final int FG = 0xffeeeeee;
    private static final class Frame {
        final BufferedImage image = new BufferedImage(320, 180, BufferedImage.TYPE_INT_ARGB);
        final Map<String, Integer> text = new HashMap<>();
    }
    private Frame draw(int style, boolean vpn, int color, boolean wifi) {
        TrioSettings s = TrioSettings.defaults(); s.trioStyle = style; s.roleColors = false;
        s.vpnColor = color; s.setShowWifi(true); s.setShowMobile(true); s.setShowSim1(true);
        s.setMobileTypeMode(Prefs.MOBILE_TYPE_IN_RING); s.setTypeHideOnWifi(false);
        s.ringZones = Prefs.decodeLayout(wifi ? "1|3|7|6" : "5|3|7|6");
        TrioState.sClockText = "19:41";
        Frame frame = new Frame(); var g = frame.image.createGraphics();
        Canvas canvas = new Canvas(g) {
            @Override public void drawText(String text, float x, float y, Paint paint) {
                frame.text.put(text, paint.getColor()); super.drawText(text, x, y, paint);
            }
        };
        try {
            TrioRenderer.drawInto(canvas, 320, 180, 79, false, false, false, false,
                    wifi ? 3 : -1, 4, new int[]{4, -1}, "5G", FG, s, false, wifi, vpn);
        } finally { g.dispose(); }
        return frame;
    }
    @Test public void wifiAndMobileTintInBothGlyphsWithoutChangingGeometry() {
        for (int style : new int[]{Prefs.STYLE_RING, Prefs.STYLE_CAPSULE})
            for (boolean wifi : new boolean[]{false, true}) {
                Frame off = draw(style, false, Prefs.DEF_VPN_COLOR, wifi);
                Frame on = draw(style, true, Prefs.DEF_VPN_COLOR, wifi);
                int changed = 0, blue = 0;
                for (int y=0;y<180;y++) for (int x=0;x<320;x++) {
                    int a=off.image.getRGB(x,y), b=on.image.getRGB(x,y);
                    assertEquals("VPN must not change ink geometry", a>>>24,b>>>24);
                    if(a!=b)changed++;
                    if(b==Prefs.DEF_VPN_COLOR)blue++;
                }
                assertTrue("network ink must change",changed>0);
                assertTrue("network must use blue",blue>0);
                if(on.text.containsKey("79"))assertEquals(Integer.valueOf(FG),on.text.get("79"));
                if(on.text.containsKey("19:41"))assertEquals(Integer.valueOf(FG),on.text.get("19:41"));
                if(!wifi)assertEquals(Integer.valueOf(Prefs.DEF_VPN_COLOR),on.text.get("5G"));
            }
    }
    @Test public void customColorAppliesOnlyWhileVpnIsActive() {
        for(int style:new int[]{Prefs.STYLE_RING,Prefs.STYLE_CAPSULE}) {
            Frame off=draw(style,false,Prefs.DEF_VPN_COLOR,true);
            Frame customOff=draw(style,false,0xffe040fb,true);
            Frame customOn=draw(style,true,0xffe040fb,true);
            int colored=0;
            for(int y=0;y<180;y++)for(int x=0;x<320;x++) {
                assertEquals(off.image.getRGB(x,y),customOff.image.getRGB(x,y));
                if(customOn.image.getRGB(x,y)==0xffe040fb)colored++;
            }
            assertTrue(colored>0);
        }
    }
    @Test public void defaultAndStoredColorsSurviveTransportAndPartialUpdate() {
        TrioSettings base=TrioSettings.defaults(); assertEquals(0xff2196f3,base.vpnColor);
        for(int style:new int[]{Prefs.STYLE_RING,Prefs.STYLE_CAPSULE}) {
            base.trioStyle=style; TrioSettings next=base.copy();next.vpnColor=0xffe040fb;
            assertNotEquals(base,next); assertEquals(next,TrioSettings.fromBundle(next.toBundle()));
            assertEquals(next,next.copy());assertEquals(next.hashCode(),next.copy().hashCode());
            TrioSettings partial=base.copy();assertTrue(partial.applyKeyFrom(next,Prefs.KEY_VPN_COLOR));
            assertEquals(next,partial);assertEquals(Prefs.DEF_VPN_COLOR,base.vpnColor);
        }
        assertEquals(Prefs.DEF_VPN_COLOR,TrioSettings.from(new PreferencesSnapshot(Map.of())).vpnColor);
    }
}
