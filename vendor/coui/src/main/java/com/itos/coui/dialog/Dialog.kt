package com.itos.coui.dialog

import com.itos.coui.theme.CouiTheme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import com.itos.coui.backdrop.CouiBackdrop

/** 受控操作。主操作省略 [colors] 时使用主题填色。关闭、状态变更及业务提交由宿主处理。 */
data class CouiDialogAction(val text: String, val onClick: () -> Unit, val enabled: Boolean = true,
    val style: CouiDialogActionStyle = CouiDialogActionStyle.Secondary, val colors: CouiDialogActionColors? = null) {
    init {
        require(text.isNotBlank()) { "操作文案不能为空" }
    }
}

/**
 * 普通警告弹窗。两短按钮横排，单个、三个或放不下的按钮竖排。
 * 标题与底部操作区独立于正文滚动区，message 和 content 一起滚动。
 * content 可装配列表、输入和选择器；不提供推荐/AI Payment 专用按钮及原厂高内容标题合并协议。
 * CouiShape 自动提供轮廓半径；其他 Shape 使用材质时须显式提供 cornerRadius。
 * visible 非空时由库保留退场窗口，调用须留在组合中；null 使用外层条件和系统窗口动画。
 * themeTint 为模糊材质染色基准色（强度由库统一控制，Color.Transparent 关闭染色，仅 material != null 时生效）。
 */
@Composable
fun Dialog(
    modifier: Modifier = Modifier,
    onDismissRequest: () -> Unit,
    confirmAction: CouiDialogAction,
    dark: Boolean = CouiTheme.isDark,
    title: String? = null,
    message: String? = null,
    dismissAction: CouiDialogAction? = null,
    neutralAction: CouiDialogAction? = null,
    placement: CouiDialogPlacement = CouiDialogPlacement.Bottom,
    colors: CouiDialogColors = CouiDialogDefaults.themeColors(dark),
    shape: Shape = CouiDialogDefaults.shape,
    cornerRadius: Dp = Dp.Unspecified,
    material: CouiDialogMaterial? = null,
    backdrop: CouiBackdrop? = null,
    properties: DialogProperties = DialogProperties(usePlatformDefaultWidth = false),
    visible: Boolean? = null,
    themeTint: Color = CouiTheme.materialTintFor(dark),
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val verticalOrder = listOfNotNull(neutralAction, confirmAction, dismissAction)
    val horizontal = neutralAction == null && dismissAction != null
    val actionRows = if (horizontal) listOf(listOfNotNull(dismissAction, confirmAction))
        else verticalOrder.map { listOf(it) }
    DialogWithActions(modifier, onDismissRequest, dark, title, message, placement, colors, shape, cornerRadius,
        material, backdrop, properties, visible, themeTint, actionRows, content,
        overflowOrder = if (horizontal) verticalOrder else null, destructiveAction = neutralAction)
}

/**
 * 按行排列底部操作。每行等分可用宽度，文案放不下的行会按声明顺序拆成竖排。
 * 标题与操作区独立于正文滚动区，关闭弹窗由各操作的回调决定。
 * themeTint 为模糊材质染色基准色（强度由库统一控制，Color.Transparent 关闭染色，仅 material != null 时生效）。
 *
 * @param actionRows 从上到下声明 1 至 4 行，行内从布局起始侧到末端排列，每行至少一个操作。
 * @throws IllegalArgumentException 声明行数超出范围或包含空行。
 */
@Composable
fun Dialog(
    modifier: Modifier = Modifier,
    onDismissRequest: () -> Unit,
    actionRows: List<List<CouiDialogAction>>,
    dark: Boolean = CouiTheme.isDark,
    title: String? = null,
    message: String? = null,
    placement: CouiDialogPlacement = CouiDialogPlacement.Bottom,
    colors: CouiDialogColors = CouiDialogDefaults.themeColors(dark),
    shape: Shape = CouiDialogDefaults.shape,
    cornerRadius: Dp = Dp.Unspecified,
    material: CouiDialogMaterial? = null,
    backdrop: CouiBackdrop? = null,
    properties: DialogProperties = DialogProperties(usePlatformDefaultWidth = false),
    visible: Boolean? = null,
    themeTint: Color = CouiTheme.materialTintFor(dark),
    content: @Composable ColumnScope.() -> Unit = {},
) {
    requireDialogActionRows(actionRows)
    DialogWithActions(modifier, onDismissRequest, dark, title, message, placement, colors, shape, cornerRadius,
        material, backdrop, properties, visible, themeTint, actionRows, content)
}

