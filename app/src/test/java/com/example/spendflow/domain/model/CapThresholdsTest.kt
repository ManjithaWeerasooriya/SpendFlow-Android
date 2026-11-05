package com.example.spendflow.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CapThresholdsTest {

    @Test
    fun `default is 75, 90 and 100 percent`() {
        assertEquals(listOf(75, 90, 100), CapThresholds.DEFAULT.asList())
    }

    @Test
    fun `level is the highest threshold reached`() {
        val thresholds = CapThresholds.DEFAULT
        val limit = 20_000_00L
        assertEquals(CapLevel.UNDER, thresholds.levelFor(14_999_99, limit))
        assertEquals(CapLevel.NOTICE, thresholds.levelFor(15_000_00, limit))
        assertEquals(CapLevel.WARNING, thresholds.levelFor(18_000_00, limit))
        assertEquals(CapLevel.OVER, thresholds.levelFor(20_000_00, limit))
        assertEquals(CapLevel.OVER, thresholds.levelFor(25_000_00, limit))
    }

    @Test
    fun `edited thresholds change the level`() {
        val thresholds = CapThresholds(50, 60, 80)
        assertEquals(CapLevel.NOTICE, thresholds.levelFor(50, 100))
        assertEquals(CapLevel.OVER, thresholds.levelFor(80, 100))
    }

    @Test
    fun `largest amounts don't overflow`() {
        assertEquals(CapLevel.OVER, CapThresholds.DEFAULT.levelFor(100_000_000_000_000, 100_000_000_000_000))
    }

    @Test
    fun `rejects out-of-range or unordered values`() {
        for (values in listOf(listOf(0, 90, 100), listOf(75, 90, 101), listOf(90, 75, 100), listOf(75, 75, 100), listOf(75, 90))) {
            assertThrows(IllegalArgumentException::class.java) { CapThresholds.fromList(values) }
        }
    }
}
