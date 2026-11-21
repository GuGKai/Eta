package io.github.mangi.eta.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.itos.coui.theme.CouiTheme
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text as CouiText
import io.github.mangi.eta.R
import io.github.mangi.eta.data.model.AppearanceUiStyle
import io.github.mangi.eta.ui.app.LocalUiStyle
import io.github.mangi.eta.ui.components.coui.CouiNoticeDialog
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.glass.GlassDialog
import top.yukonga.miuix.kmp.glass.GlassDialogButton
import top.yukonga.miuix.kmp.glass.GlassDialogButtonRole
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun ItemDescriptionDialog(
    show: Boolean,
    title: String,
    description: String,
    onDismiss: () -> Unit,
) {
    when (LocalUiStyle.current) {
        AppearanceUiStyle.COUI -> {
            // COUI 的 Dialog 把标题和 message 放在同一个滚动容器里，长文本滚动时标题会一起被推走。
            // 这里只让库渲染固定标题，正文改为限高、独立滚动的 content。
            val dark = CouiTheme.isDark
            val maxBodyHeight = (LocalConfiguration.current.screenHeightDp * 0.45f).dp
            CouiNoticeDialog(
                visible = show,
                title = title,
                message = null,
                confirmText = stringResource(R.string.action_close),
                onDismissRequest = onDismiss,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp)
                        .heightIn(max = maxBodyHeight)
                        .verticalScroll(rememberScrollState()),
                ) {
                    CouiText(
                        text = description,
                        kind = CouiTextKind.ItemSummary,
                        color = CouiTheme.colorSchemeFor(dark).onSurfaceVariant,
                        textAlign = TextAlign.Start,
                    )
                }
            }
        }
        AppearanceUiStyle.MIUIX -> {
            var retainedTitle by remember { mutableStateOf(title) }
            var retainedDescription by remember { mutableStateOf(description) }
            if (show) {
                retainedTitle = title
                retainedDescription = description
            }
            GlassDialog(
                show = show,
                title = retainedTitle,
                backdrop = currentGlassOverlayBackdrop(),
                onDismissRequest = onDismiss,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = retainedDescription,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState()),
                    )
                    GlassDialogButton(
                        onClick = onDismiss,
                        role = GlassDialogButtonRole.Normal,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = stringResource(R.string.action_close),
                            style = MiuixTheme.textStyles.button,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}