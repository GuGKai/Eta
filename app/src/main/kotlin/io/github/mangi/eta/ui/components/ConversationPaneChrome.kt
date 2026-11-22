package io.github.mangi.eta.ui.components

import android.graphics.BlendMode as AndroidBlendMode
import android.graphics.LinearGradient
import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MapsUgc
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.foundation.Image
import androidx.compose.material.icons.rounded.Add
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itos.coui.backdrop.CouiBackdrop
import com.itos.coui.backdrop.ProvideCouiBackdrop
import com.itos.coui.backdrop.couiBackdrop
import com.itos.coui.backdrop.drawCouiBackdrop
import com.itos.coui.backdrop.rememberCouiBackdrop
import com.itos.coui.effect.CouiFloatingRenderer
import com.itos.coui.effect.CouiFloatingSurface
import com.itos.coui.effect.LocalCouiContourGlowEnabled
import com.itos.coui.effect.SpotlightPreset
import com.itos.coui.effect.couiButtonFeedback
import com.itos.coui.effect.couiFloatingSurface
import com.itos.coui.effect.drawCouiGradientBackdrop
import com.itos.coui.effect.couiToolbarFeedback
import com.itos.coui.effect.rememberCouiSpotlight
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.motion.couiPressMotion
import com.itos.coui.motion.couiTouchMotion
import com.itos.coui.motion.rememberCouiPressMotion
import com.itos.coui.motion.rememberCouiTouchMotion
import com.itos.coui.search.CouiSearchBarDefaults
import com.itos.coui.search.SearchBar
import com.itos.coui.search.rememberCouiSearchBarState
import com.itos.coui.shape.CouiCapsuleShape
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text as CouiText
import com.itos.coui.theme.CouiTheme
import com.itos.coui.topappbar.CouiTopAppBarButtonDefaults
import com.itos.coui.topappbar.CouiTopAppBarDefaults
import com.itos.coui.topappbar.TopAppBarSurface
import com.itos.coui.topappbar.TopAppBarButtonGroup
import io.github.mangi.eta.R
import io.github.mangi.eta.data.model.AppearanceUiStyle
import io.github.mangi.eta.ui.app.LocalAppearanceSettings
import io.github.mangi.eta.ui.app.LocalUiStyle
import io.github.mangi.eta.ui.components.coui.CouiIcon
import io.github.mangi.eta.ui.components.coui.CouiTopAppBarIconButton
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.ProgressiveBlur
import top.yukonga.miuix.kmp.blur.progressiveTextureBlur
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.glass.GlassIconButton
import top.yukonga.miuix.kmp.glass.GlassTopAppBarDefaults
import top.yukonga.miuix.kmp.glass.glassPanel
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.NewMessage
import top.yukonga.miuix.kmp.icon.glass.Search
import top.yukonga.miuix.kmp.icon.glass.SecurityStatus
import top.yukonga.miuix.kmp.icon.glass.SettingsOutline
import top.yukonga.miuix.kmp.material.GlassDefaults
import top.yukonga.miuix.kmp.material.GlassShadows
import top.yukonga.miuix.kmp.material.GlassShape
import top.yukonga.miuix.kmp.material.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal object ConversationPanelMetrics {
    val PaneHorizontalPadding = 16.dp
    val TopBarHeight = 56.dp

    /** 工作区 chip 栏固定高度：顶栏材质把它一起盖住。 */
    val WorkspaceChipsHeight = 44.dp
    val BottomDockHeight = 44.dp
    val BottomDockVerticalPadding = 10.dp
    val BottomDockGap = 8.dp
    val DockButtonSize = 44.dp
    val DockIconSize = 20.dp
    val BrandBadgeSize = 26.dp
    val BrandIconSize = 16.dp
    val NewChatIconSize = 19.dp

    val WorkbenchCornerRadius = 22.dp
    val WorkbenchRowMinHeight = 46.dp
    val WorkbenchIconSize = 19.dp
    val WorkbenchRowHorizontalPadding = 16.dp
    val WorkbenchIconGap = 12.dp

    val FirstSectionTopPadding = 22.dp
    val SectionTopPadding = 20.dp
    val SectionBottomPadding = 6.dp
    val RowMinHeight = 44.dp
    val RowGap = 3.dp
    val RowCornerRadius = 14.dp
    val RowHorizontalPadding = 14.dp
    val RowVerticalPadding = 10.dp
    val CharacterAvatarSize = 24.dp
    val CharacterAvatarGap = 10.dp
    val ActiveDotSize = 6.dp
    val ActiveDotGap = 8.dp
    val ActionIconSize = 20.dp
    val EmptyVerticalPadding = 36.dp
}

/**
 * 侧栏与外层推移容器共用的背景色。
 *
 * 浅色下保持与各自系统设置页一致的温和浅底（COUI #F0F1F2 / Miuix surface），使纯白分组卡片浮雕分明；
 * 深色下将侧栏稳稳抬到介于聊天黑底与前景卡片之间的层级，确保两侧界面与卡片各自独立可辨。
 */
@Composable
internal fun conversationPaneCardColor(): Color {
    if (LocalUiStyle.current == AppearanceUiStyle.COUI) {
        return if (CouiTheme.isDark) Color(0xFF212328) else Color.White
    }
    return MiuixTheme.colorScheme.surfaceContainer
}

/**
 * 侧栏推开时压在会话页上的遮罩底色：深色模式压黑、浅色模式提白。
 * 透明度按拉开进度从 0 长到 [conversationPaneChatScrimAlpha]。
 */
@Composable
internal fun conversationPaneChatScrimColor(): Color {
    if (LocalUiStyle.current == AppearanceUiStyle.COUI) {
        return if (CouiTheme.isDark) Color.Black else Color.White
    }
    return if (MiuixTheme.colorScheme.background.luminance() > 0.5f) Color.White else Color.Black
}

/**
 * 遮罩满开时的透明度：浅色下会话页要压得更白才分得开；
 * 深色不再叠遮罩，靠分界线那道阴影足够分层。
 */
@Composable
internal fun conversationPaneChatScrimAlpha(): Float {
    val dark = if (LocalUiStyle.current == AppearanceUiStyle.COUI) {
        CouiTheme.isDark
    } else {
        MiuixTheme.colorScheme.background.luminance() <= 0.5f
    }
    return if (dark) ChatScrimAlphaDark else ChatScrimAlphaLight
}

private const val ChatScrimAlphaLight = 0.50f
private const val ChatScrimAlphaDark = 0f

/**
 * 会话页向左投出的渐变阴影色：贴着分界最深，向外渐隐到透明。
 * 浅色用很淡的深灰压一道，深色反过来用比面板底色亮的灰，当分界线上的高光。
 */
