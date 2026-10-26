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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.Test;
import static org.junit.Assert.*;

/** Preserve the two legacy Android integration pins that scalar JVM fixtures cannot execute. */
public final class PresenceSourceContractTest {
    private String source(String name) throws Exception {
        return Files.readString(Path.of(System.getProperty("colorduo.productionSources"), name));
    }
    @Test public void wifiRadioOffSkipsTheQueryAndPrivilegedUidBroadcastsRemainReceivable() throws Exception {
        String code=source("WifiPresence.java");
        assertFalse(code.contains("Context.RECEIVER_NOT_EXPORTED"));
        assertTrue(code.contains("Context.RECEIVER_EXPORTED"));
        int off=code.indexOf("if (!enabled)"), query=code.indexOf("connectivity.getNetworkInfo");
        assertTrue("radio-off must be handled before framework connectivity query", off>=0 && query>off);
    }
    @Test public void nativeReadoutAllowsTwoLinesWithoutSingleLineClipping() throws Exception {
        String code=source("TrioHooks.java");
        var match=Pattern.compile(Pattern.quote("private static PerfLabel createPerfLabel(ViewGroup container)")
                + " \\{.*?\n    \\}", Pattern.DOTALL).matcher(code);
        assertTrue("production readout constructor missing", match.find());
        String body=match.group().replaceAll("(?m)^\\s*//.*$", "");
        assertTrue(body.contains("setMaxLines(2)"));
        assertFalse(body.contains("setSingleLine"));
    }
}
