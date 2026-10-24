/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.yixing233.hyperduo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.view.View;
import android.view.ViewParent;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Date;
import java.util.Map;
import java.util.WeakHashMap;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** System time events plus a reversible policy gate on the paired home StatClock only. */
final class ColorOsClockBridge {
    private final XposedModule module;
    private final ClassLoader loader;
    private final Runnable changed;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<View, WeakReference<View>> leases = new WeakHashMap<>();
    private Class<?> clockType;
    private Method updateVisibility;
    private Method nativePolicy;
    private boolean capable;
    private Context context;
    private BroadcastReceiver receiver;
    private ContentObserver formatObserver;
    private volatile String time = "";

    ColorOsClockBridge(XposedModule module, ClassLoader loader, Runnable changed) {
        this.module = module; this.loader = loader; this.changed = changed;
    }

    void install() {
        try {
            clockType = Class.forName("com.oplus.systemui.statusbar.widget.StatClock", false, loader);
            updateVisibility = clockType.getMethod("updateClockVisibility");
            Method visible = clockType.getDeclaredMethod("shouldBeVisible");
            nativePolicy = visible;
            Method setter = clockType.getDeclaredMethod("setVisibility", int.class);
            visible.setAccessible(true); setter.setAccessible(true);
            module.hook(visible).setId("hyperduo-coloros-clock-policy")
                    .setExceptionMode(XposedInterface.ExceptionMode.PASSTHROUGH).intercept(chain -> {
                        Object result = chain.proceed();
                        if (Looper.myLooper() == Looper.getMainLooper() && owns((View) chain.getThisObject())) return false;
                        return result;
                    });
            module.hook(setter).setId("hyperduo-coloros-clock-visibility")
                    .setExceptionMode(XposedInterface.ExceptionMode.PASSTHROUGH).intercept(chain -> {
                        boolean owned = Looper.myLooper() == Looper.getMainLooper() && owns((View) chain.getThisObject());
                        return owned ? chain.proceed(new Object[]{View.GONE}) : chain.proceed();
                    });
            capable = true;
        } catch (Throwable failure) { log("clockReady=false " + failure.getClass().getSimpleName()); }
    }

    String time() { return time; }
    boolean ready() { return capable && receiver != null && !time.isEmpty(); }

    void setActive(Context requested, boolean active) {
        if (!active) { stop(); return; }
        if (!capable || receiver != null || requested == null) return;
        try {
            context = requested.getApplicationContext();
            if (context == null) return;
            receiver = new BroadcastReceiver() {
                @Override public void onReceive(Context received, Intent intent) { publish(); }
            };
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_TIME_TICK);
            filter.addAction(Intent.ACTION_TIME_CHANGED);
            filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
            filter.addAction(Intent.ACTION_LOCALE_CHANGED);
            filter.addAction(Intent.ACTION_CONFIGURATION_CHANGED);
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
            formatObserver = new ContentObserver(main) {
                @Override public void onChange(boolean selfChange) { publish(); }
            };
            context.getContentResolver().registerContentObserver(Settings.System.getUriFor(Settings.System.TIME_12_24),
                    false, formatObserver);
            publish();
        } catch (Throwable failure) {
            capable = false;
            log("time source unavailable=" + failure.getClass().getSimpleName());
            stop();
            main.post(changed);
        }
    }

    private void publish() {
        if (context == null) return;
        try {
            String next = DateFormat.getTimeFormat(context).format(new Date());
            if (time.equals(next)) return;
            time = next;
            TrioState.sClockText = next;
            main.post(changed);
        } catch (RuntimeException failure) {
            capable = false;
            log("clock formatting failed");
            stop();
            main.post(changed);
        }
    }

    private void stop() {
        releaseAll();
        try { if (receiver != null && context != null) context.unregisterReceiver(receiver); }
        catch (RuntimeException ignored) {}
        try { if (formatObserver != null && context != null) context.getContentResolver().unregisterContentObserver(formatObserver); }
        catch (RuntimeException ignored) {}
        receiver = null; formatObserver = null; context = null; time = "";
    }

    void commit(View meter, String scene, boolean drawsClock) {
        if (!"home".equals(scene) || !drawsClock || !ready()) { release(meter); return; }
        View clock = findHomeClock(meter);
        if (clock == null) { release(meter); return; }
        WeakReference<View> previous = leases.get(meter);
        if (previous != null && previous.get() == clock) return;
        release(meter);
        for (WeakReference<View> owned : leases.values()) if (owned.get() == clock) return;
        leases.put(meter, new WeakReference<>(clock));
        recompute(clock);
    }

    void release(View meter) {
        WeakReference<View> owned = leases.remove(meter);
        View clock = owned == null ? null : owned.get();
        if (clock != null) recompute(clock);
    }

    private void releaseAll() {
        for (View meter : new ArrayList<>(leases.keySet())) release(meter);
    }

    private boolean owns(View clock) {
        for (WeakReference<View> owned : leases.values()) if (owned.get() == clock) return true;
        return false;
    }

    private void recompute(View clock) {
        try { updateVisibility.invoke(clock); }
        catch (Throwable failure) {
            try { clock.setVisibility(Boolean.TRUE.equals(nativePolicy.invoke(clock)) ? View.VISIBLE : View.GONE); }
            catch (Throwable ignored) { log("native clock policy refresh failed=" + failure.getClass().getSimpleName()); }
        }
    }

    private View findHomeClock(View meter) {
        int contents = meter.getResources().getIdentifier("status_bar_contents", "id", "com.android.systemui");
        int clockId = meter.getResources().getIdentifier("clock", "id", "com.android.systemui");
        if (contents == 0 || clockId == 0) return null;
        for (ViewParent parent = meter.getParent(); parent instanceof View; parent = parent.getParent()) {
            View view = (View) parent;
            if (view.getId() != contents) continue;
            View clock = view.findViewById(clockId);
            return clockType.isInstance(clock) && clock.isAttachedToWindow()
                    && clock.getWindowToken() == meter.getWindowToken() ? clock : null;
        }
        return null;
    }

    private void log(String message) {
        try { module.log(4, "HyperDuoColorOSClock", message); } catch (Throwable ignored) {}
    }
}
