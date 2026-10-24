$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$jdk=Join-Path $root '.tools/jdk/jdk-21.0.12.1+1/bin'
$javac=Join-Path $jdk 'javac.exe'; $java=Join-Path $jdk 'java.exe'
if(!(Test-Path $javac)){$javac='javac';$java='java'}

$trio="$root/app/src/main/java/io/github/fengwenhua/colorduo"
$hooks=Get-Content "$trio/TrioHooks.java" -Raw
$rend=Get-Content "$trio/TrioRenderer.java" -Raw
$geom=Get-Content "$trio/TrioGeometry.java" -Raw
$appear=Get-Content "$trio/TrioAppearance.java" -Raw
$prefs=Get-Content "$trio/Prefs.java" -Raw

# The real production bodies, spliced in unchanged. A rename or a re-shape
# fails loudly here rather than silently testing a copy that drifted. Every
# pattern requires the whole signature and the closing brace of the method, so a
# match can never be whitespace-only and slip past the guard below.
$perfTextSize=[regex]::Match($hooks,'(?s)private static float perfTextSize\(String text, View container, View host\) \{.*?\n    \}').Value
if(!$perfTextSize){throw 'Production perfTextSize missing'}
$perfLineSpacing=[regex]::Match($hooks,'(?s)private static float perfLineSpacing\(TextView view, String text,\s*\n\s*View container\) \{.*?\n    \}').Value
if(!$perfLineSpacing){throw 'Production perfLineSpacing missing'}
$perfLines=[regex]::Match($hooks,'(?s)private static int perfLines\(String text\) \{.*?\n    \}').Value
if(!$perfLines){throw 'Production perfLines missing'}
$perfLineHeight=[regex]::Match($hooks,'(?s)private static float perfLineHeight\(float size, int weight\) \{.*?\n    \}').Value
if(!$perfLineHeight){throw 'Production perfLineHeight missing'}
$outTypeSizePx=[regex]::Match($hooks,'(?s)private static float outTypeSizePx\(View container\) \{.*?\n    \}').Value
if(!$outTypeSizePx){throw 'Production outTypeSizePx missing'}

# The reserve width. It is the fix for the icon row that used to step sideways
# every time the wattage changed: the strip MIUI is asked to give up is one
# scalar for every reading view and it arrives through setPadding, so measuring
# the live text made the whole row move with the digits. Spliced, not restated -
# the answer this function gives is the thing under test.
$perfWidth=[regex]::Match($hooks,'(?s)private static int perfWidth\(PerfLabel perf, String text\) \{.*?\n    \}').Value
if(!$perfWidth){throw 'Production perfWidth missing'}

# The overall-size multiplier, spliced rather than restated. A probe that wrote
# its own `perfScale / 100f` would keep passing if production's clamp were
# dropped or its scale factor changed, and the multiplier is the whole point of
# this round. The three bounds come from Prefs for the same reason.
$perfFit=[regex]::Match($appear,'(?s)public float perfFit\(\) \{.*?\n    \}').Value
if(!$perfFit){throw 'Production perfFit missing'}
# The glyph's own multiplier, for the same reason: the reading is sized to the
# height the glyph is actually painted at, and that height shrinks with the
# glyph slider. Restating `glyphScale / 100f` here would leave the coupling
# untested the moment production's clamp or divisor moved.
$glyphFit=[regex]::Match($appear,'(?s)public float glyphFit\(\) \{.*?\n    \}').Value
if(!$glyphFit){throw 'Production glyphFit missing'}
# The readout's own weight, spliced for the same reason as the multipliers: the
# reading stopped following the type label's weight when it got a slider of its
# own, and perfLineHeight takes the weight as an argument because the weight is
# what decides the line height. Restating the clamp here would let the probe
# keep passing after the bounds moved, and passing the label's weight instead
# would measure a face the reading is not drawn in.
$perfTypeWeight=[regex]::Match($appear,'(?s)public int perfTypeWeight\(\) \{.*?\n    \}').Value
if(!$perfTypeWeight){throw 'Production perfTypeWeight missing'}
$clamp=[regex]::Match($prefs,'(?s)static int clamp\(int value, int min, int max\) \{.*?\n    \}').Value
if(!$clamp){throw 'Production Prefs.clamp missing'}
$minPerfScale=[regex]::Match($prefs,'static final int MIN_PERF_SCALE = ([0-9]+);').Groups[1].Value
$maxPerfScale=[regex]::Match($prefs,'static final int MAX_PERF_SCALE = ([0-9]+);').Groups[1].Value
$minGlyphScale=[regex]::Match($prefs,'static final int MIN_GLYPH_SCALE = ([0-9]+);').Groups[1].Value
$maxGlyphScale=[regex]::Match($prefs,'static final int MAX_GLYPH_SCALE = ([0-9]+);').Groups[1].Value
$minPerfWeight=[regex]::Match($prefs,'static final int MIN_PERF_WEIGHT = ([0-9]+);').Groups[1].Value
$maxPerfWeight=[regex]::Match($prefs,'static final int MAX_PERF_WEIGHT = ([0-9]+);').Groups[1].Value
$defPerfWeight=[regex]::Match($prefs,'static final int DEF_PERF_WEIGHT = ([0-9]+);').Groups[1].Value
if(!$minPerfScale -or !$maxPerfScale){throw 'could not read the perf-scale bounds from Prefs'}
if(!$minGlyphScale -or !$maxGlyphScale){throw 'could not read the glyph-scale bounds from Prefs'}
if(!$minPerfWeight -or !$maxPerfWeight -or !$defPerfWeight){throw 'could not read the perf-weight bounds from Prefs'}

# The weight the readout is drawn at reaching production's own call sites. The
# desktop face cannot see a weight at all (AWT answers identical metrics for
# plain and bold), so a metric assertion can never catch the readout being drawn
# at the type label's weight instead of its own - which is exactly what it did
# before the slider existed. These patterns are the lock instead.
foreach($needle in @(
    'perfLineHeight\(base, a\.perfTypeWeight\(\)\)',
    # The readout is dressed through the shared styling helper rather than a bare
    # setTypeface: the helper also applies the synthetic embolden a custom
    # single-weight font needs, and keeping that pairing in one place is what
    # stops the two halves drifting apart. The weight still has to be the
    # readout's own, which is what the last two patterns pin.
    'TrioRenderer\.styleTextView\(perf, weight\)',
    'perf\.weight != weight',
    'perf\.weight = weight'
)){
    if(-not [regex]::IsMatch($hooks, $needle)){throw "production is missing: $needle"}
}
# ...and the reading must NOT be built from the type label's weight, which is the
# regression the splice above exists to prevent.
if([regex]::IsMatch($hooks, 'final int weight = a\.typeWeight;')){throw 'the readout still follows the type label weight'}
if([regex]::IsMatch($hooks, 'perfLineHeight\([^)]*a\.typeWeight\)')){throw 'the readout line height still follows the type label weight'}
# The re-sync test has to ask about the weight too, or the slider does nothing on
# the rows where the size is pinned at the floor - a weight change leaves the
# size identical there, so no other term can see it.
$resync=[regex]::Match($hooks,'(?s)if \(perf\.getVisibility\(\) != View\.VISIBLE.*?requestPerfSync\(container\);').Value
if(-not $resync){throw 'could not read the re-sync test out of TrioHooks'}
foreach($term in @('perf\.getVisibility\(\)','contentEquals','getTextSize','getLineSpacingMultiplier','perf\.weight != weight',
        # A font swap moves the face without touching any of the terms above, so
        # the gate has to ask about it too or picking a new font would not reach
        # the row until something unrelated happened to change.
        'perf\.fontGeneration != TrioRenderer\.fontGeneration\(\)')){
    if(-not [regex]::IsMatch($resync, $term)){throw "the re-sync test does not ask about: $term"}
}

