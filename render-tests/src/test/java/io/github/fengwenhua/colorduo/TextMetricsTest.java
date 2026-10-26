/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

import android.graphics.Paint;
import android.graphics.Typeface;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

/** The desktop text fixture must preserve the proportional advances Android exposes. */
public final class TextMetricsTest {
    @Test public void fractionalAdvancesScaleWithSizeIncludingTracking() {
        Paint paint = new Paint();
        for (Typeface face : new Typeface[]{Typeface.DEFAULT, Typeface.DEFAULT_BOLD}) {
            paint.setTypeface(face);
            for (float tracking : new float[]{0f, -.04f, .1f}) {
                paint.setLetterSpacing(tracking);
                for (String text : new String[]{"100", "79", "5G"}) {
                    paint.setTextSize(48.3f);
                    float base = paint.measureText(text);
                    for (float scale : new float[]{.5f, .9f, 1.3f}) {
                        paint.setTextSize(48.3f * scale);
                        assertEquals(text + "/" + tracking + "/" + scale,
                                base * scale, paint.measureText(text), .001f);
                    }
                }
            }
        }
    }
}
