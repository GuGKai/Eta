package io.github.mangi.eta.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itos.coui.effect.couiButtonFeedback
import io.github.mangi.eta.R
import io.github.mangi.eta.data.model.AppearanceUiStyle
import io.github.mangi.eta.ui.app.LocalUiStyle
import io.github.mangi.eta.ui.components.coui.CouiChipTabs
import io.github.mangi.eta.ui.components.coui.CouiSearchBarBox
import io.github.mangi.eta.ui.model.AgentModelOptionUi
import io.github.mangi.eta.ui.model.AgentModelPickerUiState
import io.github.mangi.eta.ui.model.filterGroups
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.glass.glassPress
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.icon.glass.Ok
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme
/**
 * 聊天首页顶栏标题：
 * - 角色对话：左侧头像直达角色详情，右侧展示会话标题（无标题时回落角色名）
 * - 普通对话：居中展示会话标题
 * - 新对话：不展示任何内容，顶栏只留两侧按钮
 *
 * 顶栏不再承载模型切换入口，换模型统一走输入栏的思考强度 / 模型控件。
 */
@Composable
internal fun AgentTopBarChatHeader(
    conversationTitle: String?,
    character: RoleplayChatUi?,
    onOpenCharacter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = MiuixTheme.colorScheme.surface.luminance() < 0.5f
    val isCoui = LocalUiStyle.current == AppearanceUiStyle.COUI
    val avatarInteractionSource = remember { MutableInteractionSource() }
    val avatarBorderColor = if (isDark) {
        Color.White.copy(alpha = 0.14f)
    } else {
        Color.Black.copy(alpha = 0.08f)
    }

    Box(modifier = modifier) {
        when {
            character != null -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .then(
                                if (isCoui) {
                                    Modifier.couiButtonFeedback(isDark, avatarInteractionSource, enabled = true)
                                } else {
                                    Modifier.glassPress(avatarInteractionSource, enabled = true)
                                },
                            )
                            .clickable(
                                interactionSource = avatarInteractionSource,
                                indication = null,
                                role = Role.Button,
                                onClick = onOpenCharacter,
                            )
                            .semantics { contentDescription = character.characterName }
                            .padding(2.dp),
                    ) {
                        CharacterAvatar(
                            name = character.characterName,
                            avatarPath = character.avatarPath,
                            size = 28.dp,
                            shape = CircleShape,
                            borderWidth = 0.5.dp,
                            borderColor = avatarBorderColor,
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = conversationTitle?.takeIf(String::isNotBlank) ?: character.characterName,
                        style = MiuixTheme.textStyles.body1.copy(
                            fontSize = if (isCoui) 15.5.sp else 17.sp,
                            lineHeight = if (isCoui) 19.sp else 22.sp,
                            fontWeight = if (isCoui) FontWeight.SemiBold else FontWeight.Bold,
                        ),
                        color = MiuixTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = if (isCoui) 6.dp else 16.dp, vertical = 3.dp),
                    )
                }
            }

            !conversationTitle.isNullOrBlank() -> {
                Text(
                    text = conversationTitle,
                    style = MiuixTheme.textStyles.body1.copy(
                        fontSize = if (isCoui) 15.5.sp else 17.sp,
                        lineHeight = if (isCoui) 19.sp else 22.sp,
                        fontWeight = if (isCoui) FontWeight.SemiBold else FontWeight.Bold,
                    ),
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = if (isCoui) 10.dp else 16.dp, vertical = 3.dp),
                )
            }

            else -> Unit
        }
    }
}

internal data class ModelPickerFollowOption(
    val title: String,
    val subtitle: String?,
    val modelId: String?,
    val sourceType: String?,
)

private sealed interface ModelSheetEntry {
    val key: String

    data class Follow(val option: ModelPickerFollowOption) : ModelSheetEntry {
        override val key: String get() = "follow_primary"
    }

    data class Header(val providerId: String, val name: String) : ModelSheetEntry {
        override val key: String get() = "header:$providerId"
    }

    data class Option(val model: AgentModelOptionUi) : ModelSheetEntry {
        override val key: String get() = "model:${model.providerId}:${model.id}"
    }
}

