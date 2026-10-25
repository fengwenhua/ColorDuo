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
