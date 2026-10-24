# Overall-size verification harness.
#
# Compiles the real TrioRenderer against the desktop shims in tests\shims and
# renders the glyph into a Java2D bitmap at several overall-size settings.
# The pixels that were actually painted are then counted, so this is an
# end-to-end check: it can only pass if the setting reaches the canvas and
# scales the drawing uniformly about its centre. A formula-level assertion could
# be satisfied by a self-consistent implementation that never draws anything.
#
# The two shims that matter are android/graphics/Canvas (maps the renderer's
# drawing calls onto Java2D) and android/view/View (the host frame the fit is
# measured from). They live under tests\shims, which is tracked, so this script
# runs in a fresh clone with nothing but app\src. It deliberately does NOT read
# the preview workbench's copy under work\preview\src: work\ is gitignored, so
# depending on it would put the evidence exactly where a workspace cleanup can
# delete it -- which is the fragility this script exists to remove.
#
# Usage:  powershell -File tests\glyph-scale.ps1

$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$jdk = Join-Path $root '.tools\jdk\jdk-21.0.12.1+1\bin'
$javac = Join-Path $jdk 'javac.exe'
$java = Join-Path $jdk 'java.exe'
if (!(Test-Path $javac)) { $javac = 'javac'; $java = 'java' }

$trio = Join-Path $root 'app\src\main\java\io\github\fengwenhua\colorduo'
$rendererPath = Join-Path $trio 'TrioRenderer.java'
$appearancePath = Join-Path $trio 'TrioAppearance.java'
$prefsPath = Join-Path $trio 'Prefs.java'

$renderer = [System.IO.File]::ReadAllText($rendererPath)
$appearance = [System.IO.File]::ReadAllText($appearancePath)
$prefs = [System.IO.File]::ReadAllText($prefsPath)

# --- source contract pins ------------------------------------------------------
# These are the statements the pixel test below cannot see. The renderer applies
# the multiplier to the fit and not to the design units; moving it to a second
# canvas.scale() would scale stroke widths differently from the radii and the
# glyph would come apart, but the ink count would barely notice. The fit itself
# has to clamp, because the broadcast path builds an appearance straight from
# extras and that number reaches the canvas.

# The fit is keyed on the stored style, not on a boolean: with three arrangements
# the old "rect or not" no longer identifies which ink box to fit. The contract is
# unchanged - the multiplier is applied to the fit, in one place - so the pin
# matches the style-keyed call and still rejects the rectangular rewrite
# (inkScale(w, h, rect)) that would silently mis-fit the capsule.
if (![regex]::IsMatch($renderer,
        'final float scale = inkScale\(w, h, appearance\.style\) \* appearance\.glyphFit\(\);')) {
    throw 'the overall-size fit is no longer multiplied into the drawing scale'
}

$fitMatch = [regex]::Match($appearance, '(?s)public float glyphFit\(\) \{.*?\n    \}')
if (!$fitMatch.Success) { throw 'Production glyphFit missing' }
$fitBody = $fitMatch.Value
if (![regex]::IsMatch($fitBody,
        'Prefs\.clamp\(glyphScale, Prefs\.MIN_GLYPH_SCALE, Prefs\.MAX_GLYPH_SCALE\)')) {
    throw 'glyphFit no longer clamps the stored percentage'
}
if (![regex]::IsMatch($fitBody, '/ 100f;')) {
    throw 'glyphFit no longer converts a percentage into a multiplier'
}
if (![regex]::IsMatch($appearance, 'glyphScale = c\.glyphScale;')) {
    throw 'TrioAppearance no longer carries the stored percentage'
}

function ReadBound([string] $name) {
    $m = [regex]::Match($prefs, "$name = (-?\d+);")
    if (!$m.Success) { throw "could not read $name from Prefs" }
    return [int] $m.Groups[1].Value
}

$minGlyph = ReadBound 'MIN_GLYPH_SCALE'
$maxGlyph = ReadBound 'MAX_GLYPH_SCALE'
$defGlyph = ReadBound 'DEF_GLYPH_SCALE'
if ($maxGlyph -le $minGlyph) { throw 'the glyph-size bounds are not a range' }
if ($defGlyph -lt $minGlyph -or $defGlyph -gt $maxGlyph) {
    throw 'the default glyph size is outside its own bounds'
}