@Composable
private fun DialogWithActions(
    modifier: Modifier,
    onDismissRequest: () -> Unit,
    dark: Boolean,
    title: String?,
    message: String?,
    placement: CouiDialogPlacement,
    colors: CouiDialogColors,
    shape: Shape,
    cornerRadius: Dp,
    material: CouiDialogMaterial?,
    backdrop: CouiBackdrop?,
    properties: DialogProperties,
    visible: Boolean?,
    themeTint: Color,
    actionRows: List<List<CouiDialogAction>>,
    content: @Composable ColumnScope.() -> Unit,
    overflowOrder: List<CouiDialogAction>? = null,
    destructiveAction: CouiDialogAction? = null,
) {
    CouiDialogWindow(modifier, onDismissRequest, colors, dark, placement, shape, cornerRadius,
        properties, material, backdrop, visible, themeTint = themeTint) {
        var messageLines by remember(message) { mutableIntStateOf(1) }
        var multipleRows by remember(actionRows.size) { mutableStateOf(actionRows.size > 1) }
        val tall = messageLines > 1 || multipleRows
        Column(Modifier.fillMaxWidth()) {
            if (!title.isNullOrEmpty()) DialogTitle(title, colors.title)
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                if (!message.isNullOrEmpty()) DialogText(text = message, kind = DialogTextKind.Message, color = colors.message, modifier =
                    Modifier.fillMaxWidth().padding(horizontal = CouiDialogDefaults.contentPadding)
                        .padding(top = if (title.isNullOrEmpty() && !tall) CouiDialogDefaults.messageTopWithoutTitle else 0.dp,
                            bottom = if (title.isNullOrEmpty() && !tall) CouiDialogDefaults.messageBottomWithoutTitle
                                else CouiDialogDefaults.messageBottom),
                    onLineCount = { if (messageLines != it) messageLines = it })
                content()
            }
            DialogActions(actionRows, overflowOrder, destructiveAction, colors, dark, material != null, themeTint) { multipleRows = it }
        }
    }
}

@Composable
internal fun DialogTitle(title: String, color: Color) {
    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    Box(Modifier.fillMaxWidth().padding(
        start = CouiDialogDefaults.contentPadding, end = CouiDialogDefaults.contentPadding,
        top = CouiDialogDefaults.titleTop, bottom = CouiDialogDefaults.titleBottom)) {
        DialogText(text = title, kind = DialogTextKind.Title, color = color, modifier = Modifier.fillMaxWidth()
            .heightIn(max = CouiDialogDefaults.titleMaxHeight(screenHeightDp))
            .verticalScroll(rememberScrollState()))
    }
}

internal fun requireDialogActionRows(actionRows: List<List<CouiDialogAction>>, minRows: Int = 1) {
    require(actionRows.size in minRows..4) { "操作行数必须在 $minRows 到 4 之间" }
    require(actionRows.all { it.isNotEmpty() }) { "每行至少需要一个操作" }
}

