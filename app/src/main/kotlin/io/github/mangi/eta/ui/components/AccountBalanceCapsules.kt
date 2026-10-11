package io.github.mangi.eta.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mangi.eta.R
import io.github.mangi.eta.ui.app.DeepSeekBalanceStore
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 顶栏账号胶囊：DeepSeek 余额 + 自基线起的累计消耗。点击刷新，不显示任何百分比。
 *
 * 没有可用的 DeepSeek Key 时整块不出现；首次拉取或失败时数值位置显示占位符。
 */
@Composable
internal fun AccountBalanceCapsules(state: DeepSeekBalanceStore.UiState, onRefresh: () -> Unit) {
    if (!state.configured) return
    val symbol = if (state.currency.equals("USD", ignoreCase = true)) "$" else "¥"
    val money = NumberFormat.getNumberInstance().apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }
    val balanceValue = state.total?.let { symbol + money.format(it) } ?: "—"
    val spentValue = state.spent?.let { symbol + money.format(it) } ?: "—"
    val fetchedAt = state.fetchedAtMillis
        ?.let { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(it)) }
    val description = if (state.total == null) {
        stringResource(R.string.deepseek_capsule_unavailable)
    } else {
        stringResource(R.string.deepseek_capsule_description, balanceValue, spentValue) +
            (fetchedAt?.let { " · $it" } ?: "")
    }

    Row(
        modifier = Modifier.widthIn(max = 232.dp).padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        AccountCapsule(
            text = stringResource(R.string.deepseek_capsule_balance, balanceValue),
            modifier = Modifier.weight(1f, fill = false),
            description = description,
            onClick = onRefresh,
        )
        AccountCapsule(
            text = stringResource(R.string.deepseek_capsule_spend, spentValue),
            modifier = Modifier,
            description = description,
            onClick = onRefresh,
        )
    }
}

@Composable
private fun AccountCapsule(
    text: String,
    modifier: Modifier,
    description: String,
    onClick: () -> Unit,
) {
    Text(
        text,
        modifier = modifier
            .background(MiuixTheme.colorScheme.surfaceContainer, CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description }
            .padding(horizontal = 8.dp, vertical = 5.dp),
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        style = MiuixTheme.textStyles.footnote1,
        fontSize = 11.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