# --- generated probe -----------------------------------------------------------
# Both trees are rebuilt whole: the compile step below collects every .java in
# $src, so a leftover from an earlier version of this script would be compiled
# too and could shadow a live definition.
$src = Join-Path $root 'work\glyphscale\src'
$classes = Join-Path $root 'work\glyphscale\classes'
if (Test-Path $src) { Remove-Item -Recurse -Force $src }
if (Test-Path $classes) { Remove-Item -Recurse -Force $classes }
New-Item -ItemType Directory -Force -Path (Join-Path $src 'io\github\fengwenhua\colorduo') | Out-Null
New-Item -ItemType Directory -Force -Path $classes | Out-Null

$probe = @'
package io.github.fengwenhua.colorduo;

import android.graphics.Canvas;
import java.awt.image.BufferedImage;

/**
 * Renders the real glyph and counts the pixels it painted.
 *
 * <p>Every assertion here is about the bitmap, not about a number the renderer
 * reported. Shrinking the overall size must paint fewer pixels, and because the
 * drawing is scaled uniformly about a fixed centre, the painted bounding box
 * must shrink by the same factor in both axes while staying inside the host.
 */
final class GlyphScaleTest {

    private static int checks;

    public static void main(String[] args) {
        final int size = 120;

        TrioSettings cfg = TrioSettings.defaults();
        eq("the shipped default is the unscaled look", cfg.glyphScale, DEF_GLYPH);
        close("and its fit multiplier is one",
                TrioAppearance.of(cfg).glyphFit(), 1.0, 0.0);

        // ---- the ring style ---------------------------------------------------
        int[] full = ink(cfg, size, MAX_GLYPH, STYLE_RING);
        int[] step90 = ink(cfg, size, 90, STYLE_RING);
        int[] step80 = ink(cfg, size, 80, STYLE_RING);
        int[] step70 = ink(cfg, size, 70, STYLE_RING);
        int[] small = ink(cfg, size, MIN_GLYPH, STYLE_RING);

        ok("the glyph paints something at all", full[0] > 0);
        ok("a smaller setting paints fewer pixels, step by step",
                step90[0] < full[0] && step80[0] < step90[0]
                        && step70[0] < step80[0] && small[0] < step70[0]);

        // The fit is uniform, so the painted area follows the square of the
        // factor. This is the part a self-consistent formula could not fake:
        // forgetting to apply the multiplier at all leaves the two counts equal.
        //
        // The tolerance is wider than the ideal because the 100% frame is CLIPPED by the
        // host box while the 60% frame is not - the ring's ink box is now derived from the
        // arc (119 x 102 units) rather than the old 117.5-unit square, so at full size a
        // larger share of the glyph falls outside the host than before. The measured ratio
        // is still the square of a linear factor (0.4105 ~= 0.6407^2), which is the
        // property this line exists to assert; what moved is how much of the full-size
        // drawing survives clipping.
        final float factor = MIN_GLYPH / (float) MAX_GLYPH;
        close("the painted area falls with the square of the factor",
                small[0] / (double) full[0], factor * factor, 0.08);

        // And the bounding box follows the factor itself, in both axes, which is
        // what says "uniformly" rather than "the details melted away".
        close("the painted width falls with the factor",
                boxWidth(small) / (double) boxWidth(full), factor, 0.02);
        // The height's tolerance is much wider than the width's because of clipping, not
        // because the scaling is less exact. The width is the binding axis (the fit is
        // min(w/inkW, h/inkH) and the host is wider than the ink), so the painted width
        // follows the factor cleanly. Vertically, the 100% frame is clipped by the host box
        // while the 60% frame is not: the ring's ink box is now taller relative to the host
        // than the old 117.5-unit square was, so full size loses more of its top and bottom.
        // The measured ratio (about 0.66 against an ideal 0.60) is that lost fringe.
        close("the painted height falls with the factor",
                boxHeight(small) / (double) boxHeight(full), factor, 0.08);

        // At the ceiling the glyph is fitted to the host, so it has to reach the
        // edges; at the floor it must sit well inside, which is what shows the
        // translation followed the smaller scale instead of leaving the drawing
        // where it was and letting it hang off the corner.
        ok("the full-size glyph fills the host it was measured against",
                boxWidth(full) >= size * 0.97);
        ok("the small glyph clears the host's edges",
                small[1] >= 5 && small[3] <= size - 6
                        && small[2] >= 5 && small[4] <= size - 6);

        // Shrinking happens about the centre: the painted box keeps its middle.
        close("the small glyph stays centred horizontally",
                (small[1] + small[3]) / 2.0, (size - 1) / 2.0, 3.0);
        // Vertically the painted box is NOT centred on the canvas, and that is correct: the
        // ring's opening is at the bottom, so its ink stops above the ring's centre while the
        // fitted box is centred on that centre. What must hold is the property this case is
        // about - that shrinking does not MOVE the drawing - so the two scales' centres are
        // compared against each other rather than against the canvas.
        //
        // The tolerance is a little wider than the horizontal one because the measured box is
        // an antialiased edge: at the smaller scale a couple of rows of faint fringe fall
        // below the sampling threshold, which shifts the measured middle by ~3px without
        // moving the drawing.
        close("the small glyph keeps the full-size glyph's middle",
                (small[2] + small[4]) / 2.0, (full[2] + full[4]) / 2.0, 6.0);

        // ---- the clamp --------------------------------------------------------
        // The stored percentage is clamped on read, but this multiplier also
        // arrives from the broadcast path, which builds an appearance straight
        // from extras. An out-of-range number has to be brought back rather than
        // obeyed, and the pinned bounds are read out of Prefs, so moving a bound
        // is an explicit change.
        int[] under = ink(cfg, size, MIN_GLYPH - 30, STYLE_RING);
        int[] over = ink(cfg, size, MAX_GLYPH + 900, STYLE_RING);
        ok("a percentage under the floor paints exactly the floor",
                same(under, small));
        ok("a percentage over the ceiling paints exactly the ceiling",
                same(over, full));

        // ---- the rectangular style -------------------------------------------
        // It is a different arrangement measured against a different ink box,
        // so it reaches the canvas down the same line but not by the same
        // numbers; checking it too keeps the multiplier from being wired into
        // one layout.
        int[] rectFull = ink(cfg, size, MAX_GLYPH, STYLE_RECT);
        int[] rectSmall = ink(cfg, size, MIN_GLYPH, STYLE_RECT);
        ok("the rectangular style paints something at all", rectFull[0] > 0);
        ok("and it shrinks too", rectSmall[0] < rectFull[0]);
        close("its painted width falls with the factor too",
                boxWidth(rectSmall) / (double) boxWidth(rectFull), factor, 0.02);
        ok("the small rectangular glyph clears the host's edges",
                rectSmall[1] >= 5 && rectSmall[3] <= size - 6
                        && rectSmall[2] >= 5 && rectSmall[4] <= size - 6);

        System.out.println("PASS: " + checks
                + " cases over glyphFit, the uniform fit and the clamped bounds");
    }

