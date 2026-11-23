package com.itos.coui.topappbar

import android.graphics.BlendMode
import android.graphics.ColorSpace
import android.graphics.LinearGradient
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.itos.coui.backdrop.CouiBackdrop
import com.itos.coui.backdrop.CouiBackdropEffectScope
import com.itos.coui.backdrop.couiBackdrop
import com.itos.coui.backdrop.drawCouiBackdrop
import com.itos.coui.backdrop.rememberCouiBackdrop
import com.itos.coui.backdrop.LocalCouiBackdrop
import com.itos.coui.effect.CouiCoverageBlurEffect
import com.itos.coui.effect.CouiGradientTint
import com.itos.coui.effect.CouiLightTintCurve
import com.itos.coui.effect.drawComposeGradientBackdrop
import com.itos.coui.effect.drawCouiGradientBackdrop
import com.itos.coui.theme.CouiTheme

internal val LocalCouiToolbarBackdrop = staticCompositionLocalOf<CouiBackdrop?> { null }
internal val LocalCouiToolbarOverlay = staticCompositionLocalOf<SharedTransitionScope?> { null }

internal val LocalUseCouiTopAppBarBlur = compositionLocalOf { false }

private const val FadeStopCount = 65
private const val LightTintTransitionSegments = 8
private val BlurLayers = listOf(
    Triple(2.7.dp, 0.68f, 1f),
    Triple(6.3.dp, 0.44f, 0.82f),
    Triple(CouiTopAppBarDefaults.referenceBlurRadius, 0.28f, 0.68f),
).map { (radius, start, end) ->
    radius to FloatArray(FadeStopCount) { index ->
        start + (end - start) * index / (FadeStopCount - 1f)
    }
}

private val GradientColorSpace = ColorSpace.get(ColorSpace.Named.EXTENDED_SRGB)
// 三层遮罩共用透明度曲线; 颜色和各层节点位置不随栏高变化, 只在初始化时生成。
private val FadeColors = LongArray(FadeStopCount) { index ->
    val progress = index / (FadeStopCount - 1f)
    val smoothProgress = progress * progress * (3f - 2f * progress)
    // 直接打包高精度透明度, 避免先舍入到 8 位。
    android.graphics.Color.pack(0f, 0f, 0f, 1f - smoothProgress, GradientColorSpace)
}

/**
 * 为当前作用域的顶栏设置默认渐进模糊后端。Android 13 及以上传 true 使用 Compose，
 * false 使用 COUI。顶栏显式传入的 useCouiBlur 优先。
 */
@Composable
fun ProvideCouiTopAppBarBlur(useComposeBlur: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalUseCouiTopAppBarBlur provides !useComposeBlur, content = content)
}

/**
 * 背景从标题栏顶部开始并向下延伸，不改变布局或触摸高度。
 * gradientSpeed 为 null 时保留项目固定渐隐；正值选择全高原厂幂曲线。
 * gradientColor 未指定时优先取 Scaffold 的不透明页面底色，其次取当前主题的页面底色。
 * 显式颜色覆盖默认值，其 alpha 与渐变透明度相乘。Color.Transparent 关闭遮罩。
 * topExtension 在测量时读取，背景向上延伸并平移，不改变内容占位或触摸范围。
 * useCouiBlur 在 Android 13 及以上选择 COUI 渐进模糊；默认使用 Compose，可由作用域覆盖。
 */
