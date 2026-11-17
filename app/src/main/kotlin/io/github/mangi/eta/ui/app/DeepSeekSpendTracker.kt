package io.github.mangi.eta.ui.app

/**
 * 「累计消费」的记账规则：官方没有用量接口，只能用余额差额推算。
 *
 * 记账基线 = **累计消费 + 当前余额**（`spent = 基线 - 余额`）：
 *
 * - 首次观测时用 [seedSpent]（平台「累计消费金额」，如 306.56）加当前余额建基线，
 *   于是显示值一开始就等于平台上的历史累计；
 * - 之后余额每减少多少，累计就增加多少；
 * - 余额上升（充值/退款）时把基线抬高等量，**累计不会被清零或倒退**；
 * - 基线随快照持久化，重启/离线期间充值都能接上。
 */
internal class DeepSeekSpendTracker(private val seedSpent: () -> Double) {
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
        val current = baseline
        val next = when {
            current == null -> seedSpent() + total
            previousTotal != null && total > previousTotal + TOP_UP_EPSILON ->
                current + (total - previousTotal)
            current < total - TOP_UP_EPSILON -> seedSpent() + total
            else -> current
        }
        if (next != current) {
            baseline = next
            if (current == null) baselineAtMillis = now
        }
        return Sample(
            baseline = next,
            baselineAtMillis = baselineAtMillis ?: now,
            spent = (next - total).coerceAtLeast(0.0),
        )
    }

    private companion object {
        const val TOP_UP_EPSILON = 0.005
    }
}
