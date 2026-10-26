/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

import android.os.BatteryManager;
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

    @Test public void chargingOnlyReadoutsDisappearAtFullAndReturnWhenChargingResumes() {
        TrioSettings settings = TrioSettings.defaults();
        settings.setPerfPower(true); settings.setPerfTemp(true); settings.setPerfTempChargingOnly(true);
        // Keep the last current/temperature unchanged: a status transition alone must clear the strip.
        ColorOsReadoutSource.State charging = new ColorOsReadoutSource.State(
                ColorOsReadout.charging(BatteryManager.BATTERY_STATUS_CHARGING, 2), 0.9f, 34);
        ColorOsReadoutSource.State full = new ColorOsReadoutSource.State(
                ColorOsReadout.charging(BatteryManager.BATTERY_STATUS_FULL, 2), 0.9f, 34);
        assertEquals("0.9W\n34\u00b0C", charging.text(settings));
        assertFalse(charging.same(full));
        assertEquals("", full.text(settings));
        assertFalse(full.same(charging));
        assertEquals("0.9W\n34\u00b0C", charging.text(settings));
    }

    @Test public void fullOrPausedStateKeepsTemperatureOnlyWhenTheFilterIsOff() {
        TrioSettings settings = TrioSettings.defaults();
        settings.setPerfPower(true); settings.setPerfTemp(true);
        for (int status : new int[]{BatteryManager.BATTERY_STATUS_FULL, BatteryManager.BATTERY_STATUS_NOT_CHARGING,
                BatteryManager.BATTERY_STATUS_DISCHARGING, BatteryManager.BATTERY_STATUS_UNKNOWN}) {
            ColorOsReadoutSource.State stopped = new ColorOsReadoutSource.State(ColorOsReadout.charging(status, 2), 0.9f, 34);
            settings.setPerfTempChargingOnly(true);
            assertEquals("", stopped.text(settings));
            settings.setPerfTempChargingOnly(false);
            assertEquals("34\u00b0C", stopped.text(settings));
        }
    }

    @Test public void acUsbAndWirelessRequireAnActualChargingStatus() {
        for (int plugged : new int[]{1, 2, 4}) {
            assertTrue(ColorOsReadout.charging(BatteryManager.BATTERY_STATUS_CHARGING, plugged));
            assertFalse(ColorOsReadout.charging(BatteryManager.BATTERY_STATUS_FULL, plugged));
        }
        assertFalse(ColorOsReadout.charging(BatteryManager.BATTERY_STATUS_CHARGING, 0));
    }

    @Test public void repeatedStoppedSamplesDeduplicateButNewTemperatureStillRefreshes() {
        ColorOsReadoutSource.State stopped = new ColorOsReadoutSource.State(false, Float.NaN, 34);
        assertTrue(stopped.same(new ColorOsReadoutSource.State(false, Float.NaN, 34)));
        assertFalse(stopped.same(new ColorOsReadoutSource.State(true, Float.NaN, 34)));
        assertFalse(stopped.same(new ColorOsReadoutSource.State(false, Float.NaN, 33)));
    }

    @Test public void chargingOnlyPairWaitsForBothReadingsAndHonorsIndependentSwitches() {
        TrioSettings settings = TrioSettings.defaults();
        settings.setPerfPower(true); settings.setPerfTemp(true); settings.setPerfTempChargingOnly(true);
        assertEquals("", new ColorOsReadoutSource.State(true, Float.NaN, 34).text(settings));
        assertEquals("", new ColorOsReadoutSource.State(true, 0.9f, Integer.MIN_VALUE).text(settings));
        assertEquals("0.9W\n34\u00b0C", new ColorOsReadoutSource.State(true, 0.9f, 34).text(settings));
        settings.setPerfPower(false);
        assertEquals("34\u00b0C", new ColorOsReadoutSource.State(true, Float.NaN, 34).text(settings));
        settings.setPerfPower(true); settings.setPerfTemp(false);
        assertEquals("0.9W", new ColorOsReadoutSource.State(true, 0.9f, Integer.MIN_VALUE).text(settings));
    }
}
