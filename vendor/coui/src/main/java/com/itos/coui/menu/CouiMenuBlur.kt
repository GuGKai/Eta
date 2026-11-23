package com.itos.coui.menu

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import com.itos.coui.backdrop.CouiBackdrop
import kotlin.math.ceil

@Composable
fun rememberCouiMenuBlur(backdrop: CouiBackdrop, background: Color): CouiMenuBlur? =
    rememberCouiKawaseBlur(backdrop, background, CouiMenuBlurRadius)

/** 菜单固定 radius160; 弹窗使用原厂 integer/coui_dialog_blur_radius = 200。 */
private const val CouiMenuBlurRadius = 160
private const val FastKawaseQuarterMaxRadius = 80
private const val FastKawaseEighthMaxRadius = 250
private const val FastKawaseMaxPasses = 10

private data class FastKawaseSpec(
    val scaleDivisor: Int,
    val kawasePasses: Int,
    val step: Float,
) {
    val downsampleCount: Int
        get() = if (scaleDivisor == 16) 2 else 1
}

/** 对齐 RenderEngine BlurGenerator::fastKawaseGenerator 的三档尺度、轮数与步长。 */
private fun fastKawaseSpec(radius: Int): FastKawaseSpec {
    if (radius <= 0) return FastKawaseSpec(scaleDivisor = 4, kawasePasses = 0, step = 0f)
    val scaleDivisor: Int
    val effectiveRadius: Float
    when {
        radius <= FastKawaseQuarterMaxRadius -> {
            scaleDivisor = 4
            effectiveRadius = radius.toFloat()
        }
        radius <= FastKawaseEighthMaxRadius -> {
            scaleDivisor = 8
            effectiveRadius = (radius - 10).coerceAtLeast(0).toFloat()
        }
        else -> {
            scaleDivisor = 16
            effectiveRadius = (radius - 26).coerceAtLeast(0).toFloat()
        }
    }
    val tmpRadius = effectiveRadius / 9f
    val totalPasses = ceil(tmpRadius).toInt().coerceAtMost(FastKawaseMaxPasses)
    val kawasePasses = (totalPasses - 1).coerceAtLeast(0)
    val step = if (totalPasses > 1) (tmpRadius / totalPasses) / scaleDivisor else 0f
    return FastKawaseSpec(scaleDivisor, kawasePasses, step)
}

internal class PreparedKawaseBlur(
    val layer: GraphicsLayer,
    val scaleX: Float,
    val scaleY: Float,
)

/** 原厂 BackgroundBlurDrawable 的 Fast Kawase 载体, radius 为引擎等级而不是像素。 */
@Composable
internal fun rememberCouiKawaseBlur(
    backdrop: CouiBackdrop,
    background: Color,
    radius: Int,
): CouiMenuBlur? {
    if (Build.VERSION.SDK_INT < 33) return null
    val spec = remember(radius) { fastKawaseSpec(radius) }
    val layerCount = if (radius <= 0) 0 else spec.downsampleCount + spec.kawasePasses.coerceAtLeast(1)
    val layers = key(layerCount) { List(layerCount) { rememberGraphicsLayer() } }
    return remember(backdrop, background, radius, layers) {
        CouiMenuBlur(backdrop, background, radius, layers)
    }
}

