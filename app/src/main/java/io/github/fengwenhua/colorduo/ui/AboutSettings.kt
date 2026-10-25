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

import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.pm.PackageInfoCompat
import io.github.fengwenhua.colorduo.Prefs
import io.github.fengwenhua.colorduo.R
import io.github.fengwenhua.colorduo.TrioSettings
import io.github.libxposed.service.XposedService
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme


/** About: activation state, the counters, module info, updates and the log switch. */
internal fun LazyListScope.aboutTab(
    settings: TrioSettings,
    service: XposedService?,
    updater: UpdateController,
    showMessage: (String) -> Unit,
    update: ((SettingsRepository) -> Unit) -> Unit,
) {
    item { ActivatedRow(settings = settings, service = service) }
    item { InfoCard(service = service) }
    item { UpdateCard(updater, showMessage) }
    item {
        Card {
            SwitchPreference(
                checked = settings.debugLog,
                onCheckedChange = { value -> update { it.setDebugLog(value) } },
                title = stringResource(R.string.debug_title),
                summary = stringResource(R.string.debug_summary),
                insideMargin = SettingsItemMargin,
            )
        }
    }
    // issue #38: the launcher entry is a separate activity-alias, so it can be switched off
    // without touching the LSPosed entry point (which is the MainActivity itself, advertising
    // MODULE_SETTINGS). Disabling the alias is the documented way to hide an icon: unlike a
    // launcher-side shortcut it survives reboots, and unlike removing the filter it leaves the
    // activity reachable from the manager.
    //
    // A card of its own, like the log switch above. It used to sit inside InfoCard's column,
    // under the key/value rows - where its own inside margin and its 4dp title/summary gap
    // had nothing to align against, so the row read as inset against the values above it and
    // as a slab against the footnote below.
    item {
        Card {
            val context = LocalContext.current
            // Asked of the component rather than cached across launches: the alias can also be
            // toggled by the system (a launcher cleaning up, a restore), and a remembered flag
            // would then show a switch that disagrees with what the launcher does.
            var iconHidden by remember(context) { mutableStateOf(isLauncherIconHidden(context)) }
            SwitchPreference(
                checked = iconHidden,
                title = stringResource(R.string.about_hide_icon),
                summary = stringResource(R.string.about_hide_icon_summary),
                insideMargin = SettingsItemMargin,
                onCheckedChange = { hide ->
                    setLauncherIconHidden(context, hide)
                    iconHidden = hide
                },
            )
        }
    }
    // The shell's own appearance. It lives on the About page rather than the drawing page
    // because it is about this screen, not about the glyph the drawing page edits.
    item {
        Column {
            SectionTitle(stringResource(R.string.nav_section))
            Card {
                GlassDropdownPreference(
                    title = stringResource(R.string.nav_style_title),
                    items = listOf(
                        stringResource(R.string.nav_style_docked),
                        stringResource(R.string.nav_style_floating),
                        stringResource(R.string.nav_style_liquid),
                    ),
                    selectedIndex = Prefs.normalizeNavStyle(settings.navStyle),
                    insideMargin = SettingsItemMargin,
                ) { index -> update { it.setNavStyle(index) } }
                GlassDropdownPreference(
                    title = stringResource(R.string.nav_labels_title),
                    items = listOf(
                        stringResource(R.string.nav_labels_both),
                        stringResource(R.string.nav_labels_icon),
                        stringResource(R.string.nav_labels_text),
                    ),
                    selectedIndex = Prefs.normalizeNavLabels(settings.navLabels),
                    insideMargin = SettingsItemMargin,
                ) { index -> update { it.setNavLabels(index) } }
            }
        }
    }
}

/**
 * The release notes in full, after a check turns one up.
 *
 * <p>The card can only show one elided line, and a release's notes are the only thing a user
 * can weigh before committing to an install - so they get a dialog of their own rather than a
 * two-line excerpt. Opened by the check itself, not by a second tap: the answer to "is there a
 * new version" is the notes, and asking the user to go find them is the interruption, not the
 * dialog.
 *
 * @param info the release that was found, or null when there is nothing to show. Passing null
 *   rather than a separate `show` flag keeps the two from disagreeing.
 */
