package com.itos.coui.sheet

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Velocity
import com.itos.coui.motion.CouiSpring
import kotlin.math.abs

/** 普通底部窗口的程序入退场与 Behavior 手势收尾，共用现有弹簧后端。 */
internal class CouiSheetMotion {
    var offset by mutableFloatStateOf(0f)
        private set
    var dim by mutableFloatStateOf(0f)
        private set
    var entered by mutableStateOf(false)
        private set
    var height by mutableFloatStateOf(0f)
        private set
    private var parentHeight = 0f
    private var windowHeight = 0f
    private var imeHeight = 0f
    private var peek = 0f
    private var target: Boolean? = null
    private var dragging = false
    private var settlingFromDrag = false
    private var softenReturn = false
    private var lastNestedDelta: Float? = null
    val hasNestedDrag get() = lastNestedDelta != null
    private var dismissVelocity: Float? = null
    var onHidden: () -> Unit = {}
    private val hideDistance: Float
        // 可见高度不含原厂为绘制扩展的底部 padding；程序入场只移动这一段。
        get() = height.coerceAtLeast(1f)
    private val gestureHideDistance: Float
        // Behavior 的隐藏 top 在根窗口坐标高度，而展开 top 相对 Coordinator。
        get() = hideDistance + windowHeight - parentHeight
    private val translation = CouiSpring(0f)
    private val alpha = CouiSpring(0f, minimumChange = .001f)

    fun update(visible: Boolean, height: Float, parentHeight: Float, windowHeight: Float, imeHeight: Float, peek: Float) {
        if (height <= 0f || parentHeight <= 0f) {
            // IME 可占满可用高度；没有可见几何时仍必须结束隐藏并释放窗口。
            dispose()
            this.height = 0f
            target = visible
            offset = 0f
            dim = if (visible) 1f else 0f
            entered = visible
            if (!visible) onHidden()
            return
        }
        val resized = this.height != height || this.parentHeight != parentHeight ||
            this.windowHeight != windowHeight || this.imeHeight != imeHeight || this.peek != peek
        val first = this.height == 0f
        this.height = height
        this.parentHeight = parentHeight
        this.windowHeight = windowHeight
        this.imeHeight = imeHeight
        this.peek = peek
        if (first) { offset = hideDistance; entered = false }
        val redirected = target != visible
        if (redirected || (resized && !dragging)) {
            dragging = false
            lastNestedDelta = null
            target = visible
            if (!visible) entered = false
            val velocity = if (!visible) dismissVelocity else null
            val fromDrag = if (redirected) velocity != null else settlingFromDrag
            dismissVelocity = null
            settle(visible, fromDrag, velocity)
        } else if (resized && dragging) {
            offset = if (hasNestedDrag) offset.coerceAtLeast(0f) else offset.coerceIn(0f, hideDistance)
            dim = dragDim()
        }
    }

    fun drag(delta: Float, nested: Boolean = false, velocity: Float = 0f): Float {
        if (target != true) return 0f
        val current = if (nested) offset.coerceAtLeast(0f) else offset.coerceIn(0f, hideDistance)
        // ViewDragHelper 每次 MOVE 先截断坐标差；不能跨事件累积这部分小数。
        val dragDelta = if (nested) delta else delta.toInt().toFloat()
        val movement = when {
            nested && dragDelta > 100f -> (dragDelta * .5f).toInt().toFloat()
            !nested && current > 0f && velocity > 10000f -> (dragDelta * .5f + .5f).toInt().toFloat()
            else -> dragDelta
        }
        val next = if (nested) (current + movement).coerceAtLeast(0f) else (current + movement).coerceIn(0f, hideDistance)
        // Behavior 对快速嵌套下降消费完整输入，只把实际位移减半；hideable 的嵌套路径没有底界夹取。
        val consumed = if (nested && dragDelta > 0f) dragDelta else next - current
        if (consumed == 0f) {
            // 到达边界后的下一次 MOVE 仍刷新旧 top 遮罩，但不重新接管运动通道。
            if (dragging && dragDelta != 0f) dim = dragDim()
            return 0f
        }
        // 手势接管后，旧目标、速度及尚未接受的退出请求均不再属于本次输入。
        if (!dragging) {
            translation.cancelComplete()
            alpha.cancelComplete()
            dismissVelocity = null
            dragging = true
        }
        lastNestedDelta = if (nested) dragDelta else null
        // 原厂先用移动前的 viewTop 更新遮罩，再放置面板。
        dim = dragDim()
        offset = next
        return consumed
    }

