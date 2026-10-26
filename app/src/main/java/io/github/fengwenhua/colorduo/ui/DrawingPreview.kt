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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.fengwenhua.colorduo.Prefs
import io.github.fengwenhua.colorduo.R
import io.github.fengwenhua.colorduo.TrioAppearance
import io.github.fengwenhua.colorduo.TrioPreviewView
import io.github.fengwenhua.colorduo.ChargeLayout
import io.github.fengwenhua.colorduo.TrioSettings
import io.github.fengwenhua.colorduo.TrioZones
import top.yukonga.miuix.kmp.basic.Icon
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.ChevronLeft
import com.composables.icons.lucide.ChevronRight
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * The battery and signal states the preview can be put into.
 *
 * <p>This exists because three of the six pieces are only reachable in a state the
 * phone is not usually in: the charging bolt needs a charger, the low and critical
 * colours need a low battery, and the dual rows need two answered SIMs. Without a
 * way to ask for those, those pieces would have no tappable presence at all on
 * this page.
 *
 * <p>Each carries the whole state rather than a flag, because they are mutually
 * exclusive: a frame cannot be both charging and critical, and modelling them as
 * independent toggles would allow combinations the renderer has no look for.
 */
internal enum class PreviewScene(
    val labelRes: Int,
    val level: Int,
    val charging: Boolean = false,
    val quickCharging: Boolean = false,
    val powerSave: Boolean = false,
    val low: Boolean = false,
    val wifiLevel: Int = WIFI_ON_LEVEL,
    val slotLevels: IntArray? = null,
) {
    Normal(R.string.drawing_scene_normal, 64),
    Charging(R.string.drawing_scene_charging, 88, charging = true),
    QuickCharging(R.string.drawing_scene_quick, 88, charging = true, quickCharging = true),
    Low(R.string.drawing_scene_low, 24, powerSave = true, low = true),
    Critical(R.string.drawing_scene_critical, 12, low = true),
    NoWifi(R.string.drawing_scene_no_wifi, 79, wifiLevel = NO_WIFI_LEVEL),
}

/** The Wi-Fi level the preview shows: mid-strength, so arcs are drawn. */
private const val WIFI_ON_LEVEL = 3

/** What the renderer reads as "no Wi-Fi ink": the percentage drops to the centre. */
private const val NO_WIFI_LEVEL = -1

/** Mobile level the preview shows: full, so the meter is legible. */
private const val MOBILE_LEVEL = 4

/**
 * Per-SIM levels, so the dual reading draws two rows of different heights.
 *
 * <p>SIM 1 one step down from SIM 2, matching the settings screen's own preview
 * strip. Equal levels would make the dual switch invisible in the rectangular
 * arrangement, where the reading only changes which levels the two columns carry.
 */
private val PREVIEW_SLOTS = intArrayOf(2, 4)

/** The same synthetic SIM readings feed the renderer and the placement solver. */
internal val PreviewScene.previewSlots: IntArray
    get() = slotLevels ?: PREVIEW_SLOTS

/**
 * The network-type label the preview stands in for the real one.
 *
 * <p>Deliberately the "5GA" form rather than "5G": the trailing A is what the
 * suffix-scale row changes, so it is the only form in which that setting can be
 * seen at all.
 */
internal const val PREVIEW_TYPE = "5GA"

@Composable
internal fun DrawingStyleSelector(
    settings: TrioSettings, scene: PreviewScene, dark: Boolean,
    snapshotForStyle: (Int) -> TrioSettings, onSelect: (Int) -> Unit,
) {
    val styles = listOf(Prefs.STYLE_RING, Prefs.STYLE_CAPSULE)
    val labels = listOf(R.string.trio_style_ring, R.string.trio_style_capsule)
    Row(Modifier.fillMaxWidth().padding(bottom = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        styles.forEachIndexed { index, style ->
            val chosen = settings.trioStyle == style
            // Each arrangement keeps its own components and layout now, so the tile that is
            // NOT selected has to draw THAT arrangement's pieces. Copying the current snapshot
            // and only relabelling its shape would preview a look clicking it does not
            // produce - and for the capsule that is a visible lie, since its openings and
            // body are filled by a different set of pieces.
            // The active tile must follow every edit, including priority changes made
            // lower on this same page. Only keying by style kept its old layout alive.
            val sample = if (chosen) settings
                else ChargeLayout.forState(settings.withComponentsFrom(snapshotForStyle(style), style),
                    scene.charging, scene.quickCharging)
            Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                .background(if (chosen) MiuixTheme.colorScheme.primaryContainer else MiuixTheme.colorScheme.surfaceContainerHigh)
                .border(if (chosen) 2.dp else 0.dp, if (chosen) MiuixTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(12.dp))
                .semantics { selected = chosen }
                .clickable(enabled = settings.enabled, role = Role.RadioButton) { onSelect(style) }
                .padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                DrawingSmallPreview(sample, scene, dark, Modifier.fillMaxWidth().height(64.dp))
                Text(stringResource(labels[index]), style = MiuixTheme.textStyles.footnote1,
                    color = if (chosen) MiuixTheme.colorScheme.onPrimaryContainer else MiuixTheme.colorScheme.onSurface)
            }
        }
    }
}

