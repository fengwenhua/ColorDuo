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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.fengwenhua.colorduo.Prefs
import io.github.fengwenhua.colorduo.R
import io.github.fengwenhua.colorduo.TrioAppearance
import io.github.fengwenhua.colorduo.TrioSettings
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Icon
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.ChevronLeft
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Info
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * The three out-of-ring pieces, each as a live sample.
 *
 * <p>A cell shows "hidden" only when the piece <em>cannot</em> be previewed at all - the
 * network type when it is set to draw inside the ring. A switch that is merely off is not
 * a reason to blank the cell: these samples exist so the size and weight rows have
 * something to act on, and a user who has just switched a reading off still needs to see
 * what it will look like before switching it back on. The two readouts already say so
 * themselves, in [DrawingReadoutSample]'s own empty state.
 */
/**
 * The three out-of-ring pieces, positioned the way the status bar actually lays them out.
 *
 * <p><b>The row runs left to right and the RIGHTmost tile is the one next to the battery.</b>
 * That is not a cosmetic choice: {@code layoutOutRingStrip} measures every slot from the
 * battery outwards, so the stored order's first entry is the piece nearest the battery and
 * therefore the rightmost one on screen in LTR. Drawing the list left to right in stored
 * order - which is what this card first did - showed the arrangement mirrored, so the arrows
 * moved a piece the opposite way from the tile the user pressed.
 *
 * <p>The stored array stays in "nearest the battery first" order, because that is what the
 * layout consumes. Only the <em>rendering</em> is reversed, and only here: making the storage
 * order screen order would push the mirror image into every other reader.
 *
 * <p><b>The tiles are ordered, and that is the control.</b> Showing the samples in the shipped
 * order would make the setting invisible: the user would move a piece, read "first", and still
 * be looking at the same three tiles.
 *
 * <p>Which piece survives a narrow row is separate and deliberately not offered here: that is
 * a judgement about what a reading is worth ([Prefs.OUT_PRIORITY]), not a preference.
 */
@Composable
internal fun DrawingOutsideParts(
    settings: TrioSettings, scene: PreviewScene, dark: Boolean,
    onEdit: (TrioSettingsPart) -> Unit, onReorder: (IntArray) -> Unit,
) {
    val a = TrioAppearance.of(settings)
    val order = settings.outOrder
    // Screen order: outermost (furthest from the battery) on the left, nearest on the right.
    val onScreen = order.reversedArray()
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            onScreen.forEachIndexed { screenIndex, slot ->
                val part = trioSettingsPartFor(slot) ?: return@forEachIndexed
                // The stored index this tile occupies. Moving "towards the battery" means a
                // LOWER stored index, which is one step to the RIGHT on screen.
                val stored = order.size - 1 - screenIndex
                Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                    .background(MiuixTheme.colorScheme.surfaceContainerHigh)
                    .clickable(role = Role.Button) { onEdit(part) }.padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    val previewable = when (part) {
                        // The type has exactly one reason to be unpaintable here: it is set to
                        // draw inside the ring, so there is no out-of-ring label to show.
                        TrioSettingsPart.Type -> a.typeOutOfRing
                        // Both of these can always be drawn as a sample.
                        else -> true
                    }
                    if (!previewable) {
                        Box(Modifier.fillMaxWidth().height(58.dp), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.drawing_preview_hidden), style = MiuixTheme.textStyles.footnote2,
                                textAlign = TextAlign.Center)
                        }
                    } else if (part == TrioSettingsPart.Readouts) {
                        DrawingReadoutSample(settings, dark, Modifier.fillMaxWidth().height(58.dp))
                    } else {
                        DrawingSmallPreview(settings, scene, dark, Modifier.fillMaxWidth().height(58.dp),
                            outType = part == TrioSettingsPart.Type, outSignal = part == TrioSettingsPart.Mobile)
                    }
                    Text(stringResource(drawingPartLabel(part)), style = MiuixTheme.textStyles.footnote2,
                        textAlign = TextAlign.Center)
                    OutOrderArrows(
                        // Right = towards the battery, left = outwards, matching the tiles.
                        canMoveRight = stored > 0,
                        canMoveLeft = stored < order.size - 1,
                        onMoveRight = { onReorder(swapped(order, stored, stored - 1)) },
                        onMoveLeft = { onReorder(swapped(order, stored, stored + 1)) },
                    )
                }
            }
        }
        // Which end is the battery is the one thing a horizontal row cannot say by itself.
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.out_order_outwards),
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Text(
                text = stringResource(R.string.out_order_battery),
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** One tile's left/right pair, disabled at the ends rather than hidden. */
@Composable
private fun OutOrderArrows(
    canMoveRight: Boolean,
    canMoveLeft: Boolean,
    onMoveRight: () -> Unit,
    onMoveLeft: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OrderButton(
            icon = Lucide.ChevronLeft,
            description = R.string.out_order_move_out,
            enabled = canMoveLeft,
        ) { onMoveLeft() }
        OrderButton(
            icon = Lucide.ChevronRight,
            description = R.string.out_order_move_in,
            enabled = canMoveRight,
        ) { onMoveRight() }
    }
}

/** {@code order} with two entries exchanged; a copy, so the snapshot stays immutable. */
private fun swapped(order: IntArray, from: Int, to: Int): IntArray {
    val next = order.copyOf()
    if (from !in next.indices || to !in next.indices) return next
    val held = next[from]
    next[from] = next[to]
    next[to] = held
    return next
}

/** The tile a stored {@code OUT_SLOT_*} id stands for. */
private fun trioSettingsPartFor(slot: Int): TrioSettingsPart? = when (slot) {
    Prefs.OUT_SLOT_SIGNAL -> TrioSettingsPart.Mobile
    Prefs.OUT_SLOT_READOUTS -> TrioSettingsPart.Readouts
    Prefs.OUT_SLOT_TYPE -> TrioSettingsPart.Type
    else -> null
}

/**
 * Says that the signal rows are drawn outside the ring, whatever this list holds.
 *
 * <p>Shown inside the order card rather than as a dialog: it is a standing explanation of why
 * nothing here is producing dots, not an event to acknowledge. A user who has just switched
 * the signal to 环外 (or who upgraded into that state) sees this instead of concluding the
 * module broke.
 */
@Composable
internal fun SignalOutOfRingNotice() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Lucide.Info,
            contentDescription = null,
            tint = MiuixTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = stringResource(R.string.drawing_signal_out_of_ring_notice),
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}
