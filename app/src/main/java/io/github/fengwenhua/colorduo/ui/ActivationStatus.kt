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

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.fengwenhua.colorduo.R
import io.github.fengwenhua.colorduo.TrioAppearance
import io.github.fengwenhua.colorduo.TrioSettings
import io.github.libxposed.service.XposedService
import androidx.compose.foundation.clickable
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

/**
 * The top block of the about page: a tall "activated" card on the left and two
 * short statistic cards stacked on the right.
 *
 * <p>The size is driven by aspectRatio(1f) rather than a fixed height, matching
 * the reference: the side length follows the available width on any device, so
 * the block keeps its proportions instead of looking squat on a tablet.
 */
@Composable
internal fun ActivatedRow(settings: TrioSettings, service: XposedService?) {
    val context = LocalContext.current
    val module = remember(context) { readModuleInfo(context) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ActivatedCard(
            active = service != null,
            version = module.moduleVersion,
            modifier = Modifier.weight(1f).aspectRatio(1f),
        )
        Column(
            modifier = Modifier.weight(1f).aspectRatio(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatCard(
                label = stringResource(R.string.about_scope_count),
                value = scopeCount(service).toString(),
                modifier = Modifier.weight(1f),
            )
            StatCard(
                label = stringResource(R.string.about_enabled_count),
                value = enabledCount(settings).toString(),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * The green activation card: state as a heavy title, module version underneath,
 * and the ring-and-check badge bleeding off the bottom-right corner.
 */
@Composable
private fun ActivatedCard(active: Boolean, version: String, modifier: Modifier = Modifier) {
    val dark = isDarkTheme()
    val observationBuild = io.github.fengwenhua.colorduo.BuildConfig.COLOROS_STAGE == "observe" &&
        android.os.Build.MANUFACTURER.lowercase(java.util.Locale.ROOT) in setOf("oppo", "oneplus", "realme")
    val batteryBuild = io.github.fengwenhua.colorduo.BuildConfig.COLOROS_STAGE == "battery" &&
        android.os.Build.MANUFACTURER.lowercase(java.util.Locale.ROOT) in setOf("oppo", "oneplus", "realme")
    val networkBuild = io.github.fengwenhua.colorduo.BuildConfig.COLOROS_STAGE == "network" &&
        android.os.Build.MANUFACTURER.lowercase(java.util.Locale.ROOT) in setOf("oppo", "oneplus", "realme")
    val ringBuild = io.github.fengwenhua.colorduo.BuildConfig.COLOROS_STAGE in setOf("ring", "type") &&
        android.os.Build.MANUFACTURER.lowercase(java.util.Locale.ROOT) in setOf("oppo", "oneplus", "realme")
    val capsuleBuild = io.github.fengwenhua.colorduo.BuildConfig.COLOROS_STAGE in setOf("capsule", "font") &&
        android.os.Build.MANUFACTURER.lowercase(java.util.Locale.ROOT) in setOf("oppo", "oneplus", "realme")
    val content = if (dark) MiuixTheme.colorScheme.onSurface else ActivatedContent

    Card(
        modifier = modifier.fillMaxHeight(),
        colors = CardDefaults.defaultColors(
            color = if (dark) ActivatedContainerDark else ActivatedContainer,
            contentColor = content,
        ),
        // These cards are read-outs, not buttons: they have nowhere to navigate
        // to, so they stay non-clickable. Miuix wires the press feedback on
        // independently of onClick, which gives the MIUI "give" under the finger
        // without pretending there is an action behind it. Tilt is the variant
        // the reference page uses on its equivalent cards.
        pressFeedbackType = PressFeedbackType.Tilt,
    ) {
        // clipToBounds rather than clip(RoundedCornerShape): the card has already
        // drawn its squircle outline, and clipping to a plain rounded rectangle
        // here would square off the corners the user is looking at.
        Box(modifier = Modifier.fillMaxSize().clipToBounds()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
            ) {
                Text(
                    text = stringResource(
                        if (capsuleBuild) R.string.about_coloros_capsule
                        else if (ringBuild) R.string.about_coloros_ring
                        else if (networkBuild) R.string.about_coloros_network
                        else if (batteryBuild) R.string.about_coloros_battery
                        else if (observationBuild) R.string.about_coloros_observer
                        else if (active) R.string.about_activated else R.string.about_inactive,
                    ),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = if (capsuleBuild) {
                        stringResource(R.string.about_coloros_capsule_hint)
                    } else if (ringBuild) {
                        stringResource(R.string.about_coloros_ring_hint)
                    } else if (networkBuild) {
                        stringResource(R.string.about_coloros_network_hint)
                    } else if (batteryBuild) {
                        stringResource(R.string.about_coloros_battery_hint)
                    } else if (observationBuild) {
                        stringResource(R.string.about_coloros_observer_hint)
                    } else if (active) {
                        stringResource(R.string.about_module_version, version)
                    } else {
                        stringResource(R.string.about_inactive_hint)
                    },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = content,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            // The badge is not aligned to the corner: it is nudged 27x31 further
            // out so a different slice of the ring survives the clip. The vector
            // is the reference's own asset rather than a redrawing of it -the
            // ring-to-viewport ratio is what the eye compares, and it is easy to
            // get wrong by hand.
            Box(
                modifier = Modifier.fillMaxSize().offset(27.dp, 31.dp),
                contentAlignment = Alignment.BottomEnd,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_check_circle_outline),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(if (dark) ActivatedBadgeDark else ActivatedBadge),
                    modifier = Modifier.size(110.dp),
                )
            }
        }
    }
}

/** One of the two short right-hand cards: a quiet label over a large figure. */
@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxHeight(),
        // Same read-out-not-a-button treatment as [ActivatedCard]; the two cards
        // sit side by side, so they must not disagree about how they respond.
        pressFeedbackType = PressFeedbackType.Tilt,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(14.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Text(
                text = value,
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/** Palette for the activation card, sampled off the reference screenshot. */
private val ActivatedContainer = Color(0xFFE4F9E5)
private val ActivatedContent = Color(0xFF101010)
private val ActivatedBadge = Color(0xFF7FD78A)

/** Dark-mode counterparts; the reference only shows the light theme. */
private val ActivatedContainerDark = Color(0xFF1D2A1F)
private val ActivatedBadgeDark = Color(0xFF6FC77C)

/**
 * How many packages the framework will load this module into; the reference page
 * shows an equivalent count, and it is the fastest way to spot a mis-scoped
 * install.
 */
private fun scopeCount(service: XposedService?): Int {
    if (service == null) return 0
    return runCatching { service.scope.size }.getOrDefault(0)
}

/**
 * How many of the appearance switches are on, as a single at-a-glance figure.
 *
 * <p>Derived from [TrioAppearance] rather than read off [TrioSettings] directly.
 * The switches gate one another, and the count used to ignore both the master
 * switch and those gates, so it could report a healthy total while the glyph
 * drew nothing at all.
 */
private fun enabledCount(settings: TrioSettings): Int {
    val a = TrioAppearance.of(settings)
    if (!a.glyph) return 0
    return listOf(
        a.wifi,
        // The reading counts in either position: in the ring it is the dot row,
        // outside it is the bars the out-of-ring view draws.
        a.mobile,
        // The second row only exists when the mobile level is drawn as dots, so
        // out of ring - where nothing is drawn along the bottom at all - it is
        // not a second thing on screen however the switch is set.
        a.dualSim && a.signalDots(),
        a.value,
        a.drawsBolt(),
        // The network type is a three-way mode now; anything but "off" counts as on.
        a.typeAnywhere(),
    ).count { it }
}