# The reserved strip must not be measured off the live text. It is what MIUI is
# told to give up, through setPadding, so a width that follows the digits
# re-pads the icon row and shoves every native icon on that side of the bar
# whenever the reading changes - about once a second while charging. perfWidth
# answers for the widest form the text could take instead. The strip is the only
# consumer, so the lock is on that call site.
$strip=[regex]::Match($hooks,'(?s)private static OutRingLayout.Plan outRingPlan\(ViewGroup container, View icons\) \{.*?\n    \}').Value
if(-not $strip){throw 'could not read the shared strip plan'}
if(-not [regex]::IsMatch($strip,'outRingSlotWidth\(views\[i\]\)')){throw 'the strip does not share the placement slot width'}
$slotWidth=[regex]::Match($hooks,'(?s)private static int outRingSlotWidth\(View view\) \{.*?\n    \}').Value
if(-not [regex]::IsMatch($slotWidth,'perfWidth\(perf,')){throw 'performance slot is not measured through perfWidth'}
if([regex]::IsMatch($strip,'perf\.getMeasuredWidth\(\)')){throw 'the reserved strip still follows the live text width'}
# perfWidth itself must widen every digit and split on the line break: measuring
# the whole string in one call adds the two lines together and reserves the
# reading at twice its real width.
if(-not [regex]::IsMatch($hooks,'(?s)private static int perfWidth\(PerfLabel perf, String text\) \{.*?\n    \}')){throw 'Production perfWidth missing'}
$widthBody=[regex]::Match($hooks,'(?s)private static int perfWidth\(PerfLabel perf, String text\) \{.*?\n    \}').Value
if(-not [regex]::IsMatch($widthBody,'PERF_BREAK')){throw 'perfWidth does not split on the line break'}
if(-not [regex]::IsMatch($widthBody,'c >= .0. && c <= .9.')){throw 'perfWidth does not widen the digits'}

# Neither teardown path may hand the strip back on its own way out. The strip is
# shared: removing the out-of-type label or the readout is the common case on
# every sync (the out-of-type label is off by default), and a release before the
# null check returns the icons' padding without restoring whatever else is still
# mounted. The next reserve puts it back, so the padding ping-pongs once per
# power update - about once a second while charging - and the whole native icon
# row slides in and out. reserveOutRingStrip totals what is actually on screen and
# releases the strip itself when that total is zero, so it is the only writer
# allowed to answer this question.
foreach($pair in @(
    @('removeOutTypeLabel', 'OutTypeLabel'),
    @('removePerfLabel', 'PerfLabel')
)){
    $name=$pair[0]
    $body=[regex]::Match($hooks,"(?s)private static void $name\(View container\) \{.*?\n    \}").Value
    if(-not $body){throw "Production $name missing"}
    if([regex]::IsMatch($body,'releaseOutTypeSpace\(')){throw "$name still releases the strip before it knows what is mounted"}
    if(-not [regex]::IsMatch($body,'reserveOutRingStrip\(')){throw "$name no longer recomputes the strip"}
}

# The renderer's four measurement methods, so the probe measures with the same
# arithmetic the glyph is drawn with rather than with a re-implementation.
$inkScale1=[regex]::Match($rend,'(?s)static float inkScale\(int w, int h\) \{.*?\n    \}').Value
if(!$inkScale1){throw 'Production inkScale(w,h) missing'}
$inkScale2=[regex]::Match($rend,'(?s)static float inkScale\(int w, int h, boolean rect\) \{.*?\n    \}').Value
if(!$inkScale2){throw 'Production inkScale(w,h,rect) missing'}
# The style-keyed overload. perfTextSize picks its ink box from the style, so the
# probe needs the same three-way fit the glyph is drawn with - without it the
# spliced perfTextSize below would not compile at all.
$inkScale4=[regex]::Match($rend,'(?s)static float inkScale\(int w, int h, int style\) \{.*?\n    \}').Value
if(!$inkScale4){throw 'Production inkScale(w,h,style) missing'}
$inkScale3=[regex]::Match($rend,'(?s)static float inkScale\(int w, int h, float inkW, float inkH\) \{.*?\n    \}').Value
if(!$inkScale3){throw 'Production inkScale(w,h,inkW,inkH) missing'}
$typefaceFor=[regex]::Match($rend,'(?s)static Typeface typefaceFor\(int weight\) \{.*?\n    \}').Value
if(!$typefaceFor){throw 'Production typefaceFor missing'}

# The geometry and the two floors, read out of the sources so a deliberate
# change propagates and an accidental one shows up as a failed invariant.
$inkW=[regex]::Match($geom,'static final float INK_W = ([0-9.]+)f;').Groups[1].Value
$inkH=[regex]::Match($geom,'static final float INK_H = ([0-9.]+)f;').Groups[1].Value
$rectInkW=[regex]::Match($geom,'static final float RECT_INK_W = ([0-9.]+)f;').Groups[1].Value
$rectInkH=[regex]::Match($geom,'static final float RECT_INK_H = ([0-9.]+)f;').Groups[1].Value
if(!$inkW -or !$inkH -or !$rectInkW -or !$rectInkH){throw 'could not read the ink boxes from TrioGeometry'}
# The capsule's ink box is derived, not a literal: it is the outline's bounds
# grown by half the heaviest stroke the slider offers. Its inputs are read and its
# derivation is emitted verbatim, so the harness measures with the production
# arithmetic instead of a transcription of it that could silently disagree.
$capLeft=[regex]::Match($geom,'static final float CAP_LEFT = ([0-9.]+)f;').Groups[1].Value
$capRight=[regex]::Match($geom,'static final float CAP_RIGHT = ([0-9.]+)f;').Groups[1].Value
$capTop=[regex]::Match($geom,'static final float CAP_TOP = ([0-9.]+)f;').Groups[1].Value
$capBottom=[regex]::Match($geom,'static final float CAP_BOTTOM = ([0-9.]+)f;').Groups[1].Value
$capStrokeRatio=[regex]::Match($geom,'static final float CAP_STROKE_RATIO = ([0-9.]+)f;').Groups[1].Value
$maxRingStroke=[regex]::Match($prefs,'static final int MAX_RING_STROKE = (\d+);').Groups[1].Value
if(!$capLeft -or !$capRight -or !$capTop -or !$capBottom -or !$capStrokeRatio -or !$maxRingStroke){
    throw 'could not read the capsule ink inputs from TrioGeometry/Prefs'}
