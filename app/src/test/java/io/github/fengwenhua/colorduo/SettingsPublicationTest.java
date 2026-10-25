/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public final class SettingsPublicationTest {
    @Test public void oneLocalOneRemoteOneNoticeInOrder() {
        List<String> operations = new ArrayList<>();
        assertTrue(SettingsPublication.commit(() -> { operations.add("local"); return true; },
                () -> { operations.add("remote"); return true; }, () -> operations.add("notice")));
        assertEquals(List.of("local", "remote", "notice"), operations);
    }
    @Test public void neverNotifyUncommittedState() {
        int[] count = new int[3];
        assertFalse(SettingsPublication.commit(() -> { count[0]++; return false; },
                () -> { count[1]++; return true; }, () -> count[2]++));
        assertArrayEquals(new int[]{1, 0, 0}, count);
        assertFalse(SettingsPublication.commit(() -> { count[0]++; return true; },
                () -> { count[1]++; return false; }, () -> count[2]++));
        assertArrayEquals(new int[]{2, 1, 0}, count);
    }
}
