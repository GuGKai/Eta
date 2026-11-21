package io.github.mangi.eta.ui.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ShortText
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import com.itos.coui.icons.CouiIcons
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.layout.Scaffold as CouiLayoutScaffold
import com.itos.coui.menu.CouiMenuItem
import com.itos.coui.menu.Menu
import com.itos.coui.menu.couiMenuAnchor
import com.itos.coui.menu.rememberCouiMenuAnchor
import com.itos.coui.theme.CouiTheme
import com.itos.coui.theme.LocalCouiTextStyle
import com.itos.coui.topappbar.CouiTopAppBarDefaults
import com.itos.coui.topappbar.TopAppBar as CouiTopAppBar
import com.itos.coui.topappbar.TopAppBarButtonGroup
import io.github.mangi.eta.R
import io.github.mangi.eta.data.model.AppearanceUiStyle
import io.github.mangi.eta.data.model.TtsProvider
import io.github.mangi.eta.data.repository.SpeechSettingsRepository
import io.github.mangi.eta.ui.components.AdaptiveTopAppBar
import io.github.mangi.eta.ui.components.CharacterAvatar
import io.github.mangi.eta.ui.components.ChatActionIcons
import io.github.mangi.eta.ui.components.AgentTopBarChatHeader
import io.github.mangi.eta.ui.components.ChatImagePreviewHost
import io.github.mangi.eta.ui.components.ConversationPanelMetrics
import io.github.mangi.eta.ui.components.ConversationSidePaneScaffold
import io.github.mangi.eta.ui.components.EtaTopLayer
import io.github.mangi.eta.ui.components.MiuixBackButton
import io.github.mangi.eta.ui.components.RoleplayChatUi
import io.github.mangi.eta.ui.components.captureGlassBackdrop
import io.github.mangi.eta.ui.components.coui.CouiBackButton
import io.github.mangi.eta.ui.components.coui.CouiTopAppBarIconButton
import io.github.mangi.eta.ui.components.currentGlassOverlayBackdrop
import io.github.mangi.eta.ui.components.rememberGlassBackdrop
import io.github.mangi.eta.ui.layout.rememberIsWideScreen
import io.github.mangi.eta.ui.model.AgentModelPickerUiState
import io.github.mangi.eta.ui.model.ConversationPaneUiState
import io.github.mangi.eta.ui.model.ConversationSummaryUi
import io.github.mangi.eta.ui.navigation.AppRoute
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.ProgressiveBlur
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.progressiveTextureBlur
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.glass.GlassIconButton
import top.yukonga.miuix.kmp.glass.GlassPopupItem
import top.yukonga.miuix.kmp.glass.GlassTopAppBarDefaults
import top.yukonga.miuix.kmp.glass.GlassTransformPopup
import top.yukonga.miuix.kmp.glass.glassPopupAnchor
import top.yukonga.miuix.kmp.glass.glassPopupAnchorContent
import top.yukonga.miuix.kmp.glass.rememberGlassPopupAnchor
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.Computer
import top.yukonga.miuix.kmp.icon.glass.GlobalVpn
import top.yukonga.miuix.kmp.icon.glass.More
import top.yukonga.miuix.kmp.icon.glass.NewMessage
import top.yukonga.miuix.kmp.icon.glass.Sidebar
import top.yukonga.miuix.kmp.icon.glass.VolumeOff
import top.yukonga.miuix.kmp.icon.glass.VolumeUp
import top.yukonga.miuix.kmp.material.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

/**
 * Agent App 统一壳层。
 *
 * - 负责全局 Scaffold、状态栏/横向安全边距、顶层工具栏。
 * - 首页工具栏只保留历史入口与溢出菜单，保持聊天舞台干净。
 * - 非首页子路由统一提供返回按钮与标题，避免每个页面各自像独立设置页。
 * - Settings 由标准二级页骨架自己提供 TopAppBar，壳层在此路由不重复绘制。
 */
