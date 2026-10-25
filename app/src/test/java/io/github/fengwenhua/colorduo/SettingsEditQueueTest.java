/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public final class SettingsEditQueueTest {
    @Test public void burstPreviewsImmediatelyAndPublishesLastValueOnce() {
        SettingsEditQueue q = new SettingsEditQueue();
        for (int i = 0; i < 240; i++) {
            q.stage(Map.of("size", i));
            assertEquals(i, q.overlay(Map.of("size", -1)).get("size"));
        }
        Map<String, Object> batch = q.take();
        assertEquals(Map.of("size", 239), batch);
        assertNull(q.take());
        q.acknowledged(batch);
        assertFalse(q.pending());
        assertEquals(239, q.overlay(Map.of("size", 239)).get("size"));
    }

    @Test public void olderAcknowledgementCannotEraseANewerPreview() {
        SettingsEditQueue q = new SettingsEditQueue();
        q.stage(Map.of("size", 10));
        Map<String, Object> first = q.take();
        q.stage(Map.of("size", 20, "style", 2));
        q.acknowledged(first);
        assertEquals(20, q.overlay(Map.of("size", 10)).get("size"));
        assertEquals(Map.of("size", 20, "style", 2), q.take());
    }

    @Test public void failedBinderKeepsNewerValueAndExplicitRemoval() {
        SettingsEditQueue q = new SettingsEditQueue();
        q.stage(Map.of("size", 10, "font", "old"));
        Map<String, Object> first = q.take();
        Map<String, Object> next = new HashMap<>();
        next.put("size", 20); next.put("font", null);
        q.stage(next); q.failed(first);
        assertFalse(q.overlay(Map.of("font", "old")).containsKey("font"));
        Map<String, Object> retry = q.take();
        assertEquals(20, retry.get("size"));
        assertTrue(retry.containsKey("font"));
        assertNull(retry.get("font"));
    }

    @Test public void reconnectReplaysEvenWithoutNewEdits() {
        SettingsEditQueue q = new SettingsEditQueue();
        assertNull(q.take());
        q.requestSync();
        assertEquals(Map.of(), q.take());
        assertNull(q.take());
    }
}
