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

import androidx.activity.compose.BackHandler
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.Spacer
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.DropdownArrowEndAction
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.ListPopupDefaults
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.rememberListPopupLayoutInfo
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

internal val LocalDropdownBackdrop = compositionLocalOf<LayerBackdrop?> { null }

@Composable
internal fun GlassDropdownPreference(
    items: List<String>,
    selectedIndex: Int,
    title: String,
    summary: String? = null,
    insideMargin: androidx.compose.foundation.layout.PaddingValues = androidx.compose.foundation.layout.PaddingValues(16.dp),
    enabled: Boolean = true,
    onSelectedIndexChange: (Int) -> Unit,
) {
    val selected = selectedIndex.coerceIn(0, items.lastIndex.coerceAtLeast(0))
    GlassDropdownPreference(
        entry = DropdownEntry(items.mapIndexed { index, text ->
            DropdownItem(text = text, selected = index == selected,
                onClick = { onSelectedIndexChange(index) })
        }),
        title = title,
        summary = summary,
        insideMargin = insideMargin,
        enabled = enabled,
    )
}

@Composable
internal fun GlassDropdownPreference(
    entry: DropdownEntry,
    title: String,
    summary: String? = null,
    modifier: Modifier = Modifier,
    insideMargin: androidx.compose.foundation.layout.PaddingValues = androidx.compose.foundation.layout.PaddingValues(16.dp),
    enabled: Boolean = entry.enabled,
) {
    var expanded by remember { mutableStateOf(false) }
    val available = enabled && entry.enabled && entry.items.isNotEmpty()
    val selectedValue = entry.items.firstOrNull { it.selected }?.text
    val backdrop = LocalDropdownBackdrop.current

    Box(modifier) {
        BasicComponent(
            modifier = Modifier.fillMaxWidth(),
            interactionSource = remember { MutableInteractionSource() },
            title = title,
            summary = summary ?: selectedValue,
            endActions = {
                if (summary != null && selectedValue != null) {
                    Text(selectedValue, style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(end = 8.dp))
                }
                DropdownArrowEndAction(MiuixTheme.colorScheme.onSurfaceVariantActions)
            },
            onClick = { if (available) expanded = true },
            role = Role.DropdownList,
            holdDownState = false,
            enabled = available,
            insideMargin = insideMargin,
        )
        GlassDropdownPopup(
            entry = entry,
            show = expanded,
            backdrop = backdrop,
            onDismiss = { expanded = false },
        )
    }
}