@Composable
internal fun ModelPickerSheetContent(
    pickerState: AgentModelPickerUiState,
    selectedModelId: String?,
    followOption: ModelPickerFollowOption?,
    onSelectModel: (String?) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var providerFilter by remember { mutableStateOf<String?>(null) }
    val entries = remember(pickerState.providerGroups, query, providerFilter, followOption) {
        val followEntries = if (followOption != null && query.isBlank() && providerFilter == null) {
            listOf(ModelSheetEntry.Follow(followOption))
        } else {
            emptyList()
        }
        val groupEntries = pickerState.filterGroups(query)
            .filter { providerFilter == null || it.providerId == providerFilter }
            .flatMap { group ->
                // 只看一个服务商时，筛选标签已经说明了归属，分组标题只会重复。
                val header = if (providerFilter == null) {
                    listOf(ModelSheetEntry.Header(group.providerId, group.providerName))
                } else {
                    emptyList()
                }
                header + group.models.map(ModelSheetEntry::Option)
            }
        followEntries + groupEntries
    }
    val selectedIndex = entries.indexOfFirst { entry ->
        when (entry) {
            is ModelSheetEntry.Follow -> selectedModelId == null
            is ModelSheetEntry.Option -> entry.model.id == selectedModelId
            is ModelSheetEntry.Header -> false
        }
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (selectedIndex - 2).coerceAtLeast(0))
    LaunchedEffect(listState) {
        snapshotFlow { query to providerFilter }.drop(1).collect { listState.scrollToItem(0) }
    }
    val select = rememberConfirmedSelection(onSelectModel)
    val effectiveSelectedId = select.pending?.let { it.modelId } ?: selectedModelId
    val isFollowSelected = if (select.pending != null) {
        select.pending?.modelId == null
    } else {
        selectedModelId == null
    }
    // 面板高度自适应：正文只给一个宽松上限，列表用 weight(fill = false) 按内容撑开，
    // 内容短面板就矮、内容长最多到这里为止，搜索行与筛选标签始终留在原位。
    // 上限只给 COUI：它的面板按内容测量、能一路撑到窗口高度，得自己留出顶栏与拖动条；
    // MIUIX 的 GlassBottomSheet 已经把正文夹在标题以下的可用区域里，再加窗口比例会顶出面板。
    val panelMaxHeight = rememberOverlaySheetMaxHeight()
    val isCoui = LocalUiStyle.current == AppearanceUiStyle.COUI

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isCoui) {
                    Modifier.heightIn(max = panelMaxHeight)
                } else {
                    Modifier.navigationBarsPadding()
                },
            ),
    ) {
        when (LocalUiStyle.current) {
            AppearanceUiStyle.COUI -> {
                CouiSearchBarBox(
                    query = query,
                    onQueryChange = { query = it },
                    placeholder = stringResource(R.string.ui_search_model_df5586),
                    backgroundColor = Color.Transparent,
                )
                if (pickerState.providerGroups.size > 1) {
                    val allLabel = stringResource(R.string.model_picker_filter_all)
                    val tabItems = remember(pickerState.providerGroups, allLabel) {
                        listOf("" to allLabel) + pickerState.providerGroups.map { it.providerId to it.providerName }
                    }
                    CouiChipTabs(
                        items = tabItems,
                        selectedKey = providerFilter.orEmpty(),
                        onSelectedKeyChange = { key -> providerFilter = key.takeIf { it.isNotEmpty() } },
                        backgroundColor = Color.Transparent,
                    )
                }
            }
            AppearanceUiStyle.MIUIX -> {
                InputField(
                    query = query,
                    onQueryChange = { query = it },
                    onSearch = {},
                    expanded = false,
                    onExpandedChange = {},
                    label = stringResource(R.string.ui_search_model_df5586),
                    color = glassOverlayFieldBackgroundColor(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 4.dp, bottom = 8.dp),
                )
                if (pickerState.providerGroups.size > 1) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 8.dp),
                    ) {
                        item(key = "all") {
                            EtaChoiceChip(
                                text = stringResource(R.string.model_picker_filter_all),
                                selected = providerFilter == null,
                                onClick = { providerFilter = null },
                            )
                        }
                        items(pickerState.providerGroups, key = { it.providerId }) { group ->
                            EtaChoiceChip(
                                text = group.providerName,
                                selected = providerFilter == group.providerId,
                                onClick = {
                                    providerFilter = if (providerFilter == group.providerId) null else group.providerId
                                },
                            )
                        }
                    }
                }
            }
        }
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = 2.dp, bottom = 16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false),
        ) {
            if (entries.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = stringResource(R.string.page_no_matching_model_found_ae7e96),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 48.dp),
                    )
                }
            }
            items(entries, key = ModelSheetEntry::key, contentType = { it::class }) { entry ->
                when (entry) {
                    is ModelSheetEntry.Follow -> ModelPickerOptionRow(
                        title = entry.option.title,
                        subtitle = entry.option.subtitle,
                        modelId = entry.option.modelId,
                        sourceType = entry.option.sourceType,
                        selected = isFollowSelected,
                        onClick = { select(null) },
                    )
                    is ModelSheetEntry.Header -> ModelProviderLabel(entry.name)
                    is ModelSheetEntry.Option -> ModelPickerOptionRow(
                        title = entry.model.displayName,
                        subtitle = entry.model.modelId.takeIf {
                            !it.equals(entry.model.displayName, ignoreCase = true)
                        },
                        modelId = entry.model.modelId,
                        sourceType = entry.model.providerSourceType,
                        selected = entry.model.id == effectiveSelectedId,
                        onClick = { select(entry.model.id) },
                    )
                }
            }
        }
    }
}

