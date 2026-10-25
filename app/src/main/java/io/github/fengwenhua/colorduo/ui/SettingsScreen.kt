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

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.fengwenhua.colorduo.ColorDuoApp
import io.github.fengwenhua.colorduo.Prefs
import io.github.fengwenhua.colorduo.R
import io.github.libxposed.service.XposedService
import kotlinx.coroutines.launch
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.runtimeShaderEffect
import androidx.compose.ui.layout.layout
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.NavigationBar
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.RotateCcw
import top.yukonga.miuix.kmp.basic.SnackbarDuration
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import com.kyant.backdrop.backdrops.layerBackdrop as kyantLayerBackdrop
import top.yukonga.miuix.kmp.nav.core.rememberNavController
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.shader.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Inner padding of a preference row inside its Card. Miuix's Card carries no
 * outer margin and its preferences default to a uniform 16.dp box; the MIUI look
 * wants 18.dp against the card edge and a slightly tighter 14.dp vertical rhythm.
 *
 * <p>{@code internal} rather than private because the drawing page lays out rows
 * from {@code PartControls.kt}, and the two pages have to agree about the
 * rhythm or a shared row would sit differently depending on who drew it.
 */
internal val SettingsItemMargin = PaddingValues(horizontal = 18.dp, vertical = 14.dp)