$capInkDerivation=[regex]::Match($geom,'(?s)static final float CAP_INK_X =.*?CAP_INK_H =[^;]*;').Value
if(!$capInkDerivation){throw 'could not read the capsule ink derivation from TrioGeometry'}
# The two opening bands, and the box that includes them. The renderer fits the drawing to the
# BANDED box (so an opening's reading has room outside the capsule), while the width asked of
# the status bar is still derived from the bare one - that split is what keeps the capsule the
# size it shipped at, so the harness has to reproduce both. Spliced from the real source rather
# than retyped, for the same reason as everything else here.
$capBandH=[regex]::Match($geom,'static final float CAP_BAND_H = ([0-9.]+)f;').Groups[1].Value
if(!$capBandH){throw 'could not read CAP_BAND_H from TrioGeometry'}
$capInkTop=[regex]::Match($geom,'static final float CAP_INK_TOP = ([^;]+);').Groups[1].Value
if(!$capInkTop){throw 'could not read CAP_INK_TOP from TrioGeometry'}
$capInkBottom=[regex]::Match($geom,'static final float CAP_INK_BOTTOM = ([^;]+);').Groups[1].Value
# The ink box is a FUNCTION now (the capsule can be lengthened), so the stub cannot carry it as
# a constant: it recomputes it from the same inputs the real file does.
$capInkX=[regex]::Match($geom,'static final float CAP_INK_X = ([^;]+);').Groups[1].Value
$maxRingStroke=[regex]::Match($prefs,'MAX_RING_STROKE = ([0-9.]+)').Groups[1].Value
$capStrokeRatio=[regex]::Match($geom,'static final float CAP_STROKE_RATIO = ([0-9.]+)f;').Groups[1].Value
if(!$capInkBottom){throw 'could not read CAP_INK_BOTTOM from TrioGeometry'}
# The style ordinals, read for the same reason: the spliced inkScale(w,h,style)
# switches on them, and a stub that restated 0/1/2 could drift from the real ones.
$styleRing=[regex]::Match($prefs,'static final int STYLE_RING = (\d+);').Groups[1].Value
$styleRect=[regex]::Match($prefs,'static final int STYLE_RECT = (\d+);').Groups[1].Value
$styleCapsule=[regex]::Match($prefs,'static final int STYLE_CAPSULE = (\d+);').Groups[1].Value
if(!$styleRing -or !$styleRect -or !$styleCapsule){throw 'could not read the style ordinals from Prefs'}
$minSize=[regex]::Match($hooks,'static final float PERF_MIN_SIZE_RATIO = ([0-9.]+)f;').Groups[1].Value
$minSpace=[regex]::Match($hooks,'static final float PERF_MIN_LINE_SPACING = ([0-9.]+)f;').Groups[1].Value
if(!$minSize -or !$minSpace){throw 'could not read the perf floors from TrioHooks'}

# The separator itself, read out of the production source rather than restated
# in the generated test. The separator IS the feature - two readings print one
# per line only because this character is a break - so a probe that hard-coded
# '\n' would keep passing after the join was turned back into a space. The
# matched text is the Java escape as written (e.g. the two characters \ and n),
# which is exactly what has to be emitted inside the char literal below.
$perfBreak=[regex]::Match($hooks,"static final char PERF_BREAK = '(.+?)';").Groups[1].Value
if(!$perfBreak){throw 'could not read PERF_BREAK from TrioHooks'}

# What the pure functions above cannot reach: the wiring only a layout pass
# visits. perfLineSpacing is spliced and tested directly, but nothing on a
# desktop can call refreshPerfLabel - it walks a real view tree - so the two
# statements that keep the leading in step, and the type weight that decides
# which face the reading is measured in, are pinned by their shape instead of by
# a call. Each is a real failure: dropping any one of them leaves the reading
# drawn with stale leading, or with the wrong weight, and no other case here
# would notice.
$perfWeightWired=[regex]::IsMatch($perfLineHeight,
    'PERF_PROBE\.setTypeface\(TrioRenderer\.typefaceFor\(weight\)\);')
$perfResyncWired=[regex]::IsMatch($hooks,
    'perf\.getLineSpacingMultiplier\(\)\s*\n\s*!= perfLineSpacing\(perf, text, container\)')
$perfApplyWired=[regex]::IsMatch($hooks,
    'if \(perf\.getLineSpacingMultiplier\(\) != spacing\) \{\s*\n\s*perf\.setLineSpacing\(0f, spacing\);')
if(!$perfWeightWired){throw 'perfLineHeight no longer applies the type weight'}
if(!$perfResyncWired){throw 'the re-sync test no longer compares the line spacing'}
if(!$perfApplyWired){throw 'the line spacing is no longer written conditionally'}
foreach($v in @($minSize,$minSpace)){
    if([double]$v -le 0 -or [double]$v -ge 1){throw "a perf floor is outside (0,1): $v"}
}

$src="$root/work/perflayout/src"
$classes="$root/work/perflayout/classes"
# Both trees are wiped, not just the classes. The compile step below takes every
# .java under $src, so a file emitted by an earlier version of this script and no
# longer written would stay there and still be compiled - a stale definition that
# can shadow a live one and let the probe pass against code that is not here
# anymore. Re-emitting the whole tree each run is what makes that impossible.
if(Test-Path $src){Remove-Item -Recurse -Force $src}
if(Test-Path $classes){Remove-Item -Recurse -Force $classes}
New-Item -ItemType Directory -Force $classes | Out-Null

function Emit([string]$rel,[string]$text){
    $p=Join-Path $src $rel
    New-Item -ItemType Directory -Force (Split-Path $p -Parent) | Out-Null
    [System.IO.File]::WriteAllText($p,$text,(New-Object System.Text.UTF8Encoding($false)))
}

# The typeface already has a real desktop stand-in and is copied, so a family
# the renderer asks for resolves the same way it does in the preview workbenches.
# It comes from tests\shims (tracked) and not from the preview workbench's copy
# under work\ (gitignored), so this script still runs in a fresh clone.
$gfx=Join-Path $src 'android/graphics'
New-Item -ItemType Directory -Force $gfx | Out-Null
Copy-Item (Join-Path $root 'tests/shims/android/graphics/Typeface.java') (Join-Path $gfx 'Typeface.java') -Force

