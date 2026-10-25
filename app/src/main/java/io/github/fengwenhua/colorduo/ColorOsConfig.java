/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import io.github.libxposed.api.XposedModule;

/** Main-thread publication and revision checks for the ColorOS-only config channel. */
final class ColorOsConfig {
    private final XposedModule module;
    private final Runnable changed;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ConfigRevisionGate gate = new ConfigRevisionGate();
    private final SharedPreferences preferences;
    // Keep the preference listener strongly reachable for the hooked process lifetime.
    private final SharedPreferences.OnSharedPreferenceChangeListener listener;
    private TrioSettings snapshot;
    private boolean ready;
    private String fontFile = "";
    private boolean receiverInstalled;

    ColorOsConfig(XposedModule module, Runnable changed) {
        this.module = module;
        this.changed = changed;
        snapshot = TrioSettings.defaults();
        snapshot.enabled = false;
        SharedPreferences source = null;
        try { source = module.getRemotePreferences(Prefs.NAME); }
        catch (Throwable failure) { log("remote configuration unavailable"); }
        preferences = source;
        listener = (prefs, key) -> {
            if (key == null || Prefs.KEY_SNAPSHOT.equals(key)) readRemote();
        };
    }

    void start() {
        if (preferences == null) return;
        try { preferences.registerOnSharedPreferenceChangeListener(listener); }
        catch (Throwable failure) { log("remote callback unavailable; authenticated broadcast remains"); }
        readRemote();
        main.postDelayed(this::readRemote, 5_000);
        main.postDelayed(this::readRemote, 20_000);
    }

    TrioSettings get() { return snapshot; }
    boolean ready() { return ready; }
    String fontFile() { return fontFile; }

    void registerReceiver(Context context) {
        if (receiverInstalled || Build.VERSION.SDK_INT < 34) return;
        Context app = context.getApplicationContext();
        if (app == null) return;
        try {
            app.registerReceiver(new BroadcastReceiver() {
                @Override public void onReceive(Context receivedContext, Intent intent) {
                    try {
                        if (intent == null || !Prefs.ACTION_SNAPSHOT.equals(intent.getAction())) return;
                        int expected = receivedContext.getPackageManager()
                                .getPackageUid("io.github.fengwenhua.colorduo", 0);
                        if (getSentFromUid() != expected
                                || !"io.github.fengwenhua.colorduo".equals(getSentFromPackage())) {
                            log("snapshot rejected: sender identity");
                            return;
                        }
                        // The app commits the framework packet before notifying us.
                        // A map update may arrive even when its listener callback was lost.
                        readRemote();
                        accept(intent.getStringExtra(Prefs.KEY_SNAPSHOT), false);
                    } catch (Exception failure) { log("snapshot rejected: invalid broadcast"); }
                }
            }, new IntentFilter(Prefs.ACTION_SNAPSHOT), Prefs.CONFIG_PERMISSION, null, Context.RECEIVER_EXPORTED);
            receiverInstalled = true;
            log("snapshot receiver ready; UID/package identity required");
        } catch (RuntimeException failure) { log("cannot register snapshot receiver"); }
    }

    private void readRemote() {
        if (preferences == null) return;
        try {
            String packet = preferences.getString(Prefs.KEY_SNAPSHOT, null);
            if (packet != null) accept(packet, true);
        } catch (RuntimeException failure) { log("cannot read remote snapshot"); }
    }

    private void accept(String raw, boolean trustedRemote) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post(() -> accept(raw, trustedRemote));
            return;
        }
        try {
            ConfigPacket packet = ConfigPacket.decode(raw);
            ConfigRevisionGate.Result decision = gate.offer(packet.epoch, packet.revision, raw, trustedRemote);
            if (decision == ConfigRevisionGate.Result.ACCEPTED) {
                snapshot = packet.settings;
                fontFile = packet.fontFile;
                ready = true;
                log("snapshot applied revision=" + packet.revision + " enabled=" + snapshot.enabled
                        + " source=" + (trustedRemote ? "framework" : "authenticated-broadcast"));
                changed.run();
            } else if (decision != ConfigRevisionGate.Result.DUPLICATE) {
                log("snapshot ignored reason=" + decision + " revision=" + packet.revision);
            }
        } catch (Exception failure) { log("snapshot rejected: invalid payload"); }
    }

    private void log(String message) {
        try { module.log(4, "ColorDuoColorOSConfig", message); }
        catch (Throwable ignored) {}
    }
}
