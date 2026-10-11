package io.github.mangi.eta.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
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
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.layout.DialogDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * 顶栏账号胶囊：DeepSeek 余额 + 平台口径的累计消费。不显示任何百分比。
 *
 * - 点击任一胶囊立即刷新；
 * - **长按「累计」**弹出校准输入框，可把累计改成平台上的数字（之后的增量自动接上）；
 * - 没有可用的 DeepSeek Key 时整块不出现。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AccountBalanceCapsules(
    state: DeepSeekBalanceStore.UiState,
    onRefresh: () -> Unit,
    onReseed: (Double) -> Unit,
) {
    if (!state.configured) return
    var editing by remember { mutableStateOf(false) }
    val symbol = if (state.currency.equals("USD", ignoreCase = true)) "$" else "¥"
    val money = NumberFormat.getNumberInstance().apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }
    val balanceValue = state.total?.let { symbol + money.format(it) } ?: "—"
    val spentValue = state.spent?.let { symbol + money.format(it) } ?: "—"
    val fetchedAt = state.fetchedAtMillis
        ?.let { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(it)) }
    val balanceDescription = if (state.total == null) {
        stringResource(R.string.deepseek_capsule_unavailable)
    } else {
        stringResource(R.string.deepseek_capsule_description, balanceValue, spentValue) +
            (fetchedAt?.let { " · $it" } ?: "")
    }
    val spendDescription = balanceDescription + " · " + stringResource(R.string.deepseek_capsule_reseed_hint)

    Row(
        modifier = Modifier.widthIn(max = 232.dp).padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        AccountCapsule(
            text = stringResource(R.string.deepseek_capsule_balance, balanceValue),
            modifier = Modifier.weight(1f, fill = false),
            description = balanceDescription,
            onClick = onRefresh,
        )
        AccountCapsule(
            text = stringResource(R.string.deepseek_capsule_spend, spentValue),
            modifier = Modifier,
            description = spendDescription,
            onClick = onRefresh,
            onLongClick = { editing = true },
        )
    }

    if (editing) {
        var input by remember { mutableStateOf(state.spent?.let { plainAmount(it) }.orEmpty()) }
        val parsed = parseAmount(input)
        WindowDialog(
            show = true,
            cornerRadius = DialogDefaults.CornerRadius,
            title = stringResource(R.string.deepseek_seed_title),
            summary = stringResource(R.string.deepseek_seed_summary, balanceValue),
            onDismissRequest = { editing = false },
        ) {
            Column(Modifier.fillMaxWidth()) {
                TextField(
                    value = input,
                    onValueChange = { input = it },
                    label = stringResource(R.string.deepseek_seed_hint),
                    useLabelAsPlaceholder = true,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                MiuixDialogActions(
                    confirmText = stringResource(R.string.action_confirm),
                    confirmEnabled = parsed != null,
                    onCancel = { editing = false },
                    onConfirm = {
                        parsed?.let(onReseed)
                        editing = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AccountCapsule(
    text: String,
    modifier: Modifier,
    description: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    Text(
        text,
        modifier = modifier
            // 先裁成胶囊形状，按下的水波纹才是圆的，不会露出方形遮罩。
            .clip(CircleShape)
            .background(MiuixTheme.colorScheme.surfaceContainer, CircleShape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .semantics { contentDescription = description }
            .padding(horizontal = 8.dp, vertical = 5.dp),
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        style = MiuixTheme.textStyles.footnote1,
        fontSize = 11.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

private fun parseAmount(raw: String): Double? =
    raw.trim().removePrefix("¥").removePrefix("$").replace(",", "")
        .toDoubleOrNull()
        ?.takeIf { it >= 0.0 && it < 10_000_000.0 }

private fun plainAmount(value: Double): String =
    java.math.BigDecimal(value).setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()
