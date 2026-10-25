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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.fengwenhua.colorduo.R
import io.github.fengwenhua.colorduo.TrioAppearance
import io.github.fengwenhua.colorduo.TrioSettings
import androidx.compose.foundation.selection.selectable
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.ColorPalette
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TooltipBox
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Group heading. Miuix's [SmallTitle] defaults to a 28.dp vertical margin, which
 * reads as a stray indent once the page itself only insets 12.dp; the reference
 * page narrows it to 18.dp so the heading lines up with the card padding instead
 * of the card edge.
 */
@Composable
internal fun SectionTitle(title: String) {
    SmallTitle(
        text = title,
        modifier = Modifier.padding(top = 4.dp),
        insideMargin = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
    )
}

/**
 * Which role colour the picker dialog is editing.
 *
 * <p>{@code internal} rather than private because the drawing page's long-press
 * dialog offers the same colours, and it opens the same picker: a second enum
 * would be a second thing to keep in step with the switch table.
 */
internal enum class RoleColor(val labelRes: Int) {
    Low(R.string.color_low_title),
    Charging(R.string.color_charging_title),
    Critical(R.string.color_critical_title),
}
/**
 * The gate sentence for a gated row, or null when it is live.
 *
 * <p>The first dependency that is switched off wins, so a row needing two names
 * the one the user is likeliest to fix first - reading order, top to bottom.
 */
@Composable
private fun SliderSpec.firstHint(a: TrioAppearance): String? {
    val missing = hints(a).firstOrNull { !it.first } ?: return null
    return stringResource(R.string.gate_hint, stringResource(missing.second))
}

/**
 * The confirmation for resetting the three role colours.
 *
 * <p>Same [OverlayDialog] shell as the restart confirmation, so it stays inside
 * the Scaffold's content lambda for the same reason.
 */
@Composable
internal fun ResetColorsDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    OverlayDialog(
        show = show,
        title = stringResource(R.string.color_reset),
        summary = stringResource(R.string.color_reset_dialog_summary),
        onDismissRequest = onDismiss,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextButton(
                text = stringResource(R.string.cancel),
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColorsPrimary(),
                modifier = Modifier.weight(1f),
            ) {
                Text(text = stringResource(R.string.color_reset_confirm))
            }
        }
    }
}

@Composable
internal fun ColorDialog(
    role: RoleColor,
    settings: TrioSettings,
    onPick: (onDark: Boolean, argb: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var onDark by remember { mutableStateOf(true) }

    OverlayDialog(
        show = true,
        title = stringResource(role.labelRes),
        summary = stringResource(R.string.color_dialog_summary),
        onDismissRequest = onDismiss,
    ) {
        Column(modifier = Modifier.padding(horizontal = 4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BackgroundChip(
                    label = stringResource(R.string.color_on_dark),
                    selected = onDark,
                    modifier = Modifier.weight(1f),
                ) { onDark = true }
                BackgroundChip(
                    label = stringResource(R.string.color_on_light),
                    selected = !onDark,
                    modifier = Modifier.weight(1f),
                ) { onDark = false }
            }
            ColorPalette(
                color = Color(colorFor(settings, role, onDark)),
                onColorChanged = { picked -> onPick(onDark, picked.toArgb()) },
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun BackgroundChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = if (selected) {
        ButtonDefaults.buttonColors()
    } else {
        ButtonDefaults.buttonColors(
            color = MiuixTheme.colorScheme.surfaceContainerHigh,
            contentColor = MiuixTheme.colorScheme.onSurfaceContainerHigh,
        )
    }
    Button(onClick = onClick, modifier = modifier, colors = colors) {
        Text(text = label, style = MiuixTheme.textStyles.button)
    }
}

@Composable
internal fun Swatch(color: Int) {
    Box(
        modifier = Modifier
            .size(18.dp)
            .clip(CircleShape)
            .background(Color(color)),
    )
}

/**
 * Integer slider over a closed range, showing the current value at the end.
 *
 * @param step increment between selectable values; the range must divide by it.
 * @param valueTextSuffix appended to the displayed number - "dp" on the sliders
 *   whose setting is a length, so the value text says the unit the summary
 *   talks about instead of a bare figure.
 */
@Composable
internal fun IntSlider(
    value: Int,
    min: Int,
    max: Int,
    title: String,
    summary: String,
    enabled: Boolean,
    onValueChange: (Int) -> Unit,
    step: Int = 1,
    valueTextSuffix: String? = null,
) {
    val sliderContext = LocalContext.current.applicationContext
    SliderPreference(
        value = value.toFloat(),
        // Rounded here rather than stored rounded: the slider reports floats,
        // and every preset in this screen is a whole number. Snapping through
        // [step] keeps a wide range (100..900) from producing values no font
        // actually ships.
        onValueChange = { raw ->
            val snapped = Math.round(raw / step) * step
            onValueChange(snapped.coerceIn(min, max))
        },
        title = title,
        summary = summary,
        valueText = value.toString() + (valueTextSuffix ?: ""),
        enabled = enabled,
        valueRange = min.toFloat()..max.toFloat(),
        // NO discrete steps. Compose treats steps as the number of marks BETWEEN the
        // ends, so a range of 70..100 with step 1 is 29 of them - and a thumb that
        // snaps to 31 positions inside 30 integer values stops tracking the finger
        // continuously. The value is rounded to the step in onValueChange anyway, so
        // the coarse result is the same without the snapping.
        steps = 0,
        onValueChangeFinished = {
            io.github.fengwenhua.colorduo.BufferedSettings.get(sliderContext).flush()
        },
        insideMargin = SettingsItemMargin,
    )
}

/**
 * The reason a gated row is greyed out, or null when it is not gated at all.
 *
 * <p>Each gate is a dependency paired with the title of the row that owns it.
 * The first dependency that is switched off wins, so a row that needs two of
 * them names the one the user is likeliest to fix first -reading order, top
 * to bottom. Returning null rather than a generic sentence is what lets the
 * caller pass the result straight to `TooltipBox(enabled = ...)`: a row that is
 * live has nothing to explain, and a tooltip that says nothing would still eat
 * the long-press.
 */
@Composable
private fun gateHint(vararg gates: Pair<Boolean, Int>): String? {
    val missing = gates.firstOrNull { !it.first } ?: return null
    return stringResource(R.string.gate_hint, stringResource(missing.second))
}

private val RoleColor.colorFieldLabelRes: Int
    get() = R.string.color_field_summary

/**
 * The stored colour for one role on one background.
 *
 * <p>{@code internal} because the drawing page's long-press dialog draws the same
 * swatches, and showing a different value there would be showing the wrong colour.
 */
internal fun colorFor(settings: TrioSettings, role: RoleColor, onDark: Boolean): Int =
    when (role) {
        RoleColor.Critical -> if (onDark) settings.criticalOnDark else settings.criticalOnLight
        RoleColor.Low -> if (onDark) settings.lowOnDark else settings.lowOnLight
        RoleColor.Charging -> if (onDark) settings.chargingOnDark else settings.chargingOnLight
    }
