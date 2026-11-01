/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;
import org.junit.Test;
import static org.junit.Assert.*;
public class RingViewportTest {
    @Test public void enlargementUsesAvailableRowSpaceAndKeepsNativeSpacing() {
        assertArrayEquals(new int[]{85,52},RingViewport.target(85,52,380,81,100));
        assertArrayEquals(new int[]{85,65},RingViewport.target(85,52,380,81,125));
        assertArrayEquals(new int[]{85,78},RingViewport.target(85,52,380,81,150));
    }
    @Test public void constrainedRowsAndNarrowWindowsCannotBeOverrun() {
        assertArrayEquals(new int[]{85,60},RingViewport.target(85,52,380,60,150));
        assertArrayEquals(new int[]{64,64},RingViewport.target(52,52,64,81,150));
        assertArrayEquals(new int[]{85,52},RingViewport.target(85,52,380,81,100));
    }
}
