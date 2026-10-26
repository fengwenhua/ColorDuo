/*
 * Copyright (C) 2026 yixing233 and HyperDuo contributors
 *
 * This file is part of ColorDuo, released under the GNU General Public
 * License v3.0. See the LICENSE file at the repository root, or
 * <https://www.gnu.org/licenses/gpl-3.0.html>.
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package io.github.fengwenhua.colorduo;

import android.graphics.Canvas;
import android.os.Bundle;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * Executes production placement, transport and drawing against desktop Android shims.
 *
 * <p>Covers the shared appearance/placement model: each of the three ring bands holds an ORDERED list of
 * candidate pieces, and the first one with something to draw on the current frame wins the
 * zone. That replaces the one-piece-per-zone slots this file used to describe, so the
 * assertions below are about order and fallback rather than about a single id per zone.
 * ColorOS live View leases separately require a drawn current frame in the adapter.
 */
public final class FreeRingTest {
    @org.junit.Before public void reset() { checks = 0; TrioState.sClockText = "19:41"; }
    @org.junit.After public void countAssertions() { System.out.println("FreeRing assertions: " + checks); }
    @org.junit.Test public void emptyModelAndLegacyLayoutAgree() { defaults(); }
    @org.junit.Test public void encodingMigrationAndTransportKeepPriorityLists() { encoding(); }
    @org.junit.Test public void editingPromotesMovesAndRejectsInvalidPlacements() { placement(); }
    @org.junit.Test public void fallbackChainUsesTheFirstDrawableCandidate() { priority(); }
    @org.junit.Test public void ownershipRespectsPlacementAndIndependentSwitches() { ownership(); }
    @org.junit.Test public void permittedComponentsStayInsideTheirBandAndClearTheArc() throws Exception { pixels(); }
    static int checks;
    static final int SIZE = 480;
    static void ok(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    static TrioSettings base() {
        TrioSettings s = TrioSettings.defaults();
        s.enabled = s.showWifi = s.showMobile = s.showValue = s.showBolt = true;
        s.mobileTypeMode = Prefs.MOBILE_TYPE_IN_RING;
        s.trioStyle = Prefs.STYLE_RING;
        s.typeHideOnWifi = false;
        // Three empty zones. The cases that want a placement build it explicitly, so a
        // switch alone can be shown to draw nothing.
        s.ringZones = Prefs.decodeLayout(Prefs.EMPTY_LAYOUT);
        return s;
    }
    /** The ids in one zone, which is the priority order. */
    static int[] zone(TrioSettings s, int z) { return s.ringZones[z]; }
    public static void main(String[] args) throws Exception {
        defaults(); encoding(); placement(); priority(); ownership(); pixels();
        System.out.println("PASS: " + checks + " free-ring assertions");
    }

    /** The bare model is empty; application first-install presets are covered separately. */
    static void defaults() {
        TrioSettings fresh = TrioSettings.defaults();
        for (int z = 0; z < Prefs.ZONE_COUNT; z++) {
            ok(zone(fresh, z).length == 0, "bare model starts with zone " + z + " empty");
        }
        ok("|||".equals(Prefs.encodeLayout(fresh.ringZones)),
                "canonical encoding has four empty cells");
        ok(Arrays.deepEquals(Prefs.decodeLayout(Prefs.EMPTY_LAYOUT), fresh.ringZones),
                "legacy short empty spelling still decodes to the same four cells");
    }

    /** The string encoding: round trip, tolerance for junk, and the migration from ints. */
    static void encoding() {
        int[][] zones = { { Prefs.PART_VALUE, Prefs.PART_WIFI }, { Prefs.PART_TYPE }, {} };
        String encoded = Prefs.encodeLayout(zones);
        ok("3,1|5||".equals(encoded), "the encoding keeps order and empty zones: " + encoded);
        int[][] back = Prefs.decodeLayout(encoded);
        for (int z = 0; z < 3; z++) {
            ok(Arrays.equals(back[z], zones[z]), "zone " + z + " survives the round trip");
        }
        // Order is the data, so it must not be normalised into sorted order.
        int[][] ordered = { { Prefs.PART_SIM_1, Prefs.PART_VALUE }, {}, {} };
        ok(Arrays.equals(Prefs.decodeLayout(Prefs.encodeLayout(ordered))[0], ordered[0]),
                "a deliberately reversed list stays reversed");

        // Junk is dropped piece by piece rather than rejecting the whole layout: one
        // damaged entry must not blank the other two zones.
        int[][] tolerant = Prefs.decodeLayout("3,x,99,0,-1,1|2|");
        ok(Arrays.equals(tolerant[0], new int[] { Prefs.PART_VALUE, Prefs.PART_WIFI }),
                "unknown ids are dropped, the rest of the zone survives");
        ok(tolerant[1].length == 0, "legacy middle signal row leaves the centre");
        ok(Arrays.equals(tolerant[2], new int[] { Prefs.PART_SIM_1 }), "legacy middle signal row migrates to SIM 1 at the bottom");
        ok(Arrays.equals(Prefs.decodeLayout("2,7,3||")[0], new int[]{Prefs.PART_SIM_1, Prefs.PART_VALUE}),
                "retired aggregate migration deduplicates SIM 1 without changing priority");
        // A duplicate in one zone would draw the same thing twice and make the priority
        // list ambiguous about which copy wins.
        int[][] dupe = Prefs.decodeLayout("3,3,1||");
        ok(Arrays.equals(dupe[0], new int[] { Prefs.PART_VALUE, Prefs.PART_WIFI }),
                "a duplicate inside one zone is collapsed");
        ok(Prefs.decodeLayout(null)[0].length == 0, "null decodes to empty");
        ok(Prefs.decodeLayout("")[0].length == 0, "an empty string decodes to empty");
        ok(Prefs.decodeLayout("|||")[0].length == 0, "canonical empty sections decode");
        ok("|||".equals(Prefs.encodeLayout(Prefs.decodeLayout("||||6"))),
                "a fifth section is ignored without changing the four stored cells");

        // The retired packed encodings, four-bit then three-bit.
        String fourBit = Prefs.migrateLayout(String.valueOf(
                Prefs.packRingParts(Prefs.PART_VALUE, Prefs.PART_WIFI, Prefs.PART_MOBILE)), null);
        int[][] migrated = Prefs.decodeLayout(fourBit);
        ok(migrated[0][0] == Prefs.PART_VALUE && migrated[1][0] == Prefs.PART_WIFI
                && migrated[2][0] == Prefs.PART_SIM_1,
                "the four-bit layout migrates every zone and upgrades the retired meter");
        for (int z = 0; z < 3; z++) {
            ok(migrated[z].length == 1, "migration produces one candidate per non-empty zone");
        }
        // The three-bit value is a valid four-bit one too, which is exactly why the widths
        // are a parameter: reading it with four-bit shifts scatters the pieces.
        String threeBit = Prefs.migrateLayout(null, String.valueOf(1 | 3 << 3 | 2 << 6));
        int[][] old = Prefs.decodeLayout(threeBit);
        ok(old[0][0] == Prefs.PART_WIFI && old[1][0] == Prefs.PART_VALUE
                && old[2][0] == Prefs.PART_SIM_1, "the three-bit layout migrates to the same zones and upgrades the meter");
        ok(Prefs.decodeLayout(Prefs.migrateLayout(null, null))[0].length == 0,
                "no stored layout at all migrates to empty");
        // -1 was the automatic-layout sentinel; there is no automatic layout to restore, and
        // promoting it to the old shipped three slots would contradict "fresh is blank".
        ok(Prefs.decodeLayout(Prefs.migrateLayout("-1", null))[0].length == 0,
                "the retired automatic sentinel migrates to empty");

        // Transport: every zone shape survives the bundle and the remote key update.
        TrioSettings s = base();
        s.ringZones = Prefs.decodeLayout("3,1|2|7,8");
        ok(Arrays.deepEquals(s.copy().ringZones, s.ringZones), "copy keeps the lists");
        ok(TrioSettings.fromBundle(s.toBundle()).equals(s), "bundle round trip");
        TrioSettings other = base();
        ok(other.applyKeyFrom(s, Prefs.KEY_RING_LAYOUT)
                && Arrays.deepEquals(other.ringZones, s.ringZones), "remote key update");
        Bundle partial = new Bundle();
        partial.putBoolean(Prefs.KEY_SHOW_WIFI, false);
        ok(Arrays.deepEquals(TrioSettings.fromBundle(partial, s).ringZones, s.ringZones),
                "an unrelated broadcast preserves the layout");
        ok(!base().equals(s), "the layout participates in equality");
    }

    /** Adding, promoting, moving and removing pieces. */
    static void placement() {
        // Adding puts the piece at the FRONT, because the newest choice is the one that wins.
        TrioSettings s = base();
        s = TrioZones.withPiece(s, 0, TrioZones.Part.VALUE);
        s = TrioZones.withPiece(s, 0, TrioZones.Part.WIFI);
        ok(Arrays.equals(zone(s, 0), new int[] { Prefs.PART_WIFI, Prefs.PART_VALUE }),
                "a second piece is promoted ahead of the first");

        // Promoting an existing piece moves it, it does not duplicate it.
        s = TrioZones.withPiece(s, 0, TrioZones.Part.VALUE);
        ok(Arrays.equals(zone(s, 0), new int[] { Prefs.PART_VALUE, Prefs.PART_WIFI }),
                "re-adding promotes instead of duplicating");

        // Moving to another zone removes it from the source: one piece, one place.
        s = TrioZones.withPiece(s, 1, TrioZones.Part.VALUE);
        ok(Arrays.equals(zone(s, 0), new int[] { Prefs.PART_WIFI }),
                "the moved piece leaves its old zone");
        ok(Arrays.equals(zone(s, 1), new int[] { Prefs.PART_VALUE }), "and lands in the new one");

        // Removing one entry reveals what was behind it. withPiece(null) is the "clear this
        // zone" operation instead, which is why the drop is a separate call.
        s = base();
        s = TrioZones.withPiece(s, 0, TrioZones.Part.VALUE);
        s = TrioZones.withPiece(s, 0, TrioZones.Part.WIFI);
        TrioSettings dropped = TrioZones.withoutAt(s, 0, 0);
        ok(zone(dropped, 0).length == 1, "dropping the head keeps the rest of the chain");
        ok(zone(dropped, 0)[0] == Prefs.PART_VALUE, "and the next candidate is revealed");
        ok(zone(s, 0).length == 2, "dropping does not mutate its input");
        ok(zone(TrioZones.withoutAt(s, 0, 5), 0).length == 2,
                "dropping an index that does not exist is a no-op");
        TrioSettings cleared = TrioZones.withPiece(s, 0, null);
        ok(zone(cleared, 0).length == 0, "withPiece(null) clears the whole zone");

        // Reordering within a zone.
        s = base();
        s = TrioZones.withPiece(s, 0, TrioZones.Part.VALUE);
        s = TrioZones.withPiece(s, 0, TrioZones.Part.WIFI);
        TrioSettings up = TrioZones.moveWithin(s, 0, 1, -1);
        ok(Arrays.equals(zone(up, 0), new int[] { Prefs.PART_VALUE, Prefs.PART_WIFI }),
                "one place up promotes");
        ok(Arrays.equals(zone(s, 0), new int[] { Prefs.PART_WIFI, Prefs.PART_VALUE }),
                "reordering does not mutate its input");
        ok(Arrays.equals(zone(TrioZones.moveWithin(s, 0, 0, -1), 0), zone(s, 0)),
                "moving the head further up is a no-op");
        ok(Arrays.equals(zone(TrioZones.moveWithin(s, 1, 0, 1), 1), zone(s, 1)),
                "moving in an empty zone is a no-op");

        // Placing a piece turns its own switch on, so a fresh install does not draw an
        // empty reserved zone.
        TrioSettings off = base();
        off.showWifi = off.showValue = off.showMobile = false;
        TrioSettings on = TrioZones.withPiece(off, 0, TrioZones.Part.WIFI);
        ok(on.showWifi, "placing the arcs switches them on");
        ok(!off.showWifi, "the input is not mutated");

        // The current ring has three bands. Signal rows cannot occupy the centre;
        // the aggregate MOBILE piece remains readable for legacy layouts but is retired
        // from the picker in favour of independently placed SIM rows.
        for (TrioZones.Zone z : new TrioZones.Zone[]{TrioZones.Zone.TOP, TrioZones.Zone.CENTRE, TrioZones.Zone.BOTTOM}) {
            for (TrioZones.Part part : TrioZones.Part.values()) {
                TrioSettings input = base();
                TrioSettings placed = TrioZones.withPiece(input, z.ordinal(), part);
                boolean refused = z == TrioZones.Zone.CENTRE && (part == TrioZones.Part.MOBILE
                        || part == TrioZones.Part.SIM_1 || part == TrioZones.Part.SIM_2);
                if (refused) {
                    ok(Arrays.deepEquals(input.ringZones, placed.ringZones), "refused placement preserves layout: " + part);
                    ok(!TrioZones.placementChoices(z, input, 0).contains(part), "middle picker excludes signal rows: " + part);
                } else {
                    ok(zone(placed, z.ordinal())[0] == TrioZones.encodePart(part), "legal placement: " + part + "/" + z);
                    ok(TrioZones.placementChoices(z, input, 0).contains(part) == (part != TrioZones.Part.MOBILE),
                            "picker offers current components without live SIMs: " + part + "/" + z);
                }
            }
        }
    }

    /** The fallback chain: the first candidate that can draw wins, later ones step in. */
    static void priority() {
        TrioSettings s = base();
        s = TrioZones.withPiece(s, 0, TrioZones.Part.VALUE);   // head
        s = TrioZones.withPiece(s, 0, TrioZones.Part.WIFI);    // promoted to head
        // Now the chain is [WIFI, VALUE]: the arcs lead.
        ok(Arrays.equals(zone(s, 0), new int[] { Prefs.PART_WIFI, Prefs.PART_VALUE }),
                "the chain is recorded in priority order");

        TrioAppearance a = TrioAppearance.of(s);
        ok(a.ring(79, false, 3, "5G", 2).parts[0] == Prefs.PART_WIFI,
                "the head wins when it can draw");
        // With Wi-Fi off the head cannot draw, so the percentage behind it takes the zone -
        // the fallback, not an empty third.
        TrioSettings noWifi = s.copy();
        noWifi.showWifi = false;
        ok(TrioAppearance.of(noWifi).ring(79, false, 3, "5G", 2).parts[0] == Prefs.PART_VALUE,
                "the fallback takes over when the head has nothing to draw");
        // Order is what decides: reversed, the percentage leads even with Wi-Fi available.
        TrioSettings reversed = s.copy();
        reversed.ringZones = Prefs.decodeLayout("3,1||");
        ok(TrioAppearance.of(reversed).ring(79, false, 3, "5G", 2).parts[0] == Prefs.PART_VALUE,
                "the recorded order decides, not the piece's kind");
        // Nothing drawable at all leaves the zone empty rather than painting a stale piece.
        TrioSettings none = s.copy();
        none.showWifi = none.showValue = false;
        ok(TrioAppearance.of(none).ring(79, false, 3, "5G", 2).parts[0] == Prefs.PART_NONE,
                "a zone with no drawable candidate is empty");
        // The bolt only exists while charging, so it steps aside on an idle frame. It is
        // added LAST so it heads the chain - withPiece puts the newest piece at the front.
        TrioSettings charging = base();
        charging = TrioZones.withPiece(charging, 0, TrioZones.Part.VALUE);
        charging = TrioZones.withPiece(charging, 0, TrioZones.Part.BOLT);
        ok(Arrays.equals(zone(charging, 0), new int[] { Prefs.PART_BOLT, Prefs.PART_VALUE }),
                "the bolt heads the chain");
        ok(TrioAppearance.of(charging).ring(79, true, 3, "5G", 2).parts[0] == Prefs.PART_BOLT,
                "the bolt leads while charging");
        ok(TrioAppearance.of(charging).ring(79, false, 3, "5G", 2).parts[0] == Prefs.PART_VALUE,
                "and the percentage steps in while idle");
    }

    /** Ownership: the native handoff follows the LAYOUT, not the frame. */
    static void ownership() {
        TrioSettings s = base();
        TrioAppearance a = TrioAppearance.of(s);
        ok(!a.ownsWifi() && !a.drawsBolt() && !a.signalDots() && !a.ownsValue(),
                "an empty layout releases all native components");
        ok(a.handsBackPercentage(), "the percentage uses the native display setting");

        // A piece placed ANYWHERE claims its native view, even on a frame where a
        // higher-priority candidate happens to be drawing that zone instead. Asking about
        // the frame would make the native icon flicker as the fallback chain moved.
        s = base();
        s = TrioZones.withPiece(s, 0, TrioZones.Part.WIFI);
        s = TrioZones.withPiece(s, 0, TrioZones.Part.VALUE);   // VALUE leads
        s.showWifi = false;                                    // so WIFI never draws
        a = TrioAppearance.of(s);
        ok(a.hasRingPart(Prefs.PART_WIFI), "a placed piece is still owned when it cannot draw");
        ok(!a.ownsWifi(), "but its own switch still gates the handoff");
        s.showWifi = true;
        ok(TrioAppearance.of(s).ownsWifi(), "switched on, the placed piece claims the native view");

        s = base();
        s = TrioZones.withPiece(s, 1, TrioZones.Part.CLOCK);
        ok(TrioAppearance.of(s).hidesStatusClock(), "a placed clock takes the native clock");
        ok(!TrioAppearance.of(base()).hidesStatusClock(), "an empty layout releases it");

        s = base();
        s = TrioZones.withPiece(s, 1, TrioZones.Part.WIFI);
        s.enabled = false;
        ok(!TrioAppearance.of(s).hidesNativeBolt() && !TrioAppearance.of(s).handsBackPercentage(),
                "master off owns no battery subview");

        // The two per-card rows: independent switches, independent zones.
        s = base();
        s = TrioZones.withPiece(s, 0, TrioZones.Part.SIM_1);
        s = TrioZones.withPiece(s, 2, TrioZones.Part.SIM_2);
        s.showSim1 = s.showSim2 = false;
        a = TrioAppearance.of(s);
        ok(!a.drawsSim1() && !a.drawsSim2(), "placed cards draw nothing until their switches are on");
        s.showSim1 = true;
        a = TrioAppearance.of(s);
        ok(a.drawsSim1() && !a.drawsSim2(), "the cards are switched independently");
        ok(a.foldsMobile(), "a drawn card takes the native mobile slot");
        s.showSim1 = false;
        ok(!TrioAppearance.of(s).foldsMobile(), "with both cards off nothing claims the slot");
        ok(TrioAppearance.of(s).hasRingPart(Prefs.PART_SIM_1),
                "the switch never clears the placement");

        // Placement is the only switch now. There is no "out of ring" mode to put a piece in:
        // a card that is not placed draws nothing and hands the reading back to MIUI, which
        // is what "zero cards placed" means rather than a position the user selects.
        s = base();
        ok(!TrioAppearance.of(s).drawsSim1(), "an unplaced card draws nothing");
        ok(!TrioAppearance.of(s).foldsMobile(), "and claims no native slot");
        s = TrioZones.withPiece(s, 0, TrioZones.Part.SIM_1);
        s.showSim1 = true;
        ok(TrioAppearance.of(s).drawsSim1(), "placing it is what makes it draw");
        ok(TrioAppearance.of(s).foldsMobile(), "and what claims the native slot");
    }

    static BufferedImage draw(TrioSettings s, TrioAppearance.Ring r) throws Exception {
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        Canvas c = new Canvas(g); c.scale(4f, 4f);
        Method draw = TrioRenderer.class.getDeclaredMethod("drawFreeRing", Canvas.class, int.class,
                boolean.class, int.class, int.class, int[].class, String.class, int.class,
                int.class, TrioAppearance.class, TrioAppearance.Ring.class);
        draw.setAccessible(true);
        draw.invoke(null, c, 100, false, 3, 4, new int[] {2, 4}, "5GA", 0xffff0000,
                0xffffffff, TrioAppearance.of(s), r);
        g.dispose(); return image;
    }

    static BufferedImage background(TrioSettings s, TrioAppearance.Ring r) throws Exception {
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            Canvas c = new Canvas(g); c.scale(4f, 4f);
            Method draw = TrioRenderer.class.getDeclaredMethod("drawBattery", Canvas.class, int.class,
                    int.class, int.class, TrioAppearance.class, float.class, float.class, float.class, float.class);
            draw.setAccessible(true);
            draw.invoke(null, c, 100, 0xffff0000, 0xffffffff, TrioAppearance.of(s),
                    r.gapStart(), r.gapEnd(), r.sweep(), r.arcStart());
        } finally { g.dispose(); }
        return image;
    }

