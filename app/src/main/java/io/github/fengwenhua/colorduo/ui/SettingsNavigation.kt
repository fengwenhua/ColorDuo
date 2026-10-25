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

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.roundToInt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.fengwenhua.colorduo.Prefs
import io.github.fengwenhua.colorduo.R
import kotlinx.serialization.Serializable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.layout.layout
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import top.yukonga.miuix.kmp.basic.Icon
import androidx.compose.ui.graphics.Shape
import top.yukonga.miuix.kmp.basic.NavigationBarDisplayMode
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.House
import com.composables.icons.lucide.SlidersHorizontal
import top.yukonga.miuix.kmp.basic.Text
import com.kyant.backdrop.backdrops.layerBackdrop as kyantLayerBackdrop
import top.yukonga.miuix.kmp.nav.core.NavKey
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Blur radius of the top bar, in dp.
 *
 * Well above the library's 20.dp default because the bar is as tall as the
 * status bar plus a large title: at a small radius the rows underneath stay
 * legible and the bar reads as a translucent scrim rather than frosted glass.
 */
private val BarBlurRadius = 40f

/**
 * The page's top-level sections, in strip order. The label reuses the section
 * heading the group already had, so the strip reads as a table of contents for
 * the page rather than a second vocabulary for the same four things.
 *
 * Each section is also a destination on the nav back stack ([route]), which is
 * what gives a section switch its own animated page change and gives the system
 * back gesture something to pop. The routes' names and the strip's names are the
 * same four words, but they are separate types on purpose: the label is a
 * resource id, which has no business living on a value that gets serialized into
 * the saved-state bundle.
 */
internal enum class SettingsTab(val labelRes: Int, val route: Route) {
    // Two pages. The other three were retired because each repeated a section of the
    // drawing page's own editor sheet: the size rows came from the same `geometrySliders()`
    // table, the colour rows from the same `RoleColor` entries, and the switches from the
    // same `contentControlsFor()`. Two places to change one value is how they drift.
    Drawing(R.string.tab_home, Route.Drawing),
    // The components as a page of their own. They used to be a 3-wide grid of buttons inside
    // the drawing page, each opening a sheet - which meant "what can I size" was answered by
    // a block of nine identical tiles rather than by a list of the things being sized.
    Parts(R.string.tab_parts, Route.Parts),
    About(R.string.about_title, Route.About),
}

/**
 * Destinations of the settings back stack, one per section.
 *
 * Serialized data objects rather than a plain enum or class: miuix-nav keeps its
 * stack in rememberSaveable and rebuilds it through a serializer, so every route
 * has to round-trip. Page content uses its tab name as a separate saveable-state
 * key so the preview's saved choices survive a section switch.
 *
 * The on-screen state that is not a place -the colour picker, the restart sheet,
 * the reset prompt -deliberately stays a dialog. Those are prompts dismissed
 * over the page, and pushing them would turn the back gesture into "cancel"
 * instead of "go back a section".
 */
@Serializable
internal sealed interface Route : NavKey {
    @Serializable
    data object Drawing : Route

    @Serializable
    data object About : Route

    @Serializable
    data object Parts : Route
}

/**
 * Vertical alpha mask for the top bar's progressive blur: opaque at the bar's top,
 * fading to nothing by half its (expanded) height.
 *
 * <p>`smoothstep(size.y, size.y * 0.5, coord.y)` runs from 1 at `coord.y == size.y` to 0
 * at half that, which is the direction that matters - the mask has to be DENSE where the
 * bar meets the status bar and empty where the page continues, or the bar would end on a
 * hard edge and read as a painted strip.
 */
internal const val PROGRESSIVE_BLUR_ALPHA_MASK_SHADER = """
uniform shader content;
uniform float2 size;
half4 main(float2 coord) {
    float blurAlpha = smoothstep(size.y, size.y * 0.5, coord.y);
    return content.eval(coord) * blurAlpha;
}
"""

