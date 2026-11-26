package com.itos.coui.navigation

import android.graphics.BlendMode
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.platform.LocalDensity
import com.itos.coui.effect.CouiFloatingMaterial
import com.itos.coui.effect.CouiFloatingShadow
import com.itos.coui.effect.LocalCouiContourGlowEnabled
import com.itos.coui.effect.SpotlightPreset
import com.itos.coui.floatingactionbutton.CouiFabDefaults
import com.itos.coui.theme.CouiTheme

object CouiNavigationBarDefaults {
    /** 原厂底栏材质，可用于中间胶囊或任一透明附件。 */
    @Composable
    fun material(
        dark: Boolean = CouiTheme.isDark,
        lightColor: Color = Color.White,
        themeTint: Color = CouiTheme.materialTintFor(dark),
    ): CouiFloatingMaterial = remember(dark, lightColor, themeTint) {
        CouiFloatingMaterial(
            firstColor = if (dark) Color(0x80666666) else Color(0xB3666666),
            firstBlendMode = if (dark) BlendMode.OVERLAY else BlendMode.COLOR_DODGE,
            secondColor = if (dark) Color(0xB3262626) else Color(0x99F5F5F5),
            edgeAlpha = if (dark) 0.8f else 0.25f,
            innerGlowAlpha = if (dark) 0.03f else 0.1f,
            lightColor = lightColor,
            themeTint = themeTint,
        )
    }

    /** 左右通用的透明附件样式，使用当前主题的前景色和底栏光效。 */
    @Composable
    fun transparentActionStyle(
        dark: Boolean = CouiTheme.isDark,
        material: CouiFloatingMaterial = CouiNavigationBarDefaults.material(dark),
    ): CouiNavigationBarActionStyle {
        val density = LocalDensity.current.density
        val contour = LocalCouiContourGlowEnabled.current
        return remember(dark, material, density, contour) {
            CouiNavigationBarActionStyle(
                material = material,
                shadow = transparentShadow(dark, density, contour, material.lightColor, capsule = false),
                contentColor = if (dark) Color.White else Color.Black,
            )
        }
    }

    /** 左右通用的有色 FAB 样式。省略底色时使用主题强调色。 */
    @Composable
    fun coloredActionStyle(
        containerColor: Color = Color.Unspecified,
        dark: Boolean = CouiTheme.isDark,
    ): CouiNavigationBarActionStyle {
        val density = LocalDensity.current.density
        val contour = LocalCouiContourGlowEnabled.current
        val accent = CouiTheme.colorSchemeFor(dark).controlAccent
        return remember(containerColor, dark, density, contour, accent) {
            val material = if (containerColor.isSpecified) CouiFabDefaults.material(containerColor, dark)
                else CouiFabDefaults.themeMaterial(accent, dark)
            CouiNavigationBarActionStyle(
                material = material,
                shadow = CouiFabDefaults.shadow(material.firstColor, density, contour),
                contentColor = CouiFabDefaults.contentColor,
                spotlight = SpotlightPreset.OpaqueSmall1,
                hoverFeedback = true,
            )
        }
    }

    internal fun transparentShadow(
        dark: Boolean,
        density: Float,
        contour: Boolean,
        lightColor: Color,
        capsule: Boolean,
    ): CouiFloatingShadow {
        fun pixelOffset(dp: Float) = (dp * density).toInt().toFloat()
        fun pixelSize(dp: Float): Float {
            val px = dp * density
            return (if (px >= 0f) px + 0.5f else px - 0.5f).toInt().toFloat()
        }
        val projectionColor = when {
            contour -> if (capsule) Color(0x2D000000) else Color(0x23000000)
            dark -> Color(0x78000000)
            else -> Color(0x28000000)
        }
        return CouiFloatingShadow(
            elevation = pixelSize(if (contour) 24f else 350f),
            color = projectionColor,
            lightY = pixelSize(if (contour) -320f else -160f),
            lightZ = if (contour) 0f else pixelSize(10000f),
            lightRadius = pixelSize(if (contour) 1600f else 2000f),
            causticColor = lightColor.copy(alpha = 0x42 / 255f),
            layout = Rect(0f, 0f, 0f, pixelOffset(if (capsule) 4.9f else 11.2f)),
            sdf = pixelOffset(if (capsule) 10.4f else 20f),
            smoothCorner = capsule,
        )
    }
}