@Composable
internal fun conversationPaneEdgeShadowColor(): Color {
    if (LocalUiStyle.current == AppearanceUiStyle.COUI) {
        return if (CouiTheme.isDark) Color(0x2E828282) else Color(0x0D333333)
    }
    return if (MiuixTheme.colorScheme.background.luminance() > 0.5f) {
        Color(0x0D333333)
    } else {
        Color(0x2E828282)
    }
}

/**
 * 阴影带宽度：深色只留很窄的一道（比底色亮，当分界线上的高光），浅色维持原来 15dp。
 */
@Composable
internal fun conversationPaneEdgeShadowWidth(): Dp {
    val dark = if (LocalUiStyle.current == AppearanceUiStyle.COUI) {
        CouiTheme.isDark
    } else {
        MiuixTheme.colorScheme.background.luminance() <= 0.5f
    }
    return if (dark) EdgeShadowDarkWidth else EdgeShadowLightWidth
}

private val EdgeShadowLightWidth = 15.dp
/** 暗色临时关到 0（不画阴影）；要恢复改回 5.dp 即可，绘制侧对 0 已经短路。 */
private val EdgeShadowDarkWidth = 0.dp

/**
 * 会话页左缘发丝描边色：直接沿用改造前侧栏右侧那道 0.5dp 收边线的取色
 * （COUI 深色白 8.6%，Miuix 走 dividerLine 42%）。浅色返回透明，即不描边。
 */
@Composable
internal fun conversationPaneEdgeStrokeColor(): Color {
    if (LocalUiStyle.current == AppearanceUiStyle.COUI) {
        return if (CouiTheme.isDark) Color(0x16FFFFFF) else Color.Transparent
    }
    return if (MiuixTheme.colorScheme.background.luminance() > 0.5f) {
        Color.Transparent
    } else {
        MiuixTheme.colorScheme.dividerLine.copy(alpha = 0.42f)
    }
}

/** 描边宽度：和改造前那道收边线同宽。 */
private val EdgeStrokeWidth = 0.5.dp
private val EdgeStrokeWidthNone = 0.dp

@Composable
internal fun conversationPaneEdgeStrokeWidth(): Dp =
    if (conversationPaneEdgeStrokeColor().alpha > 0f) EdgeStrokeWidth else EdgeStrokeWidthNone

