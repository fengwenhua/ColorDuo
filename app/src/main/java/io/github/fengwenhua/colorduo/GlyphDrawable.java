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

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.view.View;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The trio glyph, painted by the battery icon view itself.
 *
 * <p>MIUI's battery icon - {@code MiuiBatteryMeterIconView} - draws its own
 * artwork in {@code onDraw}: the pre-composed progress bitmap first, then
 * whatever happens to be in {@code mTintBgDrawable}. That last slot is a
 * Drawable the view is willing to call with its own bounds, so the glyph is
 * installed there rather than painted into a window of its own.
 *
 * <p>What the swap buys is that the canvas, its bounds, its clip and its
 * hardware layer stay the platform's. A separate window has none of that: it
 * has to derive its own size from the host, it has to be hidden and shown in
 * step with a bar that is drawn by somebody else, and while it is up the
 * platform reads it as a possible tap-jacking overlay - the LSPosed prompt
 * refusing touches is what that cost. None of it is the glyph's business.
 *
 * <p>{@code TrioRenderer} clears the canvas before it paints, which is also
 * what takes MIUI's own battery out from under the glyph. It is told the glyph
 * owns the whole canvas, because after the swap it does - "owning" here means
 * the last thing drawn into this view, not a surface of our own.
 *
 * <p>The slot does not stay taken. MIUI rewrites {@code mTintBgDrawable} from
 * {@code clearTint()}, from its dark-mode pass, and while resources are being
 * rebuilt, and drops it entirely in between. Every one of those is followed by
 * another draw, and {@link #install} is idempotent, so the next frame takes the
 * slot back.
 *
 * <p>Legacy builds return before drawing the tint slot. Those hosts, and hosts
 * without a writable slot, use the hook's direct draw on every frame instead.
 */
final class GlyphDrawable extends Drawable {

    /** The slot in the battery icon view that this is installed in. */
    private static final String SLOT = "mTintBgDrawable";

    /** The switch that tells the icon view to draw from a Drawable at all. */
    private static final String LEGACY = "mUseLegacyDrawable";

    // Cache negative lookups as well: a missing optional field is a class
    // capability, not something to rediscover and log on every animation frame.
    private static final Map<Class<?>, Field> SLOTS = new HashMap<Class<?>, Field>();
    private static final Map<Class<?>, Field> LEGACIES = new HashMap<Class<?>, Field>();
    private static final Set<Class<?>> MISSING_NOTED = new HashSet<Class<?>>();
    private static final Set<Class<?>> WRITE_NOTED = new HashSet<Class<?>>();
    private static final Set<Class<?>> LEGACY_NOTED = new HashSet<Class<?>>();

    /**
     * The host whose readings the next frame is built from. Held as a field
     * rather than looked up per draw: one host is one drawable, and the lookup
     * would take a lock on the draw path.
     */
    private TrioState state;

    private GlyphDrawable(TrioState state) {
        this.state = state;
    }

    /**
     * Puts the glyph in the battery icon's own drawable slot, if it is not
     * already the one holding it.
     *
     * <p>Answers whether the caller must paint this frame. MIUI's own {@code onDraw}
     * runs before the caller gets a look at the canvas, so on the frame that
     * takes the slot MIUI's battery has already been painted into it and the
     * caller has to paint over it. Later frames need nothing: the drawable is
     * in the slot and MIUI draws it itself. A missing slot or a legacy draw path
     * always needs the direct draw; it must not be confused with an installed slot.
     */
    static boolean install(View host, TrioState state) {
        final Field slot = field(host, true);
        if (slot == null) {
            return true;
        }
        final boolean legacy = noteLegacy(host);
        final Object current = Refl.get(slot, host);
        if (current instanceof GlyphDrawable) {
            ((GlyphDrawable) current).state = state;
            return legacy;
        }
        final GlyphDrawable glyph = new GlyphDrawable(state);
        Refl.set(slot, host, glyph);
        // Read back the write: if it failed the direct path is still required.
        if (Refl.get(slot, host) != glyph && WRITE_NOTED.add(host.getClass())) {
            TrioHooks.log(TrioHooks.LOG_INFO, "glyph: tint slot write failed, using direct draw");
        }
        return true;
    }

    /** Hands the slot back to MIUI, if the glyph is what is holding it. */
    static void uninstall(View host) {
        final Field slot = field(host, false);
        if (slot == null || !(Refl.get(slot, host) instanceof GlyphDrawable)) {
            return;
        }
        // clearTint() rebuilds the slot for whatever state the battery is in
        // right now. Putting back a remembered drawable would put back the
        // artwork of a state the battery has since left.
        Refl.callByName(host, "clearTint");
        // OS4 itself pairs these calls. clearTint resets mDark and installs
        // un-tinted artwork; replay the current theme before native onDraw.
        Refl.callByName(host, "onDarkChangeInternal");
        if (Refl.get(slot, host) instanceof GlyphDrawable) {
            // No clearTint() on this build: then the slot goes empty rather
            // than keep painting a glyph for a module that is switched off.
            Refl.set(slot, host, null);
        }
    }

    /**
     * The slot field, resolved once. Looked up even for the views that only
     * ever ask for it to be handed back, because that is still a field read.
     */
    private static Field field(View host, boolean needed) {
        final Class<?> cls = host.getClass();
        if (!SLOTS.containsKey(cls)) {
            SLOTS.put(cls, Refl.field(cls, SLOT));
        }
        final Field slot = SLOTS.get(cls);
        if (slot == null && needed && MISSING_NOTED.add(cls)) {
            TrioHooks.log(TrioHooks.LOG_INFO, "glyph: " + cls.getName() + " has no " + SLOT);
        }
        return slot;
    }

    /**
     * Says once whether this build draws its battery in a way the tint slot
     * never reaches. The glyph would be on and invisible, which is the one
     * failure that has no other symptom.
     */
    private static boolean noteLegacy(View host) {
        final Class<?> cls = host.getClass();
        if (!LEGACIES.containsKey(cls)) {
            LEGACIES.put(cls, Refl.field(cls, LEGACY));
        }
        final boolean legacy = Refl.getBool(LEGACIES.get(cls), host, false);
        if (legacy && LEGACY_NOTED.add(cls)) {
            TrioHooks.log(TrioHooks.LOG_INFO,
                    "glyph: this build draws the battery from a legacy drawable, "
                            + "using the direct draw path");
        }
        return legacy;
    }

    @Override
    public void draw(Canvas canvas) {
        final int width = getBounds().width();
        final int height = getBounds().height();
        if (width <= 0 || height <= 0) {
            return;
        }
        state.refresh();
        // The renderer clears first - see the class note. The box handed over
        // is the icon view's own, which is the same box the platform measured
        // the battery in.
        TrioRenderer.drawState(canvas, width, height, state, TrioConfig.get());
    }

    @Override
    public void setAlpha(int alpha) {
        // The glyph takes its colour from the row it is drawn in.
    }

    @Override
    public void setColorFilter(ColorFilter filter) {
        // The same: MIUI's tint dispatch is about MIUI's own artwork.
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
