/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Handler;
import android.os.Looper;
import android.os.Build;

/** Observe the same VPN transport as SystemUI, independently of Quick Settings visibility. */
final class VpnStateSource {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final VpnNetworks<Network> networks = new VpnNetworks<>();
    private final Runnable changed;
    private ConnectivityManager manager;
    private ConnectivityManager.NetworkCallback callback;
    private long generation;
    private volatile boolean active, listening;
    private boolean failed;
    private String error = "None";

    VpnStateSource(Runnable changed) { this.changed = changed; }
    boolean active() { return active; }
    boolean listening() { return listening; }
    String error() { return error; }

    // Executes inside SystemUI, which holds the required network permission.
    @android.annotation.SuppressLint("MissingPermission")
    void setActive(Context context, boolean enabled) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post(() -> setActive(context, enabled));
            return;
        }
        if (!enabled) { stop(); failed = false; error = "None"; return; }
        if (context == null || callback != null || failed) return;
        final long session = ++generation;
        try {
            Context app = context.getApplicationContext();
            manager = (app == null ? context : app).getSystemService(ConnectivityManager.class);
            if (manager == null) throw new IllegalStateException("Connectivity service unavailable");
            callback = new ConnectivityManager.NetworkCallback() {
                private void update(Network network, boolean present) {
                    if (session != generation || !listening) return;
                    if (networks.update(network, present)) publish();
                }
                @Override public void onAvailable(Network network) { update(network, true); }
                @Override public void onLost(Network network) { update(network, false); }
                @Override public void onCapabilitiesChanged(Network network, NetworkCapabilities caps) {
                    update(network, caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN));
                }
            };
            listening = true;
            // The default request contains NOT_VPN. Remove all defaults, as SecurityController does.
            // Do not require INTERNET/VALIDATED: an enabled local/offline VPN still counts.
            NetworkRequest.Builder request = new NetworkRequest.Builder();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                request.clearCapabilities();
            } else {
                // Android 10's public API cannot clearCapabilities; remove its three defaults.
                request.removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                        .removeCapability(NetworkCapabilities.NET_CAPABILITY_TRUSTED)
                        .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED);
            }
            manager.registerNetworkCallback(request.addTransportType(NetworkCapabilities.TRANSPORT_VPN)
                    .build(), callback, main);
        } catch (RuntimeException failure) {
            stop(); failed = true; error = failure.getClass().getSimpleName();
        }
    }

    private void publish() {
        boolean next = networks.active();
        if (active == next) return;
        active = next;
        changed.run();
    }

    private void stop() {
        generation++;
        if (callback != null && manager != null) {
            try { manager.unregisterNetworkCallback(callback); } catch (RuntimeException ignored) {}
        }
        callback = null; manager = null; listening = false; networks.clear();
        publish();
    }
}
