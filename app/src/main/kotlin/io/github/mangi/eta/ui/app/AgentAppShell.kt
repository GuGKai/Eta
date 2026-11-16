package io.github.mangi.eta.ui.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.offset
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
import io.github.mangi.eta.ui.components.ChatImagePreviewHost
import io.github.mangi.eta.ui.components.ConversationPanelMetrics
import io.github.mangi.eta.ui.components.ConversationSidePaneScaffold
import io.github.mangi.eta.ui.components.MiuixBackButton
import io.github.mangi.eta.ui.components.RoleplayChatUi
import io.github.mangi.eta.ui.components.TopBarBackdrop
import io.github.mangi.eta.ui.components.captureForTopBar
import io.github.mangi.eta.ui.components.coui.CouiBackButton
import io.github.mangi.eta.ui.components.coui.CouiTopAppBarIconButton
import io.github.mangi.eta.ui.components.rememberTopBarBackdrop
import io.github.mangi.eta.ui.components.topBarContainerColor
import io.github.mangi.eta.ui.model.ConversationPaneUiState
import io.github.mangi.eta.ui.model.ConversationSummaryUi
import io.github.mangi.eta.ui.navigation.AppRoute
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowListPopup

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
    onOpenCharacters: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenModelProviders: () -> Unit,
    conversationStatus: (@Composable () -> Unit)? = null,
    /** 顶栏会话标题；Home 路由用它取代原来的空标题。 */
    conversationTitle: String? = null,
    modifier: Modifier = Modifier,
    homeCharacter: RoleplayChatUi? = null,
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
                                        modifier = Modifier,
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
                                                onNewConversation = onNewConversation,
                                                onOpenTerminal = onOpenTerminal,
                                                onOpenBrowser = onOpenBrowser,
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
                val scrollBehavior = MiuixScrollBehavior()
                val backdrop = rememberTopBarBackdrop()
                val topBarColor = topBarContainerColor(backdrop)
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
                    ),
                    topBar = {
                        if (currentRoute !is AppRoute.Settings) {
                            TopBarBackdrop(backdrop) {
                                AgentTopBar(
                                    route = currentRoute,
                                    scrollBehavior = scrollBehavior,
                                    color = topBarColor,
                                    onBack = onBack,
                                    onOpenConversationPane = onOpenConversationPane,
                                    onNewConversation = onNewConversation,
                                    onOpenTerminal = onOpenTerminal,
                                    onOpenBrowser = onOpenBrowser,
                                    homeCharacter = homeCharacter,
                                    onOpenCharacter = onOpenCharacter,
                                    conversationStatus = conversationStatus,
                                    conversationTitle = conversationTitle,
                                )
                            }
                        }
                    },
                ) { padding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .captureForTopBar(backdrop)
                            .nestedScroll(scrollBehavior.nestedScrollConnection),
                    ) {
                        content(padding)
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
    onNewConversation: () -> Unit,
    onOpenTerminal: () -> Unit,
    onOpenBrowser: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val speechSettings by remember { SpeechSettingsRepository.settingsFlow() }.collectAsState(initial = null)
    val hasSpeechPlayback = speechSettings?.let { it.tts != TtsProvider.NONE } == true
    val autoReadEnabled = speechSettings?.chatAutoSpeak == true
    val anchor = rememberCouiMenuAnchor()

    val newConversationText = stringResource(R.string.action_new_conversation)
    val openTerminalText = stringResource(R.string.action_open_terminal)
    val openBrowserText = stringResource(R.string.action_open_browser)
    val autoReadText = stringResource(
        if (autoReadEnabled) R.string.speech_disable_auto_read else R.string.speech_enable_auto_read,
    )
    val menuItems = remember(
        newConversationText,
        openTerminalText,
        openBrowserText,
        autoReadText,
        hasSpeechPlayback,
        autoReadEnabled,
    ) {
        buildList {
            add(CouiMenuItem(value = 1, text = openTerminalText, icon = Icons.Rounded.Terminal))
            add(CouiMenuItem(value = 3, text = openBrowserText, icon = Icons.Rounded.Language))
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
    scrollBehavior: ScrollBehavior,
    color: Color,
    onBack: () -> Unit,
    onOpenConversationPane: () -> Unit,
    onNewConversation: () -> Unit,
    onOpenTerminal: () -> Unit,
    onOpenBrowser: () -> Unit,
    homeCharacter: RoleplayChatUi?,
    onOpenCharacter: () -> Unit,
    conversationStatus: (@Composable () -> Unit)?,
    conversationTitle: String?,
) {
    val isHome = route is AppRoute.Home
    val navigationIcon: @Composable () -> Unit = {
        if (isHome) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onOpenConversationPane) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ShortText,
                        modifier = Modifier.size(24.dp),
                        contentDescription = stringResource(R.string.action_conversation_history),
                    )
                }
                // 会话标题：紧跟侧边栏按钮、正文同号、单行。
                // 宽度上限按「[TOP_BAR_TITLE_CJK_WIDTH] 个中文字符」在当前字号下的实测宽度算，
                // 超出交给 Compose 加省略号：英文、数字、空格本来就比中文窄，
                // 按字符个数截断会把半个标题白白砍掉。
                conversationTitle?.takeIf { it.isNotBlank() }?.let { title ->
                    val titleMeasurer = rememberTextMeasurer()
                    val titleStyle = MiuixTheme.textStyles.body1
                    val density = LocalDensity.current
                    val titleMaxWidth = remember(titleMeasurer, titleStyle, density) {
                        with(density) {
                            titleMeasurer.measure(
                                text = AnnotatedString("中".repeat(TOP_BAR_TITLE_CJK_WIDTH)),
                                style = titleStyle,
                                maxLines = 1,
                            ).size.width.toDp()
                        }
                    }
                    // 标题整体上移 0.2 个字高（字号高度，不是行高；原 0.3，再下移 10%）。
                    // fontSize 若是 Unspecified，value 会是 NaN、toDp() 得 0，看起来就像没位移，
                    // 所以这里取 sp 值并兜底到 body1 的 16sp 再按 fontScale 换算成 dp。
                    // 与 COUI 一致：不再上移，回到顶栏按钮的水平中线，并加粗。
                    Text(
                        text = title,
                        modifier = Modifier.widthIn(max = titleMaxWidth),
                        style = titleStyle,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                // 角色对话的身份标识放在顶栏左侧，点按进入角色详情；普通对话保持空标题。
                homeCharacter?.let { character ->
                    CharacterTopBarIdentity(character = character, onClick = onOpenCharacter)
                }
            }
        } else {
            MiuixBackButton(onClick = onBack)
        }
    }
    val actions: @Composable RowScope.() -> Unit = {
        if (isHome) {
            // DeepSeek 余额/累计消费胶囊的落点：上游重构顶栏后它只剩参数、没有渲染处，胶囊整块消失（2026-10-15 修）。
            conversationStatus?.invoke()
            // 新建对话移到顶栏，与「更多选项」并排，省一层点击；自动朗读开关已由上游移入菜单。
            IconButton(onClick = onNewConversation) {
                Icon(
                    imageVector = ChatActionIcons.NewChat,
                    modifier = Modifier.size(ConversationPanelMetrics.NewChatIconSize),
                    contentDescription = stringResource(R.string.action_new_conversation),
                )
            }
            TopBarOverflowMenu(
                onOpenTerminal = onOpenTerminal,
                onOpenBrowser = onOpenBrowser,
            )
        }
    }

    val compactTitle = isHome || route is AppRoute.Terminal
    if (compactTitle) {
        SmallTopAppBar(
            title = titleForRoute(route),
            color = color,
            scrollBehavior = scrollBehavior,
            navigationIcon = navigationIcon,
            actions = actions,
        )
    } else {
        AdaptiveTopAppBar(
            title = titleForRoute(route),
            color = color,
            scrollBehavior = scrollBehavior,
            navigationIcon = navigationIcon,
            actions = actions,
        )
    }
}


