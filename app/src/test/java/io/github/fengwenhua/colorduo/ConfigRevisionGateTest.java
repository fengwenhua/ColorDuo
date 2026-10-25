/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

public final class ConfigRevisionGateTest {
    private static int checks;
    private static void expect(ConfigRevisionGate.Result got, ConfigRevisionGate.Result wanted) {
        checks++;
        if (got != wanted) throw new AssertionError("wanted " + wanted + ", got " + got);
    }
    public static void main(String[] args) {
        ConfigRevisionGate gate = new ConfigRevisionGate();
        expect(gate.offer("one", 1, "a", false), ConfigRevisionGate.Result.UNTRUSTED_EPOCH);
        expect(gate.offer("one", 1, "a", true), ConfigRevisionGate.Result.ACCEPTED);
        expect(gate.offer("one", 1, "a", false), ConfigRevisionGate.Result.DUPLICATE);
        expect(gate.offer("one", 1, "forged", false), ConfigRevisionGate.Result.COLLISION);
        expect(gate.offer("one", 2, "b", false), ConfigRevisionGate.Result.ACCEPTED);
        expect(gate.offer("one", 1, "a", true), ConfigRevisionGate.Result.STALE);
        expect(gate.offer("two", 1, "reset", false), ConfigRevisionGate.Result.UNTRUSTED_EPOCH);
        expect(gate.offer("two", 1, "reset", true), ConfigRevisionGate.Result.ACCEPTED);
        expect(gate.offer("one", 99, "late old epoch", true), ConfigRevisionGate.Result.UNTRUSTED_EPOCH);
        expect(gate.offer("two", 0, "invalid", true), ConfigRevisionGate.Result.INVALID);
        expect(gate.offer(null, 3, "invalid", true), ConfigRevisionGate.Result.INVALID);
        expect(gate.offer("two", Long.MAX_VALUE, "last", true), ConfigRevisionGate.Result.ACCEPTED);
        expect(gate.offer("two", 3, "delayed full reread", true), ConfigRevisionGate.Result.STALE);
        ConfigRevisionGate bounded = new ConfigRevisionGate();
        for (int i = 0; i <= 16; i++) expect(bounded.offer("epoch" + i, 1, "p", true), ConfigRevisionGate.Result.ACCEPTED);
        expect(bounded.offer("epoch17", 1, "p", true), ConfigRevisionGate.Result.UNTRUSTED_EPOCH);
        System.out.println("Config revision gate: " + checks + " checks passed");
    }
}
