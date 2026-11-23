package com.itos.coui.topappbar

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import com.itos.coui.effect.CouiContourStyle
import com.itos.coui.effect.CouiFloatingMaterial
import com.itos.coui.effect.CouiFloatingShadow

data class CouiToolbarStyle(
    val dark: Boolean,
    val circleShape: Shape,
    val capsuleShape: Shape,
    val material: (capsule: Boolean) -> CouiFloatingMaterial,
    val shadow: (capsule: Boolean) -> CouiFloatingShadow,
    val contentColor: Color,
    val textStyle: TextStyle,
    val cursorColor: Color,
    val contourGlowEnabled: Boolean,
    /** 沿用 TopAppBarButton 的圆形/胶囊几何；null 使用浮动轮廓，Drawable 预设（不含 Menu）在材质与内容之间合成。 */
    val contourStyle: ((capsule: Boolean) -> CouiContourStyle)? = null,
)

internal val LocalCouiToolbarStyle = staticCompositionLocalOf<CouiToolbarStyle> {
    error("顶栏样式必须由组件入口提供")
}