@Composable
internal fun ConversationPaneTopBar(
    containerColor: Color,
    couiBackdrop: CouiBackdrop?,
    miuixBackdrop: LayerBackdrop?,
    /** COUI 顶栏的模糊源（Miuix LayerBackdrop，内容真实写入 layer）；null 时退回渐变底色。 */
    paneBlurBackdrop: LayerBackdrop?,
    onNewConversation: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * 顶栏正下方的第二行（工作区 chip 栏）。
     *
     * 直接画在 TopAppBarSurface 的 content 里，整块材质是同一个超宽区域、模糊连续；
     * 这一行里的 COUI 浮动组件必须从 `LocalCouiToolbarBackdrop` 取背景材质，
     * 不能再自己 `rememberCouiBackdrop()`（独立采样会和顶栏互相顶掉，模糊失效）。
     */
    secondaryBar: (@Composable (CouiBackdrop?, LayerBackdrop?) -> Unit)? = null,
) {
    val barContent: @Composable () -> Unit = {
        Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                    )
                .padding(horizontal = ConversationPanelMetrics.PaneHorizontalPadding)
                .height(ConversationPanelMetrics.TopBarHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PaneBrandHeader(modifier = Modifier.weight(1f))
            NewConversationIconButton(
                // COUI 风格下顶栏材质由 TopAppBarSurface 统一 provide，按钮直接走 COUI 实现。
                hasCouiToolbarBackdrop = LocalUiStyle.current == AppearanceUiStyle.COUI &&
                    couiBackdrop != null,
                miuixBackdrop = miuixBackdrop,
                onClick = onNewConversation,
            )
        }
    }

    when (LocalUiStyle.current) {
        AppearanceUiStyle.COUI -> {
            if (couiBackdrop != null) {
                // 材质本体直接用 COUI 的 TopAppBarSurface（TopAppBar 只是它的一层封装）：
                // 只有拿到这个表面，才能把工作区 chip 栏塞进它的 content 里，
                // 让模糊的下沿跟着内容一起长到 chip 底部，而不是停在标题栏下边 12dp。
                // 标题行按紧凑栏几何手排：52dp 高、左右留白、标题占满、操作按钮靠右；
                // 按钮材质仍由表面内部 provide 的 LocalCouiToolbarBackdrop 提供。
                val barDark = CouiTheme.isDark
                val barTheme = CouiPageDefaults.themeColors(barDark)
                TopAppBarSurface(
                    modifier = modifier.fillMaxWidth(),
                    backdrop = couiBackdrop,
                    gradientColor = containerColor,
                    dark = barDark,
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .windowInsetsPadding(CouiTopAppBarDefaults.windowInsets)
                                .height(CouiTopAppBarDefaults.height)
                                .padding(horizontal = ConversationPanelMetrics.PaneHorizontalPadding),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PaneBrandHeader(Modifier.weight(1f))
                            TopAppBarButtonGroup {
                                CouiTopAppBarIconButton(
                                    icon = ChatActionIcons.NewChat,
                                    contentDescription = stringResource(R.string.action_new_conversation),
                                    onClick = onNewConversation,
                                    dark = barDark,
                                    colors = barTheme,
                                    iconSize = ConversationPanelMetrics.NewChatIconSize,
                                )
                            }
                        }
                        secondaryBar?.invoke(couiBackdrop, null)
                    }
                }
            } else {
                Box(
                    modifier = modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                0.0f to containerColor,
                                0.8f to containerColor.copy(alpha = 0.94f),
                                1.0f to Color.Transparent,
                            ),
                        ),
                ) {
                    Column {
                        barContent()
                        secondaryBar?.invoke(null, null)
                    }
                }
            }
        }
        AppearanceUiStyle.MIUIX -> {
            val progressiveBlurEnabled = LocalAppearanceSettings.current.progressiveBlurEnabled
            val progressiveBackdrop = miuixBackdrop?.takeIf {
                progressiveBlurEnabled && isRuntimeShaderSupported()
            }
            val progressive = progressiveBackdrop != null
            val fallbackBandBrush = remember(containerColor) {
                Brush.verticalGradient(
                    0.0f to containerColor,
                    0.8f to containerColor.copy(alpha = 0.94f),
                    1.0f to Color.Transparent,
                )
            }
            val blurColors = BlurDefaults.blurColors(
                blendColors = remember(containerColor) { listOf(BlendColorEntry(containerColor.copy(alpha = 0.3f))) },
            )
            Layout(
                modifier = modifier.fillMaxWidth(),
                content = {
                    Box(
                        modifier = if (progressiveBackdrop != null) {
                            Modifier.progressiveTextureBlur(
                                backdrop = progressiveBackdrop,
                                shape = RectangleShape,
                                gradient = ProgressiveBlur.Top.copy(curve = 0.5f),
                                blurRadius = GlassTopAppBarDefaults.ProgressiveBlurRadius.value,
                                colors = blurColors,
                            )
                        } else {
                            Modifier.background(fallbackBandBrush)
                        },
                    )
                    Box(modifier = Modifier.fillMaxWidth()) {
                        barContent()
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
    }
}

@Composable
private fun PaneBrandHeader(modifier: Modifier = Modifier) {
    val isCoui = LocalUiStyle.current == AppearanceUiStyle.COUI
    val dark = if (isCoui) CouiTheme.isDark else MiuixTheme.colorScheme.surface.luminance() < 0.5f
    val accent = if (isCoui) CouiTheme.colorScheme.accent else MiuixTheme.colorScheme.primary
    val badgeBrush = remember(accent, dark) {
        Brush.linearGradient(
            colors = listOf(
                accent,
                lerp(accent, if (dark) Color.White else Color.Black, 0.12f),
            ),
        )
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.pane_brand_mark),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(ConversationPanelMetrics.BrandBadgeSize)
                .clip(CircleShape),
        )
        Spacer(Modifier.width(10.dp))
        if (isCoui) {
            CouiText(
                text = stringResource(R.string.app_name),
                kind = CouiTextKind.TopBar,
                color = CouiPageDefaults.themeColors(dark).title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            Text(
                text = stringResource(R.string.app_name),
                style = MiuixTheme.textStyles.title3,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 侧栏顶栏与会话页顶栏共用的「新建会话」按钮（上游圆形气泡加号样式）。 */
@Composable
internal fun NewConversationIconButton(
    hasCouiToolbarBackdrop: Boolean,
    miuixBackdrop: Backdrop?,
    onClick: () -> Unit,
) {
    val description = stringResource(R.string.action_new_conversation)
    when (LocalUiStyle.current) {
        AppearanceUiStyle.COUI -> {
            val dark = CouiTheme.isDark
            val colors = CouiPageDefaults.themeColors(dark)
            if (hasCouiToolbarBackdrop) {
                // 材质、按压、玻璃全部交给 COUI 顶栏按钮自己画（它从 LocalCouiToolbarBackdrop 采样）。
                CouiTopAppBarIconButton(
                    icon = Icons.Rounded.MapsUgc,
                    contentDescription = description,
                    onClick = onClick,
                    dark = dark,
                    colors = colors,
                    iconSize = ConversationPanelMetrics.NewChatIconSize,
                )
            } else {
                // 拿不到 COUI 顶栏材质的纯回退：半透明底 + 描边，绝不垫自造模糊。
                val source = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (dark) Color(0x99252830) else Color(0xB3FFFFFF),
                            CircleShape,
                        )
                        .border(
                            0.5.dp,
                            if (dark) Color(0x1EFFFFFF) else Color(0x12000000),
                            CircleShape,
                        )
                        .couiButtonFeedback(dark, source, enabled = true)
                        .clickable(
                            interactionSource = source,
                            indication = null,
                            role = Role.Button,
                            onClick = onClick,
                        )
                        .semantics { contentDescription = description },
                    contentAlignment = Alignment.Center,
                ) {
                    CouiIcon(
                        imageVector = Icons.Rounded.MapsUgc,
                        contentDescription = null,
                        modifier = Modifier.size(ConversationPanelMetrics.NewChatIconSize),
                        tint = colors.title,
                    )
                }
            }
        }
        AppearanceUiStyle.MIUIX -> {
            val colors = MiuixTheme.colorScheme
            GlassIconButton(
                onClick = onClick,
                backdrop = miuixBackdrop,
                size = GlassTopAppBarDefaults.ButtonSize,
            ) {
                Icon(
                    imageVector = MiuixIcons.Glass.NewMessage,
                    contentDescription = description,
                    modifier = Modifier.size(28.dp),
                    tint = colors.onSurface,
                )
            }
        }
    }
}

/**
 * 侧栏搜索框的「点外部收起」协作：
 * dock 把搜索胶囊在窗口内的范围登记进来，Panel 在搜索框以外按下时把 dismissRequest 自增，
 * dock 观察到变化后自己收起（COUI 原生只认「取消」和返回键，这两件事 Panel 拿不到）。
 */
internal object ConversationPaneSearchDock {
    var barBoundsInWindow by mutableStateOf<Rect?>(null)
    var dismissRequest by mutableStateOf(0)
}

@Composable
internal fun ConversationPaneBottomDock(
    query: String,
    isRevealed: Boolean,
    hasPermissionAttention: Boolean,
    containerColor: Color,
    couiBackdrop: CouiBackdrop?,
    miuixBackdrop: LayerBackdrop?,
    onSearchChange: (String) -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 仅在侧边栏实际展开且当前窗口持有焦点时避让 IME，防止弹窗或主聊天页键盘触发侧栏重排。
    val applyIme = isRevealed && LocalWindowInfo.current.isWindowFocused
    val dockInsets = WindowInsets.safeDrawing
        .only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
        .let { base -> if (applyIme) base.union(WindowInsets.ime) else base }

    val isCoui = LocalUiStyle.current == AppearanceUiStyle.COUI
    val progressiveBlurEnabled = LocalAppearanceSettings.current.progressiveBlurEnabled
    val view = LocalView.current
    val couiDark = CouiTheme.isDark
    val activeMiuixBackdrop = if (!isCoui && isRuntimeShaderSupported()) {
        miuixBackdrop
    } else {
        null
    }
    val progressiveMiuixBackdrop = if (progressiveBlurEnabled) activeMiuixBackdrop else null
    val activeCouiBackdrop = if (isCoui && view.display != null) couiBackdrop else null
    // SearchBar 的 backdrop 形参是非空类型。canUseCouiNativeSearchBar 已经要求
    // activeCouiBackdrop 非空，这里的备用采样只是给编译器的兜底，非 COUI 主题下
    // 该分支根本不渲染，不会被 couiBackdrop 挂上去，也就不参与任何录制。
    val fallbackCouiBackdrop = rememberCouiBackdrop()
    val dockCouiBackdrop = activeCouiBackdrop ?: fallbackCouiBackdrop

    val bottomScrimBrush = remember(containerColor) {
        Brush.verticalGradient(
            0.0f to Color.Transparent,
            0.25f to containerColor.copy(alpha = 0.72f),
            0.55f to containerColor.copy(alpha = 0.94f),
            1.0f to containerColor,
        )
    }

    val miuixOrFallbackBackdropModifier = if (progressiveMiuixBackdrop != null) {
        Modifier
            .progressiveTextureBlur(
                backdrop = progressiveMiuixBackdrop,
                shape = RectangleShape,
                gradient = ProgressiveBlur.Bottom.copy(curve = 1.6f),
                blurRadius = 56f,
                colors = BlurColors(
                    blendColors = listOf(
                        BlendColorEntry(containerColor.copy(alpha = 0.36f)),
                    ),
                ),
            )
            .background(bottomScrimBrush)
    } else if (!isCoui || activeCouiBackdrop == null) {
        Modifier.background(bottomScrimBrush)
    } else {
        Modifier
    }

    val canUseCouiNativeSearchBar = remember(isCoui, activeCouiBackdrop) {
        isCoui &&
            activeCouiBackdrop != null &&
            runCatching { CouiFloatingRenderer.pair() }.isSuccess
    }
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    val searchState = rememberCouiSearchBarState(searchExpanded)
    // COUI SearchBar 的材质层按矩形铺、不按胶囊裁剪，收起动画期间会在右侧按钮位置露出一块
    // 兜底底色（fallbackBackground）。把兜底底色设成全透明，那块矩形就不再可见；
    // 正常态胶囊由材质层自己绘制，不依赖这档颜色。
    val searchBarColors = CouiSearchBarDefaults.colors(couiDark).copy(
        fallbackBackground = Color.Transparent,
    )
    val keyboard = LocalSoftwareKeyboardController.current

    // COUI 原生 SearchBar 只在「取消」按钮或返回键时收起，点别处既不丢焦点也不收。
    // 收起的唯一入口是内部的 updateTarget（外部只有 onExpandedChange 回调，拿不到主动调用），
    // 直接用它让 COUI 自己跑完宽度/取消态动画；expanded 变 false 后 SearchBar 内部会 releaseInput，
    // 焦点与键盘随即释放，不需要我们在外面 clearFocus。
    fun collapseSearch() {
        CouiSearchBarStateBridge.collapse(searchState)
    }
    LaunchedEffect(ConversationPaneSearchDock.dismissRequest) {
        if (ConversationPaneSearchDock.dismissRequest > 0 && searchExpanded) {
            searchExpanded = false
            onSearchChange("")
            keyboard?.hide()
            collapseSearch()
        }
    }
    LaunchedEffect(query, canUseCouiNativeSearchBar) {
        if (canUseCouiNativeSearchBar && query.isNotEmpty() && !searchExpanded) {
            searchExpanded = true
        }
    }
    BackHandler(enabled = canUseCouiNativeSearchBar && searchExpanded) {
        searchExpanded = false
        onSearchChange("")
        keyboard?.hide()
    }

    val permissionsDesc = if (hasPermissionAttention) {
        stringResource(R.string.conversation_permission_attention)
    } else {
        stringResource(R.string.route_permissions)
    }
    val settingsDesc = stringResource(R.string.route_settings)
    val searchPlaceholder = stringResource(R.string.conversation_search_hint)
    val cancelText = stringResource(R.string.action_cancel)
    val clearDescription = stringResource(R.string.page_clear_84fcd7)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(miuixOrFallbackBackdropModifier),
    ) {
        if (activeCouiBackdrop != null && DockBlurDebugEnabled) {
            // 独立背景图层：只负责画底部渐变模糊，搜索框与圆形按钮在它上面。
            CouiBottomGradientBlurLayer(
                backdrop = activeCouiBackdrop,
                recordBackdrop = activeCouiBackdrop,
                modifier = Modifier.matchParentSize(),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(dockInsets)
                .padding(
                    start = if (canUseCouiNativeSearchBar) 0.dp else ConversationPanelMetrics.PaneHorizontalPadding,
                    end = if (canUseCouiNativeSearchBar) 0.dp else ConversationPanelMetrics.PaneHorizontalPadding,
                    top = ConversationPanelMetrics.BottomDockVerticalPadding + 10.dp,
                    bottom = ConversationPanelMetrics.BottomDockVerticalPadding,
                ),
        ) {
            if (canUseCouiNativeSearchBar) {
                val buttonsProgress by animateFloatAsState(
                    targetValue = if (searchExpanded) 0f else 1f,
                    animationSpec = spring(dampingRatio = 0.86f, stiffness = 380f),
                    label = "CouiDockButtonsProgress",
                )
                Layout(
                    modifier = Modifier.fillMaxWidth(),
                    content = {
                        SearchBar(
                            modifier = Modifier
                                .fillMaxWidth()
                                .onGloballyPositioned { coordinates ->
                                    ConversationPaneSearchDock.barBoundsInWindow =
                                        coordinates.boundsInWindow()
                                },
                            state = searchState,
                            query = query,
                            onQueryChange = onSearchChange,
                            onExpandedChange = { next ->
                                searchExpanded = next
                                if (!next) onSearchChange("")
                            },
                            placeholder = searchPlaceholder,
                            // COUI 的取消胶囊在收起动画里会把「取消」两字连同玻璃材质一起
                            // 糊成一块边缘笔直的灰色矩形，一直拖到设置按钮淡入。收起过程中
                            // 先不给文字，只让空胶囊滑走即可，展开态照常显示。
                            cancelText = if (searchState.expanded || !searchState.active) {
                                cancelText
                            } else {
                                ""
                            },
                            clearDescription = clearDescription,
                            dark = couiDark,
                            backdrop = dockCouiBackdrop,
                            // COUI 的 SearchBar 把投影强度完全交给这个「滚动进度」：collapse 态下
                            // drop = (widthProgress - 0.8)/0.2，而折叠时 widthProgress 恒为 0，
                            // 于是 alphaScale 被算成 0，阴影色 alpha 也乘 0 —— 一条投影都没有。
                            // 侧栏底部 dock 没有滚动联动，固定喂 1，让投影与旁边的设置圆钮一致。
                            shadowProgress = 1f,
                            colors = searchBarColors,
                            onSearch = { keyboard?.hide() },
                        )
                        // 出现动画交给 COUI 材质本体（CouiFloatingSurface 的 alpha / scale），
                        // 不在 Row 上再套 graphicsLayer：外层 alpha 会让这一整棵子树走离屏合成，
                        // 节点矩形之外的投影和半影会被离屏缓冲裁成硬边方块——也就是之前
                        // 淡入时「阴影变矩形」的来源。COUI 内部是逐层 alpha（u_alpha 与
                        // shadowRenderer 各自的 alpha），不触发按主体尺寸的离屏，投影响应正常。
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PaneDockCircleButton(
                                icon = ConversationPaneIcons.Settings,
                                contentDescription = settingsDesc,
                                showBadge = false,
                                // 恢复 COUI 材质（主题玻璃感）。材质 patch 按节点矩形铺满，
                                // 按 COUI-Kit 规范不做裁剪，圆角由 shape 的描边与内容层决定。
                                couiBackdrop = activeCouiBackdrop,
                                appearProgress = buttonsProgress,
                                onClick = { if (!searchExpanded) onOpenSettings() },
                            )
                        }
                    },
                ) { measurables, constraints ->
                    val buttonsPlaceable = measurables[1].measure(
                        constraints.copy(minWidth = 0, minHeight = 0),
                    )
                    val sideMarginPx = ConversationPanelMetrics.PaneHorizontalPadding.roundToPx()
                    val gapPx = ConversationPanelMetrics.BottomDockGap.roundToPx()
                    // SearchBar 内部自带 16.dp 右边距，收起态保留 8.dp 视觉间距即可，避免双倍留白。
                    val reservedRightPx = ((buttonsPlaceable.width + sideMarginPx - (sideMarginPx - gapPx)) * buttonsProgress)
                        .toInt()
                        .coerceAtLeast(0)
                    val searchWidth = (constraints.maxWidth - reservedRightPx).coerceAtLeast(1)
                    val searchPlaceable = measurables[0].measure(
                        Constraints.fixedWidth(searchWidth).copy(
                            minHeight = 0,
                            maxHeight = constraints.maxHeight,
                        ),
                    )
                    val layoutHeight = maxOf(searchPlaceable.height, buttonsPlaceable.height)
                    layout(constraints.maxWidth, layoutHeight) {
                        searchPlaceable.placeRelative(
                            x = 0,
                            y = (layoutHeight - searchPlaceable.height) / 2,
                        )
                        if (buttonsProgress > 0.01f) {
                            // 原位放大淡入：不给 x 加位移，动画期间这一列已经预留 48dp。
                            buttonsPlaceable.placeRelative(
                                x = constraints.maxWidth - sideMarginPx - buttonsPlaceable.width,
                                y = (layoutHeight - buttonsPlaceable.height) / 2,
                            )
                        }
                    }
                }
            } else {
                val effectiveCouiBackdrop = activeCouiBackdrop
                val permissionsIcon = if (isCoui) ConversationPaneIcons.Permissions else MiuixIcons.Glass.SecurityStatus
                val settingsIcon = if (isCoui) ConversationPaneIcons.Settings else MiuixIcons.Glass.SettingsOutline
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ConversationPanelMetrics.BottomDockGap),
                ) {
                    PaneSearchCapsule(
                        query = query,
                        onSearchChange = onSearchChange,
                        couiBackdrop = effectiveCouiBackdrop,
                        cancelText = cancelText,
                        miuixBackdrop = activeMiuixBackdrop,
                        modifier = Modifier.weight(1f),
                    )
                    PaneDockCircleButton(
                        icon = permissionsIcon,
                        contentDescription = permissionsDesc,
                        showBadge = hasPermissionAttention,
                        couiBackdrop = effectiveCouiBackdrop,
                        miuixBackdrop = activeMiuixBackdrop,
                        onClick = onOpenPermissions,
                    )
                    PaneDockCircleButton(
                        icon = settingsIcon,
                        contentDescription = settingsDesc,
                        showBadge = false,
                        // 按 COUI-Kit 的标准用法：浮层组件都吃同一份 backdrop，不做裁剪。
                        couiBackdrop = effectiveCouiBackdrop,
                        miuixBackdrop = activeMiuixBackdrop,
                        onClick = onOpenSettings,
                    )
                }
            }
        }
    }
}

