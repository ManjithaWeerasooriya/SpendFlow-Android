package com.example.spendflow.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SavingsGoalTest {
    private val today = LocalDate.of(2026, 10, 11)
    private fun goal(target: Long, saved: Long, deadline: LocalDate?) =
        SavingsGoal("g", "MacBook", Money(target, "LKR"), Money(saved, "LKR"), deadline)

    @Test fun `MacBook needs 52000 a month`() {
        val g = goal(500_000_00, 240_000_00, LocalDate.of(2027, 3, 31))
        assertEquals(5, g.monthsRemaining(today))
        assertEquals(Money(52_000_00, "LKR"), g.requiredMonthly(today))
        assertEquals(Money(260_000_00, "LKR"), g.remaining)
    }

    @Test fun `months round down`() = assertEquals(2, wholeMonthsBetween(today, LocalDate.of(2026, 12, 10)))

    @Test fun `months are at least one`() {
        assertEquals(1, wholeMonthsBetween(today, LocalDate.of(2026, 10, 20)))
        assertEquals(1, wholeMonthsBetween(today, LocalDate.of(2026, 1, 1)))
    }

    @Test fun `required saving rounds up`() = assertEquals(34L, requiredMonthlySaving(100, 0, LocalDate.of(2027, 1, 11), today))

    @Test fun `completed goal needs nothing and has zero remaining`() {
        val g = goal(100, 150, LocalDate.of(2027, 1, 1))
        assertTrue(g.isCompleted)
        assertEquals(0L, g.remaining.amountMinor)
        assertEquals(0L, g.requiredMonthly(today)!!.amountMinor)
    }

    @Test fun `no deadline means no schedule`() {
        val g = goal(100, 0, null)
        assertNull(g.monthsRemaining(today))
        assertNull(g.requiredMonthly(today))
    }
}
