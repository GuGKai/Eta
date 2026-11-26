package com.itos.coui.floatingtoolbar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.ParentDataModifier
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import kotlin.math.roundToInt

private class ToolbarItemData(val id: Any)

internal fun Modifier.toolbarItem(id: Any): Modifier =
    then(object : ParentDataModifier {
        override fun Density.modifyParentData(parentData: Any?): Any = ToolbarItemData(id)
    })

private class ToolbarWidths {
    val measured = mutableMapOf<Any, Int>()
    var before: Map<Any, Int> = emptyMap()
        private set
    private var batch: ToolbarItems? = null

    fun begin(next: ToolbarItems) {
        if (batch === next) return
        batch = next
        // 同一批的起点保持不变，后续帧只更新当帧测量值。
        before = if (next.from == next.to) emptyMap() else measured.toMap()
    }
}

@Composable
internal fun ToolbarLayout(
    modifier: Modifier,
    animation: ToolbarAnimation,
    batch: ToolbarItems,
    onGeometry: (Float, Int) -> Unit,
    content: @Composable () -> Unit,
) {
    val widths = remember { ToolbarWidths() }
    Layout(modifier = modifier, content = content) { measurables, constraints ->
        val ids = measurables.map { (it.parentData as? ToolbarItemData)?.id ?: it }
        widths.begin(batch)
        val progress = animation.fraction(batch)
        val defaults = CouiFloatingToolbarDefaults
        val spacing = defaults.itemSpacing.roundToPx()
        val inset = defaults.edgeInset.roundToPx()
        val height = constraints.constrainHeight(defaults.height.roundToPx())
        val childConstraints = Constraints(maxWidth = constraints.maxWidth, maxHeight = height)
        val children = Array(ids.size) { index ->
            val id = ids[index]
            val previous = if (id !in batch.target && id in batch.old) {
                widths.before[id]?.coerceAtMost(constraints.maxWidth)
            } else null
            measurables[index].measure(
                childConstraints.copy(minWidth = previous ?: 0, maxWidth = previous ?: constraints.maxWidth),
            ).also { placeable ->
                widths.measured[id] = placeable.width
            }
        }
        if (batch.from == batch.to) widths.measured.keys.retainAll(ids.toSet())
        val frame = batch.frame(
            widths.measured, spacing, inset, progress, animation.gapFraction(batch),
            layoutDirection == LayoutDirection.Rtl,
        )
        val width = constraints.constrainWidth(frame.width.roundToInt())
        val constrainedWidth = frame.width.coerceIn(
            constraints.minWidth.toFloat(),
            constraints.maxWidth.toFloat(),
        )
        onGeometry(constrainedWidth, height)
        layout(width, height) {
            for ((index, id) in ids.withIndex()) {
                val position = frame.items[id] ?: continue
                if (position.scale <= 0f) continue
                val child = children[index]
                val x = position.x
                val y = (height - child.height) / 2
                val left = x.roundToInt()
                child.placeWithLayer(left, y) {
                    translationX = x - left
                    scaleX = position.scale
                    scaleY = position.scale
                    alpha = position.scale.coerceAtMost(1f)
                }
            }
        }
    }
}