@Composable
fun SettingsScreen(repository: SettingsRepository) {
    // Kept as explicit state objects, not just `by` delegates, because the nav
    // entries below are built once and remembered: an entry that closed over the
    // *value* of settings would keep rendering the snapshot it was built with.
    // Handing the entries these objects makes every read a real snapshot read,
    // taken while the destination composes.
    val settingsState = remember { mutableStateOf(repository.read()) }
    var settings by settingsState
    val serviceState = remember { mutableStateOf(ColorDuoApp.xposedService) }
    var service by serviceState
    var editing by remember { mutableStateOf<RoleColor?>(null) }
    // Which component's sheet is open. Owned here rather than inside the page so the sheet
    // is not torn down and rebuilt as the lazy items around it recompose.
    val partEditing = remember { mutableStateOf<TrioSettingsPart?>(null) }
    var resettingColors by remember { mutableStateOf(false) }
    var restarting by remember { mutableStateOf(false) }
    // The section the page is on *is* the top of the back stack, rather than a
    // second piece of state kept in step with it: the strip and the system back
    // gesture then read and write the same list, so they cannot disagree about
    // where the user is.
    //
    // The supertype is spelled out because the reified parameter is otherwise
    // inferred from the argument, and a stack declared as the first concrete
    // route cannot hold the other three once the process is recreated.
    val nav = rememberNavController<Route>(Route.Drawing)
    val openTab = SettingsTab.entries.firstOrNull {
        it.route == nav.backStack.lastOrNull()
    } ?: SettingsTab.Drawing
    val pageStateHolder = rememberSaveableStateHolder()
    BackHandler(enabled = nav.backStack.size > 1) { nav.pop() }

    // The framework service can bind or die at any moment; both the status row
    // and every remote write have to follow it.
    DisposableEffect(repository) {
        val listener: (XposedService?) -> Unit = { service = it }
        ColorDuoApp.addServiceListener(listener)
        onDispose { ColorDuoApp.removeServiceListener(listener) }
    }

    // Held here rather than inside the About tab: a LazyColumn disposes items
    // that scroll out of view, and a download in flight must survive the user
    // flicking over to another tab and back.
    val context = LocalContext.current
    val updater = remember(context) { UpdateController(context.applicationContext) }
    DisposableEffect(updater) { onDispose { updater.dispose() } }

    // Same reason as the updater itself: the "release notes" dialog is dismissed by the user,
    // and scrolling the card out of view disposes the item that would otherwise hold that
    // answer. Kept for the process lifetime, keyed by version - a newer release has a
    // different version string, so its notes open again, which is the point: it is different
    // news.
    var dismissedUpdateVersion by rememberSaveable { mutableStateOf<String?>(null) }

    // Same reasoning as the updater: the dialog can be dismissed mid-kill, and a
    // root prompt that outlives the dialog still has to land somewhere.
    val restarter = remember { RestartController() }
    DisposableEffect(restarter) { onDispose { restarter.dispose() } }

    // Single funnel: apply the write, then re-read so the preview never shows a
    // value the repository did not accept (clamping happens on read).
    fun update(block: (SettingsRepository) -> Unit) {
        block(repository)
        settings = repository.read()
    }

    // Switching section: tapping the one already open is a no-op rather than a
    // second copy of it on the stack -the library rejects a repeated route, and
    // the strip accepts taps faster than a transition runs. Going back to a
    // section further down walks back to it instead of pushing it again, so the
    // stack stays a path through the page rather than a log of every tap.
    fun selectTab(next: SettingsTab) {
        val backStack = nav.backStack
        if (backStack.lastOrNull() == next.route) return
        if (backStack.contains(next.route)) {
            nav.popUntil { it == next.route }
        } else {
            nav.push(next.route)
        }
    }


    val restartDone = stringResource(R.string.restart_done)

    // Miuix's own host rather than a system Toast: the snackbar is drawn on the
    // same surface as the page that raised it, so it follows the theme and dies
    // with the screen instead of hovering over whatever comes next.
    //
    // Short (4s) rather than Long (10s): these messages are one short
    // sentence, and ten seconds is a long time to look at one. The user can
    // swipe the bar away if even four is too long.
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    // Remembered so the row callbacks below capture one stable value: a lambda
    // rebuilt every recomposition would make every row that takes it unstable.
    val showMessage: (String) -> Unit = remember(scope, snackbarHostState) {
        { message ->
            scope.launch {
                snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Short)
            }
            Unit
        }
    }

    // ------------------------------------------------------------------- font

    // The picked file is copied into shared storage and its path is then written
    // as an ordinary setting - deliberately as two steps rather than one. The copy
    // can fail (a wrong file, unreadable storage), and a path recorded for a file
    // that was never written would leave the status bar loading nothing. So the
    // copy runs first, and the setting is only written once it succeeded.
    var installingFont by remember { mutableStateOf(false) }
    val fontContext = LocalContext.current
    val fontResources = LocalResources.current

    val fontPicker = rememberLauncherForActivityResult(
        // OpenDocument rather than GetContent: it hands back a document URI that
        // outlives the picker, which matters because the copy happens after the
        // picker activity is gone.
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) {
            return@rememberLauncherForActivityResult
        }
        installingFont = true
        scope.launch {
            when (val result = FontInstaller.install(fontContext, uri)) {
                is FontInstaller.Result.Installed -> {
                    // The old file goes only once the new one is in place, so a
                    // failed copy can never leave the user with neither font.
                    val previous = settingsState.value.fontPath
                    if (previous.isNotEmpty() && previous != result.path) {
                        FontInstaller.remove(previous)
                    }
                    update { it.setFontPath(result.path, result.remoteName, result.revision) }
                    showMessage(fontResources.getString(R.string.font_applied, result.name))
                }
                FontInstaller.Result.NotAFont ->
                    showMessage(fontResources.getString(R.string.font_not_a_font))
                is FontInstaller.Result.Failed -> {
                    android.util.Log.w("ColorDuo", "font install failed: " + result.detail)
                    showMessage(fontResources.getString(R.string.font_failed))
                }
            }
            installingFont = false
        }
    }

    // The kill lands on System UI, not on this process, so the confirmation
    // outlives the dialog and has to be shown from here.
    LaunchedEffect(restarter.state) {
        if (restarter.state is RestartState.Done) {
            showMessage(restartDone)
            restarting = false
        }
    }

    // Hoisted out of the draw lambda below, which is not a composable scope and
    // so cannot read the theme itself.
    val surfaceColor = MiuixTheme.colorScheme.surface

    // Blur needs a runtime shader (API 33). The library skips the effect itself
    // on older devices, but that is not enough here: the blur is what replaces
    // the bar's background, and the bar applies the modifier before that
    // background, so an un-gated bar would end up with no background at all and
    // the list would scroll straight through the title.
    val barBlurSupported = isRuntimeShaderSupported()

    // Capture the animated pages for the stationary bottom bar. Each page has a
    // separate content backdrop for its own top bar, keeping sampling acyclic.
    val navBackdrop = com.kyant.backdrop.backdrops.rememberLayerBackdrop()

    CompositionLocalProvider(LocalDropdownBackdrop provides navBackdrop) {
        Scaffold(
            bottomBar = {
                val darkTheme = isSystemInDarkTheme()
                val style = Prefs.normalizeNavStyle(settings.navStyle)
                val labels = Prefs.normalizeNavLabels(settings.navLabels)
                val openIndex = openTab.ordinal

                // Three genuinely different widgets, not one widget with three parameter sets.
                // Reading the reference made that clear: its docked bar is Miuix's own
                // `NavigationBar` (which brings the item metrics, the divider policy and the
                // label animation with it), its floating bar is a hand-built capsule, and its
                // liquid style is a three-layer composition that tints a hidden copy of the row.
                // Folding them into one Box, which is what this used to do, cannot produce any
                // of the three.
                when (style) {
                    Prefs.NAV_HYPER_OS -> DockedNavigationBar(
                        labels = labels,
                        openIndex = openIndex,
                        darkTheme = darkTheme,
                        backdrop = if (barBlurSupported) navBackdrop else null,
                        onSelect = { selectTab(SettingsTab.entries[it]) },
                    )
                    Prefs.NAV_LIQUID_GLASS -> LiquidNavigationBar(
                        labels = labels,
                        openIndex = openIndex,
                        darkTheme = darkTheme,
                        backdrop = if (barBlurSupported) navBackdrop else null,
                        onSelect = { selectTab(SettingsTab.entries[it]) },
                    )
                    else -> FloatingNavigationBar(
                        labels = labels,
                        openIndex = openIndex,
                        darkTheme = darkTheme,
                        backdrop = if (barBlurSupported) navBackdrop else null,
                        onSelect = { selectTab(SettingsTab.entries[it]) },
                    )
                }
            },
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { padding ->
            // Match HyperChanger's mainTabNavigation directly. Each page has its own
            // enter/exit tracks, including interruption handling on a rapid reversal.
            AnimatedContent(
                targetState = openTab,
                transitionSpec = {
                    val direction = if (targetState.ordinal >= initialState.ordinal) 1 else -1
                    (
                        slideInHorizontally(tween(280, easing = FastOutSlowInEasing)) { direction * it / 7 } +
                            fadeIn(tween(210)) +
                            scaleIn(tween(280), initialScale = .985f)
                        ) togetherWith (
                        slideOutHorizontally(tween(230, easing = FastOutSlowInEasing)) { -direction * it / 3 } +
                            fadeOut(tween(180)) +
                            scaleOut(tween(230), targetScale = .975f)
                        )
                },
                label = "mainTabNavigation",
                modifier = Modifier
                    .fillMaxSize()
                    .background(surfaceColor)
                    .kyantLayerBackdrop(navBackdrop),
            ) { currentTab ->
                val tabBlurRadius by transition.animateFloat(
                    transitionSpec = { tween(280, easing = FastOutSlowInEasing) },
                    label = "mainTabBlur",
                ) { state -> if (state == EnterExitState.Visible) 0f else 12f }
                Box(Modifier.fillMaxSize().blur(tabBlurRadius.dp)) {
                    pageStateHolder.SaveableStateProvider(currentTab.name) {
                        SettingsPage(
                            tab = currentTab,
                            isTop = currentTab == openTab,
                            bottomPadding = padding.calculateBottomPadding(),
                            onRestart = {
                                restarter.reset()
                                restarting = true
                            },
                        ) {
                            when (currentTab) {
                                SettingsTab.Drawing -> drawingTab(
                                    settings = settingsState.value,
                                    update = { block -> update(block) },
                                    onEditColor = { role -> editing = role },
                                    onResetColors = { resettingColors = true },
                                    // The style tiles preview the OTHER arrangement's own
                                    // components, so they need a read of that arrangement
                                    // rather than of the live one.
                                    snapshotForStyle = { style -> repository.readForStyle(style) },
                                )
                                // Not an item: this destination is a page in its own right and
                                // brings its own list. Wrapping it in one lazy item is exactly
                                // what removed the rubber band - the host had nothing left to
                                // scroll once that single tall item was on screen.
                                SettingsTab.Parts -> item {
                                    PartsPage(
                                        settings = settingsState.value,
                                        onEdit = { partEditing.value = it },
                                        installingFont = installingFont,
                                        onChooseFont = { fontPicker.launch(arrayOf("*/*")) },
                                        onClearFont = { fontPath ->
                                            // Cleared from the settings first, so the status bar is
                                            // already back on the system face by the time the file
                                            // goes; a delete that failed would otherwise leave a
                                            // setting pointing at a file that is gone.
                                            update { it.setFontPath("") }
                                            scope.launch { FontInstaller.remove(fontPath) }
                                        },
                                    )
                                }
                                SettingsTab.About -> aboutTab(
                                    settingsState.value, serviceState.value, updater, showMessage,
                                ) { block -> update(block) }
                            }
                        }
                    }
                }
            }

            // Must stay inside the Scaffold: MiuixPopupHost reads LocalRootDialogStates,
            // which Scaffold provides. Composed outside this lambda the dialog would
            // register itself on a list nothing ever renders, and silently never appear.
            // The component sheet, opened from the components page. Rendered here so it sits
            // above the page rather than inside its scroll.
            val editingPart = partEditing.value
            if (editingPart != null) {
                PartSettingsSheet(
                    show = true,
                    part = editingPart,
                    settings = settingsState.value,
                    update = { block -> update(block) },
                    onEditColor = { role ->
                        partEditing.value = null
                        editing = role
                    },
                    onResetColors = {
                        partEditing.value = null
                        resettingColors = true
                    },
                    onDismiss = { partEditing.value = null },
                    onDismissFinished = {},
                )
            }
            val current = editing
            if (current != null) {
                ColorDialog(
                    role = current,
                    settings = settings,
                    onPick = { onDark, argb ->
                        update { repo ->
                            when (current) {
                                RoleColor.Critical ->
                                    if (onDark) repo.setColorCriticalOnDark(argb)
                                    else repo.setColorCriticalOnLight(argb)

                                RoleColor.Low ->
                                    if (onDark) repo.setColorLowOnDark(argb)
                                    else repo.setColorLowOnLight(argb)

                                RoleColor.Charging ->
                                    if (onDark) repo.setColorChargingOnDark(argb)
                                    else repo.setColorChargingOnLight(argb)
                            }
                        }
                    },
                    onDismiss = { editing = null },
                )
            }

            RestartDialog(
                controller = restarter,
                show = restarting,
                onDismiss = { restarting = false },
            )

            ResetColorsDialog(
                show = resettingColors,
                onDismiss = { resettingColors = false },
                onConfirm = {
                    update { it.resetRoleColors() }
                    resettingColors = false
                },
            )

            // Hosted here, with the other dialogs, rather than inside UpdateCard: the card is
            // a LazyColumn item, and a dialog owned by an item disappears when the item is
            // recycled. It reads the state the check already produced, so it opens by itself
            // the moment a release turns up.
            val available = updater.state as? UpdateState.Available
            UpdateNotesDialog(
                info = available?.info?.takeIf { it.version != dismissedUpdateVersion },
                current = available?.current.orEmpty(),
                onDismiss = { dismissedUpdateVersion = available?.info?.version },
                onDownload = { info ->
                    dismissedUpdateVersion = info.version
                    updater.download(info)
                },
            )
        }
    }
}

