package com.itos.coui.menu

import android.view.View
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.IntRect

/** 边界属于提供它的窗口；独立 Dialog 继承 Local 时不能复用外层窗口坐标。 */
internal class CouiMenuVisibleBounds(private val view: View, private val bounds: () -> IntRect?) {
    fun boundsInWindow(view: View): IntRect? =
        if (this.view.rootView === view.rootView) bounds() else null
}

/** 稳定读取函数避免把逐帧边界广播给整棵内容树。 */
internal val LocalCouiMenuVisibleBounds = staticCompositionLocalOf<CouiMenuVisibleBounds?> { null }
