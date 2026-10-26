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
import android.graphics.drawable.MiuiArtDrawable;
import android.view.View;
import android.view.NativeHosts;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
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
@RunWith(Parameterized.class)
public class GlyphHostTest {
    private final String shape;
    public GlyphHostTest(String shape) { this.shape = shape; }
    @Parameterized.Parameters(name = "{0}")
    public static List<String> shapes() { return List.of("full", "legacy", "noclear", "nonpublic", "nofield"); }
    private View createHost() { return NativeHosts.create(shape); }
    @Test public void drawAndRestoreContract() throws Throwable {
        checks = 0; TrioRenderer.reset(); TrioHooks.LOGGED.clear();
        TrioConfig.get().enabled = true; TrioHooks.island = false;
        runShape();
    }


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

    private static Field slotField(View host) {
        try { return host.getClass().getField("mTintBgDrawable"); }
        catch (Throwable t) { return null; }
    }

    private static Object slot(View host) throws Exception {
        return slotField(host).get(host);
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

    private void runShape() throws Throwable {
        final boolean hasSlot = !"nofield".equals(shape);
        final boolean clearVisible = "full".equals(shape) || "legacy".equals(shape);

        // ---- taking the slot -------------------------------------------------
        final View host = createHost();
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
            ok("nothing is written to it", slotField(host) == null);
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
            final View owned = createHost();
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
            final View foreign = createHost();
            final MiuiArtDrawable art = new MiuiArtDrawable();
            slotField(foreign).set(foreign, art);
            final int untouched = foreign.clearTintCalls;
            GlyphDrawable.uninstall(foreign);
            eq("a host holding MIUI's own artwork is not disturbed",
                    foreign.clearTintCalls, untouched);
            ok("and that artwork is left exactly as it was", slot(foreign) == art);
        } else {
            GlyphDrawable.uninstall(createHost());
            ok("uninstall on a host with no slot is harmless", true);
        }

        // Drive the real hook around a native onDraw that calls the current
        // slot. This checks first-frame ordering and fallback over time.
        View live=createHost();
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