/**
 * Measures a child TALLER than the space it is given, then reports the original height.
 *
 * <p>For the progressive blur: the mask fades over the expanded height, so there has to BE
 * expanded height for it to fade across. Reporting the original height keeps the layout
 * unchanged - only the drawing surface grew.
 */
internal fun Modifier.expandDrawHeight(factor: Float) = layout { measurable, constraints ->
    val expandedHeight = (constraints.maxHeight * factor).toInt()
    val placeable = measurable.measure(
        constraints.copy(minHeight = expandedHeight, maxHeight = expandedHeight),
    )
    layout(constraints.maxWidth, constraints.maxHeight) { placeable.placeRelative(0, 0) }
}

/**
 * The docked bar: Miuix's own [NavigationBar], on the backdrop material.
 *
 * <p>Delegating to the library rather than re-implementing it is what makes this one look
 * like a system control - the item metrics, the selected-label animation and the divider
 * policy all come with it. The only thing added here is the material: vibrancy plus a blur
 * at the user's radius, with NO rim highlight, because a highlight on a bar that is flush
 * with the screen edge would draw a line across the bottom of the display.
 *
 * <p>The surface alpha is much higher than the capsule's (0.58/0.62 against 0.36/0.38). A
 * docked bar sits over the very bottom of the page where there is rarely anything to see
 * through, so a translucent one reads as a dirty smudge rather than as glass.
 */
@Composable
internal fun DockedNavigationBar(
    labels: Int,
    openIndex: Int,
    darkTheme: Boolean,
    backdrop: com.kyant.backdrop.backdrops.LayerBackdrop?,
    onSelect: (Int) -> Unit,
) {
    val interaction = rememberNavigationDragState(openIndex, SettingsTab.entries.size, onSelect)
    val surface = if (darkTheme) Color.Black.copy(alpha = 0.58f)
    else Color.White.copy(alpha = 0.62f)
    val material = if (backdrop != null) {
        Modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { RectangleShape },
            effects = {
                vibrancy()
                blur(12.dp.toPx())
            },
            highlight = null,
            onDrawSurface = { drawRect(surface) },
        )
    } else {
        Modifier.background(surface)
    }
    Box(Modifier.fillMaxWidth()) {
    NavigationBar(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                // The library's bar carries a shadow; over a translucent material it reads
                // as a dark band along the bottom edge.
                shape = RectangleShape
                clip = true
                shadowElevation = 0f
                ambientShadowColor = Color.Transparent
                spotShadowColor = Color.Transparent
            }
            .then(material),
        color = Color.Transparent,
        showDivider = false,
        mode = if (labels == Prefs.NAV_LABELS_ICON) {
            NavigationBarDisplayMode.IconOnly
        } else {
            NavigationBarDisplayMode.IconAndText
        },
    ) {
        SettingsTab.entries.forEachIndexed { index, tab ->
            NavigationBarItem(
                selected = index == if (interaction.pressed) interaction.position.roundToInt() else openIndex,
                onClick = { onSelect(index) },
                // ImageVector directly, not an `icon = { ... }` slot: Miuix's item takes
                // the vector and draws it itself, which is what keeps its own sizing and
                // tinting in charge.
                icon = iconFor(tab),
                label = if (labels == Prefs.NAV_LABELS_ICON) "" else stringResource(tab.labelRes),
            )
        }
    }
    Box(Modifier.matchParentSize().navigationBarsPadding().navigationDragInput(interaction))
    }
}

/** The icon for one page. Shared by all three bars so they cannot disagree. */
private fun iconFor(tab: SettingsTab): ImageVector = when (tab) {
    // A house for the home page, and the sliders for the components page. The sliders used to
    // mark the home page, which was wrong the moment that page stopped being the only one:
    // its first section is a preview, and the sliders read as "controls" generally rather
    // than as "the components themselves", which is what the second page is.
    SettingsTab.Drawing -> Lucide.House
    SettingsTab.Parts -> Lucide.SlidersHorizontal
    SettingsTab.About -> Lucide.Info
}

