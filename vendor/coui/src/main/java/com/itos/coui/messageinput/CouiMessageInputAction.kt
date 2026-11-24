package com.itos.coui.messageinput

import androidx.compose.ui.graphics.painter.Painter

/** 输入栏操作，由宿主提供图标、本地化操作说明、点击事件和启用状态。 */
data class CouiMessageInputAction(
    val icon: Painter,
    val contentDescription: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
)