/**
 * A complete tab page, including its title bar. Only the bottom navigation lives
 * outside this subtree so the title, action and list share the same transition.
 */
@Composable
private fun SettingsPage(
    tab: SettingsTab,
    isTop: Boolean,
    bottomPadding: Dp,
    onRestart: () -> Unit,
    content: LazyListScope.() -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    LaunchedEffect(isTop) {
        if (isTop) {
            scrollBehavior.state.heightOffset = 0f
            scrollBehavior.state.contentOffset = 0f
        }
    }
    val pageTitle = stringResource(
        if (tab == SettingsTab.About) R.string.about_title else R.string.app_name,
    )
    val restartTitle = stringResource(R.string.restart_title)
    val surfaceColor = MiuixTheme.colorScheme.surface
    val barBlurSupported = isRuntimeShaderSupported()
    val pageBackdrop = com.kyant.backdrop.backdrops.rememberLayerBackdrop()

    Scaffold(
        topBar = {
            // Three layers, and the thing that makes the reference look the way it does is
            // that all three are tied to the SCROLL, not to a constant:
            //
            //   collapsedFraction == 0 (page at rest under an expanded bar)
            //       the light 6dp layer is fully present and the 16dp masked layer is
            //       invisible - a bar you can see the page through;
            //   collapsedFraction == 1 (content scrolled under the bar)
            //       the light layer has faded out and the masked layer is fully present -
            //       the bar thickens as things pass beneath it.
            //
            // That hand-off is the effect. Two fixed-opacity layers, which is what this had
            // before, give a bar that is blurry in exactly the same way whether anything is
            // behind it or not - legible, but flat, and that flatness is what "not the same
            // as the reference" looks like.
            val collapsed = scrollBehavior.state.collapsedFraction.coerceIn(0f, 1f)
            // clipToBounds on the container: the masked layer is deliberately drawn taller
            // than the bar so its gradient has room to fade, and without a clip on the
            // parent that overflow is painted below the bar - a hard horizontal edge across
            // the top of the page.
            Box(modifier = Modifier.clipToBounds()) {
                if (barBlurSupported) {
                    // Light pass: present while the bar is expanded, gone once it collapses.
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .graphicsLayer { alpha = 1f - collapsed }
                            .drawPlainBackdrop(
                                backdrop = pageBackdrop,
                                shape = { RectangleShape },
                                effects = { blur(6.dp.toPx()) },
                                onDrawSurface = { drawRect(surfaceColor.copy(alpha = 0.55f)) },
                            ),
                    )
                    // Heavy pass: the masked 16dp blur, visible only as the bar collapses.
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            // expandDrawHeight BEFORE graphicsLayer: the expansion has to
                            // happen in the measure/layout pass, and graphicsLayer starts a
                            // new layer whose bounds are already fixed. Reversed, the drawn
                            // surface is laid out taller than the bar it lives in and its
                            // overflow shows as a hard edge under the bar - the boundary
                            // that was visible across the top of the page.
                            .expandDrawHeight(1.184625f)
                            .graphicsLayer { alpha = collapsed }
                            .drawPlainBackdrop(
                                backdrop = pageBackdrop,
                                shape = { RectangleShape },
                                effects = {
                                    blur(16.dp.toPx())
                                    runtimeShaderEffect(
                                        key = "progressive-blur-alpha-mask",
                                        shaderString = PROGRESSIVE_BLUR_ALPHA_MASK_SHADER,
                                        uniformShaderName = "content",
                                    ) { setFloatUniform("size", size.width, size.height) }
                                },
                            ),
                    )
                } else {
                    // A device without runtime shaders gets no backdrop at all, so the
                    // bar has to paint itself: transparent here would let the list scroll
                    // straight through the title.
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(surfaceColor),
                    )
                }
                TopAppBar(
                    title = pageTitle,
                    // Transparent on purpose: the bar's own background is now the two
                    // layers above (or the opaque fallback when shaders are unsupported),
                    // and a second opaque pass would hide the blur entirely.
                    color = Color.Transparent,
                    largeTitle = pageTitle,
                    scrollBehavior = scrollBehavior,
                    actions = {
                        GlassIconButton(
                            icon = Lucide.RotateCcw,
                            description = restartTitle,
                            backdrop = if (barBlurSupported) pageBackdrop else null,
                            collapsedFraction = collapsed,
                            // The dialog is the only place a kill can be started, so a
                            // stale failure from last time must not greet the user.
                            onClick = onRestart,
                        )
                    },
                )
            }
        },
        contentWindowInsets = WindowInsets.statusBars,
    ) { padding ->
        Box(
            Modifier.fillMaxSize()
                // Record the list alone: the top bar samples this backdrop.
                .kyantLayerBackdrop(pageBackdrop)
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        ) {
            SectionList(
                contentPadding = PaddingValues(
                    start = 12.dp,
                    top = padding.calculateTopPadding() + 12.dp,
                    end = 12.dp,
                    bottom = bottomPadding + 24.dp,
                ),
                isTop = isTop,
                content = content,
            )
        }
    }
}

