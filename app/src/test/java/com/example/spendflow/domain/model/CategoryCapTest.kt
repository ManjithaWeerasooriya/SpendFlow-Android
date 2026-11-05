package com.example.spendflow.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class CategoryCapTest {

    private val month = YearMonth.of(2026, 10)
    private val start = LocalDate.of(2026, 10, 1)

    private fun cap(id: String, categoryId: String?, limit: Long, alerts: Set<CapLevel> = CapLevel.ALERTS, currency: String = "LKR") =
        Budget(id, "Cap $id", Money(limit, currency), BudgetPeriod.MONTHLY, start, categoryId, alerts)

    private fun summary(vararg spent: Pair<String, Long>) = MonthlySummary(
        month = month,
        totalIncome = Money.zero("LKR"),
        totalExpenses = Money(spent.sumOf { it.second }, "LKR"),
        expensesByCategory = spent.associate { (id, amount) -> id to Money(amount, "LKR") },
    )

    private fun usage(spent: Long, alerts: Set<CapLevel> = CapLevel.ALERTS, thresholds: CapThresholds = CapThresholds.DEFAULT) =
        CategoryCaps.usage(listOf(cap("c", "food", 10_000, alerts)), summary("food" to spent), thresholds).getValue("food")

    @Test
    fun `usage shows spent, remaining and level`() {
        val under = usage(5_000)
        assertEquals(Money(5_000, "LKR"), under.spent)
        assertEquals(Money(5_000, "LKR"), under.remaining)
        assertNull(under.overBy)
        assertEquals(CapLevel.UNDER, under.level)
        assertEquals(CapLevel.NOTICE, usage(7_500).level)
        assertEquals(CapLevel.WARNING, usage(9_000).level)
        assertEquals(CapLevel.OVER, usage(10_000).level)
    }

    @Test
    fun `overspending reports how far over`() {
        val over = usage(10_500)
        assertEquals(CapLevel.OVER, over.level)
        assertEquals(Money(-500, "LKR"), over.remaining)
        assertEquals(Money(500, "LKR"), over.overBy)
    }

    @Test
    fun `a capped category with no spending this month is at zero`() {
        val usage = CategoryCaps.usage(listOf(cap("c", "food", 10_000)), summary(), CapThresholds.DEFAULT).getValue("food")
        assertEquals(Money.zero("LKR"), usage.spent)
        assertEquals(CapLevel.UNDER, usage.level)
    }

    @Test
    fun `Settings thresholds decide the level`() {
        assertEquals(CapLevel.WARNING, usage(6_000, thresholds = CapThresholds(50, 60, 80)).level)
        assertEquals(CapLevel.OVER, usage(8_000, thresholds = CapThresholds(50, 60, 80)).level)
    }

    @Test
    fun `one cap per category, ignoring overall and non-monthly budgets`() {
        val weekly = Budget("w", "Weekly", Money(100, "LKR"), BudgetPeriod.WEEKLY, start, "food")
        val active = CategoryCaps.active(listOf(cap("b", "food", 2), cap("a", "food", 1), cap("all", null, 5), weekly))
        assertEquals(setOf("food"), active.keys)
        assertEquals("a", active.getValue("food").id)
    }

    @Test
    fun `a cap in an old base currency is not compared with this month's totals`() {
        val usage = CategoryCaps.usage(listOf(cap("c", "food", 10_000, currency = "USD")), summary("food" to 50_000), CapThresholds.DEFAULT)
            .getValue("food")
        assertNull(usage.spent)
        assertEquals(CapLevel.UNDER, usage.level)
    }

    @Test
    fun `zero or negative limits and UNDER alerts are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { cap("c", "food", 0) }
        assertThrows(IllegalArgumentException::class.java) { cap("c", "food", -500) }
        assertThrows(IllegalArgumentException::class.java) { cap("c", "food", 1, setOf(CapLevel.UNDER)) }
    }

    @Test
    fun `each threshold warns once per month`() {
        val shown = mutableMapOf<String, CapLevel>()
        fun check(spent: Long): List<CapWarning> =
            CapEvaluator.newWarnings(listOf(usage(spent)), month) { shown[it] ?: CapLevel.UNDER }
                .onEach { shown[it.budgetId] = it.level }

        assertTrue(check(5_000).isEmpty())
        assertEquals(listOf(CapLevel.NOTICE), check(7_500).map { it.level })
        assertTrue(check(8_000).isEmpty())
        assertEquals(listOf(CapLevel.WARNING), check(9_000).map { it.level })
        // An edit back under the threshold and over it again doesn't repeat the warning
        assertTrue(check(5_000).isEmpty())
        assertTrue(check(9_500).isEmpty())
        assertEquals(listOf(CapLevel.OVER), check(12_000).map { it.level })
        assertTrue(check(15_000).isEmpty())
    }

    @Test
    fun `thresholds crossed together give one warning at the highest`() {
        val warnings = CapEvaluator.newWarnings(listOf(usage(11_000)), month) { CapLevel.UNDER }
        assertEquals(listOf(CapLevel.OVER), warnings.map { it.level })
    }

    @Test
    fun `thresholds turned off for a cap never warn`() {
        val onlyOver = setOf(CapLevel.OVER)
        assertTrue(CapEvaluator.newWarnings(listOf(usage(9_500, onlyOver)), month) { CapLevel.UNDER }.isEmpty())
        assertEquals(CapLevel.OVER, CapEvaluator.alertLevel(usage(10_000, onlyOver)))
        assertTrue(CapEvaluator.newWarnings(listOf(usage(20_000, emptySet())), month) { CapLevel.UNDER }.isEmpty())
        // 90% reached with only the 75% alert on still reports that one
        assertEquals(CapLevel.NOTICE, CapEvaluator.alertLevel(usage(9_000, setOf(CapLevel.NOTICE))))
    }
}