@Composable
internal fun UpdateNotesDialog(
    info: UpdateInfo?,
    current: String,
    onDismiss: () -> Unit,
    onDownload: (UpdateInfo) -> Unit,
) {
    if (info == null) {
        return
    }
    OverlayDialog(
        show = true,
        title = stringResource(R.string.update_dialog_title, info.version),
        summary = stringResource(
            R.string.update_dialog_summary,
            current,
            formatSize(info.apkSize),
        ),
        onDismissRequest = onDismiss,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.update_dialog_notes_title),
                style = MiuixTheme.textStyles.subtitle,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            // Scrollable and height-capped: release notes run to paragraphs, and an unbounded
            // body would push the buttons off the bottom of the dialog.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 260.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                val notes = info.notes.trim()
                Text(
                    text = notes.ifEmpty { stringResource(R.string.update_dialog_no_notes) },
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    text = stringResource(R.string.update_dialog_later),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = { onDownload(info) },
                    colors = ButtonDefaults.buttonColorsPrimary(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = stringResource(R.string.update_download))
                }
            }
        }
    }
}

/**
 * The in-app updater.
 *
 * <p>The card is a short stack of Miuix preference rows, one per action the
 * current state actually offers 鈥?check, download, install, open the release
 * page 鈥?with the state itself spelled out as the summary of the first row.
 * A row only exists while its verb is possible, so the card never shows an
 * action that would do nothing, and it never says the same words twice the way
 * a heading plus a button of the same name did.
 *
 * <p>The repository row is the one deliberate exception to that rule, and it
 * sits last so it stays out of the way: reading the source is possible in every
 * state, including the ones the card handles worst 鈥?no release published yet, or
 * a check that just failed.
 *
 * <p>The state lives in [updater], which the screen remembers, so scrolling this
 * card out of view and back does not restart a download in flight.
 */
