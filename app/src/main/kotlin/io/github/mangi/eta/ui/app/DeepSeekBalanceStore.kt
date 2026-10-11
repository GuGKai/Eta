package io.github.mangi.eta.ui.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.mangi.eta.agent.model.DeepSeekBalanceClient
import io.github.mangi.eta.data.datastore.SettingsDataStore
import io.github.mangi.eta.data.model.ProviderSetting
import io.github.mangi.eta.data.provider.BuiltinProviders
import io.github.mangi.eta.data.repository.ProviderRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * 顶栏账号胶囊的数据源：DeepSeek 余额 + 累计消耗。
 *
 * - 余额来自官方 `GET /user/balance`（只读，用「模型提供商」里已配置的 Key，不额外存密钥）；
 * - 官方没有公开的用量/消费接口，所以「累计消耗」= 首次记录（或最近一次充值）以来的**余额减少量**，
 *   充值会让余额上升，此时把基线抬高重新起算，保证这个数只增不减；
 * - 快照缓存在 DataStore：冷启动先显示上次结果，再联网刷新。
 */
internal class DeepSeekBalanceStore(
    context: Context,
    private val scope: CoroutineScope,
) {
    private val appContext = context.applicationContext

    data class UiState(
        /** 是否找到了可用的 DeepSeek Key；false 时顶栏不显示任何胶囊。 */
        val configured: Boolean = false,
        val loading: Boolean = false,
        val currency: String? = null,
        val total: Double? = null,
        /** 自基线起减少的余额，近似「这段时间的消费」。 */
        val spent: Double? = null,
        val baselineAtMillis: Long? = null,
        val fetchedAtMillis: Long? = null,
        val failed: Boolean = false,
    )

    var state by mutableStateOf(UiState())
        private set

    private var job: Job? = null
    private var lastAttemptAt = 0L

    /** 平台「累计消费金额」；未校准或未持久化时用 2026-10-11 的实测值兜底。 */
    private var seedSpent = DEFAULT_SEED_SPENT
    private var pendingSeed: Double? = null
    private val spend = DeepSeekSpendTracker { seedSpent }

    init {
        scope.launch {
            restore()
            request(force = true)
        }
    }

    /** 自动刷新（进入界面 / 定时），最短间隔 [AUTO_INTERVAL_MS]。 */
    fun refresh() = request(force = false)

    /** 手动刷新（点击胶囊），最短间隔 [MANUAL_INTERVAL_MS]。 */
    fun refreshNow() = request(force = true)

    /** 长按校准：把累计改成平台上的数字，随后立即按新余额重建基线。 */
    fun reseed(cumulativeSpent: Double) {
        val value = cumulativeSpent.coerceAtLeast(0.0)
        seedSpent = value
        pendingSeed = value
        state = state.copy(spent = value)
        scope.launch { persist() }
        request(force = true)
    }

    private fun request(force: Boolean) {
        val now = System.currentTimeMillis()
        val minInterval = if (force) MANUAL_INTERVAL_MS else AUTO_INTERVAL_MS
        if (now - lastAttemptAt < minInterval) return
        lastAttemptAt = now
        job?.cancel()
        job = scope.launch(Dispatchers.IO) { fetch() }
    }

    private suspend fun fetch() {
        val provider = resolveProvider()
        if (provider == null) {
            state = state.copy(configured = false, loading = false)
            return
        }
        state = state.copy(configured = true, loading = true)
        DeepSeekBalanceClient.fetch(provider.baseUrl, provider.apiKey)
            .onSuccess { balance -> apply(balance) }
            .onFailure {
                state = state.copy(loading = false, failed = true)
            }
    }

    private fun apply(balance: DeepSeekBalanceClient.Balance) {
        val now = System.currentTimeMillis()
        if (pendingSeed != null) {
            // 刚校准过：丢掉旧基线，用这次拿到的余额重建
            spend.restore(baseline = null, baselineAtMillis = null)
            pendingSeed = null
        }
        val sample = spend.observe(
            total = balance.total,
            now = now,
            previousTotal = state.total,
        )
        state = UiState(
            configured = true,
            loading = false,
            currency = balance.currency,
            total = balance.total,
            spent = sample.spent,
            baselineAtMillis = sample.baselineAtMillis,
            fetchedAtMillis = now,
            failed = !balance.sufficient,
        )
        scope.launch { persist() }
    }

    private suspend fun resolveProvider(): ProviderSetting? {
        val selectedId = runCatching { SettingsDataStore.selectedProviderIdFlow().first() }.getOrNull()
        val candidates = listOfNotNull(
            selectedId?.let { runCatching { ProviderRepository.providerById(it) }.getOrNull() },
            runCatching { ProviderRepository.providerById(BuiltinProviders.DEEPSEEK_ID) }.getOrNull(),
        )
        return candidates.firstOrNull { provider ->
            provider.apiKey.isNotBlank() && provider.baseUrl.contains(DEEPSEEK_HOST, ignoreCase = true)
        }
    }

    private suspend fun persist() {
        val json = JSONObject()
            .put("currency", state.currency.orEmpty())
            .put("total", state.total ?: JSONObject.NULL)
            .put("seed", seedSpent)
            .put("baseline", spend.storedBaseline ?: JSONObject.NULL)
            .put("baselineAt", spend.storedBaselineAtMillis ?: 0L)
            .put("fetchedAt", state.fetchedAtMillis ?: 0L)
        runCatching { SettingsDataStore.setDeepSeekBalanceJson(json.toString()) }
    }

    private suspend fun restore() {
        val raw = runCatching { SettingsDataStore.deepSeekBalanceJson() }.getOrNull() ?: return
        runCatching {
            val json = JSONObject(raw)
            val total = json.optDouble("total").takeUnless { it.isNaN() }
            val storedSeed = json.optDouble("seed").takeUnless { it.isNaN() }
            val storedBaseline = json.optDouble("baseline").takeUnless { it.isNaN() }
            val storedBaselineAt = json.optLong("baselineAt").takeIf { it > 0L }
            val fetchedAt = json.optLong("fetchedAt").takeIf { it > 0L }
            if (total == null) return@runCatching
            if (storedSeed != null) seedSpent = storedSeed
            spend.restore(storedBaseline, storedBaselineAt)
            state = UiState(
                configured = true,
                loading = false,
                currency = json.optString("currency").ifBlank { null },
                total = total,
                spent = ((storedBaseline ?: total) - total).coerceAtLeast(0.0),
                baselineAtMillis = storedBaselineAt,
                fetchedAtMillis = fetchedAt,
                failed = false,
            )
        }
    }

    private companion object {
        const val DEEPSEEK_HOST = "api.deepseek.com"

        /** 2026-10-11 校准：DeepSeek 平台「累计消费金额」306.56、余额 43.83。 */
        const val DEFAULT_SEED_SPENT = 306.56
        const val AUTO_INTERVAL_MS = 60_000L
        const val MANUAL_INTERVAL_MS = 5_000L
    }
}
