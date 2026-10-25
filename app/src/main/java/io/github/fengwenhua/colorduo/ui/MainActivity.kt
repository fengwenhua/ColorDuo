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

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

/**
 * The module's settings screen.
 *
 * <p>Reachable both from the launcher (through the activity alias) and from the
 * LSPosed manager, which launches the activity advertising
 * {@code de.robv.android.xposed.category.MODULE_SETTINGS}.
 */
class MainActivity : ComponentActivity() {

    override fun onStop() {
        io.github.fengwenhua.colorduo.BufferedSettings.get(applicationContext).flushBlocking()
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val repository = remember { SettingsRepository(applicationContext) }
            MiuixTheme(controller = remember { ThemeController() }) {
                SettingsScreen(repository)
            }
        }
    }
}