@Composable
internal fun UpdateCard(updater: UpdateController, showMessage: (String) -> Unit) {
    val state = updater.state

    // Null only for Ready, where the row's own title is already the whole status
    // and a summary would print the same sentence a second time.
    val status: String? = when (state) {
        is UpdateState.Idle -> stringResource(R.string.update_summary_idle)
        is UpdateState.Checking -> stringResource(R.string.update_checking)
        is UpdateState.UpToDate -> stringResource(R.string.update_uptodate, state.current)
        is UpdateState.NoRelease -> stringResource(R.string.update_no_release)
        is UpdateState.Available -> stringResource(R.string.update_available, state.info.version)
        is UpdateState.Downloading -> stringResource(
            R.string.update_downloading,
            (state.progress * 100f).toInt(),
        )

        is UpdateState.Ready -> null
        // The raw reason is diagnostic text 鈥?an HTTP code, a socket error 鈥?and
        // would lose its meaning if it were run through a string resource.
        is UpdateState.Failed -> listOfNotNull(
            stringResource(R.string.update_failed),
            state.detail?.takeIf { it.isNotBlank() },
        ).joinToString(" · ")
    }

    // A download reports itself under the row it belongs to, where the bar spans
    // the same width as the text instead of sitting in a slot of its own.
    val progressBar: (@Composable () -> Unit)? = if (state is UpdateState.Downloading) {
        {
            LinearProgressIndicator(
                // Null means the server never declared a length: the bar then
                // animates instead of claiming a progress it does not have.
                progress = state.progress.takeIf { it > 0f },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    } else {
        null
    }

    // Both states that are waiting on the network replace the row's arrow with a
    // spinner, so the row still says "working" once the summary word has stopped
    // being the thing the eye lands on. The branches are written out one by one
    // because the arrow is the default and cannot be restored by passing null.
    val busy = state is UpdateState.Checking || state is UpdateState.Downloading

    // Each row is titled with the verb it performs, so a failure offers "retry"
    // and a download in flight says "download", instead of both calling
    // themselves "check for updates" while doing something else.
    val rowTitle = when (state) {
        is UpdateState.Downloading -> stringResource(R.string.update_download)
        is UpdateState.Failed -> stringResource(R.string.update_retry)
        // Not "check for updates": this row no longer offers a check, because a
        // fresh check would drop the download the next row is about to install.
        is UpdateState.Ready -> stringResource(R.string.update_ready)
        else -> stringResource(R.string.update_title)
    }

    Card {
        // The one row that is always there: it reports the state and, whenever
        // nothing is in flight and nothing is waiting to be installed, offers
        // another look at the release feed. It is also the retry action after a
        // failure, because its summary is the failure 鈥?asking the user to press a
        // second row called "retry" to find that out would be one row too many.
        if (busy) {
            ArrowPreference(
                title = rowTitle,
                summary = status,
                insideMargin = SettingsItemMargin,
                enabled = false,
                endActions = {
                    Box(
                        modifier = Modifier.padding(end = 8.dp).size(26.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(size = 20.dp)
                    }
                },
                bottomAction = progressBar,
            )
        } else if (state is UpdateState.Ready) {
            // Reporting only, and deliberately not tappable: checking again would
            // replace "downloaded" with "up to date" and take the install row down
            // with it, costing the user a second download of a file already on
            // disk. The empty endActions removes the arrow, which would otherwise
            // promise an action this row does not have.
            ArrowPreference(
                title = rowTitle,
                summary = status,
                insideMargin = SettingsItemMargin,
                endActions = {},
            )
        } else {
            ArrowPreference(
                title = rowTitle,
                summary = status,
                insideMargin = SettingsItemMargin,
                onClick = { updater.check() },
            )
        }

        if (state is UpdateState.Available) {
            // What the install would cost: the version being replaced and the
            // download size, so the choice can be made before tapping.
            val notes = state.info.notes.lines().firstOrNull { it.isNotBlank() }
            // The release notes are the only thing the user can weigh before
            // committing to an install, so a short excerpt earns its place.
            val noteExcerpt: (@Composable () -> Unit)? = if (notes == null) {
                null
            } else {
                {
                    Text(
                        text = notes,
                        fontSize = 13.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }

            ArrowPreference(
                title = stringResource(R.string.update_download),
                summary = stringResource(
                    R.string.update_available_summary,
                    state.current,
                    formatSize(state.info.apkSize),
                ),
                insideMargin = SettingsItemMargin,
                bottomAction = noteExcerpt,
                onClick = { updater.download(state.info) },
            )
        }

        if (state is UpdateState.Ready) {
            // Resolved before the click, not inside it: a string resource needs a
            // composable context, and the snackbar is raised from a lambda.
            val refusedHint = stringResource(R.string.update_permission_needed)
            val failedHint = stringResource(R.string.update_install_failed)
            val invalidHint = stringResource(R.string.update_invalid_package)
            ArrowPreference(
                title = stringResource(R.string.update_install),
                summary = stringResource(R.string.update_ready_summary),
                insideMargin = SettingsItemMargin,
                onClick = {
                    when (updater.install(state.file)) {
                        InstallResult.Started -> Unit

                        // Only this case is the user's to fix, and only the system
                        // settings page can hand over the grant. The message says why
                        // the screen changed under the finger.
                        InstallResult.NeedsPermission -> {
                            showMessage(refusedHint)
                            updater.openInstallPermissionScreen()
                        }

                        // Granting a permission the app already holds would lead to
                        // a settings page with nothing to change.
                        InstallResult.NoInstaller -> showMessage(failedHint)
                        InstallResult.InvalidPackage -> showMessage(invalidHint)
                    }
                },
            )
        }

        // Both states where the user may want to read more than the card can
        // hold get a way out, and only those states need one.
        if (state is UpdateState.Available || state is UpdateState.Failed) {
            ArrowPreference(
                title = stringResource(R.string.update_release_page),
                insideMargin = SettingsItemMargin,
                onClick = {
                    updater.openInBrowser(
                        (state as? UpdateState.Available)?.info?.pageUrl ?: RELEASES_PAGE,
                    )
                },
            )
        }

        // Last, and unlike every row above it, always present: the repository is
        // what the user reads when the card cannot answer 鈥?no release published,
        // a check that failed, or a changelog that explains too little 鈥?so taking
        // it away exactly when the card is least useful would be backwards.
        ArrowPreference(
            title = stringResource(R.string.update_repo),
            summary = stringResource(R.string.update_repo_summary),
            insideMargin = SettingsItemMargin,
            onClick = { updater.openInBrowser(REPO_HOME) },
        )
    }
}

/**
 * One label/value pair of the about card: a heavy dark label over a lighter grey
 * value, stacked so long values (the framework string, the scope list) stay free
 * to wrap without a second column squeezing them.
 */
@Composable
private fun InfoRow(label: String, value: String, bottomPadding: Dp = 24.dp) {
    Text(
        text = label,
        style = MiuixTheme.textStyles.headline1,
        fontWeight = FontWeight.Medium,
        color = MiuixTheme.colorScheme.onSurface,
    )
    Text(
        text = value,
        style = MiuixTheme.textStyles.body2,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        modifier = Modifier.padding(top = 2.dp, bottom = bottomPadding),
    )
}

/**
 * The reference's four identity rows first, then the two the reference has no
 * equivalent for 鈥?the scope list and the Android level are what a user actually
 * needs to report a bug, so they stay on rather than being dropped for symmetry.
 */
@Composable
private fun InfoCard(service: XposedService?) {
    val context = LocalContext.current

    // Every framework getter is a binder call into the daemon: bind them to the
    // service identity so they run when it changes rather than on every
    // recomposition.
    val unknown = stringResource(R.string.info_unknown)
    val module = remember(context) { readModuleInfo(context) }
    val framework = remember(service, unknown) { readFrameworkInfo(service, unknown) }
    val scope = remember(service, unknown) { readScope(service, unknown) }

    // fillMaxWidth is required on both the card and its content column: Miuix's Card
    // wraps to its content, and this card's text is narrower than the sibling cards,
    // so without it the About card renders visibly inset on the right.
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            InfoRow(stringResource(R.string.info_system_version), module.systemVersion)
            InfoRow(stringResource(R.string.info_module_version), module.moduleVersion)
            InfoRow(stringResource(R.string.info_framework), framework)
            InfoRow(stringResource(R.string.info_device_model), module.deviceModel)
            InfoRow(stringResource(R.string.info_scope), scope)
            InfoRow(
                label = stringResource(R.string.info_android_version),
                value = module.androidVersion,
                bottomPadding = 12.dp,
            )
            Text(
                text = stringResource(R.string.about_summary),
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/**
 * Whether the launcher entry is currently switched off.
 *
 * <p>Asked of the component rather than stored: the alias can also be toggled by the
 * system (a launcher cleaning up, a restore), and a cached flag would then show the
 * user a switch that disagrees with what the launcher does.
 */
private fun isLauncherIconHidden(context: android.content.Context): Boolean {
    val component = android.content.ComponentName(context.packageName,
            context.packageName + ".ui.MainActivityAlias")
    return try {
        val state = context.packageManager.getComponentEnabledSetting(component)
        state == android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    } catch (t: Throwable) {
        false
    }
}

/**
 * Shows or hides the launcher icon by toggling the alias.
 *
 * <p>The alias is a separate component from MainActivity, which is what makes this safe:
 * the LSPosed manager launches the activity directly (it advertises MODULE_SETTINGS), so
 * hiding the icon does not cost the user their way in. The platform persists the
 * component state, so the choice survives a reboot.
 */
private fun setLauncherIconHidden(context: android.content.Context, hidden: Boolean) {
    val component = android.content.ComponentName(context.packageName,
            context.packageName + ".ui.MainActivityAlias")
    val state = if (hidden) {
        android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    } else {
        android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
    }
    try {
        context.packageManager.setComponentEnabledSetting(component, state,
                android.content.pm.PackageManager.DONT_KILL_APP)
    } catch (t: Throwable) {
        android.util.Log.w("ColorDuo", "cannot toggle launcher alias", t)
    }
}

/** Everything the about card renders about this installation. */
internal data class ModuleInfo(
    val moduleVersion: String,
    val systemVersion: String,
    val androidVersion: String,
    val deviceModel: String,
)

/**
 * Reads the values that never change while the process lives.
 *
 * [Build.VERSION.INCREMENTAL] and [Build.DISPLAY] are both shown when they differ:
 * on MIUI the former carries the ROM build (OS4.0.0.27.XNCCNXM) and the latter the
 * underlying AOSP build id, and either one alone leaves the user guessing.
 */
internal fun readModuleInfo(context: Context): ModuleInfo {
    val packageInfo = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0)
    }.getOrNull()
    val versionName = packageInfo?.versionName ?: "?"
    val versionCode = packageInfo?.let { PackageInfoCompat.getLongVersionCode(it) } ?: 0L
    val systemVersion = listOf(Build.VERSION.INCREMENTAL, Build.DISPLAY)
        .filter { !it.isNullOrBlank() }
        .distinct()
        .joinToString(" · ")
    return ModuleInfo(
        moduleVersion = "$versionName ($versionCode)",
        systemVersion = systemVersion.ifBlank { "?" },
        androidVersion = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        deviceModel = readDeviceName(context),
    )
}

/**
 * The name the user sees in Settings, not the one printed on the box.
 *
 * <p>[Build.MODEL] is the SKU (`23127PN0CC`); the marketing name (`Xiaomi 14`)
 * only exists as [Settings.Global.DEVICE_NAME], and `Build.MARKET_NAME` is not
 * present in this SDK. A device that has never had the setting written falls
 * back to the model rather than showing an empty row.
 */
private fun readDeviceName(context: Context): String {
    val name = runCatching {
        Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
    }.getOrNull()
    return name?.takeIf { it.isNotBlank() } ?: Build.MODEL.orEmpty()
}

/**
 * Every getter here is a binder call into the framework daemon, so all of them are
 * wrapped: a service that died between binding and this call must degrade to
 * [unknown] rather than take the settings screen down with it.
 */
private fun readFrameworkInfo(service: XposedService?, unknown: String): String {
    if (service == null) return unknown
    return runCatching {
        val name = service.frameworkName
        val version = service.frameworkVersion
        val code = service.frameworkVersionCode
        "$name $version ($code) API ${service.apiVersion}"
    }.getOrDefault(unknown)
}

private fun readScope(service: XposedService?, unknown: String): String {
    if (service == null) return unknown
    val packages = runCatching { service.scope }.getOrNull()
    if (packages.isNullOrEmpty()) return unknown
    return packages.joinToString("\n")
}
