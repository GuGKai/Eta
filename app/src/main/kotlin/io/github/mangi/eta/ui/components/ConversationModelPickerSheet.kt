package io.github.mangi.eta.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.data.model.AppearanceUiStyle
import io.github.mangi.eta.ui.app.LocalUiStyle
import io.github.mangi.eta.ui.components.coui.CouiSheetPanel
import io.github.mangi.eta.ui.model.AgentModelPickerUiState
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet

/**
 * 会话内选模型弹窗。
 *
 * 外观与「设置 → 自动生成对话标题 → 生成标题模型」那个弹窗完全一致（同一份
 * [OptionalModelPickerContent] + 同一套 COUI Sheet / MIUIX OverlayBottomSheet 外壳），
 * 只把标题换成「选择当前会话模型」，并去掉「跟随主模型」那一行 —— 会话必须落在某个具体模型上。
 */
@Composable
internal fun ConversationModelPickerSheet(
    visible: Boolean,
    pickerState: AgentModelPickerUiState,
    onDismissRequest: () -> Unit,
    onSelectModel: (String) -> Unit,
) {
    val title = stringResource(R.string.local_chat_conversation_model_title)
    val selectedModelId = pickerState.selectedModel?.id

    val content: @Composable () -> Unit = {
        OptionalModelPickerContent(
            pickerState = pickerState,
            selectedSecondaryModelId = selectedModelId,
            followTitle = "",
            followSubtitle = "",
            showFollowOption = false,
            onSelectModel = { modelId ->
                if (modelId != null) {
                    onSelectModel(modelId)
                    onDismissRequest()
                }
            },
        )
    }

    when (LocalUiStyle.current) {
        AppearanceUiStyle.COUI -> {
            CouiSheetPanel(
                visible = visible,
                title = title,
                onDismissRequest = onDismissRequest,
                // 正文自带 LazyColumn，滚动与边缘效果交给它自己。
                scrollableContent = false,
            ) {
                content()
            }
        }

        AppearanceUiStyle.MIUIX -> {
            OverlayBottomSheet(
                show = visible,
                title = title,
                onDismissRequest = onDismissRequest,
                insideMargin = DpSize(0.dp, 0.dp),
            ) {
                content()
            }
        }
    }
}
