/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;

/** Battery-side estimates sampled off-thread only while requested and the display is interactive. */
final class ColorOsReadoutSource {
    static final class State {
        static final State UNKNOWN = new State(false, Float.NaN, Integer.MIN_VALUE);
        final boolean charging;
        final float watts;
        final int temperature;
        State(boolean charging, float watts, int temperature) { this.charging = charging; this.watts = watts; this.temperature = temperature; }
        boolean same(State other) {
            return other != null && charging == other.charging && Float.compare(watts, other.watts) == 0 && temperature == other.temperature;
        }
        String text(TrioSettings settings) {
            boolean powerReady = charging && Float.isFinite(watts) && watts >= 0;
            boolean temperatureReady = temperature != Integer.MIN_VALUE;
            // With both charging-only readouts enabled, publish the pair atomically.
            if (settings.content.perfPower() && settings.content.perfTemp() && settings.content.perfTempChargingOnly()
                    && (!powerReady || !temperatureReady)) return "";
            String power = "";
            if (settings.content.perfPower() && powerReady) {
                int tenths = Math.round(watts * 10f);
                power = tenths >= 100 ? tenths / 10 + "W" : tenths / 10 + "." + tenths % 10 + "W";
            }
            String temp = settings.content.perfTemp() && temperatureReady && (!settings.content.perfTempChargingOnly() || charging)
                    ? temperature + "\u00b0C" : "";
            return power.isEmpty() ? temp : temp.isEmpty() ? power : power + "\n" + temp;
        }
    }

    private final Handler main = new Handler(Looper.getMainLooper());
    private final LatestWorker worker = new LatestWorker("ColorDuo-battery-readouts");
    private final Runnable changed;
    private Context context;
    private BroadcastReceiver receiver;
    private boolean interactive;
    private volatile boolean listening;
    private long generation;
    private long sampleSequence;
    private volatile State state = State.UNKNOWN;
    private final Runnable sample = this::requestSample;

    ColorOsReadoutSource(Runnable changed) { this.changed = changed; }
    State get() { return state; }
    boolean active() { return listening; }

    void setActive(Context next, boolean enabled) {
        if (enabled && context != null) return;
        if (!enabled) { stop(); return; }
        if (next == null) return;
        context = next.getApplicationContext();
        if (context == null) { context = null; return; }
        long token = ++generation;
        PowerManager power = context.getSystemService(PowerManager.class);
        interactive = power != null && power.isInteractive();
        receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context ignored, Intent intent) {
                if (generation != token || context == null) return;
                if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) interactive = false;
                else if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) interactive = true;
                main.removeCallbacks(sample);
                if (interactive) requestSample();
            }
        };
        try {
            IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            filter.addAction(Intent.ACTION_SCREEN_ON); filter.addAction(Intent.ACTION_SCREEN_OFF);
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
            listening = true;
            if (interactive) requestSample();
        } catch (RuntimeException failure) { stop(); }
    }

    private void stop() {
        if (context == null) return;
        generation++; main.removeCallbacks(sample);
        try { if (receiver != null) context.unregisterReceiver(receiver); } catch (RuntimeException ignored) {}
        receiver = null; context = null; interactive = false; listening = false; state = State.UNKNOWN;
    }

    private void requestSample() {
        main.removeCallbacks(sample);
        if (context == null || !interactive) return;
        Context app = context;
        long token = generation;
        long sequence = ++sampleSequence;
        worker.submit(() -> {
            State result = State.UNKNOWN;
            try {
                Intent battery = app.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                BatteryManager manager = app.getSystemService(BatteryManager.class);
                if (battery != null) {
                    int temperature = battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE);
                    int voltage = battery.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0);
                    boolean charging = ColorOsReadout.charging(
                            battery.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN),
                            battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0));
                    // A full battery may retain its last nonzero current. Do not report it as charging power.
                    int current = !charging || manager == null ? Integer.MIN_VALUE
                            : manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
                    float watts = ColorOsReadout.wattsOf(current, voltage);
                    result = new State(charging, watts, temperature == Integer.MIN_VALUE ? Integer.MIN_VALUE : Math.round(temperature / 10f));
                }
            } catch (RuntimeException ignored) {}
            State ready = result;
            main.post(() -> {
                if (generation != token || sampleSequence != sequence || context == null || !interactive) return;
                boolean different = !state.same(ready); state = ready;
                if (different) changed.run();
                main.removeCallbacks(sample); main.postDelayed(sample, 3000);
            });
        });
    }
}
