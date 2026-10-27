/* Copyright (C) 2026 ColorDuo contributors
 * SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

import android.graphics.Canvas;
import android.graphics.Paint;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;

/** Observe the actual renderer's font metrics and transforms, including the 100% case. */
public final class CapsuleBodySpacingTest {
    @After public void reset() { TrioGeometry.setCapsuleLength(0); TrioState.sClockText = "19:41"; }

    private static TrioSettings settings(int length) {
        TrioSettings s = TrioSettings.defaults();
        s.trioStyle = Prefs.STYLE_CAPSULE;
        s.ringZones = Prefs.decodeLayout("7|3|8|6");
        s.setShowValue(true);
        s.capsuleLayout = s.capsuleLayout.withLength(length);
        return s;
    }

    // [left start, left end, right start, right end, design-to-host scale, left text height]
    private static double[] draw(TrioSettings s, String clock, int level, boolean swap) {
        TrioState.sClockText = clock;
        TrioGeometry.setCapsuleLength(s.capsuleLayout.length());
        int width = TrioGeometry.capHostWidthPx(240), height = TrioGeometry.capHostHeightPx(240);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D graphics = image.createGraphics();
        double[] bounds = {Double.NaN, Double.NaN, Double.NaN, Double.NaN, 0, 0};
        try {
            TrioRenderer.drawInto(new Canvas(graphics) {
                @Override public void drawText(String text, float x, float y, Paint paint) {
                    if (text.equals(clock) || text.equals(String.valueOf(level))) {
                        boolean left = text.equals(clock) != swap;
                        assertEquals(left ? Paint.Align.LEFT : Paint.Align.RIGHT, paint.getTextAlign());
                        float advance = paint.measureText(text);
                        double start = left ? x : x - advance;
                        Point2D from = graphics().getTransform().transform(new Point2D.Double(start, y), null);
                        Point2D to = graphics().getTransform().transform(new Point2D.Double(start + advance, y), null);
                        int offset = left ? 0 : 2;
                        bounds[offset] = from.getX(); bounds[offset + 1] = to.getX();
                        if (left) bounds[5] = graphics().getTransform().getScaleY() * paint.getTextSize();
                    }
                    super.drawText(text, x, y, paint);
                }
            }, width, height, level, false, false, false, false, -1, 4,
                    new int[]{4, 4}, "5G", 0xffffffff, s, false);
            bounds[4] = TrioRenderer.inkScale(width, height, Prefs.STYLE_CAPSULE)
                    * TrioAppearance.of(s).glyphFit();
        } finally { graphics.dispose(); }
        assertFalse("both body readings must be drawn", Double.isNaN(bounds[0]) || Double.isNaN(bounds[2]));
        return bounds;
    }

    @Test public void fullBatteryRetainsGapAtEveryWidthAndLargeFonts() {
        for (int length : new int[]{0, Prefs.MIN_CAPSULE_LENGTH, Prefs.MAX_CAPSULE_LENGTH})
            for (String clock : new String[]{"11:11", "23:59", "23:59:59"})
                for (int weight : new int[]{100, 900})
                    for (boolean swap : new boolean[]{false, true}) {
                        TrioSettings s = settings(length);
                        if (swap) s.ringZones = Prefs.decodeLayout("7|6|8|3");
                        s.clockSize = Prefs.MAX_CAPSULE_CLOCK_SIZE;
                        s.valueSize = Prefs.MAX_CAPSULE_VALUE_SIZE;
                        s.clockWeight = s.valueWeight = weight;
                        s.capsuleLayout = s.capsuleLayout.withLeftDx(3).withRightDx(-3);
                        double[] b = draw(s, clock, 100, swap);
                        assertTrue("time and 100 need a real gap: length=" + length + ", " + clock,
                                b[2] - b[1] >= TrioGeometry.CAP_TEXT_GAP * b[4] - .01);
                    }
    }

    @Test public void liveWidthChangesKeepOuterAnchorsAndIncreaseFreeSpace() {
        TrioSettings s = settings(0);
        s.clockSize = Prefs.MIN_CLOCK_SIZE;
        s.valueSize = Prefs.MIN_VALUE_SIZE;
        double[] normal = draw(s, "23:59", 100, false);
        s.capsuleLayout = s.capsuleLayout.withLength(Prefs.MAX_CAPSULE_LENGTH);
        double[] wide = draw(s, "23:59", 100, false);
        assertEquals("left cell remains anchored left", normal[0], wide[0], 1);
        assertTrue("right cell follows the extended right edge", wide[3] > normal[3] + 100);
        assertTrue("widening creates more space", wide[2] - wide[1] > normal[2] - normal[1] + 100);
        assertEquals("widening preserves text height when contents already fit", normal[5], wide[5], .1);
        s.capsuleLayout = s.capsuleLayout.withLength(0);
        assertArrayEquals("restoring the width restores the layout without a style toggle",
                normal, draw(s, "23:59", 100, false), .01);
    }
}
