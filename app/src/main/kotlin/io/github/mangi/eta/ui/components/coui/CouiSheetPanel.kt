package io.github.mangi.eta.ui.components.coui

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.itos.coui.sheet.BottomSheet
import com.itos.coui.sheet.CouiSheetButton
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text as CouiText
import com.itos.coui.theme.CouiTheme

/**
 * COUI 底部 Sheet 面板的统一外壳：居中标题 + 顶部拖动条，不放操作按钮，
 * 靠下拉、点遮罩或系统返回关闭。用于承载列表、表单这类 Dialog 放不下的长内容。
 *
 * 与 `Dialog` 的两点差异由这里统一抹平，调用方仍然只传 `visible: Boolean`：
 * 1. 库的 `visible` 是宿主持有的 `MutableState<Boolean>`，这里同步内部副本；
 * 2. 面板必须常驻组合才能播完退场动画，所以关闭一律走 [onDismissRequest]，
 *    由调用方把外部状态置为 false，而不是让库自己改 state（否则外部状态不同步、面板会关不掉）。
 *
 * 材质与窗口背景源走库的默认值：API 33+ 且外层已装 `ProvideCouiMenuBackdrop`
 * （见 `AgentAppTheme`）时自动启用模糊材质与轮廓光。
 */
@Composable
internal fun CouiSheetPanel(
    visible: Boolean,
    title: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    /** 面板高度占屏幕高度的比例；`null` 表示按内容自适应（库会按内容测量并夹在窗口可见高度内）。 */
    heightFraction: Float? = null,
    /** 正文自带 `LazyColumn` / `verticalScroll` 时必须传 false，避免双层纵向滚动冲突。 */
    scrollableContent: Boolean = true,
    /** 需要提交动作的面板（如远程模型导入）才传顶部按钮，其余保持无按钮形态。 */
    leadingButton: CouiSheetButton? = null,
    trailingButton: CouiSheetButton? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val visibleState = remember { mutableStateOf(visible) }
    visibleState.value = visible
    BottomSheet(
        modifier = modifier,
        visible = visibleState,
        onDismissRequest = onDismissRequest,
        dark = CouiTheme.isDark,
        sheetHeight = heightFraction?.let { LocalConfiguration.current.screenHeightDp.dp * it },
        showDragHandle = true,
        scrollableContent = scrollableContent,
        leadingButton = leadingButton,
        trailingButton = trailingButton,
        titleContent = { CouiText(text = title, kind = CouiTextKind.SheetTitle) },
        content = content,
    )
}
