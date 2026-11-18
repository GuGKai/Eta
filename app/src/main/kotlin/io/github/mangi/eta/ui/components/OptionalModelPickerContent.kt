package io.github.mangi.eta.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.ui.model.AgentModelPickerUiState


/**
 * 弹窗内容区的高度上限。
 *
 * COUI 底部弹窗卡片最高 632dp（`CouiDialogDefaults.maxHeight`）；扣掉标题区
 * （titleTop 24 + 标题行 + titleBottom 6）和底部按钮区（约 12 + 44 + 16）后，
 * 内容槽实际可用约 500dp。超过可用高度，内容就会交给 Dialog 内容槽自己的
 * verticalScroll 滚动，搜索框、提供商那一排会连带被滚走。留 60dp 余量取 440dp。
 */
private val PickerContentMaxHeight = 440.dp

@Composable
internal fun OptionalModelPickerContent(
    pickerState: AgentModelPickerUiState,
    selectedSecondaryModelId: String?,
    followTitle: String,
    followSubtitle: String,
    onSelectModel: (String?) -> Unit,
    /** 「跟随主模型」一行只在可选「不指定模型」的场景有意义，会话内选模型时关掉。 */
    showFollowOption: Boolean = true,
) {
    val followOption = remember(
        followTitle,
        followSubtitle,
        pickerState.selectedModel?.modelId,
        pickerState.selectedModel?.providerSourceType,
    ) {
        ModelPickerFollowOption(
            title = followTitle,
            subtitle = followSubtitle,
            modelId = pickerState.selectedModel?.modelId,
            sourceType = pickerState.selectedModel?.providerSourceType,
        )
    }
    ModelPickerSheetContent(
        pickerState = pickerState,
        selectedModelId = selectedSecondaryModelId,
        followOption = followOption.takeIf { showFollowOption },
        onSelectModel = onSelectModel,
        heightCap = PickerContentMaxHeight,
    )
}
