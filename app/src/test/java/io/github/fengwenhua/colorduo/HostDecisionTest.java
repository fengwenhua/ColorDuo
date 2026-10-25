/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

import org.junit.Test;
import static org.junit.Assert.*;
import static io.github.fengwenhua.colorduo.HostDecision.Action.*;

/** Event sequences protect ownership: a mounted drawable alone never hides native rows. */
public final class HostDecisionTest {
    private HostDecision.Model model(int style, boolean popup, int width) {
        return new HostDecision.Model(true, style, popup, 67, 0xff000000, width, 52);
    }
    @Test public void firstFrameMustPreflightMountDrawAndOnlyThenCommit() {
        assertEquals(PREFLIGHT, HostDecision.attach(new HostDecision.Attachment(false, true, false)).action());
        assertEquals(MOUNT, HostDecision.mount(false).action());
        assertEquals(WAIT, HostDecision.presented(new HostDecision.Presentation(false, true, true, true, true, true)).action());
        assertEquals(COMMIT_LEASES, HostDecision.presented(new HostDecision.Presentation(true, true, true, true, true, true)).action());
    }
    @Test public void foldAndResizeKeepTheExistingHandoffRules() {
        HostDecision.Decision nativeLayout = HostDecision.gate(new HostDecision.Gate(true, false, true, true, false));
        assertEquals(WAIT, nativeLayout.action()); assertTrue(nativeLayout.releaseLeases());
        HostDecision.Decision moduleResize = HostDecision.gate(new HostDecision.Gate(true, false, true, true, true));
        assertEquals(WAIT, moduleResize.action()); assertFalse(moduleResize.releaseLeases());
        assertEquals(RELEASE, HostDecision.prepare(new HostDecision.Preparation(true, true)).action());
        assertEquals(PREFLIGHT, HostDecision.attach(new HostDecision.Attachment(true, true, false)).action());
        assertEquals(UPDATE, HostDecision.attach(new HostDecision.Attachment(true, true, true)).action());
    }
    @Test public void plugPopupRequiresAFreshBridgeAndNestedNativeUpdatesWait() {
        ColorOsPlugBridge bridge = new ColorOsPlugBridge();
        assertEquals(UPDATE, HostDecision.model(model(1, false, 85)).action());
        bridge.power(false, 0); bridge.power(true, 100);
        boolean popup = bridge.style(2, true, 200);
        assertTrue(popup);
        assertEquals(UPDATE, HostDecision.model(model(2, popup, 85)).action());
        HostDecision.Decision nested = HostDecision.gate(new HostDecision.Gate(true, true, false, false, false));
        assertEquals(WAIT, nested.action()); assertFalse(nested.releaseLeases());
        bridge.style(1, true, 6_200);
        assertEquals(RELEASE, HostDecision.model(model(2, bridge.active(), 85)).action());
        assertEquals(UPDATE, HostDecision.model(model(1, false, 85)).action());
    }
    @Test public void disabledFailedOrUnsupportedHostReleasesWithoutClaimingAnotherDrawable() {
        assertEquals(RELEASE, HostDecision.gate(new HostDecision.Gate(true, false, false, false, false)).action());
        assertEquals(RELEASE, HostDecision.model(model(1, false, 0)).action());
        assertEquals(RELEASE, HostDecision.model(model(1, false, 2049)).action());
        assertEquals(RELEASE, HostDecision.model(model(9, false, 85)).action());
        assertEquals(RELEASE, HostDecision.model(new HostDecision.Model(false, 1, false, 50, -1, 85, 52)).action());
        assertEquals(WAIT, HostDecision.attach(new HostDecision.Attachment(false, false, false)).action());
    }
    @Test public void networkChangesAndStaleCallbacksWaitForTheirOwnFrame() {
        assertEquals(WAIT, HostDecision.presented(new HostDecision.Presentation(true, true, true, true, true, false)).action());
        assertEquals(WAIT, HostDecision.presented(new HostDecision.Presentation(true, false, true, true, true, true)).action());
        assertEquals(WAIT, HostDecision.presented(new HostDecision.Presentation(true, true, false, true, true, true)).action());
        assertEquals(WAIT, HostDecision.presented(new HostDecision.Presentation(true, true, true, false, true, true)).action());
        assertEquals(WAIT, HostDecision.presented(new HostDecision.Presentation(true, true, true, true, false, true)).action());
        assertEquals(COMMIT_LEASES, HostDecision.presented(new HostDecision.Presentation(true, true, true, true, true, true)).action());
    }

    @Test public void plugThenFoldKeepsDrawnPopupUntilNewLayoutAndFrame() {
        ColorOsPlugBridge bridge = new ColorOsPlugBridge();
        bridge.power(false, 0); bridge.power(true, 100);
        assertTrue(bridge.style(2, true, 200));
        for (int fold = 0; fold < 2; fold++) {
            HostDecision.Decision transition = HostDecision.configuration(new HostDecision.Configuration(
                    true, true, true, true, true, false, false, true, 2, bridge.active()));
            assertEquals(WAIT, transition.action()); assertFalse(transition.releaseLeases());
            assertTrue(bridge.active());
            assertFalse(HostDecision.gate(new HostDecision.Gate(true, false, true, true, true)).releaseLeases());
            assertEquals(PREFLIGHT, HostDecision.attach(new HostDecision.Attachment(true, true, false)).action());
            assertEquals(UPDATE, HostDecision.mount(true).action());
            assertEquals(WAIT, HostDecision.presented(new HostDecision.Presentation(true, true, false, true, true, true)).action());
            assertEquals(COMMIT_LEASES, HostDecision.presented(new HostDecision.Presentation(true, true, true, true, true, true)).action());
        }
        assertFalse(bridge.style(1, true, 6_100));
        assertEquals(UPDATE, HostDecision.model(model(1, false, 85)).action());
    }

    @Test public void configurationCannotKeepDisabledHiddenFailedOrForeignFrames() {
        assertTrue(HostDecision.configuration(new HostDecision.Configuration(false, true, true, true, true, false, false, true, 2, true)).releaseLeases());
        assertTrue(HostDecision.configuration(new HostDecision.Configuration(true, true, true, true, true, true, false, true, 2, true)).releaseLeases());
        assertTrue(HostDecision.configuration(new HostDecision.Configuration(true, false, true, true, true, false, false, true, 2, true)).releaseLeases());
        assertTrue(HostDecision.configuration(new HostDecision.Configuration(true, true, false, true, true, false, false, true, 2, true)).releaseLeases());
        assertTrue(HostDecision.configuration(new HostDecision.Configuration(true, true, true, false, true, false, false, true, 2, true)).releaseLeases());
        assertTrue(HostDecision.configuration(new HostDecision.Configuration(true, true, true, true, false, false, false, true, 2, true)).releaseLeases());
        assertTrue(HostDecision.configuration(new HostDecision.Configuration(true, true, true, true, true, false, true, true, 2, true)).releaseLeases());
        assertTrue(HostDecision.configuration(new HostDecision.Configuration(true, true, true, true, true, false, false, false, 2, true)).releaseLeases());
        assertTrue(HostDecision.configuration(new HostDecision.Configuration(true, true, true, true, true, false, false, true, 2, false)).releaseLeases());
        assertTrue(HostDecision.configuration(new HostDecision.Configuration(true, true, true, true, true, false, false, true, 5, true)).releaseLeases());
    }
}
