/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

import org.junit.Test;
import static org.junit.Assert.*;

/** Runs the existing safety assertions through Gradle/JUnit, plus plug event sequences. */
public final class PolicyRegressionTest {
    @Test public void routingAndBoundedDiagnostics() throws Exception { ColorOsObserverTest.main(new String[0]); }
    @Test public void snapshotOrdering() { ConfigRevisionGateTest.main(new String[0]); }
    @Test public void warningPreservation() { ColorOsNetworkPolicyTest.main(new String[0]); }
    @Test public void exactTypeLabels() { ColorOsTypeLabelsTest.main(new String[0]); }

    @Test public void registered5gDisappearsWhenDataIsOffAndReturnsWhenDataIsEnabled() {
        assertEquals("5G", ColorOsNetworkPolicy.typeLabel("5G", true));
        assertEquals("", ColorOsNetworkPolicy.typeLabel("5G", false));
        assertEquals("", ColorOsNetworkPolicy.typeLabel("LTE", false));
        assertEquals("5G", ColorOsNetworkPolicy.typeLabel("5G", true));
        assertEquals("", ColorOsNetworkPolicy.typeLabel("", true));
        assertEquals("5G", ColorOsNetworkPolicy.typeLabel("5G", null));
    }

    @Test public void defaultAndPerSubscriptionDataSwitchesTriggerRefreshButOtherSettingsDoNot() {
        for (String key : new String[] {"mobile_data", "mobile_data1", "mobile_data12", "multi_sim_data_call",
                "airplane_mode_on", "global", null})
            assertTrue(String.valueOf(key), ColorOsNetworkPolicy.dataSettingChanged(key));
        for (String key : new String[] {"wifi_on", "screen_brightness", "bluetooth_on", ""})
            assertFalse(key, ColorOsNetworkPolicy.dataSettingChanged(key));
    }

    @Test public void suppressingRegisteredTypeSurvivesPlanCopiesAndNeedsNoSignalOccupant() {
        var source = new ColorOsNetworkAdapter.Plan(-1, -1, null, null, null, 0, "", null, true, true);
        assertTrue(source.withSignals(false, false).suppressType);
        assertTrue(source.withTypeOwnership(false).suppressType);
        var withoutSuppression = new ColorOsNetworkAdapter.Plan(-1, -1, null, null, null, 0, "", null, true, false);
        assertFalse(source.same(withoutSuppression));
        TrioSettings settings = TrioSettings.defaults();
        settings.mobileTypeMode = Prefs.MOBILE_TYPE_IN_RING;
        settings.showMobile = settings.showWifi = false;
        settings.ringZones = Prefs.decodeLayout("5|||");
        for (int style : new int[] {Prefs.STYLE_RING, Prefs.STYLE_CAPSULE}) {
            settings.trioStyle = style;
            TrioSettings paint = ColorOsRingPreset.userLayout(settings, false, source);
            assertEquals(Prefs.MOBILE_TYPE_OFF, paint.mobileTypeMode);
            assertTrue(ColorOsRingPreset.represented(paint, 79, false, source).hideType);
        }
    }

    @Test public void disablingMobileDataOnWifiKeepsTheSignalUntilTheOplusWarningChanges() {
        // Captured ROM derivation: OPlus copies the AOSP Cellular model but derives
        // inetCondition from defaultConnections. Disabling data changes the inherited
        // showExclamationMark to true while native inetCondition stays 1 on Wi-Fi.
        String[] states = {"wifi+data", "wifi, data off", "wifi lost, data off",
                "wifi restored, data off", "wifi+data restored"};
        int[] nativeConditions = {1, 1, 0, 1, 1};
        int[] expected = {3, 3, -1, 3, 3};
        for (int i = 0; i < states.length; i++) {
            assertEquals(states[i], expected[i],
                    ColorOsNetworkPolicy.mobile(true, true, nativeConditions[i], false, false, 3, 5));
        }
    }

    @Test public void normalOplusSignalLevelsRemainRepresentableAndUnknownWarningsStayNative() {
        for (int level = 0; level <= 4; level++)
            assertEquals(level, ColorOsNetworkPolicy.mobile(true, true, 1, false, false, level, 5));
        for (int unknown : new int[] {-1, 0, 2, Integer.MAX_VALUE})
            assertEquals(-1, ColorOsNetworkPolicy.mobile(true, true, unknown, false, false, 4, 5));
    }

    @Test public void chargingPopupConsumesOneFreshPlugEvent() {
        ColorOsPlugBridge bridge = new ColorOsPlugBridge();
        bridge.power(true, 0);
        assertFalse(bridge.style(2, true, 1));
        bridge.power(false, 100);
        bridge.power(true, 200);
        assertTrue(bridge.style(2, true, 5_200));
        assertTrue(bridge.style(2, true, 20_000));
        assertFalse(bridge.style(1, true, 20_001));
        assertFalse(bridge.style(2, true, 20_002));
    }

    @Test public void expiredHiddenAndDisabledHostsCannotClaimPopup() {
        ColorOsPlugBridge bridge = new ColorOsPlugBridge();
        bridge.power(false, 0);
        bridge.power(true, 1);
        assertFalse(bridge.style(2, true, 5_002));
        bridge.power(false, 6_000);
        bridge.power(true, 6_001);
        assertFalse(bridge.style(2, false, 6_002));
        assertFalse(bridge.style(2, true, 6_003));
        bridge.power(false, 7_000);
        bridge.power(true, 7_001);
        assertTrue(bridge.style(2, true, 7_002));
        bridge.clear();
        assertFalse(bridge.active());
        assertFalse(bridge.style(2, true, 7_003));
    }
}
