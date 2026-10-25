# Tappable-zone verification harness.
#
# The settings screen's visual editor lets the user press the top, middle or
# bottom of the glyph and flips the switch for whatever is drawn there. Two
# claims have to hold for that to be honest, and both are pixel claims:
#
#   1. every pixel the renderer paints belongs to one of the three zones, so no
#      visible piece is untappable;
#   2. the piece TrioZones names as a zone's occupant is really the piece the
#      renderer put there - nothing else is drawing in that zone, and a zone the
#      table calls empty really is empty;
#   3. a piece whose ink is text (the percentage) stays inside the zone it is
#      hit-tested in at ITS WIDEST reading, not merely at whichever level the
#      other checks happen to use. See LEVELS and rectCentreHoldsItsText: the
#      first version of this harness skipped this and let a real defect through,
#      where the right-hand stroke of "100" fell in the mobile column, so tapping
#      the number switched the signal meter off.
#
# (2) is what ties TrioZones to TrioAppearance. Both could be wrong together and
# pass a source-level assertion; they cannot both be wrong and pass a
# pixel-level one.
#
# It is measured by isolation rather than by "toggling X moves only zone Y",
# because that phrasing is false for this glyph and usefully so: in the ring,
# turning the percentage on moves the Wi-Fi arcs out of the ring centre and into
# the notch, and turning it off closes the ring's mouth. One switch therefore
# moves ink in two thirds - legitimately, and that relocation is exactly what the
# editor has to keep up with. So each piece's contribution is measured WITHIN
# one zone at a time: paint the configuration, paint it again with that one
# piece switched off, and see whether that zone's ink changed.
#
# The shims live under tests\shims, which is tracked, so this runs in a fresh
# clone with nothing but app\src.
#
# Usage:  powershell -File tests\drawing-zones.ps1

$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$jdk = Join-Path $root '.tools\jdk\jdk-21.0.12.1+1\bin'
$javac = Join-Path $jdk 'javac.exe'
$java = Join-Path $jdk 'java.exe'
if (!(Test-Path $javac)) { $javac = 'javac'; $java = 'java' }

$trio = Join-Path $root 'app\src\main\java\io\github\fengwenhua\colorduo'
$rendererPath = Join-Path $trio 'TrioRenderer.java'
$zonesPath = Join-Path $trio 'TrioZones.java'

$renderer = [System.IO.File]::ReadAllText($rendererPath)
$zones = [System.IO.File]::ReadAllText($zonesPath)

# --- source contract pins ------------------------------------------------------
# The pixels below cannot see these. The drawing and the hit-testing have to go
# through one transform, or the hot spots drift off the pieces they name the
# first time either side changes - which is the whole failure mode this feature
# has. The renderer therefore has to ask designTransform for its transform
# rather than working the fit out again, and zoneAt has to invert that same
# triple rather than deriving a design-space point of its own.

if (![regex]::IsMatch($renderer,
        'designTransform\(w, h, appearance, transform\)')) {
    throw 'drawInto no longer takes its transform from designTransform'
}
if (![regex]::IsMatch($renderer,
        'static boolean designTransform\(int w, int h, TrioAppearance appearance, float\[\] out\)')) {
    throw 'Production designTransform missing or its signature changed'
}
$drawInto = [regex]::Match($renderer,
    '(?s)static void drawInto\(Canvas canvas, int w, int h, int level, boolean charging,\s*\n\s*boolean quickCharging.*?\n    \}')
if (!$drawInto.Success) { throw 'could not read the full drawInto out of TrioRenderer' }
if ([regex]::IsMatch($drawInto.Value, 'TrioGeometry\.INK_W \* scale')) {
    throw 'drawInto works the fit out itself again instead of calling designTransform'
}
if (![regex]::IsMatch($zones, '\(viewX - transform\[1\]\) / scale')) {
    throw 'zoneAt no longer inverts the drawing transform'
}
if (![regex]::IsMatch($zones, 'static Zone zoneAt\(float\[\] transform, boolean rect')) {
    throw 'zoneAt no longer takes a canvas-space point'
}
# The mapping the finger actually goes through has to be defined in TrioZones, not
# in the View: a View needs an Android Context to exist, so arithmetic left there is
# unreachable from this harness - and untested arithmetic about where the tappable
# areas are is the one thing this feature cannot afford.
if (![regex]::IsMatch($zones, 'static float\[\] viewTransform\(int width, int height, float inset,')) {
    throw 'viewTransform is missing from TrioZones, so the drawn mapping is untestable'
}
$previewPath = Join-Path $trio 'TrioPreviewView.java'
$preview = [System.IO.File]::ReadAllText($previewPath)
if (![regex]::IsMatch($preview,
        'return TrioZones\.viewTransform\(width, height, inset, TrioAppearance\.of\(settings\)\);')) {
    throw 'the preview no longer defers to TrioZones.viewTransform, so the two mappings can drift'
}
if ([regex]::IsMatch($preview, 'TrioGeometry\.INK_W \* scale')) {
    throw 'the preview keeps its own copy of the fit again'
}

# The rectangular centre column has to BE the box the renderer bounds its text by,
# not a constant chosen nearby. When those were two independent numbers the seam
# sat 3 design units inside the text allowance, so the right-hand stroke of a
# three-digit "100" landed in the mobile column and tapping the number switched the
# signal meter off. The pixel check below catches that at the sizes it can resolve;
# this pin catches the shape of it, so a future "let me just nudge the seam" edit
# fails here rather than in someone's hand.
if (![regex]::IsMatch($zones,
        'RECT_SEAM_LEFT = TrioGeometry\.RECT_TEXT_LEFT;')) {
    throw 'the rect centre seam is no longer derived from the text box the renderer uses'
}
if (![regex]::IsMatch($zones,
        'RECT_SEAM_RIGHT = TrioGeometry\.RECT_TEXT_RIGHT;')) {
    throw 'the rect centre seam is no longer derived from the text box the renderer uses'
}
$geometryPath = Join-Path $trio 'TrioGeometry.java'
$geometry = [System.IO.File]::ReadAllText($geometryPath)
if (![regex]::IsMatch($geometry, 'RECT_TEXT_CLEAR = RECT_TEXT_RIGHT - RECT_TEXT_LEFT;')) {
    throw 'the renderers text allowance no longer comes off the same two edges'
}

# --- generated probe -----------------------------------------------------------
$src = Join-Path $root 'work\zonescheck\src'
$classes = Join-Path $root 'work\zonescheck\classes'
if (Test-Path $src) { Remove-Item -Recurse -Force $src }
if (Test-Path $classes) { Remove-Item -Recurse -Force $classes }
New-Item -ItemType Directory -Force -Path (Join-Path $src 'io\github\fengwenhua\colorduo') | Out-Null
New-Item -ItemType Directory -Force -Path $classes | Out-Null

$probe = @'
package io.github.fengwenhua.colorduo;

import android.graphics.Canvas;
import io.github.fengwenhua.colorduo.TrioZones.Part;
import io.github.fengwenhua.colorduo.TrioZones.Zone;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * Paints the real glyph and checks the tappable thirds against the pixels.
 *
 * <p>Nothing here asserts a number TrioZones reported. Every claim is settled by
 * which pixels the renderer put down: that the zones cover all of the ink, that
 * a zone's named occupant is the only piece drawing in it, and that a zone the
 * table calls empty has no ink at all.
 *
 * <p>Each piece's contribution is isolated by switching that one piece off and
 * looking at what the zone lost, which is what keeps the ring's genuine
 * relocations - the arcs moving between the notch and the centre - from being
 * mistaken for a table error.
 */
final class DrawingZonesTest {

    /**
     * Canvas edge in pixels. 8 pixels per design unit, so the antialiased fringe
     * on either side of a seam (about a pixel) is far inside the smallest margin
     * the seams leave - see the RECT_TOP_BOTTOM note in TrioZones.
     */
    private static final int SIZE = 960;

    private static final int LEVEL = 79;

