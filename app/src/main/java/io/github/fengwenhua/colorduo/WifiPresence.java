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
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.WifiInfo;
import android.os.Handler;
import android.os.Looper;
import android.net.wifi.WifiManager;
import android.os.Build;

/** Event-driven Wi-Fi presence, independent of the icons that we hide ourselves. */
final class WifiPresence {
    private static boolean registered;
    private static volatile Boolean connected;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static Runnable pendingRefresh;
    private static boolean watchdogStarted;

    private WifiPresence() {}

    static boolean resolve(boolean iconVisible, int cachedLevel) {
        final Boolean live = connected;
        return live != null ? live.booleanValue() : iconVisible || cachedLevel >= 0;
    }

    static synchronized void start(final Context context) {
        if (!watchdogStarted) {
            watchdogStarted = true;
            // ROM broadcast/callback delivery is not always reliable. A bounded
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
        final IntentFilter filter = new IntentFilter();
        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        filter.addAction(WifiManager.RSSI_CHANGED_ACTION);
        final BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ignored, Intent intent) {
                // Re-query framework state rather than trust broadcast extras.
                refresh(context);
                scheduleRefresh(context);
            }
        };
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                // Wi-Fi broadcasts can originate from the privileged Wi-Fi UID,
                // not the system UID. NOT_EXPORTED drops those deliveries.
                // These framework actions are protected broadcasts; always query
                // framework state instead of accepting sender-controlled extras.
                context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
            } else {
                context.registerReceiver(receiver, filter);
            }
            registered = true;
            refresh(context);
            final ConnectivityManager cm = context.getSystemService(ConnectivityManager.class);
            if (cm != null) {
                cm.registerNetworkCallback(new NetworkRequest.Builder()
                        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(),
                        new ConnectivityManager.NetworkCallback() {
                            @Override public void onAvailable(Network network) { scheduleRefresh(context); }
                            @Override public void onLost(Network network) { scheduleRefresh(context); }
                            @Override public void onCapabilitiesChanged(Network network,
                                    NetworkCapabilities capabilities) { scheduleRefresh(context); }
                        });
            }
        } catch (RuntimeException ignored) {
            // Unknown remains on the existing ROM-compatible icon fallback.
        }
    }

    // Broadcast delivery may precede ConnectivityManager's connected snapshot.
    // Coalesce a delayed recheck; callbacks also catch connection completion.
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

    @SuppressWarnings("deprecation")
    private static void refresh(Context context) {
        try {
            final WifiManager wifi = context.getSystemService(WifiManager.class);
            if (wifi == null) return;
            final boolean enabled = wifi.isWifiEnabled();
            // A definitive radio-off result must not depend on a subsequent
            // connectivity query (which may throw on a ROM/permission mismatch).
            if (!enabled) {
                publish(false);
                return;
            }
            final ConnectivityManager connectivity =
                    context.getSystemService(ConnectivityManager.class);
            // TYPE_WIFI observes Wi-Fi even when mobile/VPN is the default route.
            final NetworkInfo network = connectivity == null ? null
                    : connectivity.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
            if (enabled && connectivity == null) return;
            final boolean next = enabled && network != null && network.isConnected();
            final int before = TrioState.sWifiLevel;
            publish(next);
            if (!next) {
                TrioState.sWifiLevel = -1;
            } else {
                // Hidden native icons may not rebind after reconnect.
                final WifiInfo info = wifi.getConnectionInfo();
                if (info != null && info.getRssi() > -127) {
                    TrioState.sWifiLevel = WifiManager.calculateSignalLevel(info.getRssi(), 5);
                }
            }
            if (before != TrioState.sWifiLevel) TrioHooks.invalidateHosts();
        } catch (RuntimeException ignored) {
            // Permissions/ROM differences must not crash SystemUI.
        }
    }

    private static void publish(boolean next) {
        connected = Boolean.valueOf(next);
        final int before = TrioState.sWifiLevel;
        if (!next) TrioState.sWifiLevel = -1;
        final boolean changed = TrioState.setWifiPresent(next);
        if (changed || before != TrioState.sWifiLevel) TrioHooks.invalidateHosts();
    }
}