/**
 * A toolbar icon on frosted glass.
 *
 * <p>Sampled from the reference's `GlassToolbarIconButton`. The parts that make it read as
 * glass rather than as a tinted circle:
 *
 *   - the same backdrop stack the bars use (vibrancy, a 2dp blur, lens refraction with
 *     chromatic aberration) drawn under an 80%-alpha surface, so the button shows the page
 *     through the same material instead of approximating it;
 *   - a 0.75dp rim highlight, thin enough to read as an edge rather than as a border;
 *   - a 5dp shadow, which is what lifts it off the bar it sits on;
 *   - its alpha follows `collapsedFraction`, so at rest the button is invisible and it
 *     fades in as the bar thickens. That is the reference's behaviour, and it is why the
 *     expanded bar looks clean rather than cluttered with controls.
 */
@Composable
private fun GlassIconButton(
    icon: ImageVector,
    description: String,
    backdrop: com.kyant.backdrop.backdrops.LayerBackdrop?,
    collapsedFraction: Float,
    onClick: () -> Unit,
) {
    // The glass appears with the bar, the icon does not. That is the reference's design and
    // it is the right one: at rest the bar is nearly invisible, so a glass disc there would
    // be a grey blob floating on nothing - but the ACTION still has to be findable, so the
    // icon stays fully opaque and simply loses its backing disc until the bar thickens.
    //
    // Two separate bindings, not one: `glassAlpha` drives only the disc, and the icon uses
    // the theme colour at full strength. Tying both to the fraction, which an earlier version
    // did, hid the button completely until the page was scrolled.
    val iconColor = MiuixTheme.colorScheme.onSurface
    val glassTint = MiuixTheme.colorScheme.surface.copy(alpha = 0.80f)
    val glassAlpha by animateFloatAsState(
        targetValue = collapsedFraction.coerceIn(0f, 1f),
        animationSpec = tween(180),
        label = "glassIconAlpha",
    )
    Box(
        modifier = Modifier
            .padding(horizontal = 2.dp)
            .size(46.dp)
            .clickable(interactionSource = null, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // The disc follows the scroll.
        Box(
            modifier = Modifier
                .matchParentSize()
                // clip(CircleShape) on the DRAWN layer, not just on the backdrop's
                // `shape`: that parameter shapes the sampled region, but the surface pass
                // and its antialiased corners are painted over the full square, so the four
                // corners outside the inscribed circle showed as grey spikes.
                .graphicsLayer { alpha = glassAlpha }
                .clip(CircleShape)
                .then(
                    if (backdrop != null) {
                        Modifier.drawBackdrop(
                            backdrop = backdrop,
                            shape = { CircleShape },
                            effects = {
                                vibrancy()
                                blur(2.dp.toPx())
                                lens(
                                    refractionHeight = 16.dp.toPx(),
                                    refractionAmount = 32.dp.toPx(),
                                    depthEffect = true,
                                    chromaticAberration = true,
                                )
                            },
                            highlight = {
                                Highlight.Default.copy(
                                    width = 0.75.dp,
                                    blurRadius = 0.4.dp,
                                    alpha = 0.76f,
                                )
                            },
                            // A CIRCLE, not a rect. `shape` only shapes the backdrop pass;
                            // this lambda paints the surface on top of it, and a drawRect
                            // here shows as a grey square behind a round button - the shape
                            // and the paint have to agree.
                            onDrawSurface = { drawCircle(glassTint) },
                        )
                    } else {
                        // No backdrop: a plain circle, again not a rect.
                        Modifier.background(glassTint, CircleShape)
                    },
                ),
        )
        // The icon does not follow the scroll.
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = iconColor,
            modifier = Modifier.size(22.dp),
        )
    }
}

