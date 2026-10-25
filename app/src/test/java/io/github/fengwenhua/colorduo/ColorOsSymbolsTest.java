/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

import org.junit.Test;
import static org.junit.Assert.*;

public final class ColorOsSymbolsTest {
    @Test public void probeReportsOnlyCapabilityAndFailureType() {
        ClassLoader loader = getClass().getClassLoader();
        assertTrue(ColorOsSymbols.probe(loader, "java.lang.String", "length").available());
        var missingMethod = ColorOsSymbols.probe(loader, "java.lang.String", "missingMethod");
        assertFalse(missingMethod.available()); assertEquals("NoSuchMethodException", missingMethod.errorType());
        var missingClass = ColorOsSymbols.probe(loader, "missing.Class", "length");
        assertFalse(missingClass.available()); assertEquals("ClassNotFoundException", missingClass.errorType());
    }
    @Test public void absentOemClassesCannotClaimThisDesktopIsCompatible() {
        var report = ColorOsSymbols.inspect(getClass().getClassLoader());
        assertEquals(4, report.size());
        for (var value : report.values()) assertFalse(value.available());
    }
}