private class PaneSearchSyncState(var lastSynced: String)

@Composable
private fun PaneSearchCapsule(
    query: String,
    onSearchChange: (String) -> Unit,
    couiBackdrop: CouiBackdrop?,
    cancelText: String,
    miuixBackdrop: Backdrop?,
    modifier: Modifier = Modifier,
) {
    val placeholder = stringResource(R.string.conversation_search_hint)
    val clearDescription = stringResource(R.string.page_clear_84fcd7)
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var isFocused by remember { mutableStateOf(false) }

    val textFieldState = rememberTextFieldState(initialText = query)
    val syncState = remember { PaneSearchSyncState(query) }
    val currentOnSearchChange by rememberUpdatedState(onSearchChange)

    LaunchedEffect(query) {
        syncState.lastSynced = query
        if (textFieldState.text.toString() != query) {
            textFieldState.setTextAndPlaceCursorAtEnd(query)
        }
    }
    LaunchedEffect(textFieldState) {
        snapshotFlow { textFieldState.text.toString() }
            .distinctUntilChanged()
            .collect { currentText ->
                if (currentText != syncState.lastSynced) {
                    syncState.lastSynced = currentText
                    currentOnSearchChange(currentText)
                }
            }
    }

    val isCoui = LocalUiStyle.current == AppearanceUiStyle.COUI
    val couiDark = CouiTheme.isDark
    val miuixColors = MiuixTheme.colorScheme
    val miuixDark = miuixColors.surface.luminance() < 0.5f

    val textColor = if (isCoui) {
        CouiPageDefaults.themeColors(couiDark).title
    } else {
        LocalContentColor.current
    }
    var activeTextColor by remember { mutableStateOf(textColor) }
    LaunchedEffect(textColor) {
        activeTextColor = textColor
    }
    val accentColor = if (isCoui) CouiTheme.colorSchemeFor(couiDark).accent else miuixColors.primary
    val hintColor = if (isCoui) {
        if (couiDark) Color(0x70FFFFFF) else Color(0x66000000)
    } else {
        miuixColors.onSurfaceVariantSummary
    }
    val clearBgColor = if (isCoui) {
        if (couiDark) Color(0x4DFFFFFF) else Color(0x42000000)
    } else {
        miuixColors.onSurfaceVariantActions.copy(alpha = 0.65f)
    }
    // 延迟一帧到节点附着完成后再切换 TextStyle 颜色，规避 Compose 1.12 BasicTextField 在
    // AgentAppTheme movableContentOf 跨容器迁移节点时于未附着状态触发 TextFieldSizeConstrainerNode.update。
    val textStyle = TextStyle(
        fontSize = 15.sp,
        fontWeight = FontWeight.Normal,
        color = activeTextColor,
    )

    var dockEditing by remember { mutableStateOf(false) }

    val searchContent: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(ConversationPanelMetrics.BottomDockHeight)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    dockEditing = true
                    focusRequester.requestFocus()
                    keyboard?.show()
                }
                .padding(start = 14.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (isCoui) ConversationPaneIcons.Search else MiuixIcons.Glass.Search,
                contentDescription = null,
                modifier = Modifier.size(if (isCoui) 18.dp else 20.dp),
                tint = if (isFocused) accentColor else hintColor,
            )
            Spacer(Modifier.width(9.dp))
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (textFieldState.text.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = textStyle,
                        color = hintColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                key(isCoui) {
                    BasicTextField(
                        state = textFieldState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .onFocusChanged {
                                isFocused = it.isFocused
                                if (!it.isFocused) dockEditing = false
                            },
                        lineLimits = TextFieldLineLimits.SingleLine,
                        textStyle = textStyle,
                        cursorBrush = SolidColor(accentColor),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        onKeyboardAction = {
                            keyboard?.hide()
                            focusManager.clearFocus()
                        },
                    )
                }
            }
            AnimatedVisibility(
                visible = textFieldState.text.isNotEmpty(),
                enter = fadeIn() + scaleIn(initialScale = 0.8f),
                exit = fadeOut() + scaleOut(targetScale = 0.8f),
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .semantics { contentDescription = clearDescription }
                        .clickable(role = Role.Button) {
                            syncState.lastSynced = ""
                            textFieldState.clearText()
                            currentOnSearchChange("")
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    PaneClearGlyph(
                        modifier = Modifier.size(17.dp),
                        color = clearBgColor,
                    )
                }
            }
            AnimatedVisibility(
                visible = dockEditing,
                enter = fadeIn() + scaleIn(initialScale = 0.8f),
                exit = fadeOut() + scaleOut(targetScale = 0.8f),
            ) {
                Text(
                    text = cancelText,
                    style = MiuixTheme.textStyles.footnote1,
                    color = accentColor,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(role = Role.Button) {
                            syncState.lastSynced = ""
                            textFieldState.clearText()
                            currentOnSearchChange("")
                            dockEditing = false
                            focusManager.clearFocus()
                            keyboard?.hide()
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }

    if (isCoui) {
        CouiPaneFloatingSurface(
            modifier = modifier.height(ConversationPanelMetrics.BottomDockHeight),
            shape = CouiCapsuleShape,
            capsule = true,
            couiBackdrop = couiBackdrop,
            focused = isFocused,
            accentColor = accentColor,
            onClick = null,
        ) {
            searchContent()
        }
    } else {
        val pillShape = remember { GlassShape(ConversationPanelMetrics.BottomDockHeight / 2) }
        val fallbackBg = GlassTopAppBarDefaults.buttonFill()
        Box(
            modifier = modifier
                .height(ConversationPanelMetrics.BottomDockHeight)
                .glassPanel(
                    backdrop = miuixBackdrop,
                    shape = pillShape,
                    style = GlassDefaults.style(miuixDark),
                    material = GlassTopAppBarDefaults.buttonMaterial(),
                    stroke = GlassTopAppBarDefaults.buttonStroke(),
                    shadow = GlassShadows.Regular,
                    shading = false,
                    fallback = Modifier
                        .clip(CircleShape)
                        .background(fallbackBg),
                )
                .then(
                    if (isFocused) {
                        Modifier.border(1.2.dp, accentColor.copy(alpha = 0.65f), CircleShape)
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.CenterStart,
        ) {
            searchContent()
        }
    }
}

@Composable
private fun PaneDockCircleButton(
    icon: ImageVector,
    contentDescription: String,
    showBadge: Boolean,
    couiBackdrop: CouiBackdrop?,
    appearProgress: Float = 1f,
    miuixBackdrop: Backdrop? = null,
    onClick: () -> Unit,
) {
    val isCoui = LocalUiStyle.current == AppearanceUiStyle.COUI
    val couiDark = CouiTheme.isDark
    val miuixColors = MiuixTheme.colorScheme

    val iconTint = if (isCoui) {
        CouiPageDefaults.themeColors(couiDark).title
    } else {
        miuixColors.onSurface
    }
    val badgeBorderColor = if (isCoui) {
        if (couiDark) Color(0xFF23252A) else Color.White
    } else {
        GlassTopAppBarDefaults.buttonFill()
    }

    val buttonSize = if (isCoui) 40.dp else ConversationPanelMetrics.DockButtonSize
    Box(modifier = Modifier.size(buttonSize)) {
        if (isCoui) {
            CouiPaneFloatingSurface(
                modifier = Modifier.fillMaxSize(),
                shape = CouiTopAppBarButtonDefaults.circleShape,
                capsule = false,
                couiBackdrop = couiBackdrop,
                focused = false,
                accentColor = CouiTheme.colorSchemeFor(couiDark).accent,
                contentDescription = contentDescription,
                appearProgress = appearProgress,
                onClick = onClick,
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CouiIcon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(ConversationPanelMetrics.DockIconSize),
                        tint = iconTint,
                    )
                }
            }
        } else {
            GlassIconButton(
                onClick = onClick,
                backdrop = miuixBackdrop,
                size = ConversationPanelMetrics.DockButtonSize,
                modifier = Modifier.fillMaxSize(),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    modifier = Modifier.size(28.dp),
                    tint = iconTint,
                )
            }
        }

        if (showBadge) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = if (isCoui) 6.dp else 7.dp, end = if (isCoui) 6.dp else 7.dp)
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(badgeBorderColor)
                    .padding(1.5.dp)
                    .clip(CircleShape)
                    .background(StatusWarning),
            )
        }
    }
}

