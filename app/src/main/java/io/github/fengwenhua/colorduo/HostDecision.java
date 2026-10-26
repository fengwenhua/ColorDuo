/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

/** Pure decisions over sampled host state. View/Hook code executes effects on the main thread. */
final class HostDecision {
    enum Action { WAIT, RELEASE, PREFLIGHT, MOUNT, UPDATE, COMMIT_LEASES, FIT_OUTSIDE }
    record Decision(Action action, String reason, boolean releaseLeases) {}
    record Gate(boolean hostPresent, boolean nativeUpdating, boolean available,
                boolean awaitLayout, boolean resizing) {}
    record Model(boolean nativeDrawable, int style, boolean popup, int level, int tint,
                 int width, int height) {}
    record Preparation(boolean resizeNeeded, boolean outsideFits, boolean layoutPending) {}
    record Attachment(boolean mounted, boolean nativeOwned, boolean dimensionsTested) {}
    record Presentation(boolean current, boolean configEnabled, boolean layoutReady,
                        boolean attached, boolean shown, boolean planMatches) {}
    record Configuration(boolean enabled, boolean attached, boolean shown, boolean imageOwned,
                         boolean drawn, boolean failed, boolean nativePending, boolean policyVisible,
                         int style, boolean popup) {}
    record ChargePresentation(boolean userLayout, boolean currentPower, boolean powerReady,
                              boolean charging, boolean plugged) {}

    static boolean chargeIcon(ChargePresentation state) {
        // ColorOS may keep its ordinary bolt visible while USB charging is paused or full.
        // A drawn, current powered frame owns that indicator without pretending to be charging.
        return state.userLayout && state.currentPower && state.powerReady && (state.charging || state.plugged);
    }

    static Decision configuration(Configuration state) {
        boolean supported = state.style == 1 || state.style == 3 || state.style == 4
                || (state.style == 2 && state.popup);
        if (state.enabled && state.attached && state.shown && state.imageOwned && state.drawn
                && !state.failed && !state.nativePending && state.policyVisible && supported)
            return waitFor("configuration-handoff", false);
        return release("configuration");
    }

    static Decision gate(Gate state) {
        if (!state.hostPresent) return waitFor("host-missing", false);
        if (state.nativeUpdating) return waitFor("native-style-updating", false);
        if (!state.available) return release("not-ready-or-disabled");
        if (state.awaitLayout) return waitFor("await-native-layout", !state.resizing);
        return next(Action.UPDATE);
    }

    static Decision model(Model state) {
        if (!state.nativeDrawable) return release("missing-native-model");
        boolean style = state.style == 1 || state.style == 3 || state.style == 4
                || (state.style == 2 && state.popup);
        if (!style || state.level < 0 || state.level > 100 || (state.tint >>> 24) == 0
                || state.width <= 0 || state.height <= 0 || state.width > 2048 || state.height > 2048)
            return release("unsupported-style-or-bounds");
        return next(Action.UPDATE);
    }

    static Decision prepare(Preparation state) {
        if (state.resizeNeeded) return release("resize");
        if (!state.outsideFits && state.layoutPending) return waitFor("await-resized-layout", false);
        // Optional readouts must not poison a healthy battery host when the parent
        // grants less space than requested. Refit them to the measured allocation.
        if (!state.outsideFits) return new Decision(Action.FIT_OUTSIDE, "outside-layout-constrained", false);
        return next(Action.UPDATE);
    }

    static Decision attach(Attachment state) {
        if (!state.mounted && !state.nativeOwned) return waitFor("drawable-conflict", false);
        if (!state.dimensionsTested || !state.mounted) return next(Action.PREFLIGHT);
        return next(Action.UPDATE);
    }

    static Decision mount(boolean alreadyMounted) {
        return next(alreadyMounted ? Action.UPDATE : Action.MOUNT);
    }

    static Decision presented(Presentation state) {
        if (!state.current) return waitFor("stale-frame", false);
        if (!state.configEnabled || !state.layoutReady || !state.attached || !state.shown || !state.planMatches)
            return waitFor("await-presentation", false);
        return next(Action.COMMIT_LEASES);
    }

    private static Decision release(String reason) { return new Decision(Action.RELEASE, reason, true); }
    private static Decision waitFor(String reason, boolean release) { return new Decision(Action.WAIT, reason, release); }
    private static Decision next(Action action) { return new Decision(action, "", false); }
    private HostDecision() {}
}
