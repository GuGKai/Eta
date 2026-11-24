package com.itos.coui.effect

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/** 材质的第二层混合模式。 */
enum class CouiMaterialBlendMode { Overlay, ColorDodge }

/** 两层材质混色, 只作用于背景输入。组件负责选择颜色和模式预设。 */
@RequiresApi(33)
@Composable
fun rememberCouiMaterialEffect(mix: Color, blend: Color,
    blendMode: CouiMaterialBlendMode = CouiMaterialBlendMode.Overlay): RenderEffect =
    rememberCouiMaterialEffect(mix, blend, blendMode, rgbMix = false)

@RequiresApi(33)
@Composable
internal fun rememberCouiMaterialEffect(
    mix: Color,
    blend: Color,
    blendMode: CouiMaterialBlendMode,
    rgbMix: Boolean,
    slightDither: Boolean = false,
    themeTint: Color = Color.Transparent,
): RenderEffect =
    remember(mix, blend, blendMode, rgbMix, slightDither, themeTint) {
        val tintEnabled = themeTint.alpha > 0f
        val shader = RuntimeShader(
            "const bool RGB_MIX = $rgbMix;\nconst bool SLIGHT_DITHER = $slightDither;\n" +
                "const bool THEME_TINT = $tintEnabled;\n" + CouiMaterialShader,
        )
        shader.setFloatUniform("uMixColor", mix.red, mix.green, mix.blue, mix.alpha)
        shader.setFloatUniform("uBlendColor", blend.red, blend.green, blend.blue, blend.alpha)
        shader.setFloatUniform("uColorDodge", if (blendMode == CouiMaterialBlendMode.ColorDodge) 1f else 0f)
        if (tintEnabled) shader.setColorUniform("uThemeTint", themeTint.toArgb())
        RenderEffect.createRuntimeShaderEffect(shader, "content")
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
            float3 rgb = clamp(color.rgb, 0.0, 1.0);
            color.rgb = mix(rgb, luminosity(rgb, uThemeTint.rgb), uThemeTint.a);
        }
        if (SLIGHT_DITHER) {
            color.rgb = (color.rgb + slightDither(coordinate)) * color.a;
        } else if (THEME_TINT) {
            color.rgb *= color.a;
        }
        return color;
    }
"""
