# The capsule arrangement: the outline is the gauge, the dots interrupt it, and
# the two interior readings share it with the dot rows.
#
# What this covers that the other suites cannot: every one of them renders the
# glyph into a square host, where a shape that overflows vertically is invisible
# because the ink box is fitted by whichever axis binds. The capsule's real host
# is wider than it is tall, so it is fitted by WIDTH, and anything taller than the
# outline runs off the top and bottom of the host. That failure mode only appears
# at the real aspect ratio, which is what this renders at.
#
# The host is produced by the module rather than by MIUI: the bar measures the
# battery icon to 28dp (the width of MIUI's own narrow battery), and
# TrioGeometry.capHostWidthPx answers with the width the capsule's aspect ratio
# needs for the height on offer. Both sizes are rendered here, because the stock
# one is what a regression would fall back to and the widened one is what ships.
#
# ASCII only on purpose: PowerShell 5.1 reads BOM-less files as ANSI, so a script
# without a BOM must not contain non-ASCII bytes.
#
# Usage:  powershell -File tests\capsule.ps1

$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$jdk = Join-Path $root '.tools\jdk\jdk-21.0.12.1+1\bin'
$javac = Join-Path $jdk 'javac.exe'
$java = Join-Path $jdk 'java.exe'
if (!(Test-Path $javac)) { $javac = 'javac'; $java = 'java' }

$trio = Join-Path $root 'app\src\main\java\io\github\fengwenhua\colorduo'
$geomPath = Join-Path $trio 'TrioGeometry.java'
$prefsPath = Join-Path $trio 'Prefs.java'
$rendererPath = Join-Path $trio 'TrioRenderer.java'
$settingsPath = Join-Path $trio 'TrioSettings.java'

$geom = [System.IO.File]::ReadAllText($geomPath)
$prefs = [System.IO.File]::ReadAllText($prefsPath)
$renderer = [System.IO.File]::ReadAllText($rendererPath)
$settings = [System.IO.File]::ReadAllText($settingsPath)
$hooks = [System.IO.File]::ReadAllText((Join-Path $trio 'TrioHooks.java'))

# --- source contract pins ------------------------------------------------------
# The statements the pixel test below cannot see, because they are about numbers
# agreeing rather than about ink appearing.

# The host is MEASURED to the capsule's aspect ratio, not left at MIUI's stock
# 28dp. This is the one thing that decides whether the glyph is a wide capsule or
# a squashed pill, and no pixel test in a square host can see it: the ink box is
# fitted by whichever axis binds, so a too-narrow host just draws a smaller
# capsule and every "it fits" assertion still holds. The device is where it shows
# - 84x46px inside a 60px-tall bar - which is why the pin is here.
if (![regex]::IsMatch($hooks,
        'Refl\.method\(icon, "onMeasure", int\.class, int\.class\)')) {
    throw 'the capsule no longer hooks the icon view''s onMeasure'
}
if (![regex]::IsMatch($hooks, 'TrioGeometry\.capHostWidthPx\(height\)')) {
    throw 'the icon view no longer asks the capsule for its host width'
}
# Only the capsule may be re-measured. A pass-through for the other two keeps
# MIUI's own measurement standing, so this cannot move the ring or the rectangle.
if (![regex]::IsMatch($hooks,
        'if \(!a\.glyph \|\| !a\.capsule\) \{\s*\r?\n\s*return result;')) {
    throw 'the icon re-measure is no longer gated on the capsule alone'
}
# Resolved on View, not on the icon class: Refl.method uses getDeclaredMethod,
# which does not walk up the hierarchy, so asking the subclass returns null and
# the widening silently never happens.
if (![regex]::IsMatch($hooks,
        'Refl\.method\(View\.class, "setMeasuredDimension",\s*\r?\n\s*int\.class, int\.class\)')) {
    throw 'setMeasuredDimension is no longer resolved on View itself'
}
# The width must NOT be scaled by the overall-size setting: that multiplier is
# already applied to the drawing scale, so folding it in here as well would shrink
# the ink by the square of the setting.
if ([regex]::IsMatch($hooks, 'capHostWidthPx[\s\S]{0,500}glyphFit')) {
    throw 'the host width is scaled by the overall-size setting as well as the drawing'
}

# The dot rows are drawn on the outline's own path, not on the stroke's inner
# edge. The stroke straddles its path, so CAP_TOP IS the line's centre; adding
# half a stroke - the obvious-looking thing - hangs the dots below the line they
# are meant to be set into, which reads as beads under the outline rather than as
# dots interrupting it.
if (![regex]::IsMatch($geom,
        'static float capDotTopY\(\) \{\s*\r?\n\s*return CAP_TOP;')) {
    throw 'the top dot row no longer sits on the outline''s own centreline'
}
if (![regex]::IsMatch($geom,
        'static float capDotBottomY\(\) \{\s*\r?\n\s*return CAP_BOTTOM;')) {
    throw 'the bottom dot row no longer sits on the outline''s own centreline'
}

# Both interior readings are sized in one place, so the time and the percentage
# cannot disagree about how big they are. A second fitSize call with its own
# numbers is how that drifts.
$sizes = [regex]::Matches($renderer, 'capsuleTextSize\(')
if ($sizes.Count -lt 4) {
    throw "the capsule readings no longer share one size rule ($($sizes.Count) references)"
}

# The two interior readings are bounded by the height the dot rows leave, not
# only by the width the slot offers. That second bound is the one the other two
# arrangements never needed, and dropping it sends the digits over the outline.
if (![regex]::IsMatch($renderer, 'TrioGeometry\.capTextMaxSize\(stroke\)')) {
    throw 'the capsule readings are no longer bounded by the interior height'
}

# Capsule retains its stored ID while retired styles return to the ring.
$maxStyle = [regex]::Match($prefs, 'static final int MAX_TRIO_STYLE = STYLE_(\w+);')
if (!$maxStyle.Success -or $maxStyle.Groups[1].Value -ne 'CAPSULE') {
    throw 'MAX_TRIO_STYLE no longer reaches the capsule'
}
if (![regex]::IsMatch($settings,
        'Prefs\.normalizeTrioStyle\(p\.getInt\(Prefs\.KEY_TRIO_STYLE, Prefs\.DEF_TRIO_STYLE\)\)')) {
    throw 'the stored style no longer migrates retired configurations'
}

