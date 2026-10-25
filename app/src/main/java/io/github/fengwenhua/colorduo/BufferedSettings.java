/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** App-only editing view. apply() previews immediately; a bounded worker persists atomic patches. */
public final class BufferedSettings implements SharedPreferences {
    private static BufferedSettings instance;
    private final SharedPreferences disk;
    private final SettingsEditQueue queue = new SettingsEditQueue();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> new Thread(r, "ColorDuo-settings"));
    private final Sink sink;
    private final Runnable scheduled = this::flush;
    private volatile boolean running;
    private volatile boolean lastSuccess = true;
    private boolean flushAgain;
    private long scheduledAt;

    interface Sink { boolean persist(Map<String, Object> patch); }

    public static synchronized BufferedSettings get(Context context) {
        if (instance == null) {
            Context app = context.getApplicationContext();
            instance = new BufferedSettings(app.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE),
                    patch -> ConfigPacketPublisher.publish(app, ColorDuoApp.Companion.getXposedService(), patch));
        }
        return instance;
    }

    BufferedSettings(SharedPreferences disk, Sink sink) { this.disk = disk; this.sink = sink; }
    public SharedPreferences snapshot() { return new PreferencesSnapshot(getAll()); }
    @Override public Map<String, ?> getAll() { return queue.overlay(disk.getAll()); }
    @Override public boolean contains(String key) { return getAll().containsKey(key); }
    @Override public boolean getBoolean(String key, boolean fallback) { Object v = getAll().get(key); return v instanceof Boolean ? (Boolean) v : fallback; }
    @Override public int getInt(String key, int fallback) { Object v = getAll().get(key); return v instanceof Integer ? (Integer) v : fallback; }
    @Override public long getLong(String key, long fallback) { Object v = getAll().get(key); return v instanceof Long ? (Long) v : fallback; }
    @Override public float getFloat(String key, float fallback) { Object v = getAll().get(key); return v instanceof Float ? (Float) v : fallback; }
    @Override public String getString(String key, String fallback) { Object v = getAll().get(key); return v instanceof String ? (String) v : fallback; }
    @Override public Set<String> getStringSet(String key, Set<String> fallback) {
        Object value = getAll().get(key);
        if (!(value instanceof Set<?>)) return fallback;
        Set<String> copy = new HashSet<>();
        for (Object item : (Set<?>) value) if (item instanceof String) copy.add((String) item);
        return copy;
    }
    @Override public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) { throw new UnsupportedOperationException("Use UI state"); }
    @Override public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {}
    @Override public Editor edit() { return new PatchEditor(); }

    public void putInt(String key, int value) {
        Map<String, Object> patch = Map.of(key, value);
        queue.stage(patch);
        // Style/navigation selections are discrete. Continuous appearance values get one batch per 80ms.
        boolean immediate = key.equals(Prefs.KEY_TRIO_STYLE) || key.equals(Prefs.KEY_NAV_STYLE) || key.equals(Prefs.KEY_NAV_LABELS);
        schedule(immediate ? 0 : 80);
    }

    public void sync() { queue.requestSync(); flush(); }
    public boolean pending() { return queue.pending() || running; }

    private synchronized void schedule(long delay) {
        long target = android.os.SystemClock.uptimeMillis() + delay;
        if (scheduledAt != 0 && target >= scheduledAt) return;
        main.removeCallbacks(scheduled); scheduledAt = target;
        main.postDelayed(scheduled, delay);
    }

    public synchronized void flush() {
        main.removeCallbacks(scheduled); scheduledAt = 0;
        if (running) { flushAgain = true; return; }
        running = true;
        flushAgain = false;
        worker.execute(() -> {
            try {
                // One scheduled batch only. Draining edits that arrive during a slow Binder
                // write would bypass their 80ms timer and turn coalescing into a tight loop.
                persistOne();
            } finally {
                boolean requested;
                synchronized (BufferedSettings.this) { running = false; requested = flushAgain; flushAgain = false; }
                // New edits already own a timer. Only an explicit flush/bind may override it.
                if (requested) schedule(0);
            }
        });
    }

    private boolean persistOne() {
        Map<String, Object> patch = queue.take();
        if (patch == null) return lastSuccess;
        try {
            lastSuccess = sink.persist(patch);
            if (lastSuccess) queue.acknowledged(patch);
            else queue.failed(patch);
        } catch (RuntimeException failure) {
            lastSuccess = false;
            queue.failed(patch);
            Log.w("ColorDuo", "Settings sync pending: " + failure.getClass().getSimpleName());
        }
        return lastSuccess;
    }

    public boolean flushBlocking() {
        flush();
        // Also consume edits staged while the in-flight transaction was finishing.
        // A failed Binder is retried at most once here, never drained in a busy loop.
        try { return worker.submit(() -> persistOne() && !queue.pending()).get(2, TimeUnit.SECONDS); }
        catch (Exception failure) { return false; }
    }

    // Only the isolated debug probe owns a short-lived instance; the application singleton lives on.
    void closeForProbe() { main.removeCallbacks(scheduled); worker.shutdown(); }

    private final class PatchEditor implements Editor {
        private final Map<String, Object> values = new HashMap<>();
        public Editor putString(String key, String value) { values.put(key, value); return this; }
        public Editor putStringSet(String key, Set<String> value) { values.put(key, value == null ? null : new HashSet<>(value)); return this; }
        public Editor putInt(String key, int value) { values.put(key, value); return this; }
        public Editor putLong(String key, long value) { values.put(key, value); return this; }
        public Editor putFloat(String key, float value) { values.put(key, value); return this; }
        public Editor putBoolean(String key, boolean value) { values.put(key, value); return this; }
        public Editor remove(String key) { values.put(key, null); return this; }
        public Editor clear() { throw new UnsupportedOperationException("Explicit settings resets only"); }
        public void apply() { queue.stage(values); values.clear(); schedule(0); }
        public boolean commit() { apply(); return flushBlocking(); }
    }
}