/**
 * The floating bar: a capsule inset from the edges, with a rim highlight and a selection
 * that slides between slots.
 *
 * <p>62% width and 56dp tall, both sampled from the reference. The highlight matters more
 * here than on the docked bar: an inset capsule has an edge all the way round, and without
 * the rim it reads as a grey lozenge rather than as a piece of glass lying on the page.
 */
@Composable
internal fun FloatingNavigationBar(
    labels: Int,
    openIndex: Int,
    darkTheme: Boolean,
    backdrop: com.kyant.backdrop.backdrops.LayerBackdrop?,
    onSelect: (Int) -> Unit,
) {
    val shape = RoundedCornerShape(percent = 50)
    val surface = if (darkTheme) Color.Black.copy(alpha = 0.36f)
    else Color.White.copy(alpha = 0.38f)
    NavigationPill(
        labels = labels,
        openIndex = openIndex,
        darkTheme = darkTheme,
        backdrop = backdrop,
        shape = shape,
        surface = surface,
        widthFraction = 0.62f,
        height = 56.dp,
        lensHeight = 16.dp,
        lensAmount = 32.dp,
        chromaticAberration = false,
        onSelect = onSelect,
    )
}

/**
 * The liquid bar: the reference's three-layer composition, which is a different widget from
 * the floating one rather than a variant of it.
 *
 * <p>The layers, and why each exists:
 *
 * <ol>
 *   <li>a 64dp capsule carrying the glass and the content;</li>
 *   <li>a hidden copy of the row at 56dp, drawn with `alpha = 0` but still <em>recorded</em>
 *       into its own layer backdrop, then used as a tint source - that is how the moving
 *       selection picks up the accent colour of whatever is under it;</li>
 *   <li>the selection itself, sampling the page backdrop AND that hidden copy together, with
 *       a press-progress lens so it refracts while held.</li>
 * </ol>
 *
 * <p>Width is 0.53 x 0.82 x 1.10 of the parent, straight from the reference. That number is
 * not arbitrary: the product lands the bar at a width where three tabs still hit a comfortable
 * target, and it is narrower than the floating capsule.
 */
@Composable
internal fun LiquidNavigationBar(
    labels: Int,
    openIndex: Int,
    darkTheme: Boolean,
    backdrop: com.kyant.backdrop.backdrops.LayerBackdrop?,
    onSelect: (Int) -> Unit,
) {
    val shape = RoundedCornerShape(percent = 50)
    // 0.4 alpha in both themes, and a fixed near-black/near-white rather than the theme's
    // surface: the liquid style is meant to read as a piece of glass with its own tint, not
    // as a lighter patch of the page.
    val surface = if (darkTheme) Color(0xFF121212).copy(alpha = 0.4f)
    else Color(0xFFFAFAFA).copy(alpha = 0.4f)
    NavigationPill(
        labels = labels,
        openIndex = openIndex,
        darkTheme = darkTheme,
        backdrop = backdrop,
        shape = shape,
        surface = surface,
        widthFraction = 0.53f * 0.82f * 1.10f,
        height = 64.dp,
        lensHeight = 24.dp,
        lensAmount = 24.dp,
        chromaticAberration = true,
        liquid = true,
        onSelect = onSelect,
    )
}

