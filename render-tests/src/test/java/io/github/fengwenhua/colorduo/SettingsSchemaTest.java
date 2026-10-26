/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

import android.os.Bundle;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

/** The registry enumerates the matrix; adding a field without transport/copy parity fails CI. */
public final class SettingsSchemaTest {
    @Test public void everyPersistedModelFieldHasExactlyOneDeclaration() {
        Set<String> model = new HashSet<>();
        for (var field : TrioSettings.class.getFields())
            if (!Modifier.isStatic(field.getModifiers()) && !field.getName().equals("editingChargeScene")) {
                if (field.getType() == CapsuleLayoutSettings.class) {
                    for (var component : CapsuleLayoutSettings.class.getRecordComponents()) {
                        String name = component.getName();
                        assertTrue(model.add("capsule" + Character.toUpperCase(name.charAt(0)) + name.substring(1)));
                    }
                } else assertTrue(model.add(field.getName()));
            }
        Set<String> registered = new HashSet<>();
        for (SettingsSchema.Entry entry : SettingsSchema.ENTRIES) {
            assertTrue("duplicate " + entry.field(), registered.add(entry.field()));
            assertNotNull(entry.defaultValue()); assertNotNull(entry.group());
            assertEquals(entry.styled(), Prefs.STYLED_KEYS.contains(entry.key()));
            assertSame(entry, SettingsSchema.find(entry.wireKey(Prefs.STYLE_RING)));
            assertSame(entry, SettingsSchema.find(entry.wireKey(Prefs.STYLE_CAPSULE)));
        }
        assertEquals(model, registered);
    }

    @Test public void eachFieldSurvivesPreferencesBundleJsonCopyAndPartialUpdate() throws Exception {
        for (int style : new int[]{Prefs.STYLE_RING, Prefs.STYLE_CAPSULE}) {
            TrioSettings baseline = TrioSettings.defaults(); baseline.trioStyle = style;
            for (SettingsSchema.Entry entry : SettingsSchema.ENTRIES) {
                Bundle edited = baseline.toBundle();
                Object variant = different(entry, style);
                SettingsSchema.put(edited, entry.wireKey(style), variant);
                TrioSettings changed = TrioSettings.fromBundle(edited);
                assertNotEquals(entry.field(), baseline, changed);
                assertEquals(entry.field(), changed, changed.copy());
                assertEquals(changed.hashCode(), changed.copy().hashCode());
                Map<String, Object> values = new HashMap<>();
                Bundle canonical = changed.toBundle();
                for (String key : canonical.keySet()) values.put(key, canonical.get(key));
                assertEquals(entry.field(), changed, TrioSettings.from(new PreferencesSnapshot(values)));
                assertEquals(entry.field(), changed, ConfigPacket.decode(ConfigPacket.encode(UUID.randomUUID().toString(), 1, changed)).settings);
                TrioSettings partial = baseline.copy();
                assertTrue(entry.field(), partial.applyKeyFrom(changed, entry.wireKey(style)));
                assertEquals(entry.field(), changed, partial);
            }
        }
    }

    @Test public void numericBoundsAgreeWithTheHistoricalReader() {
        for (int style : new int[]{Prefs.STYLE_RING, Prefs.STYLE_CAPSULE}) {
            for (SettingsSchema.Entry entry : SettingsSchema.ENTRIES) {
                if (entry.kind() != SettingsSchema.Kind.INT) continue;
                for (int invalid : new int[]{Integer.MIN_VALUE, Integer.MAX_VALUE}) {
                    TrioSettings base = TrioSettings.defaults(); base.trioStyle = style;
                    Bundle values = base.toBundle();
                    values.putInt(entry.wireKey(style), invalid);
                    TrioSettings read = TrioSettings.fromBundle(values);
                    assertEquals(entry.field(), entry.normalize(invalid, style), entry.read().apply(read));
                }
            }
        }
    }

    @Test public void wrongKnownTypeFailsAndUnknownScalarIsIgnored() throws Exception {
        String raw = ConfigPacket.encode(UUID.randomUUID().toString(), 1, TrioSettings.defaults());
        JSONObject packet = new JSONObject(raw);
        packet.getJSONObject("payload").put(Prefs.KEY_RING_STROKE, "wrong-type");
        assertThrows(JSONException.class, () -> ConfigPacket.decode(packet.toString()));
        JSONObject future = new JSONObject(raw);
        future.getJSONObject("payload").put("future_setting", 123);
        assertEquals(TrioSettings.defaults(), ConfigPacket.decode(future.toString()).settings);
        assertNull(SettingsSchema.find("future_setting"));
        assertThrows(JSONException.class, () -> ConfigPacket.decode(new JSONObject(raw).put("schema", 0).toString()));
    }

    @Test public void oldClockFallbackAndStyleInheritanceRemainDefined() {
        Map<String, Object> legacy = Map.of(Prefs.KEY_VALUE_SIZE, 80, Prefs.KEY_VALUE_WEIGHT, 700,
                Prefs.KEY_TRIO_STYLE, Prefs.STYLE_CAPSULE, Prefs.KEY_SHOW_WIFI, false,
                Prefs.KEY_SHOW_MOBILE_TYPE, true);
        TrioSettings read = TrioSettings.from(new PreferencesSnapshot(legacy));
        assertEquals(read.valueSize, read.clockSize); assertEquals(700, read.clockWeight);
        assertFalse(read.showWifi); assertEquals(Prefs.MOBILE_TYPE_IN_RING, read.mobileTypeMode);
    }

    private static Object different(SettingsSchema.Entry entry, int style) {
        Object original = entry.defaultValue();
        if (entry.kind() == SettingsSchema.Kind.BOOLEAN) return !(Boolean) original;
        if (entry.kind() == SettingsSchema.Kind.INT) {
            if (entry.key().equals(Prefs.KEY_TRIO_STYLE)) return style == Prefs.STYLE_RING ? Prefs.STYLE_CAPSULE : Prefs.STYLE_RING;
            int value = (Integer) original;
            for (int candidate : new int[]{value + 1, value - 1, 0, 1, 2, entry.minimum(), entry.maximum().applyAsInt(style)}) {
                Object normalized = entry.normalize(candidate, style);
                if (!normalized.equals(original)) return normalized;
            }
            throw new AssertionError("no alternate value: " + entry.field());
        }
        if (entry.field().equals("ringZones")) return "1|3|7|6";
        if (entry.field().equals("outOrder")) return "3,2,1";
        if (entry.field().equals("fontPath")) return "/system/fonts/Roboto-Regular.ttf";
        TrioSettings charging = TrioSettings.defaults();
        charging.ringZones = Prefs.decodeLayout("1|4|7|6");
        charging.showWifi = false;
        return ChargeLayout.encode(charging);
    }
}