/** 移植 type 2 Fast Kawase，radius 使用原厂引擎等级，降采样后执行五点滤波。 */
@RequiresApi(33)
class CouiMenuBlur internal constructor(
    private val backdrop: CouiBackdrop,
    private val background: Color,
    private val radius: Int,
    private val layers: List<GraphicsLayer>,
) {
    private val spec = fastKawaseSpec(radius)
    private val boxDownsample = if (radius > 0) RuntimeShader(KawaseDownsampleShader) else null
    private val kawase = RuntimeShader(KawasePassShader)
    private var effectBlurSize = IntSize.Zero
    private var recordedBlocks = IntSize.Zero
    private var recordedCaptureSize = IntSize.Zero
    private var recordedDensity: Density? = null
    private var recordedLayoutDirection: LayoutDirection? = null

    init {
        layers.forEach {
            it.compositingStrategy = CompositingStrategy.Offscreen
            it.clip = true
            it.renderEffect = null
        }
    }

    fun draw(scope: DrawScope, coordinates: LayoutCoordinates) = draw(scope, coordinates, 1f, Offset.Zero)

    /** coordinates 为未动画的锚点；变换与消费者的实际绘制使用同一帧数据。 */
    internal fun draw(
        scope: DrawScope,
        coordinates: LayoutCoordinates,
        sampleScale: Float,
        translation: Offset,
        dimAmount: Float = 0f,
    ) {
        val menuSize = scope.size
        if (!coordinates.isAttached || menuSize.width <= 0f || menuSize.height <= 0f) return
        val prepared = prepare(scope, scope.layoutDirection, menuSize, coordinates, sampleScale, translation, dimAmount)
        if (prepared == null) {
            scope.drawRect(background)
            if (radius == 0) drawSource(scope, coordinates, sampleScale, translation)
            if (dimAmount > 0f) scope.drawRect(Color.Black, alpha = dimAmount)
            return
        }
        scope.withTransform({
            // 原厂用输入区域与最终网格尺寸的实际比值恢复尺寸。
            scale(prepared.scaleX, prepared.scaleY, Offset.Zero)
        }) {
            drawLayer(prepared.layer)
        }
    }

    /** 录制并更新最终小尺寸模糊层；调用方可在位移期间直接复用该层而无需整窗离屏缓存。 */
    internal fun prepare(
        density: Density,
        layoutDirection: LayoutDirection,
        targetSize: Size,
        coordinates: LayoutCoordinates,
        sampleScale: Float = 1f,
        translation: Offset = Offset.Zero,
        dimAmount: Float = 0f,
    ): PreparedKawaseBlur? {
        if (radius <= 0 || layers.isEmpty() || !coordinates.isAttached ||
            targetSize.width <= 0f || targetSize.height <= 0f
        ) return null
        val origin = coordinates.localToScreen(Offset.Zero)
        val right = coordinates.localToScreen(Offset(targetSize.width, 0f))
        val bottom = coordinates.localToScreen(Offset(0f, targetSize.height))
        val deviceWidth = (right - origin).getDistance() * sampleScale
        val deviceHeight = (bottom - origin).getDistance() * sampleScale
        if (!deviceWidth.isFinite() || !deviceHeight.isFinite() || deviceWidth <= 0f || deviceHeight <= 0f) {
            return null
        }

        // RenderEngine 以设备像素计算 ceil(extent / 16)，再按目标档位对齐各级尺寸。
        val blocksX = ceil(deviceWidth / 16f).toInt().coerceAtLeast(1)
        val blocksY = ceil(deviceHeight / 16f).toInt().coerceAtLeast(1)
        // 原厂 1/8 档先把输入双线性缩到半尺寸。1/4 与 1/16 档直接在原尺寸输入上取四点。
        val captureSize = if (spec.scaleDivisor != 8) {
            IntSize(ceil(deviceWidth).toInt(), ceil(deviceHeight).toInt())
        } else {
            IntSize(blocksX * 8, blocksY * 8)
        }
        val blurFactor = 16 / spec.scaleDivisor
        val blurSize = IntSize(blocksX * blurFactor, blocksY * blurFactor)
        updateEffects(blurSize)

        // 保存独立 Density 值，不让 GraphicsLayer 恢复回调持有 DrawScope / CacheDrawScope。
        val recordingDensity = Density(density.density, density.fontScale)
        layers[0].record(recordingDensity, layoutDirection, captureSize) {
            // 补底也进入采样链, 不能在材质混色之后再补不透明背景。
            drawRect(background)
            withTransform({
                scale(captureSize.width / targetSize.width, captureSize.height / targetSize.height, Offset.Zero)
            }) {
                drawSource(this, coordinates, sampleScale, translation)
            }
            // 宿主录制缺少系统遮罩，在采样层补上它以对齐窗口合成顺序。
            if (dimAmount > 0f) drawRect(Color.Black, alpha = dimAmount)
        }
        recordBlurPasses(recordingDensity, layoutDirection, blocksX, blocksY, captureSize, blurSize)
        return PreparedKawaseBlur(
            layer = layers.last(),
            scaleX = targetSize.width / blurSize.width,
            scaleY = targetSize.height / blurSize.height,
        )
    }

    private fun drawSource(
        target: DrawScope,
        coordinates: LayoutCoordinates,
        sampleScale: Float,
        translation: Offset,
    ) {
        target.withTransform({
            scale(1f / sampleScale, 1f / sampleScale, Offset.Zero)
            translate(-translation.x, -translation.y)
        }) { backdrop.draw(this, coordinates) }
    }

    private fun recordBlurPasses(
        recordingDensity: Density,
        layoutDirection: LayoutDirection,
        blocksX: Int,
        blocksY: Int,
        captureSize: IntSize,
        blurSize: IntSize,
    ) {
        val blocks = IntSize(blocksX, blocksY)
        val cachedDensity = recordedDensity
        if (recordedBlocks == blocks && recordedCaptureSize == captureSize && cachedDensity != null &&
            cachedDensity.density == recordingDensity.density &&
            cachedDensity.fontScale == recordingDensity.fontScale &&
            recordedLayoutDirection == layoutDirection
        ) return
        var previousSize = captureSize
        for (index in 1..layers.lastIndex) {
            val previous = layers[index - 1]
            val shrink = index <= spec.downsampleCount
            val size = if (index < spec.downsampleCount) IntSize(blocksX * 4, blocksY * 4) else blurSize
            if (shrink && boxDownsample != null) {
                boxDownsample.setFloatUniform("inputSize", previousSize.width.toFloat(), previousSize.height.toFloat())
                boxDownsample.setFloatUniform("inputScale", previousSize.width.toFloat() / size.width,
                    previousSize.height.toFloat() / size.height)
                boxDownsample.setFloatUniform("outputSize", size.width.toFloat(), size.height.toFloat())
                previous.renderEffect =
                    RenderEffect.createRuntimeShaderEffect(boxDownsample, "child").asComposeRenderEffect()
            }
            layers[index].record(recordingDensity, layoutDirection, size) {
                drawLayer(previous)
            }
            previousSize = size
        }
        recordedBlocks = blocks
        recordedCaptureSize = captureSize
        recordedDensity = recordingDensity
        recordedLayoutDirection = layoutDirection
    }

    private fun updateEffects(blurSize: IntSize) {
        if (effectBlurSize == blurSize || spec.kawasePasses == 0) return
        kawase.setFloatUniform("inputSize", blurSize.width.toFloat(), blurSize.height.toFloat())
        for (pass in 1..spec.kawasePasses) {
            kawase.setFloatUniform("in_blurOffset", pass * spec.step)
            layers[spec.downsampleCount + pass - 1].renderEffect =
                RenderEffect.createRuntimeShaderEffect(kawase, "child").asComposeRenderEffect()
        }
        effectBlurSize = blurSize
    }
}

