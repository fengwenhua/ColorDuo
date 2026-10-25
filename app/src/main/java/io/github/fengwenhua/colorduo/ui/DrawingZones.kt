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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.fengwenhua.colorduo.R
import io.github.fengwenhua.colorduo.TrioAppearance
import io.github.fengwenhua.colorduo.TrioSettings
import io.github.fengwenhua.colorduo.TrioZones
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronUp
import com.composables.icons.lucide.CircleDot
import com.composables.icons.lucide.Lock
import com.composables.icons.lucide.X
import top.yukonga.miuix.kmp.basic.IconButton
import androidx.compose.ui.graphics.vector.ImageVector
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Outline inks for the three zones.
 *
 * <p>Distinct hues rather than one colour, so the guide beside the glyph and the
 * outline over it identify a third by colour as well as by name. Drawn at a
 * fraction of full alpha because the outline sits over the glyph and must not
 * compete with its ink.
 */
private val ZoneInkTop = Color(0x8093C5FD)
private val ZoneInkCentre = Color(0x80A6E3A1)
private val ZoneInkBottom = Color(0x80F9E2AF)

/** Last placement, so a runtime override can be explained beside the preview. */
internal data class ZoneSelection(val zone: TrioZones.Zone, val part: TrioZones.Part)

/** Explain the saved component using the same runtime frame as the visible glyph. */
@Composable
internal fun PlacementFeedback(
    selection: ZoneSelection,
    appearance: TrioAppearance,
    scene: PreviewScene,
    sims: Int,
) {
    val actual = TrioZones.occupant(
        selection.zone, appearance, scene.level, scene.charging,
        scene.wifiLevel, PREVIEW_TYPE, sims,
    )
    if (actual == selection.part) return

    val messageRes = when {

        scene.charging && selection.part != TrioZones.Part.BOLT -> R.string.drawing_feedback_charging
        selection.part == TrioZones.Part.BOLT && !scene.charging -> R.string.drawing_feedback_bolt
        selection.part == TrioZones.Part.WIFI && scene.wifiLevel < 0 -> R.string.drawing_feedback_wifi
        else -> R.string.drawing_feedback_other
    }
    Text(
        text = stringResource(
            messageRes,
            stringResource(occupantLabelRes(selection.part)),
            stringResource(zoneLabelRes(selection.zone, appearance)),
            stringResource(occupantLabelRes(actual)),
        ),
        style = MiuixTheme.textStyles.footnote1,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        modifier = Modifier.padding(top = 10.dp),
    )
}

/**
 * Writes the switch combination already solved for the selected zone and component.
 *
 * <p>The combination is solved by [TrioZones.assign] against the real arbitration
 * rule, so nothing here decides which switches to move. That is deliberate: this
 * page asks for an outcome ("put the number in the middle") and the renderer decides
 * what that costs, which is the only arrangement that cannot drift.
 *
 * <p>Only the switches that differ are written, and they are written through the same
 * repository setters the rest of the screen uses - so the local commit, the push to
 * the hooked process and the broadcast all happen exactly once per changed switch.
 */
internal fun applyZoneAssignment(
    next: TrioSettings,
    repository: SettingsRepository,
    settings: TrioSettings,
) {
    // The slot-driven arrangements write the whole layout in one transaction. The capsule
    // joined the ring here once its two dot rows became placements: its bands now come from
    // ringZones too, so pushing only the switches would leave the rows un-moveable - which is
    // the defect this path used to have, where the openings showed dots no setting could
    // change.
    if (TrioZones.hasSlots(next)) {
        repository.applyRingAssignment(next)
        return
    }
    if (next.showWifi != settings.showWifi) repository.setShowWifi(next.showWifi)
    if (next.showMobile != settings.showMobile) repository.setShowMobile(next.showMobile)
    if (next.showValue != settings.showValue) repository.setShowValue(next.showValue)
    if (next.showBolt != settings.showBolt) repository.setShowBolt(next.showBolt)
    if (next.dualSim != settings.dualSim) repository.setDualSim(next.dualSim)
    if (next.valueCentred != settings.valueCentred) {
        repository.setValueCentred(next.valueCentred)
    }
    if (next.mobileTypeMode != settings.mobileTypeMode) {
        repository.setMobileTypeMode(next.mobileTypeMode)
    }
}