# The paint is written here rather than copied. The preview's stand-in returns
# AWT's ascent, which is positive; Android's FontMetrics.ascent is negative, and
# both the size fit and the leading measure a line as descent - ascent. With the
# AWT sign that expression comes out negative, every caller takes its
# "cannot measure" fallback, and the probe would pass while measuring nothing.
Emit 'android/graphics/Paint.java' @'
package android.graphics;

import java.awt.Font;

/** Desktop paint whose font metrics follow Android's sign convention. */
public class Paint {

    public static class FontMetrics {
        public float ascent;
        public float descent;
    }

    private float textSize = 12f;
    private String family = "SansSerif";
    private int typefaceStyle = Font.PLAIN;
    private boolean fakeBold = false;

    public void setTextSize(float s) { textSize = s; }

    public float getTextSize() { return textSize; }

    public void setTypeface(Typeface t) {
        if (t != null) {
            family = t.family();
            typefaceStyle = t.style();
        }
    }

    /**
     * Synthetic emboldening. The stub keeps the flag so a caller that sets it can
     * be seen to have done so, but leaves the metrics alone: every case in this
     * harness runs without a custom font loaded, where the real renderer never
     * emboldens anything. Making AWT retype the font here would perturb the line
     * heights the cases below measure, and none of them is about the font feature.
     */
    public void setFakeBoldText(boolean bold) { fakeBold = bold; }

    public boolean isFakeBoldText() { return fakeBold; }

    public Font font() {
        return new Font(family, typefaceStyle, (int) REFERENCE);
    }

    public FontMetrics getFontMetrics() {
        // Measured once at REFERENCE and scaled, because AWT rounds a font to
        // whole points: at 36.9 and 37 it reports the same metrics, while
        // Android's are exactly linear in the text size. The size fit divides
        // a line height taken at the base size into a size taken at another, so
        // a stub that quantises makes the identity look broken when it is not.
        java.awt.Canvas probe = new java.awt.Canvas();
        java.awt.FontMetrics fm = probe.getFontMetrics(font());
        final float scale = textSize / REFERENCE;
        FontMetrics out = new FontMetrics();
        // Negated on purpose: Android reports the ascent as the negative
        // distance from the baseline, so a line is descent - ascent.
        out.ascent = -fm.getAscent() * scale;
        out.descent = fm.getDescent() * scale;
        return out;
    }

    /**
     * Summed per character, the way the desktop shims do it, and scaled from
     * REFERENCE for the same reason the metrics are.
     *
     * <p>Note for the digit cases below: on this face every digit is exactly
     * 556/1000 wide (measured), so replacing a digit with the widest digit is a
     * no-op here and could not be observed through it. The checks that the
     * substitution really happens therefore use a face whose digits differ,
     * which is what a real status-bar face does; this one stays faithful to AWT
     * so the line height and spacing cases keep measuring real metrics.
     */
    public float measureText(String text) {
        if (text == null || text.length() == 0) {
            return 0f;
        }
        java.awt.Canvas probe = new java.awt.Canvas();
        java.awt.FontMetrics fm = probe.getFontMetrics(font());
        float total = 0f;
        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);
            if (digitWidths != null && c >= '0' && c <= '9') {
                total += digitWidths[c - '0'];
            } else {
                total += fm.charWidth(c);
            }
        }
        return total * (textSize / REFERENCE);
    }

    private float[] digitWidths;

    /**
     * Test hook: per-digit widths at REFERENCE, or null to measure the real
     * face. The wide-awake status-bar faces draw their digits at different
     * widths - the device log shows "0.0W" at 102px and "6.1W" at 92px, same
     * character count - and that difference is the whole reason the reserve
     * used to step sideways. AWT's SansSerif gives all ten digits the same 556,
     * so the substitution perfWidth makes is unobservable through it; setting a
     * table here is what lets the cases below see it. Left null everywhere else
     * so the line-height and spacing cases still measure real metrics.
     */
    public void setDigitWidths(float[] widths) { digitWidths = widths; }

    /** Font size the metrics are measured at, before scaling to textSize. */
    private static final float REFERENCE = 1000f;
}
'@

Emit 'android/view/View.java' @'
package android.view;

import android.content.res.Resources;

/** A view with a frame, a frame's padding, and a density to measure dp with. */
public class View {

    private int width;
    private int height;
    private int paddingTop;
    private int paddingBottom;
    private final Resources resources = new Resources();

    public int getWidth() { return width; }

    public int getHeight() { return height; }

    public int getPaddingTop() { return paddingTop; }

    public int getPaddingBottom() { return paddingBottom; }

    public Resources getResources() { return resources; }

    public int getMeasuredWidth() { return width; }

    public void setSize(int w, int h) { width = w; height = h; }

    public void setPadding(int top, int bottom) { paddingTop = top; paddingBottom = bottom; }
}
'@

Emit 'android/view/TextView.java' @'
package android.view;

import android.graphics.Paint;

/**
 * The two things the readout needs from a text view: its paint, and a line
 * spacing multiplier that reads back exactly what was written.
 *
 * <p>Android stores the multiplier verbatim and returns it unchanged from
 * getLineSpacingMultiplier(), so the real one round-trips bit for bit. Storing
 * it here the same way is what makes the fixpoint case below meaningful: the
 * re-sync test compares the stored value against a freshly computed one, and if
 * that comparison could never settle the status bar would post a layout on every
 * pass. A stub that returned a rounded or rewritten number would hide exactly
 * that hazard.
 */
public class TextView extends View {

    private final Paint paint = new Paint();
    private float spacingMultiplier = 1f;

    public Paint getPaint() { return paint; }

    public void setLineSpacing(float add, float mult) { spacingMultiplier = mult; }

    public float getLineSpacingMultiplier() { return spacingMultiplier; }
}
'@

Emit 'android/content/res/Resources.java' @'
package android.content.res;

import android.util.DisplayMetrics;

public class Resources {

    private final DisplayMetrics metrics = new DisplayMetrics();

    public DisplayMetrics getDisplayMetrics() { return metrics; }
}
'@

Emit 'android/util/DisplayMetrics.java' @'
package android.util;

public class DisplayMetrics {
    public float density = 1f;
}
'@

Emit 'io/github/fengwenhua/colorduo/TrioGeometry.java' @"
package io.github.fengwenhua.colorduo;

/** The four ink boxes the fit is measured against, read from the real source. */
final class TrioGeometry {
    static final float INK_W = $($inkW)f;
    static final float INK_H = $($inkH)f;
    static final float RECT_INK_W = $($rectInkW)f;
    static final float RECT_INK_H = $($rectInkH)f;
    static final float CAP_LEFT = $($capLeft)f;
    static final float CAP_RIGHT = $($capRight)f;
    static final float CAP_TOP = $($capTop)f;
    static final float CAP_BOTTOM = $($capBottom)f;
    static final float CAP_STROKE_RATIO = $($capStrokeRatio)f;

