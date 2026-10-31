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
 * 状态栏下方的执行胶囊，只服务后台虚拟屏：
 *
 * - 收起态：一枚虚拟屏微缩镜，外套一圈同圆角的细描边；点一下进入展开态。
 * - 展开态：画廊装裱视窗 + 「接管 / 全屏 / 收起」三枚按钮。
 *
 * 布局约束（实机反馈过的问题都源于此）：
 * 1. 窗口横向固定为装裱视窗宽度、左上角定位：横向永不改变，顶边固定，
 *    两态切换时画面上不存在任何位移，只有透明度变化。
 * 2. 展开面板与微缩镜放在同一个 Box 里叠放（不是 Column 相加），
 *    交叉淡入淡出时高度取两者较大值，不会被窗口裁掉或挤动。
 * 3. 全部动画只有透明度，没有位移、缩放、宽高动画。
 */
@Composable
internal fun AgentOverlayCapsule(
    state: AgentOverlayState,
    expanded: Boolean,
    yielded: Boolean,
    onToggleExpanded: () -> Unit,
    onCollapsedSettled: () -> Unit,
    onPause: () -> Unit = {},
    onResume: () -> Unit = {},
    onStop: () -> Unit = {},
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
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    // 为 Agent 手势让路：离开时快速上移收小淡出，回来时用弹簧落回，只改绘制层不触发重排。
    val clearance by animateFloatAsState(
        targetValue = if (yielded) 1f else 0f,
        animationSpec = if (yielded) {
            tween(durationMillis = 180, easing = FastOutLinearInEasing)
        } else {
            spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
        },
        label = "capsule_clearance",
    )
    val accent by animateColorAsState(overlayPhaseAccent(state.phase), tween(240), label = "capsule_accent")
    val expandDescription = stringResource(R.string.overlay_capsule_toggle, state.status.localizedText())
    val lensShape = RoundedCornerShape(CapsuleLensRingCorner)
    val stageShape = RoundedCornerShape(StageOuterCorner)

    AnimatedVisibility(
        visible = visible && capsuleVisible && virtualDisplay.active &&
            !virtualDisplay.materializingToMain && !virtualDisplay.externalizedToMain,
        enter = fadeIn(tween(CapsuleFadeMs)),
        exit = fadeOut(tween(CapsuleFadeMs)),
    ) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            // ── 展开面板：点开时从透明变实，收起时淡出 ──────────────────────
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(tween(CapsuleFadeMs)),
                exit = fadeOut(tween(CapsuleFadeMs)),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp, bottom = 6.dp)
                        .graphicsLayer { applyClearance(clearance) }
                        .shadow(10.dp, stageShape, ambientColor = accent, spotColor = accent)
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

            // ── 微缩镜：点开时先变透明，收起时重新淡入 ──────────────────────
            AnimatedVisibility(
                visible = !expanded,
                enter = fadeIn(tween(CapsuleFadeMs)),
                exit = fadeOut(tween(CapsuleFadeMs)),
            ) {
                Box(
                    modifier = Modifier
                        .padding(vertical = 6.dp)
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
