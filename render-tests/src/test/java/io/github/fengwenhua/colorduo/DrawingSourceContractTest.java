/* Copyright (C) 2026 yixing233 and HyperDuo contributors
 * SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.Test;
import static org.junit.Assert.*;

/** Integration contracts from the old PS entry, separate from actual-render assertions. */
public final class DrawingSourceContractTest {
    private static String source(String name) throws Exception {
        return Files.readString(Path.of(System.getProperty("colorduo.projectRoot"),
                "app/src/main/java/io/github/fengwenhua/colorduo", name));
    }
    private static void matches(String text, String pattern) {
        assertTrue("Missing integration contract: " + pattern, Pattern.compile(pattern).matcher(text).find());
    }

    @Test public void rendererUsesTheSharedTransformForItsActualDrawingBody() throws Exception {
        String renderer = source("TrioRenderer.java");
        matches(renderer, "static boolean designTransform\\(int w, int h, TrioAppearance appearance, float\\[\\] out\\)");
        var bodies = Pattern.compile("(?s)static void drawInto\\(Canvas canvas, int w, int h, int level, boolean charging,[^)]*boolean wifiConnected, boolean vpnActive\\) \\{.*?\\n    \\}").matcher(renderer);
        assertTrue("Live drawInto body must exist", bodies.find());
        String body = bodies.group();
        assertFalse("Live drawInto body must be unique", bodies.find());
        assertTrue(body.contains("designTransform(w, h, appearance, transform)"));
        assertFalse(body.contains("TrioGeometry.INK_W * scale"));
    }

    @Test public void hitTestingInvertsTheSameCanvasTransform() throws Exception {
        String zones = source("TrioZones.java");
        matches(zones, "static Zone zoneAt\\(float\\[\\] transform, boolean rect");
        matches(zones, "\\(viewX - transform\\[1\\]\\) / scale");
        matches(zones, "static float\\[\\] viewTransform\\(int width, int height, float inset,");
    }

    @Test public void previewDelegatesItsFitInsteadOfMaintainingAnotherCopy() throws Exception {
        String preview = source("TrioPreviewView.java");
        matches(preview, "return TrioZones\\.viewTransform\\(width, height, inset, TrioAppearance\\.of\\(settings\\)\\);");
        assertFalse(preview.contains("TrioGeometry.INK_W * scale"));
    }

    @Test public void legacyRectangularSeamsUseTheRenderersTextAllowance() throws Exception {
        String zones = source("TrioZones.java"), geometry = source("TrioGeometry.java");
        assertTrue(zones.contains("RECT_SEAM_LEFT = TrioGeometry.RECT_TEXT_LEFT;"));
        assertTrue(zones.contains("RECT_SEAM_RIGHT = TrioGeometry.RECT_TEXT_RIGHT;"));
        assertTrue(geometry.contains("RECT_TEXT_CLEAR = RECT_TEXT_RIGHT - RECT_TEXT_LEFT;"));
    }
}