/**
 * The list of pieces that can be put in one zone.
 *
 * <p>Every entry comes from [TrioZones.choices], which answers by asking the solver
 * whether each piece is placeable - so the menu and the solver cannot disagree. A
 * piece the zone cannot hold at all is still listed, greyed out with the reason,
 * rather than hidden: a missing entry leaves the user wondering whether they opened
 * the wrong list, where a disabled one tells them why.
 *
 * <p>"Nothing here" is the first entry, because emptying a zone is a normal thing to
 * want and burying it at the bottom would push it out of sight on a long list.
 */
@Composable
internal fun ZonePickerDialog(
    zone: TrioZones.Zone,
    settings: TrioSettings,
    appearance: TrioAppearance,
    scene: PreviewScene,
    sims: Int,
    onChoose: (TrioZones.Part?) -> Unit,
    onEdit: (TrioZones.Part) -> Unit,
    onDismiss: () -> Unit,
) {
    // Asked of the frame the user is looking at, not of a fixed one: the title says
    // what is in the zone now, and the bolt only exists while charging - so a
    // hard-coded frame would have the title name a piece the user cannot see.
    val current = TrioZones.occupant(
        zone, appearance, scene.level, scene.charging,
        scene.wifiLevel, PREVIEW_TYPE, sims,
    )
    val solvable = TrioZones.placementChoices(zone, settings, sims)
    // This third's saved list, which is what "already here" and "elsewhere" are judged
    // against. Read from the appearance rather than from settings so it is the same list
    // the preview is drawing from.
    val chain = appearance.ringZones[zone.ordinal]
    val canEmpty = TrioZones.place(zone, null, settings, sims) != null

    // Taking a piece out of another third is the one tap here that is not reversible by
    // tapping again: the row it came from is left short, and the user may not have been
    // looking at that third at all. So that tap asks first, and the question names both
    // thirds. Everything else - placing a piece that is nowhere, promoting one already
    // in this list, emptying this list - stays a single tap, because it either changes
    // nothing elsewhere or is the thing the user opened the sheet to do.
    var confirmingMove by remember { mutableStateOf<ZoneMove?>(null) }

    // A bottom sheet, the same container the component settings open in. Picking what goes
    // in a zone and sizing it are two answers about one component, and they used to arrive
    // in two different shapes - a centred dialog for the first, a sheet for the second - which
    // made the long-press shortcut on a picker row feel like it left the screen it was on.
    // The title carries the zone and what is in it now, as before.
    // The gesture bar overlays the bottom of the window, so the last row has to clear it.
    // The component sheet measures the same inset the same way; forgetting it here put the
    // list flush against the screen edge, under the home indicator.
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    OverlayBottomSheet(
        show = true,
        title = stringResource(
            R.string.drawing_pick_title,
            stringResource(zoneLabelRes(zone, appearance)),
            stringResource(occupantLabelRes(current)),
        ),
        onDismissRequest = onDismiss,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = bottomInset)) {
            // Same bounce as the component sheet: a bare verticalScroll has none, so the list
            // stopped dead at its ends instead of springing back.
            Column(modifier = Modifier.weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 4.dp)) {
                // Emptying first: it is a normal request, and putting it last would push
                // it off the bottom of a long list.
                PickerRow(
                    title = stringResource(R.string.drawing_occupant_none),
                    summary = stringResource(if (canEmpty) R.string.drawing_pick_none_summary else R.string.drawing_clock_fixed),
                    selected = current == null,
                    enabled = canEmpty,
                    onClick = { if (canEmpty) onChoose(null) },
                    onLongClick = null,
                )

                // placeableParts, not Part.entries: the aggregate meter is retired, so
                // listing it here would offer a piece that would immediately be reported
                // as un-placeable.
                TrioZones.placeableParts().forEach { part ->
                    val canPlace = solvable.contains(part)
                    val id = TrioZones.encodePart(part)
                    // Three questions, drawn differently:
                    //
                    //   current   - being drawn in this third right now;
                    //   inZone    - already in THIS third's list, just not the one winning;
                    //   elsewhere - placed in a DIFFERENT third. Tapping moves it here and
                    //               leaves that other third short, so the row says so first
                    //               and the tap is confirmed before it happens.
                    val inZone = chain.contains(id)
                    val elsewhere = if (inZone) null else TrioZones.Zone.entries.firstOrNull { z ->
                        z != zone && appearance.ringZones[z.ordinal].contains(id)
                    }
                    // A refusal has two shapes here, and they are different sentences. The
                    // ring's middle does not hold a signal row at all - a rule about the
                    // band, which no switch can change (see TrioZones.ringAccepts) - while
                    // anything else the solver cannot place is a frame that does not exist
                    // yet. Saying "the middle does not fit this component" for the first
                    // would leave the user hunting for a setting that would fix it.
                    val reason = when {
                        canPlace -> null
                        !TrioZones.accepts(zone, part, settings) -> stringResource(
                            R.string.drawing_pick_signal_in_centre,
                            stringResource(occupantLabelRes(part)),
                        )
                        else -> stringResource(
                            R.string.drawing_pick_impossible,
                            stringResource(zoneLabelRes(zone, appearance)),
                        )
                    }
                    // Only STATE, never what the component does. It used to fall through to
                    // the component description, so one column carried four different kinds
                    // of sentence - "showing now", "promote to first", "moving from below",
                    // and a paragraph about what Wi-Fi arcs are - and the reader had to work
                    // out which kind each line was before it meant anything.
                    // Only a REFUSAL gets a sentence. The rest of the states are already said
                    // twice - a word beside the title, a mark at the end - and adding a line
                    // under them made the list read as four kinds of message stacked.
                    val summary = reason
                    PickerRow(
                        title = stringResource(occupantLabelRes(part)),
                        summary = summary,
                        selected = current == part,
                        inZone = inZone,
                        elsewhere = elsewhere != null,
                        enabled = canPlace,
                        onClick = {
                            if (canPlace) {
                                if (elsewhere != null) {
                                    confirmingMove = ZoneMove(part, elsewhere)
                                } else {
                                    onChoose(part)
                                }
                            }
                        },
                        onLongClick = if (canPlace) {
                            { onEdit(part) }
                        } else {
                            null
                        },
                    )
                }

            }
            // No close button. This is a bottom sheet: it dismisses by swipe and by tapping
            // the scrim, and a full-width primary-coloured "Close" under a list of choices
            // reads as the thing to do rather than as the way out. The component sheet has
            // never had one either, and the two are the same kind of surface now.
        }
    }

    confirmingMove?.let { move ->
        OverlayDialog(
            show = true,
            title = stringResource(R.string.drawing_move_title),
            summary = stringResource(
                R.string.drawing_move_summary,
                stringResource(occupantLabelRes(move.part)),
                stringResource(zoneLabelRes(move.from, appearance)),
            ),
            onDismissRequest = { confirmingMove = null },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    text = stringResource(R.string.cancel),
                    onClick = { confirmingMove = null },
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = {
                        confirmingMove = null
                        onChoose(move.part)
                    },
                    colors = ButtonDefaults.buttonColorsPrimary(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = stringResource(R.string.drawing_move_confirm))
                }
            }
        }
    }
}