/** Floating and liquid bars share input; the liquid selection has its own optical layer. */
@Composable
private fun NavigationPill(
    labels: Int,
    openIndex: Int,
    darkTheme: Boolean,
    backdrop: com.kyant.backdrop.backdrops.LayerBackdrop?,
    shape: Shape,
    surface: Color,
    widthFraction: Float,
    height: Dp,
    lensHeight: Dp,
    lensAmount: Dp,
    chromaticAberration: Boolean,
    liquid: Boolean = false,
    onSelect: (Int) -> Unit,
) {
    val count = SettingsTab.entries.size
    // The capsule was measured at 62% for the two-page bar. A third page at the same total
    // width leaves every slot a third narrower, and the longer label starts to overrun its
    // own cell. Rather than holding the width or scaling it by the count (which would put a
    // three-page bar at 93% and read as a different shape), each page past the second buys a
    // fixed 7% - enough for the label to breathe, still visibly a capsule floating on the page.
    val fitted = widthFraction + (count - 2).coerceAtLeast(0) * 0.07f
    val interaction = rememberNavigationDragState(openIndex, count, onSelect)
    val density = LocalDensity.current
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    val accent = MiuixTheme.colorScheme.primary
    val ink = MiuixTheme.colorScheme.onSurface
    val press by animateFloatAsState(
        targetValue = if (interaction.pressed || (liquid && interaction.settling)) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f),
        label = "navigationPress",
    )
    val stretch by animateFloatAsState(
        targetValue = abs(interaction.velocity),
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 350f),
        label = "navigationStretch",
    )
    val panelShift by animateFloatAsState(
        targetValue = if (liquid) with(density) { 4.dp.toPx() } *
            interaction.travel.coerceIn(-1f, 1f) else 0f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 300f),
        label = "navigationPanelShift",
    )
    val tabsBackdrop = com.kyant.backdrop.backdrops.rememberLayerBackdrop()
    val selectionBackdrop = if (liquid && backdrop != null) {
        rememberCombinedBackdrop(backdrop, tabsBackdrop)
    } else null
    val baseScale = { if (liquid) 1f + 0.025f * press else 1f - 0.03f * press }
    val material = if (backdrop != null) {
        Modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                vibrancy()
                blur(12.dp.toPx())
                lens(lensHeight.toPx(), lensAmount.toPx(),
                    depthEffect = true, chromaticAberration = chromaticAberration)
            },
            highlight = {
                Highlight.Default.copy(width = 0.75.dp, blurRadius = 0.4.dp,
                    alpha = if (darkTheme) 0.78f else 0.96f)
            },
            shadow = null,
            layerBlock = { scaleX = baseScale(); scaleY = baseScale() },
            onDrawSurface = { drawRect(surface) },
            onDrawFront = {
                if (liquid && press > 0f) {
                    val center = Offset(
                        size.width / 2f + direction *
                            (interaction.position - (count - 1) / 2f) * (size.width - 8.dp.toPx()) / count,
                        size.height / 2f,
                    )
                    drawRect(Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.16f * press.coerceIn(0f, 1f)), Color.Transparent),
                        center = center,
                        radius = size.height * 1.5f,
                    ))
                }
            },
        )
    } else {
        Modifier.graphicsLayer { scaleX = baseScale(); scaleY = baseScale() }
            .background(surface, shape)
    }

    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        BoxWithConstraints(
            Modifier.fillMaxWidth(fitted)
                .navigationBarsPadding()
                .padding(bottom = 8.dp)
                .height(height),
        ) {
            val slot = (maxWidth - 8.dp) / count
            val slotPx = with(density) { slot.toPx() }
            Box(Modifier.matchParentSize()
                .absoluteOffset { IntOffset(panelShift.roundToInt(), 0) }
                .shadow(6.dp, shape, clip = false)
                .then(material))

            if (!liquid) {
                Box(Modifier.align(Alignment.Center)
                    .absoluteOffset {
                        IntOffset((direction * (interaction.position - (count - 1) / 2f) * slotPx).roundToInt(), 0)
                    }
                    .width(slot).height(height - 8.dp)
                    .clip(shape)
                    .background(if (darkTheme) Color.White.copy(alpha = 0.18f)
                        else Color.Black.copy(alpha = 0.08f)))
            }
            NavigationTabRow(
                labels, openIndex, ink,
                Modifier.matchParentSize()
                    .absoluteOffset { IntOffset(panelShift.roundToInt(), 0) }
                    .padding(horizontal = 4.dp),
                onSelect = onSelect,
            )

            if (liquid) {
                if (selectionBackdrop != null) {
                    // Record only the tinted source row. The lens that reads this source
                    // is a sibling, outside the recording, keeping the sampling graph acyclic.
                    Box(Modifier.matchParentSize()
                        .absoluteOffset { IntOffset(panelShift.roundToInt(), 0) }
                        .clearAndSetSemantics { }
                        .graphicsLayer { alpha = 0f }
                        .kyantLayerBackdrop(tabsBackdrop)
                        .then(material)) {
                        NavigationTabRow(
                            labels, openIndex, accent,
                            Modifier.matchParentSize().padding(horizontal = 4.dp),
                            contentScale = { 1f + 0.2f * press },
                        )
                    }
                }
                val lensScaleX = { (1f + 0.35f * press) * (1f + 0.16f * stretch) }
                val lensScaleY = { (1f + 0.35f * press) * (1f - 0.08f * stretch) }
                val lensMaterial = if (selectionBackdrop != null) {
                    Modifier.drawBackdrop(
                        backdrop = selectionBackdrop,
                        shape = { shape },
                        effects = {
                            val p = press.coerceIn(0f, 1f)
                            lens((8.dp + 8.dp * p).toPx(), (10.dp + 12.dp * p).toPx(),
                                depthEffect = true, chromaticAberration = true)
                        },
                        highlight = {
                            Highlight.Default.copy(
                                width = 0.8.dp,
                                alpha = 0.25f + 0.65f * press.coerceIn(0f, 1f),
                                style = HighlightStyle.Default(angle = 45f + 40f * interaction.velocity),
                            )
                        },
                        shadow = null,
                        // Use the backdrop's transform hook so refraction stays aligned
                        // with the page while the glass stretches.
                        layerBlock = { scaleX = lensScaleX(); scaleY = lensScaleY() },
                        onDrawSurface = {
                            val p = press.coerceIn(0f, 1f)
                            drawRect((if (darkTheme) Color.White else Color.Black).copy(alpha = 0.10f * (1f - p)))
                            drawRect(Color.White.copy(alpha = 0.035f * p))
                        },
                    )
                } else {
                    Modifier.graphicsLayer { scaleX = lensScaleX(); scaleY = lensScaleY() }
                        .background(accent.copy(alpha = 0.16f), shape)
                }
                Box(Modifier.align(Alignment.Center)
                    .absoluteOffset {
                        IntOffset((direction * (interaction.position - (count - 1) / 2f) * slotPx + panelShift).roundToInt(), 0)
                    }
                    .width(slot).height(height - 8.dp)
                    .then(lensMaterial))
            }
            // Input stays on the unscaled capsule. Enlarging the glass does not move
            // the hit targets or interfere with the system gesture area below it.
            Box(Modifier.matchParentSize().navigationDragInput(interaction))
        }
    }
}

