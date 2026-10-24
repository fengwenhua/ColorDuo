/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

/** Desktop tests for the routing safety boundary and diagnostic backpressure. */
public final class ColorOsObserverTest {
    private static int checks;

    private static void check(boolean condition, String reason) {
        checks++;
        if (!condition) throw new AssertionError(reason);
    }

    public static void main(String[] args) throws Exception {
        check(RomProfile.select(false, true) == RomProfile.COLOR_OS, "ColorOS must have its own route");
        check(RomProfile.select(true, false) == RomProfile.HYPER_OS, "MIUI route retained");
        check(RomProfile.select(false, false) == RomProfile.UNKNOWN, "Unknown must not hide icons");
        check(RomProfile.select(true, true) == RomProfile.AMBIGUOUS, "Ambiguous must not hide icons");

        ObservationLedger ledger = new ObservationLedger(3, 500);
        check(ledger.record("battery", "level=99", 0) != null, "First sample logged");
        check(ledger.record("battery", "level=99", 700) == null, "Unchanged sample deduplicated");
        check(ledger.record("battery", "level=100", 800) != null, "Changed sample logged");
        check(ledger.record("battery", "level=98", 900) == null, "Burst throttled");
        check(ledger.snapshot().get(1).contains("hits=4 level=98"), "Latest throttled sample retained");
        check(ledger.record("battery", "level=98", 1300) != null, "Pending latest value can be reported");
        ledger.record("wifi", "connected", 1400);
        ledger.record("mobile", "level=3", 1400);
        ledger.record("battery", "level=98", 1500);
        ledger.record("new-host", "bound", 1500);
        check(ledger.snapshot().size() == 4, "Memory is bounded plus one summary row");
        check(ledger.snapshot().get(0).contains("evictions=1"), "Eviction counted");
        check(ledger.snapshot().stream().noneMatch(s -> s.startsWith("wifi ")), "Least recently used entry evicted");
        check(ledger.snapshot().stream().anyMatch(s -> s.startsWith("battery ")), "Active host retained");

        ObservationLedger concurrent = new ObservationLedger(2, 0);
        Thread first = new Thread(() -> { for (int i = 0; i < 2000; i++) concurrent.record("one", "v", i); });
        Thread second = new Thread(() -> { for (int i = 0; i < 2000; i++) concurrent.record("two", "v", i); });
        first.start(); second.start(); first.join(); second.join();
        check(concurrent.snapshot().stream().filter(s -> s.contains("hits=2000")).count() == 2,
                "Concurrent producer counts cannot be lost");
        System.out.println("ColorOS observer: " + checks + " checks passed");
    }
}