/**
 * Puts one [TrioPreviewView] into the state a preview wants.
 *
 * <p>Called from BOTH the factory and the update block, which is the point. A Compose
 * {@code AndroidView} runs {@code factory} once and {@code update} only on a
 * recomposition of that node; a LazyColumn item whose parent does not recompose can
 * therefore live its whole life with the state its factory gave it. The factory above
 * used to construct the view and leave it at its defaults - an empty ring - so the
 * preview showed the shipped look instead of the user's, and every settings tile drew
 * the same thing whatever they configured. One function, called from both places, is
 * what makes "the preview shows what you picked" true rather than likely.
 */
private fun applyPreviewState(
    view: TrioPreviewView,
    settings: TrioSettings,
    scene: PreviewScene,
    dark: Boolean,
    /**
     * Ink for the press highlight, or 0 to leave the view's own default alone.
     *
     * <p>Passed in rather than fixed in the view: the preview follows the system theme, and
     * the highlight was a fixed white - invisible on the light surface it spends most of
     * its life on. The callers pass the theme's own primary, so the region under the
     * finger is the same colour as every other "this is selected" in the app.
     */
    accent: Int = 0,
    outType: Boolean = false,
    outSignal: Boolean = false,
    highlight: TrioZones.Zone? = null,
) {
    if (accent != 0) {
        view.setHighlightColors(accent)
    }
    view.setSettings(ChargeLayout.forState(settings, scene.charging, scene.quickCharging))
    view.setOutTypeOnly(outType)
    view.setOutSignalOnly(outSignal)
    view.setHighlight(highlight)
    view.setPreviewBackground(if (dark) 0xFF1C1B1F.toInt() else 0xFFF2F2F7.toInt())
    view.setForeground(if (dark) 0xFFFFFFFF.toInt() else 0xFF000000.toInt())
    view.setState(scene.level, scene.charging, scene.quickCharging, scene.powerSave,
        scene.low, scene.wifiLevel, MOBILE_LEVEL, scene.previewSlots, PREVIEW_TYPE)
}

@Composable
internal fun DrawingSmallPreview(
    settings: TrioSettings, scene: PreviewScene, dark: Boolean, modifier: Modifier,
    outType: Boolean = false, outSignal: Boolean = false,
) {
    AndroidView(
        modifier = modifier,
        // The state is applied here as well as in update: see applyPreviewState.
        factory = { context ->
            TrioPreviewView(context).also {
                applyPreviewState(it, settings, scene, dark, outType = outType,
                    outSignal = outSignal)
            }
        },
        update = { view ->
            applyPreviewState(view, settings, scene, dark, outType = outType,
                outSignal = outSignal)
        },
    )
}

/**
 * The sample at the top of a component's settings sheet.
 *
 * <p>Both branches clip to the same shape as the cards this sheet is made of: the preview
 * is an {@code AndroidView} that paints its own background, and a Compose {@code clip}
 * alone does not reach inside it - the view rounds its own corners, and this keeps the
 * Compose layer from showing a square edge underneath the 12dp it rounds to.
 */
@Composable
internal fun DrawingSettingsPreview(
    part: TrioSettingsPart, settings: TrioSettings, scene: PreviewScene, dark: Boolean,
) {
    val a = TrioAppearance.of(settings)
    val shape = RoundedCornerShape(12.dp)
    if (part == TrioSettingsPart.Readouts) {
        DrawingReadoutSample(settings, dark, Modifier.fillMaxWidth().height(92.dp))
        Text(stringResource(R.string.drawing_readout_sample_summary),
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(vertical = 4.dp))
    } else {
        DrawingSmallPreview(settings, scene, dark,
            Modifier.fillMaxWidth().height(92.dp).clip(shape),
            outType = part == TrioSettingsPart.Type && a.typeOutOfRing,
            // The cell follows what the signal actually is this frame: the stacked reading
            // only exists out of ring with its switch on. In every other case the glyph
            // itself is the right sample, so this stays false.
            outSignal = part == TrioSettingsPart.Mobile && a.stackedOut(),
        )
    }
}

