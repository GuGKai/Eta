package com.itos.coui.navigation

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.itos.coui.effect.CouiFloatingMaterial
import com.itos.coui.effect.CouiFloatingShadow
import com.itos.coui.effect.SpotlightPreset
import com.itos.coui.floatingactionbutton.CouiFabDefaults
import com.itos.coui.shape.CouiShape

/** 保留配置并切换 [visible]，可在收起动画中保留图标和样式。 */
data class CouiNavigationBarAction(
    val icon: ImageVector,
    val contentDescription: String,
    val onClick: (() -> Unit)? = null,
    val visible: Boolean = true,
    val style: CouiNavigationBarActionStyle? = null,
)

/** 单侧附件的外观。形状同时用于材质、融合轮廓和内容裁剪。 */
data class CouiNavigationBarActionStyle(
    val material: CouiFloatingMaterial,
    val shadow: CouiFloatingShadow,
    val contentColor: Color,
    val shape: CouiShape = CouiFabDefaults.circleShape,
    val spotlight: SpotlightPreset = SpotlightPreset.OpaqueSmall2,
    val hoverFeedback: Boolean = false,
)
