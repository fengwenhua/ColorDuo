/* Copyright (C) 2026 yixing233 and HyperDuo contributors
 * SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

/** Immutable capsule geometry settings. Wire keys and read-time bounds remain unchanged. */
public record CapsuleLayoutSettings(int leftDx, int leftDy, int rightDx, int rightDy, int length) {
    public static final CapsuleLayoutSettings DEFAULT = new CapsuleLayoutSettings(
            Prefs.DEF_CAPSULE_NUDGE, Prefs.DEF_CAPSULE_NUDGE,
            Prefs.DEF_CAPSULE_NUDGE, Prefs.DEF_CAPSULE_NUDGE, Prefs.DEF_CAPSULE_LENGTH);

    @FunctionalInterface interface IntReader { int get(String key, int fallback); }

    /** Both preference and Bundle readers use exactly the same defaults and bounds. */
    static CapsuleLayoutSettings read(IntReader values) {
        return new CapsuleLayoutSettings(
                Prefs.clamp(values.get(Prefs.KEY_CAPSULE_LEFT_DX, Prefs.DEF_CAPSULE_NUDGE),
                        Prefs.MIN_CAPSULE_NUDGE_X, Prefs.MAX_CAPSULE_NUDGE_X),
                Prefs.clamp(values.get(Prefs.KEY_CAPSULE_LEFT_DY, Prefs.DEF_CAPSULE_NUDGE),
                        Prefs.MIN_CAPSULE_NUDGE, Prefs.MAX_CAPSULE_NUDGE),
                Prefs.clamp(values.get(Prefs.KEY_CAPSULE_RIGHT_DX, Prefs.DEF_CAPSULE_NUDGE),
                        Prefs.MIN_CAPSULE_NUDGE_X_RIGHT, Prefs.MAX_CAPSULE_NUDGE_X_RIGHT),
                Prefs.clamp(values.get(Prefs.KEY_CAPSULE_RIGHT_DY, Prefs.DEF_CAPSULE_NUDGE),
                        Prefs.MIN_CAPSULE_NUDGE, Prefs.MAX_CAPSULE_NUDGE),
                Prefs.clamp(values.get(Prefs.KEY_CAPSULE_LENGTH, Prefs.DEF_CAPSULE_LENGTH),
                        Prefs.MIN_CAPSULE_LENGTH, Prefs.MAX_CAPSULE_LENGTH));
    }

    public CapsuleLayoutSettings withLeftDx(int value) {
        return new CapsuleLayoutSettings(value, leftDy, rightDx, rightDy, length);
    }
    public CapsuleLayoutSettings withLeftDy(int value) {
        return new CapsuleLayoutSettings(leftDx, value, rightDx, rightDy, length);
    }
    public CapsuleLayoutSettings withRightDx(int value) {
        return new CapsuleLayoutSettings(leftDx, leftDy, value, rightDy, length);
    }
    public CapsuleLayoutSettings withRightDy(int value) {
        return new CapsuleLayoutSettings(leftDx, leftDy, rightDx, value, length);
    }
    public CapsuleLayoutSettings withLength(int value) {
        return new CapsuleLayoutSettings(leftDx, leftDy, rightDx, rightDy, value);
    }
}
