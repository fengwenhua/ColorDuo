/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

import android.view.View;
import org.junit.Test;
import static org.junit.Assert.*;

/** Executes the production lease; the View shim models visibility, not OEM hook dispatch. */
public final class ColorOsPercentIconTest {
    @Test public void outsideNumberIsRetainedUntilCommitAndRestoredOnRelease() {
        View view = new View();
        ColorOsPercentIcon icon = new ColorOsPercentIcon(view);
        assertTrue(icon.isFor(view));
        assertFalse(icon.owned());
        assertEquals(View.VISIBLE, view.getVisibility());
        icon.commit(false);
        assertEquals(View.VISIBLE, view.getVisibility());
        icon.commit(true);
        assertTrue(icon.owned());
        assertEquals(View.GONE, view.getVisibility());
        icon.commit(true); // A repeated draw must not cache our GONE as the system's state.
        icon.restore();
        icon.restore();
        assertFalse(icon.owned());
        assertEquals(View.VISIBLE, view.getVisibility());
    }

    @Test public void systemInsideAndHiddenModesStayHiddenAfterModuleRelease() {
        for (int nativeState : new int[] {View.GONE, View.INVISIBLE}) {
            View view = new View();
            view.setVisibility(nativeState);
            ColorOsPercentIcon icon = new ColorOsPercentIcon(view);
            icon.commit(true);
            assertEquals(View.GONE, view.getVisibility());
            icon.commit(false);
            assertEquals(nativeState, view.getVisibility());
        }
    }

    @Test public void liveNativeModeChangesAreRememberedWhileDuplicateStaysHidden() {
        View view = new View();
        ColorOsPercentIcon icon = new ColorOsPercentIcon(view);
        icon.commit(true);
        for (int nativeState : new int[] {View.GONE, View.VISIBLE, View.GONE, View.VISIBLE}) {
            view.setVisibility(nativeState); // Exact OEM helper has applied its model.
            icon.onNativeUpdate();
            assertEquals(nativeState, icon.nativeVisibility());
            assertEquals(View.GONE, view.getVisibility());
            icon.commit(true);
        }
        icon.commit(false);
        assertEquals(View.VISIBLE, view.getVisibility());
        view.setVisibility(View.GONE);
        icon.onNativeUpdate();
        icon.commit(true);
        icon.restore();
        assertEquals(View.GONE, view.getVisibility());
    }

    @Test public void hostsAreIsolatedAndForeignChangesAreNotOverwrittenOnRelease() {
        View one = new View(), two = new View();
        ColorOsPercentIcon first = new ColorOsPercentIcon(one), second = new ColorOsPercentIcon(two);
        assertFalse(first.isFor(two));
        first.commit(true);
        assertEquals(View.VISIBLE, two.getVisibility());
        second.commit(true);
        first.restore();
        assertEquals(View.VISIBLE, one.getVisibility());
        assertEquals(View.GONE, two.getVisibility());
        two.setVisibility(View.INVISIBLE);
        second.restore();
        assertEquals(View.INVISIBLE, two.getVisibility());
    }
}
