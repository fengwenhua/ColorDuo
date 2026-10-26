/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

import android.os.Bundle;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

/** Component grouping must preserve old storage and isolate style/charging edits. */
public final class ContentSettingsTest {
    @Test public void snapshotsShareImmutableContentUntilEdited() {
        TrioSettings original = TrioSettings.defaults();
        TrioSettings edited = original.copy();
        assertSame(original.content, edited.content);
        edited.setShowWifi(!original.content.showWifi());
        assertNotSame(original.content, edited.content);
        assertEquals(ContentSettings.DEFAULT, original.content);
        assertNotEquals(original, edited);
        assertSame(edited.content, edited.content.withShowWifi(edited.content.showWifi()));
        assertEquals(ContentSettings.EMPTY, new TrioSettings().content);
        java.util.Set<String> model = new java.util.HashSet<>();
        for (var component : ContentSettings.class.getRecordComponents()) model.add(component.getName());
        java.util.Set<String> registry = new java.util.HashSet<>();
        for (var entry : SettingsSchema.ENTRIES)
            if (entry.group() == SettingsSchema.Group.CONTENT) registry.add(entry.field());
        assertEquals(model, registry);
    }

    @Test public void explicitCapsuleDefaultOverridesDifferentRingValue() {
        for (SettingsSchema.Entry entry : SettingsSchema.ENTRIES) {
            if (entry.group() != SettingsSchema.Group.CONTENT || !entry.styled()) continue;
            Map<String, Object> values = new HashMap<>();
            Object own = entry.defaultValue();
            Object ring = own instanceof Boolean ? !(Boolean) own
                    : ((Integer) own == entry.minimum() ? entry.maximum().applyAsInt(Prefs.STYLE_RING) : entry.minimum());
            values.put(Prefs.KEY_TRIO_STYLE, Prefs.STYLE_CAPSULE);
            values.put(entry.key(), ring);
            TrioSettings inherited = TrioSettings.from(new PreferencesSnapshot(values));
            assertEquals(entry.field(), ring, entry.read().apply(inherited));
            values.put(entry.wireKey(Prefs.STYLE_CAPSULE), own);
            TrioSettings explicit = TrioSettings.from(new PreferencesSnapshot(values));
            assertEquals(entry.field(), own, entry.read().apply(explicit));
            Bundle bundle = new Bundle();
            for (var value : values.entrySet()) SettingsSchema.put(bundle, value.getKey(), value.getValue());
            assertEquals(entry.field(), explicit, TrioSettings.fromBundle(bundle));
        }
    }

    @Test public void legacyTypeAndRetiredMeterKeepTheirHistoricalReadRules() {
        for (boolean legacy : new boolean[]{false, true}) {
            Map<String, Object> values = new HashMap<>();
            values.put(Prefs.KEY_TRIO_STYLE, Prefs.STYLE_CAPSULE);
            values.put(Prefs.KEY_SHOW_MOBILE_TYPE, legacy);
            int wanted = legacy ? Prefs.MOBILE_TYPE_IN_RING : Prefs.MOBILE_TYPE_OFF;
            assertEquals(wanted, TrioSettings.from(new PreferencesSnapshot(values)).content.mobileTypeMode());
            values.put(Prefs.KEY_MOBILE_TYPE_MODE, 9999);
            assertEquals(Prefs.MAX_MOBILE_TYPE_MODE,
                    TrioSettings.from(new PreferencesSnapshot(values)).content.mobileTypeMode());
            values.put(Prefs.styledKey(Prefs.KEY_MOBILE_TYPE_MODE, Prefs.STYLE_CAPSULE), -9999);
            assertEquals(Prefs.MIN_MOBILE_TYPE_MODE,
                    TrioSettings.from(new PreferencesSnapshot(values)).content.mobileTypeMode());
        }
        // Only preference loading historically enables SIM 1 for a retired aggregate meter.
        Map<String, Object> retired = new HashMap<>();
        retired.put(Prefs.KEY_RING_LAYOUT, Integer.toString(Prefs.PART_MOBILE));
        retired.put(Prefs.KEY_SHOW_SIM_1, false);
        assertTrue(TrioSettings.from(new PreferencesSnapshot(retired)).content.showSim1());
        Bundle bundle = new Bundle();
        for (var value : retired.entrySet()) SettingsSchema.put(bundle, value.getKey(), value.getValue());
        assertFalse(TrioSettings.fromBundle(bundle).content.showSim1());
    }

