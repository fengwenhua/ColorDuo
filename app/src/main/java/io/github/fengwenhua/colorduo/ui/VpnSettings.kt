/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.fengwenhua.colorduo.Prefs
import io.github.fengwenhua.colorduo.R
import io.github.fengwenhua.colorduo.TrioSettings
import top.yukonga.miuix.kmp.basic.ColorPalette
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun VpnSettings(settings: TrioSettings, update: ((SettingsRepository) -> Unit) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(stringResource(R.string.vpn_color_summary), style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        ColorPalette(color = Color(settings.vpnColor),
            onColorChanged = { picked -> update { it.setVpnColor(picked.toArgb()) } },
            modifier = Modifier.padding(top = 12.dp))
        TextButton(text = stringResource(R.string.vpn_color_reset),
            onClick = { update { it.setVpnColor(Prefs.DEF_VPN_COLOR) } })
    }
}
