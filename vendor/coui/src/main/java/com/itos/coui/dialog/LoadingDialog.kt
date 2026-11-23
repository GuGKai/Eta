package com.itos.coui.dialog

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.window.DialogProperties
import com.itos.coui.backdrop.CouiBackdrop
import com.itos.coui.theme.CouiTheme

private object LoadingDialogDefaults {
    val windowWidth = 180.dp
    val contentWidth = 152.dp
    val minHeight = 120.dp
    val horizontalPadding = 16.dp
    val verticalPadding = 28.dp
    val spacing = 12.dp
    val indicatorSize = 36.dp
    const val durationMillis = 1267
    const val lastKeyframe = 75f
    const val shortestArcKeyframe = 37f
    val trimFirst = CubicBezierEasing(.167f, .167f, .833f, 1f)
    val trimSecond = CubicBezierEasing(.167f, 0f, .833f, .833f)
}

/**
 * 居中显示循环加载动画和非空单行提示。将调用留在组合中，由 [visible] 控制窗口显隐。
 * 提供 [onDismissRequest] 时，返回键会请求宿主关闭。外点请求还需启用 [dismissOnClickOutside]。
 * 任务的完成和取消由宿主处理。
 * [material] 使用 API 33 及以上的窗口背景源，可显式传入 [backdrop]。
 */
@Composable
fun LoadingDialog(
    modifier: Modifier = Modifier,
    visible: Boolean,
    message: String,
    onDismissRequest: (() -> Unit)? = null,
    dark: Boolean = CouiTheme.isDark,
    colors: CouiDialogColors = CouiDialogDefaults.themeColors(dark),
    material: CouiDialogMaterial? = null,
    backdrop: CouiBackdrop? = null,
    dismissOnClickOutside: Boolean = false,
) {
    require(message.isNotBlank()) { "加载提示不能为空" }
    CouiDialogWindow(
        modifier = modifier,
        onDismissRequest = onDismissRequest ?: {},
        colors = colors,
        dark = dark,
        placement = CouiDialogPlacement.Center,
        properties = DialogProperties(
            dismissOnBackPress = onDismissRequest != null,
            dismissOnClickOutside = dismissOnClickOutside && onDismissRequest != null,
            usePlatformDefaultWidth = false,
        ),
        material = material,
        backdrop = backdrop,
        visible = visible,
        maxWidth = LoadingDialogDefaults.windowWidth,
        horizontalMargin = 0.dp,
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center)
                .widthIn(max = LoadingDialogDefaults.contentWidth)
                .fillMaxWidth()
                .heightIn(min = LoadingDialogDefaults.minHeight)
                .padding(horizontal = LoadingDialogDefaults.horizontalPadding, vertical = LoadingDialogDefaults.verticalPadding)
                .semantics(mergeDescendants = true) {
                    progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
                    liveRegion = LiveRegionMode.Polite
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(LoadingDialogDefaults.spacing, Alignment.CenterVertically),
        ) {
            LoadingIndicator(dark)
            BasicText(
                text = message,
                style = rememberDialogTextStyle(DialogTextKind.Loading).copy(color = colors.message, textAlign = TextAlign.Center),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LoadingIndicator(dark: Boolean) {
    val frame by rememberInfiniteTransition(label = "LoadingDialog").animateFloat(
        initialValue = 0f,
        targetValue = LoadingDialogDefaults.lastKeyframe + 1f,
        animationSpec = infiniteRepeatable(tween(LoadingDialogDefaults.durationMillis, easing = LinearEasing)),
        label = "Frame",
    )
    val color = if (dark) Color.White else Color.Black
    Canvas(Modifier.size(LoadingDialogDefaults.indicatorSize)) {
        val current = frame.coerceAtMost(LoadingDialogDefaults.lastKeyframe)
        val middle = LoadingDialogDefaults.shortestArcKeyframe
        val trim: Float
        val offset: Float
        if (current <= middle) {
            val progress = current / middle
            trim = lerp(.24f, .86f, LoadingDialogDefaults.trimFirst.transform(progress))
            offset = lerp(-90f, -41f, progress)
        } else {
            val progress = (current - middle) / (LoadingDialogDefaults.lastKeyframe - middle)
            trim = lerp(.86f, .24f, LoadingDialogDefaults.trimSecond.transform(progress))
            offset = lerp(-41f, 270f, progress)
        }
        // 合并原厂椭圆与两级旋转，Trim Paths 的起点和终点共用同一帧。
        val rotation = lerp(90f, 450f, current / LoadingDialogDefaults.lastKeyframe)
        val diameter = size.minDimension * (78f / 84f)
        drawArc(
            color = color,
            startAngle = rotation + offset + trim * 360f,
            sweepAngle = (1f - trim) * 360f,
            useCenter = false,
            topLeft = center - Offset(diameter / 2f, diameter / 2f),
            size = Size(diameter, diameter),
            alpha = 216f / 255f,
            style = Stroke(width = size.minDimension * (6f / 84f)),
        )
    }
}