    /**
     * The battery levels the occupancy checks are run at.
     *
     * <p>Three of them, because the rectangular arrangement's percentage and the
     * ring's notch percentage are <em>text</em>: their ink width depends on how
     * many digits the reading has. The first version of this harness rendered
     * every configuration at a single two-digit level and therefore never measured
     * the widest case - which hid a real defect where the right-hand stroke of
     * "100" fell outside the centre column it is hit-tested in, so a tap on the
     * number turned the mobile meter off instead. 100 is in this list permanently
     * for that reason: it is the only three-digit level, and it is the one that
     * reaches furthest.
     */
    private static final int[] LEVELS = { 79, 5, 100 };

    /**
     * Per-SIM levels for the frames that draw the dual reading, matching the
     * settings screen's own preview: SIM 1 one step down from SIM 2, so the two
     * rows differ. Equal levels would make the dual switch a no-op in the
     * rectangular arrangement - where the reading only changes which levels the
     * two columns carry - and the harness would then report a working switch as
     * dead.
     */
    private static final int[] TWO_SIMS = { 2, 4 };

    private static int checks;
    private static int renders;

    /**
     * A render configuration: the settings, plus the runtime state the renderer
     * takes as arguments rather than reading from the settings. Charging is the
     * one that matters here - it is the only frame the bolt can appear on, and
     * the bolt is one of the pieces the editor offers a hot spot for.
     */
    private static final class Scene {
        final TrioSettings settings;
        final boolean charging;
        /** The battery level this frame reports; see {@link #LEVELS}. */
        final int level;
        final int wifiLevel;

        Scene(TrioSettings settings, boolean charging) {
            this(settings, charging, LEVEL);
        }

        Scene(TrioSettings settings, boolean charging, int level) {
            this(settings, charging, level, 3);
        }

        Scene(TrioSettings settings, boolean charging, int level, int wifiLevel) {
            this.settings = settings;
            this.charging = charging;
            this.level = level;
            this.wifiLevel = wifiLevel;
        }

        Scene copy() {
            return new Scene(settings.copy(), charging, level, wifiLevel);
        }

        /** The same scene with charging on, for the bolt's own cases. */
        Scene charging() {
            return new Scene(settings.copy(), true, level, wifiLevel);
        }

        /** The same scene reporting a different battery level. */
        Scene atLevel(int newLevel) {
            return new Scene(settings.copy(), charging, newLevel, wifiLevel);
        }

        Scene atWifi(int newWifiLevel) {
            return new Scene(settings.copy(), charging, level, newWifiLevel);
        }
    }

    public static void main(String[] args) {
        // The configurations worth pinning: the default look, each way a piece
        // can be displaced out of its usual slot, the charging frame, the
        // out-of-ring signal (which leaves the glyph's bottom empty), and the
        // extremes of every slider that sizes ink.
        final List<Scene> scenes = new ArrayList<>();
        for (boolean rect : new boolean[] { false, true }) {
            scenes.add(cfg(rect, s -> { }));
            // (The old "percentage centred" scene is gone: the field is no longer read.)
            scenes.add(cfg(rect, s -> s.showWifi = false));
            scenes.add(cfg(rect, s -> s.showValue = false));
            scenes.add(cfg(rect, s -> s.showMobile = false));
            scenes.add(cfg(rect, s -> { s.showWifi = false; s.showValue = false;
                    s.mobileTypeMode = Prefs.MOBILE_TYPE_IN_RING; }));
            scenes.add(cfg(rect, s -> { s.showWifi = false; s.showValue = false;
                    s.dualSim = true; }));
            // A card left unplaced: the reading goes back to the status bar and the glyph's
            // bottom goes empty. This replaces the old "signal out of ring" scene - same
            // picture, but expressed as an absent placement rather than a switch.
            scenes.add(cfg(rect, s -> s.ringZones = Prefs.decodeLayout("||")));
            // Every slider at the value that makes ink largest, then smallest.
            scenes.add(cfg(rect, s -> { s.valueSize = Prefs.MAX_VALUE_SIZE;
                    s.typeSize = Prefs.MAX_TYPE_SIZE; s.arcStroke = Prefs.MAX_ARC_STROKE;
                    s.ringStroke = Prefs.MAX_RING_STROKE;
                    s.mobileTypeMode = Prefs.MOBILE_TYPE_IN_RING; }));
            scenes.add(cfg(rect, s -> { s.valueSize = Prefs.MIN_VALUE_SIZE;
                    s.typeSize = Prefs.MIN_TYPE_SIZE; s.arcStroke = Prefs.MIN_ARC_STROKE;
                    s.ringStroke = Prefs.MIN_RING_STROKE; }));
            // Charging, which is the only frame the bolt can appear on.
            scenes.add(cfg(rect, s -> { }).charging());
            scenes.add(cfg(rect, s -> s.valueCentred = true).charging());
        }

        for (Scene base : scenes) {
            // Every configuration is checked at every level. The level is not a
            // setting, so it cannot be folded into the configuration list without
            // multiplying that list; and it has to be swept rather than fixed,
            // because a percentage's ink width depends on its digit count - see
            // LEVELS.
            for (int wifiLevel : new int[] { 3, -1 }) {
            for (int level : LEVELS) {
                final Scene scene = base.atWifi(wifiLevel).atLevel(level);
                final TrioSettings c = scene.settings;
                final String what = (c.trioStyle == Prefs.STYLE_RECT ? "rect/" : "ring/")
                        + "w" + (c.showWifi ? 1 : 0) + "v" + (c.showValue ? 1 : 0)
                        + "m" + (c.showMobile ? 1 : 0)
                        + "/c" + (c.valueCentred ? 1 : 0) + "d" + (c.dualSim ? 1 : 0)
                        + "t" + c.mobileTypeMode + "g" + (scene.charging ? 1 : 0)
                        + "z" + Prefs.encodeLayout(c.ringZones)
                        + "/v" + c.valueSize + "y" + c.typeSize
                        + "/a" + c.arcStroke + "r" + c.ringStroke
                        + "/L" + level + "/wifi" + wifiLevel;

                coversAllInk(scene, what);
                occupancyMatchesInk(scene, what);
            }
            }
        }

        // The mapping itself, and the taps that should do nothing.
        roundTrip(false);
        roundTrip(true);
        offGlyphMisses();

        // The rectangular centre column versus the text the renderer puts in it.
        // This is the assertion the defect of the thirty-first round needed and did
        // not have: it is stated against the widest reading each slider can make,
        // rather than against whichever level the other checks happen to use.
        rectCentreHoldsItsText();

        // The mapping the finger really goes through, inset and all. The scenes
        // above are drawn with no inset because the harness paints the glyph edge to
        // edge; the settings screen insets it, so that path needs its own check or
        // the one transform the user touches would be the untested one.
        insetMapping();

        // Every switch has to actually change the drawing: a switch that moves no
        // pixel would be offered by the editor as a hot spot with no effect.
        everySwitchMovesInk();

        // A tap on any zone always names a real piece - never null, and never a
        // piece with no switch behind it.
        everyZoneNamesAPiece();

        // The assignment solver: what the page's per-zone picker offers, and what it
        // writes when the user chooses an entry. Verified by re-deriving every answer
        // through the real arbitration, and by brute-forcing every rejection over the
        // full switch space so "unreachable" is a fact about the renderer rather than
        // a limitation of the solver's search.
        assignmentSolverPlacesWhatItOffers();
        assignmentSolverMakesTheSmallestEdit();
        assignmentRejectionsAreReal();
        assignmentNeedsTheRuntimeState();
        assignmentCanEmptyAZone();
        wifiOccupantNeedsPixels();

        // The mouth's width is a drawn fact no other check can see: everything
        // else still passes when the ring quietly loses a fifth of each side.
        onlyTheDualReadingWidensTheMouth();

        // The dot-size slider has to reach the pixels, and has to stop at the ceiling
        // instead of letting the beads run outside the box that fits the glyph. The
        // geometry assertions live in issue-regressions; this is the pixel claim.
        dotSizeReachesThePixels();

        System.out.println("PASS: " + checks + " cases over " + scenes.size()
                + " configurations and " + renders + " renders");
    }

    // ------------------------------------------------------------------ cases

    private static Scene cfg(boolean rect, java.util.function.Consumer<TrioSettings> f) {
        final TrioSettings s = TrioSettings.defaults();
        s.trioStyle = rect ? Prefs.STYLE_RECT : Prefs.STYLE_RING;
        f.accept(s);
        return new Scene(s, false);
    }

