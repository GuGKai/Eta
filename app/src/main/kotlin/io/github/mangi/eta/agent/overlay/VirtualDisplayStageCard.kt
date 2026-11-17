package io.github.mangi.eta.agent.overlay

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import io.github.mangi.eta.R
import io.github.mangi.eta.agent.virtualdisplay.VirtualDisplaySnapshot
import io.github.mangi.eta.agent.virtualdisplay.VirtualTouchEffect
import kotlin.math.hypot
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 镶嵌在收起态顶部胶囊右侧的 18×32.dp 实时微缩取景器（Micro-Lens）。
 *
 * 平时安静展示虚拟屏缩略画面；当虚拟屏内发生点击或滑动时，外圈泛起柔和的呼吸微光。
 */
@Composable
internal fun VirtualDisplayMicroLens(
    snapshot: VirtualDisplaySnapshot,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val pulse = remember { Animatable(0f) }
    LaunchedEffect(snapshot.touchEffect?.sequence) {
        if (snapshot.touchEffect != null) {
            pulse.snapTo(1f)
            pulse.animateTo(0f, tween(durationMillis = 420, easing = FastOutSlowInEasing))
        }
    }

    val borderColor = androidx.compose.ui.graphics.lerp(
        MiuixTheme.colorScheme.onSurface.copy(alpha = 0.14f),
        accent.copy(alpha = 0.78f),
        pulse.value,
    )
    val lensShape = RoundedCornerShape(6.dp)

    Box(
        modifier = modifier
            .size(width = 18.dp, height = 32.dp)
            .graphicsLayer {
                val scale = 1f + 0.06f * pulse.value
                scaleX = scale
                scaleY = scale
            }
            .clip(lensShape)
            .background(MiuixTheme.colorScheme.surfaceContainerHigh)
            .border(width = 0.85.dp, color = borderColor, shape = lensShape),
        contentAlignment = Alignment.Center,
    ) {
        val frame = snapshot.frame
        if (frame != null) {
            Image(
                bitmap = frame,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accent.copy(alpha = 0.55f)),
            )
        }
        val effect = snapshot.touchEffect
        if (effect != null && pulse.value > 0.02f) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val (nx, ny) = when (effect) {
                    is VirtualTouchEffect.Tap -> effect.normalizedX to effect.normalizedY
                    is VirtualTouchEffect.Swipe -> effect.endX to effect.endY
                }
                val center = Offset(nx * size.width, ny * size.height)
                drawCircle(
                    color = accent.copy(alpha = 0.85f * pulse.value),
                    radius = 2.2.dp.toPx(),
                    center = center,
                )
            }
        }
    }
}

/**
 * 画廊装裱式虚拟屏视窗（Gallery Matte Stage）。
 *
 * 采用外框 28.dp + 10.dp 留白衬底 + 内画框 18.dp 的同心圆角装裱设计，
 * 支持用户手指直接在画布上点按/滑动接管，以及一键全屏投射至物理主屏。
 */
