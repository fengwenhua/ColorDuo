param([string]$SourceDirectory)
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$jdk=Join-Path $root '.tools/jdk/jdk-21.0.12.1+1/bin'
$javac=Join-Path $jdk 'javac.exe'; $java=Join-Path $jdk 'java.exe'
if(!(Test-Path $javac)){$javac='javac';$java='java'}

# ---------------------------------------------------------------------------
# GlyphDrawable is how the trio reaches the screen since the window route was
# removed: it writes itself into the battery icon view's OWN tint slot
# (`MiuiBatteryMeterIconView.mTintBgDrawable`, verified public at
# MiuiBatteryMeterIconView.java:66, drawn last at :549-553), so the canvas,
# its bounds and its clip stay the platform's.
#
# That makes the class the one piece of the refactor that replaced a probed
# component (the deleted tests\overlay-window.ps1 locked the window's private
# flag in three platform shapes) with nothing. This script closes that gap the
# same way: it compiles the REAL GlyphDrawable.java and the REAL Refl.java, not
# copies, against stub hosts that take several shapes, because whether a field
# or a method exists at all is a compile-time fact - one test class cannot
# stand in for both.
#
# The stub canvas is deliberately minimal: the draw path under test ends at the
# renderer, which is stubbed here, so no Canvas method is ever reached. What is
# asserted about draw is which box it hands over and when it declines to.
#
# Usage:  powershell -File tests\glyph-drawable.ps1
# ---------------------------------------------------------------------------

$trio=Join-Path $root 'app/src/main/java/io/github/fengwenhua/colorduo'
if($SourceDirectory){$trio=$SourceDirectory}
$glyphPath=Join-Path $trio 'GlyphDrawable.java'
$hooksPath=Join-Path $trio 'TrioHooks.java'
$glyph=[System.IO.File]::ReadAllText($glyphPath)
$hooks=[System.IO.File]::ReadAllText($hooksPath)
$drawHook=[regex]::Match($hooks,'(?s)"colorduo-draw", new XposedInterface.Hooker\(\) \{(.*?)\n                \}\);').Groups[1].Value
if(!$drawHook){throw 'Production battery draw hook missing'}

# --- source contract pins ----------------------------------------------------
# Everything the desktop cannot observe: which ROM names are reached, that the
# glyph is not scaled twice, that no window machinery crept back in, and the
# order of the two steps that hand the slot back.

if($glyph -notmatch 'final class GlyphDrawable extends Drawable'){
    throw 'GlyphDrawable no longer extends Drawable'
}
$slot=[regex]::Match($glyph,'static final String SLOT = "(.+?)";').Groups[1].Value
if($slot -ne 'mTintBgDrawable'){
    throw "the slot field is no longer mTintBgDrawable (got '$slot')"
}
$legacy=[regex]::Match($glyph,'static final String LEGACY = "(.+?)";').Groups[1].Value
if($legacy -ne 'mUseLegacyDrawable'){
    throw "the legacy field is no longer mUseLegacyDrawable (got '$legacy')"
}
# The point of the slot is that the box is the platform's. TrioRenderer.drawInto
# already multiplies by glyphFit(); reaching for it here as well would shrink the
# glyph by the square of the setting - and the pixels would still look plausible.
if($glyph -match 'glyphFit|TrioAppearance'){
    throw 'GlyphDrawable scales the glyph a second time'
}
foreach($banned in @('WindowManager','TYPE_STATUS_BAR','FLAG_NOT_TOUCHABLE','addView')){
    if($glyph -match [regex]::Escape($banned)){
        throw "GlyphDrawable references $banned; the slot route creates no window"
    }
}
# Refl.set swallows a failure, so the write is read back before it is believed.
if($glyph -notmatch 'if \(Refl\.get\(slot, host\) != glyph'){
    throw 'install no longer reads the slot back after writing it'
}
# uninstall asks MIUI to rebuild first and only empties the slot if that did not
# work; the other order would blank a slot MIUI was willing to take back.
$guardAt=$glyph.IndexOf('!(Refl.get(slot, host) instanceof GlyphDrawable)')
$clearAt=$glyph.IndexOf('Refl.callByName(host, "clearTint");')
$nullAt=$glyph.IndexOf('Refl.set(slot, host, null);')
if($guardAt -lt 0){throw 'uninstall no longer checks that it owns the slot before touching it'}
if($clearAt -lt 0){throw 'uninstall no longer asks MIUI to rebuild the slot'}
if($nullAt -lt 0){throw 'uninstall no longer has the empty-slot fallback'}
if($guardAt -gt $clearAt){throw 'uninstall touches the slot before checking that it owns it'}
if($clearAt -gt $nullAt){throw 'the empty-slot fallback runs before MIUI is asked to rebuild'}
# The hook is what puts the drawable there and takes it back, and the frame that
# takes the slot has to repaint over MIUI's own battery - the drawable is only
# asked from the NEXT frame.
if($hooks -notmatch 'if \(GlyphDrawable\.install\(host, state\)\) \{'){
    throw 'the repaint is no longer gated on the slot changing hands'
}
if($hooks -notmatch 'GlyphDrawable\.uninstall\(\(View\) self\);'){
    throw 'the icon hook no longer hands the slot back when the module is off'
}
$installAt=$hooks.IndexOf('GlyphDrawable.install(host, state)')
$repaintAt=$hooks.IndexOf('TrioRenderer.draw(canvas, host, state)', $installAt)
if($installAt -lt 0 -or $repaintAt -lt 0){
    throw 'the frame that takes the slot no longer repaints over MIUI''s own battery'
}
$gateAt=$hooks.IndexOf('final boolean paint =')
$uninstallAt=$hooks.IndexOf('GlyphDrawable.uninstall((View) self);', $gateAt)
$nativeAt=$hooks.IndexOf('chain.proceed()', $gateAt)
if($gateAt -lt 0 -or $uninstallAt -gt $nativeAt){throw 'native onDraw sees the old glyph before uninstall'}