/**
 * COUI 侧边栏浮层胶囊/圆钮：在真实 Display 与背景源就绪时接入 COUI 流体材质与光效，
 * 其余环境平滑降级为连续圆角实色浮层，保持相同的按压回弹与轮廓对比度。
 */
@Composable
private fun CouiPaneFloatingSurface(
    modifier: Modifier,
    shape: Shape,
    capsule: Boolean,
    couiBackdrop: CouiBackdrop?,
    focused: Boolean,
    accentColor: Color,
    contentDescription: String? = null,
    appearProgress: Float = 1f,
    onClick: (() -> Unit)?,
    content: @Composable () -> Unit,
) {
    val dark = CouiTheme.isDark
    val view = LocalView.current
    val renderer = remember {
        runCatching { CouiFloatingRenderer.standalone() }.getOrNull()
    }
    if (couiBackdrop != null &&
        renderer != null &&
        view.display != null
    ) {
        ProvideCouiBackdrop(couiBackdrop) {
            CouiRenderedFloatingSurface(
                modifier = modifier,
                shape = shape,
                capsule = capsule,
                backdrop = couiBackdrop,
                renderer = renderer,
                dark = dark,
                focused = focused,
                accentColor = accentColor,
                contentDescription = contentDescription,
                appearProgress = appearProgress,
                onClick = onClick,
                content = content,
            )
        }
    } else {
        val fallbackBg = if (dark) Color(0xFF252830) else Color.White
        val borderColor = when {
            focused -> accentColor.copy(alpha = 0.65f)
            dark -> Color(0x1EFFFFFF)
            else -> Color(0x12000000)
        }
        val interactionSource = remember { MutableInteractionSource() }
        val fallbackAppear = appearProgress.coerceIn(0f, 1f)
        val fallbackScale = 0.76f + 0.24f * fallbackAppear
        Box(
            modifier = modifier
                .graphicsLayer {
                    if (fallbackAppear < 1f) {
                        alpha = fallbackAppear
                        scaleX = fallbackScale
                        scaleY = fallbackScale
                    }
                }
                .dropShadow(
                    shape = shape,
                    shadow = Shadow(
                        radius = 12.dp,
                        color = Color.Black,
                        alpha = if (dark) 0.24f else 0.06f,
                    ),
                )
                .clip(shape)
                .background(fallbackBg, shape)
                .border(if (focused) 1.dp else 0.5.dp, borderColor, shape)
                .then(
                    if (onClick != null) {
                        Modifier
                            .couiButtonFeedback(dark, interactionSource, enabled = true)
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                                role = Role.Button,
                                onClick = onClick,
                            )
                    } else {
                        Modifier
                    },
                )
                .then(
                    if (contentDescription != null) {
                        Modifier.semantics { this.contentDescription = contentDescription }
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

@Composable
private fun CouiRenderedFloatingSurface(
    modifier: Modifier,
    shape: Shape,
    capsule: Boolean,
    backdrop: CouiBackdrop,
    renderer: CouiFloatingRenderer,
    dark: Boolean,
    focused: Boolean,
    accentColor: Color,
    contentDescription: String?,
    appearProgress: Float = 1f,
    onClick: (() -> Unit)?,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current.density
    // 出没动画：0=完全收起，1=在位。全部通过 CouiFloatingSurface 的 alpha / scaleX / scaleY
    // 表达——COUI 的 u_alpha 与 shadowRenderer 各自带 alpha，阴影的半影不会被按主体尺寸
    // 离屏裁掉；放大则复用按压缩放那条路径（mapRect + ball 的 1/scale）。
    val appear = appearProgress.coerceIn(0f, 1f)
    val appearScale = 0.76f + 0.24f * appear
    val contour = LocalCouiContourGlowEnabled.current
    // 严格对齐 COUI-Kit 规范（effect/CouiFloatingSurfaces + topappbar/TopAppBarSearchPair
    // + search/SearchBar）：每个浮层都用 systemMaterial(dark, capsule = true)，
    // 再 copy 三项官方参数——blurRadius（SearchBar 取 20f）、extension（统一 4dp 外扩）、
    // themeTint（CouiTheme.materialTintFor(dark)，主题着色）。材质按节点矩形铺，不裁剪。
    // 渲染器仍是 standalone（pair 需要两个面），只是材质参数与官方一致。
    val materialTint = CouiTheme.materialTintFor(dark)
    val materialExtension = with(LocalDensity.current) { 4.dp.roundToPx() }
    val material = remember(dark, materialTint, materialExtension) {
        CouiTopAppBarButtonDefaults
            .systemMaterial(dark = dark, capsule = true)
            .copy(
                blurRadius = 20f,
                extension = materialExtension,
                themeTint = materialTint,
            )
    }
    val shadow = remember(dark, density, contour) {
        CouiTopAppBarButtonDefaults.shadow(
            dark = dark,
            capsule = true,
            density = density,
            contour = contour,
            search = true,
            alphaScale = 1f,
        )
    }
    var boundsSize by remember { mutableStateOf(IntSize.Zero) }
    val contentLayer = rememberGraphicsLayer()
    val interactive = onClick != null
    val motion = rememberCouiTouchMotion(interactive)
    val press = rememberCouiPressMotion()
    val interactionSource = remember { MutableInteractionSource() }
    val (lightInput, drawLight) = rememberCouiSpotlight(
        interactive = interactive,
        preset = if (capsule) SpotlightPreset.OpaqueMedium2 else SpotlightPreset.OpaqueSmall2,
        dark = dark,
    )

    Box(
        modifier = modifier
            .onSizeChanged { boundsSize = it }
            .drawWithCache {
                val lightClip = Path().apply {
                    addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache))
                }
                onDrawWithContent {
                    drawContent()
                    if (drawLight != null) {
                        withTransform({
                            translate(motion.translationX.value, motion.translationY.value)
                            scale(
                                motion.scaleX.value + press.scale.value - 1f,
                                motion.scaleY.value + press.scale.value - 1f,
                                center,
                            )
                        }) {
                            clipPath(lightClip) { drawLight.invoke(this) }
                        }
                    }
                }
            }
            .couiFloatingSurface(
                backdrop = backdrop,
                renderer = renderer,
                material = material,
                shadow = shadow,
                shape = shape,
                contentLayer = contentLayer,
                contourGlowEnabled = contour,
                foregroundComposition = true,
            ) {
                CouiFloatingSurface(
                    bounds = Rect(
                        Offset(motion.translationX.value, motion.translationY.value),
                        Size(boundsSize.width.toFloat(), boundsSize.height.toFloat()),
                    ),
                    scaleX = (motion.scaleX.value + press.scale.value - 1f) * appearScale,
                    scaleY = (motion.scaleY.value + press.scale.value - 1f) * appearScale,
                    cornerWeight = if (capsule) 1f else 2f,
                    alpha = appear,
                    visible = appear > 0.001f,
                )
            }
            .then(
                if (focused) {
                    Modifier.border(1.dp, accentColor.copy(alpha = 0.55f), shape)
                } else {
                    Modifier
                },
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = motion.translationX.value
                    translationY = motion.translationY.value
                    scaleX = motion.scaleX.value + press.scale.value - 1f
                    scaleY = motion.scaleY.value + press.scale.value - 1f
                }
                .then(
                    if (interactive) {
                        Modifier
                            .couiTouchMotion(motion, true)
                            .couiPressMotion(press, enabled = true)
                            .then(lightInput)
                    } else {
                        Modifier
                    },
                )
                .drawWithContent {
                    contentLayer.record { this@drawWithContent.drawContent() }
                }
                .clip(shape)
                .then(
                    if (onClick != null) {
                        Modifier
                            .couiToolbarFeedback(dark, interactionSource, enabled = true)
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                                role = Role.Button,
                                onClick = onClick,
                            )
                    } else {
                        Modifier
                    },
                )
                .then(
                    if (contentDescription != null) {
                        Modifier.semantics { this.contentDescription = contentDescription }
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

@Composable
private fun PaneClearGlyph(
    modifier: Modifier,
    color: Color,
) {
    Canvas(modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
        val unit = size.width / 22f
        drawCircle(color, 9f * unit)
        drawLine(
            Color.Transparent,
            Offset(8.03f, 8.03f) * unit,
            Offset(13.97f, 13.97f) * unit,
            1.65f * unit,
            StrokeCap.Round,
            blendMode = BlendMode.Clear,
        )
        drawLine(
            Color.Transparent,
            Offset(13.97f, 8.03f) * unit,
            Offset(8.03f, 13.97f) * unit,
            1.65f * unit,
            StrokeCap.Round,
            blendMode = BlendMode.Clear,
        )
    }
}

/*
 * 底部 dock 的渐变模糊：完全照抄侧栏顶栏 TopAppBarSurface(SDK 33+) 那套
 * `drawCouiGradientBackdrop` 的实现——同样的 AGSL 渐变高斯、两趟分离卷积
 * （先水平、后垂直）、同样的 GraphicsLayer 录制 + padding 扩边，
 * 仅仅把渐变方向反过来：
 *   顶栏：gradientTop = 内容顶边，ratio 由顶到底衰减 → 顶边最模糊、往下渐清晰；
 *   这里：gradientTop = 内容底边，gradientHeight 取负 → 底边最模糊、往上渐清晰。
 */
private const val CouiGradientSigmaScale = 0.57735f

/**
 * 侧栏底部这层渐变模糊会留下一条肉眼可见的雾块边界：保留模糊时是雾块边，
 * 去掉模糊只留颜色遮罩时仍有一条色带边，两种形态都实测过。按用户选择整层关掉（方案 A）。
 */
private const val DockBlurDebugEnabled = false

internal fun couiGradientGaussianShader(kernelRadius: Int): String = """
    const int in_blurRadius = $kernelRadius;
    const float PI = 3.1415926;
    uniform shader child;
    uniform float2 inputSize;
    uniform float gradientTop;
    uniform float gradientHeight;
    uniform half2 in_blurOffset;

    float4 sampleChild(float2 coord) {
        return child.eval(clamp(coord, float2(0.5), inputSize - 0.5));
    }

    float getWeight(int i, int radius) {
        float sigma = float(radius) / 3.0;
        float value = (1.0 / sqrt(2.0 * PI * sigma * sigma))
            * exp(float(-i * i) / (2.0 * sigma * sigma + 0.00000001));
        return clamp(value, 0.0, 1.0);
    }

    float interleavedGradientNoise(float2 coord) {
        const float3 magicNumbers = float3(0.06711056, 0.00583715, 52.9829189);
        return fract(magicNumbers.z * fract(dot(coord, magicNumbers.xy)));
    }

    half4 main(float2 coord) {
        float4 c = sampleChild(coord);
        float position = clamp((coord.y - gradientTop) / gradientHeight, 0.0, 1.0);
        float ratio = smoothstep(0.0, 1.0, 1.0 - position);
        int radius = int(float(in_blurRadius) * ratio);
        if (radius <= 1) return half4(c);
        if (c.a == 0.0) return half4(0.0);
        float2 offset = float2(0.0);
        float sumWeight = 0.0;
        float4 finalColor = float4(0.0);
        for (int i = 1; i <= in_blurRadius; i++) {
            if (i > radius) break;
            float weight = getWeight(i, radius);
            offset.x = min(in_blurOffset.x, float(radius)) * float(i);
            offset.y = min(in_blurOffset.y, float(radius)) * float(i);
            finalColor += sampleChild(coord - offset) * weight;
            finalColor += sampleChild(coord + offset) * weight;
            sumWeight += 2.0 * weight;
        }
        finalColor += c * (1.0 - sumWeight);
        if (finalColor.a <= 0.0) return half4(0.0);
        finalColor = float4(finalColor.rgb * (c.a / finalColor.a), c.a);
        float noise = interleavedGradientNoise(coord + 0.5) - 0.5;
        finalColor.rgb = clamp(finalColor.rgb + noise * c.a / 200.0, float3(0.0), float3(c.a));
        return half4(finalColor);
    }
""".trimIndent()

@Composable
private fun CouiBottomGradientBlurLayer(
    backdrop: CouiBackdrop,
    modifier: Modifier = Modifier,
    recordBackdrop: CouiBackdrop? = null,
    fadeHeight: Dp = 96.dp,
    blurRadius: Dp = 10.8.dp,
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val fadePx = with(density) { fadeHeight.toPx() }
    val blurPx = with(density) { blurRadius.toPx() }
    val kernelRadius = kotlin.math.ceil(((blurPx * CouiGradientSigmaScale) + 0.5f) * 1.5f)
        .toInt()
        .coerceIn(2, 48)
    val padding = kernelRadius * 2f + 1f
    val horizontal = remember(kernelRadius) {
        RuntimeShader(couiGradientGaussianShader(kernelRadius)).apply {
            setFloatUniform("in_blurOffset", 2f, 0f)
        }
    }
    val vertical = remember(kernelRadius) {
        RuntimeShader(couiGradientGaussianShader(kernelRadius)).apply {
            setFloatUniform("in_blurOffset", 0f, 2f)
        }
    }
    val passHorizontal = rememberGraphicsLayer()
    val passVertical = rememberGraphicsLayer()
    var coordinates by remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }

    Box(
        modifier = modifier
            .then(
                if (recordBackdrop != null) Modifier.couiBackdrop(recordBackdrop) else Modifier,
            )
            .onGloballyPositioned { coordinates = it }
            .drawBehind {
                val coords = coordinates?.takeIf { it.isAttached } ?: return@drawBehind
                if (size.width <= 2f || size.height <= 2f) return@drawBehind
                val layerSize = IntSize(
                    (size.width + padding * 2f).toInt().coerceAtLeast(1),
                    (size.height + padding * 2f).toInt().coerceAtLeast(1),
                )
                val layerWidth = layerSize.width.toFloat()
                val layerHeight = layerSize.height.toFloat()
                val fade = fadePx.coerceIn(1f, size.height)
                listOf(horizontal, vertical).forEach { shader ->
                    shader.setFloatUniform("inputSize", layerWidth, layerHeight)
                    // 渐变贴着内容区：底边在 layer 坐标 padding + height 处。
                    shader.setFloatUniform("gradientTop", layerHeight - padding)
                    shader.setFloatUniform("gradientHeight", -fade)
                }
                passHorizontal.compositingStrategy = androidx.compose.ui.graphics.layer.CompositingStrategy.Offscreen
                passHorizontal.clip = true
                passHorizontal.renderEffect = AndroidRenderEffect
                    .createRuntimeShaderEffect(horizontal, "child")
                    .asComposeRenderEffect()
                passHorizontal.record(density, layoutDirection, layerSize) {
                    translate(padding, padding) {
                        backdrop.draw(this, coords)
                    }
                }
                passVertical.compositingStrategy = androidx.compose.ui.graphics.layer.CompositingStrategy.Offscreen
                passVertical.clip = true
                passVertical.renderEffect = AndroidRenderEffect
                    .createRuntimeShaderEffect(vertical, "child")
                    .asComposeRenderEffect()
                passVertical.record(density, layoutDirection, layerSize) {
                    drawLayer(passHorizontal)
                }
                translate(-padding, -padding) {
                    drawLayer(passVertical)
                }
            },
    )
}
