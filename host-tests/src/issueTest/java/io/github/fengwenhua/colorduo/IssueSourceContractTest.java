/* Copyright (C) 2026 yixing233 and HyperDuo contributors
 * SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.Test;
import static org.junit.Assert.*;

/** Historical UI integration pins; these do not simulate Android gestures or Compose. */
public final class IssueSourceContractTest {
    private static String source(String name) throws Exception {
        return Files.readString(Path.of(System.getProperty("colorduo.projectRoot"),
                "app/src/main/java/io/github/fengwenhua/colorduo", name));
    }
    private static void has(String source, String pattern) {
        assertTrue("Missing integration: " + pattern, Pattern.compile(pattern).matcher(source).find());
    }
    private static void lacks(String source, String pattern) {
        assertFalse("Forbidden integration: " + pattern, Pattern.compile(pattern).matcher(source).find());
    }
    private static String body(String source, String signature) {
        var match = Pattern.compile("(?s)" + Pattern.quote(signature) + ".*?\\n    \\}").matcher(source);
        assertTrue("Missing method " + signature, match.find());
        String result = match.group();
        assertFalse("Ambiguous method " + signature, match.find());
        return result;
    }

    @Test public void previewAndSolverShareSimReadings() throws Exception {
        String drawing = source("ui/DrawingScreen.kt");
        has(drawing, "val sims = scene\\.previewSlots\\.count \\{ it >= 0 \\}");
        has(source("ui/DrawingPreview.kt"), "scene\\.previewSlots, PREVIEW_TYPE");
    }

    @Test public void capsuleTrackDoesNotDependOnNeighbourOrRuntimeReadings() throws Exception {
        String geometry = source("TrioGeometry.java");
        has(geometry, "static float capReadingTrackWidth");
        lacks(geometry, "capReadingSlotWidth|capPieceWidthUnits");
        lacks(body(geometry, "static float capReadingTrackWidth"), "TrioState|int level");
        has(geometry, "static float capReadingEdge");
    }

    @Test public void pressHighlightFillsBeforeOutlineUsingOneAccent() throws Exception {
        String preview = source("TrioPreviewView.java");
        String outline = body(preview, "private void drawZoneOutline(");
        has(outline, "Style\\.FILL"); has(outline, "Style\\.STROKE");
        has(outline, "for \\(int pass = 0; pass < 2; pass\\+\\+\\)");
        has(outline, "\\.setStyle\\(Paint\\.Style\\.FILL\\)");
        has(outline, "pass == 1\\) \\{"); lacks(outline, "pass == 0\\) \\{");
        assertTrue(outline.indexOf("Paint.Style.FILL") < outline.indexOf("pass == 1"));
        has(preview, "setHighlightColors"); has(preview, "withAlpha\\(accent, 0xB0\\)");
        has(preview, "withAlpha\\(accent, 0x24\\)"); has(preview, "highlightFillColor");
    }

    @Test public void highlightUsesSeparateGeometryFromHitTesting() throws Exception {
        has(source("TrioZones.java"), "public static float\\[\\]\\[\\] highlightBoxes\\(");
        String preview = source("TrioPreviewView.java");
        has(preview, "TrioZones\\.highlightBoxes\\("); lacks(preview, "TrioZones\\.boxes\\(highlight");
    }

    @Test public void appearanceCarriesFlagsWithoutReadingRuntimeState() throws Exception {
        String appearance = source("TrioAppearance.java");
        lacks(appearance, "TrioState\\.|WifiPresence\\."); has(appearance, "typeHideOnWifi");
    }

    @Test public void typeSuppressionUsesConnectivityInsteadOfSignalLevel() throws Exception {
        String renderer = source("TrioRenderer.java");
        lacks(renderer, "typeHideOnWifi && wifiLevel"); has(renderer, "wifiConnected\\)");
        has(source("TrioAppearance.java"), "typeHideOnWifi \\|\\| !wifiConnected");
    }

    @Test public void typeSuppressionRemainsAnArrangementAndBandDecision() throws Exception {
        lacks(source("TrioRenderer.java"), "typeHideOnWifi && wifiConnected");
        String appearance = source("TrioAppearance.java");
        has(appearance, "public boolean hidesTypeForWifi"); has(appearance, "hasDrawableBehind");
    }

    @Test public void editorUsesCurrentCallbacksAndFeedbackForAppliedPriorityLists() throws Exception {
        String drawing = source("ui/DrawingScreen.kt");
        String feedback = source("ui/DrawingZones.kt");
        var match = Pattern.compile("(?s)internal fun DrawingGlyph\\(.*?\\n\\}").matcher(source("ui/DrawingPreview.kt"));
        assertTrue("Missing DrawingGlyph", match.find()); String glyph = match.group();
        has(glyph, "currentEdit = rememberUpdatedState\\(onEditZone\\)");
        has(glyph, "(?s)onLongPress = \\{ offset ->.*?currentEdit\\.value\\(it\\)");
        lacks(glyph, "onLongPress = \\{ offset -> zoneAt\\(offset\\)");
        has(drawing, "(?s)onEditZone = \\{ zone ->.*?TrioZones\\.occupant\\(.*?if \\(part == null\\) \\{\\s*picking = zone\\s*\\} else \\{\\s*editing = TrioSettingsPart\\.of\\(part\\)");
        has(drawing, "val next = TrioZones\\.withPiece\\(settings, pickZone\\.ordinal, part\\)");
        has(drawing, "applyZoneAssignment\\(next, r, settings\\)");
        has(drawing, "lastSelection = part\\?\\.let \\{ ZoneSelection\\(pickZone, it\\) \\}");
        has(drawing, "PlacementFeedback\\(selection, appearance, scene, sims\\)");
        has(feedback, "if \\(actual == selection\\.part\\) return");
        has(drawing, "TrioZones\\.(withPiece\\(settings, zone\\.ordinal, null\\)|withoutAt\\(settings, zone\\.ordinal, index\\))");
        has(drawing, "TrioZones\\.moveWithin\\(settings, zone\\.ordinal, index, delta\\)");
        for (String reason : new String[]{"charging", "bolt", "wifi", "other"})
            has(feedback, "R\\.string\\.drawing_feedback_" + reason);
    }
}