@Composable
internal fun AgentAppShell(
    currentRoute: AppRoute?,
    isCurrentRoute: Boolean,
    conversationPaneState: ConversationPaneUiState?,
    isConversationPaneOpen: Boolean,
    onBack: () -> Unit,
    onOpenConversationPane: () -> Unit,
    onDismissConversationPane: () -> Unit,
    onSearchConversations: (String) -> Unit,
    onNewConversation: () -> Unit,
    onNewConversationWorkspace: (String?) -> Unit = {},
    onOpenTerminal: () -> Unit,
    onOpenBrowser: () -> Unit,
    onOpenTasks: () -> Unit,
    onSelectConversation: (String) -> Unit,
    onConversationTogglePin: (ConversationSummaryUi) -> Unit,
    onConversationRename: (ConversationSummaryUi) -> Unit,
    onConversationPinnedChange: (ConversationSummaryUi, Boolean) -> Unit,
    onConversationExport: (ConversationSummaryUi) -> Unit,
    onConversationDelete: (ConversationSummaryUi) -> Unit,
    onCreateWorkspace: (String) -> Unit,
    onRenameWorkspace: (String, String) -> Unit,
    onDeleteWorkspace: (String) -> Unit,
    onWorkspaceCollapsedChange: (String, Boolean) -> Unit,
    onConversationMoveToWorkspace: (String, String?) -> Unit,
    onOpenTools: () -> Unit,
    onOpenSkills: () -> Unit,
    onOpenScheduledTasks: () -> Unit = {},
    onOpenCharacters: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenModelProviders: () -> Unit,
    conversationStatus: (@Composable () -> Unit)? = null,
    /** 顶栏会话标题；Home 路由用它取代原来的空标题。 */
    conversationTitle: String? = null,
    modifier: Modifier = Modifier,
    homeTitle: String? = null,
    homeCharacter: RoleplayChatUi? = null,
    homeModelPickerState: AgentModelPickerUiState? = null,
    isHomeStreaming: Boolean = false,
    hasPermissionAttention: Boolean = false,
    onHomeModelSelected: (String) -> Unit = {},
    onOpenCharacter: () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val uiStyle = LocalUiStyle.current
    val pageContent: @Composable () -> Unit = {
        when (uiStyle) {
            AppearanceUiStyle.COUI -> {
                val dark = CouiTheme.isDark
                val colors = CouiPageDefaults.themeColors(dark)
                val isHome = currentRoute is AppRoute.Home
                // Home 路由优先显示当前会话标题；没有标题（新会话、空对话）时回退到路由名（Home 为空串）。
                val homeTitle = conversationTitle?.takeIf { it.isNotBlank() }
                // 会话标题由无到有、或换成新名字时淡入；路由名不做动画。
                val conversationTitleAlpha = rememberConversationTitleFade(homeTitle)
                val couiTextStyle = LocalCouiTextStyle.current
                // 标题不再上移：回到与左侧按钮、右侧操作钮同一水平中线，并加粗。
                // 标题左距完全交给 COUI 顶栏自带的标题起点间距：再补空格或 padding 都会叠宽。
                val barTitle = homeTitle ?: titleForRoute(currentRoute)
                CouiLayoutScaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MiuixTheme.colorScheme.surface,
                    windowInsets = WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
                    ),
                    topBar = {
                        if (currentRoute !is AppRoute.Settings) {
                            CouiTopAppBar(
                                title = {
                                    BasicText(
                                        text = barTitle,
                                        modifier = if (homeTitle != null) {
                                            Modifier.alpha(conversationTitleAlpha)
                                        } else {
                                            Modifier
                                        },
                                        // COUI 的正文样式自带固定深色，暗色模式下会黑字压深底，这里显式跟随主题前景色。
                                        style = couiTextStyle.copy(
                                            color = MiuixTheme.colorScheme.onSurface,
                                            fontWeight = FontWeight.Bold,
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                navigationIcon = {
                                    if (isHome) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            CouiTopAppBarIconButton(
                                                icon = Icons.AutoMirrored.Outlined.ShortText,
                                                contentDescription = stringResource(R.string.action_conversation_history),
                                                onClick = onOpenConversationPane,
                                                dark = dark,
                                                colors = colors,
                                            )
                                            homeCharacter?.let { character ->
                                                CharacterTopBarIdentity(character = character, onClick = onOpenCharacter)
                                            }
                                        }
                                    } else {
                                        CouiBackButton(onClick = onBack, dark = dark, colors = colors)
                                    }
                                },
                                actions = {
                                    if (isHome) {
                                        TopAppBarButtonGroup {
                                            // 新建对话独立成按钮放在右侧，图标用加号；菜单里不再重复一项。
                                            CouiTopAppBarIconButton(
                                                icon = ChatActionIcons.NewChat,
                                                contentDescription = stringResource(R.string.action_new_conversation),
                                                onClick = onNewConversation,
                                                dark = dark,
                                                colors = colors,
                                                iconSize = ConversationPanelMetrics.NewChatIconSize,
                                            )
                                            CouiTopBarOverflowMenu(
                                                dark = dark,
                                                colors = colors,
                                                onOpenTerminal = onOpenTerminal,
                                                onOpenBrowser = onOpenBrowser,
                                                onOpenTasks = onOpenTasks,
                                                onOpenScheduledTasks = onOpenScheduledTasks,
                                            )
                                        }
                                    }
                                },
                                dark = dark,
                                colors = CouiTopAppBarDefaults.colors(
                                    backgroundColor = MiuixTheme.colorScheme.surface,
                                    titleColor = colors.title,
                                    dark = dark,
                                ),
                            )
                        }
                    },
                ) { padding ->
                    Box(modifier = Modifier.fillMaxSize()) {
                        content(padding)
                    }
                }
            }
            AppearanceUiStyle.MIUIX -> {
                // 首页顶栏高度固定，不参与折叠；未绑定顶栏的滚动行为会吞掉聊天列表的单向滑动。
                val scrollBehavior = if (currentRoute is AppRoute.Home) null else MiuixScrollBehavior()
                val pageBackdrop = rememberGlassBackdrop()
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
                    ),
                    topBar = {
                        if (currentRoute !is AppRoute.Settings) {
                            AgentTopBar(
                                route = currentRoute,
                                isCurrentRoute = isCurrentRoute,
                                scrollBehavior = scrollBehavior,
                                backdrop = pageBackdrop,
                                onBack = onBack,
                                onOpenConversationPane = onOpenConversationPane,
                                onNewConversation = onNewConversation,
                                onOpenTerminal = onOpenTerminal,
                                onOpenBrowser = onOpenBrowser,
                                homeTitle = homeTitle,
                                homeCharacter = homeCharacter,
                                homeModelPickerState = homeModelPickerState,
                                isHomeStreaming = isHomeStreaming,
                                onHomeModelSelected = onHomeModelSelected,
                                onOpenCharacter = onOpenCharacter,
                            )
                        }
                    },
                ) { padding ->
                    CompositionLocalProvider(LocalGlassPageBackdrop provides pageBackdrop) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .captureGlassBackdrop(pageBackdrop)
                                .then(
                                    if (scrollBehavior != null) Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
                                    else Modifier,
                                ),
                        ) {
                            content(padding)
                        }
                    }
                }
            }
        }
    }

    // 壳层顶部的全屏图片查看器宿主：消息气泡通过 LocalChatImagePreviewer 打开原位预览。
    ChatImagePreviewHost(modifier = modifier) {
        if (conversationPaneState != null && currentRoute is AppRoute.Home) {
            ConversationSidePaneScaffold(
                state = conversationPaneState,
                visible = isConversationPaneOpen,
                backHandlerEnabled = isCurrentRoute,
                onOpen = onOpenConversationPane,
                onDismiss = onDismissConversationPane,
                onSearchChange = onSearchConversations,
                onNewConversation = onNewConversation,
                onNewConversationWorkspace = onNewConversationWorkspace,
                onConversationSelected = onSelectConversation,
                onConversationTogglePin = onConversationTogglePin,
                onConversationRename = onConversationRename,
                onConversationPinnedChange = onConversationPinnedChange,
                onConversationExport = onConversationExport,
                onConversationDelete = onConversationDelete,
                onCreateWorkspace = onCreateWorkspace,
                onRenameWorkspace = onRenameWorkspace,
                onDeleteWorkspace = onDeleteWorkspace,
                onWorkspaceCollapsedChange = onWorkspaceCollapsedChange,
                onConversationMoveToWorkspace = onConversationMoveToWorkspace,
                onOpenSettings = onOpenSettings,
                onOpenModelProviders = onOpenModelProviders,
                onOpenTools = onOpenTools,
                onOpenSkills = onOpenSkills,
                onOpenScheduledTasks = onOpenScheduledTasks,
                onOpenCharacters = onOpenCharacters,
                onOpenPermissions = onOpenPermissions,
            ) {
                pageContent()
            }
        } else {
            pageContent()
        }
    }
}

