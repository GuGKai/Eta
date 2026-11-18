package io.github.mangi.eta.ui.components

import android.content.res.Resources
import android.view.RoundedCorner
import android.view.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp

/**
 * 设备屏幕玻璃的圆角半径。
 *
 * 侧栏把会话页推开后，会话页贴着分界的那一侧要收出和机身屏幕同款的圆角，
 * 看起来才像同一块屏幕被滑开。优先读窗口圆角（跟随当前显示的实际尺寸），
 * 读不到再退回框架里的圆角 dimen；两者都没有时按 0 处理，不硬造圆角。
 */
@Composable
internal fun rememberScreenCornerRadius(): Dp {
    val view = LocalView.current
    val density = LocalDensity.current
    return remember(view, density) {
        val radiusPx = view.rootWindowInsets?.roundedCornerRadiusPx()
            ?: systemScreenCornerRadiusPx()
            ?: 0
        with(density) { radiusPx.toDp() }
    }
}

/** 取窗口四角里最大的有效圆角；虚拟屏等无圆角的显示会返回 null。 */
private fun WindowInsets.roundedCornerRadiusPx(): Int? = listOf(
    RoundedCorner.POSITION_TOP_LEFT,
    RoundedCorner.POSITION_TOP_RIGHT,
    RoundedCorner.POSITION_BOTTOM_LEFT,
    RoundedCorner.POSITION_BOTTOM_RIGHT,
).mapNotNull { position -> getRoundedCorner(position)?.radius }
    .filter { it > 0 }
    .maxOrNull()

/** 框架里的圆角 dimen，ColorOS 等 ROM 通过 overlay 覆盖成机型实际值。 */
private fun systemScreenCornerRadiusPx(): Int? {
    val resources = Resources.getSystem()
    val identifier = resources.getIdentifier("rounded_corner_radius", "dimen", "android")
    if (identifier == 0) return null
    return resources.getDimensionPixelSize(identifier).takeIf { it > 0 }
}
