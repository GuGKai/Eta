package io.github.mangi.eta.ui.app

import org.junit.Assert.assertEquals
import org.junit.Test

class DeepSeekSpendTrackerTest {
    private fun tracker(seed: Double = 0.0) = DeepSeekSpendTracker { seed }

    @Test
    fun seedMatchesPlatformCumulativeSpendOnFirstSample() {
        // 平台累计 306.56、当前余额 43.83 → 基线 350.39，显示值应当就是 306.56
        val spend = tracker(seed = 306.56)
        val sample = spend.observe(total = 43.83, now = 1_000L, previousTotal = null)
        assertEquals(350.39, sample.baseline, 0.0001)
        assertEquals(306.56, sample.spent, 0.0001)
        assertEquals(1_000L, sample.baselineAtMillis)
    }

    @Test
    fun balanceDropAddsToTheCumulativeSpend() {
        val spend = tracker(seed = 306.56)
        spend.observe(total = 43.83, now = 1_000L, previousTotal = null)
        val sample = spend.observe(total = 41.20, now = 5_000L, previousTotal = 43.83)
        assertEquals(350.39, sample.baseline, 0.0001)
        assertEquals(309.19, sample.spent, 0.0001)
    }

    @Test
    fun topUpKeepsTheCumulativeSpendGrowing() {
        val spend = tracker(seed = 306.56)
        spend.observe(total = 43.83, now = 1_000L, previousTotal = null)
        spend.observe(total = 41.20, now = 2_000L, previousTotal = 43.83)
        val afterTopUp = spend.observe(total = 91.20, now = 3_000L, previousTotal = 41.20)
        assertEquals(400.39, afterTopUp.baseline, 0.0001)
        assertEquals(309.19, afterTopUp.spent, 0.0001)
        val later = spend.observe(total = 89.00, now = 4_000L, previousTotal = 91.20)
        assertEquals(311.39, later.spent, 0.0001)
    }

    @Test
    fun offlineTopUpIsReconciledFromTheCachedBalance() {
        val spend = tracker(seed = 306.56)
        spend.observe(total = 43.83, now = 1_000L, previousTotal = null)
        // 应用关闭期间充值：恢复出的基线仍是旧的，previousTotal 是缓存余额
        spend.restore(baseline = 350.39, baselineAtMillis = 1_000L)
        val sample = spend.observe(total = 93.83, now = 9_000L, previousTotal = 43.83)
        assertEquals(400.39, sample.baseline, 0.0001)
        assertEquals(306.56, sample.spent, 0.0001)
    }

    @Test
    fun restoredSnapshotKeepsCountingWithoutSeed() {
        val spend = tracker(seed = 306.56)
        spend.restore(baseline = 400.39, baselineAtMillis = 42L)
        val sample = spend.observe(total = 97.50, now = 9_000L, previousTotal = null)
        assertEquals(400.39, sample.baseline, 0.0001)
        assertEquals(302.89, sample.spent, 0.0001)
        assertEquals(42L, sample.baselineAtMillis)
    }

    @Test
    fun reseedingAfterPlatformChangeReplacesTheBaseline() {
        // 长按校准：扔掉旧基线，用新平台数字 + 最新余额重新起算
        val spend = tracker(seed = 306.56)
        spend.observe(total = 43.83, now = 1_000L, previousTotal = null)
        spend.restore(baseline = null, baselineAtMillis = null)
        val sample = spend.observe(total = 41.20, now = 2_000L, previousTotal = null)
        assertEquals(347.76, sample.baseline, 0.0001)
        assertEquals(306.56, sample.spent, 0.0001)
    }

    @Test
    fun corruptBaselineBelowBalanceIsRebuilt() {
        val spend = tracker(seed = 306.56)
        spend.restore(baseline = 10.0, baselineAtMillis = 1L)
        val sample = spend.observe(total = 43.83, now = 5_000L, previousTotal = null)
        assertEquals(350.39, sample.baseline, 0.0001)
        assertEquals(306.56, sample.spent, 0.0001)
    }
}
