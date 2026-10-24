/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

/** Recognize the OEM style-2 charging popup without overriding ordinary hide requests. */
final class ColorOsPlugBridge {
    private static final long START_WINDOW_MS = 5_000;
    private boolean sampled, plugged, active;
    private long armedAt = Long.MIN_VALUE;

    void power(boolean nextPlugged, long now) {
        if (sampled && !plugged && nextPlugged) armedAt = now;
        if (!nextPlugged) armedAt = Long.MIN_VALUE;
        sampled = true;
        plugged = nextPlugged;
    }

    boolean style(int style, boolean eligible, long now) {
        if (style != 2) { active = false; return false; }
        if (!eligible) { clear(); return false; }
        if (active) return true; // The popup ends when the OEM restores its normal style.
        if (armedAt != Long.MIN_VALUE && now >= armedAt && now - armedAt <= START_WINDOW_MS) {
            active = true;
            armedAt = Long.MIN_VALUE;
        }
        return active;
    }

    boolean active() { return active; }
    void clear() { active = false; armedAt = Long.MIN_VALUE; }
}