    $capInkDerivation
    static final float CAP_BAND_H = $($capBandH)f;
    static final float CAP_INK_TOP = $($capInkTop);
    static final float CAP_INK_BOTTOM = $($capInkBottom);
    // The drawn width. The stub never lengthens the capsule, so this is the reference one.
    static float capRight() { return CAP_RIGHT; }
}
"@

Emit 'io/github/fengwenhua/colorduo/Prefs.java' @"
package io.github.fengwenhua.colorduo;

/** The bounds the two fit multipliers and the weight clamp themselves to, read from the real Prefs. */
final class Prefs {
    static final int MIN_PERF_SCALE = $minPerfScale;
    static final int MAX_PERF_SCALE = $maxPerfScale;
    static final int MIN_GLYPH_SCALE = $minGlyphScale;
    static final int MAX_GLYPH_SCALE = $maxGlyphScale;
    static final int MIN_PERF_WEIGHT = $minPerfWeight;
    static final int MAX_PERF_WEIGHT = $maxPerfWeight;
    static final int MAX_RING_STROKE = $maxRingStroke;
    // The style ordinals the spliced inkScale(w,h,style) switches on, read from
    // the real Prefs so the stub cannot disagree with the renderer about them.
    static final int STYLE_RING = $styleRing;
    static final int STYLE_RECT = $styleRect;
    static final int STYLE_CAPSULE = $styleCapsule;

    $clamp
}
"@

Emit 'io/github/fengwenhua/colorduo/TrioAppearance.java' @"
package io.github.fengwenhua.colorduo;

/** Only the fields the size fit reads, plus the real accessors. */
final class TrioAppearance {
    boolean rect;
    boolean capsule;
    int style;
    int typeWeight = 400;
    int outTypeSize = 14;
    int perfScale = 100;
    int glyphScale = 100;
    int perfWeight = $defPerfWeight;

    $glyphFit
    $perfFit
    $perfTypeWeight
}
"@

Emit 'io/github/fengwenhua/colorduo/TrioConfig.java' @'
package io.github.fengwenhua.colorduo;

final class TrioConfig {

    private static final TrioAppearance SNAPSHOT = new TrioAppearance();

    static TrioAppearance appearance() { return SNAPSHOT; }
}
'@

Emit 'io/github/fengwenhua/colorduo/TrioRenderer.java' @"
package io.github.fengwenhua.colorduo;

import android.graphics.Typeface;

/** The renderer's own measurement methods, spliced so there is one arithmetic. */
final class TrioRenderer {

    private static final Typeface TEXT_BASE = Typeface.create("sans-serif", Typeface.NORMAL);

    /**
     * No custom font is loaded here: this harness checks the readout's arithmetic,
     * not the font feature. The field exists because the spliced {@code
     * typefaceFor} and {@code fakeBoldFor} read it, and null is the "system face"
     * branch - the one every case below asserts against.
     *
     * <p>The production styling helper for a real label is deliberately absent:
     * nothing spliced here calls it, and it names a platform class this stripped
     * stub does not carry.
     */
    private static final Typeface sFontFace = null;

    private static final int FAKE_BOLD_FROM = 600;

    static boolean fakeBoldFor(int weight) {
        return sFontFace != null && weight >= FAKE_BOLD_FROM;
    }

    $inkScale1

    $inkScale2

    $inkScale4

    $inkScale3

    $typefaceFor
}
"@

Emit 'io/github/fengwenhua/colorduo/PerfLayoutTest.java' @"
package io.github.fengwenhua.colorduo;

import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.TextView;
import android.view.View;

public class PerfLayoutTest {

    static final float PERF_MIN_SIZE_RATIO = $($minSize)f;
    static final float PERF_MIN_LINE_SPACING = $($minSpace)f;
    static final char PERF_BREAK = '$perfBreak';
    private static final Paint PERF_PROBE = new Paint();

    $perfLines

    $perfLineHeight

    $perfTextSize

    $perfLineSpacing

    $perfWidth

    $outTypeSizePx

    /** Stands in for TrioHooks.PerfLabel: a text view with a frame and a paint. */
    static final class PerfLabel extends TextView {
    }

    static int checks;

    static void eq(String what, Object got, Object want){
        checks++;
        if(!String.valueOf(got).equals(String.valueOf(want))){
            throw new AssertionError(what + ": got <" + got + "> want <" + want + ">");
        }
    }

    static void ok(String what, boolean fine){
        checks++;
        if(!fine){
            throw new AssertionError(what);
        }
    }

    static void close(String what, float got, float want, float tol){
        checks++;
        if(Math.abs(got - want) > tol){
            throw new AssertionError(what + ": got <" + got + "> want <" + want + ">");
        }
    }

    static View box(int w, int h){
        View v = new View();
        v.setSize(w, h);
        return v;
    }

