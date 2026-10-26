/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

import org.junit.Test;
import static org.junit.Assert.*;

public final class ColorOsReadoutTest {
    @Test public void measuredOplusMilliampSamplesRenderNonZeroPower() {
        // Find N5: API current -651 mA, battery voltage 4415 mV, charge counter rising.
        for (int current : new int[]{-651, 651}) {
            float watts = ColorOsReadout.wattsOf(current, 4415);
            assertEquals(2.874165f, watts, 0.00001f);
            TrioSettings settings = TrioSettings.defaults();
            settings.setPerfPower(true); settings.setPerfTemp(false);
            assertEquals("2.9W", new ColorOsReadoutSource.State(true, watts, 34).text(settings));
        }
    }

    @Test public void smallAndLargeCurrentsStayInOneUnit() {
        assertEquals(0.0044f, ColorOsReadout.wattsOf(-1, 4400), 0.000001f);
        assertEquals(0.044f, ColorOsReadout.wattsOf(10, 4400), 0.000001f);
        assertEquals(44f, ColorOsReadout.wattsOf(-10000, 4400), 0.00001f);
        assertEquals(66f, ColorOsReadout.wattsOf(15000, 4400), 0.00001f);
    }

    @Test public void unknownReadingsStayUnknownButRealZeroIsDisplayed() {
        assertTrue(Float.isNaN(ColorOsReadout.wattsOf(Integer.MIN_VALUE, 4400)));
        assertTrue(Float.isNaN(ColorOsReadout.wattsOf(500, 0)));
        assertTrue(Float.isNaN(ColorOsReadout.wattsOf(500, -1)));
        TrioSettings settings = TrioSettings.defaults();
        settings.setPerfPower(true); settings.setPerfTemp(false);
        assertEquals("", new ColorOsReadoutSource.State(true, Float.NaN, 34).text(settings));
        assertEquals("0.0W", new ColorOsReadoutSource.State(true,
                ColorOsReadout.wattsOf(0, 4400), 34).text(settings));
    }

    @Test public void powerAndTemperatureFormattingIsUnchanged() {
        TrioSettings settings = TrioSettings.defaults();
        settings.setPerfPower(true); settings.setPerfTemp(true); settings.setPerfTempChargingOnly(false);
        assertEquals("2.9W\n34\u00b0C", new ColorOsReadoutSource.State(true,
                ColorOsReadout.wattsOf(-651, 4415), 34).text(settings));
        assertEquals("34\u00b0C", new ColorOsReadoutSource.State(true, Float.NaN, 34).text(settings));
        assertEquals("34\u00b0C", new ColorOsReadoutSource.State(false, 2.9f, 34).text(settings));
    }

    @Test public void hyperOsStillUsesMicroamps() {
        assertEquals(ColorOsReadout.wattsOf(-651, 4415), HyperOsReadout.wattsOf(-651000, 4415), 0.00001f);
        assertEquals(0.002874165f, HyperOsReadout.wattsOf(-651, 4415), 0.0000001f);
    }
}
