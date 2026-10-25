/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.fengwenhua.colorduo.CompatibilityReport
import io.github.fengwenhua.colorduo.R
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.preference.ArrowPreference

@Composable
internal fun CompatibilityReportCard(showMessage: (String) -> Unit) {
    val context = LocalContext.current.applicationContext
    var pending by remember { mutableStateOf(false) }
    val copied = stringResource(R.string.compatibility_report_copied)
    val failed = stringResource(R.string.compatibility_report_copy_failed)
    Card {
        ArrowPreference(
            title = stringResource(R.string.compatibility_report_title),
            summary = stringResource(R.string.compatibility_report_summary),
            enabled = !pending,
            insideMargin = SettingsItemMargin,
            onClick = {
                pending = true
                CompatibilityReport.request(context) { report ->
                    val success = runCatching {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("ColorDuo compatibility", report))
                    }.isSuccess
                    pending = false
                    showMessage(if (success) copied else failed)
                }
            },
        )
    }
}
