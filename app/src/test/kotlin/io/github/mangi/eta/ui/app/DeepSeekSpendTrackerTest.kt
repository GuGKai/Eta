package io.github.mangi.eta.ui.app

import org.junit.Assert.assertEquals
import org.junit.Test

class DeepSeekSpendTrackerTest {
    @Test
    fun firstSampleSetsBaselineWithoutSpending() {
        val tracker = DeepSeekSpendTracker()
        val sample = tracker.observe(total = 43.83, now = 1_000L, previousTotal = null)
        assertEquals(43.83, sample.baseline, 0.0001)
        assertEquals(1_000L, sample.baselineAtMillis)
        assertEquals(0.0, sample.spent, 0.0001)
    }

    @Test
    fun laterSamplesReportBalanceDropAsSpending() {
        val tracker = DeepSeekSpendTracker()
        tracker.observe(total = 43.83, now = 1_000L, previousTotal = null)
        val sample = tracker.observe(total = 41.20, now = 5_000L, previousTotal = 43.83)
        assertEquals(43.83, sample.baseline, 0.0001)
        assertEquals(1_000L, sample.baselineAtMillis)
        assertEquals(2.63, sample.spent, 0.0001)
    }

    @Test
    fun topUpRestartsTheBaseline() {
        val tracker = DeepSeekSpendTracker()
        tracker.observe(total = 10.00, now = 1_000L, previousTotal = null)
        tracker.observe(total = 6.00, now = 2_000L, previousTotal = 10.00)
        val afterTopUp = tracker.observe(total = 56.00, now = 3_000L, previousTotal = 6.00)
        assertEquals(56.00, afterTopUp.baseline, 0.0001)
        assertEquals(3_000L, afterTopUp.baselineAtMillis)
        assertEquals(0.0, afterTopUp.spent, 0.0001)
        val later = tracker.observe(total = 51.50, now = 4_000L, previousTotal = 56.00)
        assertEquals(4.50, later.spent, 0.0001)
    }

    @Test
    fun restoredBaselineSurvivesRestart() {
        val tracker = DeepSeekSpendTracker()
        tracker.restore(baseline = 100.0, baselineAtMillis = 42L)
        val sample = tracker.observe(total = 97.5, now = 9_000L, previousTotal = null)
        assertEquals(100.0, sample.baseline, 0.0001)
        assertEquals(42L, sample.baselineAtMillis)
        assertEquals(2.5, sample.spent, 0.0001)
    }
}