@Composable
internal fun DialogActions(
    actionRows: List<List<CouiDialogAction>>,
    overflowOrder: List<CouiDialogAction>? = null,
    destructiveAction: CouiDialogAction? = null,
    colors: CouiDialogColors,
    dark: Boolean,
    material: Boolean,
    themeTint: Color,
    interactive: Boolean = true,
    onMultipleRows: (Boolean) -> Unit = {},
) {
    val density = LocalDensity.current
    val textStyle = rememberDialogTextStyle(DialogTextKind.Button)
    val measurer = rememberTextMeasurer()
    val scroll = rememberScrollState()
    val secondaryColors = DialogButtonDefaults.secondaryColors(colors, dark, material, themeTint)
    // 文字视口只裁剪自己的内容；按钮运动绘制在文字之上。
    BoxWithConstraints(Modifier.fillMaxWidth().zIndex(1f)) {
        val edge = CouiDialogDefaults.buttonEdgePadding
        // 保留旧版两侧各自取整后的间距，测量和实际排列使用同一像素值。
        val gapPx = with(density) { CouiDialogDefaults.buttonInsidePadding.roundToPx() * 2 }
        val gap = with(density) { gapPx.toDp() }
        val top = CouiDialogDefaults.buttonTopPadding
        val bottom = CouiDialogDefaults.buttonBottomPadding
        val parentWidth = with(density) { maxWidth.roundToPx() }
        val rowWidth = parentWidth - with(density) { edge.roundToPx() * 2 }
        val textPaddingPx = with(density) { CouiDialogDefaults.buttonHorizontalPadding.roundToPx() * 2 }
        fun textWidth(buttonCount: Int): Int {
            val buttonWidth = (rowWidth - (buttonCount - 1) * gapPx) / buttonCount
            return (buttonWidth - textPaddingPx).coerceAtLeast(0)
        }
        val textWidths = actionRows.flatten().associateWith {
            measurer.measure(it.text, textStyle, softWrap = false).multiParagraph.maxIntrinsicWidth
        }
        // 旧双按钮分支沿用 COUIButtonBarLayout 的横排临界值。
        val legacyAvailable = if (overflowOrder != null) parentWidth / 2 - with(density) {
            (CouiDialogDefaults.buttonHorizontalPadding * 2 + edge + CouiDialogDefaults.buttonInsidePadding).toPx()
        } else null
        val rows = actionRows.flatMap { row ->
            val available = legacyAvailable ?: textWidth(row.size).toFloat()
            if (row.size > 1 && row.any { textWidths.getValue(it) > available }) {
                (overflowOrder ?: row).map { listOf(it) }
            } else listOf(row)
        }
        SideEffect { onMultipleRows(rows.size > 1) }
        val primaryColors = DialogButtonDefaults.primaryColors(CouiTheme.colorSchemeFor(dark).controlAccent)
        fun buttonHeight(action: CouiDialogAction, width: Int, multiline: Boolean): Int {
            val textHeight = measurer.measure(action.text, textStyle, maxLines = 2, overflow = TextOverflow.Ellipsis,
                constraints = Constraints(maxWidth = width)).size.height
            return with(density) {
                maxOf(textHeight + 4.dp.roundToPx() * 2,
                    (if (multiline) CouiDialogDefaults.multilineButtonHeight else CouiDialogDefaults.buttonHeight).roundToPx())
            }
        }
        val naturalHeight = with(density) {
            rows.sumOf { row ->
                val available = textWidth(row.size)
                row.maxOf { action -> buttonHeight(action, available, row.size == 1 && textWidths.getValue(action) > available) } +
                    top.roundToPx() * 2
            } + bottom.roundToPx() - top.roundToPx()
        }
        // verticalScroll 即使无法滚动也会裁剪，只有实际超高时才需要这个视口。
        Column(if (naturalHeight > constraints.maxHeight) Modifier.verticalScroll(scroll) else Modifier) {
            // 重排时释放旧按钮的按压会话，避免复用到另一项操作。
            key(rows.map { it.size }) {
                rows.forEachIndexed { index, row ->
                    val available = textWidth(row.size)
                    Row(
                        Modifier.fillMaxWidth().padding(start = edge, end = edge, top = top,
                            bottom = if (index == rows.lastIndex) bottom else top),
                        horizontalArrangement = Arrangement.spacedBy(gap),
                    ) {
                        row.forEach { action ->
                            val actionColors = action.colors ?: when {
                                action === destructiveAction -> secondaryColors.copy(content = colors.destructive)
                                action.style == CouiDialogActionStyle.Primary -> primaryColors
                                else -> secondaryColors
                            }
                            DialogActionButton(
                                action, actionColors,
                                dark, material, Modifier.weight(1f), row.size == 1 && textWidths.getValue(action) > available,
                                interactive,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DialogActionButton(action: CouiDialogAction, colors: CouiDialogActionColors, dark: Boolean, material: Boolean,
    modifier: Modifier, multiline: Boolean, interactive: Boolean) {
    DialogButton(modifier = modifier.heightIn(min = if (multiline) CouiDialogDefaults.multilineButtonHeight
        else CouiDialogDefaults.buttonHeight), text = action.text,
        onClick = { if (interactive && action.enabled) action.onClick() },
        dark = dark, enabled = action.enabled, style = action.style, material = material,
        colors = colors)
}
