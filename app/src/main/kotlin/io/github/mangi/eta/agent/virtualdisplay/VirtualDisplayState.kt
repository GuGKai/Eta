package io.github.mangi.eta.agent.virtualdisplay

import android.view.Display
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap

/**
 * 虚拟屏画布内的微距触控视觉反馈。
 * 坐标全部归一化到 [0f, 1f]，与画布当前渲染尺寸解耦，在微缩镜与画廊视窗中均可精确绘制。
 */
@Immutable
internal sealed interface VirtualTouchEffect {
    val sequence: Long
    val createdAtMs: Long

    @Immutable
    data class Tap(
        override val sequence: Long,
        override val createdAtMs: Long,
        val normalizedX: Float,
        val normalizedY: Float,
        val longPress: Boolean = false,
    ) : VirtualTouchEffect

    @Immutable
    data class Swipe(
        override val sequence: Long,
        override val createdAtMs: Long,
        val startX: Float,
        val startY: Float,
        val endX: Float,
        val endY: Float,
        val durationMs: Int = 420,
    ) : VirtualTouchEffect
}

/**
 * 破壁过渡的起手矩形（物理屏幕像素，含状态栏坐标系）。
 *
 * 过渡层的第一帧必须和画板最后一帧完全重合，放大才有连续感；
 * 所以起手位置不能靠猜比例，得按画板在屏幕上的真实矩形算出来。
 */
@Immutable
internal data class VirtualStageBounds(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
) {
    val centerX: Float get() = left + width / 2f
    val centerY: Float get() = top + height / 2f
}

/**
 * 虚拟屏实时状态快照，供顶部胶囊微缩镜、画廊装裱视窗与破壁全屏过渡共享订阅。
 */
@Immutable
internal data class VirtualDisplaySnapshot(
    val active: Boolean = false,
    val displayId: Int = Display.DEFAULT_DISPLAY,
    val width: Int = 1080,
    val height: Int = 2400,
    val appName: String = "",
    val packageName: String = "",
    /** 用户是否已在画廊视窗内开启手动触控接管。 */
    val userControlling: Boolean = false,
    /** 是否正在执行从虚拟屏到物理主屏的破壁全屏放大动画。 */
    val materializingToMain: Boolean = false,
    /** 放大到位、主屏已接管：过渡层进入收尾淡出。 */
    val materializeFading: Boolean = false,
    /** 过渡层起手矩形（画板在屏幕上的实际位置）。 */
    val materializeFrom: VirtualStageBounds? = null,
    /**
     * 任务已全屏搬到物理主屏、原虚拟屏仍完整保留：
     * 用户离开该应用时把同一个 RootTask 原位搬回，胶囊小窗随之恢复。
     */
    val externalizedToMain: Boolean = false,
    /** 最新采集的画面帧。 */
    val frame: ImageBitmap? = null,
    val frameSequence: Long = 0L,
    /** 最近一次在虚拟屏内发生的手势光效。 */
    val touchEffect: VirtualTouchEffect? = null,
) {
    val aspectRatio: Float
        get() = if (width > 0 && height > 0) {
            (width.toFloat() / height.toFloat()).coerceIn(0.38f, 0.75f)
        } else {
            9f / 19.5f
        }

    companion object {
        val Inactive = VirtualDisplaySnapshot()
    }
}
