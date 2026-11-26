package com.itos.coui.floatingtoolbar

import android.graphics.BlendMode
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.itos.coui.effect.CouiFloatingMaterial
import com.itos.coui.effect.CouiFloatingShadow
import com.itos.coui.effect.LocalCouiContourGlowEnabled
import com.itos.coui.navigation.CouiNavigationBarDefaults
import com.itos.coui.shape.CouiCapsuleShape
import com.itos.coui.theme.CouiTheme

/**
 * 悬浮操作栏及其操作项的样式，装配要求 API 34。
 * [contentColor] 默认取 [dark] 对应配色的 `onSurface`；显式设为 `Color.Unspecified` 时保留宿主环境内容色。
 */
data class CouiFloatingToolbarStyle(
    val dark: Boolean,
    val shape: Shape,
    val material: CouiFloatingMaterial,
    val shadow: CouiFloatingShadow,
    val contentColor: Color,
    val lightColor: Color,
    val contourGlowEnabled: Boolean,
)

internal val LocalCouiFloatingToolbarStyle = staticCompositionLocalOf<CouiFloatingToolbarStyle> {
    error("悬浮操作栏的按钮项必须位于 FloatingToolbar 内")
}

/**
 * 尺寸取自系统便签 17.0.30 的 `coui_floating_toolbar_*` 资源：
 * 栏高 44dp、项宽 44dp、项高 40dp、项间距 4dp、图标 24dp、两项以上时两侧各留 6dp。
 * 操作项按图标按钮处理：只承担点击与悬停，不各自绘制玻璃表面。
 * 组件不按位置或颜色区分操作项，多色图标与单色图标反馈完全一致。
 */
object CouiFloatingToolbarDefaults {
    /** `coui_floating_toolbar_menu_view_height`：胶囊本体高度。 */
    val height = 44.dp

    /** 不含底部手势条额外边距的基础工具区高度（上下各 16dp 悬浮留白加 44dp 胶囊高度）。 */
    val containerHeight = 76.dp

    /** `coui_floating_toolbar_item_width`。 */
    internal val itemSize = 44.dp

    /** `coui_floating_toolbar_item_height`：项比栏矮 4dp，上下各留 2dp。 */
    internal val itemHeight = 40.dp

    /** `coui_floating_toolbar_item_view_space`：删除时先扩到 1.75 倍，插入时先压到 0.25 倍再收拢。 */
    internal val itemSpacing = 4.dp

    /**
     * `coui_floating_toolbar_menu_view_item_bg_inset_horizontal`：胶囊边缘到首/末操作项的距离。
     * 项只在相邻一侧带半个间距，两端直接补这个值；单项时原厂取 0。
     */
    internal val edgeInset = 6.dp

    /** `coui_floating_toolbar_item_single_mask_inset_horizontal`：单项时蒙版左右各内缩 2dp。 */
    internal val singleItemFeedbackInset = 2.dp

    /** `coui_floating_toolbar_item_icon_width`：项内左右各留 10dp。 */
    internal val iconSize = 24.dp

    /** 系统便签悬浮操作栏材质预设的模糊半径，单位是设备像素。 */
    private const val FloatingToolbarBlurRadius = 25f

    /** 与底栏中间胶囊、搜索胶囊共用的胶囊轮廓。 */
    val capsuleShape: Shape = CouiCapsuleShape

    /** `containerColor` 未指定时使用玻璃材质；指定时同一绘制链改为不模糊的纯色底栏。 */
    @Composable
    fun style(
        dark: Boolean = CouiTheme.isDark,
        themeTint: Color = CouiTheme.materialTintFor(dark),
        containerColor: Color = Color.Unspecified,
    ): CouiFloatingToolbarStyle {
        val density = LocalDensity.current.density
        val contour = LocalCouiContourGlowEnabled.current
        val material = if (containerColor.isSpecified) {
            CouiFloatingMaterial(
                firstColor = containerColor,
                firstBlendMode = BlendMode.SRC_OVER,
                secondColor = Color.Transparent,
                secondBlendMode = BlendMode.SRC_OVER,
                edgeAlpha = 0f,
                innerGlowAlpha = 0f,
                blurRadius = 0f,
                strokeType = 1,
                themeTint = Color.Transparent,
            )
        } else {
            // 这里的 strokeType=1 是本库的胶囊几何标记；原厂 FRAMEWORK_CAPSULE_5 的描边值单独映射到宽度/透明度。
            CouiNavigationBarDefaults.material(dark, themeTint = themeTint).copy(
                edgeAlpha = if (dark) 0.8f else 0.2f,
                edgeWidthDp = if (dark) 2.5f else 2.3f,
                innerGlowAlpha = if (dark) 0.03f else 0.1f,
                blurRadius = FloatingToolbarBlurRadius,
                strokeType = 1,
            )
        }
        return CouiFloatingToolbarStyle(
            dark = dark,
            shape = capsuleShape,
            material = material,
            // 底栏共用投影分支：contour 开启时用 caustic，关闭时用普通投影。
            // 原厂 ShadowUtils 的 profile=2 映射到 level3b；保留底栏预设的日夜色和光源参数。
            shadow = CouiNavigationBarDefaults.transparentShadow(dark, density, contour, Color.White, capsule = true),
            contentColor = CouiTheme.colorSchemeFor(dark).onSurface,
            lightColor = Color.White,
            contourGlowEnabled = contour,
        )
    }
}