/**
 * A live sample of the two performance readouts.
 *
 * <p>The sample always shows a value for every readout that is switched on, even when the
 * current preview scene has nothing to report - the power reading only exists while
 * charging, and the default scene is not charging, so keying the sample to the scene left
 * this cell permanently blank.
 *
 * <p>The numbers (`9.6W`, `32°C`) are fixed stand-ins: a sample of the type, not a claim
 * about the device.
 *
 * <p><b>One fixed size, and the text is always whole.</b> The sample is not a measurement of
 * the status bar - the status bar is live, so a size change is visible there the moment the
 * slider moves. What this cell has to do is show <em>which pieces</em> are in the strip and
 * <em>roughly how they sit together</em>, so it draws one legible size and lets the longest
 * line decide it. Deriving the size from `outTypeSize` was tried and rejected: it made this
 * cell disagree with the network-type cell next to it, and at the sizes that fit the height
 * the text ran past the width and was silently clipped - "9.6W" showed as "9.6".
 *
 * <p>The weight is the reading's own, because that one is worth previewing: it is the only
 * property that changes the shape of the text rather than just its size.
 */
@Composable
internal fun DrawingReadoutSample(settings: TrioSettings, dark: Boolean, modifier: Modifier) {
    val a = TrioAppearance.of(settings)
    // With neither switch on there is nothing to report, but the cell still has to show
    // the type it sizes: one line, because the single-reading size is what the
    // "overall reading size" row acts on when only one reading exists.
    val readings = listOfNotNull(
        if (settings.content.perfPower()) "9.6W" else null,
        if (settings.content.perfTemp()) "32°C" else null,
    ).ifEmpty { listOf("32°C") }
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(modifier.clip(RoundedCornerShape(12.dp))
        .background(if (dark) Color(0xFF1C1B1F) else Color(0xFFF2F2F7)), contentAlignment = Alignment.Center) {
        // Fit the cell on both axes, measured rather than estimated: the widest line at a
        // trial size gives the width factor, and the number of lines gives the height one.
        // Whatever the longer constraint says wins, so nothing is ever clipped.
        val longest = readings.maxByOrNull { it.length }.orEmpty()
        val probe = if (longest.isEmpty()) {
            null
        } else {
            textMeasurer.measure(
                text = longest,
                style = TextStyle(
                    fontSize = 100.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight(a.perfTypeWeight()),
                ),
                maxLines = 1,
            )
        }
        // 100sp was the trial size, so a single glyph-run's width per unit of font size is
        // its measured width over 100. Margins keep the text clear of the rounded corners.
        val widthPerUnit = probe?.let { with(density) { it.size.width.toDp().value } / 100f } ?: 0f
        val byWidth = if (widthPerUnit > 0f) maxWidth.value * 0.86f / widthPerUnit else 0f
        val byHeight = maxHeight.value * 0.82f / (readings.size * 1.35f)
        val size = minOf(
            if (byWidth > 0f) byWidth else byHeight,
            byHeight,
        )
        Text(readings.joinToString("\n"), color = if (dark) Color.White else Color.Black,
            fontSize = with(density) { size.dp.toSp() },
            lineHeight = with(density) { (size * 1.3f).dp.toSp() },
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight(a.perfTypeWeight()), textAlign = TextAlign.Center)
    }
}

/** The large glyph, with the three zones as touch targets and guides.
 *
 * <p>The zones are resolved by the <em>view</em>, because it is what knows the
 * transform the glyph is drawn with: the gestures here ask it for the third under
 * the finger rather than working one out from the touch coordinates. That is the
 * whole design of this composable - a second copy of the design-to-view mapping on
 * the Compose side is exactly how a hot spot ends up beside the piece it names.
 *
 * <p>The gestures are Compose's rather than a touch listener on the view, and that
 * is a deliberate correction: a listener returning true for `ACTION_DOWN` swallows
 * the event, so the page could not be scrolled by dragging over a glyph that fills
 * most of the screen. [detectTapGestures] instead gives up the moment the finger
 * moves past the touch slop, so a drag scrolls the list and a press that stays put
 * is a tap.
 *
 * <p>The handler reads its callbacks through [rememberUpdatedState] so the detector,
 * which is keyed on the settings rather than rebuilt per frame, always calls the
 * current lambda; one captured at composition would close over an older snapshot
 * and flip a switch based on what was on screen several edits ago.
 */
