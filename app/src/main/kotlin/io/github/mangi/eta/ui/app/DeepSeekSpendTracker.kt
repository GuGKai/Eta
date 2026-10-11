package io.github.mangi.eta.ui.app

/**
 * 「累计消耗」的基线规则：官方没有用量接口，只能用余额减少量近似。
 *
 * - 第一次拿到余额时把当前值记为基线；
 * - 之后 `消耗 = 基线 - 当前余额`；
 * - 余额上升（充值）时把基线抬高到新高点重新起算，保证这个数只增不减；
 * - 基线可以随快照一起持久化，重启后继续沿用。
 */
internal class DeepSeekSpendTracker {
    data class Sample(
        val baseline: Double,
        val baselineAtMillis: Long,
        val spent: Double,
    )

    private var baseline: Double? = null
    private var baselineAtMillis: Long? = null

    val storedBaseline: Double? get() = baseline
    val storedBaselineAtMillis: Long? get() = baselineAtMillis

    fun restore(baseline: Double?, baselineAtMillis: Long?) {
        this.baseline = baseline
        this.baselineAtMillis = baselineAtMillis
    }

    /** [previousTotal] 是上一次观测到的余额，用于识别充值；首帧传 null。 */
    fun observe(total: Double, now: Long, previousTotal: Double?): Sample {
        val toppedUp = baseline == null ||
            (previousTotal != null && total > previousTotal + TOP_UP_EPSILON)
        if (toppedUp) {
            baseline = total
            baselineAtMillis = now
        }
        val sample = baseline ?: total
        return Sample(
            baseline = sample,
            baselineAtMillis = baselineAtMillis ?: now,
            spent = (sample - total).coerceAtLeast(0.0),
        )
    }

    private companion object {
        const val TOP_UP_EPSILON = 0.005
    }
}