# --- generated trees ---------------------------------------------------------
# Both are rebuilt whole: the compile step collects every .java below the tree,
# so a file left by an earlier run would be compiled too and could shadow a live
# definition.
$src=Join-Path $root 'work/glyphdrawable/src'
$classes=Join-Path $root 'work/glyphdrawable/classes'
if(Test-Path $src){Remove-Item -Recurse -Force $src}
if(Test-Path $classes){Remove-Item -Recurse -Force $classes}
New-Item -ItemType Directory -Force -Path $src | Out-Null
New-Item -ItemType Directory -Force -Path $classes | Out-Null

$utf8=New-Object System.Text.UTF8Encoding($false)

$drawable=@'
package android.graphics.drawable;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Rect;

/** The parts of {@code android.graphics.drawable.Drawable} that GlyphDrawable uses. */
public abstract class Drawable {

    private final Rect bounds = new Rect();

    public Rect getBounds() { return bounds; }

    public void setBounds(int l, int t, int r, int b) { bounds.set(l, t, r, b); }

    public void invalidateSelf() { }

    public abstract void draw(Canvas canvas);
    public abstract void setAlpha(int alpha);
    public abstract void setColorFilter(ColorFilter filter);
    public abstract int getOpacity();
}
'@

$miuiArt=@'
package android.graphics.drawable;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;

/** Stands in for MIUI's own artwork, the thing the slot normally holds. */
public class MiuiArtDrawable extends Drawable {

    @Override public void draw(Canvas canvas) { }

    @Override public void setAlpha(int alpha) { }

    @Override public void setColorFilter(ColorFilter filter) { }

    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
'@

$rect=@'
package android.graphics;

/** Minimal desktop stand-in for {@code android.graphics.Rect}. */
public class Rect {

    public int left;
    public int top;
    public int right;
    public int bottom;

    public Rect() { }

    public void set(int l, int t, int r, int b) {
        left = l; top = t; right = r; bottom = b;
    }

    public int width() { return right - left; }

    public int height() { return bottom - top; }
}
'@

$colorFilter=@'
package android.graphics;

/** Marker stand-in for {@code android.graphics.ColorFilter}. */
public class ColorFilter { }
'@

$pixelFormat=@'
package android.graphics;

/** Minimal desktop stand-in for {@code android.graphics.PixelFormat}. */
public class PixelFormat {

    public static final int TRANSLUCENT = -3;
}
'@

$canvas=@'
package android.graphics;

/**
 * Marker stand-in for {@code android.graphics.Canvas}.
 *
 * <p>Deliberately empty: on the path under test the canvas is only handed on to
 * the renderer, which is stubbed, so no method of it is ever reached.
 */
public class Canvas { }
'@

$trioHooks=@"
package io.github.fengwenhua.colorduo;

import java.util.ArrayList;
import java.util.List;
import android.view.View;
import android.graphics.Canvas;

final class XposedInterface {
 interface Hooker { Object intercept(Chain chain) throws Throwable; }
 interface Chain { Object proceed(); Object getThisObject(); Object getArg(int index); }
}

/** Records what the drawable logs, so the one-shot notes can be asserted. */
final class TrioHooks {