@Composable
private fun GlassDropdownPopup(
    entry: DropdownEntry,
    show: Boolean,
    backdrop: LayerBackdrop?,
    onDismiss: () -> Unit,
) {
    val progress = remember { Animatable(0f) }
    val alpha = remember { Animatable(0f) }
    var mounted by remember { mutableStateOf(false) }
    val dismiss by rememberUpdatedState(onDismiss)
    var anchor by remember { mutableStateOf(IntRect.Zero) }
    var hostPosition by remember { mutableStateOf(Offset.Zero) }
    var surfaceSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val shadowPadding = with(density) { 28.dp.roundToPx() }

    LaunchedEffect(show) {
        if (show) {
            mounted = true
            launch { progress.animateTo(1f, spring(dampingRatio = .78f, stiffness = 232f)) }
            launch { alpha.animateTo(1f, tween(120)) }
        } else if (mounted) {
            launch { progress.animateTo(0f, spring(dampingRatio = .78f, stiffness = 400f)) }
            alpha.animateTo(0f, tween(180))
            mounted = false
        }
    }
    if (!show && !mounted) return

    Spacer(Modifier.onGloballyPositioned { coordinates ->
        coordinates.parentLayoutCoordinates?.let { parent ->
            val position = parent.positionInWindow()
            anchor = IntRect(position.x.toInt(), position.y.toInt(),
                position.x.toInt() + parent.size.width, position.y.toInt() + parent.size.height)
        }
    })
    if (anchor == IntRect.Zero) return

    val layoutInfo = rememberListPopupLayoutInfo(
        alignment = PopupPositionProvider.Align.End,
        popupPositionProvider = ListPopupDefaults.DropdownPositionProvider,
        parentBounds = anchor,
        popupContentSize = surfaceSize,
    )
    val popupState = remember { mutableStateOf(mounted) }
    popupState.value = mounted
    MiuixPopupUtils.Companion.PopupLayout(
        visible = popupState,
        enableWindowDim = false,
        enableBackHandler = false,
        enterTransition = EnterTransition.None,
        exitTransition = ExitTransition.None,
        renderInRootScaffold = true,
    ) {
        BackHandler(enabled = show, onBack = dismiss)
        Layout(
            content = {
                GlassDropdownSurface(progress.value, alpha.value, backdrop,
                    onSizeChanged = { surfaceSize = it }) {
                    ListPopupColumn {
                        entry.items.forEachIndexed { index, item ->
                            DropdownImpl(
                                item = item,
                                optionSize = entry.items.size,
                                isSelected = item.selected,
                                index = index,
                                dropdownColors = DropdownDefaults.dropdownColors(
                                    containerColor = Color.Transparent,
                                    selectedContainerColor = Color.Transparent,
                                ),
                                enabled = entry.enabled && item.enabled,
                                onSelectedIndexChange = { selected ->
                                    entry.items[selected].onClick?.invoke()
                                    dismiss()
                                },
                            )
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
                .onGloballyPositioned { hostPosition = it.positionInWindow() }
                .pointerInput(Unit) { detectTapGestures(onTap = { dismiss() }) },
        ) { measurables, constraints ->
            val maxHeight = layoutInfo.windowBounds.height - layoutInfo.popupMargin.top - layoutInfo.popupMargin.bottom
            val placeable = measurables.single().measure(constraints.copy(
                minWidth = 0, minHeight = 0, maxHeight = maxHeight.coerceAtLeast(1),
            ))
            val position = ListPopupDefaults.DropdownPositionProvider.calculatePosition(
                anchor, layoutInfo.windowBounds, layoutDirection,
                IntSize(placeable.width, placeable.height), layoutInfo.popupMargin,
                PopupPositionProvider.Align.End,
            )
            val safeTop = (anchor.height * .10f).roundToInt()
            val minY = layoutInfo.windowBounds.top + layoutInfo.popupMargin.top
            val maxY = (layoutInfo.windowBounds.bottom - placeable.height - layoutInfo.popupMargin.bottom)
                .coerceAtLeast(minY)
            val y = (anchor.top + safeTop - shadowPadding).coerceIn(minY, maxY)
            layout(constraints.maxWidth, constraints.maxHeight) {
                placeable.place(position.x + with(density) { 18.dp.roundToPx() } - hostPosition.x.toInt(),
                    y - hostPosition.y.toInt())
            }
        }
    }
}

@Composable
private fun GlassDropdownSurface(
    progress: Float,
    alpha: Float,
    backdrop: LayerBackdrop?,
    onSizeChanged: (IntSize) -> Unit,
    content: @Composable () -> Unit,
) {
    val surface = MiuixTheme.colorScheme.surfaceContainer
    val light = surface.luminance() > .5f
    val tint = (if (light) Color(0xFFF8F8F8) else surface).copy(alpha = .58f)
    val shape = RoundedCornerShape(25.dp)
    Box(Modifier.padding(28.dp)) {
        Box(
            Modifier.sizeIn(minWidth = DropdownDefaults.MinWidth)
                .onSizeChanged(onSizeChanged)
                .graphicsLayer {
                    scaleX = .24f + .76f * progress
                    scaleY = .24f + .76f * progress
                    this.alpha = alpha
                    transformOrigin = TransformOrigin(.5f, 0f)
                }
                .then(if (backdrop != null) Modifier.drawBackdrop(
                    backdrop = backdrop,
                    shape = { shape },
                    effects = {
                        vibrancy()
                        blur(5.dp.toPx())
                        lens(10.dp.toPx(), 28.dp.toPx(), chromaticAberration = true)
                    },
                    highlight = { Highlight.Default.copy(alpha = .7f) },
                    shadow = { Shadow.Default.copy(radius = 12.dp, color = if (light) Color.Gray else Color.Black, alpha = .35f) },
                    innerShadow = null,
                    onDrawSurface = { drawRect(tint) },
                ) else Modifier.clip(shape).background(tint)),
        ) { content() }
    }
}