/**
 * The scrolling body of one destination: the shared header and then the rows of
 * whichever section this is.
 *
 * Each destination carries its own copy of the header -the preview and the strip
 * are the same two rows everywhere -because they have to scroll with the list
 * they belong to. A band pinned above the display would not: it would have to sit
 * outside every destination, which is a different page shape from the one the rows
 * were written for.
 *
 * The rows arrive as a [LazyListScope] builder rather than a composable so each
 * section keeps contributing plain `item {}`s: the list owns their identity and
 * therefore their state, and the shape of the four `*Tab` functions is unchanged.
 *
 * [isTop] is whether this destination is the section currently on screen -the top
 * of the back stack. It gates the reset to the first row, so a section opens at the
 * top every time the strip brings it forward rather than resuming where it was left.
 */
@Composable
private fun SectionList(
    contentPadding: PaddingValues,
    isTop: Boolean,
    content: LazyListScope.() -> Unit,
) {
    // A section is a place the reader arrives at, not a document they come back to:
    // opening one starts at its first row. The offset therefore belongs to the visit
    // and is not carried across. Saved page state can restore the old list position,
    // so reset it explicitly on arrival, including an interrupted return transition.
    //
    // Keyed on [isTop] rather than run once, because the same composed list is
    // covered and uncovered as sections change; the reset has to happen on each
    // arrival, not on the composition that happened to create it.
    val listState = rememberLazyListState()
    LaunchedEffect(isTop) {
        if (isTop) listState.scrollToItem(0)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            // Match the reference's opaque page surface while both pages animate.
            .background(MiuixTheme.colorScheme.surface),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        content()
    }
}

