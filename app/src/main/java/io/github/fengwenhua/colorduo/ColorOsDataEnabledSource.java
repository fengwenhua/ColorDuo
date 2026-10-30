/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;
import java.util.Objects;
import java.util.function.Consumer;

/** Cached user data switch for the default data SIM; no binder reads in the draw path. */
final class ColorOsDataEnabledSource {
    // Framework protected broadcast; only resample the current subscription, never trust extras.
    private static final String DEFAULT_DATA_CHANGED = "android.intent.action.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED";
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable changed;
    private final Consumer<String> report;
    private Context context;
    private ContentObserver observer;
    private SubscriptionManager subscriptions;
    private SubscriptionManager.OnSubscriptionsChangedListener subscriptionListener;
    private BroadcastReceiver defaultDataReceiver;
    private TelephonyManager observedManager;
    private TelephonyCallback dataCallback;
    private int observedSubscription = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    private volatile Boolean enabled;

    ColorOsDataEnabledSource(Runnable changed, Consumer<String> report) {
        this.changed = changed; this.report = report;
    }

    Boolean get() { return enabled; }
    boolean active() { return observer != null; }
    boolean live() { return dataCallback != null; }
    int subscription() { return observedSubscription; }

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
        } catch (RuntimeException failure) {
            report.accept("dataEnabled=unknown reason=" + failure.getClass().getSimpleName());
            stop();
            return;
        }
        try {
            subscriptions = context.getSystemService(SubscriptionManager.class);
            if (subscriptions != null) {
                subscriptionListener = new SubscriptionManager.OnSubscriptionsChangedListener() {
                    @Override public void onSubscriptionsChanged() { sample(); }
                };
                subscriptions.addOnSubscriptionsChangedListener(subscriptionListener);
            }
            defaultDataReceiver = new BroadcastReceiver() {
                @Override public void onReceive(Context ignored, Intent intent) { sample(); }
            };
            IntentFilter filter = new IntentFilter(DEFAULT_DATA_CHANGED);
            if (Build.VERSION.SDK_INT >= 33)
                context.registerReceiver(defaultDataReceiver, filter, Context.RECEIVER_EXPORTED);
            else context.registerReceiver(defaultDataReceiver, filter);
        } catch (RuntimeException failure) {
            report.accept("dataSubscriptionEvents=unavailable reason=" + failure.getClass().getSimpleName());
        }
        sample();
    }

    @SuppressLint("MissingPermission") // Runs inside privileged SystemUI, which holds READ_PHONE_STATE.
    private void sample() {
        if (context == null) return;
        try {
            int subscription = SubscriptionManager.getDefaultDataSubscriptionId();
            if (subscription == observedSubscription && dataCallback != null) return;
            unlisten();
            if (!SubscriptionManager.isValidSubscriptionId(subscription)) {
                // SystemUI can start before the default SIM exists. Unknown is not user OFF.
                publish(null);
                return;
            }
            TelephonyManager manager = context.getSystemService(TelephonyManager.class);
            if (manager == null) { publish(null); return; }
            TelephonyManager selected = manager.createForSubscriptionId(subscription);
            publish(selected.isDataEnabled());
            if (Build.VERSION.SDK_INT >= 31) listen(selected, subscription);
        } catch (RuntimeException failure) {
            report.accept("dataEnabled=unknown reason=" + failure.getClass().getSimpleName());
            publish(null);
        }
    }

    @SuppressLint({"NewApi", "MissingPermission"}) // Only called on API 31+ inside SystemUI.
    private void listen(TelephonyManager manager, int subscription) {
        final class Callback extends TelephonyCallback implements TelephonyCallback.UserMobileDataStateListener {
            @Override public void onUserMobileDataStateChanged(boolean value) {
                if (context == null || dataCallback != this || observedSubscription != subscription) return;
                publish(value);
            }
        }
        observedManager = manager;
        observedSubscription = subscription;
        dataCallback = new Callback();
        try {
            manager.registerTelephonyCallback(context.getMainExecutor(), dataCallback);
            report.accept("dataSource=telephony subscription=" + subscription);
        } catch (RuntimeException failure) {
            unlisten();
            report.accept("dataSource=settingsFallback reason=" + failure.getClass().getSimpleName());
        }
    }

    private void publish(Boolean next) {
        if (!Objects.equals(enabled, next)) {
            enabled = next;
            report.accept("dataEnabled=" + next);
            changed.run();
        }
    }

    @SuppressLint("NewApi") // A callback exists only after API 31+ registration.
    private void unlisten() {
        TelephonyManager previousManager = observedManager;
        TelephonyCallback previousCallback = dataCallback;
        observedManager = null; dataCallback = null;
        observedSubscription = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        try {
            if (previousManager != null && previousCallback != null)
                previousManager.unregisterTelephonyCallback(previousCallback);
        } catch (RuntimeException ignored) { }
    }

    private void stop() {
        Context previous = context;
        context = null;
        unlisten();
        if (observer != null && previous != null) {
            try { previous.getContentResolver().unregisterContentObserver(observer); }
            catch (RuntimeException ignored) { }
        }
        if (subscriptions != null && subscriptionListener != null) {
            try { subscriptions.removeOnSubscriptionsChangedListener(subscriptionListener); }
            catch (RuntimeException ignored) { }
        }
        if (defaultDataReceiver != null && previous != null) {
            try { previous.unregisterReceiver(defaultDataReceiver); }
            catch (RuntimeException ignored) { }
        }
        observer = null; subscriptions = null; subscriptionListener = null;
        defaultDataReceiver = null; enabled = null;
    }
}
