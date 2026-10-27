/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

import org.junit.Test;
import static org.junit.Assert.*;

public final class StatusBarHeightPolicyTest {
    @Test public void scaleEachDisplayFromItsOwnNativeHeightWithoutShrinkingCutouts() {
        assertEquals(107,StatusBarHeightPolicy.scale(107,100));
        assertEquals(128,StatusBarHeightPolicy.scale(107,120));
        assertEquals(161,StatusBarHeightPolicy.scale(107,150));
        assertEquals(144,StatusBarHeightPolicy.scale(120,120));
        assertEquals(107,StatusBarHeightPolicy.scale(107,-100));
        assertEquals(161,StatusBarHeightPolicy.scale(107,Integer.MAX_VALUE));
    }
    @Test public void nonDimensionLayoutValuesAndIntegerOverflowAreHandled() {
        assertEquals(-1,StatusBarHeightPolicy.scale(-1,150));
        assertEquals(0,StatusBarHeightPolicy.scale(0,150));
        assertEquals(Integer.MAX_VALUE,StatusBarHeightPolicy.scale(Integer.MAX_VALUE,150));
    }
    @Test public void disabledOrUnknownConfigurationRestoresNativeHeight() {
        assertEquals(100,StatusBarHeightPolicy.percent(null));
        TrioSettings s=TrioSettings.defaults();s.statusBarHeightPercent=140;
        assertEquals(140,StatusBarHeightPolicy.percent(s));
        s.enabled=false;assertEquals(100,StatusBarHeightPolicy.percent(s));
    }
}