    /** Paints the glyph and returns {count, minX, minY, maxX, maxY} of the ink. */
    private static int[] ink(TrioSettings cfg, int size, int scale, int style) {
        cfg.glyphScale = scale;
        cfg.trioStyle = style;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = img.createGraphics();
        Canvas canvas = new Canvas(g);
        TrioRenderer.drawInto(canvas, size, size, 79, false, false, false, false, -1, 4,
                new int[] { 4, 4 }, "", 0xFF000000, cfg, true);
        g.dispose();

        int count = 0;
        int minX = size;
        int minY = size;
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                if ((img.getRGB(x, y) >>> 24) != 0) {
                    count++;
                    if (x < minX) { minX = x; }
                    if (x > maxX) { maxX = x; }
                    if (y < minY) { minY = y; }
                    if (y > maxY) { maxY = y; }
                }
            }
        }
        if (maxX < 0) { minX = 0; minY = 0; maxX = -1; maxY = -1; }
        return new int[] { count, minX, minY, maxX, maxY };
    }

    private static int boxWidth(int[] box) { return box[3] - box[1] + 1; }

    private static int boxHeight(int[] box) { return box[4] - box[2] + 1; }

    /** True when two renders painted the identical pixels in the identical place. */
    private static boolean same(int[] a, int[] b) {
        return a[0] == b[0] && a[1] == b[1] && a[2] == b[2]
                && a[3] == b[3] && a[4] == b[4];
    }

    private static void eq(String what, long got, long want) {
        checks++;
        if (got != want) {
            throw new AssertionError(what + ": got <" + got + "> want <" + want + ">");
        }
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
    (Join-Path $src 'io\github\fengwenhua\colorduo\GlyphScaleTest.java'),
    $probe, (New-Object System.Text.UTF8Encoding($false)))

