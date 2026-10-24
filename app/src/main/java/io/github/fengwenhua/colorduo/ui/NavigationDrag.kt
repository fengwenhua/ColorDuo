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

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** One gesture/settling model for all settings bars; the route remains authoritative. */
@Composable
internal fun rememberNavigationDragState(
    selectedIndex: Int,
    tabCount: Int,
    onSelect: (Int) -> Unit,
): NavigationDragState {
    val scope = rememberCoroutineScope()
    val selection = rememberUpdatedState(selectedIndex)
    val select = rememberUpdatedState(onSelect)
    val direction = rememberUpdatedState(LocalLayoutDirection.current)
    val haptics = rememberUpdatedState(LocalHapticFeedback.current)
    val state = remember(scope, tabCount) {
        NavigationDragState(
            scope = scope,
            initialIndex = selectedIndex,
            tabCount = tabCount,
            selectedIndex = { selection.value },
            isRtl = { direction.value == LayoutDirection.Rtl },
            onSelect = { select.value(it) },
            onCrossing = {
                haptics.value.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            },
        )
    }
    LaunchedEffect(selectedIndex, state) { state.syncSelection(selectedIndex) }
    return state
}

@Stable
internal class NavigationDragState(
    private val scope: CoroutineScope,
    initialIndex: Int,
    private val tabCount: Int,
    private val selectedIndex: () -> Int,
    private val isRtl: () -> Boolean,
    private val onSelect: (Int) -> Unit,
    private val onCrossing: () -> Unit,
) {
    private val resting = Animatable(initialIndex.toFloat())
    private var fingerPosition by mutableStateOf<Float?>(null)
    private var settleJob: Job? = null
    private var velocityJob: Job? = null
    private var generation = 0
    private var destinationIndex = initialIndex
    private var lastMoveTime = 0L
    val position: Float get() = fingerPosition ?: resting.value
    var pressed by mutableStateOf(false)
        private set
    var settling by mutableStateOf(false)
        private set
    var travel by mutableFloatStateOf(0f)
        private set
    var velocity by mutableFloatStateOf(0f)
        private set
    var touch by mutableStateOf(Offset.Zero)
        private set

    fun press(position: Offset, time: Long) {
        val start = this.position
        generation++
        settleJob?.cancel()
        velocityJob?.cancel()
        fingerPosition = start
        pressed = true
        settling = false
        travel = 0f
        velocity = 0f
        touch = position
        lastMoveTime = time
    }

    fun move(delta: Float, width: Float, position: Offset, time: Long) {
        if (width <= 0f) return
        val slotWidth = width / tabCount
        val before = this.position
        val step = delta / slotWidth * if (isRtl()) -1f else 1f
        val next = (before + step).coerceIn(0f, (tabCount - 1).toFloat())
        if (before.roundToInt() != next.roundToInt()) onCrossing()
        fingerPosition = next
        travel += delta / width
        touch = position
        velocity = (step * 250f / (time - lastMoveTime).coerceAtLeast(1)).coerceIn(-1f, 1f)
        lastMoveTime = time
        velocityJob?.cancel()
        velocityJob = scope.launch {
            delay(80)
            velocity = 0f
        }
    }

    fun finish(tapPosition: Float? = null, width: Float = 1f) {
        val target = if (tapPosition != null && width > 0f) {
            val x = if (isRtl()) width - tapPosition else tapPosition
            (x / width * tabCount).toInt()
        } else {
            position.roundToInt()
        }.coerceIn(0, tabCount - 1)
        val tapped = tapPosition != null
        settleTo(target, keepLens = pressed && !tapped, tapped = tapped)
        // The route owner checks its current back stack. A recomposition-backed
        // index may still describe the previous page during rapid reversals.
        onSelect(target)
    }

    fun cancel() {
        if (pressed) settleTo(selectedIndex(), keepLens = false)
    }

    fun syncSelection(index: Int) {
        if (!pressed && destinationIndex != index) {
            settleTo(index, keepLens = settling)
        }
    }

    private fun settleTo(index: Int, keepLens: Boolean, tapped: Boolean = false) {
        val start = position
        val token = ++generation
        // Record the destination before launching: snapTo temporarily changes the
        // Animatable target, but route synchronization must not restart this motion.
        destinationIndex = index
        settleJob?.cancel()
        velocityJob?.cancel()
        pressed = false
        settling = keepLens
        travel = 0f
        velocity = 0f
        settleJob = scope.launch {
            try {
                // Keep the finger position visible until the animation has its start.
                resting.snapTo(start)
                fingerPosition = null
                resting.animateTo(
                    index.toFloat(),
                    // Taps arrive with the page transition; drags retain a spring
                    // from the finger's release position.
                    if (tapped) tween(durationMillis = 240, easing = FastOutSlowInEasing)
                    else spring(dampingRatio = 0.78f, stiffness = 420f, visibilityThreshold = 0.01f),
                )
            } finally {
                if (generation == token) settling = false
            }
        }
    }
}

/** The overlay owns taps and horizontal drags; vertical motion cancels selection. */
internal fun Modifier.navigationDragInput(state: NavigationDragState): Modifier =
    pointerInput(state) {
        try {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                down.consume()
                state.press(down.position, down.uptimeMillis)
                var pointer = down.id
                var pending = Offset.Zero
                var dragging = false
                try {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == pointer } ?: break
                        if (change.isConsumed) break
                        if (!change.pressed) {
                            change.consume()
                            val replacement = event.changes.firstOrNull { it.pressed }
                            if (replacement != null) {
                                pointer = replacement.id
                                pending = Offset.Zero
                                continue
                            }
                            if (dragging) state.finish()
                            else state.finish(change.position.x, size.width.toFloat())
                            break
                        }
                        val delta = change.position - change.previousPosition
                        change.consume()
                        if (!dragging) {
                            pending += delta
                            val slop = viewConfiguration.touchSlop
                            if (abs(pending.y) > slop && abs(pending.y) > abs(pending.x)) break
                            if (abs(pending.x) <= slop) continue
                            // Once crossed, this remains a drag even after reversing back
                            // to the starting point. Include the initial movement as well.
                            dragging = true
                            state.move(pending.x, size.width.toFloat(), change.position, change.uptimeMillis)
                        } else {
                            state.move(delta.x, size.width.toFloat(), change.position, change.uptimeMillis)
                        }
                    }
                } finally {
                    state.cancel()
                }
            }
        } finally {
            state.cancel()
        }
    }
