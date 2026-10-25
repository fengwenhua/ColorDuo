/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

import org.junit.Test;
import static org.junit.Assert.*;

public final class VersionPolicyTest {
    @Test public void numericSegmentsAndPreReleasesHaveSemanticOrder() {
        assertTrue(VersionPolicy.compare("1.10.0", "1.9.99") > 0);
        assertEquals(0, VersionPolicy.compare("v1.2", "1.2.0+build5"));
        assertTrue(VersionPolicy.compare("1.0.3", "1.0.3-test2") > 0);
        assertTrue(VersionPolicy.compare("1.0.3-rc.10", "1.0.3-rc.2") > 0);
        assertTrue(VersionPolicy.compare("1.0.4", "1.0.3-test9999") > 0);
        assertTrue(VersionPolicy.compare("999999999999999999999.0.0", "2.0.0") > 0);
        assertThrows(IllegalArgumentException.class, () -> VersionPolicy.compare("../v1", "1.0.0"));
    }
}
