/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

import org.junit.Test;
import static org.junit.Assert.*;

public final class VpnNetworksTest {
    @Test public void connectionAndDisconnectionPublishOnlyRealTransitions() {
        VpnNetworks<String> state = new VpnNetworks<>();
        assertFalse(state.active());
        assertTrue(state.update("vpn-a", true)); assertTrue(state.active());
        assertFalse(state.update("vpn-a", true));
        assertFalse(state.update("unknown", false)); assertTrue(state.active());
        assertTrue(state.update("vpn-a", false)); assertFalse(state.active());
    }
    @Test public void overlappingHandoverDoesNotResetHighlight() {
        VpnNetworks<String> state = new VpnNetworks<>();
        state.update("wifi-tunnel", true);
        assertFalse(state.update("mobile-tunnel", true));
        assertFalse(state.update("wifi-tunnel", false)); assertTrue(state.active());
        assertTrue(state.update("mobile-tunnel", false)); assertFalse(state.active());
    }
    @Test public void disableClearsTrackedNetworksBeforeReconnect() {
        VpnNetworks<String> state = new VpnNetworks<>();
        state.update("old", true); state.clear(); assertFalse(state.active());
        assertFalse(state.update("old", false));
        assertTrue(state.update("new", true));
    }
}
