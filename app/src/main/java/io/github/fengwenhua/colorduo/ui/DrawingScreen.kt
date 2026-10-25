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

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import io.github.fengwenhua.colorduo.Prefs
import io.github.fengwenhua.colorduo.R
import io.github.fengwenhua.colorduo.TrioAppearance
import io.github.fengwenhua.colorduo.ChargeLayout
import io.github.fengwenhua.colorduo.TrioSettings
import io.github.fengwenhua.colorduo.TrioZones
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.File

/**
 * The drawing page: the glyph rendered large, with its three parts tappable.
 *
 * <p>This is the page that answers "which switch draws the thing I am looking
 * at". Everywhere else in this app the settings are a list of switches named after
 * pieces; here the pieces are the interface, and a switch is what a tap on one
 * does.
 *
 * <p>It is a page of its own rather than a mode of the General tab because it
 * needs room: the targets are thirds of the glyph, so the glyph has to be large
 * enough that a finger can land inside one third on purpose. The General tab's
 * preview strip is about forty dp a cell - a comparison aid, not an input surface.
 *
 * <h2>What a tap does</h2>
 *
 * <p>Tap a third and a list opens of the components that could be drawn there; pick
 * one and it is placed, displacing whatever was there. The list is not a fixed table -
 * it is whatever [TrioZones.choices] reports as placeable in that zone, and choosing
 * an entry writes the switch combination [TrioZones.assign] solved for. Both live in
 * {@code TrioZones} rather than here because they have to agree with where the pieces
 * are actually drawn, and both are checked against real rendered pixels by
 * {@code tests/drawing-zones.ps1}.
 *
 * <p>The first entry is "nothing here", which empties the zone.
 *
 * <p>Long-press on a row opens that component's own size and colour settings, from
 * the same row table the size page renders, so the two places cannot offer
 * different ranges for one setting.
 */

/** Row rhythm inside the editor card, matching the rest of the settings screen. */
private val DrawingItemMargin = PaddingValues(horizontal = 18.dp, vertical = 16.dp)

/**
 * The drawing page's body.
 *
 * @param settings the live snapshot, read at composition so a write is visible on
 *   the next frame: the page and the glyph have to agree about what is switched on,
 *   and the glyph is drawn from this same object.
 */
internal fun LazyListScope.drawingTab(
    settings: TrioSettings,
    update: ((SettingsRepository) -> Unit) -> Unit,
    onEditColor: (RoleColor) -> Unit,
    onResetColors: () -> Unit,
    /**
     * The stored snapshot for a given arrangement, used by the style tiles.
     *
     * <p>A plain lambda rather than handing the whole repository down: the page only ever
     * needs to READ one other snapshot, and a read accessor is what keeps that dependency
     * from turning into "the page may write anything".
     */
    snapshotForStyle: (Int) -> TrioSettings,
) {
    item {
        DrawingEditorCard(
            storedSettings = settings,
            persistUpdate = update,
            onEditColor = onEditColor,
            onResetColors = onResetColors,
            snapshotForStyle = snapshotForStyle,
        )
    }
}

/**
 * The whole editor: what each third holds, the large glyph, and the state selector.
 *
 * <p>[settings] is the live snapshot rather than a local copy - a tap calls
 * straight through to the repository and the parent recomposes with the new value -
 * so there is no second model here to fall out of step with what the status bar
 * will draw.
 */
