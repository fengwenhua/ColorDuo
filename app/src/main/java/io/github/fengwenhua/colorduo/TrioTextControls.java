/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

import android.graphics.Paint;
import android.graphics.Path;
import java.util.Arrays;

/** Effective numeric controls for the font and layout actually used by the renderer. */
public final class TrioTextControls {
    private TrioTextControls() {}

    private static int fontGeneration = -1;
    private static int[] weights;
    private static String sizeKey;
    private static int sizeMax;

    /**
     * Font vendors may advertise weights beyond the outlines their file contains.
     * Compare real outlines, not advance widths (tabular digits can share advances).
     * Keep one setting per consecutive identical outline; custom synthetic bold is
     * a separate result even on platforms where getTextPath omits that paint flag.
     * This runs on a font change, never in the drawing path.
     */
    public static int[] valueWeights(TrioAppearance a) {
        TrioRenderer.setFont(a.fontPath);
        final int generation = TrioRenderer.fontGeneration();
        if (weights == null || fontGeneration != generation) {
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setTextSize(128f);
            Path path = new Path();
            float[] previous = null;
            boolean previousBold = false;
            int[] result = new int[9];
            int count = 0;
            for (int weight = Prefs.MIN_VALUE_WEIGHT; weight <= Prefs.MAX_VALUE_WEIGHT; weight += 100) {
                paint.setTypeface(TrioRenderer.typefaceFor(weight));
                boolean bold = TrioRenderer.fakeBoldFor(weight);
                paint.setFakeBoldText(bold);
                path.reset();
                paint.getTextPath("0123456789", 0, 10, 0f, 0f, path);
                float[] outline = path.approximate(.05f);
                if (previous == null || bold != previousBold || !Arrays.equals(previous, outline)) {
                    result[count++] = weight;
                }
                previous = outline;
                previousBold = bold;
            }
            weights = Arrays.copyOf(result, count);
            fontGeneration = generation;
        }
        return weights.clone();
    }

    /**
     * Bound the ring slider to an honest size for its placed percentage. Check all
     * battery readings so reaching 100 does not silently shrink the selected size.
     * Hidden fallback placements count too, keeping the control stable while charging.
     * Existing saved sizes are not rewritten; drawing retains its safety fit.
     */
    public static int maxValueSize(TrioAppearance a) {
        TrioRenderer.setFont(a.fontPath);
        final int maximum = Prefs.maxValueSize(a.style);
        if (!a.ring) return maximum;
        String key = TrioRenderer.fontGeneration() + ":" + a.valueWeight + ":"
                + a.ringStroke + ":" + Prefs.encodeLayout(a.ringZones)+":"+a.ringValuePriority
                +":"+a.wifiScale+":"+a.boltScale+":"+a.dotScale+":"+a.arcStroke
                +":"+a.typeSize+":"+a.typeWeight+":"+a.clockSize+":"+a.clockWeight+":"+a.value;
        if (key.equals(sizeKey)) return sizeMax;
        int answer = maximum;
        for (int z = 0; z < 3; z++) {
            boolean placed = false;
            for (int part : a.ringZones[z]) placed |= part == Prefs.PART_VALUE;
            if (!placed) continue;
            int last = Prefs.MIN_VALUE_SIZE;
            for (int size = Prefs.MIN_VALUE_SIZE; size <= maximum; size++) {
                boolean fits = true;
                for (int level = 0; level <= 100 && fits; level++) {
                    fits = TrioRenderer.fittedRingTextSize(a, z, Prefs.PART_VALUE,
                            String.valueOf(level), size) >= size - .05f;
                }
                if (!fits) break;
                last = size;
            }
            answer = Math.min(answer, last);
        }
        sizeKey = key;
        sizeMax = answer;
        return answer;
    }
}
