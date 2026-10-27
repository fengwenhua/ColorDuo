/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.fengwenhua.colorduo.ColorOsFontPublisher
import io.github.fengwenhua.colorduo.Prefs
import io.github.fengwenhua.colorduo.R
import io.github.fengwenhua.colorduo.TrioSettings
import top.yukonga.miuix.kmp.preference.ArrowPreference

@Composable
internal fun StatusBarHeightSettings(settings: TrioSettings, update: ((SettingsRepository) -> Unit) -> Unit) {
    val supported = ColorOsFontPublisher.supportedDevice()
    IntSlider(
        value = settings.statusBarHeightPercent,
        min = Prefs.MIN_STATUS_BAR_HEIGHT,
        max = Prefs.MAX_STATUS_BAR_HEIGHT,
        step = 5,
        title = stringResource(R.string.status_bar_height_title),
        summary = stringResource(if (supported) R.string.status_bar_height_summary else R.string.status_bar_height_unsupported),
        enabled = settings.enabled && supported,
        valueTextSuffix = "%",
        onValueChange = { value -> update { it.setStatusBarHeight(value) } },
    )
    ArrowPreference(
        title = stringResource(R.string.status_bar_height_reset),
        summary = stringResource(R.string.status_bar_height_default),
        insideMargin = SettingsItemMargin,
        enabled = supported && settings.statusBarHeightPercent != Prefs.DEF_STATUS_BAR_HEIGHT,
        onClick = { update { it.setStatusBarHeight(Prefs.DEF_STATUS_BAR_HEIGHT) } },
    )
}