    static final int LOG_INFO = 4;
    static final int LOG_WARN = 5;
    static final int LOG_ERROR = 6;

    static final List<String> LOGGED = new ArrayList<String>();

    private TrioHooks() { }

    static void log(int priority, String msg) { LOGGED.add(priority + ":" + msg); }
    static boolean island;
    static boolean islandHideBattery(){return island;}
    static TrioState stateFor(View host){return null;}
    static TrioState registerHost(View host){return new TrioState();}
    static void recolourOutRing(TrioState s,int ink){}
    static Object draw(View host, Canvas canvas) throws Throwable {
        return new XposedInterface.Hooker() {
            $drawHook
        }.intercept(new XposedInterface.Chain(){
            public Object proceed(){host.nativeDraw(canvas);return null;}
            public Object getThisObject(){return host;}
            public Object getArg(int index){return canvas;}
        });
    }
}
"@

$trioState=@'
package io.github.fengwenhua.colorduo;

/** Counts the refreshes, because draw has to refresh before it builds a frame. */
final class TrioState {

    int refreshes;
    int outRingInk;
    int foreground(){return 0xFF000000;}
    static void attachContext(Object context){}

    void refresh() { refreshes++; }
}
'@

$trioSettings=@'
package io.github.fengwenhua.colorduo;

/** The renderer below ignores the settings, so this only has to exist. */
final class TrioSettings { boolean enabled=true; }
'@

$trioConfig=@'
package io.github.fengwenhua.colorduo;

/** Hands the drawable a settings object, as the real TrioConfig does. */
final class TrioConfig {

    private static final TrioSettings SNAPSHOT = new TrioSettings();

    private TrioConfig() { }

    static TrioSettings get() { return SNAPSHOT; }
    static void installReceiver(Object context){}
}
'@

$trioRenderer=@'
package io.github.fengwenhua.colorduo;

import android.graphics.Canvas;

/**
 * Records what the drawable asked it to paint instead of painting.
 *
 * <p>This is the seam the whole class exists for: it receives the box, so the
 * assertions can be about the numbers handed over rather than about pixels the
 * desktop would have to render.
 */
final class TrioRenderer {

    static int calls;
    static int lastW;
    static int lastH;
    static Canvas lastCanvas;
    static TrioState lastState;

    private TrioRenderer() { }

    static void drawState(Canvas canvas, int w, int h, TrioState s, TrioSettings cfg) {
        calls++;
        lastW = w;
        lastH = h;
        lastCanvas = canvas;
        lastState = s;
    }
    static void draw(Canvas canvas,android.view.View host,TrioState s){
        drawState(canvas,host.getWidth(),host.getHeight(),s,TrioConfig.get());
    }

    static void reset() {
        calls = 0;
        lastW = -1;
        lastH = -1;
        lastCanvas = null;
        lastState = null;
    }
}
'@

$test=@'
package io.github.fengwenhua.colorduo;

import android.graphics.Canvas;
import android.graphics.drawable.MiuiArtDrawable;
import android.view.View;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

/**
 * Drives the real GlyphDrawable against a stub host.
 *
 * <p>Every assertion is about observable behaviour: what ends up in the slot,
 * which box is handed to the renderer, whether MIUI is asked to take its slot
 * back. The one thing it cannot see is the second scaling, which lives in
 * TrioRenderer instead - that is pinned in the script.
 */
public class GlyphTest {

    private static int checks;

    private static void ok(String what, boolean cond) {
        if (!cond) { throw new AssertionError(what); }
        checks++;
    }

    private static void eq(String what, long got, long want) {
        if (got != want) {
            throw new AssertionError(what + ": got <" + got + "> want <" + want + ">");
        }
        checks++;
    }

    private static Field slotField() {
        try { return View.class.getField("mTintBgDrawable"); }
        catch (Throwable t) { return null; }
    }

    private static Object slot(View host) throws Exception {
        return slotField().get(host);
    }

    private static boolean logged(String needle) {
        for (int i = 0; i < TrioHooks.LOGGED.size(); i++) {
            if (TrioHooks.LOGGED.get(i).indexOf(needle) >= 0) { return true; }
        }
        return false;
    }

