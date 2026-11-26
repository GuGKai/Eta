package io.github.mangi.eta.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.itos.coui.dialog.CouiDialogAction
import com.itos.coui.dialog.CouiDialogPlacement
import com.itos.coui.dialog.Dialog
import com.itos.coui.menu.rememberCouiWindowBackdrop
import com.itos.coui.theme.CouiTheme
import io.github.mangi.eta.R
import io.github.mangi.eta.data.model.AppearanceUiStyle
import io.github.mangi.eta.ui.app.LocalUiStyle
import io.github.mangi.eta.ui.components.coui.rememberCouiDialogMaterial
import io.github.mangi.eta.ui.model.AgentModelPickerUiState
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet

/**
 * 会话内选模型弹窗。
 *
 * 外观与「设置 → 自动生成对话标题 → 生成标题模型」那个弹窗完全一致（同一份
 * [OptionalModelPickerContent] + 同一套 COUI Dialog / MIUIX OverlayBottomSheet 外壳），
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
    val closeText = stringResource(R.string.action_close)
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
            val dark = CouiTheme.isDark
            Dialog(
                visible = visible,
                onDismissRequest = onDismissRequest,
                dark = dark,
                title = title,
                confirmAction = CouiDialogAction(
                    text = closeText,
                    onClick = onDismissRequest,
                ),
                placement = CouiDialogPlacement.Bottom,
                material = rememberCouiDialogMaterial(dark),
                backdrop = rememberCouiWindowBackdrop(),
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
