package io.github.mangi.eta.agent.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.agent.virtualdisplay.VirtualDisplaySnapshot
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 执行胶囊拆成两个互不重叠的系统窗口：
 *
 * - [AgentOverlayCapsuleLens]：收起态的微缩镜，独立窗口，宽度恒为 46.dp。
 * - [AgentOverlayCapsulePanel]：展开态的画廊面板，独立窗口，宽度恒为装裱视窗宽度。
 *
 * 为什么必须拆两个窗口：
 * 窗口矩形内的透明区域同样会吃掉点击，所以收起态窗口宽必须小到只剩微缩镜，
 * 展开态又必须宽到装得下面板 —— 单窗口只能来回改宽度，而宽度一变，
 * Compose 会在新约束下重新测量内容，画面看起来就像「左右移动」。
 * 拆成两个宽度都固定的窗口后，每次开关只切换透明度与触摸标志位，几何完全不动。
 */
@Composable
internal fun AgentOverlayCapsuleLens(
    state: AgentOverlayState,
    expanded: Boolean,
    yielded: Boolean,
    onToggleExpanded: () -> Unit,
    virtualDisplay: VirtualDisplaySnapshot,
    capsuleVisible: Boolean,
) {
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { revealed = true }

    // 为 Agent 手势让路：只改绘制层，不触发重排。
    val clearance by animateFloatAsState(
        targetValue = if (yielded) 1f else 0f,
        animationSpec = if (yielded) {
            tween(durationMillis = 180, easing = FastOutLinearInEasing)
        } else {
            spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
        },
        label = "capsule_lens_clearance",
    )
    val accent by animateColorAsState(overlayPhaseAccent(state.phase), tween(240), label = "capsule_lens_accent")
    val expandDescription = stringResource(R.string.overlay_capsule_toggle, state.status.localizedText())
    val lensShape = RoundedCornerShape(CapsuleLensRingCorner)

    AnimatedVisibility(
        visible = revealed && capsuleVisible && virtualDisplay.active &&
            !virtualDisplay.materializingToMain && !virtualDisplay.externalizedToMain && !expanded,
        enter = fadeIn(tween(CapsuleFadeMs)),
        exit = fadeOut(tween(CapsuleFadeMs)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(
                modifier = Modifier
                    .width(CapsuleLensOuterWidth)
                    .graphicsLayer { applyClearance(clearance) }
                    .shadow(6.dp, lensShape, ambientColor = accent, spotColor = accent)
                    .clip(lensShape)
                    .background(MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.95f))
                    .border(
                        width = 0.85.dp,
                        color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.14f),
                        shape = lensShape,
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onToggleExpanded,
                    )
                    .semantics { contentDescription = expandDescription }
                    .padding(CapsuleLensRingPadding),
                contentAlignment = Alignment.Center,
            ) {
                VirtualDisplayMicroLens(
                    snapshot = virtualDisplay,
                    accent = accent,
                )
            }
        }
    }
}

/** 展开态画布 + 「接管 / 全屏 / 收起」三枚按钮；窗口宽度恒定，开关只靠透明度。 */
@Composable
internal fun AgentOverlayCapsulePanel(
    state: AgentOverlayState,
    expanded: Boolean,
    yielded: Boolean,
    onToggleExpanded: () -> Unit,
    onSupplementModeChange: (Boolean) -> Unit = {},
    onSupplement: (String) -> Unit = {},
    virtualDisplay: VirtualDisplaySnapshot = VirtualDisplaySnapshot.Inactive,
    capsuleVisible: Boolean = true,
    onTakeOverVirtualDisplay: () -> Unit = {},
    onResumeVirtualDisplay: () -> Unit = {},
    onFullscreenVirtualDisplay: () -> Unit = {},
    onTapVirtualCanvas: (Float, Float, Boolean) -> Unit = { _, _, _ -> },
    onSwipeVirtualCanvas: (Float, Float, Float, Float, Int) -> Unit = { _, _, _, _, _ -> },
) {
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { revealed = true }
    val clearance by animateFloatAsState(
        targetValue = if (yielded) 1f else 0f,
        animationSpec = if (yielded) {
            tween(durationMillis = 180, easing = FastOutLinearInEasing)
        } else {
            spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
        },
        label = "capsule_panel_clearance",
    )
    val accent by animateColorAsState(overlayPhaseAccent(state.phase), tween(240), label = "capsule_panel_accent")
    val stageShape = RoundedCornerShape(StageOuterCorner)

    AnimatedVisibility(
        visible = revealed && capsuleVisible && virtualDisplay.active &&
            !virtualDisplay.materializingToMain && !virtualDisplay.externalizedToMain && expanded,
        enter = fadeIn(tween(CapsuleFadeMs)),
        exit = fadeOut(tween(CapsuleFadeMs)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, bottom = 6.dp)
                .graphicsLayer { applyClearance(clearance) }
                .clip(stageShape)
                .background(MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.95f))
                .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
        ) {
            VirtualDisplayStageSection(
                snapshot = virtualDisplay,
                accent = accent,
                isPaused = state.phase == AgentOverlayPhase.PAUSED,
                onTakeOver = onTakeOverVirtualDisplay,
                onResumeAuto = onResumeVirtualDisplay,
                onFullscreen = onFullscreenVirtualDisplay,
                onCollapse = onToggleExpanded,
                onTapCanvas = onTapVirtualCanvas,
                onSwipeCanvas = onSwipeVirtualCanvas,
            )
        }
    }
}

/** 让路时的绘制层变换：只动绘制、不动测量，回到原位时不会触发重排。 */
private fun androidx.compose.ui.graphics.GraphicsLayerScope.applyClearance(clearance: Float) {
    val shrink = 1f - 0.08f * clearance
    scaleX = shrink
    scaleY = shrink
    translationY = -12.dp.toPx() * clearance
    alpha = 1f - clearance
    transformOrigin = TransformOrigin(0.5f, 0f)
}

/** 淡入淡出时长；两个方向一致，避免一快一慢看起来像位移。 */
private const val CapsuleFadeMs = 160

/** 微缩镜外圈留白：圆角 = 微缩镜自身 6.dp + 圈距，间距与微缩镜内边一致。 */
private val CapsuleLensRingPadding = 4.dp
private val CapsuleLensRingCorner = 10.dp

/** 收起态胶囊宽度：18.dp 微缩镜 + 两侧各 4.dp 圈距。 */
private val CapsuleLensOuterWidth = 26.dp
private val StageOuterCorner = 28.dp