@Composable
private fun NavigationTabRow(
    labels: Int,
    openIndex: Int,
    color: Color,
    modifier: Modifier,
    contentScale: () -> Float = { 1f },
    onSelect: ((Int) -> Unit)? = null,
) {
    Row(modifier.selectableGroup()) {
        SettingsTab.entries.forEachIndexed { index, tab ->
            val label = stringResource(tab.labelRes)
            val showIcon = labels != Prefs.NAV_LABELS_TEXT
            val showLabel = labels != Prefs.NAV_LABELS_ICON
            val action = if (onSelect != null) Modifier.selectable(
                selected = index == openIndex,
                interactionSource = null,
                indication = null,
                role = Role.Tab,
                onClick = { onSelect(index) },
            ) else Modifier
            Column(Modifier.weight(1f).fillMaxHeight().then(action),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center) {
                Column(Modifier.graphicsLayer { scaleX = contentScale(); scaleY = contentScale() },
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    if (showIcon) Icon(
                        imageVector = iconFor(tab),
                        contentDescription = if (showLabel) null else label,
                        tint = color,
                        modifier = Modifier.size(23.dp),
                    )
                    if (showLabel) Text(
                        text = label,
                        color = color,
                        style = TextStyle(fontSize = if (showIcon) 12.3.sp else 14.sp,
                            fontWeight = FontWeight.Medium, textAlign = TextAlign.Center),
                        maxLines = 1,
                        modifier = if (showIcon) Modifier.padding(top = 1.6.dp) else Modifier,
                    )
                }
            }
        }
    }
}