    @Test public void partialUpdatesKeepAllOtherContentFields() {
        for (int style : new int[]{Prefs.STYLE_RING, Prefs.STYLE_CAPSULE}) {
            TrioSettings base = TrioSettings.defaults(); base.trioStyle = style;
            Bundle allChanged = base.toBundle();
            for (var entry : SettingsSchema.ENTRIES) {
                if (entry.group() != SettingsSchema.Group.CONTENT) continue;
                Object old = entry.read().apply(base);
                Object value = old instanceof Boolean ? !(Boolean) old
                        : ((Integer) old == entry.minimum() ? entry.maximum().applyAsInt(style) : entry.minimum());
                SettingsSchema.put(allChanged, entry.wireKey(style), value);
            }
            TrioSettings source = TrioSettings.fromBundle(allChanged);
            ContentSettings sourceContent = source.content;
            for (var selected : SettingsSchema.ENTRIES) {
                if (selected.group() != SettingsSchema.Group.CONTENT) continue;
                TrioSettings result = base.copy();
                assertTrue(result.applyKeyFrom(source, selected.wireKey(style)));
                for (var sibling : SettingsSchema.ENTRIES) {
                    if (sibling.group() != SettingsSchema.Group.CONTENT) continue;
                    assertEquals(selected.field() + " changed " + sibling.field(),
                            sibling.read().apply(sibling == selected ? source : base), sibling.read().apply(result));
                }
                assertSame(sourceContent, source.content);
                assertEquals(ContentSettings.DEFAULT, base.content);
            }
        }
    }

    @Test public void stylePreviewCopiesStyledComponentsAndKeepsGlobalFlags() {
        TrioSettings base = TrioSettings.defaults();
        TrioSettings other = base.copy();
        other.setShowWifi(!base.content.showWifi());
        other.setTypeHideOnWifi(!base.content.typeHideOnWifi());
        other.setChargingShowsValue(!base.content.chargingShowsValue());
        other.setPerfPower(!base.content.perfPower());
        other.setPerfTemp(!base.content.perfTemp());
        other.setPerfTempChargingOnly(!base.content.perfTempChargingOnly());
        TrioSettings preview = base.withComponentsFrom(other, Prefs.STYLE_CAPSULE);
        for (var entry : SettingsSchema.ENTRIES) {
            if (entry.group() != SettingsSchema.Group.CONTENT) continue;
            assertEquals(entry.field(), entry.read().apply(entry.styled() ? other : base), entry.read().apply(preview));
        }
        assertEquals(ContentSettings.DEFAULT, base.content);
    }

    @Test public void everyChargingFlagCombinationKeepsEncodingAndGlobalReadouts() {
        TrioSettings base = TrioSettings.defaults();
        base.setTypeHideOnWifi(true); base.setPerfPower(true);
        base.setPerfTemp(true); base.setPerfTempChargingOnly(true);
        for (int flags = 0; flags < 1024; flags++) {
            String encoded = "1;" + flags + ";" + Prefs.MOBILE_TYPE_IN_RING + ";" + Prefs.MIN_SIGNAL_MODE + ";1|3|7|6|";
            base.chargingLayout = encoded;
            TrioSettings decoded = ChargeLayout.forScene(base, ChargeLayout.CHARGING);
            assertNotNull(decoded);
            assertEquals(ChargeLayout.normalize(encoded), ChargeLayout.encode(decoded));
            assertTrue(decoded.content.typeHideOnWifi());
            assertTrue(decoded.content.perfPower());
            assertTrue(decoded.content.perfTemp());
            assertTrue(decoded.content.perfTempChargingOnly());
        }
        assertEquals(ContentSettings.DEFAULT.showWifi(), base.content.showWifi());
    }
}
