/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

import org.junit.Test;
import static org.junit.Assert.*;

public final class WindowSheetPolicyTest {
    @Test public void outerInnerAndSplitWindowsUseTheirOwnBounds() {
        assertFalse(WindowSheetPolicy.compact(2616, 3f, 1f));
        assertFalse(WindowSheetPolicy.compact(2480, 3f, 1f));
        assertTrue(WindowSheetPolicy.compact(1200, 3f, 1f));
        assertEquals(288f, WindowSheetPolicy.maxHeightDp(1200, 3f), 0.01f);
        assertEquals(576f, WindowSheetPolicy.maxHeightDp(2400, 3f), 0.01f);
    }
    @Test public void largeFontsSelectCompactScrollableControls() {
        assertFalse(WindowSheetPolicy.compact(2400, 3f, 1f));
        assertFalse(WindowSheetPolicy.compact(2400, 3f, 1.3f));
        assertTrue(WindowSheetPolicy.compact(2400, 3f, 1.5f));
        assertTrue(WindowSheetPolicy.compact(0, 3f, 1f));
        assertThrows(IllegalArgumentException.class, () -> WindowSheetPolicy.heightDp(2400, 0));
    }
}
