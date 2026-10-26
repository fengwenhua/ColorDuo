/* Copyright (C) 2026 yixing233 and HyperDuo contributors
 * SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

import android.graphics.Canvas;
import java.awt.image.BufferedImage;
import org.junit.Test;
import static org.junit.Assert.*;

/** The ring ink box is centred on B_CY, not the SVG's 60-unit midpoint. */
public final class RingHitBoundaryTest {
    @Test public void everyRingPixelIsTappableAtExtremeStrokesAndBothHostShapes() {
        try {
            TrioState.sClockText = "19:41";
            for (String layout : new String[] {"|||", "7||8|", "6||7|"})
                for (int stroke : new int[] {Prefs.MIN_RING_STROKE, Prefs.MAX_RING_STROKE})
                    for (int[] size : new int[][] {{96, 96}, {960, 960}, {170, 104}}) {
                        TrioSettings s = TrioSettings.defaults();
                        s.trioStyle = Prefs.STYLE_RING; s.ringStroke = stroke;
                        s.ringZones = Prefs.decodeLayout(layout);
                        TrioAppearance a = TrioAppearance.of(s);
                        BufferedImage image = new BufferedImage(size[0], size[1], BufferedImage.TYPE_INT_ARGB);
                        java.awt.Graphics2D graphics = image.createGraphics();
                        try {
                            TrioRenderer.drawInto(new Canvas(graphics), size[0], size[1], 79, false, false,
                                    false, false, 3, 4, new int[] {2, 4}, "5G", 0xFF000000, s, true);
                        } finally { graphics.dispose(); }
                        float[] transform = new float[3];
                        assertTrue(TrioRenderer.designTransform(size[0], size[1], a, transform));
                        int painted = 0;
                        for (int y = 0; y < size[1]; y++) for (int x = 0; x < size[0]; x++) {
                            if ((image.getRGB(x, y) >>> 24) == 0) continue;
                            painted++;
                            TrioZones.Zone zone = TrioZones.zoneAt(transform, false, x + .5f, y + .5f);
                            assertNotNull("untappable ring pixel at " + x + "," + y + " stroke=" + stroke, zone);
                            assertEquals(zone, TrioZones.zoneAt(transform, a, x + .5f, y + .5f));
                        }
                        assertTrue("frame must paint", painted > 0);
                    }
        } finally { TrioState.sClockText = ""; }
    }

    @Test public void bottomInkEdgeHitsBottomAndPointsBeyondTheInkBoxStillMiss() {
        TrioAppearance a = TrioAppearance.of(TrioSettings.defaults());
        float[] transform = new float[3], point = new float[2];
        assertTrue(TrioRenderer.designTransform(960, 960, a, transform));
        float bottom = TrioGeometry.INK_Y + TrioGeometry.INK_H;
        assertTrue("regression requires ink below the old SVG box", bottom > TrioGeometry.BOX);
        assertTrue(TrioZones.toView(transform, TrioGeometry.B_CX, bottom - .01f, point));
        assertEquals(TrioZones.Zone.BOTTOM, TrioZones.zoneAt(transform, a, point[0], point[1]));
        assertTrue(TrioZones.toView(transform, TrioGeometry.B_CX, bottom + 1f, point));
        assertNull(TrioZones.zoneAt(transform, a, point[0], point[1]));
        assertNull(TrioZones.zoneAt(transform, a, -500, -500));
    }
}
