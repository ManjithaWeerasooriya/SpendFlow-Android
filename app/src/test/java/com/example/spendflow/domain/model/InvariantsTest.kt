package com.example.spendflow.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class InvariantsTest {
    private fun goal(name: String = "Trip", target: Long = 10_000, saved: Long = 0, savedCurrency: String = "USD") =
        SavingsGoal("g", name, Money(target, "USD"), Money(saved, savedCurrency))

    private fun rejected(block: () -> Unit) = assertThrows(IllegalArgumentException::class.java) { block() }

    @Test
    fun `goal is completed exactly when saved reaches target`() {
        assertFalse(goal(saved = 9_999).isCompleted)
        assertTrue(goal(saved = 10_000).isCompleted)
        assertTrue(goal(saved = 12_000).isCompleted)
    }

    @Test
    fun `goal rejects invalid name, target, saved and currency mismatch`() {
        rejected { goal(name = " ") }
        rejected { goal(name = "x".repeat(101)) }
        rejected { goal(target = 0) }
        rejected { goal(target = 100_000_000_000L) }
        rejected { goal(saved = -1) }
        rejected { goal(savedCurrency = "LKR") }
        goal(name = "x".repeat(100), target = 99_999_999_999L)
    }

    @Test
    fun `budget rejects blank name, non positive limit and UNDER alert`() {
        fun budget(name: String = "B", limit: Long = 1, alerts: Set<CapLevel> = CapLevel.ALERTS) =
            Budget("b", name, Money(limit, "USD"), BudgetPeriod.MONTHLY, LocalDate.of(2026, 1, 1), alerts = alerts)
        budget()
        rejected { budget(name = "") }
        rejected { budget(limit = 0) }
        rejected { budget(alerts = setOf(CapLevel.UNDER)) }
    }

    @Test
    fun `user requires an ISO base currency`() {
        assertEquals("LKR", User.DEFAULT_BASE_CURRENCY)
        rejected { User("u", null, null, "usd") }
        assertEquals("USD", User("u", null, null, "USD").baseCurrency)
    }

    @Test
    fun `preferences default to streaks off and default thresholds`() {
        val p = UserPreferences()
        assertFalse(p.streakEnabled)
        assertFalse(p.lockScreenQuickEntry)
        assertEquals(listOf(75, 90, 100), p.capThresholds.asList())
    }

    @Test
    fun `cap thresholds validate range, order and list size`() {
        rejected { CapThresholds(0, 50, 100) }
        rejected { CapThresholds(50, 90, 101) }
        rejected { CapThresholds(90, 90, 100) }
        rejected { CapThresholds.fromList(listOf(1, 2)) }
        assertEquals(CapThresholds(10, 20, 30), CapThresholds.fromList(listOf(10, 20, 30)))
    }

    @Test
    fun `cap levels switch exactly at each threshold`() {
        val t = CapThresholds.DEFAULT
        assertEquals(CapLevel.UNDER, t.levelFor(74, 100))
        assertEquals(CapLevel.NOTICE, t.levelFor(75, 100))
        assertEquals(CapLevel.WARNING, t.levelFor(90, 100))
        assertEquals(CapLevel.OVER, t.levelFor(100, 100))
        rejected { t.levelFor(1, 0) }
    }

    @Test
    fun `converter returns exact converted values and rounds half even`() {
        assertEquals(Money(30_000, "LKR"), CurrencyConverter.convert(Money(1_000, "USD"), BigDecimal("30"), "LKR"))
        // 0.005 -> 0.00 (even), 0.015 -> 0.02 (even)
        assertEquals(Money(0, "USD"), CurrencyConverter.convert(Money(1, "USD"), BigDecimal("0.5"), "USD"))
        assertEquals(Money(2, "USD"), CurrencyConverter.convert(Money(3, "USD"), BigDecimal("0.5"), "USD"))
    }
}
