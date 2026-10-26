/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

import org.junit.Test;
import static org.junit.Assert.*;

/** Grouped geometry remains independent across snapshots and per-key remote deliveries. */
public final class CapsuleLayoutSettingsTest {
    @Test public void missingGeometryUsesTheSameDefaultsAcrossBothReaders() {
        assertEquals(CapsuleLayoutSettings.DEFAULT,
                TrioSettings.from(new PreferencesSnapshot(java.util.Map.of())).capsuleLayout);
        assertEquals(CapsuleLayoutSettings.DEFAULT,
                TrioSettings.fromBundle(new android.os.Bundle()).capsuleLayout);
    }

    @Test public void editingACopyCannotChangeTheOriginalSnapshotOrItsWireValues() {
        TrioSettings original = TrioSettings.defaults();
        original.capsuleLayout = new CapsuleLayoutSettings(1, 2, 3, 4, 5);
        var wire = original.toBundle();
        TrioSettings edited = original.copy();
        edited.capsuleLayout = edited.capsuleLayout.withLeftDx(9).withLength(12);
        assertEquals(new CapsuleLayoutSettings(1, 2, 3, 4, 5), original.capsuleLayout);
        assertEquals(new CapsuleLayoutSettings(9, 2, 3, 4, 12), edited.capsuleLayout);
        assertNotEquals(original, edited);
        assertTrue(wire.containsKey(Prefs.KEY_CAPSULE_LEFT_DX));
        assertTrue(wire.containsKey(Prefs.KEY_CAPSULE_LENGTH));
        assertEquals(wire.getInt(Prefs.KEY_CAPSULE_LEFT_DX, 0), original.toBundle().getInt(Prefs.KEY_CAPSULE_LEFT_DX, 0));
        assertEquals(wire.getInt(Prefs.KEY_CAPSULE_LENGTH, 0), original.toBundle().getInt(Prefs.KEY_CAPSULE_LENGTH, 0));
    }

    @Test public void partialDeliveriesCannotOverwriteOtherCapsuleGeometry() {
        String[] keys = {Prefs.KEY_CAPSULE_LEFT_DX, Prefs.KEY_CAPSULE_LEFT_DY,
                Prefs.KEY_CAPSULE_RIGHT_DX, Prefs.KEY_CAPSULE_RIGHT_DY, Prefs.KEY_CAPSULE_LENGTH};
        TrioSettings source = TrioSettings.defaults();
        source.capsuleLayout = new CapsuleLayoutSettings(11, 12, 13, 14, 15);
        for (int field = 0; field < keys.length; field++) {
            TrioSettings target = TrioSettings.defaults();
            target.capsuleLayout = new CapsuleLayoutSettings(1, 2, 3, 4, 5);
            int[] expected = {1, 2, 3, 4, 5}; expected[field] += 10;
            assertTrue(target.applyKeyFrom(source, keys[field]));
            assertEquals(keys[field], new CapsuleLayoutSettings(expected[0], expected[1], expected[2], expected[3], expected[4]),
                    target.capsuleLayout);
            assertEquals(target, target.copy());
            assertEquals(target.hashCode(), target.copy().hashCode());
        }
    }
}