@Composable
internal fun DrawingGlyph(
    settings: TrioSettings,
    scene: PreviewScene,
    dark: Boolean,
    pressed: TrioZones.Zone?,
    onPressChange: (TrioZones.Zone?) -> Unit,
    onPickZone: (TrioZones.Zone) -> Unit,
    onEditZone: (TrioZones.Zone) -> Unit,
    onEditBattery: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The theme's own accent, so the press highlight is the same colour as the rest of the
    // app's "selected" affordances and stays visible on both themes.
    val accent = MiuixTheme.colorScheme.primary.toArgb()
    val currentPress = rememberUpdatedState(onPressChange)
    val currentPick = rememberUpdatedState(onPickZone)
    val currentEdit = rememberUpdatedState(onEditZone)
    val currentBattery = rememberUpdatedState(onEditBattery)

    // The view the gestures interrogate. A plain holder rather than a MutableState:
    // it is written once, from `factory`, and read only later - when a finger
    // arrives - so there is nothing here that ought to trigger recomposition, and
    // writing state during the layout that creates the view would.
    val holder = remember { arrayOfNulls<TrioPreviewView>(1) }
    // Resolved against the view that is actually laid out, so a gesture arriving
    // before the first layout finds no zone instead of guessing one.
    val zoneAt: (Offset) -> TrioZones.Zone? = { offset ->
        holder[0]?.zoneAt(offset.x, offset.y)
    }

    Box(
        modifier = modifier
            .fillMaxWidth(0.94f)
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .pointerInput(settings, scene) {
                detectTapGestures(
                    onPress = { offset ->
                        currentPress.value(if (holder[0]?.isBatteryRingAt(offset.x, offset.y) == true)
                            null else zoneAt(offset))
                        // Waits for the press to resolve, which is also what lets a
                        // drag cancel this detector and be handled as a scroll.
                        tryAwaitRelease()
                        currentPress.value(null)
                    },
                    onTap = { offset ->
                        if (holder[0]?.isBatteryRingAt(offset.x, offset.y) == true) currentBattery.value()
                        else zoneAt(offset)?.let { currentPick.value(it) }
                    },
                    onLongPress = { offset ->
                        if (holder[0]?.isBatteryRingAt(offset.x, offset.y) == true) currentBattery.value()
                        else zoneAt(offset)?.let { currentEdit.value(it) }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            // Fills the square Box above; the aspect ratio belongs to the Box so the
            // gesture detector covers exactly the area the glyph is drawn in.
            modifier = Modifier.fillMaxSize(),
            // Applied in the factory too: an AndroidView whose node never recomposes
            // keeps whatever its factory gave it, and a view left at its defaults draws
            // the shipped look rather than the user's. See applyPreviewState.
            factory = { context ->
                TrioPreviewView(context).also { view ->
                    holder[0] = view
                    applyPreviewState(view, settings, scene, dark, accent = accent,
                        highlight = pressed)
                }
            },
            update = { view ->
                applyPreviewState(view, settings, scene, dark, accent = accent,
                    highlight = pressed)
            },
        )
    }
}

/**
 * The preview-state chips.
 *
 * <p>A horizontal strip rather than a dropdown: there are six, their labels are
 * short, and seeing the whole set is what tells the user that a charging frame is
 * reachable at all - which is the only way the bolt becomes tappable.
 */
@Composable
internal fun SceneSelector(
    selected: PreviewScene,
    onSelect: (PreviewScene) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val overflow = state.canScrollForward || state.canScrollBackward
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LazyRow(
                state = state,
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(PreviewScene.entries, key = { it.name }) { entry ->
                    SceneChip(
                        label = stringResource(entry.labelRes),
                        selected = entry == selected,
                        onClick = { onSelect(entry) },
                    )
                }
            }
            if (overflow) {
                val forward = state.canScrollForward
                IconButton(
                    onClick = {
                        val target = if (forward) maxOf(state.firstVisibleItemIndex + 1,
                            state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0)
                            .coerceAtMost(PreviewScene.entries.lastIndex) else 0
                        scope.launch { state.animateScrollToItem(target) }
                    },
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(if (forward) Lucide.ChevronRight else Lucide.ChevronLeft,
                        contentDescription = stringResource(if (forward) R.string.drawing_more_states
                            else R.string.drawing_previous_states),
                        tint = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp).graphicsLayer { scaleX = if (rtl) -1f else 1f })
                }
            }
        }
        if (overflow) {
            Text(stringResource(R.string.drawing_states_scroll_hint),
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
    }
}

/** One state chip. */
@Composable
internal fun SceneChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(
                if (selected) {
                    MiuixTheme.colorScheme.primary
                } else {
                    MiuixTheme.colorScheme.surfaceContainerHigh
                },
            )
            .semantics { this.selected = selected }
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.button,
            color = if (selected) {
                MiuixTheme.colorScheme.onPrimary
            } else {
                MiuixTheme.colorScheme.onSurfaceContainerHigh
            },
            textAlign = TextAlign.Center,
        )
    }
}
