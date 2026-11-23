package com.itos.coui.messageinput

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.MutableWindowInsets
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.onConsumedWindowInsetsChanged
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.itos.coui.backdrop.CouiBackdrop
import com.itos.coui.backdrop.couiBackdrop
import com.itos.coui.backdrop.rememberCouiBackdrop

/**
 * 输入栏内部的全宽遮罩装配，颜色由输入栏的同一份主题与材质状态决定。
 * content 的尺寸决定容器高度，输入栏留白位于 content 内。
 * 容器应用并消费剩余的底部导航栏占位，IME 避让仍由宿主页处理。
 * 遮罩单独录制并叠加 backdrop，供内部输入栏采样；不录制输入栏或其阴影。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MessageInputBarSurface(
    modifier: Modifier,
    backdrop: CouiBackdrop,
    colors: List<Color>,
    content: @Composable (CouiBackdrop) -> Unit,
) {
    val navigation = WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)
    val remaining = remember(navigation) { MutableWindowInsets(navigation) }
    val surfaceBackdrop = rememberCouiBackdrop(parent = backdrop)
    Box(modifier.fillMaxWidth().onConsumedWindowInsetsChanged { remaining.insets = navigation.exclude(it) }) {
        Spacer(Modifier.matchParentSize().couiBackdrop(surfaceBackdrop).drawWithCache {
            val gap = remaining.getBottom(this).toFloat().coerceAtMost(size.height)
            val bottom = size.height - gap
            val top = CouiMessageInputBarDefaults.surfaceGradientTop.toPx().coerceAtMost(bottom)
            val gradient = Brush.verticalGradient(colors, startY = top, endY = bottom)
            onDrawBehind {
                if (bottom > top) drawRect(gradient, topLeft = Offset(0f, top), size = Size(size.width, bottom - top))
                if (gap > 0f) drawRect(colors.last(), topLeft = Offset(0f, bottom), size = Size(size.width, gap))
            }
        })
        Box(Modifier.windowInsetsPadding(navigation)) {
            content(surfaceBackdrop)
        }
    }
}