/**
 * The confirmation for restarting System UI.
 *
 * <p>[OverlayDialog] rather than a bottom sheet: this is the dialog the colour
 * picker already uses, so the confirmation reads as part of the module instead
 * of borrowing a second popup style. Rendering through the Scaffold's popup host
 * is also why this has to stay inside the Scaffold's content lambda.
 *
 * <p>Dismissal is refused while the kill is in flight -the user may already be
 * looking at a root prompt, and closing the dialog there would leave the outcome
 * with nowhere to be reported.
 */
@Composable
private fun RestartDialog(
    controller: RestartController,
    show: Boolean,
    onDismiss: () -> Unit,
) {
    val state = controller.state
    val running = state is RestartState.Running

    OverlayDialog(
        show = show,
        title = stringResource(R.string.restart_title),
        summary = stringResource(R.string.restart_summary),
        onDismissRequest = { if (!running) onDismiss() },
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // The raw shell text rides along with the localised headline, the
            // same way a failed update pairs its diagnostic detail.
            if (state is RestartState.Failed) {
                Text(
                    text = listOfNotNull(
                        stringResource(R.string.restart_failed),
                        state.detail?.takeIf { it.isNotBlank() },
                    ).joinToString(" · "),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    text = stringResource(R.string.cancel),
                    onClick = onDismiss,
                    enabled = !running,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = { controller.restart() },
                    enabled = !running,
                    colors = ButtonDefaults.buttonColorsPrimary(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = stringResource(
                            if (running) R.string.restart_running else R.string.restart_confirm,
                        ),
                    )
                }
            }
        }
    }
}

