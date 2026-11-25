package io.github.mangi.eta.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.ui.model.AgentContextUsageUi
import io.github.mangi.eta.ui.model.formatContextUsage
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme
import io.github.mangi.eta.data.model.AppearanceUiStyle
import io.github.mangi.eta.ui.app.LocalUiStyle
import top.yukonga.miuix.kmp.glass.glassPopupAnchor
import top.yukonga.miuix.kmp.glass.glassPopupAnchorContent
import top.yukonga.miuix.kmp.glass.rememberGlassPopupAnchor

@Composable
internal fun AgentContextUsageButton(
    usage: AgentContextUsageUi,
    onCompact: () -> Unit = {},
    canCompact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val progress = usage.progress
    val progressColor = when {
        progress == null -> MiuixTheme.colorScheme.onSurfaceVariantActions
        progress >= 0.95f -> StatusError
        progress >= 0.80f -> StatusWarning
        else -> MiuixTheme.colorScheme.primary
    }
    val locale = LocalConfiguration.current.locales[0]
    val summary = formatContextUsage(
        usage = usage,
        noUsageText = stringResource(R.string.context_no_previous_usage),
        noLimitText = stringResource(R.string.context_no_model_limit),
        locale = locale,
    )
    val detail = when {
        usage.contextWindow == null -> summary
        usage.contextTokens == null -> stringResource(R.string.context_usage_after_response)
        else -> stringResource(R.string.context_usage_previous_response, summary)
    }
    val usageDescription = stringResource(
        R.string.context_usage_description,
        summary.replace('\n', ' '),
    )
    var showPopover by remember { mutableStateOf(false) }
    val isCoui = LocalUiStyle.current == AppearanceUiStyle.COUI
    val popupAnchor = rememberGlassPopupAnchor()
    val indicatorContent: @Composable () -> Unit = {
        CircularProgressIndicator(
            progress = progress ?: 0f,
            colors = ProgressIndicatorDefaults.progressIndicatorColors(
                foregroundColor = progressColor,
                disabledForegroundColor = progressColor,
                backgroundColor = MiuixTheme.colorScheme.secondaryContainer,
            ),
            strokeWidth = 2.5.dp,
            size = ChatInputActionIconSize,
            modifier = Modifier.semantics {
                contentDescription = usageDescription
            },
        )
    }

    Box(modifier = modifier) {
        IconButton(
            onClick = { showPopover = !showPopover },
            minWidth = ChatInputActionSize,
            minHeight = ChatInputActionSize,
            modifier = if (!isCoui) {
                Modifier.glassPopupAnchor(anchor = popupAnchor, cornerRadius = 17.dp)
            } else {
                Modifier
            },
        ) {
            Box(
                modifier = if (!isCoui) {
                    Modifier.glassPopupAnchorContent(popupAnchor)
                } else {
                    Modifier
                },
            ) {
                indicatorContent()
            }
        }
        ContextUsageStatsPopoverPopup(
            usage = usage,
            summaryText = summary,
            detailText = detail,
            canCompact = canCompact,
            onCompact = onCompact,
            onDismissRequest = { showPopover = false },
            show = showPopover,
            anchor = if (!isCoui) popupAnchor else null,
            anchorContent = if (!isCoui) indicatorContent else null,
        )
    }
}
