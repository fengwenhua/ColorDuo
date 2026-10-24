/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.yixing233.hyperduo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import io.github.libxposed.api.XposedModule;

/** One native Java listener; callbacks only copy scalars and post out of the OEM lock. */
final class ColorOsPowerSource {
    static final class State {
        static final State UNKNOWN = new State(false, false, false, false, false, false);
        final boolean ready, charging, quick, plugged, full, saving;
        State(boolean ready, boolean charging, boolean quick, boolean plugged, boolean full, boolean saving) {
            this.ready = ready; this.charging = charging; this.quick = quick;
            this.plugged = plugged; this.full = full; this.saving = saving;
        }
        boolean same(State other) {
            return ready == other.ready && charging == other.charging && quick == other.quick
                    && plugged == other.plugged && full == other.full && saving == other.saving;
        }
    }

    private final XposedModule module;
    private final ClassLoader loader;
    private final Runnable changed;
    private final Handler main = new Handler(Looper.getMainLooper());
    private Object controller, listener;
    private Method unregister;
    private Context context;
    private BroadcastReceiver receiver;
    private volatile State state = State.UNKNOWN;
    private boolean failed;
    private volatile long generation;

    ColorOsPowerSource(XposedModule module, ClassLoader loader, Runnable changed) {
        this.module = module; this.loader = loader; this.changed = changed;
    }

    State get() { return state; }

    void setActive(Context requestedContext, boolean active) {
        if (Looper.myLooper() != Looper.getMainLooper()) throw new IllegalStateException("power lifecycle off main");
        if (!active) { stop(); return; }
        if (listener != null || failed || requestedContext == null) return;
        final long session = ++generation;
        try {
            context = requestedContext.getApplicationContext();
            if (context == null) throw new IllegalStateException("application context not ready");
            Class<?> owner = Class.forName("com.oplusos.systemui.common.battery.OplusBatteryController", false, loader);
            Class<?> callback = Class.forName(owner.getName() + "$IBatteryStateChanged", false, loader);
            Class<?> additional = Class.forName("com.android.systemui.common.charge.OplusAddBatteryStatus", false, loader);
            Method realCharging = additional.getMethod("isRealCharging");
            Method fastCharging = additional.getMethod("isSuperVoocCharge");
            Method plugged = additional.getMethod("isPluggin");
            Method full = additional.getMethod("isClockFullCharge");
            Field status = additional.getField("status");
            unregister = owner.getMethod("unregisterIBatteryStateChanged", callback);
            controller = owner.getMethod("getInstance").invoke(null);
            listener = Proxy.newProxyInstance(loader, new Class<?>[]{callback}, (proxy, method, args) -> {
                switch (method.getName()) {
                    case "equals": return args != null && args.length == 1 && proxy == args[0];
                    case "hashCode": return System.identityHashCode(proxy);
                    case "toString": return "HyperDuo ColorOS battery listener";
                    default: break;
                }
                if (!"onAdditionalBatteryStateChanged".equals(method.getName())
                        || args == null || args.length != 1 || !additional.isInstance(args[0])) return null;
                try {
                    Object source = args[0];
                    boolean charging = (Boolean) realCharging.invoke(source);
                    State next = new State(status.getInt(source) >= 2 && status.getInt(source) <= 5,
                            charging, charging && (Boolean) fastCharging.invoke(source),
                            (Boolean) plugged.invoke(source), (Boolean) full.invoke(source), false);
                    main.post(() -> {
                        if (generation != session || listener == null) return;
                        try { publish(withSaving(next)); } catch (Throwable failure) { fail(failure); }
                    });
                } catch (Throwable failure) {
                    main.post(() -> { if (generation == session) fail(failure); });
                }
                return null;
            });
            receiver = new BroadcastReceiver() {
                @Override public void onReceive(Context received, Intent intent) {
                    if (generation == session && PowerManager.ACTION_POWER_SAVE_MODE_CHANGED.equals(intent.getAction())) {
                        try { publish(withSaving(state)); } catch (Throwable failure) { fail(failure); }
                    }
                }
            };
            context.registerReceiver(receiver, new IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED), Context.RECEIVER_NOT_EXPORTED);
            owner.getMethod("registerIBatteryStateChanged", callback).invoke(controller, listener);
            log("listener registered");
        } catch (Throwable failure) { fail(failure); }
    }

    private State withSaving(State source) {
        PowerManager power = context == null ? null : context.getSystemService(PowerManager.class);
        return new State(source.ready, source.charging, source.quick, source.plugged, source.full,
                power != null && power.isPowerSaveMode());
    }

    private void publish(State next) {
        if (state.same(next)) return;
        state = next;
        log("ready=" + next.ready + " charging=" + next.charging + " quick=" + next.quick
                + " plugged=" + next.plugged + " full=" + next.full + " saving=" + next.saving);
        changed.run();
    }

    private void fail(Throwable failure) {
        failed = true;
        log("native battery state unavailable: " + failure.getClass().getSimpleName());
        stop();
        changed.run();
    }

    private void stop() {
        if (listener == null && receiver == null) return;
        generation++;
        try { if (listener != null && controller != null && unregister != null) unregister.invoke(controller, listener); }
        catch (Throwable failure) { log("listener removal failed: " + failure.getClass().getSimpleName()); }
        try { if (receiver != null && context != null) context.unregisterReceiver(receiver); }
        catch (RuntimeException ignored) {}
        listener = null; controller = null; receiver = null; context = null;
        state = State.UNKNOWN;
        log("listener stopped");
    }

    private void log(String message) {
        try { module.log(4, "HyperDuoColorOSPower", message); } catch (Throwable ignored) {}
    }
}