    /** Every painted pixel belongs to a zone, and the zones tile the design box. */
    private static void coversAllInk(Scene scene, String what) {
        final boolean rect = scene.settings.trioStyle == Prefs.STYLE_RECT;
        final boolean[] ink = ink(scene);
        final float[] t = transform(scene.settings);

        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                if (!ink[y * SIZE + x]) {
                    continue;
                }
                if (TrioZones.zoneAt(t, rect, x + 0.5f, y + 0.5f) == null) {
                    throw new AssertionError(what + ": ink at " + x + "," + y
                            + " is in none of the three zones, so it cannot be pressed");
                }
            }
        }

        // And the zones have to tile the box rather than merely cover the ink: a
        // design point belonging to no zone is a tap that silently does nothing.
        for (float dy = 0f; dy <= TrioGeometry.BOX; dy += 0.5f) {
            for (float dx = 0f; dx <= TrioGeometry.BOX; dx += 0.5f) {
                final float[] v = new float[2];
                if (!TrioZones.toView(t, dx, dy, v)) {
                    throw new AssertionError(what + ": toView refused a valid transform");
                }
                if (TrioZones.zoneAt(t, rect, v[0], v[1]) == null) {
                    throw new AssertionError(what + ": design point " + dx + "," + dy
                            + " belongs to no zone");
                }
            }
        }
        checks++;
    }

    /**
     * The table and the drawing agree about where each piece's ink is.
     *
     * <p>The claim tested is containment, not "this switch moves only this
     * third": switching a piece off must not remove ink from any third the table
     * does not hold it responsible for. Containment is the honest form, because
     * this glyph's pieces genuinely gate and displace one another - the bolt needs
     * the percentage it replaces, the dual rows push the arcs out of the notch,
     * and the ring's mouth opens and closes with them. An exclusivity claim would
     * be false, and asserting it would mean weakening the test until it passed.
     *
     * <p>Two confounds are handled explicitly rather than papered over:
     *
     * <ul>
     *   <li><b>The mouth reflow.</b> The ring is always drawn, and its 12 o'clock
     *       mouth opens or closes with the same switches; when it does, arc ink
     *       appears exactly where the digits were. The strict comparison is
     *       therefore only made while {@code TrioAppearance.Ring.gapUsed} agrees
     *       between the two frames - read from the rule the renderer obeys, since
     *       the pixels are the thing that cannot be trusted across this change.
     *       Where it differs, only "the switch changed the drawing" is claimed.
     *   <li><b>Pieces that gate others.</b> Switching the percentage off also
     *       removes the bolt, because the bolt only exists as a replacement for
     *       it. That is not the table being wrong about the bolt's location, so
     *       the pieces a switch is a precondition for are folded into what it is
     *       allowed to affect.
     * </ul>
     */
    private static void occupancyMatchesInk(Scene scene, String what) {
        final int[] before = zoneMasks(scene, transform(scene.settings), rect(scene));

        for (Part piece : SWITCHABLE) {
            final Scene off = scene.copy();
            switchOff(off.settings, piece);
            if (sameConfig(scene.settings, off.settings)) {
                // Already off in this frame: there is nothing for it to occupy.
                continue;
            }

            if (gapChanges(scene, off)) {
                // The mouth reflowed into the space the piece had. The weak claim
                // is all that is meaningful here.
                ok(what + ": " + piece + " changes the drawing",
                        differs(ink(scene), ink(off)));
                continue;
            }

            // Every third the table holds this piece - or anything it is a
            // precondition for - responsible for.
            final TreeSet<Zone> allowed = zonesFor(scene, piece);
            final TreeSet<Zone> leaked = lostZonesOutside(before,
                    zoneMasks(off, transform(off.settings), rect(off)), allowed);
            eq(what + ": " + piece + " loses ink only in the thirds that name it",
                    join(leaked), "");
        }
        checks++;
    }

    /**
     * The thirds a piece may legitimately remove ink from: the ones the table
     * names it for, plus those of any piece it is a precondition for.
     *
     * <p>The one precondition in this glyph is the bolt's: {@code drawsBolt()} is
     * {@code boltWanted && value}, so the bolt cannot be drawn while the
     * percentage is off, and switching the percentage off must therefore be
     * allowed to take the bolt's ink with it.
     */
    private static TreeSet<Zone> zonesFor(Scene scene, Part piece) {
        final TreeSet<Zone> allowed = new TreeSet<>();
        for (Zone zone : Zone.values()) {
            final Part named = occupantOf(scene, zone);
            if (named == piece || (named != null && sharesZone(zone, named, piece))) {
                allowed.add(zone);
            }
            // A piece that gates the named occupant's ink is allowed to affect the
            // same third.
            if (named != null && gates(piece, named)) {
                allowed.add(zone);
            }
        }
        // A piece's ink is not confined to its own third. The Wi-Fi group is 39.65 units
        // tall and the centre third is 54, so a group fitted to that band reaches across
        // its lower cut line - measured at y 93.25 against a cut at 92. That is a fact
        // about the drawing, not a leak: switching the arcs off legitimately removes ink
        // from both bands. Allow the neighbour a piece genuinely straddles.
        if (piece == Part.WIFI) {
            allowed.add(Zone.CENTRE);
            allowed.add(Zone.BOTTOM);
        }
        return allowed;
    }

    /** Whether switching {@code gate} off can also remove {@code gated}'s ink. */
    private static boolean gates(Part gate, Part gated) {
        // The bolt is drawn only in place of the percentage, so the percentage
        // switch gates it. Nothing else in the glyph gates anything else.
        return gate == Part.VALUE && gated == Part.BOLT;
    }

    /** The thirds that lost ink, excluding any the piece is allowed to affect. */
    private static TreeSet<Zone> lostZonesOutside(int[] before, int[] after,
                                                 TreeSet<Zone> allowed) {
        final TreeSet<Zone> leaked = new TreeSet<>();
        for (Zone zone : Zone.values()) {
            if (before[zone.ordinal()] > after[zone.ordinal()] && !allowed.contains(zone)) {
                leaked.add(zone);
            }
        }
        return leaked;
    }

    private static boolean rect(Scene scene) {
        return scene.settings.trioStyle == Prefs.STYLE_RECT;
    }

    /** The third's occupant per the table, for this frame. */
    private static Part occupantOf(Scene scene, Zone zone) {
        final TrioSettings c = scene.settings;
        return TrioZones.occupant(zone, TrioAppearance.of(c), scene.level, scene.charging,
                scene.wifiLevel, "5G", TWO_SIMS.length);
    }

    /** Whether the ring's 12 o'clock mouth differs between the two frames. */
    private static boolean gapChanges(Scene a, Scene b) {
        // The WIDTH matters as much as the open/closed flag, and the width is what the
        // ring's ink box is now derived from: changing an occupant can reflow the mouth,
        // which moves every arc end and therefore every pixel of the ring. Comparing only
        // the flag would let that reflow count as a leak into an unrelated third.
        return gapShape(a) != gapShape(b);
    }

    /** A single value that changes whenever the mouth's shape does. */
    private static int gapShape(Scene scene) {
        final TrioSettings c = scene.settings;
        if (c.trioStyle == Prefs.STYLE_RECT) {
            return 0;
        }
        final TrioAppearance.Ring r = TrioAppearance.of(c).ring(scene.level, scene.charging,
                scene.wifiLevel, "5G", TWO_SIMS.length);
        if (!r.gapUsed) {
            return 0;
        }
        return 1 + Math.round(r.gapStart() * 100000f) * 31
                + Math.round(r.gapEnd() * 100000f);
    }

    /**
     * Whether the ring's mouth is open in this frame.
     *
     * <p>Read from the rule the renderer obeys rather than from the pixels: the
     * question is what the appearance decided, and the pixel count is exactly the
     * thing that cannot be trusted across this particular change.
     */
    private static boolean gapUsed(Scene scene) {
        final TrioSettings c = scene.settings;
        if (c.trioStyle == Prefs.STYLE_RECT) {
            // No ring, so no mouth to open or close.
            return false;
        }
        final TrioAppearance appearance = TrioAppearance.of(c);
        return appearance.ring(scene.level, scene.charging, scene.wifiLevel, "5G",
                TWO_SIMS.length).gapUsed;
    }

    /**
     * Only the dual reading may widen the mouth, and a free layout must not.
     *
     * <p>The mouth has three widths and the widest one is not a cosmetic choice:
     * the dual reading's top row is {@code DOTS} mirrored about the ring centre,
     * so its outer dots land almost on the arc's own radius and the narrow mouth
     * would run the rounded stroke end into them. Nothing else reaches the arc -
     * a placed component sits in a compact slot box that is scaled to fit.
     *
     * <p>This is here because the regression it catches is invisible to every
     * other check: the mouth still opens, the pieces are still where they belong,
     * and the only symptom is that the visible arc quietly shrinks from 0.650 of
     * the path to 0.516 - a fifth of each side, which reads as "the ring got
     * shorter". Widening on {@code parts != null} is exactly that mistake, so a
     * free ring is asserted against the narrow mouth here.
     */
    private static void onlyTheDualReadingWidensTheMouth() {
        // There is one mouth width now. It used to have three - idle, charging and the
        // wide "dual" one, which existed because the two-row reading reached the arc's
        // radius. Two cards are two placed pieces now, each in its own slot, so nothing
        // reaches the arc and the wide mouth has no reason to exist.
        final TrioSettings placed = cfg(false, s -> {
            s.ringZones = Prefs.decodeLayout("3|1|2");
        }).settings;
        final TrioAppearance a = TrioAppearance.of(placed);
        final TrioAppearance.Ring r = a.ring(79, false, 3, "5G", 2);
        ok("a placed ring opens its mouth", r.gapUsed);
        eq("the mouth is the idle width", String.valueOf(r.gapStart()),
                String.valueOf(TrioGeometry.GAP_START_IDLE));
        eq("the mouth is the idle width at the far end",
                String.valueOf(r.gapEnd()), String.valueOf(TrioGeometry.GAP_END_IDLE));
        // And it is one number regardless of what is placed: the mouth no longer depends
        // on which piece occupies the notch.
        final TrioSettings bare = cfg(false, s -> {
            s.ringZones = Prefs.decodeLayout("6||");
        }).settings;
        eq("a different occupant does not change the mouth width",
                String.valueOf(TrioAppearance.of(bare).ring(79, false, 3, "5G", 2).gapStart()),
                String.valueOf(r.gapStart()));
    }

    /** The zones that lost ink between the two frames. */
    private static TreeSet<Zone> lostZones(int[] before, int[] after) {
        final TreeSet<Zone> lost = new TreeSet<>();
        for (Zone zone : Zone.values()) {
            if (before[zone.ordinal()] > after[zone.ordinal()]) {
                lost.add(zone);
            }
        }
        return lost;
    }

    /**
     * The one place two pieces legitimately share a zone.
     *
     * <p>In the ring the dual reading is the mobile meter drawn as two rows
     * instead of one, so it changes the size of the dots in the bottom band as
     * well as adding a row in the notch. Both are the same meter and the same
     * switch decides how it is arranged, so the extra ink in the bottom band is
     * not a second occupant - it is the meter's shape. The rectangular
     * arrangement has no such pair: its columns are square and the dual reading
     * only changes their levels.
     */
    private static boolean sharesZone(Zone zone, Part named, Part other) {
        // No pair shares a zone any more: a slot holds exactly one piece, so every
        // occupant is named by exactly one component.
        return false;
    }

    private static final Part[] SWITCHABLE = {
        Part.WIFI, Part.MOBILE, Part.VALUE, Part.TYPE, Part.BOLT,
        Part.SIM_1, Part.SIM_2,
    };

    /** Turns one piece's switch off, leaving everything else alone. */
    private static void switchOff(TrioSettings s, Part piece) {
        switch (piece) {
            case WIFI: s.showWifi = false; break;
            case MOBILE: s.showMobile = false; break;
            case VALUE: s.showValue = false; break;
            case TYPE: s.mobileTypeMode = Prefs.MOBILE_TYPE_OFF; break;
            case BOLT: s.showBolt = false; break;
            case SIM_1: s.showSim1 = false; break;
            case SIM_2: s.showSim2 = false; break;
            default: throw new AssertionError("unreachable " + piece);
        }
    }

    private static boolean sameConfig(TrioSettings a, TrioSettings b) {
        return a.showWifi == b.showWifi && a.showMobile == b.showMobile
                && a.showValue == b.showValue && a.mobileTypeMode == b.mobileTypeMode
                && a.dualSim == b.dualSim && a.showBolt == b.showBolt
                && a.showSim1 == b.showSim1 && a.showSim2 == b.showSim2;
    }

    // ------------------------------------------------------------- round trip

    private static void roundTrip(boolean rect) {
        final Scene scene = cfg(rect, s -> { });
        final float[] t = transform(scene.settings);
        for (Zone zone : Zone.values()) {
            final float[] box = TrioZones.boxes(zone, rect)[0];
            final float cx = (box[0] + box[2]) * 0.5f;
            final float cy = (box[1] + box[3]) * 0.5f;
            final float[] v = new float[2];
            ok("toView maps a design point", TrioZones.toView(t, cx, cy, v));
            eq("zoneAt inverts toView for " + zone,
                    String.valueOf(TrioZones.zoneAt(t, rect, v[0], v[1])),
                    String.valueOf(zone));
        }
    }

    /** A point well outside the ink box belongs to no zone. */
    private static void offGlyphMisses() {
        final float[] t = transform(cfg(false, s -> { }).settings);
        ok("a tap far outside the glyph hits no zone",
                TrioZones.zoneAt(t, false, -500f, -500f) == null);
        ok("a tap past the glyph's right edge hits no zone",
                TrioZones.zoneAt(t, false, SIZE + 500f, SIZE * 0.5f) == null);
        ok("a tap above the glyph hits no zone",
                TrioZones.zoneAt(t, false, SIZE * 0.5f, -500f) == null);
        ok("a null transform is refused rather than obeyed",
                TrioZones.zoneAt(null, false, 10f, 10f) == null);
        ok("a zero-scale transform is refused rather than dividing by it",
                TrioZones.zoneAt(new float[] { 0f, 0f, 0f }, false, 10f, 10f) == null);
    }

    /**
     * The transform the settings screen actually uses, inset and all.
     *
     * <p>The scenes above are painted with no inset, because this harness draws the
     * glyph edge to edge. The editor does not: it draws into a box with a margin
     * around it, and its hot zones are resolved through that inset transform. So the
     * inset path is checked here against real ink, which is the only way to catch
     * the failure that would matter - an inset folded in twice, or not at all,
     * putting every hot spot a margin's width away from the piece it names.
     */
    private static void insetMapping() {
        // The inset the preview applies by default, and a deliberately different one
        // so a hard-coded value cannot pass by coincidence.
        for (float inset : new float[] { 0.06f, 0.2f }) {
            for (boolean rect : new boolean[] { false, true }) {
                final Scene scene = cfg(rect, s -> { });
                final float[] t = TrioZones.viewTransform(SIZE, SIZE, inset,
                        TrioAppearance.of(scene.settings));
                ok("viewTransform returns a transform at inset " + inset, t != null);

                // Every painted pixel still resolves to a zone through it, and the
                // zones still tile the box - the same two claims as above, but read
                // through the mapping that has the margin in it.
                final boolean[] ink = inkInset(scene, inset);
                int painted = 0;
                for (int y = 0; y < SIZE; y++) {
                    for (int x = 0; x < SIZE; x++) {
                        if (!ink[y * SIZE + x]) {
                            continue;
                        }
                        painted++;
                        if (TrioZones.zoneAt(t, rect, x + 0.5f, y + 0.5f) == null) {
                            throw new AssertionError("inset " + inset + ": painted ink at "
                                    + x + "," + y + " resolves to no zone");
                        }
                    }
                }
                ok("inset " + inset + " still shows a glyph", painted > 0);

                // The ink has to sit inside the inset box, not merely inside the
                // canvas: a transform that forgot the margin would place the glyph
                // hard against the edges, and this is what catches it. Measured from
                // the painted pixels rather than from a design point, because the fit
                // is taken on the ink box - design (0,0) is not the glyph's corner.
                final float minPad = SIZE * inset;
                int inkMinX = SIZE, inkMinY = SIZE, inkMaxX = -1, inkMaxY = -1;
                for (int y = 0; y < SIZE; y++) {
                    for (int x = 0; x < SIZE; x++) {
                        if (!ink[y * SIZE + x]) {
                            continue;
                        }
                        if (x < inkMinX) { inkMinX = x; }
                        if (x > inkMaxX) { inkMaxX = x; }
                        if (y < inkMinY) { inkMinY = y; }
                        if (y > inkMaxY) { inkMaxY = y; }
                    }
                }
                // The glyph must sit INSIDE the margin, on every side. It no longer
                // reaches all four edges, and that is correct: the fit is
                // min(width / inkW, height / inkH), so whichever axis is tighter fills
                // its span while the other is centred with room to spare. The ring's ink
                // box is 119 x 102 units, so in a square view the height is what binds
                // and the left and right margins stay wider than the pad.
                ok("at inset " + inset + " the ink stays inside the margin (was "
                                + inkMinX + "," + inkMinY + " want >= " + minPad + ")",
                        inkMinX >= minPad - 3f && inkMinY >= minPad - 3f);
                // The far edges get a wider tolerance than the near ones: the ink box is
                // derived from the arc, and the arc's own round caps plus the beads that
                // stand on it reach a little past the nominal extremes - a few pixels at
                // the sizes this harness draws at.
                ok("at inset " + inset + " the ink clears the far edges (was "
                                + inkMaxX + "," + inkMaxY + " limit "
                                + (SIZE - minPad + 3f) + ")",
                        inkMaxX <= SIZE - minPad + 8f && inkMaxY <= SIZE - minPad + 8f);

                // And the round trip still holds through the inset transform.
                final float[] v = new float[2];
                for (Zone zone : Zone.values()) {
                    final float[] box = TrioZones.boxes(zone, rect)[0];
                    final float cx = (box[0] + box[2]) * 0.5f;
                    final float cy = (box[1] + box[3]) * 0.5f;
                    ok("toView maps a design point through the inset transform",
                            TrioZones.toView(t, cx, cy, v));
                    eq("zoneAt inverts the inset transform for " + zone + " at " + inset,
                            String.valueOf(TrioZones.zoneAt(t, rect, v[0], v[1])),
                            String.valueOf(zone));
                }
            }
        }

        // The clamp: an inset beyond the ceiling is brought back to it rather than
        // obeyed, which matches what setInset enforces on the view.
        final TrioAppearance a = TrioAppearance.of(cfg(false, s -> { }).settings);
        final float[] atCeiling = TrioZones.viewTransform(SIZE, SIZE,
                TrioZones.MAX_INSET, a);
        final float[] beyond = TrioZones.viewTransform(SIZE, SIZE, 5f, a);
        ok("an inset beyond the ceiling is clamped, not obeyed",
                atCeiling != null && beyond != null
                        && Math.abs(atCeiling[0] - beyond[0]) < 0.0001f
                        && Math.abs(atCeiling[1] - beyond[1]) < 0.0001f);
        ok("a negative inset is clamped to none",
                TrioZones.viewTransform(SIZE, SIZE, -1f, a) != null);
        ok("an empty box is refused",
                TrioZones.viewTransform(0, SIZE, 0.1f, a) == null);
        ok("a null appearance is refused",
                TrioZones.viewTransform(SIZE, SIZE, 0.1f, null) == null);
    }

    /**
     * The rectangular arrangement's centre column must contain the text drawn in it.
     *
     * <p>This exists because the generic checks above could not catch the defect it
     * pins. They render each configuration at a level, and a percentage is
     * <em>text</em>: "79" is narrower than "100". At the level they used, a seam
     * placed a few design units inside the renderer's own text allowance looked
     * fine - but at 100% with a large value size the right-hand stroke of "100"
     * landed outside the centre column, so tapping the number hit the mobile column
     * and turned the signal meter off instead of flipping the percentage.
     *
     * <p>So the claim here is the direct one, at the widest reading the settings can
     * produce: every pixel of the percentage's ink must resolve to CENTRE. It is
     * deliberately independent of which levels the sweep uses, and it is stated
     * against ink rather than against a constant, so a seam and a text allowance
     * that drift apart fail here rather than in the user's hand.
     */
    private static void rectCentreHoldsItsText() {
        final int rect = Prefs.STYLE_RECT;
        // Every value size the slider offers, at the three-digit reading, because a
        // three-digit number is the widest case and the one that reaches furthest.
        for (int valueSize = Prefs.MIN_VALUE_SIZE; valueSize <= Prefs.MAX_VALUE_SIZE;
                valueSize++) {
            final TrioSettings s = TrioSettings.defaults();
            s.trioStyle = rect;
            s.valueSize = valueSize;
            // Value only, so the diff isolates the percentage's own ink.
            s.showWifi = false;
            s.showMobile = false;
            s.mobileTypeMode = Prefs.MOBILE_TYPE_OFF;
            s.showValue = true;

            final Scene on = new Scene(s, false, 100);
            final TrioSettings off = s.copy();
            off.showValue = false;
            final Scene offScene = new Scene(off, false, 100);

            final boolean[] withValue = ink(on);
            final boolean[] without = ink(offScene);
            final float[] t = transform(on.settings);

            int outside = 0;
            int minX = SIZE;
            int maxX = -1;
            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++) {
                    final int i = y * SIZE + x;
                    if (!withValue[i] || without[i]) {
                        continue;
                    }
                    if (x < minX) { minX = x; }
                    if (x > maxX) { maxX = x; }
                    if (TrioZones.zoneAt(t, true, x + 0.5f, y + 0.5f) != Zone.CENTRE) {
                        outside++;
                    }
                }
            }

            ok("rect valueSize=" + valueSize + " draws a three-digit reading",
                    maxX >= minX);
            eq("rect valueSize=" + valueSize
                            + ": every pixel of \"100\" resolves to the centre column",
                    String.valueOf(outside), "0");
        }

        // And the mirror claim, so the fix cannot be "widen the centre column until
        // it swallows the dots": the dot columns' ink must resolve to BOTTOM.
        for (int level : LEVELS) {
            final TrioSettings s = TrioSettings.defaults();
            s.trioStyle = rect;
            s.showValue = false;
            s.showWifi = false;
            s.mobileTypeMode = Prefs.MOBILE_TYPE_OFF;
            s.showMobile = true;
            final Scene scene = new Scene(s, false, level);
            final TrioSettings off = s.copy();
            off.showMobile = false;
            final boolean[] withDots = ink(scene);
            final boolean[] without = ink(new Scene(off, false, level));
            final float[] t = transform(scene.settings);

            int wrong = 0;
            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++) {
                    final int i = y * SIZE + x;
                    if (!withDots[i] || without[i]) {
                        continue;
                    }
                    if (TrioZones.zoneAt(t, true, x + 0.5f, y + 0.5f) != Zone.BOTTOM) {
                        wrong++;
                    }
                }
            }
            eq("rect dot columns at level " + level + " resolve to the bottom zone",
                    String.valueOf(wrong), "0");
        }
    }

    /**
     * Every switch changes the drawing, or the editor would offer a hot spot that
     * does nothing.
     *
     * <p>Each piece is measured in a frame where it CAN appear, which is the whole
     * point: the type label never draws while the arcs do (they share the
     * rectangle's top slot and the ring's centre), and the bolt never draws
     * without the percentage it replaces. Measuring them all against the shipped
     * default would report the type label and the dual reading as dead switches,
     * which says nothing about whether the switch works.
     */
    private static void everySwitchMovesInk() {
        for (boolean rect : new boolean[] { false, true }) {
            forEachSwitchable(rect);
        }
    }

    private static void forEachSwitchable(boolean rect) {
        // A fresh install starts BLANK, so every one of these frames has to place the piece
        // it is about - a switch with nothing placed draws nothing, which is the model rather
        // than a defect. The ring names a zone per case; the rectangle keeps its own layout
        // and only needs the switch.
        movesInk(rect, Part.WIFI, s -> placeRing(s, rect, Prefs.PART_WIFI));
        movesInk(rect, Part.VALUE, s -> placeRing(s, rect, Prefs.PART_VALUE));
        // The type is drawn where a slot names it, so its frame has to place it - the
        // shipped layout holds the arcs in the middle, where the label would otherwise go.
        movesInk(rect, Part.TYPE, s -> {
            s.showWifi = false;
            if (!rect) s.ringZones = Prefs.decodeLayout("3|5|2");
        });
        movesInk(rect, Part.MOBILE, s -> placeRing(s, rect, Prefs.PART_MOBILE));
        // The bolt replaces the percentage, so the frame draws that percentage - and in the
        // ring it also needs a slot of its own, because the slots are the layout.
        movesInkCharging(rect, Part.BOLT, s -> {
            s.showValue = true;
            if (!rect) s.ringZones = Prefs.decodeLayout("4|1|2");
        });
        // The two per-card rows exist only as placed pieces, and only the ring has
        // slots - so their frame is a free ring, and only the ring is asked. In the
        // rectangular arrangement these switches have nothing to act on by design,
        // which is a fact this harness states rather than one it invents.
        if (!rect) {
            movesInk(false, Part.SIM_1, s -> placedRow(s, Prefs.PART_SIM_1));
            movesInk(false, Part.SIM_2, s -> placedRow(s, Prefs.PART_SIM_2));
        }
    }

    /**
     * Places one ring piece in the middle zone, which is where the switch cases can see it.
     *
     * <p>A no-op for the rectangle: that arrangement derives what it draws from the switches,
     * so it needs no layout. Only the ring is slot-driven.
     */
    private static void placeRing(TrioSettings s, boolean rect, int part) {
        if (!rect) s.ringZones = Prefs.decodeLayout("|" + part + "|");
    }

    /** Gives a per-card row a slot, which is what makes its switch meaningful. */
    private static void placedRow(TrioSettings s, int part) {
        s.ringZones = Prefs.decodeLayout(part + "|3|2");
    }

    // ------------------------------------------------------ the assignment solver
    //
    // The drawing page no longer flips a switch per zone; it offers a list of
    // pieces per zone and writes whatever combination puts the chosen one there.
    // That combination is solved in TrioZones.assign by enumerating switch
    // settings and asking the REAL arbitration where each lands. These checks hold
    // it to four properties: it places what it offers, its edit is the smallest
    // one, its rejections are facts, and it accounts for the runtime state that
    // decides whether a piece exists at all.

    /** What a snapshot really puts in a zone, on one frame. */
    private static Part where(TrioSettings s, Zone zone, boolean charging, int sims) {
        return where(s, zone, charging, sims, 3);
    }

    private static Part where(TrioSettings s, Zone zone, boolean charging, int sims, int wifi) {
        final TrioAppearance a = TrioAppearance.of(s);
        return a.rect
                ? TrioZones.occupant(zone, a, a.rect(ASSIGN_LEVEL, charging, wifi, "5G", sims))
                : TrioZones.occupant(zone, a, a.ring(ASSIGN_LEVEL, charging, wifi, "5G", sims));
    }

    private static final int ASSIGN_LEVEL = 79;

    /** Every answer the solver gives must really place the piece it was asked for. */
    private static void assignmentSolverPlacesWhatItOffers() {
        for (boolean rect : new boolean[] { false, true }) {
            final TrioSettings cur = cfg(rect, s -> { }).settings;
            for (Zone zone : Zone.values()) {
                for (Part want : Part.values()) {
                    final TrioZones.Assignment as = TrioZones.assign(zone, rect, want, cur, 2);
                    if (as == null) {
                        // Checked by the rejection test; nothing to place here.
                        continue;
                    }
                    // The bolt exists only while charging, so it is judged on that
                    // frame; everything else is judged idle.
                    final boolean charging = want == Part.BOLT;
                    eq((rect ? "rect/" : "ring/") + zone + " places " + want,
                            String.valueOf(where(as.settings, zone, charging, 2)),
                            String.valueOf(want));
                }
            }
            // The list the page offers must be exactly the solvable set - a piece
            // offered but unsolvable is a dead menu entry, and a piece solvable but
            // unlisted is a capability with no way to reach it.
            for (Zone zone : Zone.values()) {
                final java.util.List<Part> offered = TrioZones.choices(zone, rect, cur, 2);
                for (Part p : Part.values()) {
                    final boolean solvable = TrioZones.assign(zone, rect, p, cur, 2) != null;
                    ok((rect ? "rect/" : "ring/") + zone + "/" + p
                                    + " is offered exactly when it is solvable",
                            solvable == offered.contains(p));
                }
            }
        }
    }

    /**
     * The chosen edit must be a smallest one.
     *
     * <p>Otherwise the page would move switches the user never asked about - moving
     * the top slot from the arcs to the percentage would also silently move the
     * signal meter. Checked by exhaustive search: no combination strictly closer to
     * the current settings may achieve the same request.
     */
    private static void assignmentSolverMakesTheSmallestEdit() {
        for (boolean rect : new boolean[] { false, true }) {
            final TrioSettings cur = cfg(rect, s -> { }).settings;
            for (Zone zone : Zone.values()) {
                for (Part want : Part.values()) {
                    final TrioZones.Assignment as = TrioZones.assign(zone, rect, want, cur, 2);
                    if (as == null || as.changes == 0) {
                        continue;
                    }
                    for (int bits = 0; bits < 128; bits++) {
                        for (int typeMode = 0; typeMode <= Prefs.MOBILE_TYPE_IN_RING;
                                typeMode++) {
                            final TrioSettings s = cur.copy();
                            s.showWifi = (bits & 1) != 0;
                            s.showValue = (bits & 2) != 0;
                            s.valueCentred = (bits & 4) != 0;
                            s.showBolt = (bits & 8) != 0;
                            s.dualSim = (bits & 16) != 0;
                            s.showMobile = (bits & 32) != 0;
                            s.mobileTypeMode = typeMode;
                            if (switchDistance(cur, s) >= as.changes) {
                                continue;
                            }
                            for (boolean charging : new boolean[] { false, true }) {
                                if (where(s, zone, charging, 2) == want) {
                                    throw new AssertionError((rect ? "rect/" : "ring/")
                                            + zone + "/" + want + " returned " + as.changes
                                            + " changes, but a smaller edit also works: "
                                            + describe(s));
                                }
                            }
                        }
                    }
                }
            }
        }
        checks++;
    }

    /**
     * A rejection must be a fact about the renderer, not a limitation of the search.
     *
     * <p>The solver searches a restricted set for speed, so it could in principle
     * refuse something reachable. This exhausts the whole switch space - 128 x 3 - on
     * both frames for every refused piece and fails if any combination would have
     * worked.
     */
    private static void assignmentRejectionsAreReal() {
        for (boolean rect : new boolean[] { false, true }) {
            final TrioSettings cur = cfg(rect, s -> { }).settings;
            for (Zone zone : Zone.values()) {
                for (Part want : Part.values()) {
                    if (TrioZones.assign(zone, rect, want, cur, 2) != null) {
                        continue;
                    }
                    for (int bits = 0; bits < 128; bits++) {
                        for (int typeMode = 0; typeMode <= Prefs.MOBILE_TYPE_IN_RING;
                                typeMode++) {
                            final TrioSettings s = cur.copy();
                            s.showWifi = (bits & 1) != 0;
                            s.showValue = (bits & 2) != 0;
                            s.valueCentred = (bits & 4) != 0;
                            s.showBolt = (bits & 8) != 0;
                            s.dualSim = (bits & 16) != 0;
                            s.showMobile = (bits & 32) != 0;
                            s.mobileTypeMode = typeMode;
                            for (boolean charging : new boolean[] { false, true }) {
                                if (where(s, zone, charging, 2) == want) {
                                    throw new AssertionError((rect ? "rect/" : "ring/")
                                            + zone + "/" + want + " was called unreachable,"
                                            + " but " + describe(s) + " (charging=" + charging
                                            + ") puts it there");
                                }
                            }
                        }
                    }
                }
            }
            ok((rect ? "rect/" : "ring/") + " every rejection survives the full search", true);
        }
    }

    /**
     * The solver must account for the runtime state that decides whether a piece
     * exists, because two pieces cannot be placed without it.
     *
     * <p>The bolt exists only while charging, and the dual rows need two SIMs with a
     * reading. Both were reported unreachable by the first version of the solver,
     * which asked an idle, single-SIM frame to show them.
     */
    private static void assignmentNeedsTheRuntimeState() {
        for (boolean rect : new boolean[] { false, true }) {
            final TrioSettings cur = cfg(rect, s -> { }).settings;

            // The bolt exists only while charging, so offering it as the notch's content
            // when it cannot appear would be a lie about the frame.
            final TrioZones.Assignment bolt = TrioZones.assign(Zone.TOP, rect, Part.BOLT, cur, 2);
            ok((rect ? "rect/" : "ring/") + " the bolt is placeable", bolt != null);
            eq((rect ? "rect/" : "ring/") + " the bolt really lands while charging",
                    String.valueOf(where(bolt.settings, Zone.TOP, true, 2)), "BOLT");
            ok((rect ? "rect/" : "ring/") + " the bolt is not drawn while idle",
                    where(bolt.settings, Zone.TOP, false, 2) != Part.BOLT);
        }
    }

    /**
     * Emptying a zone must leave it empty on every frame, not just the idle one.
     *
     * <p>A notch that holds nothing while idle but a bolt while charging is not
     * empty - the bolt arrives the moment the phone is plugged in. The first version
     * of the solver judged the request on one frame and got this wrong.
     */
    private static void assignmentCanEmptyAZone() {
        for (boolean rect : new boolean[] { false, true }) {
          for (boolean fallbackType : new boolean[] { false, true }) {
            final TrioSettings cur = cfg(rect, s -> {
                if (fallbackType) {
                    s.showValue = false;
                    s.mobileTypeMode = Prefs.MOBILE_TYPE_IN_RING;
                }
            }).settings;
            for (Zone zone : Zone.values()) {
                final TrioZones.Assignment as = TrioZones.assign(zone, rect, null, cur, 2);
                ok((rect ? "rect/" : "ring/") + " " + zone + " can be emptied", as != null);
                for (boolean charging : new boolean[] { false, true }) {
                  for (int wifi : new int[] { 3, -1 }) {
                    ok((rect ? "rect/" : "ring/") + " " + zone + " emptied is empty"
                                    + " (charging=" + charging + ", wifi=" + wifi + ")",
                            where(as.settings, zone, charging, 2, wifi) == null);
                  }
                }
            }
            ok((rect ? "rect/" : "ring/") + " the page can offer \"nothing here\"", true);
          }
        }
    }

    /** A named Wi-Fi occupant must have ink in this runtime frame, not just a switch. */
    private static void wifiOccupantNeedsPixels() {
        final Scene base = cfg(false, s -> {
            s.showValue = false;
            s.mobileTypeMode = Prefs.MOBILE_TYPE_OFF;
            s.dualSim = false;
            // The arcs are placed, because a fresh install is blank: this case is about a
            // live Wi-Fi READING, not about whether anything is placed.
            s.ringZones = Prefs.decodeLayout("|1|");
        });
        for (int level : new int[] { -1, 0 }) {
            final Scene absent = base.atWifi(level);
            final Scene off = absent.copy();
            off.settings.showWifi = false;
            ok("Wi-Fi without ink has no middle occupant " + level,
                    occupantOf(absent, Zone.CENTRE) == null);
            ok("Wi-Fi without ink contributes no pixels " + level,
                    !differs(ink(absent), ink(off)));
        }
        for (int level : new int[] { 1, 3 }) {
            final Scene present = base.atWifi(level);
            final Scene disabled = present.copy();
            disabled.settings.showWifi = false;
            ok("live Wi-Fi names its middle occupant " + level,
                    occupantOf(present, Zone.CENTRE) == Part.WIFI);
            ok("live Wi-Fi contributes pixels " + level,
                    differs(ink(present), ink(disabled)));
        }
    }

    /** How many switches differ between two snapshots. */
    private static int switchDistance(TrioSettings a, TrioSettings b) {
        int n = 0;
        if (a.showWifi != b.showWifi) { n++; }
        if (a.showMobile != b.showMobile) { n++; }
        if (a.showValue != b.showValue) { n++; }
        if (a.showBolt != b.showBolt) { n++; }
        if (a.dualSim != b.dualSim) { n++; }
        if (a.valueCentred != b.valueCentred) { n++; }
        if (a.mobileTypeMode != b.mobileTypeMode) { n++; }
        return n;
    }

    private static String describe(TrioSettings s) {
        return "wifi=" + (s.showWifi ? 1 : 0) + " value=" + (s.showValue ? 1 : 0)
                + " centre=" + (s.valueCentred ? 1 : 0) + " bolt=" + (s.showBolt ? 1 : 0)
                + " dual=" + (s.dualSim ? 1 : 0) + " type=" + s.mobileTypeMode
                + " mobile=" + (s.showMobile ? 1 : 0);
    }

    private static void movesInk(boolean rect, Part piece,
                                 java.util.function.Consumer<TrioSettings> base) {
        final Scene on = cfg(rect, base).copy();
        switchOn(on.settings, piece);
        final Scene off = on.copy();
        switchOff(off.settings, piece);
        ok((rect ? "rect/" : "ring/") + piece + " changes the drawing when switched",
                differs(ink(on), ink(off)));
    }

    private static void movesInkCharging(boolean rect, Part piece,
                                         java.util.function.Consumer<TrioSettings> base) {
        final Scene on = cfg(rect, base).charging().copy();
        switchOn(on.settings, piece);
        final Scene off = on.copy();
        switchOff(off.settings, piece);
        ok((rect ? "rect/" : "ring/") + piece + " changes the drawing while charging",
                differs(ink(on), ink(off)));
    }

    /** Turns one piece's switch on, leaving everything else alone. */
    private static void switchOn(TrioSettings s, Part piece) {
        switch (piece) {
            case WIFI: s.showWifi = true; break;
            case MOBILE: s.showMobile = true; break;
            case VALUE: s.showValue = true; break;
            case TYPE: s.mobileTypeMode = Prefs.MOBILE_TYPE_IN_RING; break;
            case BOLT: s.showBolt = true; break;
            case SIM_1: s.showSim1 = true; break;
            case SIM_2: s.showSim2 = true; break;
            default: throw new AssertionError("unreachable " + piece);
        }
    }

    /** A tap on any zone always names a piece, and never null. */
    /**
     * A zone is never offered a piece that has no switch behind it.
     *
     * <p>This replaces the older "every zone always names a piece to switch" check,
     * which belonged to the tap-to-flip model. The page's per-zone picker needs a
     * different guarantee: every entry it offers must be placeable, which is checked
     * against the solver in assignmentSolverPlacesWhatItOffers. What is left here is
     * the constraint that the emptiness entry is real - a zone must be reachable with
     * nothing in it - which is checked in assignmentCanEmptyAZone.
     */
    private static void everyZoneNamesAPiece() {
        for (boolean rect : new boolean[] { false, true }) {
            final Scene scene = cfg(rect, s -> { });
            final TrioAppearance a = TrioAppearance.of(scene.settings);
            for (Zone zone : Zone.values()) {
                // Whatever the frame, the occupancy answer is about a piece the
                // renderer knows - never a null enum smuggled in as "something".
                for (int level : LEVELS) {
                    final Part visible = TrioZones.occupant(zone, a, level, false, 3, "5G", 0);
                    ok(rect + "/" + zone + "/L" + level + " names a known piece or nothing",
                            visible == null || visible.name().length() > 0);
                }
            }
        }
    }

    // ------------------------------------------------------------- rendering

    /** The per-zone pixel masks of a scene, indexed by {@code Zone.ordinal()}. */
    private static int[] zoneMasks(Scene scene, float[] t, boolean rect) {
        final boolean[] ink = ink(scene);
        final int[] masks = new int[Zone.values().length];
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                if (!ink[y * SIZE + x]) {
                    continue;
                }
                final Zone zone = TrioZones.zoneAt(t, rect, x + 0.5f, y + 0.5f);
                // coversAllInk already reports stray ink; skipping it here keeps
                // this method from throwing for a reason it does not own.
                if (zone != null) {
                    masks[zone.ordinal()]++;
                }
            }
        }
        return masks;
    }

    private static boolean lostInZone(int[] before, int[] after, Zone zone) {
        return before[zone.ordinal()] > after[zone.ordinal()];
    }

    /** The zone names in a set, comma-joined and sorted, for assertion messages. */
    private static String join(TreeSet<Zone> zones) {
        final StringBuilder sb = new StringBuilder();
        for (Zone z : zones) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(z.name());
        }
        return sb.toString();
    }

    /**
     * The dot-size setting moves the pixels, and its ceiling keeps the beads inside the
     * ink box.
     *
     * <p>Two claims a geometry assertion cannot make on its own. The first is that the
     * value reaches the canvas at all - a slider wired into the settings but not into
     * {@code drawArcDotRow} would pass every unit check and change nothing on screen. The
     * second is the one that matters for the user's phone: the beads ride the ring's own
     * arc, so growing them pushes ink toward the edge of the box that fits the glyph to its
     * view. Past that edge the host clips, and the glyph would visibly change size as the
     * slider moved. So the glyph's own ink must stay inside the box at the largest setting.
     */
    private static void dotSizeReachesThePixels() {
        // The reference look: one card's row in each opening, nothing else competing for
        // the body, so the ink that changes is the dots' own.
        final TrioSettings base = cfg(false, s -> {
            s.showValue = false;
            s.showWifi = false;
            s.showBolt = false;
            s.showMobile = true;
            s.showSim1 = true;
            s.showSim2 = true;
            s.mobileTypeMode = Prefs.MOBILE_TYPE_OFF;
            // SIM 1 in the top opening, SIM 2 in the bottom: both rows, both caps.
            s.ringZones = Prefs.decodeLayout("7||8");
        }).settings;

        final TrioSettings small = base.copy();
        small.dotScale = Prefs.MIN_DOT_SCALE;
        final TrioSettings normal = base.copy();
        normal.dotScale = Prefs.DEF_DOT_SCALE;
        final TrioSettings large = base.copy();
        large.dotScale = Prefs.MAX_DOT_SCALE;

        final boolean[] smallInk = ink(new Scene(small, false));
        final boolean[] normalInk = ink(new Scene(normal, false));
        final boolean[] largeInk = ink(new Scene(large, false));

        ok("the smallest dot size draws less ink than the reference",
                count(smallInk) < count(normalInk));
        ok("the largest dot size draws more ink than the reference",
                count(largeInk) > count(normalInk));

        // The ceiling, stated against pixels rather than against the radius: at the largest
        // setting the glyph must still fit the box the transform was built from.
        final float[] t = transform(large);
        final float minX = (0f - t[1]) / t[0];
        final float minY = (0f - t[2]) / t[0];
        final float maxX = (SIZE - t[1]) / t[0];
        final float maxY = (SIZE - t[2]) / t[0];
        ok("the largest dot size still centres the box on the ring",
                Math.abs((minX + maxX) * 0.5f - TrioGeometry.B_CX) < 0.5f
                        && Math.abs((minY + maxY) * 0.5f - TrioGeometry.B_CY) < 0.5f);
        // Every painted pixel has to fall inside the design box; one outside is one the
        // host would clip on a real status bar.
        int outside = 0;
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                if (!largeInk[y * SIZE + x]) {
                    continue;
                }
                final float dx = (x + 0.5f - t[1]) / t[0];
                final float dy = (y + 0.5f - t[2]) / t[0];
                if (dx < -0.5f || dy < -0.5f || dx > TrioGeometry.BOX + 0.5f
                        || dy > TrioGeometry.BOX + 0.5f) {
                    outside++;
                }
            }
        }
        eq("the largest dot size keeps every pixel inside the design box",
                String.valueOf(outside), "0");
    }

    /** How many pixels carry ink. */
    private static int count(boolean[] set) {
        int n = 0;
        for (boolean b : set) {
            if (b) {
                n++;
            }
        }
        return n;
    }

    /** True when the two renders differ anywhere on the canvas. */
    private static boolean differs(boolean[] a, boolean[] b) {
        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) {
                return true;
            }
        }
        return false;
    }

    /** Paints the glyph and returns the set of pixels it touched. */
    private static boolean[] ink(Scene scene) {
        renders++;
        final TrioSettings cfg = scene.settings;
        final BufferedImage img = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        final java.awt.Graphics2D g = img.createGraphics();
        final Canvas canvas = new Canvas(g);
        TrioRenderer.drawInto(canvas, SIZE, SIZE, scene.level, scene.charging, false, false, false,
                scene.wifiLevel, 4, TWO_SIMS,
                cfg.mobileTypeMode == Prefs.MOBILE_TYPE_IN_RING ? "5G" : "",
                0xFF000000, cfg, true);
        g.dispose();
        return painted(img);
    }

    /**
     * The same, drawn the way {@code TrioPreviewView.onDraw} draws: into an inset
     * box, with the canvas translated by the margin first.
     *
     * <p>It mirrors that method's own arithmetic rather than calling it, because the
     * view cannot be built here - and mirroring it is the point: if the view's inset
     * handling and {@link TrioZones#viewTransform} disagree, the ink lands one place
     * and the hot zones another, and this is what notices.
     */
    private static boolean[] inkInset(Scene scene, float inset) {
        renders++;
        final TrioSettings cfg = scene.settings;
        final BufferedImage img = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        final java.awt.Graphics2D g = img.createGraphics();
        final Canvas canvas = new Canvas(g);

        final float pad = SIZE * inset;
        final int inner = Math.round(SIZE - 2f * pad);
        canvas.translate(pad, pad);
        TrioRenderer.drawInto(canvas, inner, inner, scene.level, scene.charging, false, false, false,
                scene.wifiLevel, 4, TWO_SIMS,
                cfg.mobileTypeMode == Prefs.MOBILE_TYPE_IN_RING ? "5G" : "",
                0xFF000000, cfg, true);
        g.dispose();
        return painted(img);
    }

    /** The set of pixels that carry any ink. */
    private static boolean[] painted(BufferedImage img) {
        final boolean[] set = new boolean[SIZE * SIZE];
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                set[y * SIZE + x] = (img.getRGB(x, y) >>> 24) != 0;
            }
        }
        return set;
    }

    private static float[] transform(TrioSettings cfg) {
        final float[] t = new float[3];
        if (!TrioRenderer.designTransform(SIZE, SIZE, TrioAppearance.of(cfg), t)) {
            throw new AssertionError("designTransform refused a valid canvas");
        }
        return t;
    }

    // ------------------------------------------------------------- assertions

    private static void ok(String what, boolean held) {
        checks++;
        if (!held) {
            throw new AssertionError(what);
        }
    }

    private static void eq(String what, String got, String want) {
        checks++;
        if (!got.equals(want)) {
            throw new AssertionError(what + ": got <" + got + "> want <" + want + ">");
        }
    }
}
'@

