/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

import android.content.SharedPreferences;
import android.graphics.Canvas;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.json.JSONException;
import org.junit.Test;
import static org.junit.Assert.*;

/** Real configuration/renderer code on desktop Android shims; not an OEM/device test. */
public final class LayoutRegressionTest {
    @Test public void chargingScenesRemainIndependentAcrossStylesAndTransport() throws Exception {
        for (int style : new int[]{Prefs.STYLE_RING, Prefs.STYLE_CAPSULE}) {
            TrioSettings base = TrioSettings.defaults();
            base.trioStyle = style;
            base.ringZones = Prefs.decodeLayout("5,1|3|7|6");
            TrioSettings charging = base.copy();
            charging.ringZones = Prefs.decodeLayout("1|3|7|6");
            TrioSettings quick = base.copy();
            quick.ringZones = Prefs.decodeLayout("1|4|7|6");
            base.chargingLayout = ChargeLayout.encode(charging);
            base.quickChargingLayout = ChargeLayout.encode(quick);
            ConfigPacket decoded = ConfigPacket.decode(ConfigPacket.encode(UUID.randomUUID().toString(), 1, base));
            TrioSettings restored = decoded.settings;
            assertArrayEquals(new int[]{3}, ChargeLayout.forState(restored, true, false).ringZones[1]);
            assertArrayEquals(new int[]{4}, ChargeLayout.forState(restored, true, true).ringZones[1]);
            assertArrayEquals(new int[]{3}, ChargeLayout.forState(restored, false, true).ringZones[1]);
            assertEquals(style, restored.trioStyle);
            assertEquals(base, restored);
            TrioSettings copy = base.copy();
            copy.ringZones[0][0] = Prefs.PART_BOLT;
            assertEquals(Prefs.PART_TYPE, base.ringZones[0][0]);
        }
    }

    @Test public void malformedPacketsAndUnsupportedSchemasAreRejected() throws Exception {
        String valid = ConfigPacket.encode(UUID.randomUUID().toString(), 1, TrioSettings.defaults());
        assertThrows(JSONException.class, () -> ConfigPacket.decode(valid.replace("\"schema\":1", "\"schema\":2")));
        assertThrows(JSONException.class, () -> ConfigPacket.decode(valid.replace("\"revision\":1", "\"revision\":0")));
        assertThrows(JSONException.class, () -> ConfigPacket.decode(" ".repeat(ConfigPacket.MAX_LENGTH + 1)));
        assertThrows(JSONException.class, () -> ConfigPacket.decode("{}"));
        assertFalse(RemoteFontKey.valid("../secret"));
    }

    @Test public void firstInstallAndExistingUsersHaveDifferentInitializationRules() {
        MemoryPreferences preferences = new MemoryPreferences();
        assertTrue(FirstRunDefaults.install(preferences));
        assertEquals("5,1|3|7|", preferences.getString(Prefs.KEY_RING_LAYOUT, ""));
        Map<String, ?> captured = preferences.getAll();
        assertFalse(FirstRunDefaults.install(preferences));
        assertEquals(captured, preferences.getAll());
        MemoryPreferences existing = new MemoryPreferences();
        existing.edit().putBoolean(Prefs.KEY_ENABLED, false).commit();
        assertFalse(FirstRunDefaults.install(existing));
        assertEquals(1, existing.getAll().size());
        assertFalse(existing.getBoolean(Prefs.KEY_ENABLED, true));
    }

    @Test public void normalAndChargingFramesDrawAtBothHostShapes() {
        for (int style : new int[]{Prefs.STYLE_RING, Prefs.STYLE_CAPSULE}) {
            for (int level : new int[]{0, 1, 15, 50, 99, 100}) {
                TrioSettings settings = TrioSettings.defaults();
                settings.enabled = true;
                settings.trioStyle = style;
                settings.showWifi = settings.showValue = settings.showMobile = true;
                settings.ringZones = Prefs.decodeLayout("1|3|7|6");
                BufferedImage first = render(settings, level, false);
                BufferedImage second = render(settings, level, false);
                assertArrayEquals(pixels(first), pixels(second));
                assertTrue("glyph must paint", java.util.Arrays.stream(pixels(first)).anyMatch(p -> (p >>> 24) != 0));
                assertTrue(java.util.Arrays.stream(pixels(render(settings, level, true))).anyMatch(p -> (p >>> 24) != 0));
            }
        }
    }

    static BufferedImage render(TrioSettings settings, int level, boolean charging) {
        int width = settings.trioStyle == Prefs.STYLE_CAPSULE ? 222 : 170;
        int height = settings.trioStyle == Prefs.STYLE_CAPSULE ? 130 : 104;
        BufferedImage bitmap = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = bitmap.createGraphics();
        try {
            TrioRenderer.drawInto(new Canvas(graphics), width, height, level, charging, false, false, 3, 4,
                    0xff000000, settings);
        } finally { graphics.dispose(); }
        return bitmap;
    }

    static int[] pixels(BufferedImage image) {
        return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
    }

    static final class MemoryPreferences implements SharedPreferences {
        private final Map<String, Object> values = new HashMap<>();
        public Map<String, ?> getAll() { return new HashMap<>(values); }
        public boolean getBoolean(String key, boolean fallback) { return (boolean) values.getOrDefault(key, fallback); }
        public int getInt(String key, int fallback) { return (int) values.getOrDefault(key, fallback); }
        public String getString(String key, String fallback) { return (String) values.getOrDefault(key, fallback); }
        public boolean contains(String key) { return values.containsKey(key); }
        public Editor edit() {
            return new Editor() {
                private final Map<String, Object> pending = new HashMap<>();
                public Editor putBoolean(String key, boolean value) { pending.put(key, value); return this; }
                public Editor putInt(String key, int value) { pending.put(key, value); return this; }
                public Editor putString(String key, String value) { pending.put(key, value); return this; }
                public boolean commit() { values.putAll(pending); return true; }
            };
        }
    }
}