@Composable
internal fun VirtualDisplayStageSection(
    snapshot: VirtualDisplaySnapshot,
    accent: Color,
    isPaused: Boolean,
    onTakeOver: () -> Unit,
    onResumeAuto: () -> Unit,
    onFullscreen: () -> Unit,
    onCollapse: () -> Unit,
    onTapCanvas: (Float, Float, Boolean) -> Unit,
    onSwipeCanvas: (Float, Float, Float, Float, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val frameShape = RoundedCornerShape(StageCanvasCorner)
    val borderColor = if (snapshot.userControlling || isPaused) {
        accent.copy(alpha = 0.42f)
    } else {
        MiuixTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = StageMattePadding, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // ── 画廊留白内衬画布（Concentric 18.dp Corner） ────────────────────
        Box(
            modifier = Modifier
                .height(StageCanvasHeight)
                .aspectRatio(snapshot.aspectRatio, matchHeightConstraintsFirst = true)
                .clip(frameShape)
                .background(MiuixTheme.colorScheme.surfaceContainerHigh)
                .border(width = 0.75.dp, color = borderColor, shape = frameShape)
                .pointerInput(snapshot.displayId, snapshot.userControlling, isPaused) {
                    val touchSlopPx = viewConfiguration.touchSlop
                    val longPressTimeoutMs = viewConfiguration.longPressTimeoutMillis
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        val startPos = down.position
                        val downAt = SystemClock.uptimeMillis()
                        var lastPos = startPos
                        var dragged = false

                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: event.changes.firstOrNull()
                            if (change != null) {
                                lastPos = change.position
                                if (hypot(lastPos.x - startPos.x, lastPos.y - startPos.y) > touchSlopPx) {
                                    dragged = true
                                }
                                change.consume()
                            }
                        } while (event.changes.any { it.pressed })

                        val w = size.width.toFloat().coerceAtLeast(1f)
                        val h = size.height.toFloat().coerceAtLeast(1f)
                        if (!snapshot.userControlling && !isPaused) {
                            onTakeOver()
                        }
                        val elapsed = (SystemClock.uptimeMillis() - downAt).toInt()
                        if (dragged) {
                            onSwipeCanvas(
                                (startPos.x / w).coerceIn(0f, 1f),
                                (startPos.y / h).coerceIn(0f, 1f),
                                (lastPos.x / w).coerceIn(0f, 1f),
                                (lastPos.y / h).coerceIn(0f, 1f),
                                elapsed.coerceIn(180, 900),
                            )
                        } else {
                            onTapCanvas(
                                (lastPos.x / w).coerceIn(0f, 1f),
                                (lastPos.y / h).coerceIn(0f, 1f),
                                elapsed >= longPressTimeoutMs,
                            )
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            val frame = snapshot.frame
            if (frame != null) {
                Image(
                    bitmap = frame,
                    contentDescription = snapshot.appName.ifBlank {
                        stringResource(R.string.overlay_virtual_canvas_auto)
                    },
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text = snapshot.appName.ifBlank {
                        stringResource(R.string.overlay_virtual_canvas_auto)
                    },
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                )
            }

            VirtualTouchEffectOverlay(
                effect = snapshot.touchEffect,
                accent = accent,
            )
        }

        Spacer(Modifier.height(10.dp))

        // ── 接管栏：接管/继续、全屏、收起 三枚胶囊按钮，等分画布宽度 ──────────
        // 用 weight 等分而不是各自按文字撑开，避免文字被挤到只剩一个字。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(PillGap, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StagePillButton(
                modifier = Modifier.weight(1f),
                label = stringResource(
                    if (snapshot.userControlling || isPaused) {
                        R.string.overlay_virtual_continue
                    } else {
                        R.string.overlay_virtual_takeover
                    },
                ),
                highlighted = snapshot.userControlling || isPaused,
                accent = accent,
                onClick = {
                    if (snapshot.userControlling || isPaused) onResumeAuto() else onTakeOver()
                },
            )
            StagePillButton(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.overlay_virtual_fullscreen),
                highlighted = false,
                accent = accent,
                onClick = onFullscreen,
            )
            StagePillButton(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.overlay_virtual_collapse),
                highlighted = false,
                accent = accent,
                onClick = onCollapse,
            )
        }
    }
}

@Composable
private fun StagePillButton(
    label: String,
    highlighted: Boolean,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = if (highlighted) {
        accent.copy(alpha = 0.16f)
    } else {
        MiuixTheme.colorScheme.surfaceContainerHigh
    }
    val textColor = if (highlighted) {
        accent
    } else {
        MiuixTheme.colorScheme.onSurface
    }
    Box(
        modifier = modifier
            .height(27.dp)
            .clip(RoundedCornerShape(13.5.dp))
            .background(bg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Visible,
        )
    }
}

/**
 * 画布内生微距触控光效：点击呈现双层阻尼水波纹，滑动呈现流光尾迹。
 */
@Composable
private fun VirtualTouchEffectOverlay(
    effect: VirtualTouchEffect?,
    accent: Color,
) {
    if (effect == null) return
    val progress = remember(effect.sequence) { Animatable(0f) }
    LaunchedEffect(effect.sequence) {
        val duration = when (effect) {
            is VirtualTouchEffect.Tap -> if (effect.longPress) 540 else 380
            is VirtualTouchEffect.Swipe -> effect.durationMs.coerceIn(240, 900)
        }
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = duration,
                easing = if (effect is VirtualTouchEffect.Tap) FastOutSlowInEasing else LinearEasing,
            ),
        )
    }

    val p = progress.value
    if (p >= 0.99f) return

    Canvas(modifier = Modifier.fillMaxSize()) {
        when (effect) {
            is VirtualTouchEffect.Tap -> {
                val cx = effect.normalizedX * size.width
                val cy = effect.normalizedY * size.height
                val center = Offset(cx, cy)
                val maxRadius = (if (effect.longPress) 20.dp else 15.dp).toPx()
                val minRadius = 3.5.dp.toPx()
                val radius = lerp(minRadius, maxRadius, p)
                val fade = (1f - p).coerceIn(0f, 1f)

                drawCircle(
                    color = accent.copy(alpha = 0.18f * fade),
                    radius = radius,
                    center = center,
                )
                drawCircle(
                    color = accent.copy(alpha = 0.88f * fade),
                    radius = radius,
                    center = center,
                    style = Stroke(width = 1.6.dp.toPx()),
                )
                drawCircle(
                    color = accent.copy(alpha = 0.92f * (1f - p * 0.7f)),
                    radius = 2.8.dp.toPx(),
                    center = center,
                )
            }

            is VirtualTouchEffect.Swipe -> {
                val sx = effect.startX * size.width
                val sy = effect.startY * size.height
                val ex = effect.endX * size.width
                val ey = effect.endY * size.height
                val curX = lerp(sx, ex, p)
                val curY = lerp(sy, ey, p)
                val fade = if (p < 0.75f) 1f else (1f - (p - 0.75f) / 0.25f).coerceIn(0f, 1f)

                drawLine(
                    color = accent.copy(alpha = 0.55f * fade),
                    start = Offset(sx, sy),
                    end = Offset(curX, curY),
                    strokeWidth = 2.2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                drawCircle(
                    color = accent.copy(alpha = 0.9f * fade),
                    radius = 4.2.dp.toPx(),
                    center = Offset(curX, curY),
                )
                drawCircle(
                    color = accent.copy(alpha = 0.36f * fade),
                    radius = 8.5.dp.toPx(),
                    center = Offset(curX, curY),
                    style = Stroke(width = 1.2.dp.toPx()),
                )
            }
        }
    }
}

