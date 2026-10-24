/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.yixing233.hyperduo;

import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.system.Os;
import android.system.OsConstants;
import io.github.libxposed.api.XposedModule;
import java.io.IOException;
import java.security.MessageDigest;

/** Read-only remote fonts. Hashing and parsing never run in a View or Drawable callback. */
final class ColorOsFontSource {
    private final XposedModule module;
    private final Runnable changed;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final LatestWorker worker = new LatestWorker("HyperDuo-font-load");
    private String wanted = "";
    private String cachedName = "";
    private Typeface cachedFace;
    private boolean active;
    private long generation;
    private volatile String status = "system";

    ColorOsFontSource(XposedModule module, Runnable changed) { this.module = module; this.changed = changed; }

    void setActive(String name, boolean enabled) {
        if (active == enabled && wanted.equals(name)) return;
        active = enabled; wanted = name;
        long token = ++generation;
        if (!enabled || name.isEmpty()) {
            if (name.isEmpty()) { cachedName = ""; cachedFace = null; }
            status = "system"; prepareRenderer(); return;
        }
        if (cachedName.equals(name) && cachedFace != null) { status = "ready"; prepareRenderer(); return; }
        status = "loading";
        prepareRenderer();
        worker.submit(() -> {
            Typeface loaded = null;
            String outcome;
            try { loaded = load(name); outcome = "ready"; }
            catch (Exception failure) { outcome = "fallback:" + failure.getClass().getSimpleName(); }
            Typeface face = loaded;
            String result = outcome;
            main.post(() -> {
                if (generation != token || !active || !wanted.equals(name)) return;
                cachedName = name; cachedFace = face; status = result;
                prepareRenderer(); changed.run();
            });
        });
    }

    String key() { return active && cachedFace != null && wanted.equals(cachedName) ? "remote-font:" + cachedName : ""; }
    String status() { return status; }
    void prepareRenderer() { TrioRenderer.setLoadedFont(key(), key().isEmpty() ? null : cachedFace); }

    private Typeface load(String name) throws Exception {
        if (!RemoteFontKey.valid(name) || name.isEmpty()) throw new IOException("font key");
        try (ParcelFileDescriptor.AutoCloseInputStream input = new ParcelFileDescriptor.AutoCloseInputStream(module.openRemoteFile(name))) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[16_384];
            int total = 0, count;
            while ((count = input.read(buffer)) != -1) {
                total += count;
                if (total > RemoteFontKey.MAX_BYTES) throw new IOException("font too large");
                digest.update(buffer, 0, count);
            }
            if (total == 0 || !RemoteFontKey.hex(digest.digest()).equals(RemoteFontKey.expectedDigest(name)))
                throw new IOException("font digest");
            Os.lseek(input.getFD(), 0, OsConstants.SEEK_SET);
            Typeface face = new Typeface.Builder(input.getFD()).build();
            if (face == null) throw new IOException("font parsing");
            return face;
        }
    }
}