/** Signal levels the preview shows; the middle of each range. */
private const val WIFI_LEVEL = 3
private const val MOBILE_LEVEL = 4

/** What the renderer treats as "no Wi-Fi ink": the value drops to the centre. */
private const val NO_WIFI_LEVEL = -1

/**
 * Preview glyph edge. Sized so the eight cells still fit one row inside a 368.dp
 * card: eight of these plus seven 4.dp gutters come to 340.dp, clear of the
 * 344.dp content width, and the cell that is not a glyph has no fixed aspect of
 * its own to preserve.
 */
private val PREVIEW_SIZE = 39.dp

/**
 * Stand-in label for the type preview cells; the real one comes from SystemUI.
 *
 * <p>Deliberately the "5GA" form rather than "5G": the trailing A is what the
 * suffix-scale row changes, so this is the only cell where that setting can be
 * seen at all.
 */
private const val MOBILE_TYPE_PREVIEW = "5GA"

/**
 * Stand-in per-SIM levels for the preview: SIM 1 one step down from SIM 2, so a
 * preview that has quietly collapsed to a single row still looks wrong rather
 * than looking like a plausible single-SIM reading.
 */
private val DUAL_SIM_PREVIEW = intArrayOf(2, 4)

/**
 * True when the app is rendering its dark palette, following the system setting
 * too. Miuix exposes the mode rather than a resolved boolean, so the system case
 * has to be resolved here.
 *
 * <p>{@code internal} because the drawing page's large preview sits on the same
 * surface and has to follow the same theme, or the two previews would disagree
 * about what the status bar looks like.
 */
@Composable
internal fun isDarkTheme(): Boolean = MiuixTheme.colorSchemeMode == ColorSchemeMode.Dark ||
    (MiuixTheme.colorSchemeMode == ColorSchemeMode.System && isSystemInDarkTheme())