@Composable
private fun CouiTopBarOverflowMenu(
    dark: Boolean,
    colors: com.itos.coui.layout.CouiPageColors,
    onOpenTerminal: () -> Unit,
    onOpenBrowser: () -> Unit,
    onOpenTasks: () -> Unit,
    onOpenScheduledTasks: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val speechSettings by remember { SpeechSettingsRepository.settingsFlow() }.collectAsState(initial = null)
    val hasSpeechPlayback = speechSettings?.let { it.tts != TtsProvider.NONE } == true
    val autoReadEnabled = speechSettings?.chatAutoSpeak == true
    val anchor = rememberCouiMenuAnchor()

    val openTerminalText = stringResource(R.string.action_open_terminal)
    val openBrowserText = stringResource(R.string.action_open_browser)
    val openTasksText = stringResource(R.string.automation_title)
    val openScheduledText = stringResource(R.string.route_scheduled_tasks)
    val autoReadText = stringResource(
        if (autoReadEnabled) R.string.speech_disable_auto_read else R.string.speech_enable_auto_read,
    )
    val menuItems = remember(
        openTerminalText,
        openBrowserText,
        openTasksText,
        openScheduledText,
        autoReadText,
        hasSpeechPlayback,
        autoReadEnabled,
    ) {
        buildList {
            add(CouiMenuItem(value = 1, text = openTerminalText, icon = Icons.Rounded.Terminal))
            add(CouiMenuItem(value = 3, text = openBrowserText, icon = Icons.Rounded.Language))
            // 与设置页同一个入口，直接跳到自动任务列表。
            add(CouiMenuItem(value = 4, text = openTasksText, icon = Icons.Rounded.FilterAlt))
            // 定时任务（时间驱动）排在自动任务下面，与设置页入口一致。
            add(CouiMenuItem(value = 6, text = openScheduledText, icon = Icons.Rounded.Schedule))
            if (hasSpeechPlayback) {
                add(
                    CouiMenuItem(
                        value = 5,
                        text = autoReadText,
                        icon = if (autoReadEnabled) Icons.AutoMirrored.Rounded.VolumeUp else Icons.AutoMirrored.Rounded.VolumeOff,
                    ),
                )
            }
        }
    }

    Box {
        CouiTopAppBarIconButton(
            icon = CouiIcons.Outlined.More,
            contentDescription = stringResource(R.string.action_more),
            modifier = Modifier.couiMenuAnchor(anchor),
            dark = dark,
            colors = colors,
            onClick = { anchor.open { showMenu = true } },
        )
        Menu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            items = menuItems,
            onItemClick = { item ->
                showMenu = false
                when (item.value) {
                    1 -> onOpenTerminal()
                    3 -> onOpenBrowser()
                    4 -> onOpenTasks()
                    6 -> onOpenScheduledTasks()
                    5 -> speechSettings?.let { current ->
                        scope.launch {
                            SpeechSettingsRepository.save(current.copy(chatAutoSpeak = !current.chatAutoSpeak))
                        }
                    }
                }
            },
            anchor = anchor,
        )
    }
}

