/* Copyright (C) 2026 yixing233 and HyperDuo contributors
 * SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;
import static org.junit.Assert.*;

/** Legacy source integration pins complement the actual-render pixel checks. */
public final class CapsuleSourceContractTest {
    private static String source(String name) throws Exception {
        return Files.readString(Path.of(System.getProperty("colorduo.projectRoot"),
                "app/src/main/java/io/github/fengwenhua/colorduo", name));
    }
    private static void matches(String source, String regex) {
        assertTrue("Missing integration contract: " + regex, java.util.regex.Pattern.compile(regex).matcher(source).find());
    }

    @Test public void nativeMeasurementOnlyGrowsCapsuleAndResolvesTheViewSetter() throws Exception {
        String hooks = source("TrioHooks.java");
        matches(hooks, "Refl\\.method\\(icon, \"onMeasure\", int\\.class, int\\.class\\)");
        matches(hooks, "Refl\\.method\\(View\\.class, \"setMeasuredDimension\",\\s*int\\.class, int\\.class\\)");
        matches(hooks, "if \\(!a\\.glyph \\|\\| !a\\.capsule\\) \\{\\s*return result;");
        matches(hooks, "TrioGeometry\\.setCapsuleLength\\(a\\.capsuleLength\\);\\s*final int width = TrioGeometry\\.capHostWidthPx\\(height\\);");
        matches(hooks, "final int tall = TrioGeometry\\.capHostHeightPx\\(height\\);");
        assertFalse(java.util.regex.Pattern.compile("capHostWidthPx[\\s\\S]{0,500}glyphFit").matcher(hooks).find());
        assertTrue(hooks.contains("Integer.valueOf(width), Integer.valueOf(tall)"));
    }

    @Test public void dotsStayOnTheOutlineAndReadingsShareTheHeightFitter() throws Exception {
        String geometry = source("TrioGeometry.java"), renderer = source("TrioRenderer.java");
        matches(geometry, "static float capDotTopY\\(\\) \\{\\s*return CAP_TOP;");
        matches(geometry, "static float capDotBottomY\\(\\) \\{\\s*return CAP_BOTTOM;");
        assertTrue(renderer.split("capsuleTextSize\\(", -1).length - 1 >= 4);
        assertTrue(renderer.contains("TrioGeometry.capTextMaxSize(stroke)"));
    }

    @Test public void storedStyleAndDropdownKeepNonContiguousIds() throws Exception {
        String settings = source("TrioSettings.java"), ui = source("ui/ContentControls.kt");
        assertEquals(Prefs.STYLE_CAPSULE, Prefs.MAX_TRIO_STYLE);
        matches(settings, "Prefs\\.normalizeTrioStyle\\(\\s*p\\.getInt\\(Prefs\\.KEY_TRIO_STYLE, Prefs\\.DEF_TRIO_STYLE\\)\\)");
        matches(ui, "(?s)ContentControl.Style ->.*?listOf\\(R.string.trio_style_ring, R.string.trio_style_capsule\\)");
        assertTrue(ui.contains("setTrioStyle(styles[index])"));
    }

    @Test public void actualHostDimensionsReserveOpeningBandsWithoutChangingBodyScale() {
        try {
            for (int length : new int[] {0, Prefs.MAX_CAPSULE_LENGTH}) {
                TrioGeometry.setCapsuleLength(length);
                assertEquals(0, TrioGeometry.capHostWidthPx(0));
                assertEquals(0, TrioGeometry.capHostHeightPx(0));
                for (int height : new int[] {30, 60, 90}) {
                    int width = TrioGeometry.capHostWidthPx(height), tall = TrioGeometry.capHostHeightPx(height);
                    assertTrue(tall > height);
                    float scale = TrioRenderer.inkScale(width, tall, Prefs.STYLE_CAPSULE);
                    assertEquals(height, TrioGeometry.CAP_INK_H * scale, 1.1f);
                    assertEquals(width, TrioGeometry.capInkW() * scale, 1.1f);
                    assertTrue((TrioGeometry.CAP_INK_BOTTOM - TrioGeometry.CAP_INK_TOP) * scale <= tall + .01f);
                }
            }
        } finally { TrioGeometry.setCapsuleLength(0); }
    }
}
