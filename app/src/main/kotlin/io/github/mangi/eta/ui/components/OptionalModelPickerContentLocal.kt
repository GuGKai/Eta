package io.github.mangi.eta.ui.components

// 本文件是 fork 挂载点：上游同名文件 OptionalModelPickerContent.kt 只保留上游声明，
// 本文件承接 fork 新增的 showFollowOption 等本地逻辑。

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.mangi.eta.ui.model.AgentModelPickerUiState


/**
 * 「可选模型」选择正文：在 [ModelPickerSheetContent] 之上加一行「跟随主模型」。
 *
 * 高度不再由这里限定：底部面板按内容自适应，列表自身带高度上限，短内容面板就矮、长内容撑到上限。
 */
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
    )
}