# Dropdown indices map explicitly to the non-contiguous storage IDs 0 and 2.
$screen = [System.IO.File]::ReadAllText((Join-Path $trio 'ui\ContentControls.kt'))
$styleList = [regex]::Match($screen,
    '(?s)ContentControl.Style ->.*?listOf\(R.string.trio_style_ring, R.string.trio_style_capsule\)').Value
if (!$styleList) { throw 'could not read the shared style dropdown' }
$iRing = $styleList.IndexOf('trio_style_ring')
$iRect = $styleList.IndexOf('trio_style_rect')
$iCaps = $styleList.IndexOf('trio_style_capsule')
if ($iRing -lt 0) { throw 'the style dropdown no longer offers trio_style_ring' }
if ($iRect -ge 0) { throw 'the style dropdown still offers the retired rectangular style' }
if ($iCaps -lt 0) { throw 'the style dropdown no longer offers trio_style_capsule' }
if (!($iRing -lt $iCaps) -or !$screen.Contains('setTrioStyle(styles[index])')) {
    throw 'the style dropdown no longer maps its indices to stored style IDs'
}

# --- generated probe -----------------------------------------------------------
$src = Join-Path $root 'work\capsule\src'
$classes = Join-Path $root 'work\capsule\classes'
if (Test-Path $src) { Remove-Item -Recurse -Force $src }
if (Test-Path $classes) { Remove-Item -Recurse -Force $classes }
New-Item -ItemType Directory -Force -Path (Join-Path $src 'io\github\fengwenhua\colorduo') | Out-Null
New-Item -ItemType Directory -Force -Path $classes | Out-Null

$probe = @'
package io.github.fengwenhua.colorduo;

import android.graphics.Canvas;
import java.awt.image.BufferedImage;

/**
 * Renders the real capsule at both host sizes and inspects the pixels.
 *
 * <p>The stock host is MIUI's battery icon, 28dp x 20dp - 84x60 at density 3. The
 * widened one is what the module asks for instead, and is what the device shows.
 */
final class CapsuleTest {

    /** MIUI's own battery icon slot, 28dp x 20dp at density 3. */
    private static final int HOST_W = 84;
    private static final int HOST_H = 60;

    /** The panel the glyph is drawn onto; its colour is the "no ink" reference. */
    private static final int BG_R = 0x1C;
    private static final int BG_G = 0x1B;
    private static final int BG_B = 0x1F;

    private static int checks;

