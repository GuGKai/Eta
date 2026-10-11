package io.github.mangi.eta.agent.model

import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * DeepSeek 官方余额接口的最小客户端：`GET /user/balance`。
 *
 * 只借用已经配置好的 API Key 查询余额，既不落盘 Key，也不请求消费明细（官方没有公开的用量接口）。
 * 金额在官方响应里都是字符串，这里按官方字段语义解析。
 */
internal object DeepSeekBalanceClient {
    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .callTimeout(12, TimeUnit.SECONDS)
            .build()
    }

    data class Balance(
        val currency: String,
        val total: Double,
        val granted: Double,
        val toppedUp: Double,
        val sufficient: Boolean,
    )

    fun fetch(baseUrl: String, apiKey: String): Result<Balance> = runCatching {
        val request = Request.Builder()
            .url(baseUrl.trimEnd('/') + "/user/balance")
            .header("Authorization", "Bearer $apiKey")
            .header("Accept", "application/json")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            val json = JSONObject(response.body.string())
            val info = json.optJSONArray("balance_infos")?.optJSONObject(0)
                ?: error("balance_infos 缺失")
            Balance(
                currency = info.optString("currency").ifBlank { "CNY" },
                total = info.optString("total_balance").toDoubleOrNull() ?: error("total_balance 缺失"),
                granted = info.optString("granted_balance").toDoubleOrNull() ?: 0.0,
                toppedUp = info.optString("topped_up_balance").toDoubleOrNull() ?: 0.0,
                sufficient = json.optBoolean("is_available", true),
            )
        }
    }
}
