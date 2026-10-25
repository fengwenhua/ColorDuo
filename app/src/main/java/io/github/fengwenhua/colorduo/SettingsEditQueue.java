/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Thread-safe patch coalescing. An older failed transaction cannot overwrite newer UI edits. */
final class SettingsEditQueue {
    private final Map<String, Object> pending = new LinkedHashMap<>();
    private final Map<String, Object> visible = new LinkedHashMap<>();
    private boolean sync;

    synchronized void stage(Map<String, Object> changes) {
        pending.putAll(changes); visible.putAll(changes);
    }
    synchronized void requestSync() { sync = true; }
    synchronized Map<String, Object> overlay(Map<String, ?> disk) {
        Map<String, Object> result = new LinkedHashMap<>(disk);
        result.putAll(visible);
        result.values().removeIf(Objects::isNull);
        return result;
    }
    synchronized Map<String, Object> take() {
        if (pending.isEmpty() && !sync) return null;
        Map<String, Object> result = Collections.unmodifiableMap(new LinkedHashMap<>(pending));
        pending.clear(); sync = false;
        return result;
    }
    synchronized void acknowledged(Map<String, Object> batch) {
        batch.forEach((key, value) -> {
            if (!pending.containsKey(key) && Objects.equals(visible.get(key), value)) visible.remove(key);
        });
    }
    synchronized void failed(Map<String, Object> batch) {
        batch.forEach((key, value) -> { if (!pending.containsKey(key)) pending.put(key, value); });
        sync = true;
    }
    synchronized boolean pending() { return sync || !pending.isEmpty(); }
}
