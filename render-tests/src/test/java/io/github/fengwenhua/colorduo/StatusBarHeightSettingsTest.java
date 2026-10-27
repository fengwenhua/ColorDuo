/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public final class StatusBarHeightSettingsTest {
    @Test public void nativeDefaultAndRangeSurviveLegacyAndBundleReads() {
        assertEquals(100,TrioSettings.defaults().statusBarHeightPercent);
        assertEquals(100,TrioSettings.from(new PreferencesSnapshot(Map.of())).statusBarHeightPercent);
        TrioSettings s=TrioSettings.defaults();s.statusBarHeightPercent=140;
        assertEquals(s,TrioSettings.fromBundle(s.toBundle()));
        var bundle=s.toBundle();bundle.putInt(Prefs.KEY_STATUS_BAR_HEIGHT,500);
        assertEquals(150,TrioSettings.fromBundle(bundle).statusBarHeightPercent);
    }
    @Test public void heightIsGlobalAcrossStylesAndChargeScenesWithoutChangingOtherSettings() {
        for(int style:new int[]{Prefs.STYLE_RING,Prefs.STYLE_CAPSULE}) {
            TrioSettings base=TrioSettings.defaults();base.trioStyle=style;
            TrioSettings next=base.copy();next.statusBarHeightPercent=125;
            assertNotEquals(base,next);assertEquals(next,next.copy());
            assertEquals(next.hashCode(),next.copy().hashCode());
            TrioSettings partial=base.copy();assertTrue(partial.applyKeyFrom(next,Prefs.KEY_STATUS_BAR_HEIGHT));
            assertEquals(next,partial);
            for(int scene:new int[]{ChargeLayout.BASE,ChargeLayout.CHARGING,ChargeLayout.QUICK})
                assertEquals(125,ChargeLayout.forScene(next,scene).statusBarHeightPercent);
            assertEquals(100,base.statusBarHeightPercent);
        }
    }
}