    public static void main(String[] args){
        TrioConfig.appearance().outTypeSize = 14;
        TrioConfig.appearance().rect = false;
        TrioConfig.appearance().typeWeight = 400;

        View container = box(491, 88);
        container.getResources().getDisplayMetrics().density = 3f;
        final float base = outTypeSizePx(container);
        eq("the base size is the dp setting times the density", base, 42f);

        // ---- one reading keeps the type size whatever the row does.
        final String single = "31\u00B0C";
        final String pair = "6.1W" + PERF_BREAK + "31\u00B0C";
        // The separator is spliced out of the production source above, so the
        // cases below are built from whatever it really is. This is the lock on
        // its value: the feature is that the two readings sit on separate
        // lines, so the separator has to be a line break and nothing else. A
        // change back to ' ' would make every two-line case below a one-line
        // case while still "passing" a probe that had restated '\n' itself.
        eq("the two readings are separated by a line break", (int) PERF_BREAK, (int) '\n');
        eq("a pair is two lines", perfLines(pair), 2);
        eq("a single reading is one", perfLines(single), 1);
        eq("one line keeps the base size", perfTextSize(single, container, box(10, 10)), base);
        eq("one line keeps it even with no host", perfTextSize(single, container, null), base);

        // ---- the pair: shrink to one icon height, but never disappear.
        eq("a pair with no host falls back to the base size", perfTextSize(pair, container, null), base);
        eq("a pair in an unmeasured host falls back too", perfTextSize(pair, container, box(0, 0)), base);
        eq("a host far taller than the pair is clamped at the base size", perfTextSize(pair, container, box(1000, 1000)), base);

        final float lhBase = perfLineHeight(base, TrioConfig.appearance().typeWeight);
        ok("the base line height is positive", lhBase > 0f);

        // The identity below holds only while the fit is deciding: the host has
        // to want a size strictly between the base and the floor, i.e. an icon
        // height between 1.2 and 2 base line heights. Derive the host from the
        // measured line height instead of hard-coding pixel sizes, so the case
        // stays in that window whatever line-height ratio the desktop font has
        // (AWT's SansSerif is taller than the device's face, and a hard-coded
        // host that is fine on one is inside the floor's reach on the other).
        final int iconPx = Math.round(lhBase * 1.8f);
        final View sized = box(1000, iconPx);
        final float iconHeight = iconPx;
        ok("the host is in the window where the fit decides, not the clamps",
                iconHeight > lhBase * 1.2f && iconHeight < lhBase * 2f);

        final float size = perfTextSize(pair, container, sized);
        ok("a pair beside a real icon is smaller than a single reading", size < base);
        ok("but not below the legibility floor", size >= base * PERF_MIN_SIZE_RATIO);
        close("two lines at the chosen size are one icon tall",
                2f * perfLineHeight(size, TrioConfig.appearance().typeWeight), iconHeight, 0.05f);
        close("and the chosen size is the fit's own answer, unclamped",
                size, base * (iconHeight / 2f) / lhBase, 0.05f);

        // A host so small that the fit wants an illegible size: the floor wins.
        final float floored = perfTextSize(pair, container, box(10, 10));
        close("a tiny host hits the floor", floored, base * PERF_MIN_SIZE_RATIO, 0.001f);
        // The floor's own value, stated here as an external fact. The case above
        // compares the answer against the same constant production clamped to, so
        // it holds however that constant moves; this one can fail, and it is what
        // catches the floor being lowered to the point where the reading stops
        // being legible.
        eq("the legibility floor is 0.6 of the type size", PERF_MIN_SIZE_RATIO, 0.6f);
        ok("and the unclamped fit really was below it", perfTextSize(single, container, box(10, 10)) == base);
        // More readings share the same icon, so each line gets less of it. The
        // host is the SAME one the pair was measured in: comparing two line
        // counts in different hosts compares two different fits. The original
        // shape here measured the trio in a tiny host of its own, where
        // perfTextSize answers with the floor whatever the line count is, so
        // hard-coding production's line count to two still passed. In this host
        // the pair fits and the trio does not, so the two answers differ
        // precisely because the count is read.
        final String trio = "6.1W" + PERF_BREAK + "31\u00B0C" + PERF_BREAK + "40\u00B0C";
        eq("three readings are three lines", perfLines(trio), 3);
        final float size3 = perfTextSize(trio, container, sized);
        ok("three lines are smaller than two in the same host", size3 < size);

        // The rect arrangement has a shorter ink box (114.4 against 117.5), so
        // in a host whose width limits the fit it comes out a little smaller.
        // The host is wide enough that the result stays above the floor; a
        // narrower one would put both arrangements on the floor and compare
        // two equal clamped values instead of the arithmetic.
        TrioConfig.appearance().rect = true;
        final float rectSize = perfTextSize(pair, container, box(84, 1000));
        TrioConfig.appearance().rect = false;
        final float roundSize = perfTextSize(pair, container, box(84, 1000));
        ok("both arrangements are off the floor here",
                rectSize > base * PERF_MIN_SIZE_RATIO && roundSize > base * PERF_MIN_SIZE_RATIO);
        // Each arrangement is fitted against ITS OWN box, and the readout's size follows the
        // box's HEIGHT (perfTextSize: icon = inkH * fit), not its width. So the taller box
        // yields the larger reading - and the ring's square (119) is taller than the
        // rectangle's (114.4).
        //
        // This is the term that flipped when the ring's box became a square centred on the
        // ring; the comment above it, which spoke about widths, was describing an older fit.
        ok("the two ink boxes are actually different",
                TrioGeometry.INK_H != TrioGeometry.RECT_INK_H
                        || TrioGeometry.INK_W != TrioGeometry.RECT_INK_W);
        ok("the taller box fits the larger reading",
                (TrioGeometry.INK_H > TrioGeometry.RECT_INK_H) == (roundSize > rectSize));

        // ---- the leading: only a stack is squeezed, and only as far as the floor.
        final TextView perf = new TextView();
        perf.getPaint().setTextSize(base);
        final Paint.FontMetrics fm = perf.getPaint().getFontMetrics();
        final float lh = fm.descent - fm.ascent;
        ok("the probe font has a positive line height", lh > 0f);

        eq("no break, no squeeze", perfLineSpacing(perf, single, container), 1f);
        eq("an empty container leaves the leading alone", perfLineSpacing(perf, pair, box(491, 0)), 1f);
        eq("a row taller than the stack leaves it alone",
                perfLineSpacing(perf, pair, box(491, Math.round(lh * 2.5f))), 1f);
        // The stack is two lines tall, so this is the room at which the
        // multiplier first reaches 1. One line's worth of room is not "the
        // stack's height" - it is half of it, and the pair cannot fit there at
        // any leading, which is why the floor answers below.
        eq("a row exactly the stack's height leaves it alone",
                perfLineSpacing(perf, pair, box(491, (int) Math.ceil(lh * 2f))), 1f);
        eq("a row of one line's height cannot fit two and hits the floor",
                perfLineSpacing(perf, pair, box(491, Math.round(lh))), PERF_MIN_LINE_SPACING);
        // One pixel under the height at which the multiplier reaches exactly 1:
        // the one case where the answer is decided by "what is left after the
        // first line, shared between the rest" rather than by either clamp. A
        // room rounded down another way can land on the same integer as the case
        // above (about 51px at these metrics), which would test the same value
        // twice and leave the whole near-boundary region unexercised.
        final int justUnder = (int) Math.ceil(lh * 2f) - 1;
        final float nearBoundary = perfLineSpacing(perf, pair, box(491, justUnder));
        ok("a row one pixel under the stack's height still squeezes", nearBoundary < 1f);
        ok("and it squeezes only just", nearBoundary > 0.9f);

        final int snug = Math.round(lh * 1.8f);
        final float m = perfLineSpacing(perf, pair, box(491, snug));
        ok("a snug row squeezes the pair", m < 1f);
        ok("but never past the floor", m >= PERF_MIN_LINE_SPACING);
        close("and the squeezed stack then fits the room", lh + m * lh, snug, 1f);
        // The bug this guards: dividing the room by the line count spreads the
        // first line's full height over all of them and leaves the stack taller
        // than the row. Dividing what is left after the first line does not.
        ok("the squeeze is not just the room over the line count",
                Math.abs(m - (snug / 2f) / lh) > 0.01f);

        final int tight = Math.round(lh * 1.2f);
        close("a row too short for any squeeze hits the leading floor",
                perfLineSpacing(perf, pair, box(491, tight)), PERF_MIN_LINE_SPACING, 0.001f);
        close("a row not much taller than one line hits it too",
                perfLineSpacing(perf, pair, box(491, Math.round(lh * 1.05f))), PERF_MIN_LINE_SPACING, 0.001f);

        // The padding belongs to the row, so it is taken out of the room.
        final View padded = box(491, snug + 40);
        padded.setPadding(20, 20);
        close("the row's own padding is not part of the room",
                perfLineSpacing(perf, pair, padded), m, 0.02f);

        // Three lines: more of the same room between more lines.
        final float m3 = perfLineSpacing(perf, trio, box(491, snug));
        ok("three readings squeeze harder than two in the same row", m3 < m);
        ok("and they still respect the floor", m3 >= PERF_MIN_LINE_SPACING);
        // In this row the unclamped multiplier would be below the floor, so the
        // floor answers and the stack is taller than the row. That is the
        // documented trade: legibility wins over fitting, and the size fit -
        // not the leading - is what normally keeps the stack inside the row.
        ok("a stack the floor cannot fit overflows the row rather than shrinking past it",
                lh + 2f * m3 * lh > snug);

        // ---- the leading has to settle. refreshPerfLabel re-posts a sync when
        // the view's stored multiplier differs from a freshly computed one, so if
        // writing the computed value did not then read back equal, every layout
        // pass would post another and the status bar would relayout forever. The
        // pure function is idempotent by construction; what is pinned here is the
        // round trip through the view, which is the half a desktop probe can
        // reach. The stub stores the multiplier verbatim the way Android does,
        // so a rounded or rewritten value shows up as a failure rather than a
        // silently passable no-op.
        perf.setLineSpacing(0f, perfLineSpacing(perf, pair, box(491, snug)));
        eq("writing the computed leading reads back unchanged, so the re-sync settles",
                perf.getLineSpacingMultiplier(), perfLineSpacing(perf, pair, box(491, snug)));
        // The value really was squeezed, so the round trip above is not the
        // trivial one where 1f is stored and 1f read back.
        ok("the settled leading is a real squeeze, not the untouched 1",
                perf.getLineSpacingMultiplier() < 1f);

        // ---- the identity measured a second way. perfLineHeight is production's
        // own answer, so comparing 2 * perfLineHeight(size) against the icon is a
        // consistency check: it catches a size formula that stopped matching the
        // host, but it would also hold if perfLineHeight itself carried a factor
        // that the size formula cancelled out. Measuring the same two lines
        // straight off a paint that never went through perfLineHeight is what
        // makes the claim independent.
        final Paint raw = new Paint();
        raw.setTextSize(size);
        final Paint.FontMetrics rawMetrics = raw.getFontMetrics();
        final float rawLine = rawMetrics.descent - rawMetrics.ascent;
        ok("the raw line height is positive", rawLine > 0f);
        close("two lines measured without perfLineHeight are one icon tall too",
                2f * rawLine, iconHeight, 0.05f);
        close("and the spliced line height agrees with the raw one",
                perfLineHeight(size, TrioConfig.appearance().typeWeight), rawLine, 0.01f);

        // ---- the overall-size setting multiplies whatever the rules settled on.
        // It is applied last and to every answer, which is what these cases pin:
        // a lone reading scales too, so moving the slider with one switch on does
        // something instead of waiting for the second switch to make it jump, and
        // the fallback branches scale with it rather than staying at the base.
        // The test value is the production floor, not an arbitrary 50: the
        // multiplier clamps, so 50 is not reachable and asking for it would only
        // re-test the clamp below.
        final float low = Prefs.MIN_PERF_SCALE / 100f;
        TrioConfig.appearance().perfScale = Prefs.MIN_PERF_SCALE;
        eq("the setting scales a lone reading", perfTextSize(single, container, box(10, 10)), base * low);
        eq("and its no-host fallback", perfTextSize(single, container, null), base * low);
        final float half = perfTextSize(pair, container, sized);
        close("a pair scales from the fit's own answer", half, size * low, 0.05f);
        close("a floored pair scales from the floor", perfTextSize(pair, container, box(10, 10)),
                base * PERF_MIN_SIZE_RATIO * low, 0.001f);
        // The bounds are clamped rather than trusted. The stored number is
        // clamped on read, but the broadcast path builds an appearance straight
        // from extras and this multiplier reaches the canvas, so an out-of-range
        // percentage has to be brought back rather than obeyed. Both directions
        // are checked against the values read out of Prefs, so moving a bound is
        // an explicit change and dropping the clamp is a failure.
        TrioConfig.appearance().perfScale = 1000;
        eq("a percentage over the ceiling clamps to it",
                perfTextSize(single, container, box(10, 10)), base * (Prefs.MAX_PERF_SCALE / 100f));
        TrioConfig.appearance().perfScale = 1;
        eq("a percentage under the floor clamps to it",
                perfTextSize(single, container, box(10, 10)), base * (Prefs.MIN_PERF_SCALE / 100f));
        TrioConfig.appearance().perfScale = 100;
        eq("and 100 restores the unscaled answer, so the shipped look is the default",
                perfTextSize(pair, container, sized), size);

        // ---- the pair is sized to the icon that is actually painted. The glyph
        // slider shrinks what is drawn inside MIUI's reserved box without moving
        // the box, so a fit measured off the box alone would keep the stack at
        // the height of an icon that is no longer there. These cases pin the
        // coupling; the slider's own bounds are read from Prefs above.
        //
        // The host is chosen so that BOTH readings land between the floor and the
        // base size. That window is narrow and it is why the case uses 10% rather
        // than the whole span: at two lines the fit is `base * (icon / 2) / line`
        // clamped to [0.6 * base, base], so a host that is unclamped at 100% is
        // already on the floor by 60% - the floor is absolute and does not shrink
        // with the glyph, so the full span is not linearly observable. What is
        // observable is the ratio wherever neither clamp is in play.
        TrioConfig.appearance().glyphScale = 100;
        final float pairFull = perfTextSize(pair, container, box(1000, 90));
        ok("the coupling case really is off both clamps",
                pairFull < base && pairFull > base * PERF_MIN_SIZE_RATIO);
        TrioConfig.appearance().glyphScale = 90;
        final float pairNine = perfTextSize(pair, container, box(1000, 90));
        close("shrinking the glyph shrinks the pair by the same factor",
                pairNine, pairFull * 0.9f, 0.05f);
        // Not merely a lower answer: the pair tracks the icon that is painted, so
        // a host that draws the same icon asks for the same text size. The drawn
        // height is taken from the renderer's own fit and the geometry constants
        // rather than from perfTextSize, so this compares two hosts instead of
        // restating the formula the size is computed from.
        final float drawnNine = Math.round(TrioGeometry.INK_H * TrioRenderer.inkScale(1000, 90, false) * 0.9f);
        TrioConfig.appearance().glyphScale = 100;
        close("a host drawing the same icon at 100% agrees",
                perfTextSize(pair, container, box(1000, (int) drawnNine)), pairNine, 0.05f);
        TrioConfig.appearance().glyphScale = 90;
        TrioConfig.appearance().glyphScale = Prefs.MIN_GLYPH_SCALE;
        final float pairSmall = perfTextSize(pair, container, box(1000, 90));
        ok("and at the smallest glyph the pair is smaller still", pairSmall < pairNine);
        // The legibility floor is absolute: it is about the text being readable,
        // not about how big the icon is, so it does not shrink with the glyph.
        // A row this small is already on the floor at either setting, which is
        // the documented trade - legibility wins over matching the icon.
        final float flooredSmall = perfTextSize(pair, container, box(10, 10));
        TrioConfig.appearance().glyphScale = 100;
        close("the legibility floor does not shrink with the glyph",
                flooredSmall, perfTextSize(pair, container, box(10, 10)), 0.001f);
        // A lone reading is deliberately left out of the coupling: it has no
        // icon-height identity to keep and already fits, so it stays at the
        // out-of-ring type size whatever the glyph slider says.
        TrioConfig.appearance().glyphScale = Prefs.MIN_GLYPH_SCALE;
        eq("a lone reading ignores the glyph slider",
                perfTextSize(single, container, sized), base);
        TrioConfig.appearance().glyphScale = 100;

        // ---- the readout's own weight. The desktop face is the reason this
        // section is nearly all clamps: AWT answers byte-identical metrics for
        // plain and bold (asc=1006 desc=220 at 1000pt), so no measurement here
        // can tell one weight from another and a metric assertion would pass
        // whatever the reading was drawn at. What IS observable is the accessor
        // production feeds to perfLineHeight and to the typeface, so the clamp
        // is pinned through it; the wiring itself is pinned by the source
        // contracts run above, which is the only place it can be pinned at all.
        TrioConfig.appearance().perfWeight = $defPerfWeight;
        eq("the default weight is the readout's own default",
                TrioConfig.appearance().perfTypeWeight(), $defPerfWeight);
        TrioConfig.appearance().perfWeight = 1000;
        eq("a weight over the ceiling clamps to it",
                TrioConfig.appearance().perfTypeWeight(), $maxPerfWeight);
        TrioConfig.appearance().perfWeight = 0;
        eq("a weight under the floor clamps to it",
                TrioConfig.appearance().perfTypeWeight(), $minPerfWeight);
        // The stored number is clamped on read, but the broadcast path builds an
        // appearance straight from extras and this number reaches the canvas.
        // Both bounds are read out of Prefs, so moving one is an explicit change.
        eq("the weight ceilings agree with the nine-step range",
                $maxPerfWeight - $minPerfWeight, 800);
        TrioConfig.appearance().perfWeight = 700;
        // A weight change must not move the size on a row where the floor is
        // already biting: the floor is absolute, so the answer is the same
        // whatever the face. This is the documented trade and it is also why the
        // weight needed its own term in the re-sync test.
        close("the floor does not move with the weight",
                perfTextSize(pair, container, box(10, 10)), base * PERF_MIN_SIZE_RATIO * 1f, 0.001f);

        // ---- the reserved strip. This is the fix for the icon row that stepped
        // sideways whenever the wattage changed: the strip MIUI is asked to give
        // up is one scalar shared by every view in it and it arrives through
        // setPadding, so measuring the live text moved the whole row with the
        // digits - about once a second while charging. What is pinned here is
        // that the answer is a function of what the reading IS (its widest
        // possible form) and not of what it happens to say right now.
        final PerfLabel label = new PerfLabel();
        label.getPaint().setTextSize(base);
        label.setSize(77, 40);

        // The digits are given distinct widths. AWT's SansSerif gives all ten
        // digits the same 556, which would make the substitution invisible:
        // with equal digits "0.0W" and "6.1W" reserve the same width whether or
        // not the widest digit was substituted, so the equality below would hold
        // for the wrong reason. Real status-bar faces are proportional - the
        // device log shows "0.0W" at 102px against "6.1W" at 92px - and this
        // table stands in for that.
        final float[] widths = new float[10];
        for (int d = 0; d < 10; d++) { widths[d] = 500f; }
        widths[4] = 900f;
        widths[6] = 600f;
        widths[1] = 520f;
        label.getPaint().setDigitWidths(widths);

        ok("the reserve is wider than the literal, so the substitution is observable",
                perfWidth(label, "0.0W") > (int) Math.ceil(label.getPaint().measureText("0.0W")));
        eq("every digit is measured at the widest one",
                perfWidth(label, "0.0W"), perfWidth(label, "4.4W"));
        eq("so two different wattages reserve the same strip",
                perfWidth(label, "0.0W"), perfWidth(label, "6.1W"));
        final int widened = (int) Math.ceil(label.getPaint().measureText("4.4W"));
        eq("and the answer is the ceiling of that widened width",
                perfWidth(label, "0.0W"), widened);
        ok("the widened width is not a whole number, so rounding up is observable",
                label.getPaint().measureText("4.4W") != widened);
        // Digits can still change count - "9.9W" to "10W" - and that is a step
        // in the strip, not a flicker. The reserve has to follow the count.
        ok("a longer number still reserves more",
                perfWidth(label, "10W") < perfWidth(label, "100W"));
        // Per line. Measuring the whole string in one call would add the two
        // lines together - the break is just a character to the paint - and
        // reserve the pair at the sum of its lines instead of its widest.
        ok("the pair's strip is not the sum of its lines",
                perfWidth(label, pair) < perfWidth(label, "6.1W") + perfWidth(label, "31\u00B0C"));
        eq("it is exactly the wider of the two lines",
                perfWidth(label, pair), Math.max(perfWidth(label, "6.1W"), perfWidth(label, "31\u00B0C")));
        // The widest digit is asked of the face rather than assumed to be '0':
        // it is not the same at every weight. Swapping the face changes which
        // digit is widest and how wide it is, and the answer has to follow both.
        final float[] nines = new float[10];
        for (int d = 0; d < 10; d++) { nines[d] = 500f; }
        nines[9] = 700f;
        label.getPaint().setDigitWidths(nines);
        eq("a different face widens to its own widest digit",
                perfWidth(label, "0.0W"), perfWidth(label, "9.9W"));
        ok("and the answer really did change with the face",
                perfWidth(label, "0.0W") != widened);
        // Nothing to measure: fall back to the frame rather than to no strip.
        eq("an empty reading falls back to the frame it already has", perfWidth(label, ""), 77);
        // On a face whose digits are all one width the substitution is a no-op,
        // and the reserve still may not come out short of what is drawn.
        label.getPaint().setDigitWidths(null);
        ok("with uniform digits the reserve is still at least the literal",
                perfWidth(label, "0.0W") >= (int) Math.ceil(label.getPaint().measureText("0.0W")));

        System.out.println("PASS: " + checks + " cases over perfTextSize, perfLineSpacing, perfWidth and inkScale");
    }
}
"@

$sources = Get-ChildItem -Recurse -Filter *.java $src | ForEach-Object { $_.FullName }
& $javac -nowarn -d $classes -encoding UTF-8 $sources
if($LASTEXITCODE){throw 'Compilation failed'}
& $java -Xmx64m -cp $classes io.github.fengwenhua.colorduo.PerfLayoutTest
if($LASTEXITCODE){throw 'Test failed'}