/** A pending "take this piece out of another third" request, named well enough to ask about. */
private class ZoneMove(val part: TrioZones.Part, val from: TrioZones.Zone)

/** One entry in the picker. */
@Composable
private fun PickerRow(
    title: String,
    summary: String?,
    selected: Boolean,
    inZone: Boolean = false,
    elsewhere: Boolean = false,
    enabled: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
) {
    val content = if (enabled) {
        MiuixTheme.colorScheme.onSurface
    } else {
        MiuixTheme.colorScheme.onSurfaceVariantSummary
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                when {
                    // Four states, four looks, because each answers a different question:
                    //
                    //   selected  - being drawn here now       (strong tint + check)
                    //   inZone    - already in this list       (quiet tint + tag)
                    //   elsewhere - placed in ANOTHER third    (faint tint + tag)
                    //   none      - not placed anywhere        (plain row)
                    //
                    // One highlight for "present" and "drawn" would claim every listed
                    // piece is on screen; no mark for "elsewhere" would hide that tapping
                    // it takes the piece away from another third.
                    selected -> MiuixTheme.colorScheme.primaryContainer
                    inZone -> MiuixTheme.colorScheme.surfaceContainerHigh
                    elsewhere -> MiuixTheme.colorScheme.surfaceContainer
                    else -> Color.Transparent
                },
            )
            .semantics { this.selected = selected }
            .combinedClickable(enabled = enabled, role = Role.RadioButton,
                onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MiuixTheme.textStyles.body1,
                    color = if (selected) MiuixTheme.colorScheme.onPrimaryContainer else content,
                )
                // A word rather than only a tint: this state has to survive a colour the
                // user cannot distinguish, and it decides whether a tap adds a second
                // entry, promotes the existing one, or MOVES the piece out of another
                // third. "已在环内" and "在别处" are deliberately different words.
                when {
                    inZone && !selected -> Text(
                        text = stringResource(R.string.drawing_pick_in_zone),
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                    elsewhere -> Text(
                        text = stringResource(R.string.drawing_pick_elsewhere),
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            // No second line. The state is already written beside the title as a word and
            // marked at the end of the row; a subtitle repeating it made one fact appear
            // three times, and when it repeated a REFUSAL it said the same sentence twice
            // in two different places.
            //
            // What is left is the one case a tag cannot carry: a frame that cannot hold the
            // piece at all, where the reason is not a state but an obstacle.
            if (summary != null) {
                Text(
                    text = summary,
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
        when {
            // Three marks, not three tints: the end of the row has to say which kind of
            // "present" this is even for a reader who cannot tell the tints apart, and it
            // is the only cue for why tapping takes the piece AWAY from another third.
            //
            //   ✓  drawn here right now  - a check, the universal "this is it";
            //   lock  held by another third - the piece is taken, and tapping steals it;
            //   dot   in this third's list, just not the one winning - present, not taken.
            selected -> Icon(
                imageVector = Lucide.Check,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(18.dp),
            )
            inZone -> Icon(
                imageVector = Lucide.CircleDot,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            elsewhere -> Icon(
                imageVector = Lucide.Lock,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** The page-tab handler's "remove this entry" delta; anything else moves by it. */
internal const val REMOVE = 0

/**
 * One entry of a zone's priority list: a standard radio preference with reorder controls.
 *
 * <p>The first row is marked selected because it is the one that WINS the third; [isDrawing]
 * marks whether it is the one actually on screen this frame. Those differ when the head has
 * nothing to draw - the fallback behind it is drawn while the head stays the chosen one.
 *
 * <p>Order moves with the two arrows rather than by dragging. That is not a compromise: the
 * library has no sortable list (checked across `miuix-ui`, `miuix-preference` and
 * `miuix-icons`), and arrows sit exactly where a thumb already is, work one-handed, and stay
 * reachable for anyone who cannot drag.
 */
@Composable
internal fun PriorityPreferenceRow(
    index: Int,
    part: TrioZones.Part,
    lastIndex: Int,
    isDrawing: Boolean,
    onMove: (Int, Int) -> Unit,
    onRemove: () -> Unit,
    onEdit: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .weight(1f)
                // The library row offers onClick and nothing else, so the long press goes on
                // this wrapper instead of changing the library.
                //
                // detectTapGestures would not do: the row IS a clickable, so it consumes the
                // press before an ancestor sees it, and that helper only watches unconsumed
                // input. Waiting on the raw stream instead is what makes the gesture fire, and
                // since nothing here consumes anything the row keeps every event it had - its
                // own tap still lands, and the arrow buttons beside it are outside this Box.
                .pointerInput(part, onEdit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        // The block returns normally when the gesture ENDS (finger up, or a
                        // drag past touch slop) and is cancelled by the timeout when it does
                        // not. So only a null result means "held long enough" - returning from
                        // the block is the opposite answer. Getting that backwards would open
                        // the sheet on every ordinary tap.
                        val endedEarly = withTimeoutOrNull(
                                viewConfiguration.longPressTimeoutMillis) {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id }
                                    ?: continue
                                if (!change.pressed ||
                                    (change.position - down.position).getDistance() >
                                    viewConfiguration.touchSlop) {
                                    break
                                }
                            }
                            true
                        }
                        if (endedEarly == null) onEdit()
                    }
                },
        ) {
            RadioButtonPreference(
                title = stringResource(occupantLabelRes(part)),
                // Where this row sits in the chain, and - when it is the head - whether it
                // is the one actually visible. Those differ when the head has nothing to
                // draw on this frame and the fallback behind it takes over.
                summary = when {
                    index == 0 && isDrawing -> stringResource(R.string.drawing_priority_showing)
                    index == 0 -> stringResource(R.string.drawing_priority_first, index + 1)
                    else -> stringResource(R.string.drawing_priority_fallback, index + 1)
                },
                selected = index == 0,
                insideMargin = SettingsItemMargin,
                onClick = {},
            )
        }
        Row(
            modifier = Modifier.padding(end = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OrderButton(
                icon = Lucide.ChevronUp,
                description = R.string.drawing_priority_move_up,
                enabled = index > 0,
            ) { onMove(index, -1) }
            OrderButton(
                icon = Lucide.ChevronDown,
                description = R.string.drawing_priority_move_down,
                enabled = index < lastIndex,
            ) { onMove(index, 1) }
            OrderButton(
                icon = Lucide.X,
                description = R.string.drawing_priority_remove,
                enabled = true,
            ) { onRemove() }
        }
    }
}

/**
 * One of the three small controls beside a priority row.
 *
 * <p>Disabled rather than hidden at the ends of the list: a row that loses its up arrow at the
 * top would look like a different row, and the user is clicking by position.
 */
@Composable
internal fun OrderButton(
    icon: ImageVector,
    description: Int,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(36.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = stringResource(description),
            tint = if (enabled) MiuixTheme.colorScheme.onSurfaceVariantSummary
                else MiuixTheme.colorScheme.outline,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** The short name for a piece, for the zone rows and the picker. */
internal fun occupantLabelRes(part: TrioZones.Part?): Int = when (part) {
    TrioZones.Part.WIFI -> R.string.drawing_occupant_wifi
    TrioZones.Part.MOBILE -> R.string.drawing_occupant_mobile
    TrioZones.Part.VALUE -> R.string.drawing_occupant_value
    TrioZones.Part.BOLT -> R.string.drawing_occupant_bolt
    TrioZones.Part.TYPE -> R.string.drawing_occupant_type
    TrioZones.Part.SIM_1 -> R.string.drawing_occupant_sim_1
    TrioZones.Part.SIM_2 -> R.string.drawing_occupant_sim_2
    TrioZones.Part.CLOCK -> R.string.drawing_occupant_clock
    null -> R.string.drawing_occupant_none
}

/**
 * The zone's own name, for the picker's title and its refusals.
 *
 * <p>One name set for every style: top, middle, bottom. The three arrangements differ in
 * their shape, not in the question the user is asking - "where on the icon does this go" -
 * so giving the capsule its own vocabulary ("top opening", "inside") said the same thing
 * less directly and implied a difference that does not exist.
 *
 * <p>The `appearance` parameter is kept because callers already have it to hand and a future
 * style that genuinely needs a different word should change it here rather than at six call
 * sites; nothing in this function reads it today.
 */
internal fun zoneLabelRes(zone: TrioZones.Zone, a: TrioAppearance): Int = when (zone) {
    TrioZones.Zone.TOP -> R.string.drawing_hint_top
    // In the capsule the interior is two cells side by side, so its right half is
    // the RIGHT cell rather than a centre. The ring and the rectangle keep 涓儴: for
    // them the interior really is the middle, and a single cell.
    TrioZones.Zone.CENTRE -> if (a.capsule) R.string.drawing_hint_right
        else R.string.drawing_hint_centre
    TrioZones.Zone.BOTTOM -> R.string.drawing_hint_bottom
    TrioZones.Zone.LEFT -> R.string.drawing_hint_left
}

/** The zones this arrangement shows, in tab order. All three styles have the same three. */
/**
 * The zones this arrangement shows, in tab order.
 *
 * <p>The capsule has four cells - the two openings, its right-hand interior and its
 * left-hand interior - so it shows four. The ring and the rectangle have three, and
 * LEFT is not one of them: their interiors are single cells.
 */
internal fun zonesFor(a: TrioAppearance): List<TrioZones.Zone> =
    if (a.capsule) {
        // Four cells, clockwise from the top: the two openings and the interior
        // split into a left and a right half. Spatially ordered rather than
        // enum-ordered, so the strip reads the way the glyph is laid out.
        listOf(TrioZones.Zone.TOP, TrioZones.Zone.LEFT, TrioZones.Zone.CENTRE,
                TrioZones.Zone.BOTTOM)
    } else {
        listOf(TrioZones.Zone.TOP, TrioZones.Zone.CENTRE, TrioZones.Zone.BOTTOM)
    }

/** One line saying what a piece is, for the picker. */
private fun partSummaryRes(part: TrioZones.Part): Int = when (part) {
    TrioZones.Part.WIFI -> R.string.show_wifi_summary
    TrioZones.Part.MOBILE -> R.string.show_mobile_summary
    TrioZones.Part.VALUE -> R.string.show_value_summary
    TrioZones.Part.BOLT -> R.string.show_bolt_summary
    TrioZones.Part.TYPE -> R.string.show_mobile_type_summary
    TrioZones.Part.SIM_1 -> R.string.show_sim_1_summary
    TrioZones.Part.SIM_2 -> R.string.show_sim_2_summary
    TrioZones.Part.CLOCK -> R.string.clock_size_summary
}
