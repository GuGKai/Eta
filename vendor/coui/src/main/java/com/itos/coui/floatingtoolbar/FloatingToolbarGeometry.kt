package com.itos.coui.floatingtoolbar

import kotlin.math.roundToInt

/** 原厂按收纳前的可见项数决定背景两端的留白，哪怕窄屏上只放得下“更多”。 */
internal data class ToolbarGroup(val ids: List<Any>, val hasInset: Boolean)

/** 编辑器提交一批增删后，退出的项仍紧跟它在旧列表中的前一项。 */
internal class ToolbarItems(val from: ToolbarGroup, val to: ToolbarGroup) {
    val old = from.ids
    val target = to.ids
    val order = if (old == target) target else target.toMutableList().apply {
        var previous: Any? = null
        for (id in old) {
            if (id !in target) add(indexOf(previous) + 1, id)
            previous = id
        }
    }
}

internal data class ToolbarPlacement(val x: Float, val scale: Float)
internal class ToolbarFrame(val width: Float, val items: Map<Any, ToolbarPlacement>)

/** ItemViewAnimatorImpl 的逐项位置和材质边界，坐标原点取胶囊的可见左边。 */
internal fun ToolbarItems.frame(
    widths: Map<Any, Int>,
    spacing: Int,
    inset: Int,
    progress: Float,
    gap: Float,
    rtl: Boolean,
): ToolbarFrame {
    if (order.isEmpty()) return ToolbarFrame(0f, emptyMap())
    fun edge(group: ToolbarGroup) = if (group.hasInset) inset else 0
    fun width(group: ToolbarGroup) = group.ids.sumOf { widths.getValue(it) } +
        (group.ids.size - 1).coerceAtLeast(0) * spacing + edge(group) * 2
    val signedGap = if (width(from) > width(to)) gap else -gap
    val step = spacing * (1f + signedGap)
    val bounded = progress.coerceIn(0f, 1f)
    val edge = (edge(from) + (edge(to) - edge(from)) * bounded).roundToInt()
    val ordered = if (rtl) order.asReversed() else order
    val before = if (rtl) old.asReversed() else old
    val after = if (rtl) target.asReversed() else target
    val firstChanged = before.firstOrNull() != after.firstOrNull()
    val lastChanged = before.lastOrNull() != after.lastOrNull()
    var advance = edge - spacing * signedGap * (old.size - 1) / 2
    var left = 0f
    var right = 0f
    val placements = LinkedHashMap<Any, ToolbarPlacement>(order.size)
    for ((index, id) in ordered.withIndex()) {
        val w = widths.getValue(id)
        val raw = when {
            id !in old -> progress
            id !in target -> 1f - progress
            else -> 1f
        }
        val scale = raw.coerceAtLeast(0f)
        var leading = if (raw < 0f) 5f * raw * step else scale * step / 2
        var trailing = leading
        when {
            firstChanged && id == after.firstOrNull() -> leading *= 1f - bounded
            firstChanged && id == before.firstOrNull() -> leading *= bounded
        }
        when {
            lastChanged && id == after.lastOrNull() -> trailing *= 1f - bounded
            lastChanged && id == before.lastOrNull() -> trailing *= bounded
        }
        var visualX = advance + leading
        if (index == 0) visualX -= leading
        val pivotOffset = (1f - scale) * w / 2
        var x = visualX - pivotOffset
        if (index == ordered.lastIndex) {
            if (raw < 0f) x += trailing
            right = (if (scale <= 0f) x + pivotOffset else x + w - pivotOffset) + edge
        }
        if (index == 0) left = x + pivotOffset.coerceAtLeast(0f) - edge
        placements[id] = ToolbarPlacement(x - left, scale)
        advance = scale * w + visualX + trailing
    }
    return ToolbarFrame((right - left).coerceAtLeast(0f), placements)
}