// 原厂在输出像素中心的 ±0.25 处取样，再经逆矩阵映射到输入像素。
private const val KawaseDownsampleShader = """
    uniform shader child;
    uniform float2 inputSize;
    uniform float2 inputScale;
    uniform float2 outputSize;

    half4 sampleChild(float2 xy) {
        return child.eval(clamp(xy * inputScale, float2(0.5), inputSize - 0.5));
    }

    half4 main(float2 xy) {
        // RenderEffect 的输出仍占输入层尺寸，后续只读取原厂目标网格。
        if (any(greaterThanEqual(xy, outputSize))) return half4(0.0);
        half4 c = sampleChild(xy + float2(+0.25, +0.25));
        c += sampleChild(xy + float2(+0.25, -0.25));
        c += sampleChild(xy + float2(-0.25, -0.25));
        c += sampleChild(xy + float2(-0.25, +0.25));
        return half4(c.rgb * 0.25, 1.0);
    }
"""

// RenderEngine 的五点 Kawase 滤波，Clamp 限定在各轮实际图像范围内。
private const val KawasePassShader = """
    uniform shader child;
    uniform float2 inputSize;
    uniform float in_blurOffset;

    half4 sampleChild(float2 xy) {
        return child.eval(clamp(xy, float2(0.5), inputSize - 0.5));
    }

    half4 main(float2 xy) {
        half4 c = sampleChild(xy);
        c += sampleChild(xy + float2(+in_blurOffset, +in_blurOffset));
        c += sampleChild(xy + float2(+in_blurOffset, -in_blurOffset));
        c += sampleChild(xy + float2(-in_blurOffset, -in_blurOffset));
        c += sampleChild(xy + float2(-in_blurOffset, +in_blurOffset));
        return half4(c.rgb * 0.2, 1.0);
    }
"""