    static void pixels() throws Exception {
        for (TrioZones.Zone zone : new TrioZones.Zone[]{TrioZones.Zone.TOP, TrioZones.Zone.CENTRE, TrioZones.Zone.BOTTOM})
            for (TrioZones.Part part : TrioZones.Part.values())
            for (int stroke : new int[] {4, 8, 16}) for (int size : new int[] {18, 90}) {
                if (zone == TrioZones.Zone.CENTRE && (part == TrioZones.Part.MOBILE
                        || part == TrioZones.Part.SIM_1 || part == TrioZones.Part.SIM_2)) continue;
                TrioSettings s = TrioZones.withPiece(base(), zone.ordinal(), part);
                s.ringStroke = s.arcStroke = stroke;
                s.valueSize = s.typeSize = s.clockSize = size;
                s.valueWeight = s.typeWeight = s.clockWeight = size == 18 ? 100 : 900;
                TrioAppearance a = TrioAppearance.of(s);
                TrioAppearance.Ring r = a.ring(100, true, 3, "5GA", 2);
                ok(TrioZones.occupant(zone, a, r) == part, "runtime occupant matches requested component");
                BufferedImage full = draw(s, r);
                // Clearing parts would close the dynamic bottom opening and change the
                // battery itself. Draw its real background with this frame's unchanged gaps.
                BufferedImage ring = background(s, r);
                int ink = 0, outside = 0, overlap = 0;
                float top = zone == TrioZones.Zone.TOP ? 0
                        : zone == TrioZones.Zone.CENTRE ? TrioGeometry.ZONE_CUT_TOP
                        : TrioGeometry.ZONE_CUT_BOTTOM;
                float bottom = zone == TrioZones.Zone.TOP ? TrioGeometry.ZONE_CUT_TOP
                        : zone == TrioZones.Zone.CENTRE ? TrioGeometry.ZONE_CUT_BOTTOM : 120;
                for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++) {
                    if (full.getRGB(x, y) == ring.getRGB(x, y)) continue;
                    ink++;
                    if (y < top * 4 || y >= bottom * 4) outside++;
                    if ((ring.getRGB(x, y) >>> 24) != 0) overlap++;
                }
                String label = part + "/" + zone + "/stroke=" + stroke + "/size=" + size;
                ok(ink > 0, "component paints ink: " + label);
                ok(outside == 0, "ink stays in its tappable third: " + label + " outside=" + outside);
                ok(overlap == 0, "component clears battery arc: " + label + " overlap=" + overlap);
            }
    }
}
