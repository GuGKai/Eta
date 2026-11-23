package com.itos.coui.effect

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.CompositingStrategy as ComposeCompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.blur.BlurRadiusSpec
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.toColorLong
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import com.itos.coui.backdrop.CouiBackdrop
import com.itos.coui.backdrop.CouiBackdropEffectScope
import com.itos.coui.backdrop.DrawCouiBackdropNode
import com.itos.coui.backdrop.drawCouiBackdrop
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.random.Random

private const val BlurOffsetPx = 2f
private const val SigmaScale = 0.57735f

internal data class CouiGradientTint(val color: Color, val opacity: Float, val dark: Boolean)

internal object CouiLightTintCurve {
    const val BendPosition = 0.6f
    const val TransitionRadius = 0.12f
    // 原厂中点的 70% 截断为 178/255。只圆滑接点附近，两端仍沿用原斜率。
    private const val BendAlpha = 178f / 255f
    const val UpperSlope = (1f - BendAlpha) / BendPosition
    const val LowerSlope = BendAlpha / (1f - BendPosition)
    const val BendCorrection = (LowerSlope - UpperSlope) / (4f * TransitionRadius)

    fun alphaAt(position: Float): Float {
        val linear = minOf(1f - UpperSlope * position, LowerSlope * (1f - position))
        val blend = (TransitionRadius - abs(position - BendPosition)).coerceAtLeast(0f)
        return linear - BendCorrection * blend * blend
    }
}

