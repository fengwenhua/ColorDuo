/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

import java.util.Map;

/** Fixed fields only: no arbitrary metadata, exception messages, network names or logs. */
final class CompatibilitySnapshot {
    enum Capability { BATTERY_SYMBOLS, NETWORK_SYMBOLS, POWER_SYMBOLS, CLOCK_SYMBOLS,
        BATTERY_HOOK, NETWORK_HOOK, TYPE_HOOK, CLOCK_HOOK, POWER_LISTENER }
    record Status(boolean available, String errorType) {
        Status { errorType = errorType != null && errorType.matches("[A-Za-z][A-Za-z0-9_$.]{0,79}") ? errorType : "Unknown"; }
    }
    record Device(String maker, String model, String android, int api, String firmware,
                  String systemui, String module, String rom, boolean live) {}

    static String render(Device device, Map<Capability, Status> capabilities) {
        StringBuilder out = new StringBuilder("ColorDuo compatibility report v1\n");
        field(out, "device.manufacturer", device.maker);
        field(out, "device.model", device.model);
        field(out, "android.release", device.android);
        field(out, "android.api", Integer.toString(device.api));
        field(out, "firmware.build", device.firmware);
        field(out, "systemui.version", device.systemui);
        field(out, "module.version", device.module);
        field(out, "adapter.rom", device.rom);
        field(out, "runtime.report", Boolean.toString(device.live));
        field(out, "compatibility", grade(device, capabilities));
        for (Capability key : Capability.values()) {
            Status state = capabilities.get(key);
            String prefix = "capability." + key.name().toLowerCase(java.util.Locale.ROOT);
            field(out, prefix, state == null ? "unknown" : Boolean.toString(state.available));
            if (state != null && !state.available) field(out, prefix + ".errorType", state.errorType);
        }
        return out.toString();
    }
    static String grade(Device device, Map<Capability, Status> capabilities) {
        if (!device.live) return "no-runtime-report";
        if (!"COLOR_OS".equals(device.rom)) return "unverified-device";
        for (Capability key : new Capability[]{Capability.BATTERY_SYMBOLS, Capability.NETWORK_SYMBOLS,
                Capability.POWER_SYMBOLS, Capability.BATTERY_HOOK, Capability.NETWORK_HOOK}) {
            Status state = capabilities.get(key);
            if (state == null || !state.available) return "missing-capabilities-native-fallback";
        }
        if ("PKH110".equals(device.model) && device.systemui != null && device.systemui.startsWith("16.99.12 "))
            return "matches-tested-Find-N5-profile";
        return "expected-profile-device-unverified";
    }
    private static void field(StringBuilder out, String key, String value) {
        String clean = value == null ? "unknown" : value.replaceAll("[\\r\\n\\t\\p{Cntrl}]", " ").trim();
        if (clean.length() > 128) clean = clean.substring(0, 128);
        out.append(key).append('=').append(clean).append('\n');
    }
    private CompatibilitySnapshot() {}
}