    public static void main(String[] args) {
        final TrioSettings cfg = TrioSettings.defaults();
        cfg.trioStyle = Prefs.STYLE_CAPSULE;
        cfg.showWifi = true;
        cfg.showMobile = true;
        cfg.showValue = true;
        // The network type is off by default, so the percentage owns the
        // right-hand slot unless a case asks otherwise.
        TrioState.sClockText = "19:41";

        // ---- the outline itself is the gauge ---------------------------------
        // Charging is where the role colour is unambiguous green, so the growth is
        // measured there. The track is constant underneath and the fill goes over
        // it, so the coloured length has to rise with the level.
        final Stats c10 = stats(render(cfg, 10, true, 0, new int[] { 4, 4 }, ""));
        final Stats c50 = stats(render(cfg, 50, true, 0, new int[] { 4, 4 }, ""));
        final Stats c90 = stats(render(cfg, 90, true, 0, new int[] { 4, 4 }, ""));
        ok("the capsule paints something at all", c50.ink > 0);
        ok("the charged part grows with the level",
                c10.coloured < c50.coloured && c50.coloured < c90.coloured);

        // ---- green is the REMAINING charge -----------------------------------
        // The written spec: green is what is left, grey is the whole 100% track.
        // So the outline is drawn grey in full and the green part is the level
        // itself - a full battery is almost all green, a nearly flat one almost all
        // grey. Asserted on the pixels because it is exactly the inversion a later
        // "cleanup" would silently undo, and a comment cannot catch it.
        final Stats charged = stats(render(cfg, 100, true, 0, new int[] { 4, 4 }, ""));
        final Stats flat = stats(render(cfg, 1, true, 0, new int[] { 4, 4 }, ""));
        ok("a full battery fills more of the outline than a flat one",
                charged.coloured > flat.coloured);
        ok("both still show the track they are measured against",
                charged.track > 0 && flat.track > 0);
        // At 100% the fill SHOULD cover nearly the whole outline - that is what
        // "full" means for a gauge whose track is the shape itself - so the claim
        // worth pinning is the other end: a nearly flat battery has to leave most
        // of the outline in the track colour. An earlier version asserted
        // "the fill never covers the entire outline" at full, which the corrected
        // measurement above showed to be false of a correct drawing; it had been
        // passing only because the old luminance split was counting white text
        // rather than the fill.
        ok("a full battery does cover nearly all of it",
                charged.coloured > charged.track * 3);
        ok("while a flat battery leaves most of it as track",
                flat.track > flat.coloured * 3);
        // And it is monotone across the range, not just at the two ends.
        final Stats c25 = stats(render(cfg, 25, true, 0, new int[] { 4, 4 }, ""));
        final Stats c75 = stats(render(cfg, 75, true, 0, new int[] { 4, 4 }, ""));
        ok("the fill is monotone across the range",
                flat.coloured < c25.coloured && c25.coloured < c50.coloured
                        && c50.coloured < c75.coloured && c75.coloured < charged.coloured);

        // ---- it fits the host -------------------------------------------------
        final BufferedImage[] cases = {
                render(cfg, 100, false, 3, new int[] { 4, 4 }, ""),
                render(cfg, 73, false, 3, new int[] { 4, 3 }, ""),
                render(cfg, 45, true, 0, new int[] { 3, 2 }, ""),
                render(cfg, 8, false, 2, new int[] { 2, 1 }, ""),
                render(cfg, 61, false, 0, new int[] { -1, -1 }, ""),
        };
        for (int i = 0; i < cases.length; i++) {
            final Stats s = stats(cases[i]);
            ok("case " + i + " paints nothing outside the host",
                    s.minX >= 0 && s.minY >= 0 && s.maxX <= HOST_W - 1 && s.maxY <= HOST_H - 1);
            ok("case " + i + " keeps the whole reading inside the host",
                    s.height() <= HOST_H);
        }

        // ---- the widened host, which is the size it really gets --------------
        // The bar measures the icon to 28dp, and the module answers with the width
        // the capsule's aspect ratio needs for the height it was offered.
        //
        // Rendered here rather than left to the device because the difference is
        // visible and quantifiable: at the stock width the glyph is 46px tall in a
        // 60px bar, and the whole point of the re-measure is that it stops being.
        final int wideW = TrioGeometry.capHostWidthPx(HOST_H);
        ok("the requested host is wider than the stock icon slot", wideW > HOST_W);
        final BufferedImage wide = renderAt(cfg, wideW, HOST_H, 73, false, 3,
                new int[] { 4, 3 }, "");
        final Stats w = stats(wide);
        ok("the widened host paints nothing outside itself",
                w.minX >= 0 && w.minY >= 0 && w.maxX <= wideW - 1 && w.maxY <= HOST_H - 1);
        // The host is now exactly the ink box's aspect, so BOTH axes bind at once
        // and the glyph fills the bar's full height. That is the assertion the
        // stock-width host cannot make: there the height is left over.
        ok("and the glyph uses the host's full height", w.height() >= HOST_H - 2);
        ok("while still spanning its width", w.width() >= wideW - 2);
        // Growing the host must not change the shape's proportions: the capsule is
        // the same design either way, just drawn larger.
        final Stats stock = stats(cases[1]);
        close("the aspect is the same at both host sizes",
                w.width() / (double) w.height(),
                stock.width() / (double) stock.height(), 0.12);

        // ---- the outline is broken where the dots interrupt it ---------------
        // With the mobile switch on, the outline has a gap at 12 and 6 o'clock and
        // the four dots are drawn in it; with it off, one continuous stroke runs the
        // whole way.
        //
        // Measured only across the FLAT RUN between the end caps, not the whole
        // host: the caps curve away below the top edge, so even a closed outline
        // lights far fewer than all the columns on that row, and an assertion
        // written against the full width fails on a perfectly correct drawing.
        //
        // The row and the run both come from the geometry rather than from literal
        // pixels, because they move with the aspect ratio.
        final int hostW = TrioGeometry.capHostWidthPx(HOST_H);
        final float scale = HOST_H / TrioGeometry.CAP_INK_H;
        final float tx = (hostW - TrioGeometry.CAP_INK_W * scale) / 2f
                - TrioGeometry.CAP_INK_X * scale;
        final float ty = (HOST_H - TrioGeometry.CAP_INK_H * scale) / 2f
                - TrioGeometry.CAP_INK_Y * scale;
        final int rowY = Math.round(ty + TrioGeometry.CAP_TOP * scale);
        final int runFrom = (int) Math.ceil(tx + TrioGeometry.capFlatLeft() * scale) + 2;
        final int runTo = (int) Math.floor(tx + TrioGeometry.capFlatRight() * scale) - 2;
        final int runWidth = runTo - runFrom + 1;
        ok("the sampled run is a sensible width", runWidth > hostW / 3);

        // Both renders at the same host and the same reading, differing only in the
        // mobile switch - that is the whole variable under test.
        final BufferedImage broken = renderAt(cfg, hostW, HOST_H, 50, false, 0,
                new int[] { 4, 4 }, "");
        cfg.showMobile = false;
        final BufferedImage closed = renderAt(cfg, hostW, HOST_H, 50, false, 0,
                new int[] { 4, 4 }, "");
        cfg.showMobile = true;
        final int closedLit = litColumns(closed, rowY, runFrom, runTo);
        final int brokenLit = litColumns(broken, rowY, runFrom, runTo);
        // The closed outline is a continuous stroke, so every column of the run
        // carries ink. This is what would catch the cut being applied when it should
        // not be - the outline silently opening under a style that draws no rows.
        ok("the closed outline runs unbroken along its top edge", closedLit == runWidth);

        // With the rows on, the gap is where the closed row had stroke and the open
        // one has none. Counted per column rather than as a ratio of the run, because
        // the dots deliberately fill most of the gap: the reference leaves only a
        // narrow break between each dot and the line, so "mostly gap" is not true of
        // a correct drawing and an assertion written that way fails on one.
        int gapColumns = 0;
        for (int x = runFrom; x <= runTo; x++) {
            if (hasInk(closed, x, rowY) && !hasInk(broken, x, rowY)) {
                gapColumns++;
            }
        }
        ok("the rows cut the outline open", gapColumns > 0);
        ok("and the cut is a break, not the whole run", gapColumns < runWidth / 3);
        // The dots ride in that break, so the open row is still lit - an outline
        // that simply vanished would be near zero.
        ok("while the dots themselves carry ink", brokenLit > 0);
        ok("and the broken row is dimmer than the closed one", brokenLit < closedLit);

        // ---- the dots keep clear of the stroke ends --------------------------
        // The gap has to be WIDER than the dots. When it was not - the first cut
        // sized the cut to the dots' own span and nothing more - the outer two
        // merged into the ends of the stroke and the row read as a line with two
        // lumps on it rather than as four dots interrupting a line.
        //
        // Measured as the clearance that is actually VISIBLE, which is not simply
        // half the cut minus the dots. STROKE is drawn with Paint.Cap.ROUND, so the
        // cut end inks a semicircle of radius stroke/2 past its endpoint, and that
        // semicircle eats into the gap. An earlier version of these assertions
        // ignored it and compared the cut against the dot span directly, which is
        // how a clearance that was negative at the top of the stroke slider - the
        // round cap overlapping the outer dot - passed as "a real gap".
        final float stroke = Prefs.DEF_RING_STROKE * TrioGeometry.CAP_STROKE_RATIO;
        final float dotSpan = 3f * TrioGeometry.CAP_DOT_PITCH + 2f * TrioGeometry.CAP_DOT_R;
        final float reach = TrioGeometry.capDotReach(stroke);
        final float visible = reach - dotSpan * 0.5f - stroke * 0.5f;
        ok("the outline is cut wider than the dots themselves", 2f * reach > dotSpan);
        // The clearance is 9% of the path height. The reference's own figure is
        // 6.67% (9px out of 135), and this is deliberately a third MORE than that
        // at the user's request: at 6.67% the device's gap is 3px, which is
        // faithful to the mockup but reads as tight on a 60px-tall glyph.
        //
        // Asserted as a number rather than as a bare "more than a unit" threshold,
        // because the loose form passes at half the real value: a negative control
        // that set the ratio back to the mis-measured 3.3% was NOT caught by it.
        final float pathHeight = TrioGeometry.CAP_BOTTOM - TrioGeometry.CAP_TOP;
        close("the clearance is the intended share of the path height",
                TrioGeometry.capDotClearance() / pathHeight, 0.09, 0.005);
        ok("and it is at least the reference's own clearance",
                TrioGeometry.capDotClearance() / pathHeight >= 0.0667f);
        ok("and the visible clearance is a real gap, not a rounding error", visible > 1f);
        // It has to hold at the TOP of the stroke slider too, which is where the
        // round cap is largest and where the earlier arithmetic went negative.
        final float maxStroke = Prefs.MAX_RING_STROKE * TrioGeometry.CAP_STROKE_RATIO;
        final float visibleMax = TrioGeometry.capDotReach(maxStroke) - dotSpan * 0.5f
                - maxStroke * 0.5f;
        ok("and at the heaviest stroke as well", visibleMax > 1f);
        // The whole point of the stroke term: the visible clearance is the same at
        // every stroke width, rather than shrinking as the line thickens.
        close("the visible clearance does not depend on the stroke width",
                visible, visibleMax, 0.01);
        ok("the clearance is smaller than the stroke that spends it", visible < maxStroke);
        // But not so wide that the outline reads as broken rather than as a row
        // sitting in it: the reference's clearance is a few percent of the height.
        ok("while the clearance stays small enough to read as one outline",
                visible < (TrioGeometry.CAP_BOTTOM - TrioGeometry.CAP_TOP) * 0.12f);
        // And the straight runs the gap leaves have to still exist, or the cut
        // would swallow the shape's top and bottom edges entirely.
        ok("and the straight run survives the widest cut",
                (TrioGeometry.CAP_DOT_CX - reach) - TrioGeometry.capFlatLeft() > 0f);

        // ---- the readings keep a margin from the outline's inner face ---------
        // The time sits in the left slot, right where the end cap curves back in
        // underneath it. With the slot starting at the interior's own edge the
        // digits begin immediately at the stroke's inner face, and at the largest
        // text size the first digit touches the line.
        final float strokePx = Prefs.DEF_RING_STROKE * TrioGeometry.CAP_STROKE_RATIO;
        final float height = TrioGeometry.CAP_BOTTOM - TrioGeometry.CAP_TOP;
        ok("the readings start inside the outline's inner face",
                TrioGeometry.capTextLeft(strokePx) > TrioGeometry.capInteriorLeft(strokePx));
        ok("and stop inside it on the far side",
                TrioGeometry.capTextRight(strokePx) < TrioGeometry.capInteriorRight(strokePx));
        // The margin is the reference's own share of the height - about 8.6%, which
        // is the number measured off it. Not "at least the stroke's width": the
        // reference's margin is in fact smaller than its stroke, so that assertion
        // would fail on a faithful drawing. It was written by mistake and this is
        // what replaced it.
        final float inset = TrioGeometry.capTextLeft(strokePx)
                - TrioGeometry.capInteriorLeft(strokePx);
        close("the margin matches the reference's share of the height",
                inset / height, 0.086, 0.01);
        // And it has to be big enough to see: a margin of a fraction of a pixel is
        // no margin at all, which is the state this replaced.
        ok("and is a visible margin, not a rounding error", inset > 2f);

        // ---- both readings can reach their own maximum --------------------------
        // This is the check that would have caught "the clock's font ceiling is too
        // small", and it is expressed as reachability rather than as a drawn size,
        // because the drawn size was not what was wrong. The two slots used to be
        // equal halves of the interior while the strings in them are not - "19:41"
        // is 2.52 units wide per unit of size and "100" is 1.68 - so the time ran out
        // of width at a setting of about 36 and the clock's documented maximum of 44
        // was unreachable at EVERY value of the number's setting. Raising the ceiling
        // would have changed nothing, which is exactly the trap: the slot, not the
        // ceiling, was binding.
        //
        // Stated against the settings' own ceilings so it keeps holding if either
        // range moves, and computed from the two per-unit widths the geometry itself
        // declares - so it is a statement about the split rather than a second copy
        // of the numbers.
        final float timeSlot = TrioGeometry.capTimeSlotWidth(strokePx);
        final float valueSlot = TrioGeometry.capValueSlotWidth(strokePx);
        final float timeMaxSetting =
                timeSlot / TrioGeometry.CAP_TIME_TEXT_PER_UNIT / TrioGeometry.CAP_TEXT_SCALE;
        final float valueMaxSetting =
                valueSlot / TrioGeometry.CAP_VALUE_TEXT_PER_UNIT / TrioGeometry.CAP_TEXT_SCALE;
        // Within a step of the ceiling: the exact proportional split lands a fraction
        // short, because the interior is not infinite, and demanding equality would
        // fail on a correct drawing.
        ok("the clock can reach its own maximum setting",
                timeMaxSetting >= Prefs.MAX_CLOCK_SIZE - 1f);
        ok("and so can the number",
                valueMaxSetting >= Prefs.MAX_VALUE_SIZE - 1f);
        // The time's slot is the wider one, because its string is the longer. This is
        // the direction of the fix: an even split fails here by construction.
        ok("the time's slot is wider than the number's", timeSlot > valueSlot);
        // The two slots plus their gap have to be exactly the usable interior, or the
        // readings would overlap or leave a strip unused.
        close("the two slots and their gap fill the interior exactly",
                timeSlot + valueSlot + TrioGeometry.CAP_TEXT_GAP,
                TrioGeometry.capTextRight(strokePx) - TrioGeometry.capTextLeft(strokePx), 0.01);
        // The split follows the two strings' own widths, so the reading that is
        // proportionally longer gets the proportionally wider slot.
        close("and the split is the strings' own width ratio",
                timeSlot / valueSlot,
                TrioGeometry.CAP_TIME_TEXT_PER_UNIT / TrioGeometry.CAP_VALUE_TEXT_PER_UNIT, 0.01);

        // The clock's slider has to keep buying real size all the way to its ceiling.
        // Before the fix the drawn size was flat above a setting of 36 - 44, 60 and
        // 120 all produced the same 23 units - so a monotonic check across the top of
        // the range is what pins the behaviour rather than the endpoints alone.
        final android.graphics.Paint probe = new android.graphics.Paint();
        probe.setTypeface(android.graphics.Typeface.create("sans-serif",
                android.graphics.Typeface.NORMAL));
        float previous = -1f;
        for (int setting = 36; setting <= Prefs.MAX_CLOCK_SIZE; setting += 2) {
            final float requested = Math.min(setting * TrioGeometry.CAP_TEXT_SCALE,
                    TrioGeometry.capTextMaxSize(strokePx));
            probe.setTextSize(requested);
            final float width = probe.measureText("19:41");
            final float drawn = width <= timeSlot ? requested : requested * timeSlot / width;
            ok("the clock's slider buys size at setting " + setting, drawn > previous);
            previous = drawn;
        }

        // ---- the charge is spent from the bottom of the right bar ------------
        // The two dot rows divide the outline into a left bar and a right bar, and
        // the reading is taken off them as two bars: full is entirely lit, and the
        // charge goes from the bottom of the RIGHT one, counterclockwise. So the
        // order the segments go dark is botR, capR, topR, topL, capL, botL.
        //
        // Asserted as that ORDER rather than as a set of levels, because the order
        // is the whole requirement and any of the six could be mis-wired on its
        // own. The earlier implementation ran the other way - counterclockwise from
        // the top gap - which spent the charge from the top left and lit the wrong
        // half of the shape at every level below full.
        final boolean[][] lit = new boolean[7][];
        final int[] probeLevels = { 100, 85, 60, 50, 40, 10 };
        for (int i = 0; i < probeLevels.length; i++) {
            lit[i] = segments(renderAt(cfg, wideW, HOST_H, probeLevels[i], true, 0,
                    new int[] { 4, 4 }, ""));
        }
        // Full: everything on.
        ok("a full battery lights the whole outline",
                lit[0][0] && lit[0][1] && lit[0][2] && lit[0][3] && lit[0][4] && lit[0][5]);
        // The right bar empties before the left one does: at half, the right bar is
        // dark and the left is still lit.
        final int half = 3;
        ok("at half the right bar is spent", !lit[half][3] && !lit[half][4] && !lit[half][5]);
        ok("and the left bar is not", lit[half][0] && lit[half][1] && lit[half][2]);
        // Segment by segment, in the order they must go dark.
        ok("the first to go is the bottom of the right bar", !lit[1][3]);
        ok("then the right cap", lit[1][4] && !lit[2][4]);
        ok("then the top of the right bar", lit[2][5] && !lit[half][5]);
        ok("then the top of the left bar", lit[half][0] && !lit[4][0]);
        ok("then the left cap", lit[4][1] && !lit[5][1]);
        // At the very bottom only the foot of the left bar is left, which is the
        // far end of the counterclockwise walk from where it started.
        ok("and the foot of the left bar is the last to go",
                lit[5][2] || lit[4][2]);

        // ---- the clock has its own size and weight ---------------------------
        // The time and the battery number share the capsule but not a setting: they
        // sit in two slots that hold strings of different lengths and are read for
        // different reasons, so moving one must not move the other. Before these two
        // keys existed the time was drawn at `valueSize` / `valueWeight`, which is
        // exactly what "one slider moves both readings" means.
        //
        // Both directions are asserted. Checking only that the clock responds would
        // pass on an implementation that also let the clock drive the number - which
        // is the same coupling from the other end, and the failure mode a later
        // "simplification" would most plausibly introduce.
        final TrioSettings clockCfg = TrioSettings.defaults();
        clockCfg.trioStyle = Prefs.STYLE_CAPSULE;
        clockCfg.showValue = true;
        clockCfg.showMobile = true;
        clockCfg.showWifi = false;

        TrioState.sClockText = "19:41";
        clockCfg.clockSize = 16;
        clockCfg.valueSize = 44;
        final Stats smallClock = stats(render(clockCfg, 76, false, 0, new int[] { 4, 4 }, ""));
        clockCfg.clockSize = 44;
        final Stats bigClock = stats(render(clockCfg, 76, false, 0, new int[] { 4, 4 }, ""));
        ok("the clock's own size slider changes the drawing",
                smallClock.ink != bigClock.ink);
        // The number's setting is untouched across those two renders, so the
        // right-hand slot must be pixel-identical. Compared to the right of the real
        // slot boundary, which is not the host's midpoint.
        final float boundary = slotBoundary();
        clockCfg.clockSize = 16;
        final BufferedImage valueAtSmallClock =
                render(clockCfg, 76, false, 0, new int[] { 4, 4 }, "");
        clockCfg.clockSize = 44;
        final BufferedImage valueAtBigClock =
                render(clockCfg, 76, false, 0, new int[] { 4, 4 }, "");
        ok("and does not move the battery number",
                sameRegion(valueAtSmallClock, valueAtBigClock, boundary, 1f));

        // Now the other direction: the value's size changes while the clock's is
        // pinned, and the time's slot has to be unchanged.
        clockCfg.clockSize = 30;
        clockCfg.valueSize = 20;
        final BufferedImage clockAtSmallValue =
                render(clockCfg, 76, false, 0, new int[] { 4, 4 }, "");
        clockCfg.valueSize = 44;
        final BufferedImage clockAtBigValue =
                render(clockCfg, 76, false, 0, new int[] { 4, 4 }, "");
        ok("the number's size slider does not move the clock",
                sameRegion(clockAtSmallValue, clockAtBigValue, 0f, boundary));
        ok("while it does move the number",
                !sameRegion(clockAtSmallValue, clockAtBigValue, boundary, 1f));

        // The weight is a second, independent axis, and it is asserted per SLOT
        // rather than on a total ink count. A count is the wrong instrument here:
        // the two weights the slider lands on by default - 700 and 900 - draw the
        // same number of dark pixels at this size, so "the ink differs" is false of
        // a correct drawing and comparing two totals proves nothing either way. What
        // has to hold is positional, and that is what is checked.
        clockCfg.valueSize = 36;
        clockCfg.valueWeight = 700;
        clockCfg.clockSize = 36;
        clockCfg.clockWeight = 100;
        final BufferedImage clockThin = render(clockCfg, 76, false, 0, new int[] { 4, 4 }, "");
        clockCfg.clockWeight = 900;
        final BufferedImage clockThick = render(clockCfg, 76, false, 0, new int[] { 4, 4 }, "");
        // The clock's weight moves the clock...
        ok("the clock's weight slider changes the clock's own slot",
                !sameRegion(clockThin, clockThick, 0f, boundary));
        // ...and nothing else.
        ok("and leaves the number alone",
                sameRegion(clockThin, clockThick, boundary, 1f));

        // The mirror image: the number's weight moves the number and not the clock.
        clockCfg.clockWeight = 700;
        clockCfg.valueWeight = 100;
        final BufferedImage valueThin = render(clockCfg, 76, false, 0, new int[] { 4, 4 }, "");
        clockCfg.valueWeight = 900;
        final BufferedImage valueThick = render(clockCfg, 76, false, 0, new int[] { 4, 4 }, "");
        ok("the number's weight slider changes the number's own slot",
                !sameRegion(valueThin, valueThick, boundary, 1f));
        ok("and leaves the clock alone",
                sameRegion(valueThin, valueThick, 0f, boundary));

        // The default has to reproduce the old shared-size look exactly, or an
        // install that never opens the new sliders would see the time change size.
        ok("the clock defaults to the number's size",
                Prefs.DEF_CLOCK_SIZE == Prefs.DEF_VALUE_SIZE);
        ok("and to the number's weight",
                Prefs.DEF_CLOCK_WEIGHT == Prefs.DEF_VALUE_WEIGHT);
        // The two keys are new, so anything that predates them must fall back on the
        // value's OWN setting rather than on the constant - otherwise a user who had
        // already moved the old slider would see the clock jump on upgrade. That
        // fallback lives in the READERS, not in defaults(): a fresh TrioSettings has
        // never seen a stored file or a bundle and legitimately carries the
        // constants. Asserting it against defaults() - which is what the first cut of
        // this did - tests the wrong object and fails on a correct fallback.
        //
        // Driven through the bundle reader, which carries the same fallback as the
        // stored-file one and needs no extra shim. Both are written the same way, so
        // a divergence between them is a real defect this would then miss; the two
        // are kept adjacent in the source for exactly that reason.
        final android.os.Bundle legacyBundle = new android.os.Bundle();
        legacyBundle.putInt(Prefs.KEY_VALUE_SIZE, 40);
        legacyBundle.putInt(Prefs.KEY_VALUE_WEIGHT, 300);
        final TrioSettings legacy = TrioSettings.fromBundle(legacyBundle);
        ok("a snapshot without the clock keys keeps the value's size",
                legacy.clockSize == 40);
        ok("and the value's weight", legacy.clockWeight == 300);
        // But an explicit clock setting always wins over that fallback.
        legacyBundle.putInt(Prefs.KEY_CLOCK_SIZE, 22);
        legacyBundle.putInt(Prefs.KEY_CLOCK_WEIGHT, 800);
        final TrioSettings explicit = TrioSettings.fromBundle(legacyBundle);
        ok("an explicit clock size wins over the fallback", explicit.clockSize == 22);
        ok("and an explicit clock weight", explicit.clockWeight == 800);
        // And a partial update through the seeded reader - the path the settings app
        // uses - must leave the clock where it was rather than resetting it. The
        // bundle here is genuinely partial: it carries one unrelated key and no clock
        // keys at all. Reusing the bundle from above, which by then held explicit
        // clock values, is not a partial update and asserts nothing about this path.
        final android.os.Bundle partial = new android.os.Bundle();
        partial.putBoolean(Prefs.KEY_SHOW_WIFI, true);
        final TrioSettings switched = TrioSettings.fromBundle(partial, explicit);
        ok("a partial update carries the clock's size through",
                switched.clockSize == explicit.clockSize);
        ok("and its weight", switched.clockWeight == explicit.clockWeight);
        ok("while still applying the key it did carry", switched.showWifi);

        // ---- the right-hand slot is arbitrated, not stacked -------------------
        // A charger takes the slot from the arcs and from the percentage, exactly as
        // it takes the ring's notch - so the Wi-Fi level must not change what a
        // charging frame looks like.
        ok("the charging bolt does not depend on the Wi-Fi level",
                same(render(cfg, 50, true, 3, new int[] { 4, 4 }, ""),
                     render(cfg, 50, true, 0, new int[] { 4, 4 }, "")));

        System.out.println("PASS: " + checks
                + " cases over the capsule arrangement at both host sizes");
    }

