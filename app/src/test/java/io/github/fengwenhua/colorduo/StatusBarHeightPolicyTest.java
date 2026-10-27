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
    @Test public void bothDisplayContentBandsStayCentredOnThePhysicalCamera() {
        for(int[] nativeCase:new int[][]{{107,17,0,17,115},{107,23,0,23,107}})
            for(int percent=105;percent<=150;percent+=5) {
                int[] padding=StatusBarHeightPolicy.padding(nativeCase[0],nativeCase[1],nativeCase[2],nativeCase[3],nativeCase[4],percent);
                int height=StatusBarHeightPolicy.scale(nativeCase[0],percent);
                assertTrue(padding[0]>=0);assertTrue(padding[1]>=0);
                assertTrue(height-padding[0]-padding[1]>0);
                assertTrue(Math.abs((height+padding[0]-padding[1])-(nativeCase[3]+nativeCase[4]))<=1);
            }
    }
    @Test public void restoringDefaultReturnsExactNativePaddingWithoutTheOldBottomGap() {
        assertArrayEquals(new int[]{0,29},StatusBarHeightPolicy.padding(107,17,0,17,115,150));
        assertArrayEquals(new int[]{17,0},StatusBarHeightPolicy.padding(107,17,0,17,115,100));
        assertArrayEquals(new int[]{23,0},StatusBarHeightPolicy.padding(107,23,0,23,107,100));
        assertArrayEquals(new int[]{0,0},StatusBarHeightPolicy.padding(55,0,0,0,0,150));
    }
}
