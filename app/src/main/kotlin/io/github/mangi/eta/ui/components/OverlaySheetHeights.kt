package io.github.mangi.eta.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp

/**
 * 底部面板正文的高度上限比例。
 *
 * 面板一律按内容自适应：内容短面板就矮，内容长最多撑到这个比例为止。
 * 早先几个面板把屏高写死成 0.60 左右，长列表、长文本被压成一小截，
 * 因此统一改成「内容决定高度 + 宽松上限」。
 */
internal const val OverlaySheetMaxHeightFraction = 0.85f

/** 当前窗口高度 × [fraction]，用作底部面板正文的最大高度；旋转、分屏时自动重新取值。 */
@Composable
internal fun rememberOverlaySheetMaxHeight(fraction: Float = OverlaySheetMaxHeightFraction): Dp {
    val density = LocalDensity.current
    val containerHeightPx = LocalWindowInfo.current.containerSize.height
    return with(density) { containerHeightPx.toDp() } * fraction
}