    /** Paints the capsule into a panel-coloured bitmap at MIUI's stock host size. */
    private static BufferedImage render(TrioSettings cfg, int level, boolean charging,
                                        int wifi, int[] slots, String type) {
        return renderAt(cfg, HOST_W, HOST_H, level, charging, wifi, slots, type);
    }

    /** The same, at an arbitrary host size - used for the widened measurement. */
    private static BufferedImage renderAt(TrioSettings cfg, int w, int h, int level,
                                          boolean charging, int wifi, int[] slots,
                                          String type) {
        final BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        final java.awt.Graphics2D g = img.createGraphics();
        g.setColor(new java.awt.Color(BG_R, BG_G, BG_B));
        g.fillRect(0, 0, w, h);
        final Canvas canvas = new Canvas(g);
        TrioRenderer.drawInto(canvas, w, h, level, charging, false, false,
                level <= 15, wifi, level, slots, type, 0xFFFFFFFF, cfg, false);
        g.dispose();
        return img;
    }

    /**
     * The ink summary of a whole render.
     *
     * <p>Over the image's own dimensions, not {@link #HOST_W}/{@link #HOST_H}: the
     * widened host is a different size, and measuring it inside the stock bounds
     * would silently ignore everything past column 83 - which is where most of the
     * widened glyph is, and would make both the "nothing outside the host" and the
     * "spans its width" assertions vacuous rather than wrong.
     */
    private static Stats stats(BufferedImage img) {
        return measure(img, 0, 0, img.getWidth(), img.getHeight());
    }