[System.IO.File]::WriteAllText(
    (Join-Path $src 'io\github\fengwenhua\colorduo\DrawingZonesTest.java'),
    $probe, (New-Object System.Text.UTF8Encoding($false)))

# --- compile and run -----------------------------------------------------------
$shims = Join-Path $root 'tests\shims'
if (!(Test-Path (Join-Path $shims 'android\graphics\Canvas.java'))) {
    throw "the desktop shims are missing: $shims (see docs/DEVELOPMENT.md, the test section)"
}
$preview = Get-ChildItem -Recurse -Filter *.java $shims | ForEach-Object { $_.FullName }
$shared = @(
    (Join-Path $trio 'Prefs.java'),
    (Join-Path $trio 'ChargeLayout.java'),
    (Join-Path $trio 'SettingsSchema.java'),
    (Join-Path $trio 'TrioSettings.java'),
    (Join-Path $trio 'TrioGeometry.java'),
    (Join-Path $trio 'TrioAppearance.java'),
    (Join-Path $trio 'TrioZones.java'),
    (Join-Path $trio 'TrioRenderer.java'))
$sources = $preview + $shared + @(Get-ChildItem -Recurse -Filter *.java $src |
    ForEach-Object { $_.FullName })

& $javac -nowarn -d $classes -encoding UTF-8 $sources
if ($LASTEXITCODE) { throw 'Compilation failed' }
& $java -Xmx512m -cp $classes io.github.fengwenhua.colorduo.DrawingZonesTest
if ($LASTEXITCODE) { throw 'Test failed' }