    private static GlyphDrawable fresh(TrioState state) throws Exception {
        Constructor<GlyphDrawable> c =
                GlyphDrawable.class.getDeclaredConstructor(TrioState.class);
        c.setAccessible(true);
        return c.newInstance(state);
    }

    public static void main(String[] args) throws Throwable {
        final String shape = args[0];
        final boolean hasSlot = !"nofield".equals(shape);
        final boolean clearVisible = "full".equals(shape) || "legacy".equals(shape);

        // ---- taking the slot -------------------------------------------------
        final View host = new View();
        final TrioState state = new TrioState();
        final boolean took = GlyphDrawable.install(host, state);
        if (hasSlot) {
            ok("the first frame on a slot host takes the slot", took);
            final Object mounted = slot(host);
            ok("the slot holds the glyph afterwards", mounted instanceof GlyphDrawable);
            ok("subsequent frames use direct drawing only for legacy hosts",
                    GlyphDrawable.install(host, state) == "legacy".equals(shape));
            ok("and keeps the same drawable, not a fresh one", slot(host) == mounted);

            // Re-installing is how a host whose state object was replaced is
            // repointed; the drawable has to build from the newer state.
            final TrioState later = new TrioState();
            GlyphDrawable.install(host, later);
            final GlyphDrawable owner = (GlyphDrawable) slot(host);
            owner.setBounds(0, 0, 84, 60);
            TrioRenderer.reset();
            owner.draw(new Canvas());
            ok("a re-install repoints the drawable at the newer state",
                    TrioRenderer.lastState == later);
        } else {
            ok("a host without the field requests direct drawing", took);
            ok("missing slot keeps drawing on later frames", GlyphDrawable.install(host, state));
            ok("nothing is written to it", slotField() == null);
            ok("and it says so", logged("has no mTintBgDrawable"));
        }

        // ---- the legacy build is called out ----------------------------------
        // `battery_meter_use_legacy_drawable` returns before the tint slot is
        // drawn, so the glyph would be switched on and invisible. Silence there
        // is the one failure with no other symptom.
        eq("a build that never reaches the tint slot is logged once",
                logged("legacy drawable") ? 1 : 0, "legacy".equals(shape) ? 1 : 0);

        // ---- draw hands the platform's own box over --------------------------
        final TrioState drawState = new TrioState();
        final GlyphDrawable glyph = fresh(drawState);
        final Canvas canvas = new Canvas();

        TrioRenderer.reset();
        glyph.setBounds(0, 0, 84, 60);
        glyph.draw(canvas);
        eq("the renderer is asked to paint exactly once", TrioRenderer.calls, 1);
        eq("the width handed over is the view's own", TrioRenderer.lastW, 84);
        eq("the height handed over is the view's own", TrioRenderer.lastH, 60);
        ok("the canvas is the one the platform gave", TrioRenderer.lastCanvas == canvas);
        ok("the state handed over is the drawable's own", TrioRenderer.lastState == drawState);
        eq("the state is refreshed before the frame is built", drawState.refreshes, 1);

        // A box with no area has nothing to paint, and refreshing the state for
        // one would be work on every collapsed layout pass.
        glyph.setBounds(0, 0, 0, 60);
        TrioRenderer.reset();
        drawState.refreshes = 0;
        glyph.draw(canvas);
        eq("a collapsed width paints nothing", TrioRenderer.calls, 0);
        eq("and does not read the state either", drawState.refreshes, 0);

        glyph.setBounds(0, 0, 84, 0);
        TrioRenderer.reset();
        glyph.draw(canvas);
        eq("a collapsed height paints nothing", TrioRenderer.calls, 0);

        glyph.setBounds(84, 0, 0, 60);
        TrioRenderer.reset();
        glyph.draw(canvas);
        eq("an inverted box paints nothing", TrioRenderer.calls, 0);

        // ---- handing the slot back -------------------------------------------
        if (hasSlot) {
            final View owned = new View();
            GlyphDrawable.install(owned, new TrioState());
            ok("the host under test really holds the glyph",
                    slot(owned) instanceof GlyphDrawable);
            final int before = owned.clearTintCalls;
            GlyphDrawable.uninstall(owned);
            if (clearVisible) {
                eq("uninstall asks MIUI to rebuild the slot", owned.clearTintCalls, before + 1);
                ok("MIUI's own artwork owns the slot again",
                        !(slot(owned) instanceof GlyphDrawable));
                ok("so the empty fallback did not run", slot(owned) != null);
                eq("uninstall replays native dark mode after clearing tint", owned.darkCalls, 1);
                ok("native tint is restored immediately", owned.tinted);
            } else {
                eq("a clearTint this build does not expose is not called",
                        owned.clearTintCalls, before);
                ok("so the slot is emptied rather than left on the glyph",
                        slot(owned) == null);
            }
            GlyphDrawable.uninstall(owned);
            ok("uninstalling twice is harmless",
                    !(slot(owned) instanceof GlyphDrawable));

            // A host that never held the glyph must not be disturbed at all.
            // MIUI's own artwork is in there, and rebuilding it on every frame
            // would reset the tint MIUI had just applied.
            final View foreign = new View();
            final MiuiArtDrawable art = new MiuiArtDrawable();
            slotField().set(foreign, art);
            final int untouched = foreign.clearTintCalls;
            GlyphDrawable.uninstall(foreign);
            eq("a host holding MIUI's own artwork is not disturbed",
                    foreign.clearTintCalls, untouched);
            ok("and that artwork is left exactly as it was", slot(foreign) == art);
        } else {
            GlyphDrawable.uninstall(new View());
            ok("uninstall on a host with no slot is harmless", true);
        }

        // Drive the real hook around a native onDraw that calls the current
        // slot. This checks first-frame ordering and fallback over time.
        View live=new View();
        for(int frame=0;frame<3;frame++){
            TrioRenderer.reset();TrioHooks.draw(live,new Canvas());
            eq("glyph exists exactly once in frame "+frame,TrioRenderer.calls,1);
        }
        boolean legacyBefore=live.mUseLegacyDrawable;
        live.mUseLegacyDrawable=!legacyBefore;TrioRenderer.reset();TrioHooks.draw(live,new Canvas());
        eq("legacy mode changes are read per instance",TrioRenderer.calls,1);
        live.mUseLegacyDrawable=legacyBefore;TrioRenderer.reset();TrioHooks.draw(live,new Canvas());
        eq("returning from legacy also paints once",TrioRenderer.calls,1);
        if(!hasSlot){
            int notes=0;for(String message:TrioHooks.LOGGED)if(message.contains("has no mTintBgDrawable"))notes++;
            eq("missing slot is logged once per host class",notes,1);
        }
        TrioConfig.get().enabled=false;TrioRenderer.reset();int nativeBefore=live.nativeArt;
        TrioHooks.draw(live,new Canvas());
        eq("first disabled frame contains no stale glyph",TrioRenderer.calls,0);
        ok("native artwork restored in that frame",live.nativeArt>nativeBefore && live.tinted);
        TrioConfig.get().enabled=true;TrioHooks.draw(live,new Canvas());
        TrioHooks.island=true;TrioRenderer.reset();TrioHooks.draw(live,new Canvas());
        eq("first island frame contains no stale glyph",TrioRenderer.calls,0);
        TrioHooks.island=false;TrioRenderer.reset();TrioHooks.draw(live,new Canvas());
        eq("leaving island resumes painting immediately",TrioRenderer.calls,1);

        System.out.println("PASS: " + checks + " cases over the glyph slot (" + shape + ")");
    }
}
'@

# A host's field and method set is a compile-time fact, so each shape is a
# separate tree. `clear=package` models the trap Refl.callByName has: getMethod
# cannot see a non-public member, so the fallback branch has to exist.
$shapes=@(
    @{ name='full';      slot=$true;  clear='public';  legacy=$false },
    @{ name='legacy';    slot=$true;  clear='public';  legacy=$true  },
    @{ name='noclear';   slot=$true;  clear='none';    legacy=$false },
    @{ name='nonpublic'; slot=$true;  clear='package'; legacy=$false },
    @{ name='nofield';   slot=$false; clear='none';    legacy=$false }
)

$ran=0
foreach($shape in $shapes){
    $tree=Join-Path $src $shape.name
    $out=Join-Path $classes $shape.name
    $pkg=Join-Path $tree 'io/github/fengwenhua/colorduo'
    New-Item -ItemType Directory -Force -Path $pkg | Out-Null
    New-Item -ItemType Directory -Force -Path (Join-Path $tree 'android/graphics/drawable') | Out-Null
    New-Item -ItemType Directory -Force -Path (Join-Path $tree 'android/graphics') | Out-Null
    New-Item -ItemType Directory -Force -Path (Join-Path $tree 'android/view') | Out-Null
    New-Item -ItemType Directory -Force -Path $out | Out-Null

    # The production files, unchanged.
    Copy-Item $glyphPath (Join-Path $pkg 'GlyphDrawable.java')
    Copy-Item (Join-Path $trio 'Refl.java') (Join-Path $pkg 'Refl.java')

    [System.IO.File]::WriteAllText((Join-Path $pkg 'TrioHooks.java'), $trioHooks, $utf8)
    [System.IO.File]::WriteAllText((Join-Path $pkg 'TrioState.java'), $trioState, $utf8)
    [System.IO.File]::WriteAllText((Join-Path $pkg 'TrioSettings.java'), $trioSettings, $utf8)
    [System.IO.File]::WriteAllText((Join-Path $pkg 'TrioConfig.java'), $trioConfig, $utf8)
    [System.IO.File]::WriteAllText((Join-Path $pkg 'TrioRenderer.java'), $trioRenderer, $utf8)
    [System.IO.File]::WriteAllText((Join-Path $pkg 'GlyphTest.java'), $test, $utf8)

    [System.IO.File]::WriteAllText((Join-Path $tree 'android/graphics/Canvas.java'), $canvas, $utf8)
    [System.IO.File]::WriteAllText((Join-Path $tree 'android/graphics/Rect.java'), $rect, $utf8)
    [System.IO.File]::WriteAllText((Join-Path $tree 'android/graphics/ColorFilter.java'), $colorFilter, $utf8)
    [System.IO.File]::WriteAllText((Join-Path $tree 'android/graphics/PixelFormat.java'), $pixelFormat, $utf8)
    [System.IO.File]::WriteAllText((Join-Path $tree 'android/graphics/drawable/Drawable.java'), $drawable, $utf8)
    [System.IO.File]::WriteAllText((Join-Path $tree 'android/graphics/drawable/MiuiArtDrawable.java'), $miuiArt, $utf8)

    $view = "package android.view;`n`n"
    $view += "import android.graphics.drawable.Drawable;`n"
    $view += "import android.graphics.drawable.MiuiArtDrawable;`n`n"
    $view += "public class View {`n`n"
    if($shape.slot){ $view += "    public Drawable mTintBgDrawable;`n`n" }
    $view += "    public boolean mUseLegacyDrawable = " + $(if($shape.legacy){'true'}else{'false'}) + ";`n`n"
    $view += "    public int clearTintCalls;`n`n"
    $view += "    public int darkCalls; public boolean tinted=true;`n"
    $view += "    public void onDarkChangeInternal() { darkCalls++; tinted=true; }`n"
    $view += "    public int nativeArt;`n"
    $view += "    public Object getContext(){return null;} public int getWidth(){return 84;} public int getHeight(){return 60;}`n"
    $view += "    public void nativeDraw(android.graphics.Canvas canvas){`n"
    if($shape.slot){
        $view += "        if(!mUseLegacyDrawable && mTintBgDrawable!=null){if(!mTintBgDrawable.getClass().getSimpleName().equals(""GlyphDrawable"")){nativeArt++;}mTintBgDrawable.setBounds(0,0,84,60);mTintBgDrawable.draw(canvas);}else{nativeArt++;}`n"
    }else{
        $view += "        nativeArt++;`n"
    }
    $view += "    }`n"
    if($shape.clear -eq 'public'){
        $view += "    public void clearTint() {`n"
    } elseif($shape.clear -eq 'package'){
        $view += "    void clearTint() {`n"
    }
    if($shape.clear -ne 'none'){
        $view += "        clearTintCalls++;`n"
        $view += "        tinted=false;`n"
        $view += "        mTintBgDrawable = new MiuiArtDrawable();`n"
        $view += "    }`n"
    }
    $view += "}`n"
    [System.IO.File]::WriteAllText((Join-Path $tree 'android/view/View.java'), $view, $utf8)

    $sources = Get-ChildItem -Recurse -Filter *.java $tree | ForEach-Object { $_.FullName }
    & $javac -nowarn -d $out -encoding UTF-8 $sources
    if($LASTEXITCODE){ throw "Compilation failed for the $($shape.name) tree" }
    & $java -Xmx128m -cp $out io.github.fengwenhua.colorduo.GlyphTest $shape.name
    if($LASTEXITCODE){ throw "Test failed for the $($shape.name) tree" }
    $ran++
}

Write-Output "PASS: $ran host shapes over the real GlyphDrawable"