private class PendingModelSelection(val modelId: String?)

/**
 * 点选后先把对勾移到新模型上，停留片刻再收起弹层，让人看清选中了谁。
 * 弹层收起会让作用域随组合一起离开，未完成的确认随之取消，不会回写已关闭的界面。
 */
private class ConfirmedSelection(
    private val onConfirm: (String?) -> Unit,
    private val launch: (suspend () -> Unit) -> Unit,
) {
    var pending by mutableStateOf<PendingModelSelection?>(null)
        private set

    operator fun invoke(modelId: String?) {
        if (pending != null) return
        pending = PendingModelSelection(modelId)
        launch {
            try {
                delay(SelectionConfirmMs)
                onConfirm(modelId)
                // 等待关闭动画完成后再清状态，避免淡出期间对勾回跳，也防止未销毁宿主再次打开时锁定点击。
                delay(400L)
            } finally {
                pending = null
            }
        }
    }
}

@Composable
private fun rememberConfirmedSelection(onConfirm: (String?) -> Unit): ConfirmedSelection {
    val scope = rememberCoroutineScope()
    val currentOnConfirm by rememberUpdatedState(onConfirm)
    return remember { ConfirmedSelection({ currentOnConfirm(it) }) { block -> scope.launch { block() } } }
}

@Composable
private fun ModelProviderLabel(name: String, horizontalPadding: Dp = 24.dp) {
    Text(
        text = name,
        style = MiuixTheme.textStyles.footnote1,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding)
            .padding(top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun ModelPickerOptionRow(
    title: String,
    subtitle: String?,
    modelId: String?,
    sourceType: String?,
    selected: Boolean,
    onClick: () -> Unit,
    horizontalPadding: Dp = 12.dp,
) {
    val isCoui = LocalUiStyle.current == AppearanceUiStyle.COUI
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val containerColor = when {
        !selected -> Color.Transparent
        isCoui -> MiuixTheme.colorScheme.tertiaryContainer
        // 模型列表行是大块面，不跟着上面那排 chip 的白字实底走：
        // 保持原来的浅主题色底 + 主题色文字，只有勾选状态强调一下。
        else -> MiuixTheme.colorScheme.primary.copy(alpha = if (isDark) 0.16f else 0.08f)
    }
    val contentColor = when {
        !selected -> MiuixTheme.colorScheme.onSurface
        isCoui -> MiuixTheme.colorScheme.onTertiaryContainer
        else -> MiuixTheme.colorScheme.primary
    }
    val checkIcon = if (isCoui) Icons.Rounded.Check else MiuixIcons.Glass.Ok
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = 2.dp)
            .then(
                if (selected) {
                    Modifier.squircleSurface(color = containerColor, cornerRadius = 14.dp)
                } else {
                    Modifier.clip(RoundedCornerShape(14.dp))
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ModelBrandMark(
            modelId = modelId,
            sourceType = sourceType,
            size = 20.dp,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MiuixTheme.textStyles.body1,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = subtitle,
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (selected) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = checkIcon,
                contentDescription = stringResource(R.string.ui_current_model_a0af8f),
                modifier = Modifier.size(18.dp),
                tint = contentColor,
            )
        }
    }
}

private const val SelectionConfirmMs = 140L