@Composable
fun TopAppBarSurface(
    modifier: Modifier = Modifier,
    backdrop: CouiBackdrop = checkNotNull(LocalCouiBackdrop.current) {
        "顶栏需要显式 backdrop 或 ProvideCouiBackdrop 作用域"
    },
    fadeHeight: androidx.compose.ui.unit.Dp = CouiTopAppBarDefaults.fadeHeight,
    referenceBlurRadius: androidx.compose.ui.unit.Dp = CouiTopAppBarDefaults.referenceBlurRadius,
    bottomExtension: androidx.compose.ui.unit.Dp = CouiTopAppBarDefaults.bottomExtension,
    backgroundAlpha: Float = 1f,
    gradientSpeed: Float? = null,
    gradientColor: Color = Color.Unspecified,
    dark: Boolean = CouiTheme.isDark,
    topExtension: () -> androidx.compose.ui.unit.Dp = { 0.dp },
    useCouiBlur: Boolean = LocalUseCouiTopAppBarBlur.current,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current.density
    val destination = rememberCouiBackdrop(parent = backdrop)
    // 按钮采样复用顶栏背景的绘制指令。
    SideEffect { destination.setIsolateWhenIdle(false) }
    val resolvedGradientColor = gradientColor.takeOrElse {
        CouiTopAppBarDefaults.defaultBackgroundColor(CouiTheme.colorSchemeFor(dark).background)
    }.takeOrElse { Color.Transparent }
    val opacity = backgroundAlpha.coerceIn(0f, 1f)
    val backgroundModifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val tint = CouiGradientTint(resolvedGradientColor, opacity, dark)
        if (useCouiBlur) {
            Modifier.drawCouiGradientBackdrop(
                backdrop, fadeHeight.value * density, referenceBlurRadius.value * density, gradientSpeed, tint,
            )
        } else {
            Modifier.drawComposeGradientBackdrop(
                backdrop, fadeHeight.value * density, referenceBlurRadius.value * density, gradientSpeed, tint,
            )
        }
    } else {
        val blurEffects = remember(density) {
            BlurLayers.map { (radius, _) -> CouiCoverageBlurEffect(radius.value * density) }
        }
        Modifier.graphicsLayer {
            compositingStrategy = CompositingStrategy.ModulateAlpha
            alpha = opacity
        }.drawCouiBackdrop(
            backdrop = backdrop,
            shape = { RectangleShape },
            effects = { progressiveBlur(blurEffects, fadeHeight) },
        )
    }
    val gradient = remember(resolvedGradientColor, dark) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU || resolvedGradientColor.alpha == 0f) return@remember null
        val color = resolvedGradientColor.copy(alpha = 1f)
        val transparent = color.copy(alpha = 0f)
        if (dark) {
            Brush.verticalGradient(0f to color.copy(alpha = .4f), 1f to transparent)
        } else {
            val transition = Array(LightTintTransitionSegments + 1) { index ->
                val offset = 2f * index / LightTintTransitionSegments - 1f
                val position = CouiLightTintCurve.BendPosition + CouiLightTintCurve.TransitionRadius * offset
                position to color.copy(alpha = CouiLightTintCurve.alphaAt(position))
            }
            Brush.verticalGradient(0f to color, *transition, 1f to transparent)
        }
    }
    SharedTransitionScope { overlayModifier ->
        Layout(
            modifier = modifier.then(overlayModifier)
                // 空白处也参与命中，挡住下层正文，并让栏内控件自行处理事件。
                .pointerInput(Unit) {},
            content = {
                Box(Modifier.couiBackdrop(destination)) {
                    Spacer(Modifier.matchParentSize().then(backgroundModifier))
                    if (gradient != null) {
                        Spacer(Modifier.matchParentSize().drawBehind {
                            drawRect(gradient, alpha = opacity * resolvedGradientColor.alpha)
                        })
                    }
                }
                Box {
                    CompositionLocalProvider(
                        LocalCouiToolbarBackdrop provides destination,
                        LocalCouiToolbarOverlay provides this@SharedTransitionScope,
                        content = content,
                    )
                }
            },
        ) { measurables, constraints ->
            val topBar = measurables[1].measure(constraints.copy(minHeight = 0))
            val topExtensionPx = topExtension().roundToPx()
            val background = measurables[0].measure(
                Constraints.fixed(
                    width = topBar.width,
                    height = topBar.height + topExtensionPx + bottomExtension.roundToPx(),
                )
            )
            // 折叠时固定高度的背景随顶栏上移，渐进曲线不随当前栏高重新拉伸。
            layout(topBar.width, topBar.height) {
                background.placeRelative(0, -topExtensionPx)
                topBar.placeRelative(0, 0)
            }
        }
    }
}

private fun CouiBackdropEffectScope.progressiveBlur(blurEffects: List<CouiCoverageBlurEffect>, fadeHeight: androidx.compose.ui.unit.Dp) {
    if (!size.isSpecified || size.height <= 0f) return

    // CouiBackdrop 会平移采样原点; 遮罩使用相同偏移并保留平台模糊的采样外沿。
    padding = blurEffects.maxOf { it.padding }
    var combined = RenderEffect.createOffsetEffect(0f, 0f)
    for ((index, layer) in BlurLayers.withIndex()) {
        val positions = layer.second
        val blurred = blurEffects[index].effect
        val mask = fadeEffect(positions, fadeHeight)
        val maskedBlur = RenderEffect.createBlendModeEffect(blurred, mask, BlendMode.DST_IN)
        // 在滤镜内叠到不透明采样底图上, 不再由 Compose 分别输出三张半透明图层。
        combined = RenderEffect.createBlendModeEffect(combined, maskedBlur, BlendMode.SRC_OVER)
    }
    // 保留采样内容本身的颜色, 避免主题底色覆盖不同表面时形成额外的明暗渐变。
    renderEffect = combined.asComposeRenderEffect()
}

private fun CouiBackdropEffectScope.fadeEffect(positions: FloatArray, fadeHeight: androidx.compose.ui.unit.Dp): RenderEffect {
    // 渐变沿固定长度展开并锚定背景下沿, 折叠时只平移, 不随栏高压缩。
    // 起点允许超出顶部; 由渐变自身延续端点颜色, 不裁短坐标区间。
    val gradientBottom = padding + size.height
    val gradientTop = gradientBottom - fadeHeight.toPx()
    return RenderEffect.createShaderEffect(
        LinearGradient(
            0f,
            gradientTop,
            0f,
            gradientBottom,
            FadeColors,
            positions,
            Shader.TileMode.CLAMP,
        )
    )
}
