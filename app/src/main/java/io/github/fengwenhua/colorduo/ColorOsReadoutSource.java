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
        final boolean attached;
        final float watts;
        final int temperature;
        State(boolean attached, float watts, int temperature) { this.attached = attached; this.watts = watts; this.temperature = temperature; }
        boolean same(State other) {
            return other != null && attached == other.attached && Float.compare(watts, other.watts) == 0 && temperature == other.temperature;
        }
        String text(TrioSettings settings) {
            String power = "";
            if (settings.perfPower && attached && Float.isFinite(watts) && watts >= 0) {
                int tenths = Math.round(watts * 10f);
                power = tenths >= 100 ? tenths / 10 + "W" : tenths / 10 + "." + tenths % 10 + "W";
            }
            String temp = settings.perfTemp && temperature != Integer.MIN_VALUE && (!settings.perfTempChargingOnly || attached)
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
                    int current = manager == null ? Integer.MIN_VALUE : manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
                    boolean attached = battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
                    float watts = current == Integer.MIN_VALUE || voltage <= 0 ? Float.NaN
                            : Math.abs((long) current) * (voltage / 1000f) / 1_000_000f;
                    result = new State(attached, watts, temperature == Integer.MIN_VALUE ? Integer.MIN_VALUE : Math.round(temperature / 10f));
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