@Composable
private fun DrawingEditorCard(
    storedSettings: TrioSettings,
    persistUpdate: ((SettingsRepository) -> Unit) -> Unit,
    onEditColor: (RoleColor) -> Unit,
    onResetColors: () -> Unit,
    snapshotForStyle: (Int) -> TrioSettings,
) {
    // Which component's settings dialog is open, if any. The named enum rather than
    // the raw part, so the dialog can be titled with the row name the rest of the
    // screen uses for it.
    var editing by remember { mutableStateOf<TrioSettingsPart?>(null) }
    var sheetVisible by remember(editing) { mutableStateOf(editing != null) }
    var pendingColor by remember { mutableStateOf<RoleColor?>(null) }
    var pendingReset by remember { mutableStateOf(false) }
    // The zone whose picker is open, if any. One at a time: the picker is a dialog,
    // and two stacked pickers would be a state with no sensible dismissal order.
    var picking by remember { mutableStateOf<TrioZones.Zone?>(null) }
    // The capsule container's own sheet: its five sliders live there, one row away.
    var containerSheet by remember { mutableStateOf(false) }
    // The third under the finger, so the guide can outline what a press would open.
    var pressed by remember { mutableStateOf<TrioZones.Zone?>(null) }
    var scene by rememberSaveable { mutableStateOf(PreviewScene.Normal) }
    var previewTheme by rememberSaveable { mutableStateOf(0) }
    var lastSelection by remember { mutableStateOf<ZoneSelection?>(null) }

    val editScene = ChargeLayout.scene(scene.charging, scene.quickCharging)
    val settings = ChargeLayout.forScene(storedSettings, editScene)
    val update: ((SettingsRepository) -> Unit) -> Unit = { change ->
        persistUpdate { repository -> change(repository.forChargeScene(editScene)) }
    }
    val appearance = TrioAppearance.of(settings)
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val dark = when (previewTheme) { 1 -> false; 2 -> true; else -> systemDark }

    // This page previews synthetic readings, independently of the switch being
    // edited. Counting the switch would make enabling the second row unreachable.
    val sims = scene.previewSlots.count { it >= 0 }
    val ringSettings = TrioZones.editableRingSettings(settings, scene.level, scene.charging,
        scene.wifiLevel, PREVIEW_TYPE, sims)
    // Which cell the page-level tabs show. Kept in the page rather than in a dialog so the
    // glyph above stays visible while the order is edited - which is the point of editing it.
    var openZone by remember { mutableStateOf(TrioZones.Zone.CENTRE) }
    val chooseZone: (TrioZones.Zone) -> Unit = { zone ->
        // One path for every zone now. The capsule's left column used to be special-cased
        // straight to the clock's settings, because the clock was pinned there and could not
        // be moved; with four cells the clock is an ordinary piece, so tapping its cell opens
        // that cell's picker like any other.
        openZone = zone
        pressed = zone
        picking = zone
    }
    /**
     * Edit one zone's priority list.
     *
     * <p>[delta] is {@code -1}/{@code +1} to move an entry up or down, and {@code 0} to drop
     * that single entry. Dropping is deliberately not "clear the zone": when the head is
     * removed the candidate behind it takes over, which is the whole point of a fallback
     * chain.
     */
    val reorderZone: (TrioZones.Zone, Int, Int) -> Unit = { zone, index, delta ->
        // A stale row (the list shrank since it was drawn) resolves to a no-op rather than
        // writing a list built from an index that no longer exists.
        val next = if (delta == 0) {
            TrioZones.withoutAt(settings, zone.ordinal, index)
        } else {
            TrioZones.moveWithin(settings, zone.ordinal, index, delta)
        }
        if (next !== settings) {
            update { r -> applyZoneAssignment(next, r, settings) }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DrawingSection(R.string.drawing_master_title, PaddingValues(0.dp)) {
            ContentControlRow(ContentControl.Enabled, settings, update)
        }

        DrawingSection(R.string.drawing_quick_edit_title) {
            DrawingStyleSelector(
                settings = settings,
                scene = scene,
                dark = dark,
                snapshotForStyle = snapshotForStyle,
                onSelect = { style ->
                    lastSelection = null
                    update { it.setTrioStyle(style) }
                },
            )
            Text(
                text = stringResource(R.string.drawing_state_title),
                style = MiuixTheme.textStyles.subtitle,
            )
            SceneSelector(
                selected = scene,
                onSelect = { scene = it; lastSelection = null; picking = null; editing = null },
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            )
            if (editScene != ChargeLayout.BASE) {
                Text(stringResource(R.string.drawing_charge_layout_notice),
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(listOf(R.string.drawing_theme_system, R.string.drawing_theme_light,
                    R.string.drawing_theme_dark).withIndex().toList(), key = { it.index }) { entry ->
                    SceneChip(stringResource(entry.value), previewTheme == entry.index) {
                        previewTheme = entry.index
                    }
                }
            }

            DrawingGlyph(
                settings = settings,
                scene = scene,
                dark = dark,
                pressed = pressed,
                onPressChange = { pressed = it },
                // A press on the glyph opens that zone's picker: the request is not
                // "flip something" but "put something else here", so pointing at the
                // zone is choosing which slot to fill, and the list follows.
                onPickZone = chooseZone,
                onEditZone = { zone ->
                    val part = TrioZones.occupant(
                        zone, appearance, scene.level, scene.charging,
                        scene.wifiLevel, PREVIEW_TYPE, sims,
                    )
                    if (part == null) {
                        picking = zone
                    } else {
                        editing = TrioSettingsPart.of(part)
                    }
                },
                onEditBattery = { editing = TrioSettingsPart.Battery },
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            )

            // The capsule container's shape, under Quick Edit because that is where the style
            // tiles are: picking the capsule and then sizing it are the same decision, and the
            // component-order section is about where pieces GO, not what holds them. Behind a
            // row rather than inline - five sliders would double this section - and in the same
            // sheet a component uses, since it answers the same kind of question.


            if (!appearance.glyph) {
                Text(
                    text = stringResource(R.string.drawing_master_off),
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }

        // The capsule container, in the same shape as the About page's update row: a card of
        // its own holding one ArrowPreference, whose insideMargin is the row's inset. It is
        // NOT inside a DrawingSection, because that card already insets its content by the
        // same 18dp - nesting the two added up and made the row read narrower than any other
        // row on the screen.
        if (appearance.capsule) {
            Card {
                ArrowPreference(
                    title = stringResource(R.string.capsule_container_title),
                    summary = stringResource(R.string.capsule_container_summary),
                    insideMargin = DrawingItemMargin,
                    onClick = { containerSheet = true },
                )
            }
        }

        DrawingSection(R.string.drawing_order_title) {
            // The strip lists the zones this arrangement actually has: three for the ring and
            // the rectangle, four for the capsule (left, upper-right, lower-right, dots).
            // Built from `zonesFor` rather than from Zone.entries so the capsule's extra
            // cells cannot leak into a ring's strip.
            val zones = zonesFor(appearance)
            // Miuix's own outlined tab row, with nothing overridden. The page used to
            // wrap this in a hand-drawn glass capsule (backdrop, blur, lens, contour
            // border) to match the shell's floating bar, but that made the tabs a
            // second material next to the cards they sit inside: the component's own
            // contour already IS the outline, and re-drawing it by hand is one more
            // copy of a shape the library ships.
            TabRowWithContour(
                tabs = zones.map { zone -> stringResource(zoneLabelRes(zone, appearance)) },
                selectedTabIndex = zones.indexOf(openZone).coerceAtLeast(0),
                onTabSelected = { index ->
                    openZone = zones[index]
                    pressed = null
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
            )

            val chain = appearance.ringZones[openZone.ordinal]
            // Shown whenever the signal is out of ring, not only when a signal row happens to
            // be in this zone's list. Two different things go wrong without it, and both read
            // as "the module stopped working":
            //
            //   1. a signal row IS listed here but draws nothing, because the reading moved
            //      to the status bar - the list claims it is on screen when it is not;
            //   2. nothing is listed here yet, and the user is about to add a signal row that
            //      will never appear - which is worse, because there is no evidence at all.
            //
            // One sentence covers both: it says where the signal is, so "nothing here draws
            // dots" stops being a mystery in either case.
            if (appearance.signalOutOfRing) {
                SignalOutOfRingNotice()
            }
            val actual = TrioZones.occupant(
                openZone, appearance, scene.level, scene.charging,
                scene.wifiLevel, PREVIEW_TYPE, sims,
            )
            Column(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                if (chain.isEmpty()) {
                    Text(
                        text = stringResource(R.string.drawing_priority_empty_summary),
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                    )
                }
                chain.forEachIndexed { index, id ->
                    val part = TrioZones.decodePart(id) ?: return@forEachIndexed
                    PriorityPreferenceRow(
                        index = index,
                        part = part,
                        lastIndex = chain.size - 1,
                        isDrawing = part == actual,
                        onMove = { i, delta -> reorderZone(openZone, i, delta) },
                        onRemove = { reorderZone(openZone, index, REMOVE) },
                        // The same sheet the library opens, so the two entrances cannot
                        // disagree about what a piece's settings are.
                        onEdit = { TrioSettingsPart.of(part)?.let { editing = it } },
                    )
                }
            }

            lastSelection?.let { selection ->
                PlacementFeedback(selection, appearance, scene, sims)
            }

            Button(
                onClick = { picking = openZone },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Text(stringResource(R.string.drawing_priority_add))
            }


        }

        DrawingSection(R.string.drawing_outside_title) {
            DrawingOutsideParts(settings, scene, dark,
                onEdit = { editing = it },
                onReorder = { order -> update { it.setOutOrder(order) } },
            )
        }

    }


    // The container sheet. Same surface as a component's, because it answers the same kind
    // of question - and it needs the same inset handling, or the last slider sits under the
    // gesture bar.
    if (containerSheet) {
        val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        OverlayBottomSheet(
            show = true,
            title = stringResource(R.string.capsule_container_title),
            onDismissRequest = { containerSheet = false },
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = bottomInset)) {
                CapsuleContainerRows(settings, update, Modifier.padding(horizontal = 4.dp))
            }
        }
    }

    val pickZone = picking
    if (pickZone != null) {
        ZonePickerDialog(
            zone = pickZone,
            settings = settings,
            appearance = appearance,
            scene = scene,
            sims = sims,
            onChoose = { part ->
                // Choosing a piece puts it at the FRONT of this zone's priority list; the
                // pieces already there stay behind it as fallbacks. Choosing "empty" removes
                // that one entry, so the next candidate is revealed rather than the zone
                // being wiped.
                val next = TrioZones.withPiece(settings, pickZone.ordinal, part)
                update { r -> applyZoneAssignment(next, r, settings) }
                lastSelection = part?.let { ZoneSelection(pickZone, it) }
                picking = null
            },
            onEdit = { part ->
                picking = null
                editing = TrioSettingsPart.of(part)
            },
            onDismiss = { picking = null },
        )
    }

    val current = editing
    if (current != null) {
        PartSettingsSheet(
            show = sheetVisible,
            part = current,
            settings = settings,
            ringSettings = ringSettings,
            update = update,
            // The colour picker is the page's own, opened over this dialog: the
            // dialog closes and the picker takes its place, which is the stacking
            // the colour page already uses. Two dialogs at once would be a state
            // the rest of the screen has no way to present.
            onEditColor = { role ->
                pendingColor = role
                sheetVisible = false
            },
            onResetColors = {
                pendingReset = true
                sheetVisible = false
            },
            preview = {
                Column {
                    DrawingSettingsPreview(current, settings, scene, dark)
                    SceneSelector(scene, { scene = it }, Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 16.dp))
                }
            },
            onDismiss = { sheetVisible = false },
            onDismissFinished = {
                editing = null
                pendingColor?.let { role ->
                    pendingColor = null
                    onEditColor(role)
                }
                if (pendingReset) {
                    pendingReset = false
                    onResetColors()
                }
            },
        )
    }
}

/** A labelled card using the same section rhythm as the settings page. */
@Composable
private fun DrawingSection(
    titleRes: Int,
    contentPadding: PaddingValues = DrawingItemMargin,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column {
        SmallTitle(
            text = stringResource(titleRes),
            modifier = Modifier.padding(top = 4.dp),
            insideMargin = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
        )
        Card {
            Column(modifier = Modifier.fillMaxWidth().padding(contentPadding), content = content)
        }
    }
}

/**
 * The components page: one row per component.
 *
 * <p>Emitted into the host list as a single item, like the drawing page, so the list that
 * scrolls it is the same one that scrolls every other page.
 */
@Composable
internal fun PartsPage(
    settings: TrioSettings,
    onEdit: (TrioSettingsPart) -> Unit,
    installingFont: Boolean,
    onChooseFont: () -> Unit,
    onClearFont: (String) -> Unit,
) {
    val a = TrioAppearance.of(settings)
    Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        partsForPage(settings).forEach { part ->
            PartRow(part, settings, onClick = { onEdit(part) })
        }

        // The face the glyph's text is drawn in. Here rather than on the About page: it is a
        // property of the ink, and About answers questions about the BUILD (version, framework,
        // scope) rather than about how the glyph looks.
        SmallTitle(
            text = stringResource(R.string.group_font),
            modifier = Modifier.padding(top = 4.dp),
            insideMargin = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
        )
        Card {
            val chosen = settings.fontPath
            ArrowPreference(
                title = stringResource(R.string.font_title),
                // The file name, not a bare "chosen": it is how a user tells two fonts
                // apart when comparing them, and the full path is far too long to show.
                summary = when {
                    installingFont -> stringResource(R.string.font_installing)
                    chosen.isEmpty() -> stringResource(R.string.font_summary_system)
                    else -> File(chosen).name
                },
                insideMargin = SettingsItemMargin,
                // Disabled while a copy is in flight: a second pick would race the first
                // one's file out from under it.
                enabled = !installingFont,
                onClick = onChooseFont,
            )
            // Always present, greyed out when there is nothing to clear.
            val nothingToClear = chosen.isEmpty()
            ArrowPreference(
                title = stringResource(R.string.font_clear_title),
                summary = if (nothingToClear) {
                    stringResource(R.string.font_clear_summary_idle)
                } else {
                    stringResource(R.string.font_clear_summary)
                },
                insideMargin = SettingsItemMargin,
                enabled = !nothingToClear && !installingFont,
                onClick = { onClearFont(chosen) },
            )
        }

    }
}

/** The components this arrangement can hold, in the order the page lists them. */
/**
 * The components this arrangement can hold, in page order.
 *
 * <p>Takes the SETTINGS, not an appearance: it has to be callable from a LazyListScope, which
 * is not a composable scope, and the only thing it needs is which arrangement is in force.
 */
internal fun partsForPage(settings: TrioSettings): List<TrioSettingsPart> {
    // Only the clock depends on the arrangement: the ring and the capsule both draw one, the
    // rectangle has no slot for it. Read from the raw style rather than from TrioAppearance -
    // that class is resolved by a composable, and this runs inside a LazyListScope.
    val style = Prefs.normalizeTrioStyle(settings.trioStyle)
    val slotted = style == Prefs.STYLE_RING || style == Prefs.STYLE_CAPSULE
    return listOf(TrioSettingsPart.Appearance, TrioSettingsPart.Battery, TrioSettingsPart.Wifi,
        TrioSettingsPart.Mobile, TrioSettingsPart.Value, TrioSettingsPart.Bolt,
        TrioSettingsPart.Type, TrioSettingsPart.Readouts) +
        if (slotted) listOf(TrioSettingsPart.Clock) else emptyList()
}

@Composable
private fun PartRow(
    part: TrioSettingsPart,
    settings: TrioSettings,
    onClick: () -> Unit,
) {
    val a = TrioAppearance.of(settings)
    // The row is its NAME and nothing else. A summary was tried - the first size row, then a
    // placeholder for the cards that only hold colours - and both were noise: the number is
    // one tap away in the sheet the row opens, and "this one has no size" is a sentence about
    // the absence of a feature, which is not what a list of components is for.
    Card {
        ArrowPreference(
            title = stringResource(if (part == TrioSettingsPart.Battery && a.ring)
                R.string.drawing_part_ring else drawingPartLabel(part)),
            summary = null,
            insideMargin = SettingsItemMargin,
            onClick = onClick,
        )
    }
}

internal fun drawingPartLabel(part: TrioSettingsPart): Int = when (part) {
    TrioSettingsPart.Appearance -> R.string.drawing_part_appearance
    TrioSettingsPart.Readouts -> R.string.drawing_part_readouts
    else -> part.part?.let(::occupantLabelRes) ?: part.labelRes
}
