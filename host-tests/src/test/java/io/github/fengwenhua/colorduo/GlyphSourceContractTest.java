/*
 * Copyright (C) 2026 yixing233 and HyperDuo contributors
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.github.fengwenhua.colorduo;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;
import static org.junit.Assert.*;

/** Historical source tripwires retained alongside runtime host-shape coverage. */
public final class GlyphSourceContractTest {
    private String source(String name) throws Exception {
        return Files.readString(Path.of(System.getProperty("colorduo.glyphSources"), name));
    }
    @Test public void drawableStaysInNativeSlotWithoutASecondFitOrSeparateWindow() throws Exception {
        String code = source("GlyphDrawable.java");
        assertTrue(code.contains("final class GlyphDrawable extends Drawable"));
        assertTrue(code.contains("static final String SLOT = \"mTintBgDrawable\";"));
        assertTrue(code.contains("static final String LEGACY = \"mUseLegacyDrawable\";"));
        for (String forbidden : new String[]{"glyphFit", "TrioAppearance", "WindowManager", "TYPE_STATUS_BAR", "FLAG_NOT_TOUCHABLE", "addView"})
            assertFalse("native-slot drawable must not contain " + forbidden, code.contains(forbidden));
    }
    @Test public void restoreOnlyTouchesOurSlotAndTriesNativeRebuildBeforeNullFallback() throws Exception {
        String code = source("GlyphDrawable.java");
        int guard = code.indexOf("!(Refl.get(slot, host) instanceof GlyphDrawable)");
        int restore = code.indexOf("Refl.callByName(host, \"clearTint\");");
        int clear = code.indexOf("Refl.set(slot, host, null);");
        assertTrue(guard >= 0 && restore > guard && clear > restore);
    }
    @Test public void hookReturnsOwnershipBeforeNativeDrawAndInstallsBeforeDirectRepaint() throws Exception {
        String code = source("TrioHooks.java");
        int hook = code.indexOf("\"colorduo-draw\", new XposedInterface.Hooker() {");
        assertTrue(hook >= 0); code = code.substring(hook);
        int gate = code.indexOf("final boolean paint =");
        int release = code.indexOf("GlyphDrawable.uninstall((View) self);");
        int nativeDraw = code.indexOf("chain.proceed()");
        assertTrue(gate >= 0 && release > gate && nativeDraw > release);
        int install = code.indexOf("GlyphDrawable.install(host, state)");
        assertTrue(install >= 0 && code.indexOf("TrioRenderer.draw(canvas, host, state)", install) > install);
    }
}