    /** Whether one pixel carries ink. */
    private static boolean hasInk(BufferedImage img, int x, int y) {
        final int argb = img.getRGB(x, y);
        final int r = (argb >> 16) & 0xFF;
        final int g = (argb >> 8) & 0xFF;
        final int b = argb & 0xFF;
        return Math.abs(r - BG_R) + Math.abs(g - BG_G) + Math.abs(b - BG_B) >= 24;
    }

    /**
     * Which of the outline's six pieces carry the charged colour, for one render.
     *
     * <p>Returns {@code {topL, capL, botL, botR, capR, topR}} - the left bar's three
     * pieces first, then the right bar's, because that is the order they are read
     * in and the order the fill's direction is stated against.
     *
     * <p>Each piece is sampled at its own midpoint, in host pixels, through the same
     * transform {@code TrioRenderer.drawInto} applies. The end caps are sampled at
     * their EXTREME x rather than at their centre: a stadium's cap is a half-circle,
     * so at the centre of that circle the stroke is at the top and the bottom and
     * the mid-height sample misses it entirely - which is how an earlier version of
     * this reported every cap unlit at every level.
     */
    private static boolean[] segments(BufferedImage img) {
        final float scale = Math.min(img.getWidth() / TrioGeometry.CAP_INK_W,
                img.getHeight() / TrioGeometry.CAP_INK_H);
        final float tx = (img.getWidth() - TrioGeometry.CAP_INK_W * scale) / 2f
                - TrioGeometry.CAP_INK_X * scale;
        final float ty = (img.getHeight() - TrioGeometry.CAP_INK_H * scale) / 2f
                - TrioGeometry.CAP_INK_Y * scale;
        final float reach = TrioGeometry.capDotReach();

        final float gapLeft = TrioGeometry.CAP_DOT_CX - reach;
        final float gapRight = TrioGeometry.CAP_DOT_CX + reach;
        final float runLCx = (TrioGeometry.capFlatLeft() + gapLeft) * 0.5f;
        final float runRCx = (gapRight + TrioGeometry.capFlatRight()) * 0.5f;
        final float my = (TrioGeometry.CAP_TOP + TrioGeometry.CAP_BOTTOM) * 0.5f;

        final int lx = Math.round(tx + runLCx * scale);
        final int rx = Math.round(tx + runRCx * scale);
        final int topY = Math.round(ty + TrioGeometry.CAP_TOP * scale);
        final int botY = Math.round(ty + TrioGeometry.CAP_BOTTOM * scale);
        final int capLY = Math.round(ty + my * scale);
        final int capLx = Math.round(tx + TrioGeometry.CAP_LEFT * scale);
        final int capRx = Math.round(tx + TrioGeometry.CAP_RIGHT * scale);

        return new boolean[] {
                charged(img, lx, topY),
                charged(img, capLx, capLY),
                charged(img, lx, botY),
                charged(img, rx, botY),
                charged(img, capRx, capLY),
                charged(img, rx, topY),
        };
    }