@Composable
private fun AgentTopBar(
    route: AppRoute?,
    isCurrentRoute: Boolean,
    scrollBehavior: ScrollBehavior?,
    backdrop: Backdrop?,
    onBack: () -> Unit,
    onOpenConversationPane: () -> Unit,
    onNewConversation: () -> Unit,
    onOpenTerminal: () -> Unit,
    onOpenBrowser: () -> Unit,
    homeTitle: String?,
    homeCharacter: RoleplayChatUi?,
    homeModelPickerState: AgentModelPickerUiState?,
    isHomeStreaming: Boolean,
    onHomeModelSelected: (String) -> Unit,
    onOpenCharacter: () -> Unit,
) {
    val isHome = route is AppRoute.Home
    if (isHome && homeModelPickerState != null) {
        HomeMiuixTopBar(
            backdrop = backdrop,
            isCurrentRoute = isCurrentRoute,
            onOpenConversationPane = onOpenConversationPane,
            onNewConversation = onNewConversation,
            onOpenTerminal = onOpenTerminal,
            onOpenBrowser = onOpenBrowser,
        ) {
            AgentTopBarChatHeader(
                conversationTitle = homeTitle,
                character = homeCharacter,
                state = homeModelPickerState,
                isStreaming = isHomeStreaming,
                onModelSelected = onHomeModelSelected,
                onOpenCharacter = onOpenCharacter,
            )
        }
        return
    }

    AdaptiveTopAppBar(
        title = titleForRoute(route),
        backdrop = backdrop,
        expandable = route !is AppRoute.Terminal && !rememberIsWideScreen(),
        scrollBehavior = scrollBehavior,
        navigationIcon = { MiuixBackButton(onClick = onBack) },
        actions = {},
    )
}

