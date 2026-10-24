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

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateFormat;

import java.util.Calendar;

/**
 * The time of day for the capsule arrangement, and the repaint that keeps it
 * current.
 *
 * <p>Nothing in the module had ever needed to know the time: every other reading
 * arrives as an event - a battery broadcast, a signal level, a tint change - and
 * repaints whatever it changed. A clock has no such event. It is correct exactly
 * until the minute rolls over, so something has to wake on that boundary and
 * invalidate the hosts, or the capsule would sit on whatever minute it was drawn
 * at.
 *
 * <p>Two decisions worth stating:
 *
 * <ul>
 * <li><b>Aligned to the minute boundary, not a flat 60 s.</b> A fixed delay from
 * whenever the module happened to start would leave the displayed minute
 * changing a fraction of a second after the real one, drifting further with every
 * tick. Sleeping until the next boundary keeps the two in step for the life of
 * the process and costs one extra wake-up at start.</li>
 * <li><b>Nothing is formatted unless the capsule is drawing the time.</b> The
 * ticker runs once per process, but each tick asks what is on screen first, so an
 * install on any other style pays one cheap check a minute rather than a format
 * and a repaint.</li>
 * </ul>
 *
 * <p>Copied in shape from {@link WifiPresence} and {@link BatteryPresence}: a
 * static guard so the start is idempotent, work posted to the main looper because
 * the hooked views live there, and a single re-posted runnable rather than a
 * scheduled executor.
 */
final class ClockTicker {

    /**
     * The longest a tick may be late before the time is simply re-read and the
     * next boundary recomputed. Only reached if the process was frozen (doze, a
     * stalled main thread), where chasing the boundary would otherwise queue a
     * burst of catch-up ticks.
     */
    private static final long MAX_LATENESS_MS = 60_000L;

    private static boolean started;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    /**
     * The context the ticker formats with, held for the life of the process so a
     * later settings change can publish the time without walking the view tree.
     * The application context, matching what the samplers keep.
     */
    private static Context sContext;

    private ClockTicker() {}

    /** Idempotent: the first caller starts the ticker, later ones do nothing. */
    static synchronized void start(final Context context) {
        if (started) {
            return;
        }
        started = true;
        final Context app = context.getApplicationContext();
        sContext = (app != null) ? app : context;
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                tick();
            }
        });
    }

    /**
     * Publishes the time now, without waiting for the boundary.
     *
     * <p>Called when the settings change. The ticker only wakes on the minute, so
     * without this a user who switched to the capsule style mid-minute would
     * watch an empty left slot for up to a minute before the first tick filled
     * it - which reads as the feature being broken rather than as the clock not
     * having ticked yet.
     *
     * <p>Cheap and safe on any settings change: it formats nothing unless the
     * clock is actually being drawn, and {@code publish} drops a repeat of the
     * string already on screen.
     */
    static void refreshNow() {
        final Context context = sContext;
        if (context == null || !usesClock()) {
            return;
        }
        publish(format(context));
    }

    /** One tick: publish the time if it is on screen, then sleep to the boundary. */
    private static void tick() {
        long delay = MAX_LATENESS_MS;
        try {
            final Context context = sContext;
            if (context != null && usesClock()) {
                publish(format(context));
            }
            // Recomputed either way: the style can be switched at any moment and
            // the next tick should already be aligned to the boundary.
            delay = millisToNextMinute();
        } catch (Throwable ignored) {
            // keep ticking
        } finally {
            // In a finally, deliberately: a ticker that died on one bad frame
            // would leave the capsule frozen on that minute for the life of the
            // process, which is the one failure this class exists to prevent.
            MAIN.postDelayed(new Runnable() {
                @Override
                public void run() {
                    tick();
                }
            }, Math.max(1L, delay));
        }
    }

    /**
     * Whether anything on screen wants the time.
     *
     * <p>Asks the same rule the clock-hiding hook asks, so the renderer drawing
     * the time and the hook hiding the bar's own copy cannot disagree - if the two
     * used separate conditions, some combination of switches would show two
     * clocks or none.
     */
    private static boolean usesClock() {
        return TrioConfig.appearance().hidesStatusClock();
    }

    /**
     * The time in the format the user's locale asks for.
     *
     * <p>{@code getTimeFormat} rather than a pattern of this module's own: the
     * status bar's clock follows the system setting, and a second clock that
     * disagreed with every other one on the phone would read as a bug.
     */
    private static String format(Context context) {
        return DateFormat.getTimeFormat(context).format(Calendar.getInstance().getTime());
    }

    /** Milliseconds from now to the top of the next minute. */
    private static long millisToNextMinute() {
        final Calendar now = Calendar.getInstance();
        final long elapsed = now.get(Calendar.SECOND) * 1000L + now.get(Calendar.MILLISECOND);
        return 60_000L - elapsed;
    }

    /**
     * Stores the time and repaints, but only when it really changed - the same
     * string can come round twice across a locale or timezone change, and an
     * unconditional invalidate would repaint the status bar every minute on
     * installs that are not even showing a clock.
     */
    private static void publish(String text) {
        if (text == null || text.equals(TrioState.sClockText)) {
            return;
        }
        TrioState.sClockText = text;
        TrioHooks.invalidateHosts();
    }
}
