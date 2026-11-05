package com.example.spendflow.core.notifications

import com.example.spendflow.domain.model.Budget
import com.example.spendflow.domain.model.BudgetPeriod
import com.example.spendflow.domain.model.CapLevel
import com.example.spendflow.domain.model.CapThresholds
import com.example.spendflow.domain.model.CapUsage
import com.example.spendflow.domain.model.Money
import com.example.spendflow.testing.FakeUserSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class CapWarningsTest {

    private class History : CapAlertHistory {
        val shown = mutableMapOf<String, CapLevel>()
        override fun lastShown(uid: String, month: YearMonth, budgetId: String) = shown["$uid/$month/$budgetId"] ?: CapLevel.UNDER
        override fun record(uid: String, month: YearMonth, budgetId: String, level: CapLevel) {
            shown["$uid/$month/$budgetId"] = level
        }
    }

    private val history = History()
    private val session = FakeUserSession("alice")
    private val warnings = CapWarnings(history, session)
    private val october = YearMonth.of(2026, 10)

    private fun usage(budgetId: String, spent: Long) = CapUsage(
        "cat-$budgetId",
        Budget(budgetId, budgetId, Money(10_000, "LKR"), BudgetPeriod.MONTHLY, LocalDate.of(2026, 10, 1), "cat-$budgetId"),
        Money(spent, "LKR"),
        CapThresholds.DEFAULT,
    )

    @Test
    fun `a warning is pending once and remembered across screens and restarts`() {
        warnings.check(october, listOf(usage("food", 7_600)))
        assertEquals(listOf(CapLevel.NOTICE), warnings.pending.value.map { it.level })
        warnings.dismiss(warnings.pending.value.single())

        // Home and Budget both check the same usage; a new process reads the same history
        warnings.check(october, listOf(usage("food", 7_600)))
        CapWarnings(history, session).check(october, listOf(usage("food", 7_600)))
        assertTrue(warnings.pending.value.isEmpty())
    }

    @Test
    fun `a higher level replaces the pending warning for the same cap`() {
        warnings.check(october, listOf(usage("food", 7_600), usage("fun", 9_100)))
        warnings.check(october, listOf(usage("food", 10_100), usage("fun", 9_100)))
        assertEquals(
            listOf("fun" to CapLevel.WARNING, "food" to CapLevel.OVER),
            warnings.pending.value.map { it.budgetId to it.level },
        )
    }

    @Test
    fun `a shown warning follows edits, goes when spending drops below it, and dismisses from an older copy`() {
        warnings.check(october, listOf(usage("food", 10_500)))
        assertEquals(Money(500, "LKR"), warnings.pending.value.single().usage.overBy)
        warnings.check(october, listOf(usage("food", 11_000)))
        assertEquals(Money(1_000, "LKR"), warnings.pending.value.single().usage.overBy)

        val shownBeforeEdit = warnings.pending.value.single()
        warnings.check(october, listOf(usage("food", 12_000)))
        warnings.dismiss(shownBeforeEdit)
        assertTrue(warnings.pending.value.isEmpty())

        warnings.check(october, listOf(usage("food", 10_500)))
        assertTrue(warnings.pending.value.isEmpty())
        warnings.check(october, listOf(usage("fun", 7_600)))
        warnings.check(october, listOf(usage("fun", 5_000)))
        assertTrue(warnings.pending.value.isEmpty())
        // Back over the threshold the same month: already shown, so it stays quiet
        warnings.check(october, listOf(usage("fun", 7_600)))
        assertTrue(warnings.pending.value.isEmpty())
    }

    @Test
    fun `removing the cap clears its warning`() {
        warnings.check(october, listOf(usage("food", 9_000), usage("fun", 9_000)))
        warnings.check(october, listOf(usage("fun", 9_000)))
        assertEquals(listOf("fun"), warnings.pending.value.map { it.budgetId })
    }

    @Test
    fun `a new month warns again`() {
        warnings.check(october, listOf(usage("food", 9_000)))
        warnings.dismiss(warnings.pending.value.single())
        warnings.check(october.plusMonths(1), listOf(usage("food", 9_000)))
        assertEquals(listOf(october.plusMonths(1)), warnings.pending.value.map { it.month })
    }

    @Test
    fun `warnings are per user and cleared when another user signs in`() {
        warnings.check(october, listOf(usage("food", 9_000)))
        session.signIn("bob")
        warnings.check(october, listOf(usage("other", 100)))
        assertTrue(warnings.pending.value.isEmpty())
        warnings.check(october, listOf(usage("food", 9_000)))
        assertEquals(1, warnings.pending.value.size)
    }

    @Test
    fun `nothing is checked while signed out`() {
        session.signOut()
        warnings.check(october, listOf(usage("food", 20_000)))
        assertTrue(warnings.pending.value.isEmpty())
        assertTrue(history.shown.isEmpty())
    }
}