/**
 * 破壁移交全屏放大过渡层（Seamless Materialize Overlay）。
 *
 * 不变量只有一条：**屏幕上任何一帧都有画面盖着**。所以
 * - 起手几何取画板在屏幕上的真实矩形（VirtualDisplaySnapshot.materializeFrom），
 *   而不是写死一个比例 —— 画板消失与位图出现是同一帧，两帧完全重合才不会「跳一下」；
 * - 放大全程 alpha 保持不透明，任务迁移在整屏被盖住之后才发生，虚拟屏变空的空画面被挡住；
 * - 淡出改由宿主（VirtualDisplayController.handoffToMainDisplay）在探明主屏画出首帧后才触发，
 *   这里只做一段渐变收尾，露出来时底下已经是活的窗口。
 */
@Composable
internal fun VirtualDisplayMaterializeOverlay(
    snapshot: VirtualDisplaySnapshot,
    corners: ScreenCornerRadii,
) {
    val frame = snapshot.frame ?: return
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }

    val expandProgress by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.9f,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "vd_materialize_expand",
    )
    val reveal by animateFloatAsState(
        targetValue = if (snapshot.materializeFading) 0f else 1f,
        animationSpec = tween(
            durationMillis = MaterializeFadeOutMs,
            easing = FastOutSlowInEasing,
        ),
        label = "vd_materialize_reveal",
    )

    val density = LocalDensity.current
    val screenCornerDp = with(density) { corners.radius.toDp() }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        val screenWidthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val screenHeightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val from = snapshot.materializeFrom
        val fromWidthPx = from?.width?.takeIf { it > 1f } ?: (screenWidthPx * FallbackMaterializeScale)
        val fromHeightPx = from?.height?.takeIf { it > 1f } ?: (screenHeightPx * FallbackMaterializeScale)
        val startScaleX = (fromWidthPx / screenWidthPx).coerceIn(0.05f, 1f)
        val startScaleY = (fromHeightPx / screenHeightPx).coerceIn(0.05f, 1f)
        // 位图原始位置就是整屏（中心对齐屏幕中心），平移量从「画板中心 − 屏幕中心」收敛到 0。
        val translateX = ((from?.centerX ?: (screenWidthPx / 2f)) - screenWidthPx / 2f) * (1f - expandProgress)
        val translateY = ((from?.centerY ?: (screenHeightPx / 2f)) - screenHeightPx / 2f) * (1f - expandProgress)
        val scaleX = lerp(startScaleX, 1f, expandProgress)
        val scaleY = lerp(startScaleY, 1f, expandProgress)
        // 裁剪在内层一起被缩放：起手先按缩放比放大，落到屏幕上正好是画布原本的 18.dp 圆角。
        val startCornerInnerDp = StageCanvasCorner / ((startScaleX + startScaleY) / 2f)
        val cornerDp = lerp(startCornerInnerDp, screenCornerDp, expandProgress)

        Image(
            bitmap = frame,
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    this.scaleX = scaleX
                    this.scaleY = scaleY
                    translationX = translateX
                    translationY = translateY
                    alpha = reveal
                }
                .clip(RoundedCornerShape(cornerDp)),
        )
    }
}

/** 没有画板几何时的兜底起手比例（正常情况下不会走到）。 */
private const val FallbackMaterializeScale = 0.68f

/** 与 VirtualDisplayController.MaterializeFadeOutMs 保持一致。 */
private const val MaterializeFadeOutMs = 220

private val StageMattePadding = 10.dp
/** 三枚胶囊按钮之间的间距。 */
private val PillGap = 4.dp
private val StageCanvasCorner = 18.dp
internal val StageCanvasHeight = 336.dp
internal val StageCapsuleWidth = 296.dp
internal val MicroLensCapsuleWidth = 264.dp
internal val DefaultCapsuleWidth = 248.dp
