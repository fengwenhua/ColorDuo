/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

import java.math.BigInteger;

/** Semantic release ordering; numeric fragments inside preview labels are not patch versions. */
public final class VersionPolicy {
    private VersionPolicy() {}
    public static int compare(String left, String right) {
        String[] a = parse(left), b = parse(right);
        String[] an = a[0].split("\\."), bn = b[0].split("\\.");
        for (int i = 0; i < 3; i++) {
            int c = new BigInteger(i < an.length ? an[i] : "0").compareTo(new BigInteger(i < bn.length ? bn[i] : "0"));
            if (c != 0) return c;
        }
        if (a.length == 1 || b.length == 1) return Integer.compare(b.length, a.length);
        String[] ap = a[1].split("\\."), bp = b[1].split("\\.");
        for (int i = 0; i < Math.min(ap.length, bp.length); i++) {
            boolean na = ap[i].matches("[0-9]+"), nb = bp[i].matches("[0-9]+");
            int c = na && nb ? new BigInteger(ap[i]).compareTo(new BigInteger(bp[i]))
                    : na != nb ? (na ? -1 : 1) : ap[i].compareTo(bp[i]);
            if (c != 0) return c;
        }
        return Integer.compare(ap.length, bp.length);
    }
    private static String[] parse(String value) {
        String clean = value.trim().replaceFirst("^[vV]", "").split("\\+", 2)[0];
        if (!clean.matches("[0-9]+(?:\\.[0-9]+){0,2}(?:-[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?"))
            throw new IllegalArgumentException("Invalid version");
        return clean.split("-", 2);
    }
}
