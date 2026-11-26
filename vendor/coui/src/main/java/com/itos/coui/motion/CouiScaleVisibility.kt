package com.itos.coui.motion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import com.itos.coui.effect.rememberCouiMaterialSettings

/**
 * 为同一绘制主体提供显隐动效，配合 [Modifier.couiScaleVisibility] 或在自绘绘制链直接读取
 * [CouiScaleVisibilityState.scale]、[CouiScaleVisibilityState.alpha] 和 [CouiScaleVisibilityState.foregroundBlurEffect]。
 * 交互和触发时机由宿主按 [visible] 控制。
 */
@Composable
fun rememberCouiScaleVisibility(visible: Boolean): CouiScaleVisibilityState {
    val blurEnabled = rememberCouiMaterialSettings().blur
    val state = remember { CouiScaleVisibilityState(visible, blurEnabled) }
    DisposableEffect(state) {
        onDispose { state.dispose() }
    }
    // 翻译页在 550 ms 的原文输入转场首帧收起底栏，在 350 ms 的返回转场剩余不足 180 ms 时才显示。
    // 这段等待属于页面编排，显隐弹簧收到目标后立即启动。
    SideEffect { state.updateTarget(visible, blurEnabled) }
    return state
}

/** 对主体图层同时应用居中缩放、透明度和系统材质允许的前景模糊。 */
fun Modifier.couiScaleVisibility(state: CouiScaleVisibilityState): Modifier =
    graphicsLayer {
        val scale = state.scale.value
        scaleX = scale
        scaleY = scale
        alpha = state.alpha.value
        renderEffect = state.foregroundBlurEffect
    }

/** 同一主体的缩放、透明度、前景模糊与退场保留状态。 */
@Stable
class CouiScaleVisibilityState internal constructor(initialVisible: Boolean, initialBlurEnabled: Boolean) {
    private val scaleSpring = CouiSpring(if (initialVisible) 1f else HiddenScale, MinimumVisibleChange)
    private val alphaSpring = CouiSpring(if (initialVisible) 1f else 0f, MinimumVisibleChange)
    private val blurSpring = CouiSpring(
        if (!initialVisible && initialBlurEnabled) HiddenBlurRadiusPx else 0f, MinimumVisibleChange,
    )
    private var targetVisible = initialVisible
    private var blurEnabled = initialBlurEnabled

    val scale: State<Float> get() = scaleSpring.state
    val alpha: State<Float> get() = alphaSpring.state

    /** 应用于主体前景图层，清晰时为 null。独立绘制的前景图标可共用同一效果。 */
    val foregroundBlurEffect: RenderEffect? by derivedStateOf {
        val radius = blurSpring.value
        if (radius > 0f) BlurEffect(null, radius, radius, TileMode.Clamp) else null
    }

    /** 从显示开始到所有退场弹簧结束期间保留内容。 */
    var isPresent by mutableStateOf(initialVisible)
        private set

    internal fun updateTarget(visible: Boolean, systemBlurEnabled: Boolean) {
        if (visible == targetVisible && systemBlurEnabled == blurEnabled) return
        if (visible != targetVisible) {
            targetVisible = visible
            isPresent = true
            alphaSpring.animateTo(if (visible) 1f else 0f, response = AlphaResponse, onEnd = ::finishIfHidden)
            scaleSpring.animateTo(if (visible) 1f else HiddenScale, response = ScaleResponse,
                bounce = ScaleBounce, onEnd = ::finishIfHidden)
        }
        blurEnabled = systemBlurEnabled
        val targetRadius = if (blurEnabled && !visible) HiddenBlurRadiusPx else 0f
        if (blurEnabled && isPresent && (blurSpring.isRunning || blurSpring.value != targetRadius)) {
            blurSpring.animateTo(targetRadius, response = BlurResponse, onEnd = ::finishIfHidden)
        } else {
            blurSpring.snapTo(targetRadius)
            finishIfHidden()
        }
    }

    internal fun dispose() {
        scaleSpring.cancel()
        alphaSpring.cancel()
        blurSpring.cancel()
    }

    private fun finishIfHidden() {
        if (!targetVisible && !alphaSpring.isRunning && !scaleSpring.isRunning && !blurSpring.isRunning) {
            isPresent = false
        }
    }

    private companion object {
        const val HiddenScale = 0.8f
        const val ScaleResponse = 0.35f
        const val ScaleBounce = 0.35f
        const val AlphaResponse = 0.2f
        const val BlurResponse = 0.3f
        const val HiddenBlurRadiusPx = 30f
        // 原厂将输入放大 10000 倍交给 FloatValueHolder；换回实际倍率、透明度和像素单位。
        const val MinimumVisibleChange = 1f / 10000f
    }
}
