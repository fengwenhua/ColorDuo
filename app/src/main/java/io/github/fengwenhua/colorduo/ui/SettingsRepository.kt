/*
 * Copyright (C) 2026 yixing233 and HyperDuo contributors
 *
 * This file is part of ColorDuo, released under the GNU General Public
 * License v3.0. See the LICENSE file at the repository root, or
 * <https://www.gnu.org/licenses/gpl-3.0.html>.
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package io.github.fengwenhua.colorduo.ui

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import io.github.fengwenhua.colorduo.ChargeLayout
import androidx.core.content.edit
import io.github.fengwenhua.colorduo.Prefs
import io.github.fengwenhua.colorduo.TrioSettings

/** Immediate preview reads with coalesced, ordered persistence on the settings worker. */
class SettingsRepository @JvmOverloads constructor(
    context: Context,
    private val chargeScene: Int = ChargeLayout.BASE,
    private val store: io.github.fengwenhua.colorduo.BufferedSettings =
        io.github.fengwenhua.colorduo.BufferedSettings.get(context.applicationContext),
) {

    fun forChargeScene(scene: Int) = SettingsRepository(appContext, scene, store)

    private val appContext = context.applicationContext

    /** The display density, needed to migrate the retired raw-pixel size key. */
    private val density: Float
        get() = appContext.resources.displayMetrics.density

    private fun preferences() = store

    /** The current values, as the preview should render them. */
    fun read(): TrioSettings = ChargeLayout.forScene(TrioSettings.from(preferences().snapshot(), density), chargeScene)

    /**
     * The snapshot for a style the user is <em>not</em> currently in.
     *
     * <p>The style tiles need this: each arrangement keeps its own component switches and
     * layout now, so showing the current one under both names would advertise a look that
     * picking that tile does not produce.
     *
     * <p>A pure read of the other arrangement's keys - the stored style is not touched, so
     * nothing observes a transient state and no write is pushed to the hooked process.
     */
    fun readForStyle(style: Int): TrioSettings =
        ChargeLayout.forScene(TrioSettings.from(preferences().snapshot(), density, Prefs.normalizeTrioStyle(style)), chargeScene)

    // ------------------------------------------------------------- appearance

    fun setEnabled(value: Boolean) = writeBoolean(Prefs.KEY_ENABLED, value)
    fun setShowWifi(value: Boolean) = writeComponent(Prefs.KEY_SHOW_WIFI, value)
    fun setShowMobile(value: Boolean) = writeComponent(Prefs.KEY_SHOW_MOBILE, value)
    fun setShowValue(value: Boolean) = writeComponent(Prefs.KEY_SHOW_VALUE, value)
    fun setShowBolt(value: Boolean) = writeComponent(Prefs.KEY_SHOW_BOLT, value)
    fun setDualSim(value: Boolean) = writeComponent(Prefs.KEY_DUAL_SIM, value)
    /** SIM 1's own row of dots, placed as a component - see [Prefs.KEY_SHOW_SIM_1]. */
    fun setShowSim1(value: Boolean) = writeComponent(Prefs.KEY_SHOW_SIM_1, value)
    /** SIM 2's own row of dots, placed as a component - see [Prefs.KEY_SHOW_SIM_2]. */
    fun setShowSim2(value: Boolean) = writeComponent(Prefs.KEY_SHOW_SIM_2, value)
    fun setValueCentred(value: Boolean) = writeComponent(Prefs.KEY_VALUE_CENTRED, value)
    /** 0 = hidden, 1 = inside the ring, 2 = outside it. */
    fun setMobileTypeMode(value: Int) = writeComponent(Prefs.KEY_MOBILE_TYPE_MODE, value)
    /** Stored IDs stay 0 = ring and 2 = capsule; retired ID 1 returns to the ring. */
    fun setTrioStyle(value: Int) = writeInt(Prefs.KEY_TRIO_STYLE, Prefs.normalizeTrioStyle(value))

    fun setNavStyle(value: Int) = writeInt(Prefs.KEY_NAV_STYLE, Prefs.normalizeNavStyle(value))
    fun setNavLabels(value: Int) = writeInt(Prefs.KEY_NAV_LABELS, Prefs.normalizeNavLabels(value))

    /** Component edits follow the selected charging scene; visual sizes keep their shared keys. */
    private fun writeComponent(base: String, value: Any) {
        val current = read()
        val key = Prefs.styledKey(base, current.trioStyle)
        if (chargeScene != ChargeLayout.BASE) {
            val changed = Bundle().apply {
                putInt(Prefs.KEY_TRIO_STYLE, current.trioStyle)
                when (value) {
                    is Boolean -> putBoolean(key, value)
                    is Int -> putInt(key, value)
                }
            }
            applyRingAssignment(TrioSettings.fromBundle(changed, current), layoutEdit = false)
            return
        }
        val write: (SharedPreferences.Editor) -> Unit = { e ->
            when (value) {
                is Boolean -> e.putBoolean(key, value)
                is Int -> e.putInt(key, value)
            }
        }
        preferences().edit() { write(this) }
    }

    /** Publish one layout edit as a single local/remote transaction and one broadcast. */
    fun applyRingAssignment(next: TrioSettings, layoutEdit: Boolean = true) {
        val scene = if (next.editingChargeScene != ChargeLayout.BASE) next.editingChargeScene else chargeScene
        if (scene != ChargeLayout.BASE) {
            val snapshot = next.copy()
            // An explicit placement takes precedence over the legacy charging-centre override.
            if (layoutEdit) snapshot.setChargingShowsValue(false)
            val key = Prefs.styledKey(ChargeLayout.key(scene), next.trioStyle)
            val encoded = ChargeLayout.encode(snapshot)
            preferences().edit() { putString(key, encoded) }
            return
        }
        // The arrangement being written is the snapshot's own, not the stored one: the caller
        // built `next` for a specific style, and a layout written under the wrong key would
        // show up in the other arrangement on the next style switch.
        val style = Prefs.normalizeTrioStyle(next.trioStyle)
        val write: (SharedPreferences.Editor) -> Unit = { e ->
            e.putString(Prefs.styledKey(Prefs.KEY_RING_LAYOUT, style),
                Prefs.encodeLayout(next.ringZones))
            e.putBoolean(Prefs.styledKey(Prefs.KEY_SHOW_WIFI, style), next.content.showWifi())
            e.putBoolean(Prefs.styledKey(Prefs.KEY_SHOW_MOBILE, style), next.content.showMobile())
            e.putBoolean(Prefs.styledKey(Prefs.KEY_SHOW_VALUE, style), next.content.showValue())
            e.putBoolean(Prefs.styledKey(Prefs.KEY_SHOW_BOLT, style), next.content.showBolt())
            e.putBoolean(Prefs.styledKey(Prefs.KEY_SHOW_SIM_1, style), next.content.showSim1())
            e.putBoolean(Prefs.styledKey(Prefs.KEY_SHOW_SIM_2, style), next.content.showSim2())
            e.putInt(Prefs.styledKey(Prefs.KEY_MOBILE_TYPE_MODE, style), next.content.mobileTypeMode())
        }
        val editor = preferences().edit()
        write(editor)
        editor.apply()
    }

    /**
     * Put a piece at the front of a zone's priority list, or take it out of the layout.
     *
     * <p>Mirrors the old "position" control: 1..3 name a ring zone and 0 (or the external
     * type slot) removes the piece. The three ints are still the three zones, which is why
     * the mapping is kept - the ring's editor drives the priority lists directly.
     */
    fun setRingPartPosition(part: io.github.fengwenhua.colorduo.TrioZones.Part, position: Int) {
        val current = read()
        // The choices a caller can pass are the arrangement's own cells, in the order
        // the dropdown lists them. The capsule lists four (top, left, right, bottom)
        // and the ring three, so the mapping from "choice number" to "zone" is read
        // from the same list rather than assumed to be "index - 1". Assuming it was
        // what wrote the capsule's left cell into the bottom zone.
        val slots = if (current.trioStyle == Prefs.STYLE_CAPSULE) {
            listOf(io.github.fengwenhua.colorduo.TrioZones.Zone.TOP,
                io.github.fengwenhua.colorduo.TrioZones.Zone.LEFT,
                io.github.fengwenhua.colorduo.TrioZones.Zone.CENTRE,
                io.github.fengwenhua.colorduo.TrioZones.Zone.BOTTOM)
        } else {
            listOf(io.github.fengwenhua.colorduo.TrioZones.Zone.TOP,
                io.github.fengwenhua.colorduo.TrioZones.Zone.CENTRE,
                io.github.fengwenhua.colorduo.TrioZones.Zone.BOTTOM)
        }
        if (position in 1..slots.size) {
            applyRingAssignment(
                io.github.fengwenhua.colorduo.TrioZones.withPiece(
                    current, slots[position - 1].ordinal, part)
            )
        } else {
            val next = current.copy()
            val id = io.github.fengwenhua.colorduo.TrioZones.encodePart(part)
            next.ringZones = io.github.fengwenhua.colorduo.TrioSettings.copyZones(current.ringZones)
            for (zone in next.ringZones.indices) {
                next.ringZones[zone] = next.ringZones[zone].filter { it != id }.toIntArray()
            }
            if (part == io.github.fengwenhua.colorduo.TrioZones.Part.TYPE) {
                next.setMobileTypeMode(
                    if (position == slots.size + 1) Prefs.MOBILE_TYPE_OUT_RING
                    else Prefs.MOBILE_TYPE_OFF
                )
            }
            applyRingAssignment(next)
        }
    }
    /**
     * Wi-Fi's switch and slot as one decision: {@code 0} turns the arcs off and takes them
     * out of the layout, and a positive number puts them in that choice of the current
     * arrangement's cells.
     *
     * <p>One call rather than two setters, because the pair has to move together: between a
     * "switch off" write and a "remove from layout" write the module would be asked to draw
     * arcs it has no slot for, or hold a slot it will not fill.
     *
     * <p>The choice is read through the arrangement's own cell list rather than used as a
     * zone ordinal. The capsule has four cells in the order top, left, right, bottom while
     * the zones are stored as top, centre, bottom, left, so "third choice" is the right-hand
     * interior for the capsule and the bottom band for the ring - the two are not the same
     * ordinal, and assuming they were is what wrote the capsule's third choice into the
     * bottom band.
     */
    fun setWifiPosition(position: Int) {
        val current = read()
        val slots = if (current.trioStyle == Prefs.STYLE_CAPSULE) {
            listOf(io.github.fengwenhua.colorduo.TrioZones.Zone.TOP,
                io.github.fengwenhua.colorduo.TrioZones.Zone.LEFT,
                io.github.fengwenhua.colorduo.TrioZones.Zone.CENTRE,
                io.github.fengwenhua.colorduo.TrioZones.Zone.BOTTOM)
        } else {
            listOf(io.github.fengwenhua.colorduo.TrioZones.Zone.TOP,
                io.github.fengwenhua.colorduo.TrioZones.Zone.CENTRE,
                io.github.fengwenhua.colorduo.TrioZones.Zone.BOTTOM)
        }
        if (position in 1..slots.size) {
            val next = io.github.fengwenhua.colorduo.TrioZones.withPiece(
                current, slots[position - 1].ordinal,
                io.github.fengwenhua.colorduo.TrioZones.Part.WIFI)
            next.setShowWifi(true)
            applyRingAssignment(next)
        } else {
            val next = current.copy()
            next.setShowWifi(false)
            val id = io.github.fengwenhua.colorduo.TrioZones.encodePart(
                io.github.fengwenhua.colorduo.TrioZones.Part.WIFI)
            next.ringZones = TrioSettings.copyZones(current.ringZones)
            for (zone in next.ringZones.indices) {
                next.ringZones[zone] = next.ringZones[zone].filter { it != id }.toIntArray()
            }
            applyRingAssignment(next)
        }
    }

    /** 0 = the signal readings stay inside the ring, 1 = they go back to the system (环外). */
    fun setSignalMode(value: Int) = writeComponent(Prefs.KEY_SIGNAL_MODE, value.coerceIn(Prefs.MIN_SIGNAL_MODE, Prefs.MAX_SIGNAL_MODE))

    /** Out of ring, draw the module's own reading rather than handing the icon back. */
    fun setStackedSignal(value: Boolean) = writeComponent(Prefs.KEY_STACKED_SIGNAL, value)

    fun setDataSimOnly(value: Boolean) = writeBoolean(Prefs.KEY_DATA_SIM_ONLY, value)

    // ------------------------------------------------------- performance readouts

    /** Charging power in the out-of-ring strip - see [Prefs.KEY_PERF_POWER]. */
    fun setPerfPower(value: Boolean) = writeBoolean(Prefs.KEY_PERF_POWER, value)
    /** Battery temperature in the out-of-ring strip - see [Prefs.KEY_PERF_TEMP]. */
    fun setPerfTemp(value: Boolean) = writeBoolean(Prefs.KEY_PERF_TEMP, value)
    /** Temperature only while charging - see [Prefs.KEY_PERF_TEMP_CHARGING_ONLY]. */
    fun setPerfTempChargingOnly(value: Boolean) =
        writeBoolean(Prefs.KEY_PERF_TEMP_CHARGING_ONLY, value)
    /** Overall size of the two readouts, in percent - see [Prefs.KEY_PERF_SCALE]. */
    fun setPerfScale(value: Int) = writeInt(Prefs.KEY_PERF_SCALE, value)
    /** Weight of the two readouts - see [Prefs.KEY_PERF_WEIGHT]. */
    fun setPerfWeight(value: Int) = writeInt(Prefs.KEY_PERF_WEIGHT, value)

    /** Size of the in-ring charging bolt, in percent - see [Prefs.KEY_BOLT_SCALE]. */
    fun setBoltScale(value: Int) = writeInt(Prefs.KEY_BOLT_SCALE, value)
    /** Wi-Fi arcs' size, in percent; see [Prefs.KEY_WIFI_SCALE]. */
    fun setWifiScale(value: Int) = writeInt(Prefs.KEY_WIFI_SCALE, value)
    /** Drop the network type while Wi-Fi is up; see [Prefs.KEY_TYPE_HIDE_ON_WIFI]. */
    fun setTypeHideOnWifi(value: Boolean) =
        writeBoolean(Prefs.KEY_TYPE_HIDE_ON_WIFI, value)
    /** While charging the ring centre shows the percentage; see [Prefs.KEY_CHARGING_SHOWS_VALUE]. */
    fun setChargingShowsValue(value: Boolean) =
        writeComponent(Prefs.KEY_CHARGING_SHOWS_VALUE, value)

    /**
     * Size of the ring's level-dot rows, in percent - see [Prefs.KEY_DOT_SCALE].
     *
     * <p>One setter for the top and the bottom rows: they are one piece of ink, and the
     * settings screen offers one row for them.
     */
    fun setDotScale(value: Int) = writeInt(Prefs.KEY_DOT_SCALE, value)

    /**
     * The out-of-ring order, nearest the battery first - see [Prefs.KEY_OUT_ORDER].
     *
     * <p>One write for the whole arrangement rather than a "move this piece" verb: the strip
     * always has exactly three positions, so a swap is expressible as the resulting order
     * and there is no intermediate state another reader could observe.
     */
    fun setOutOrder(order: IntArray) =
        writeString(Prefs.KEY_OUT_ORDER, Prefs.encodeOutOrder(order))
    // ------------------------------------------------------------------- font

    /**
     * Path of the installed font, or the empty string for the system face.
     *
     * See [Prefs.KEY_FONT_PATH]. The caller has already copied the file to a
     * location the hooked process can read; this only records where.
     */
    @JvmOverloads
    fun setFontPath(value: String, remoteName: String = "", revision: String = java.util.UUID.randomUUID().toString()) {
        synchronized(io.github.fengwenhua.colorduo.ColorOsFontPublisher::class.java) {
            val editor = preferences().edit().putString(Prefs.KEY_FONT_PATH, value)
            io.github.fengwenhua.colorduo.ColorOsFontPublisher.bind(editor, value, revision, remoteName)
            editor.apply()
        }
    }

    // ----------------------------------------------------------------- colours

    fun setRoleColors(value: Boolean) = writeBoolean(Prefs.KEY_ROLE_COLORS, value)
    fun setColorCriticalOnDark(value: Int) = writeInt(Prefs.KEY_COLOR_CRITICAL_ON_DARK, value)
    fun setColorCriticalOnLight(value: Int) = writeInt(Prefs.KEY_COLOR_CRITICAL_ON_LIGHT, value)
    fun setColorChargingOnDark(value: Int) = writeInt(Prefs.KEY_COLOR_CHARGING_ON_DARK, value)
    fun setColorChargingOnLight(value: Int) = writeInt(Prefs.KEY_COLOR_CHARGING_ON_LIGHT, value)
    fun setColorLowOnDark(value: Int) = writeInt(Prefs.KEY_COLOR_LOW_ON_DARK, value)
    fun setColorLowOnLight(value: Int) = writeInt(Prefs.KEY_COLOR_LOW_ON_LIGHT, value)

    /** Restores all six role colours to their defaults in one write. */
    fun resetRoleColors() {
        preferences().edit() {
            putInt(Prefs.KEY_COLOR_CRITICAL_ON_DARK, Prefs.DEF_COLOR_CRITICAL_ON_DARK)
            putInt(Prefs.KEY_COLOR_CRITICAL_ON_LIGHT, Prefs.DEF_COLOR_CRITICAL_ON_LIGHT)
            putInt(Prefs.KEY_COLOR_CHARGING_ON_DARK, Prefs.DEF_COLOR_CHARGING_ON_DARK)
            putInt(Prefs.KEY_COLOR_CHARGING_ON_LIGHT, Prefs.DEF_COLOR_CHARGING_ON_LIGHT)
            putInt(Prefs.KEY_COLOR_LOW_ON_DARK, Prefs.DEF_COLOR_LOW_ON_DARK)
            putInt(Prefs.KEY_COLOR_LOW_ON_LIGHT, Prefs.DEF_COLOR_LOW_ON_LIGHT)
        }
    }

    // ------------------------------------------------------------------ sizing

    fun setLowThreshold(value: Int) = writeInt(Prefs.KEY_LOW_THRESHOLD, value)
    fun setRingStroke(value: Int) = writeInt(Prefs.KEY_RING_STROKE, value)
    fun setArcStroke(value: Int) = writeInt(Prefs.KEY_ARC_STROKE, value)
    fun setValueSize(value: Int) = writeInt(Prefs.KEY_VALUE_SIZE, value)
    fun setValueWeight(value: Int) = writeInt(Prefs.KEY_VALUE_WEIGHT, value)
    fun setClockSize(value: Int) = writeInt(Prefs.KEY_CLOCK_SIZE, value)
    fun setClockWeight(value: Int) = writeInt(Prefs.KEY_CLOCK_WEIGHT, value)
    fun setTypeSize(value: Int) = writeInt(Prefs.KEY_TYPE_SIZE, value)
    fun setOutTypeSize(value: Int) = writeInt(Prefs.KEY_OUT_TYPE_SIZE_DP, value)
    fun setOutSignalSize(value: Int) = writeInt(Prefs.KEY_OUT_SIGNAL_SIZE, value)
    fun setOutTypeMarginLeft(value: Int) = writeInt(Prefs.KEY_OUT_TYPE_MARGIN_LEFT, value)
    fun setOutTypeMarginRight(value: Int) = writeInt(Prefs.KEY_OUT_TYPE_MARGIN_RIGHT, value)
    fun setOutSignalMargin(value: Int) = writeInt(Prefs.KEY_OUT_SIGNAL_MARGIN, value)
    fun setTypeSuffixScale(value: Int) = writeInt(Prefs.KEY_TYPE_SUFFIX_SCALE, value)
    fun setTypeWeight(value: Int) = writeInt(Prefs.KEY_TYPE_WEIGHT, value)
    fun setTrackAlpha(value: Int) = writeInt(Prefs.KEY_TRACK_ALPHA, value)

    /** Overall size of the trio glyph, in percent - see [Prefs.KEY_GLYPH_SCALE]. */
    fun setGlyphScale(value: Int) = writeInt(Prefs.KEY_GLYPH_SCALE, value)

    /** Interior cell nudges; see [Prefs.KEY_CAPSULE_LEFT_DX]. */
    fun setCapsuleLeftDx(value: Int) = writeInt(Prefs.KEY_CAPSULE_LEFT_DX, value)
    fun setCapsuleLeftDy(value: Int) = writeInt(Prefs.KEY_CAPSULE_LEFT_DY, value)
    fun setCapsuleRightDx(value: Int) = writeInt(Prefs.KEY_CAPSULE_RIGHT_DX, value)
    fun setCapsuleRightDy(value: Int) = writeInt(Prefs.KEY_CAPSULE_RIGHT_DY, value)
    /** Extra straight-run length; see [Prefs.KEY_CAPSULE_LENGTH]. */
    fun setCapsuleLength(value: Int) = writeInt(Prefs.KEY_CAPSULE_LENGTH, value)

    // ---------------------------------------------------------------- advanced

    fun setDebugLog(value: Boolean) = writeBoolean(Prefs.KEY_DEBUG_LOG, value)

    /** Replays the whole snapshot, including edits made while the service was absent. */
    fun syncAllToFramework() = preferences().sync()
    fun flushPending() = preferences().flush()
    private fun writeBoolean(key: String, value: Boolean) {
        preferences().edit { putBoolean(key, value) }
    }
    private fun writeInt(key: String, value: Int) = preferences().putInt(key, value)
    private fun writeString(key: String, value: String) {
        preferences().edit { putString(key, value) }
    }
}
