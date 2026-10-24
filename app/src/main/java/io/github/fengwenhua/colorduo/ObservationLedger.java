/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Bounded, thread-safe diagnostics. No target objects, SSIDs or phone numbers are retained. */
final class ObservationLedger {
    private final int capacity;
    private final long intervalMs;
    private final Map<String, Entry> entries = new LinkedHashMap<>(16, 0.75f, true);
    private long evictions;

    private static final class Entry {
        String value;
        String reported;
        long hits;
        long reportedAt;
    }

    ObservationLedger(int capacity, long intervalMs) {
        if (capacity < 1 || intervalMs < 0) throw new IllegalArgumentException();
        this.capacity = capacity;
        this.intervalMs = intervalMs;
    }

    /** Returns a changed, rate-limited log line, while retaining the newest sample even if muted. */
    synchronized String record(String key, String value, long nowMs) {
        if (key == null || value == null) throw new IllegalArgumentException();
        key = clip(key, 160);
        value = clip(value, 1000);
        Entry entry = entries.get(key);
        if (entry == null) {
            if (entries.size() == capacity) {
                entries.remove(entries.keySet().iterator().next());
                evictions++;
            }
            entry = new Entry();
            entries.put(key, entry);
        }
        entry.hits++;
        entry.value = value;
        if (value.equals(entry.reported)) return null;
        if (entry.reported != null && nowMs - entry.reportedAt < intervalMs) return null;
        entry.reported = value;
        entry.reportedAt = nowMs;
        return key + " hits=" + entry.hits + " " + value;
    }

    synchronized List<String> snapshot() {
        List<String> result = new ArrayList<>();
        result.add("entries=" + entries.size() + " evictions=" + evictions);
        for (Map.Entry<String, Entry> item : entries.entrySet()) {
            result.add(item.getKey() + " hits=" + item.getValue().hits + " " + item.getValue().value);
        }
        return result;
    }

    private static String clip(String text, int limit) {
        return text.length() <= limit ? text : text.substring(0, limit);
    }
}
