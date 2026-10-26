/*
 * Copyright (C) 2026 yixing233 and HyperDuo contributors
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.github.fengwenhua.colorduo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original integration tripwires complement the real pure sizing method bodies. */
public final class ReadoutLayoutSourceContractTest {
    private String hooks() throws Exception {
        return Files.readString(Path.of(System.getProperty("colorduo.productionSources"), "TrioHooks.java"));
    }
    private String method(String signature) throws Exception {
        var match = Pattern.compile(Pattern.quote(signature) + " \\{.*?\n    \\}", Pattern.DOTALL).matcher(hooks());
        assertTrue("production method missing: " + signature, match.find()); return match.group();
    }
    private void matches(String text, String pattern) { assertTrue("missing integration: " + pattern, Pattern.compile(pattern, Pattern.DOTALL).matcher(text).find()); }

    @Test public void dedicatedWeightAndFontGenerationReachStyleAndResync() throws Exception {
        String code = hooks();
        for (String pattern : new String[]{"perfLineHeight\\(base, a\\.perfTypeWeight\\(\\)\\)",
                "TrioRenderer\\.styleTextView\\(perf, weight\\)", "perf\\.weight != weight", "perf\\.weight = weight"}) matches(code, pattern);
        assertFalse(code.contains("final int weight = a.typeWeight;"));
        assertFalse(Pattern.compile("perfLineHeight\\([^)]*a\\.typeWeight\\)").matcher(code).find());
        var gate = Pattern.compile("if \\(perf\\.getVisibility\\(\\) != View\\.VISIBLE.*?requestPerfSync\\(container\\);", Pattern.DOTALL).matcher(code);
        assertTrue(gate.find());
        for (String pattern : new String[]{"perf\\.getVisibility\\(\\)", "contentEquals", "getTextSize", "getLineSpacingMultiplier",
                "perf\\.weight != weight", "perf\\.fontGeneration != TrioRenderer\\.fontGeneration\\(\\)"}) matches(gate.group(), pattern);
    }

    @Test public void sharedStripUsesReservedWidthAndTeardownRecomputesTheWholeStrip() throws Exception {
        String plan = method("private static OutRingLayout.Plan outRingPlan(ViewGroup container, View icons)");
        assertTrue(plan.contains("outRingSlotWidth(views[i])")); assertFalse(plan.contains("perf.getMeasuredWidth()"));
        assertTrue(method("private static int outRingSlotWidth(View view)").contains("perfWidth(perf,"));
        String width = method("private static int perfWidth(PerfLabel perf, String text)");
        assertTrue(width.contains("PERF_BREAK")); matches(width, "c >= .0. && c <= .9.");
        for (String name : new String[]{"removeOutTypeLabel", "removePerfLabel"}) {
            String body = method("private static void " + name + "(View container)");
            assertFalse(body.contains("releaseOutTypeSpace(")); assertTrue(body.contains("reserveOutRingStrip("));
        }
    }

    @Test public void lineSpacingAndMeasurementKeepTheCurrentReadoutTypeface() throws Exception {
        matches(method("private static float perfLineHeight(float size, int weight)"),
                "PERF_PROBE\\.setTypeface\\(TrioRenderer\\.typefaceFor\\(weight\\)\\);");
        String code = hooks();
        matches(code, "perf\\.getLineSpacingMultiplier\\(\\)\\s*\n\\s*!= perfLineSpacing\\(perf, text, container\\)");
        matches(code, "if \\(perf\\.getLineSpacingMultiplier\\(\\) != spacing\\) \\{\\s*\n\\s*perf\\.setLineSpacing\\(0f, spacing\\);");
    }

    @Test public void configuredFloorsAreStrictlyBetweenZeroAndOne() {
        assertTrue(PerfLayoutTest.PERF_MIN_SIZE_RATIO > 0f && PerfLayoutTest.PERF_MIN_SIZE_RATIO < 1f);
        assertTrue(PerfLayoutTest.PERF_MIN_LINE_SPACING > 0f && PerfLayoutTest.PERF_MIN_LINE_SPACING < 1f);
    }
}
