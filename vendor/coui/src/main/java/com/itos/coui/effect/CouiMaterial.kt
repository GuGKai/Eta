package com.itos.coui.effect

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.toArgb

/** 材质的第二层混合模式。 */
enum class CouiMaterialBlendMode { Overlay, ColorDodge }

/** 全库材质保亮度染色的统一固定强度；只作用于模糊材质染色步骤，不影响不透明背景与遮罩。 */
internal const val CouiMaterialTintStrength = 0.30f

/** 将基准色归一化为最终染色值；透明或未指定颜色关闭染色，非透明色忽略外部 alpha 并采用库内固定强度。 */
internal fun resolveCouiThemeTint(baseColor: Color): Color =
    if (baseColor.isSpecified && baseColor.alpha > 0f) baseColor.copy(alpha = CouiMaterialTintStrength) else Color.Transparent

/** 两层材质混色, 只作用于背景输入。组件负责选择颜色和模式预设；themeTint 为染色基准色，透明关闭染色。 */
@RequiresApi(33)
@Composable
fun rememberCouiMaterialEffect(
    mix: Color,
    blend: Color,
    blendMode: CouiMaterialBlendMode = CouiMaterialBlendMode.Overlay,
    themeTint: Color = Color.Transparent,
): RenderEffect =
    rememberCouiMaterialEffect(mix, blend, blendMode, rgbMix = false, themeTint = themeTint)

@RequiresApi(33)
@Composable
internal fun rememberCouiMaterialEffect(
    mix: Color,
    blend: Color,
    blendMode: CouiMaterialBlendMode,
    rgbMix: Boolean,
    slightDither: Boolean = false,
    themeTint: Color = Color.Transparent,
): RenderEffect {
    val resolvedTint = resolveCouiThemeTint(themeTint)
    return remember(mix, blend, blendMode, rgbMix, slightDither, resolvedTint) {
        val tintEnabled = resolvedTint.alpha > 0f
        val shader = RuntimeShader(
            "const bool RGB_MIX = $rgbMix;\nconst bool SLIGHT_DITHER = $slightDither;\n" +
                "const bool THEME_TINT = $tintEnabled;\n" + CouiMaterialShader,
        )
        shader.setFloatUniform("uMixColor", mix.red, mix.green, mix.blue, mix.alpha)
        shader.setFloatUniform("uBlendColor", blend.red, blend.green, blend.blue, blend.alpha)
        shader.setFloatUniform("uColorDodge", if (blendMode == CouiMaterialBlendMode.ColorDodge) 1f else 0f)
        if (tintEnabled) shader.setColorUniform("uThemeTint", resolvedTint.toArgb())
        RenderEffect.createRuntimeShaderEffect(shader, "content")
    }
}

internal const val CouiMaterialShaderFunctions = """
    float lum(float3 color) {
        return dot(color, float3(0.3, 0.59, 0.11));
    }

    float3 luminosity(float3 source, float3 destination) {
        float destinationLum = lum(destination);
        float sourceLum = lum(source);
        float3 color = destination + (sourceLum - destinationLum);
        float minColor = min(min(color.x, color.y), color.z);
        float maxColor = max(max(color.x, color.y), color.z);
        if (minColor < 0.0) {
            return sourceLum + ((color - sourceLum) * sourceLum) / (sourceLum - minColor);
        } else if (maxColor > 1.0) {
            return sourceLum + ((color - sourceLum) * (1.0 - sourceLum)) / (maxColor - sourceLum);
        }
        return color;
    }

    half3 applyCouiThemeTint(half3 rgb, half4 tint) {
        float3 clamped = clamp(float3(rgb), 0.0, 1.0);
        return half3(mix(clamped, luminosity(clamped, float3(tint.rgb)), float(tint.a)));
    }

    float overlay(float source, float destination) {
        return destination < 0.5
            ? 2.0 * source * destination
            : 1.0 - 2.0 * (1.0 - source) * (1.0 - destination);
    }

    float3 overlay(float3 source, float3 destination) {
        return float3(
            overlay(source.x, destination.x),
            overlay(source.y, destination.y),
            overlay(source.z, destination.z)
        );
    }

    half materialOverlay(half source, half destination) {
        return destination < 0.5
            ? 2.0 * source * destination
            : 1.0 - 2.0 * (1.0 - source) * (1.0 - destination);
    }

    half3 materialOverlay(half3 source, half3 destination) {
        return half3(
            materialOverlay(source.r, destination.r),
            materialOverlay(source.g, destination.g),
            materialOverlay(source.b, destination.b)
        );
    }

    half4 couiMaterial(half4 inColor, float4 mixColor, float4 blendColor) {
        half4 tint = half4(mixColor);
        half4 blend = half4(blendColor);
        half3 luminosityColor = luminosity(tint.rgb, inColor.rgb);
        inColor.rgb = mix(inColor.rgb, luminosityColor, tint.a);
        inColor.rgb = mix(inColor.rgb, materialOverlay(blend.rgb, inColor.rgb), blend.a);
        return inColor;
    }
"""

// SkiaDitherEffect::createSlightDitherShader 在输出位置施加共享 RGB 扰动。
internal const val CouiSlightDitherShaderFunction = """
    float slightDither(float2 coordinate) {
        float noise = fract(sin(dot(coordinate, float2(12.9898, 78.233))) * 43758.5453);
        return mix(-0.005, 0.005, noise);
    }
"""

private const val CouiMaterialShader = CouiMaterialShaderFunctions + CouiSlightDitherShaderFunction + """
    uniform shader content;
    uniform float4 uMixColor;
    uniform float4 uBlendColor;
    uniform float uColorDodge;
    layout(color) uniform half4 uThemeTint;

    half4 main(float2 coordinate) {
        half4 color = content.eval(coordinate);
        if (SLIGHT_DITHER || THEME_TINT) {
            // 染色和抖动使用非预乘颜色，随后恢复放大采样的覆盖度。
            if (color.a == 0.0) return half4(0.0);
            color.rgb /= color.a;
        }
        if (RGB_MIX) {
            // 原厂的 RGB 混色与第二层混合均使用 half；ColorDodge 保留未限幅结果。
            half4 mixColor = half4(uMixColor);
            half4 blendColor = half4(uBlendColor);
            color.rgb = mix(color.rgb, mixColor.rgb, mixColor.a);
            half3 blend = uColorDodge < 0.5
                ? materialOverlay(blendColor.rgb, color.rgb)
                : color.rgb / (half3(1.0) - blendColor.rgb);
            color.rgb = mix(color.rgb, blend, blendColor.a);
        } else {
            float3 tint = luminosity(uMixColor.rgb, color.rgb);
            color.rgb = mix(color.rgb, tint, uMixColor.a);
            float3 blend = uColorDodge < 0.5
                ? overlay(uBlendColor.rgb, color.rgb)
                : color.rgb / (1.0 - uBlendColor.rgb);
            color.rgb = mix(color.rgb, blend, uBlendColor.a);
        }
        if (THEME_TINT) {
            // ColorDodge 可超过 SDR 白点，染色以可见的材质亮度为准。
            color.rgb = applyCouiThemeTint(color.rgb, uThemeTint);
        }
        if (SLIGHT_DITHER) {
            color.rgb = (color.rgb + slightDither(coordinate)) * color.a;
        } else if (THEME_TINT) {
            color.rgb *= color.a;
        }
        return color;
    }
"""
