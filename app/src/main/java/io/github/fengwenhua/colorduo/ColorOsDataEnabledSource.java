/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

import android.annotation.SuppressLint;
import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import java.util.Objects;
import java.util.function.Consumer;

/** Cached user data switch for the default data SIM; no binder reads in the draw path. */
final class ColorOsDataEnabledSource {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable changed;
    private final Consumer<String> report;
    private Context context;
    private ContentObserver observer;
    private volatile Boolean enabled;

    ColorOsDataEnabledSource(Runnable changed, Consumer<String> report) {
        this.changed = changed; this.report = report;
    }

    Boolean get() { return enabled; }
    boolean active() { return observer != null; }

    void setActive(Context requested, boolean active) {
        if (!active || requested == null) { stop(); return; }
        if (context != null) return;
        context = requested.getApplicationContext();
        if (context == null) return;
        try {
            observer = new ContentObserver(main) {
                @Override public void onChange(boolean selfChange, Uri uri) {
                    if (ColorOsNetworkPolicy.dataSettingChanged(uri == null ? null : uri.getLastPathSegment())) sample();
                }
                @Override public void onChange(boolean selfChange) { sample(); }
            };
            // Per-sub keys (mobile_data1, etc.) are siblings, not descendants of mobile_data.
            context.getContentResolver().registerContentObserver(Settings.Global.CONTENT_URI, true, observer);
            sample();
        } catch (RuntimeException failure) {
            report.accept("dataEnabled=unknown reason=" + failure.getClass().getSimpleName());
            stop();
        }
    }

    @SuppressLint("MissingPermission") // Runs inside privileged SystemUI, which holds READ_PHONE_STATE.
    private void sample() {
        if (context == null) return;
        Boolean next = null;
        try {
            int subscription = SubscriptionManager.getDefaultDataSubscriptionId();
            TelephonyManager manager = context.getSystemService(TelephonyManager.class);
            if (!SubscriptionManager.isValidSubscriptionId(subscription)) next = false;
            else if (manager != null) next = manager.createForSubscriptionId(subscription).isDataEnabled();
        } catch (RuntimeException failure) {
            report.accept("dataEnabled=unknown reason=" + failure.getClass().getSimpleName());
        }
        if (!Objects.equals(enabled, next)) {
            enabled = next;
            report.accept("dataEnabled=" + next);
            changed.run();
        }
    }

    private void stop() {
        if (observer != null && context != null) {
            try { context.getContentResolver().unregisterContentObserver(observer); }
            catch (RuntimeException ignored) { }
        }
        observer = null; context = null; enabled = null;
    }
}