    /** ViewDragHelper 只在纵向速度占主导或面板越过中线时请求隐藏。 */
    fun release(velocity: Velocity): Boolean {
        val hide = offset > 0f && velocity.y >= 0f &&
            ((velocity.y > 500f && velocity.y > abs(velocity.x)) || offset > hideDistance * .5f)
        return endDrag(velocity.y, hide)
    }

    /** skipCollapsed 的嵌套释放按面板最后实际消费的方向判定。 */
    fun releaseNested(velocity: Float): Boolean {
        val lastDelta = lastNestedDelta ?: return false
        return endDrag(velocity, offset > 0f && lastDelta > 0f)
    }

    fun cancelDrag() {
        endDrag(0f, hide = false)
    }

    private fun dragDim(): Float = (1f - offset / parentHeight.coerceAtLeast(1f)).coerceIn(0f, 1f)

    private fun endDrag(velocity: Float, hide: Boolean): Boolean {
        if (!dragging) return false
        dragging = false
        lastNestedDelta = null
        // 关闭请求更新显示目标；等待确认期间先按 Behavior 的展开分支复位。
        dismissVelocity = if (hide) velocity.coerceAtLeast(0f) else null
        if (offset == 0f) {
            // 原厂 settleCapturedViewAt 在当前位置已经是目标时直接结束，不把抬手速度带进弹簧。
            translation.snapTo(0f)
            alpha.snapTo(1f)
            dim = 1f
            settlingFromDrag = false
            softenReturn = false
            return false
        }
        settle(show = true, fromDrag = true, velocity = if (hide) 0f else velocity)
        return hide
    }

    private fun programResponse(show: Boolean, distance: Float): Float {
        val range = (parentHeight - peek).coerceAtLeast(0f)
        val ratio = if (range > 0f) (distance - peek).coerceAtLeast(0f) / range else 1f
        return if (show) .25f + .20f * ratio else .18f + .19f * ratio
    }

    private fun settle(show: Boolean, fromDrag: Boolean, velocity: Float? = null) {
        settlingFromDrag = fromDrag
        val returning = fromDrag && show
        if (returning && velocity != null) softenReturn = velocity < -100f && offset > 0f
        else if (!returning) softenReturn = false
        val response = when {
            returning -> .4f
            fromDrag -> .18f + .19f * (hideDistance - offset).coerceAtLeast(0f) / parentHeight.coerceAtLeast(1f)
            else -> programResponse(show, hideDistance)
        }
        val targetOffset = when {
            show -> 0f
            fromDrag -> gestureHideDistance
            else -> hideDistance + imeHeight + 1f
        }
        if (!translation.isRunning) translation.snapTo(offset)
        translation.animateTo(targetOffset, response, velocity = velocity,
            onFrame = { value ->
                // Behavior 以整数 top 放置；上甩越过展开位置时只显示 40% 的超越量。
                if (returning) dim = dragDim()
                offset = if (fromDrag) {
                    val expandedTop = (parentHeight - hideDistance).coerceAtLeast(0f)
                    val top = (expandedTop + value).toInt()
                    val position = if (returning && softenReturn && top < expandedTop) {
                        (expandedTop - (expandedTop - top) * .4f).toInt()
                    } else top
                    position - expandedTop
                } else value.coerceAtLeast(0f)
            },
            onEnd = {
                if (!dragging) {
                    settlingFromDrag = false
                    alpha.snapTo(if (target == true) 1f else 0f)
                    dim = if (target == true) 1f else 0f
                    if (target == true) { entered = true; dismissVelocity = null }
                    else if (target == false) onHidden()
                }
            })
        if (returning) {
            alpha.cancelComplete()
        } else {
            // 手势隐藏由 PullUpToDismissPanelListener 另起 Dialog alpha，不再使用拖动位置 alpha。
            val alphaResponse = if (fromDrag) programResponse(false, gestureHideDistance - offset) else response
            if (!alpha.isRunning) alpha.snapTo(dim)
            alpha.animateTo(if (show) 1f else 0f, alphaResponse, minimumChange = 1f / hideDistance,
                onFrame = { dim = it.coerceIn(0f, 1f) })
        }
    }

    fun dispose() {
        dragging = false
        lastNestedDelta = null
        dismissVelocity = null
        settlingFromDrag = false
        softenReturn = false
        translation.cancelComplete()
        alpha.cancelComplete()
    }
}