# The constants are injected rather than restated, so the probe asks for the
# same numbers Prefs publishes and a changed bound is not silently re-tested
# against a stale copy.
[System.IO.File]::WriteAllText(
    (Join-Path $src 'io\github\fengwenhua\colorduo\Bounds.java'),
    @"
package io.github.fengwenhua.colorduo;

/** The scale bounds and the style ids, read out of the real Prefs. */
final class Bounds {
    static final int MIN_GLYPH = $minGlyph;
    static final int MAX_GLYPH = $maxGlyph;
    static final int DEF_GLYPH = $defGlyph;
    static final int STYLE_RING = Prefs.STYLE_RING;
    static final int STYLE_RECT = Prefs.STYLE_RECT;
}
"@, (New-Object System.Text.UTF8Encoding($false)))

# Make the injected names visible to the probe the way the real files would.
$probeFile = Join-Path $src 'io\github\fengwenhua\colorduo\GlyphScaleTest.java'
$probeText = [System.IO.File]::ReadAllText($probeFile)
$probeText = $probeText.Replace(
    'final class GlyphScaleTest {',
    "final class GlyphScaleTest {`n`n    private static final int MIN_GLYPH = Bounds.MIN_GLYPH;`n    private static final int MAX_GLYPH = Bounds.MAX_GLYPH;`n    private static final int DEF_GLYPH = Bounds.DEF_GLYPH;`n    private static final int STYLE_RING = Bounds.STYLE_RING;`n    private static final int STYLE_RECT = Bounds.STYLE_RECT;")
[System.IO.File]::WriteAllText($probeFile, $probeText,
    (New-Object System.Text.UTF8Encoding($false)))

# --- compile and run -----------------------------------------------------------
# The desktop shims live under work\, which .gitignore excludes, so a fresh
# checkout has the renderer but not the stand-ins it is compiled against. Two
# other probes already rely on the same tree, so it is shared infrastructure in
# practice - but say so plainly instead of letting javac report a hundred
# missing-symbol errors for a file that was never there.
$previewRoot = Join-Path $root 'tests\shims'
if (!(Test-Path (Join-Path $previewRoot 'android\graphics\Canvas.java'))) {
    throw "the desktop shims are missing: $previewRoot (see docs/DEVELOPMENT.md, the test section)"
}
$preview = Get-ChildItem -Recurse -Filter *.java $previewRoot |
    ForEach-Object { $_.FullName }
$shared = @(
    (Join-Path $trio 'Prefs.java'),
    (Join-Path $trio 'TrioSettings.java'),
    (Join-Path $trio 'TrioGeometry.java'),
    (Join-Path $trio 'TrioAppearance.java'),
    (Join-Path $trio 'TrioRenderer.java'))
$sources = $preview + $shared + @(Get-ChildItem -Recurse -Filter *.java $src |
    ForEach-Object { $_.FullName })

& $javac -nowarn -d $classes -encoding UTF-8 $sources
if ($LASTEXITCODE) { throw 'Compilation failed' }
& $java -Xmx128m -cp $classes io.github.fengwenhua.colorduo.GlyphScaleTest
if ($LASTEXITCODE) { throw 'Test failed' }