@Composable
private fun HomeMiuixTopBar(
    backdrop: Backdrop?,
    isCurrentRoute: Boolean,
    onOpenConversationPane: () -> Unit,
    onNewConversation: () -> Unit,
    onOpenTerminal: () -> Unit,
    onOpenBrowser: () -> Unit,
    centerContent: @Composable () -> Unit,
) {
    val surfaceColor = MiuixTheme.colorScheme.surface
    val progressiveBlurEnabled = LocalAppearanceSettings.current.progressiveBlurEnabled
    val progressiveSource = if (progressiveBlurEnabled) {
        backdrop?.takeIf { isRuntimeShaderSupported() }
    } else {
        null
    }
    val progressive = progressiveSource != null
    val bandBrush = remember(surfaceColor) { GlassTopAppBarDefaults.bandBrush(surfaceColor) }
    var backgroundCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val progressiveBackdrop = if (progressiveSource != null) {
        rememberLayerBackdrop {
            val coordinates = backgroundCoordinates?.takeIf { it.isAttached }
            with(progressiveSource) { drawBackdrop(this@rememberLayerBackdrop, coordinates) }
            drawContent()
        }
    } else {
        null
    }
    val buttonBackdrop = progressiveBackdrop ?: backdrop
    val blurColors = BlurDefaults.blurColors(
        blendColors = remember(surfaceColor) { listOf(BlendColorEntry(surfaceColor.copy(alpha = 0.3f))) },
    )

    Layout(
        content = {
            Box(
                modifier = if (progressiveSource != null) {
                    Modifier
                        .onGloballyPositioned { backgroundCoordinates = it }
                        .let { if (progressiveBackdrop != null) it.layerBackdrop(progressiveBackdrop) else it }
                        .progressiveTextureBlur(
                            backdrop = progressiveSource,
                            shape = RectangleShape,
                            gradient = ProgressiveBlur.Top.copy(curve = 0.5f),
                            blurRadius = GlassTopAppBarDefaults.ProgressiveBlurRadius.value,
                            colors = blurColors,
                        )
                } else {
                    Modifier.background(bandBrush)
                },
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                    .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal))
                    .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top))
                    .height(60.dp)
                    .pointerInput(Unit) {
                        detectTapGestures { }
                    }
                    .padding(horizontal = GlassTopAppBarDefaults.HorizontalPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassIconButton(
                    onClick = onOpenConversationPane,
                    backdrop = buttonBackdrop,
                ) {
                    Icon(
                        imageVector = MiuixIcons.Glass.Sidebar,
                        modifier = Modifier.size(28.dp),
                        tint = MiuixTheme.colorScheme.onSurface,
                        contentDescription = stringResource(R.string.action_conversation_history),
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    centerContent()
                }
                TopBarOverflowMenu(
                    backdrop = buttonBackdrop,
                    isCurrentRoute = isCurrentRoute,
                    onNewConversation = onNewConversation,
                    onOpenTerminal = onOpenTerminal,
                    onOpenBrowser = onOpenBrowser,
                )
            }
        },
    ) { measurables, constraints ->
        val foreground = measurables[1].measure(constraints.copy(minWidth = 0, minHeight = 0))
        val width = constraints.constrainWidth(foreground.width)
        val height = constraints.constrainHeight(foreground.height)
        val bandHeight = if (progressive) {
            ((height + 20.dp.toPx()) * 1.15f).roundToInt()
        } else {
            height + GlassTopAppBarDefaults.BandOverhang.roundToPx()
        }
        val background = measurables[0].measure(Constraints.fixed(width, bandHeight))
        layout(width, height) {
            background.placeRelative(0, 0)
            foreground.placeRelative(0, 0)
        }
    }
}

