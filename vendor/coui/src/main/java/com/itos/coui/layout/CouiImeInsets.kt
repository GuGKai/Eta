package com.itos.coui.layout

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContextWrapper
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.DialogWindowProvider
import java.util.WeakHashMap

/**
 * 当前窗口的底部 IME 占位，长度由 WindowInsets 的像素接口提供。
 *
 * 在测量阶段随系统键盘动画读取当前高度；窗口失焦时只允许占位减小。
 * 键盘收起后释放占位，其他窗口的键盘不能抬高本页；重新获焦后读取当前 IME。
 * 宿主通过 Modifier.windowInsetsPadding 消费，可用于页面容器或底部组件。
 * Activity 在使用期间采用 adjustResize，同窗最后一个使用者退出时恢复原调整位和根 View 的滚动容器标记。
 * Compose Dialog 自行管理窗口模式，页面使用该弹窗收到的 IME Insets。
 * 宿主使用边到边布局，导航栏由宿主另行处理。无法取得所属窗口时抛出 IllegalStateException。
 */
@Composable
fun rememberCouiImeInsets(): WindowInsets {
    ConfigureCouiImeWindow()
    val ime = WindowInsets.ime
    val windowInfo = LocalWindowInfo.current
    return remember(ime, windowInfo) { FocusRetainedImeInsets(ime, windowInfo) }
}

@Composable
internal fun ConfigureCouiImeWindow() {
    val view = LocalView.current
    val window = remember(view) {
        // 弹窗或其嵌套 ComposeView 沿用 DialogProperties 管理的窗口模式。
        if (generateSequence(view) { it.parent as? View }.any { it is DialogWindowProvider }) return@remember null
        var context = view.context
        while (context is ContextWrapper && context !is Activity) context = context.baseContext
        checkNotNull((context as? Activity)?.window) { "IME 避让需要 Activity 或 Compose Dialog 窗口" }
    } ?: return
    DisposableEffect(window) {
        val decorView = window.decorView
        // 输入栏与页面占位可共享同一窗口，最后一个使用者退出时才恢复配置。
        val adjustment = imeWindowAdjustments.getOrPut(window) {
            val original = ImeWindowAdjustment(window.attributes.softInputMode and
                WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST, decorView.isScrollContainer)
            // ViewRootImpl 在首次测量后仍会按初始模式和容器标记选择 resize/pan。
            decorView.isScrollContainer = true
            @Suppress("DEPRECATION")
            window.setImeAdjustment(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            original
        }
        adjustment.users++
        onDispose {
            adjustment.users--
            if (adjustment.users == 0) {
                decorView.isScrollContainer = adjustment.originalScrollContainer
                window.setImeAdjustment(adjustment.originalAdjustment)
                imeWindowAdjustments.remove(window)
            }
        }
    }
}

private val imeWindowAdjustments = WeakHashMap<Window, ImeWindowAdjustment>()

// 反向掩码只用于保留窗口的其他位，调整位取自系统常量或原窗口属性。
@SuppressLint("WrongConstant")
private fun Window.setImeAdjustment(adjustment: Int) {
    // setSoftInputMode(0) 仅清除显式配置标记。写回 LayoutParams 才能恢复零值。
    val params = attributes
    params.softInputMode = (params.softInputMode and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST.inv()) or adjustment
    attributes = params
}

private class ImeWindowAdjustment(val originalAdjustment: Int, val originalScrollContainer: Boolean) {
    var users = 0
}

private class FocusRetainedImeInsets(private val ime: WindowInsets, private val windowInfo: WindowInfo) : WindowInsets {
    private var bottom = 0

    override fun getLeft(density: Density, layoutDirection: LayoutDirection): Int = 0
    override fun getTop(density: Density): Int = 0
    override fun getRight(density: Density, layoutDirection: LayoutDirection): Int = 0

    override fun getBottom(density: Density): Int {
        // 失焦后仍读取 IME，保持测量对收起动画的订阅；其他窗口只不能增加本页占位。
        val currentBottom = ime.getBottom(density)
        bottom = if (windowInfo.isWindowFocused) currentBottom else minOf(bottom, currentBottom)
        return bottom
    }
}