    /**
     * Whether the stroke at this point is a role colour rather than the track.
     *
     * <p>Chroma, for the reason given on {@link #measure}: brightness cannot tell
     * the charging green (142) from the track (77) with any threshold that is not a
     * magic number, and the green and the critical red land on opposite sides of
     * 150.
     */
    private static boolean charged(BufferedImage img, int x, int y) {
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                final int xx = x + dx;
                final int yy = y + dy;
                if (xx < 0 || yy < 0 || xx >= img.getWidth() || yy >= img.getHeight()) {
                    continue;
                }
                final int argb = img.getRGB(xx, yy);
                final int r = (argb >> 16) & 0xFF;
                final int g = (argb >> 8) & 0xFF;
                final int b = argb & 0xFF;
                if (Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b)) >= 30) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * How many columns in {@code x0..x1} along row {@code y} carry ink.
     *
     * <p>Counting columns rather than sampling the centre column matters because the
     * centre is a dot when the rows are on and stroke when they are off - it is
     * painted either way, so a single-column probe would distinguish nothing.
     */
    private static int litColumns(BufferedImage img, int y, int x0, int x1) {
        int lit = 0;
        for (int x = Math.max(0, x0); x <= Math.min(img.getWidth() - 1, x1); x++) {
            if (hasInk(img, x, y)) {
                lit++;
            }
        }
        return lit;
    }

    /**
     * Counts ink, the coloured part of it, the track, and the ink's bounds.
     *
     * <p><b>Chroma, not brightness.</b> An earlier version split the two passes at
     * a luminance of 150, reasoning that the track is drawn dim and the fill at
     * full opacity. That is wrong, and measurably so: the charging green is
     * #34C759, whose luminance is 142 - under the threshold - and the critical red
     * is 116. Both were therefore counted as track, and "coloured" ended up
     * counting only the white readings and the arcs. The monotone-growth assertion
     * still passed, which is the dangerous part: it passed because more of the
     * white text's antialiased edge cleared 150 when green was behind it than when
     * the panel was. The number moved with the level, but for a reason unrelated
     * to the fill.
     *
     * <p>Chroma separates them properly. Every role colour is saturated - green
     * #34C759 spans 147, red #FF3B30 spans 207, amber #F2B900 spans 242 - while
     * the track is the foreground grey over the panel (rgb(77,77,80), spanning 3)
     * and the readings are white (spanning 0). So "coloured" is exactly the role
     * colour and nothing else, and `track` is neutral and dim, which keeps the
     * white readings out of both.
     */
    private static Stats measure(BufferedImage img, int x0, int y0, int x1, int y1) {
        final Stats s = new Stats();
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                final int argb = img.getRGB(x, y);
                final int r = (argb >> 16) & 0xFF;
                final int g = (argb >> 8) & 0xFF;
                final int b = argb & 0xFF;
                if (Math.abs(r - BG_R) + Math.abs(g - BG_G) + Math.abs(b - BG_B) < 24) {
                    continue;
                }
                s.ink++;
                if (x < s.minX) { s.minX = x; }
                if (x > s.maxX) { s.maxX = x; }
                if (y < s.minY) { s.minY = y; }
                if (y > s.maxY) { s.maxY = y; }
                final int hi = Math.max(r, Math.max(g, b));
                final int lo = Math.min(r, Math.min(g, b));
                if (hi - lo >= 30) {
                    s.coloured++;
                } else if (hi < 150) {
                    // Neutral AND dim: the track. The white readings are neutral but
                    // bright, so they are excluded here rather than inflating it.
                    s.track++;
                }
            }
        }
        if (s.maxX < 0) { s.minX = 0; s.minY = 0; s.maxX = -1; s.maxY = -1; }
        return s;
    }

    /** One render's ink counts and bounds. */
    private static final class Stats {
        int ink;
        int coloured;
        int track;
        int minX = HOST_W;
        int minY = HOST_H;
        int maxX = -1;
        int maxY = -1;

        int width() { return maxX - minX + 1; }
        int height() { return maxY - minY + 1; }
    }

    /** True when two renders painted the identical pixels in the identical place. */
    private static boolean same(BufferedImage a, BufferedImage b) {
        for (int y = 0; y < HOST_H; y++) {
            for (int x = 0; x < HOST_W; x++) {
                if (a.getRGB(x, y) != b.getRGB(x, y)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * True when two renders agree across one horizontal share of the host.
     *
     * <p>Used to prove the two readings are independent: changing the clock's size
     * must leave the right-hand slot alone, and changing the number's must leave
     * the left. Checked on a band rather than the whole image because the thing
     * under test is precisely that one half moves and the other does not - a
     * whole-image comparison is false either way and would assert nothing.
     *
     * <p><b>The boundary is not the host's midpoint.</b> The two slots are divided
     * in the ratio of the two strings' natural widths - 0.60 to the time, 0.40 to
     * the number - so half the width falls INSIDE the time's slot. An earlier cut of
     * this compared at 0.5, which caught the clock's own drawing on both sides and
     * so reported "the clock's size moved the number" on a correct render.
     *
     * <p>The band is taken about the real boundary rather than exactly at it, so a
     * glyph that overhangs by a pixel or two - the digits are centred in their slots
     * and the time's "1" is narrow - cannot straddle the line and be counted on the
     * wrong side. What is being tested is which SLOT a pixel belongs to, and the
     * slots meet at {@link TrioGeometry#CAP_TIME_WIDTH_SHARE} of the interior, which
     * sits a little inside the host's own 0.5 because of the left inset.
     */
    private static boolean sameRegion(BufferedImage a, BufferedImage b,
                                      float fromFraction, float toFraction) {
        final int x0 = Math.max(0, (int) (a.getWidth() * fromFraction));
        final int x1 = Math.min(a.getWidth(), (int) (a.getWidth() * toFraction));
        for (int y = 0; y < a.getHeight(); y++) {
            for (int x = x0; x < x1; x++) {
                if (a.getRGB(x, y) != b.getRGB(x, y)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * The host x that separates the two readings, as a fraction of the width.
     *
     * <p>Taken at the midpoint between the two slot centres, which is the one place
     * neither reading can reach: each is centred in its own slot and the slots are
     * separated by {@link TrioGeometry#CAP_TEXT_GAP}, so the midpoint lies in that
     * gap however wide either string is drawn.
     *
     * <p>Not the host's midpoint. An earlier cut of this used 0.5 on the reasoning
     * that the two slots were equal halves of the interior - which was true, and was
     * the very bug being fixed: they are now divided in the strings' width ratio, so
     * half the width falls inside the time's slot and the check caught the clock's
     * own ink on both sides. It reported "the clock's size moved the number" for a
     * correct render.
     *
     * <p>Derived through the same transform the renderer applies, so it follows the
     * inset, the stroke and the share instead of restating them.
     */
    private static float slotBoundary() {
        final float stroke = Prefs.DEF_RING_STROKE * TrioGeometry.CAP_STROKE_RATIO;
        final float scale = TrioRenderer.inkScale(HOST_W, HOST_H, Prefs.STYLE_CAPSULE);
        final float designX = (TrioGeometry.capLeftCx(stroke)
                + TrioGeometry.capRightCx(stroke)) * 0.5f;
        final float tx = (HOST_W - TrioGeometry.CAP_INK_W * scale) / 2f
                - TrioGeometry.CAP_INK_X * scale;
        return (tx + designX * scale) / HOST_W;
    }

    private static void ok(String what, boolean held) {
        checks++;
        if (!held) {
            throw new AssertionError(what);
        }
    }

    private static void close(String what, double got, double want, double tol) {
        checks++;
        if (Math.abs(got - want) > tol) {
            throw new AssertionError(what + ": got <" + got + "> want <" + want
                    + "> within <" + tol + ">");
        }
    }
}
'@

[System.IO.File]::WriteAllText(
    (Join-Path $src 'io\github\fengwenhua\colorduo\CapsuleTest.java'),
    $probe, (New-Object System.Text.UTF8Encoding($false)))

# --- compile and run -----------------------------------------------------------
$previewRoot = Join-Path $root 'tests\shims'
if (!(Test-Path (Join-Path $previewRoot 'android\graphics\Canvas.java'))) {
    throw "the desktop shims are missing: $previewRoot (see docs/DEVELOPMENT.md, the test section)"
}
$preview = Get-ChildItem -Recurse -Filter *.java $previewRoot | ForEach-Object { $_.FullName }
$shared = @(
    (Join-Path $trio 'Prefs.java'),
    (Join-Path $trio 'ChargeLayout.java'),
    (Join-Path $trio 'SettingsSchema.java'),
    (Join-Path $trio 'TrioSettings.java'),
    (Join-Path $trio 'TrioGeometry.java'),
    (Join-Path $trio 'TrioAppearance.java'),
    (Join-Path $trio 'TrioRenderer.java'))
$sources = $preview + $shared + @(Get-ChildItem -Recurse -Filter *.java $src |
    ForEach-Object { $_.FullName })

& $javac -nowarn -d $classes -encoding UTF-8 $sources
if ($LASTEXITCODE) { throw 'Compilation failed' }
& $java -Xmx128m -cp $classes io.github.fengwenhua.colorduo.CapsuleTest
if ($LASTEXITCODE) { throw 'Test failed' }
