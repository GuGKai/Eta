package com.itos.coui.floatingtoolbar

import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.tappableElement
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.itos.coui.R
import com.itos.coui.backdrop.CouiBackdrop
import com.itos.coui.backdrop.LocalCouiBackdrop
import com.itos.coui.effect.CouiFloatingRenderer
import com.itos.coui.menu.CouiMenuAnchor
import com.itos.coui.menu.CouiMenuDefaults
import com.itos.coui.menu.CouiMenuItem
import com.itos.coui.menu.Menu
import com.itos.coui.menu.ProvideCouiMenuBackdrop
import com.itos.coui.menu.rememberCouiWindowBackdrop
import com.itos.coui.theme.CouiIcon
import com.itos.coui.theme.CouiTheme

/** value 是重排、进出场及点击共用的稳定业务标识。多色图标可指定 Color.Unspecified。 */
data class CouiFloatingToolbarItem<T : Any>(
    val value: T,
    val label: String,
    val icon: ImageVector,
    val enabled: Boolean = true,
    val iconTint: Color? = null,
)

private object OverflowKey

private class ToolbarAction(val item: State<CouiFloatingToolbarItem<*>>, val onClick: () -> Unit)

/** COUIResponsiveUtils 的工具组外边距。宽屏横置手机和较窄的竖屏折叠设备仍走中屏分支。 */
internal fun toolbarMarginDp(width: Float, height: Float): Int = when {
    width < 600f -> 16
    width < 840f || height < 480f -> 24
    height >= 900f && height > width && width < 960f -> 24
    else -> 40
}

internal fun toolbarDirectCount(count: Int, available: Int, itemWidth: Int, gap: Int): Int {
    var remaining = available
    for (index in 0 until count) {
        val preceding = if (index == 0) 0 else gap
        val needed = preceding + itemWidth
        if (index == count - 1) return if (needed <= remaining) count else index
        if (index >= 6 || needed + gap + itemWidth > remaining) return index
        remaining -= needed
    }
    return count
}

/**
 * 悬浮操作栏：按窗口可用宽度排列同级操作，剩余操作收进同一个“更多”菜单。
 * 组件内部已处理横向与底部 `WindowInsets.tappableElement` 安全区及悬浮留白；
 * 切换 [visible] 时保留在组合中以播放整栏缩放显隐动画，键盘避让由宿主容器负责。
 */
@RequiresApi(34)
@Composable
fun <T : Any> FloatingToolbar(
    modifier: Modifier = Modifier,
    items: List<CouiFloatingToolbarItem<T>>,
    onItemClick: (T) -> Unit,
    visible: Boolean = true,
    backdrop: CouiBackdrop = checkNotNull(LocalCouiBackdrop.current) { "悬浮操作栏需要页面背景源" },
    renderer: CouiFloatingRenderer = remember { CouiFloatingRenderer.standalone() },
    dark: Boolean = CouiTheme.isDark,
    style: CouiFloatingToolbarStyle = CouiFloatingToolbarDefaults.style(dark),
    themeTint: Color = style.material.themeTint,
) {
    require(items.distinctBy { it.value }.size == items.size) { "悬浮操作栏的 value 必须唯一" }
    val resolvedStyle = if (style.material.blurRadius > 0f && themeTint != style.material.themeTint) {
        style.copy(material = style.material.copy(themeTint = themeTint))
    } else style
    val density = LocalDensity.current
    val window = LocalWindowInfo.current.containerDpSize
    val bottomMargin = if (WindowInsets.tappableElement.getBottom(density) > 0) 0.dp else 8.dp
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.tappableElement.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .padding(top = 16.dp, bottom = 16.dp + bottomMargin),
        contentAlignment = Alignment.Center,
    ) {
        val marginPx = with(density) {
            toolbarMarginDp(window.width.value, window.height.value).dp.roundToPx()
        }
        val direct = with(density) {
            val inset = if (items.size > 1) CouiFloatingToolbarDefaults.edgeInset.roundToPx() else 0
            // 外层测量菜单时先加回菜单自身的透明 padding，两处相抵。
            val available = constraints.maxWidth - 2 * (marginPx + inset)
            toolbarDirectCount(items.size, available, CouiFloatingToolbarDefaults.itemSize.roundToPx(),
                CouiFloatingToolbarDefaults.itemSpacing.roundToPx())
        }
        val currentOnClick by rememberUpdatedState(onItemClick)
        // 连续显示的同一动作可更新内容。移除再添加会创建新绑定，退出批次仍持有旧绑定。
        val directItems = items.take(direct).map { item ->
            key(item.value) {
                val currentItem = rememberUpdatedState(item)
                remember { ToolbarAction(currentItem) { currentOnClick(currentItem.value.value) } }
            }
        }
        val overflow = items.drop(direct)
        val group = ToolbarGroup(buildList {
            addAll(directItems)
            if (overflow.isNotEmpty()) add(OverflowKey)
        }, items.size > 1)
        val animation = rememberToolbarAnimation(group)
        val batch = animation.batch
        SideEffect { animation.submit(group) }
        ToolbarSurface(Modifier.fillMaxWidth(), visible, backdrop, renderer, resolvedStyle, animation, batch) {
            for (id in batch.order) key(id) {
                val active = visible && id in batch.target && id in group.ids
                if (id is ToolbarAction) {
                    val item = id.item.value
                    ToolbarButton(Modifier.toolbarItem(id), id.onClick, item.enabled, active) {
                        CouiIcon(Modifier.size(CouiFloatingToolbarDefaults.iconSize), item.icon, item.label,
                            tint = if (item.enabled) item.iconTint ?: CouiTheme.contentColor else CouiTheme.contentColor)
                    }
                } else {
                    ToolbarOverflowButton(overflow, active, marginPx, backdrop, themeTint, onItemClick)
                }
            }
        }
    }
}

@Composable
private fun <T : Any> ToolbarOverflowButton(
    items: List<CouiFloatingToolbarItem<T>>,
    active: Boolean,
    marginPx: Int,
    backdrop: CouiBackdrop,
    themeTint: Color,
    onItemClick: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    var menuItems by remember { mutableStateOf(items) }
    if (items.isNotEmpty()) menuItems = items
    val label = stringResource(R.string.coui_toolbar_more)
    ToolbarButton(Modifier.toolbarItem(OverflowKey), { open = true }, true, active) {
        val color = CouiTheme.contentColor
        Canvas(Modifier.size(CouiFloatingToolbarDefaults.iconSize).semantics { contentDescription = label }) {
            val unit = size.width / 24f
            repeat(3) { index ->
                drawCircle(color, 1.75f * unit, Offset(12f * unit, (4.875f + index * 7.125f) * unit))
            }
        }
        val view = LocalView.current
        val anchor = remember(view, marginPx) { CouiMenuAnchor(view, editorButtonMarginPx = marginPx) }
        ProvideCouiMenuBackdrop(rememberCouiWindowBackdrop() ?: backdrop) {
            Menu(
                expanded = open && active,
                style = CouiMenuDefaults.style(LocalCouiFloatingToolbarStyle.current.dark),
                anchor = anchor,
                themeTint = themeTint,
                onDismissRequest = { open = false },
                items = menuItems.map {
                    CouiMenuItem(it.value, it.label, it.icon, enabled = it.enabled, iconTint = it.iconTint)
                },
                onItemClick = {
                    open = false
                    onItemClick(it.value)
                },
            )
        }
    }
    if (!active && open) SideEffect { open = false }
}
