package com.itos.coui.dialog

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.itos.coui.backdrop.CouiBackdrop
import com.itos.coui.effect.couiButtonFeedback
import com.itos.coui.layout.Divider
import com.itos.coui.layout.Item
import com.itos.coui.menu.rememberCouiWindowBackdrop
import com.itos.coui.radiobutton.CouiRadioButtonDefaults
import com.itos.coui.radiobutton.RadioButton
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.theme.CouiTheme
import com.itos.coui.theme.ProvideCouiContent

/** 单选项。[value] 必须在选项间唯一且稳定，[icon] 用于展示行首装饰图标。 */
data class CouiSingleChoiceItem<T : Any>(
    val value: T,
    val text: String,
    val enabled: Boolean = true,
    val icon: (@Composable () -> Unit)? = null,
) {
    init {
        require(text.isNotBlank()) { "单选项文案不能为空" }
    }
}

private object SingleChoiceListDefaults {
    val rowHeight = 60.dp
    val indicatorSpacing = 16.dp
    val horizontalPadding = CouiDialogDefaults.contentPadding
    val rowContentPadding = PaddingValues(horizontal = horizontalPadding, vertical = 8.dp)
}

/**
 * 单选弹窗的标题和底部操作区固定，正文和选项共用滚动区。
 *
 * 将调用留在组合中，由 [visible] 控制显隐。宿主通过 [onItemSelected] 更新选中值。
 * [selectedValue] 为空或不在 [items] 中时，所有选项均未选中。
 * 返回和外点触发 [onDismissRequest]，底部按钮各自执行 [CouiDialogAction.onClick]。
 * API 33 及以上且有 [backdrop] 时默认启用模糊材质，显式传入 `material = null` 可使用纯色表面。
 *
 * @param actionRows 从上到下声明 0 至 4 行操作，行内从布局起始侧到末端排列。空列表隐藏操作区。
 * @param message 标题下、选项前的说明文字。为 null 或空白时省略。
 * @throws IllegalArgumentException 标题为空白、选项为空或存在重复值，或操作行超出范围或包含空行。
 */
@Composable
fun <T : Any> SingleChoiceDialog(
    modifier: Modifier = Modifier,
    visible: Boolean,
    title: String,
    items: List<CouiSingleChoiceItem<T>>,
    selectedValue: T?,
    onItemSelected: (T) -> Unit,
    onDismissRequest: () -> Unit,
    actionRows: List<List<CouiDialogAction>> = emptyList(),
    placement: CouiDialogPlacement = CouiDialogPlacement.Bottom,
    properties: DialogProperties = DialogProperties(usePlatformDefaultWidth = false, windowTitle = title),
    backdrop: CouiBackdrop? = rememberCouiWindowBackdrop(),
    dark: Boolean = CouiTheme.isDark,
    colors: CouiDialogColors = CouiDialogDefaults.themeColors(dark),
    material: CouiDialogMaterial? = if (backdrop != null && CouiDialogDefaults.isMaterialSupported())
        CouiDialogDefaults.material(dark) else null,
    themeTint: Color = CouiTheme.materialTintFor(dark),
    message: String? = null,
) {
    require(title.isNotBlank()) { "单选弹窗标题不能为空" }
    requireDialogActionRows(actionRows, minRows = 0)

    CouiDialogWindow(
        modifier = modifier,
        onDismissRequest = { if (visible) onDismissRequest() },
        colors = colors,
        dark = dark,
        placement = placement,
        properties = properties,
        material = material,
        backdrop = backdrop,
        visible = visible,
        themeTint = themeTint,
    ) {
        Column(Modifier.fillMaxWidth()) {
            DialogTitle(title, colors.title)
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                if (!message.isNullOrBlank()) {
                    DialogText(
                        modifier = Modifier.fillMaxWidth()
                            .padding(horizontal = CouiDialogDefaults.contentPadding)
                            .padding(bottom = CouiDialogDefaults.messageBottom),
                        text = message,
                        kind = DialogTextKind.Message,
                        color = colors.message,
                    )
                }
                SingleChoiceList(
                    items = items,
                    selectedValue = selectedValue,
                    onItemSelected = onItemSelected,
                    interactive = visible,
                    dark = dark,
                    colors = colors,
                )
            }
            if (actionRows.isNotEmpty()) {
                DialogActions(
                    actionRows = actionRows,
                    colors = colors,
                    dark = dark,
                    material = material != null,
                    themeTint = themeTint,
                    interactive = visible,
                )
            }
        }
    }
}

@Composable
private fun <T : Any> SingleChoiceList(
    items: List<CouiSingleChoiceItem<T>>,
    selectedValue: T?,
    onItemSelected: (T) -> Unit,
    interactive: Boolean,
    dark: Boolean,
    colors: CouiDialogColors,
) {
    require(items.isNotEmpty() && items.distinctBy { it.value }.size == items.size) {
        "单选弹窗需要至少一个选项，且选项值必须唯一"
    }
    val colorScheme = CouiTheme.colorSchemeFor(dark)
    val radioColors = CouiRadioButtonDefaults.colors(colorScheme.controlAccent, dark)
    Column(Modifier.fillMaxWidth().selectableGroup()) {
        items.forEachIndexed { index, item ->
            key(item.value) {
                val selected = item.value == selectedValue
                val enabled = interactive && item.enabled
                val rowColor = if (item.enabled) colors.action else colors.disabledAction
                val interaction = remember { MutableInteractionSource() }
                Item(
                    modifier = Modifier.heightIn(min = SingleChoiceListDefaults.rowHeight)
                        .couiButtonFeedback(dark, interaction, enabled, list = true)
                        .selectable(selected, interaction, null, enabled, Role.RadioButton) {
                            // Compose 的语义动作也能直接调用 onClick。
                            if (enabled) onItemSelected(item.value)
                        },
                    icon = item.icon?.let { icon -> @Composable {
                        Box(Modifier.clearAndSetSemantics {}) {
                            ProvideCouiContent(color = rowColor, content = icon)
                        }
                    } },
                    trailing = {
                        RadioButton(
                            modifier = Modifier.padding(start = SingleChoiceListDefaults.indicatorSpacing),
                            selected = selected,
                            enabled = item.enabled,
                            colors = radioColors,
                        )
                    },
                    contentPadding = SingleChoiceListDefaults.rowContentPadding,
                    title = { Text(text = item.text, kind = CouiTextKind.ItemTitle, color = rowColor) },
                )
                if (index < items.lastIndex) Divider(
                    startIndent = SingleChoiceListDefaults.horizontalPadding,
                    endIndent = SingleChoiceListDefaults.horizontalPadding,
                    color = colorScheme.outlineVariant,
                )
            }
        }
    }
}