/** 仅模糊背景；长度为 px，各节点独立持有横纵图层，Shader 在首次绘制创建。 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun Modifier.drawCouiGradientBackdrop(
    backdrop: CouiBackdrop,
    fadeHeightPx: Float,
    referenceBlurRadiusPx: Float,
    gradientSpeed: Float? = null,
): Modifier = drawCouiGradientBackdrop(backdrop, fadeHeightPx, referenceBlurRadiusPx, gradientSpeed, tint = null)

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
internal fun Modifier.drawCouiGradientBackdrop(
    backdrop: CouiBackdrop,
    fadeHeightPx: Float,
    referenceBlurRadiusPx: Float,
    gradientSpeed: Float?,
    tint: CouiGradientTint?,
): Modifier {
    require(gradientSpeed == null || gradientSpeed.isFinite() && gradientSpeed > 0f)
    return graphicsLayer {
        shape = RectangleShape
        clip = true
        compositingStrategy = ComposeCompositingStrategy.Offscreen
    }.then(GradientBackdropElement(backdrop, fadeHeightPx, referenceBlurRadiusPx, gradientSpeed, tint))
}

@RequiresApi(33)
@Composable
internal fun Modifier.drawComposeGradientBackdrop(
    backdrop: CouiBackdrop,
    fadeHeightPx: Float,
    referenceBlurRadiusPx: Float,
    gradientSpeed: Float?,
    tint: CouiGradientTint,
): Modifier {
    require(gradientSpeed == null || gradientSpeed.isFinite() && gradientSpeed > 0f)
    val density = LocalDensity.current
    val currentTint = rememberUpdatedState(tint)
    val effects = remember<CouiBackdropEffectScope.() -> Unit>(
        density, fadeHeightPx, referenceBlurRadiusPx, gradientSpeed,
    ) {
        val paddingPx = gradientPadding(gradientKernelRadius(referenceBlurRadiusPx))
        // Compose 负责卷积；遮罩只给出与 COUI 相同的空间半径比例。
        val radiusMask = RuntimeShader("""
            uniform float gradientTop;
            uniform float gradientHeight;
            half4 main(float2 coord) {
                ${gradientPosition(gradientSpeed)}
                return half4(0.0, 0.0, 0.0, ratio);
            }
        """.trimIndent())
        val radiusSpec = BlurRadiusSpec.shader(with(density) { referenceBlurRadiusPx.toDp() }) { radiusMask }
        val tintShader = RuntimeShader("""
            uniform shader child;
            float4 blur(float2 coord) { return child.eval(coord); }
            $GradientNoiseShader
            $TintOutputShader
        """.trimIndent())
        var lastLayerSize = Size.Zero
        var lastTint: CouiGradientTint? = null
        var blur: RenderEffect? = null
        var effect: androidx.compose.ui.graphics.RenderEffect? = null
        return@remember {
            padding = paddingPx
            val layerSize = Size(size.width + 2f * paddingPx, size.height + 2f * paddingPx)
            val geometryChanged = layerSize != lastLayerSize
            if (geometryChanged) {
                val gradientHeight = if (gradientSpeed == null) fadeHeightPx else size.height
                radiusMask.setFloatUniform("gradientTop", paddingPx + size.height - gradientHeight)
                radiusMask.setFloatUniform("gradientHeight", gradientHeight)
                blur = radiusSpec.createRenderEffect(layerSize, density).asAndroidRenderEffect()
                lastLayerSize = layerSize
            }
            // 搜索页随滚动改变遮罩透明度时继续复用模糊效果。
            val appliedTint = currentTint.value
            if (geometryChanged || appliedTint != lastTint) {
                tintShader.setGradientTint(appliedTint, paddingPx, size.height)
                val overlay = RenderEffect.createRuntimeShaderEffect(tintShader, "child")
                effect = RenderEffect.createChainEffect(overlay, checkNotNull(blur)).asComposeRenderEffect()
                lastTint = appliedTint
            }
            renderEffect = effect
        }
    }
    return drawCouiBackdrop(backdrop, shape = { RectangleShape }, effects = effects)
}

@RequiresApi(33)
private data class GradientBackdropElement(
    val backdrop: CouiBackdrop,
    val fadeHeightPx: Float,
    val referenceBlurRadiusPx: Float,
    val gradientSpeed: Float?,
    val tint: CouiGradientTint?,
) : ModifierNodeElement<GradientBackdropNode>() {
    override fun create() = GradientBackdropNode(backdrop, fadeHeightPx, referenceBlurRadiusPx, gradientSpeed, tint)
    override fun update(node: GradientBackdropNode) {
        node.backdrop = backdrop
        node.update(fadeHeightPx, referenceBlurRadiusPx, gradientSpeed, tint)
    }
    override fun InspectorInfo.inspectableProperties() { name = "drawCouiGradientBackdrop" }
}

@RequiresApi(33)
private class GradientBackdropNode(
    backdrop: CouiBackdrop,
    private var fadeHeightPx: Float,
    private var referenceBlurRadiusPx: Float,
    private var gradientSpeed: Float?,
    private var tint: CouiGradientTint?,
) : DrawCouiBackdropNode(backdrop) {
    private var passes = emptyList<GraphicsLayer>()
    private var renderer: CouiGradientRenderer? = null
    private var rendererDensity: Density? = null

    override fun onAttach() {
        super.onAttach()
        passes = List(2) { requireGraphicsContext().createGraphicsLayer() }
    }

    override fun onDetach() {
        passes.forEach { requireGraphicsContext().releaseGraphicsLayer(it) }
        passes = emptyList()
        renderer = null
        rendererDensity = null
        super.onDetach()
    }

    fun update(fadeHeightPx: Float, referenceBlurRadiusPx: Float, gradientSpeed: Float?, tint: CouiGradientTint?) {
        if (this.fadeHeightPx != fadeHeightPx || this.referenceBlurRadiusPx != referenceBlurRadiusPx ||
            this.gradientSpeed != gradientSpeed || (this.tint == null) != (tint == null)) {
            this.fadeHeightPx = fadeHeightPx
            this.referenceBlurRadiusPx = referenceBlurRadiusPx
            this.gradientSpeed = gradientSpeed
            renderer = null
        }
        this.tint = tint
    }

    override fun configureEffects(scope: CouiBackdropEffectScope) {
        val density = scope.recordingDensity
        val current = renderer?.takeIf { rendererDensity == density }
            ?: CouiGradientRenderer(fadeHeightPx, referenceBlurRadiusPx, gradientSpeed, density, passes, tint != null).also {
                renderer = it
                rendererDensity = density
            }
        scope.padding = current.padding
        current.height = scope.size.height
    }

    override fun drawSource(scope: DrawScope, source: DrawScope.() -> Unit) {
        checkNotNull(renderer).draw(scope, source, tint)
    }
}

/** 原厂渐进 Gaussian 先横后纵, 每遍保留原尺寸中间图, 不使用多级缩小图混合。 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class CouiGradientRenderer(
    private val fadeHeightPx: Float,
    referenceBlurRadiusPx: Float,
    private val gradientSpeed: Float?,
    private val density: Density,
    private val passes: List<GraphicsLayer>,
    hasTint: Boolean,
) {
    private val kernelRadius = gradientKernelRadius(referenceBlurRadiusPx)
    private val shaders = List(2) { index ->
        RuntimeShader(gradientGaussianShader(kernelRadius, gradientSpeed, hasTint && index == 1)).apply {
            setFloatUniform(
                "in_blurOffset",
                if (index == 0) BlurOffsetPx else 0f,
                if (index == 1) BlurOffsetPx else 0f,
            )
        }
    }
    private var lastSize = IntSize.Zero
    private var lastHeight = Float.NaN
    private var recordedSize = IntSize.Zero
    private var recordedLayoutDirection: LayoutDirection? = null

    var height: Float = 0f

    val padding: Float = gradientPadding(kernelRadius)

    init {
        passes.forEach {
            it.compositingStrategy = CompositingStrategy.Offscreen
            it.clip = true
        }
    }

    fun draw(scope: DrawScope, drawBackdrop: DrawScope.() -> Unit, tint: CouiGradientTint?) {
        if (scope.size.width <= 0f || scope.size.height <= 0f) return
        val paddedSize = IntSize(scope.size.width.toInt(), scope.size.height.toInt())
        updateEffects(paddedSize, tint)
        // 新 Canvas 不继承 CouiBackdrop 的 padding 平移; 独立 Density 避免 DrawScope 循环代理。
        passes[0].record(density, scope.layoutDirection, paddedSize) {
            translate(padding, padding) { drawBackdrop() }
        }
        // 独立图层落实两次栅格化, 不让第二遍在 Shader 内嵌套执行第一遍卷积。
        // 第二遍指令只引用第一遍; 子层更新仍会传播, 不需要因背景滚动而重录。
        if (recordedSize != paddedSize || recordedLayoutDirection != scope.layoutDirection) {
            val horizontal = passes[0]
            passes[1].record(density, scope.layoutDirection, paddedSize) { drawLayer(horizontal) }
            recordedSize = paddedSize
            recordedLayoutDirection = scope.layoutDirection
        }
        scope.translate(-padding, -padding) { drawLayer(passes[1]) }
    }

    private fun updateEffects(size: IntSize, tint: CouiGradientTint?) {
        val geometryChanged = lastSize != size || lastHeight != height
        val gradientHeight = if (gradientSpeed == null) fadeHeightPx else height
        // 原厂每次执行卷积时取一个百分位种子，两遍共用。
        val noiseSeed = Random.nextInt(101) / 100f
        shaders.forEachIndexed { index, shader ->
            if (geometryChanged) {
                shader.setFloatUniform("inputSize", size.width.toFloat(), size.height.toFloat())
                shader.setFloatUniform("gradientTop", padding + height - gradientHeight)
                shader.setFloatUniform("gradientHeight", gradientHeight)
            }
            if (index == 1 && tint != null) {
                shader.setGradientTint(tint, padding, height)
            }
            shader.setFloatUniform("in_random", noiseSeed)
            // RenderEffect 保存 uniform 快照，种子随绘制录制更新。
            passes[index].renderEffect = RenderEffect.createRuntimeShaderEffect(shader, "child")
                .asComposeRenderEffect()
        }
        lastSize = size
        lastHeight = height
    }
}

// 将宿主的参考半径换算为 COUI 核: 原权重 sigma=radius/3, 采样间距为 2px。
private fun gradientKernelRadius(referenceBlurRadiusPx: Float): Int =
    ceil((referenceBlurRadiusPx * SigmaScale + 0.5f) * 3f / BlurOffsetPx).toInt()

// 覆盖最大采样偏移和线性插值的外沿, 不让边界 Clamp 进入可见区域。
private fun gradientPadding(kernelRadius: Int): Float = kernelRadius * BlurOffsetPx + 1f

@RequiresApi(33)
private fun RuntimeShader.setGradientTint(tint: CouiGradientTint, padding: Float, height: Float) {
    setColorUniform("tintColor", tint.color.toColorLong())
    setFloatUniform("tintBounds", padding, height)
    setFloatUniform("backgroundOpacity", tint.opacity)
    setIntUniform("darkTint", if (tint.dark) 1 else 0)
}

// 固定渐隐长度使用 smoothstep；全高渐隐使用原厂幂曲线。
private fun gradientPosition(gradientSpeed: Float?): String = """
    float position = clamp((coord.y - gradientTop) / gradientHeight, 0.0, 1.0);
    float ratio = ${if (gradientSpeed == null) "smoothstep(0.0, 1.0, 1.0 - position)" else "1.0 - pow(position, ${gradientSpeed})"};
""".trimIndent()

private const val GradientNoiseShader = """
    float interleavedGradientNoise(float2 coord) {
        const float3 magicNumbers = float3(0.06711056, 0.00583715, 52.9829189);
        return fract(magicNumbers.z * fract(dot(coord, magicNumbers.xy)));
    }
"""

// libhwui.so: OplusGradientGaussianBlurEffect::make_blur_effect, 保留整数半径、权重和剩余中心权重。
private fun gradientGaussianShader(kernelRadius: Int, gradientSpeed: Float?, tint: Boolean): String = """
    const int in_blurRadius = $kernelRadius;
    const float PI = 3.1415926;
    uniform shader child;
    uniform float2 inputSize;
    uniform float gradientTop;
    uniform float gradientHeight;
    uniform float in_random;
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

    $GradientNoiseShader

    float4 blur(float2 coord) {
        float4 c = sampleChild(coord);
        ${gradientPosition(gradientSpeed)}
        int radius = int(float(in_blurRadius) * ratio);
        if (radius <= 1) return c;
        // 卷积结果恢复中心 alpha 后恒为零; 保留清晰端直通, 不截断低但非零的覆盖率。
        if (c.a == 0.0) return float4(0.0);
        float2 offset = float2(0.0);
        float sumWeight = 0.0;
        // 颜色累加与覆盖率归一保持 float，避免 half 舍入随整数半径形成横向色带。
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
        // 屏幕外没有页面采样; 恢复有效颜色但不扩张中心覆盖度, 避免顶边变暗或补成不透明。
        if (finalColor.a <= 0.0) return float4(0.0);
        finalColor = float4(finalColor.rgb * (c.a / finalColor.a), c.a);
        float noise = interleavedGradientNoise(coord + in_random) - 0.5;
        finalColor.rgb = clamp(finalColor.rgb + noise * c.a / 200.0, float3(0.0), float3(c.a));
        return finalColor;
    }
    ${if (tint) TintOutputShader else "half4 main(float2 coord) { return half4(blur(coord)); }"}
""".trimIndent()

// 在纵向模糊末尾按 SrcOver 合成遮罩，再抖动待写入的颜色，避免 8 位舍入把整行压成同色。
private const val TintOutputShader = """
    layout(color) uniform half4 tintColor;
    uniform float2 tintBounds;
    uniform float backgroundOpacity;
    uniform int darkTint;

    half4 main(float2 coord) {
        float4 blurred = blur(coord);
        float4 result = blurred * backgroundOpacity;
        if (tintColor.a == 0.0) return half4(result);

        float y = clamp((coord.y - tintBounds.x) / tintBounds.y, 0.0, 1.0);
        float mask = 0.4 * (1.0 - y);
        if (darkTint == 0) {
            float linear = min(1.0 - ${CouiLightTintCurve.UpperSlope} * y, ${CouiLightTintCurve.LowerSlope} * (1.0 - y));
            float blend = max(${CouiLightTintCurve.TransitionRadius} - abs(y - ${CouiLightTintCurve.BendPosition}), 0.0);
            mask = linear - ${CouiLightTintCurve.BendCorrection} * blend * blend;
        }
        float alpha = mask * tintColor.a * backgroundOpacity;
        result = float4(float3(tintColor.rgb) * alpha, alpha) + result * (1.0 - alpha);

        if (alpha > 0.0 && alpha < 1.0 && any(notEqual(float3(tintColor.rgb) * blurred.a, blurred.rgb))) {
            float noise = (interleavedGradientNoise(coord) - 0.5) / 255.0;
            float3 limit = float3(result.a);
            result.rgb += clamp(result.rgb + noise, float3(0.0), limit)
                - clamp(result.rgb, float3(0.0), limit);
        }
        return half4(result);
    }
"""
