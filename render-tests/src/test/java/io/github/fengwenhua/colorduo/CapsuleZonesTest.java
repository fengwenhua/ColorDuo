/* Copyright (C) 2026 yixing233 and HyperDuo contributors
 * SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

import android.graphics.Canvas;
import java.awt.image.BufferedImage;
import io.github.fengwenhua.colorduo.TrioZones.Zone;
import io.github.fengwenhua.colorduo.TrioZones.Part;
import org.junit.Before;
import org.junit.After;
import org.junit.Test;

/** Current four-cell layout, actual renderer and hit testing on desktop Android shims. */
public final class CapsuleZonesTest {
    private static final int SIZE = 960;
    private int checks;
    @Before public void reset() { checks = 0; TrioState.sClockText = "19:41"; TrioGeometry.setCapsuleLength(0); }
    @After public void report() {
        TrioGeometry.setCapsuleLength(0);
        System.out.println("Capsule region assertions: " + checks);
    }
    private void ok(String message, boolean result) {
        if (!result) throw new AssertionError(message);
        checks++;
    }
    private static TrioSettings config() {
        TrioSettings s = TrioSettings.defaults();
        s.trioStyle = Prefs.STYLE_CAPSULE;
        s.roleColors = false;
        s.trackAlpha = 40;
        s.showWifi = false;
        s.showBolt = false;
        s.showValue = s.showMobile = s.showSim1 = s.showSim2 = true;
        s.typeHideOnWifi = false;
        s.ringZones = Prefs.decodeLayout("7|3|8|6");
        return s;
    }
    private static BufferedImage paint(TrioSettings s, int battery, int mobile, int[] slots, String clock) {
        TrioState.sClockText = clock;
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = image.createGraphics();
        try {
            TrioRenderer.drawInto(new Canvas(g), SIZE, SIZE, battery, false, false, false,
                    false, -1, mobile, slots, "5GA", 0xFF000000, s, true);
        } finally { g.dispose(); }
        return image;
    }
    private static float[] transform(TrioSettings s) {
        return TrioZones.viewTransform(SIZE, SIZE, 0, TrioAppearance.of(s));
    }
    private void differencesStayInBody(BufferedImage a, BufferedImage b, TrioSettings s, String label) {
        int changed = 0;
        float[] t = transform(s);
        TrioAppearance appearance = TrioAppearance.of(s);
        for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++) {
            if (a.getRGB(x, y) == b.getRGB(x, y)) continue;
            changed++;
            Zone zone = TrioZones.zoneAt(t, appearance, x + .5f, y + .5f);
            if (zone != Zone.LEFT && zone != Zone.CENTRE)
                throw new AssertionError(label + " escaped body at " + x + "," + y + " into " + zone);
        }
        ok(label + " has actual pixels", changed > 0);
    }
    private void anchorResolvesTo(TrioSettings s, boolean left) {
        TrioAppearance appearance = TrioAppearance.of(s);
        float stroke = s.ringStroke * TrioGeometry.CAP_STROKE_RATIO;
        float x = TrioGeometry.capReadingEdge(stroke, left);
        float y = TrioGeometry.capInteriorCy(stroke);
        float[] point = new float[2], t = transform(s);
        TrioZones.toView(t, x, y, point);
        ok("body anchor resolves to its own editable cell",
                TrioZones.zoneAt(t, appearance, point[0], point[1]) == (left ? Zone.LEFT : Zone.CENTRE));
    }

    @Test public void extremeFontsAndLengthsKeepAllInkEditableAndBodyReadingsInside() {
        for (int length : new int[] {0, Prefs.MAX_CAPSULE_LENGTH})
            for (int stroke : new int[] {Prefs.MIN_RING_STROKE, Prefs.MAX_RING_STROKE})
                for (int clock : new int[] {Prefs.MIN_CLOCK_SIZE, Prefs.MAX_CLOCK_SIZE})
                    for (int value : new int[] {Prefs.MIN_VALUE_SIZE, Prefs.MAX_VALUE_SIZE})
                        for (int level : new int[] {5, 100}) {
                            TrioSettings s = config();
                            s.capsuleLength = length; s.ringStroke = stroke;
                            s.clockSize = clock; s.valueSize = value;
                            BufferedImage on = paint(s, level, 2, new int[] {2, 4}, "19:41");
                            differencesStayInBody(on, paint(s, level, 2, new int[] {2, 4}, ""), s, "clock");
                            TrioSettings off = s.copy(); off.showValue = false;
                            differencesStayInBody(on, paint(off, level, 2, new int[] {2, 4}, "19:41"), s, "value");
                            float[] t = transform(s); TrioAppearance a = TrioAppearance.of(s);
                            for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++) {
                                if ((on.getRGB(x, y) >>> 24) > 0
                                        && TrioZones.zoneAt(t, a, x + .5f, y + .5f) == null)
                                    throw new AssertionError("capsule ink has no editable region at " + x + "," + y);
                            }
                            checks++;
                            anchorResolvesTo(s, true); anchorResolvesTo(s, false);
                        }
    }

    private static Part occupant(TrioSettings s, Zone zone, boolean charging, int wifi) {
        return TrioZones.occupant(zone, TrioAppearance.of(s), 79, charging, wifi, "5GA", 2);
    }
    @Test public void fourEditableCellsCanMoveAndClearReadingsWithoutClearingOtherCells() {
        TrioSettings s = config();
        ok("clock occupies its own LEFT cell", occupant(s, Zone.LEFT, false, -1) == Part.CLOCK);
        for (Zone zone : Zone.values()) {
            TrioSettings empty = TrioZones.withPiece(s, zone.ordinal(), null);
            ok("cleared zone has an empty saved list", empty.ringZones[zone.ordinal()].length == 0);
            for (boolean charging : new boolean[] {false, true}) for (int wifi : new int[] {-1, 3}) {
                ok("cleared zone stays empty", occupant(empty, zone, charging, wifi) == null);
                for (Zone other : Zone.values()) if (other != zone)
                    ok("clearing preserves another cell", occupant(empty, other, charging, wifi) == occupant(s, other, charging, wifi));
            }
            for (Part part : new Part[] {Part.BOLT, Part.WIFI, Part.VALUE, Part.TYPE, Part.CLOCK}) {
                TrioSettings ready = s.copy();
                ready.showBolt = ready.showWifi = true;
                ready.mobileTypeMode = Prefs.MOBILE_TYPE_IN_RING;
                TrioSettings placed = TrioZones.withPiece(ready, zone.ordinal(), part);
                ok("picker accepts " + part + " in " + zone, TrioZones.placementChoices(zone, ready, 2).contains(part));
                ok("placed item leads the priority list", placed.ringZones[zone.ordinal()][0] == TrioZones.encodePart(part));
                ok("placed item is rendered", occupant(placed, zone, part == Part.BOLT, 3) == part);
                ok("capsule ignores ring centring switch", placed.valueCentred == ready.valueCentred);
                ok("placement leaves original snapshot unchanged", java.util.Arrays.deepEquals(ready.ringZones, s.ringZones));
            }
        }
        for (Zone zone : Zone.values()) {
            TrioSettings rect = s.copy(); rect.trioStyle = Prefs.STYLE_RECT;
            ok("retired rectangle refuses clock assignment", TrioZones.assign(zone, true, Part.CLOCK, rect, 2) == null);
        }
        TrioSettings ring = s.copy(); ring.trioStyle = Prefs.STYLE_RING;
        ok("ring still places clock", TrioZones.assign(Zone.CENTRE, false, Part.CLOCK, ring, 2) != null);
    }

    private static int alpha(BufferedImage image, TrioSettings s, int dot, boolean bottom) {
        float x = TrioGeometry.capDotCx() + (dot - 1.5f) * TrioGeometry.CAP_DOT_PITCH;
        float y = bottom ? TrioGeometry.capDotBottomY() : TrioGeometry.capDotTopY();
        float[] point = new float[2]; TrioZones.toView(transform(s), x, y, point);
        return image.getRGB(Math.round(point[0]), Math.round(point[1])) >>> 24;
    }
    @Test public void dotRowsFollowMobileLevelAndRemainIndependentOfBatteryAndEachOther() {
        TrioSettings s = config();
        BufferedImage weak = paint(s, 79, 1, null, "19:41"), zero = paint(s, 79, 0, null, "19:41");
        ok("first signal dot follows mobile level", alpha(weak, s, 0, false) > alpha(zero, s, 0, false));
        ok("second dot inactive at level one", alpha(weak, s, 1, false) < alpha(weak, s, 0, false));
        BufferedImage low = paint(s, 5, 1, null, "19:41");
        ok("signal independent of battery", alpha(low, s, 1, false) == alpha(weak, s, 1, false));
        s.dualSim = true;
        BufferedImage dual = paint(s, 79, 0, new int[] {2, 4}, "19:41");
        ok("independent SIM levels", alpha(dual, s, 2, true) > alpha(dual, s, 2, false));
    }
}