@Composable
private fun TopBarOverflowMenu(
    backdrop: Backdrop?,
    isCurrentRoute: Boolean,
    onNewConversation: () -> Unit,
    onOpenTerminal: () -> Unit,
    onOpenBrowser: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    LaunchedEffect(isCurrentRoute) {
        if (!isCurrentRoute) showMenu = false
    }
    val moreAnchor = rememberGlassPopupAnchor()
    val scope = rememberCoroutineScope()
    val speechSettings by remember { SpeechSettingsRepository.settingsFlow() }.collectAsState(initial = null)
    val hasSpeechPlayback = speechSettings?.let { it.tts != TtsProvider.NONE } == true
    val autoReadEnabled = speechSettings?.chatAutoSpeak == true
    val newConversationText = stringResource(R.string.action_new_conversation)
    val openTerminalText = stringResource(R.string.action_open_terminal)
    val openBrowserText = stringResource(R.string.action_open_browser)
    val autoReadText = stringResource(
        if (autoReadEnabled) R.string.speech_disable_auto_read else R.string.speech_enable_auto_read,
    )
    val overlayBackdrop = currentGlassOverlayBackdrop() ?: backdrop

    GlassIconButton(
        onClick = { showMenu = true },
        backdrop = backdrop,
        modifier = Modifier.glassPopupAnchor(
            anchor = moreAnchor,
            cornerRadius = GlassTopAppBarDefaults.ButtonSize / 2,
            floating = true,
        ),
    ) {
        Icon(
            imageVector = MiuixIcons.Glass.More,
            modifier = Modifier
                .size(28.dp)
                .glassPopupAnchorContent(moreAnchor),
            tint = MiuixTheme.colorScheme.onSurface,
            contentDescription = stringResource(R.string.action_more),
        )
    }

    if (isCurrentRoute) {
        EtaTopLayer {
            GlassTransformPopup(
                show = showMenu,
                onDismissRequest = { showMenu = false },
                anchor = moreAnchor,
                backdrop = overlayBackdrop,
                anchorContent = {
                    Icon(
                        imageVector = MiuixIcons.Glass.More,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MiuixTheme.colorScheme.onSurface,
                    )
                },
            ) {
                GlassPopupItem(
                    text = newConversationText,
                    icon = MiuixIcons.Glass.NewMessage,
                    onClick = {
                        showMenu = false
                        onNewConversation()
                    },
                )
                GlassPopupItem(
                    text = openTerminalText,
                    icon = MiuixIcons.Glass.Computer,
                    onClick = {
                        showMenu = false
                        onOpenTerminal()
                    },
                )
                GlassPopupItem(
                    text = openBrowserText,
                    icon = MiuixIcons.Glass.GlobalVpn,
                    onClick = {
                        showMenu = false
                        onOpenBrowser()
                    },
                )
                if (hasSpeechPlayback) {
                    GlassPopupItem(
                        text = autoReadText,
                        icon = if (autoReadEnabled) MiuixIcons.Glass.VolumeUp else MiuixIcons.Glass.VolumeOff,
                        onClick = {
                            showMenu = false
                            speechSettings?.let { current ->
                                scope.launch {
                                    SpeechSettingsRepository.save(current.copy(chatAutoSpeak = !current.chatAutoSpeak))
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

/** 顶栏会话标题的宽度上限：按当前字号实测出的 11 个中文字符宽度。 */
private const val TOP_BAR_TITLE_CJK_WIDTH = 11

/** 会话标题透明度渐显时长：够看出「淡出来」，又不至于让人等标题。 */
private const val TOP_BAR_TITLE_FADE_MS = 320

/**
 * 会话标题的透明度渐显。
 *
 * 标题由无到有、或换成新名字时从 0 淡到 1；空标题直接把透明度压到 0，
 * 这样「发完消息、命名还没落地」的那一瞬间不会闪出旧的占位文案。
 */
@Composable
private fun rememberConversationTitleFade(title: String?): Float {
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(title) {
        if (title.isNullOrBlank()) {
            alpha.snapTo(0f)
        } else {
            alpha.snapTo(0f)
            alpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = TOP_BAR_TITLE_FADE_MS, easing = LinearOutSlowInEasing),
            )
        }
    }
    return alpha.value
}

@Composable
private fun titleForRoute(route: AppRoute?): String = when (route) {
    is AppRoute.Home, is AppRoute.Browser -> ""
    is AppRoute.Terminal -> stringResource(R.string.route_terminal)
    is AppRoute.Tools -> stringResource(R.string.route_tools)
    is AppRoute.WebToolsSettings -> stringResource(R.string.web_tools_settings_title)
    is AppRoute.ImageGenSettings -> stringResource(R.string.image_gen_settings_title)
    is AppRoute.ImageGenGallery -> stringResource(R.string.image_gen_gallery_title)
    is AppRoute.Skills -> stringResource(R.string.route_skills)
    is AppRoute.ScheduledTasks -> stringResource(R.string.route_scheduled_tasks)
    is AppRoute.ScheduledTaskDetail -> stringResource(R.string.route_scheduled_task_detail)
    is AppRoute.ScheduledTaskEditor -> stringResource(
        if (route.taskId.isNullOrBlank()) R.string.route_scheduled_task_new else R.string.route_scheduled_task_edit,
    )
    is AppRoute.Subagents -> "Subagent"
    is AppRoute.Characters -> "角色"
    is AppRoute.CharacterDetail -> "角色详情"
    is AppRoute.CharacterEditor -> "编辑角色"
    is AppRoute.CharacterPersona -> "我的人设"
    is AppRoute.DefaultAssistantPrompt -> stringResource(R.string.default_prompt_title)
    is AppRoute.CharacterMemory -> "剧情记忆"
    is AppRoute.Permissions -> stringResource(R.string.route_permissions)
    is AppRoute.SystemEnhance -> stringResource(R.string.route_system_enhancements)
    is AppRoute.Settings -> stringResource(R.string.route_settings)
    is AppRoute.SpeechSettings -> stringResource(R.string.speech_settings_title)
    is AppRoute.SpeechRecognition -> stringResource(R.string.speech_recognition_title)
    is AppRoute.SpeechSynthesis -> stringResource(R.string.speech_synthesis_title)
    is AppRoute.SpeechOss -> stringResource(R.string.speech_oss_title)
    is AppRoute.AppearanceSettings -> stringResource(R.string.appearance_title)
    is AppRoute.DataBackup -> stringResource(R.string.data_backup_title)
    is AppRoute.Memory -> stringResource(R.string.route_memory)
    is AppRoute.Tasks -> stringResource(R.string.automation_title)
    is AppRoute.LinuxEnvironment -> stringResource(R.string.route_linux_environment)
    is AppRoute.Workspace -> stringResource(R.string.capability_workspace)
    is AppRoute.SharedFolders -> stringResource(R.string.route_shared_folders)
    is AppRoute.LinuxFiles -> stringResource(R.string.route_linux_files)
    is AppRoute.ModelProviders -> stringResource(R.string.route_model_providers)
    is AppRoute.CommunityCatalog -> "从 models.dev 添加"
    is AppRoute.CommunityCatalogProvider -> "选择模型"
    is AppRoute.McpServers -> stringResource(R.string.route_mcp_servers)
    is AppRoute.McpServerDetail -> stringResource(R.string.route_mcp_server_detail)
    is AppRoute.ModelProviderDetail -> stringResource(R.string.route_provider_details)
    is AppRoute.ModelProviderNew -> stringResource(R.string.route_new_provider)
    is AppRoute.ConversationTitleSettings -> stringResource(R.string.conversation_title_settings_title)
    is AppRoute.AssistantOverlaySettings -> stringResource(R.string.assistant_overlay_settings_title)
    null -> stringResource(R.string.app_name)
}

@Composable
private fun CharacterTopBarIdentity(character: RoleplayChatUi, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(start = 4.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CharacterAvatar(
            name = character.characterName,
            avatarPath = character.avatarPath,
            size = 32.dp,
            shape = CircleShape,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = character.characterName,
            style = MiuixTheme.textStyles.body1,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 180.dp),
        )
    }
}
