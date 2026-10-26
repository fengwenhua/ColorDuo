/*
 * Copyright (C) 2026 yixing233 and HyperDuo contributors
 *
 * This file is part of ColorDuo, released under the GNU General Public
 * License v3.0. See the LICENSE file at the repository root, or
 * <https://www.gnu.org/licenses/gpl-3.0.html>.
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package io.github.fengwenhua.colorduo;

/** Pure HyperOS readout conversion/formatting; native sampling and View sizing remain with their owners. */
final class HyperOsReadout {
    private HyperOsReadout() {}

    static float wattsOf(int currentMicroAmps, int voltageMilliVolts) {
        if (currentMicroAmps == Integer.MIN_VALUE || voltageMilliVolts <= 0) {
            return Float.NaN;
        }
        return Math.abs(currentMicroAmps) * (voltageMilliVolts / 1000f) / 1000000f;
    }

    static String text(boolean showPower, boolean showTemperature, boolean temperatureChargingOnly,
                       boolean attached, float watts, int temperature, int unknownTemperature, char separator) {
        if (!showPower && !showTemperature) {
            return "";
        }
        final StringBuilder text = new StringBuilder();
        // Both halves of the question, because they say different things: a
        // source being attached is not the same as the platform being able to
        // say how much power is going in. Attached with no reading - the shape
        // this had before the sentinel existed - renders a confident "0.0W"
        // that is not a reading at all.
        //
        // isNaN and not != POWER_UNKNOWN: the sentinel is a NaN, so != answers
        // true for it and the gate would let the very value it exists to
        // exclude through. Math.round then turns that NaN into 0 and the row
        // reads "0.0W" - the exact claim this test is here to prevent.
        if (showPower && attached
                && !Float.isNaN(watts)) {
            text.append(powerText(watts));
        }
        // The charging-only switch is a filter on the temperature, not a second reading:
        // with it on, both halves of the strip appear and disappear together, which is the
        // block a user watching a charge asked for (issue 45). Off - the default and the
        // behaviour this shipped with - the temperature stands on its own, because it is a
        // property of the battery rather than of the charger.
        final boolean tempWanted = showTemperature
                && temperature != unknownTemperature
                && (!temperatureChargingOnly || attached);
        if (tempWanted) {
            if (text.length() > 0) {
                // One reading per line: the pair stacks instead of running on,
                // so the view never grows a second reading wide. With only the
                // temperature on there is nothing to break from and the view
                // stays one line tall.
                text.append(separator);
            }
            // Written as a unicode escape so this file stays ASCII: the degree
            // sign is the one character in the readout that no keyboard on the
            // build machine is guaranteed to produce.
            text.append(temperature).append("\u00B0C");
        }
        return text.toString();
    }

    static int lines(String text, char separator) {
        int lines = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == separator) {
                lines++;
            }
        }
        return lines;
    }

    static String powerText(float watts) {
        final int tenths = Math.round(watts * 10f);
        if (tenths >= 100) {
            return (tenths / 10) + "W";
        }
        return (tenths / 10) + "." + (tenths % 10) + "W";
    }
}
