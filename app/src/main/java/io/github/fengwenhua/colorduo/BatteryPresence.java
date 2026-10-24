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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

/**
 * Event-driven battery state for the status-bar performance readouts: the
 * charging power and the battery temperature.
 *
 * <p>Why the framework and not the filesystem: this module runs inside SystemUI
 * as {@code android.uid.systemui}, and every kernel-side route is closed to it.
 * {@code /sys/class/power_supply/battery/} refuses even a rooted shell, the
 * health HAL answers {@code FAILED_TRANSACTION} to {@code dumpsys
 * android.hardware.health.IHealth/default}, and {@code dumpsys battery} is a
 * shell tool rather than an API. What is left is the pair the platform itself
 * uses: the sticky {@code ACTION_BATTERY_CHANGED} broadcast for the temperature
 * and the voltage, and {@link BatteryManager} for the current.
 *
 * <p>Copied in shape from {@link WifiPresence}, for the same reasons: the
 * broadcast is sticky and cheap to re-read, a bounded low-frequency watchdog
 * recovers from a delivery the ROM dropped, and nothing here is ever called from
 * the per-frame drawing path.
 */
final class BatteryPresence {

    private static boolean registered;
    private static boolean watchdogStarted;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static Runnable pendingRefresh;

    private BatteryPresence() {}

    static synchronized void start(final Context context) {
        if (!watchdogStarted) {
            watchdogStarted = true;
            // ROM broadcast delivery is not always reliable, and the temperature
            // and the current both drift while the panel is idle. A bounded
            // low-frequency recheck also recovers from transient query failures;
            // never put framework IPC in the per-frame drawing path.
            MAIN.post(new Runnable() {
                @Override public void run() {
                    refresh(context);
                    MAIN.postDelayed(this, 3000L);
                }
            });
        }
        if (registered) return;
        final IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        final BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ignored, Intent intent) {
                // Re-read the sticky intent rather than trust this delivery's
                // extras: the two must not be able to disagree.
                refresh(context);
                scheduleRefresh(context);
            }
        };
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                // ACTION_BATTERY_CHANGED is a protected broadcast, so only the
                // system can send it; the flag is required for every receiver
                // registered on a modern SDK and does not gate this delivery.
                // Nothing is taken from the extras of a received broadcast, so a
                // forged one could not move the reading either.
                context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
            } else {
                context.registerReceiver(receiver, filter);
            }
            registered = true;
            refresh(context);
        } catch (RuntimeException ignored) {
            // A ROM that refuses the registration leaves the readouts on their
            // "unknown" state rather than crashing SystemUI.
        }
    }

    // A broadcast can arrive before the charge counter and the current have both
    // settled, so the frame after it is re-read once. The same coalescer serves
    // a burst of deliveries.
    private static void scheduleRefresh(final Context context) {
        MAIN.post(new Runnable() {
            @Override public void run() {
                refresh(context);
                if (pendingRefresh != null) MAIN.removeCallbacks(pendingRefresh);
                pendingRefresh = new Runnable() {
                    @Override public void run() { refresh(context); }
                };
                MAIN.postDelayed(pendingRefresh, 1000L);
            }
        });
    }

    private static void refresh(Context context) {
        try {
            final Intent battery = context.registerReceiver(null,
                    new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (battery == null) {
                return;
            }
            final int plugged = battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
            final int status = battery.getIntExtra(BatteryManager.EXTRA_STATUS,
                    BatteryManager.BATTERY_STATUS_UNKNOWN);
            final int temperature = battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE,
                    Integer.MIN_VALUE);
            final int voltage = battery.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0);
            // "An external source is attached" is deliberately wider than
            // "status == charging": once the battery is full the platform flips
            // the status to FULL and stops pushing current, and a readout that
            // vanished at 100% would be at its most confusing exactly where a
            // user looks for it.
            final boolean attached = plugged != 0
                    || status == BatteryManager.BATTERY_STATUS_CHARGING
                    || status == BatteryManager.BATTERY_STATUS_FULL;
            final int whole = (temperature == Integer.MIN_VALUE)
                    ? TrioState.TEMP_UNKNOWN
                    : Math.round(temperature / 10f);
            final int current = currentNow(context);
            final float watts = wattsOf(current, voltage);
            publish(attached, watts, whole);
        } catch (RuntimeException ignored) {
            // Permissions/ROM differences must not crash SystemUI.
        }
    }

    /**
     * The battery current in microamperes, or {@link Integer#MIN_VALUE} when the
     * platform will not answer.
     */
    private static int currentNow(Context context) {
        try {
            final BatteryManager manager = context.getSystemService(BatteryManager.class);
            if (manager == null) {
                return Integer.MIN_VALUE;
            }
            return manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
        } catch (RuntimeException ignored) {
            return Integer.MIN_VALUE;
        }
    }

    /**
     * The power going into the battery, in watts, from its own current and its
     * own voltage.
     *
     * <p>Deliberately the battery's side of the circuit rather than the
     * charger's negotiated limit: the negotiated figure is fixed for the whole
     * session and would sit there unchanged while the real power tapered away,
     * which is the opposite of what a performance readout is for.
     *
     * <p>The sign of {@code current_now} is not portable, so only its magnitude
     * is used. This platform answers with a <em>negative</em> current while the
     * battery is charging - measured at -204000 uA against 4008 mV with USB
     * powered, status charging and the charge counter rising - while other
     * platforms answer positive for the same situation. Reading the sign as
     * "discharging" therefore turned every real charging current into 0 and the
     * status bar sat on "0.0W" until a sampling glitch happened to land on a
     * positive value. The magnitude is the current actually flowing either way,
     * and the caller only draws it while something is attached.
     *
     * <p>Not being able to ask is a different answer, and it is
     * {@link TrioState#POWER_UNKNOWN} rather than 0. {@code getIntProperty}
     * answers {@code Integer.MIN_VALUE} when the platform does not implement the
     * property, and the voltage is absent on a device that does not report it;
     * folding either into 0 would put a "0.0W" next to the temperature that
     * claims a reading the platform never gave. A real zero stays a real zero:
     * no current flowing is 0 W and is drawn as such.
     */
    private static float wattsOf(int currentMicroAmps, int voltageMilliVolts) {
        if (currentMicroAmps == Integer.MIN_VALUE || voltageMilliVolts <= 0) {
            return TrioState.POWER_UNKNOWN;
        }
        return Math.abs(currentMicroAmps) * (voltageMilliVolts / 1000f) / 1000000f;
    }

    private static void publish(boolean attached, float watts, int temperature) {
        final boolean beforeAttached = TrioState.sChargingAttached;
        final float beforeWatts = TrioState.sChargePowerW;
        final int beforeTemperature = TrioState.sBatteryTempC;
        TrioState.sChargingAttached = attached;
        TrioState.sChargePowerW = watts;
        TrioState.sBatteryTempC = temperature;
        // Float.compare rather than != because the watts may be POWER_UNKNOWN,
        // and NaN != NaN is true: a plain comparison would call every frame a
        // change on a device that cannot report power, and repaint the status
        // bar once every few seconds for nothing. Float.compare orders NaN
        // against itself as equal, which is what "did the reading move" means.
        if (beforeAttached != attached
                || Float.compare(beforeWatts, TrioState.sChargePowerW) != 0
                || beforeTemperature != TrioState.sBatteryTempC) {
            TrioHooks.invalidateHosts();
        }
    }
}