private val TopBarMenuIconSize = 20.dp

@Composable
private fun TopBarOverflowMenu(
    onOpenTerminal: () -> Unit,
    onOpenBrowser: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val speechSettings by remember { SpeechSettingsRepository.settingsFlow() }.collectAsState(initial = null)
    val hasSpeechPlayback = speechSettings?.let { it.tts != TtsProvider.NONE } == true
    val autoReadEnabled = speechSettings?.chatAutoSpeak == true
    Box {
        IconButton(onClick = { showMenu = true }) {
            Icon(
                imageVector = Icons.Outlined.MoreHoriz,
                modifier = Modifier.size(24.dp),
                contentDescription = stringResource(R.string.action_more),
            )
        }
        WindowListPopup(
            show = showMenu,
            alignment = PopupPositionProvider.Align.End,
            onDismissRequest = { showMenu = false },
        ) {
            val openTerminalText = stringResource(R.string.action_open_terminal)
                            val openBrowserText = stringResource(R.string.action_open_browser)
            val autoReadText = stringResource(
                if (autoReadEnabled) R.string.speech_disable_auto_read else R.string.speech_enable_auto_read,
            )
            val menuItems = remember(
                openTerminalText,
                openBrowserText,
                autoReadText,
                hasSpeechPlayback,
                autoReadEnabled,
            ) {
                listOf(
                    DropdownItem(
                        text = openTerminalText,
                        icon = { modifier ->
                            Icon(
                                imageVector = Icons.Rounded.Terminal,
                                contentDescription = null,
                                modifier = modifier.size(TopBarMenuIconSize),
                            )
                        },
                    ),
                    DropdownItem(
                        text = openBrowserText,
                        icon = { modifier ->
                            Icon(
                                imageVector = Icons.Rounded.Language,
                                contentDescription = null,
                                modifier = modifier.size(TopBarMenuIconSize),
                            )
                        },
                    ),
                ) + if (hasSpeechPlayback) {
                    listOf(
                        DropdownItem(
                            text = autoReadText,
                            icon = { modifier ->
                                Icon(
                                    imageVector = if (autoReadEnabled) Icons.AutoMirrored.Rounded.VolumeUp
                                    else Icons.AutoMirrored.Rounded.VolumeOff,
                                    contentDescription = null,
                                    modifier = modifier.size(TopBarMenuIconSize),
                                )
                            },
                        ),
                    )
                } else emptyList()
            }
            ListPopupColumn {
                menuItems.forEachIndexed { index, item ->
                    DropdownImpl(
                        item = item,
                        optionSize = menuItems.size,
                        isSelected = false,
                        index = index,
                        onSelectedIndexChange = {
                            showMenu = false
                            if (hasSpeechPlayback && index == menuItems.lastIndex) {
                                speechSettings?.let { current ->
                                    scope.launch {
                                        SpeechSettingsRepository.save(current.copy(chatAutoSpeak = !current.chatAutoSpeak))
                                    }
                                }
                            } else when (index) {
                                0 -> onOpenTerminal()
                                1 -> onOpenBrowser()
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

@Composable
private fun titleForRoute(route: AppRoute?): String = when (route) {
    is AppRoute.Home, is AppRoute.Browser -> ""
    is AppRoute.Terminal -> stringResource(R.string.route_terminal)
    is AppRoute.Tools -> stringResource(R.string.route_tools)
    is AppRoute.WebToolsSettings -> stringResource(R.string.web_tools_settings_title)
    is AppRoute.Skills -> stringResource(R.string.route_skills)
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
