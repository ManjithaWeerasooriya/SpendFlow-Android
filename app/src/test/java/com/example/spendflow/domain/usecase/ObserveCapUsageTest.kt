package com.example.spendflow.domain.usecase

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.example.spendflow.core.common.AppResult
import com.example.spendflow.domain.model.Budget
import com.example.spendflow.domain.model.BudgetPeriod
import com.example.spendflow.domain.model.CapLevel
import com.example.spendflow.domain.model.CapThresholds
import com.example.spendflow.domain.model.Money
import com.example.spendflow.domain.model.User
import com.example.spendflow.testing.FakeBudgetRepository
import com.example.spendflow.testing.FakeProfileRepository
import com.example.spendflow.testing.FakeTransactionRepository
import com.example.spendflow.testing.expense
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset

/** AC4: progress recalculates when expenses change, when Settings thresholds change and when a month starts. */
class ObserveCapUsageTest {

    private val start = Instant.parse("2026-10-20T10:00:00Z")
    private val transactions = FakeTransactionRepository()
    private val profiles = FakeProfileRepository(User("u", null, null, "LKR"))
    private val budgets = FakeBudgetRepository(
        listOf(Budget("cap", "Food", Money(10_000, "LKR"), BudgetPeriod.MONTHLY, LocalDate.of(2026, 10, 1), "food")),
    )

    /** Runs with the test scheduler's virtual time, so waiting for the next month takes no real time. */
    private class SchedulerClock(private val scope: TestScope, private val origin: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?) = this
        override fun instant(): Instant = origin.plusMillis(scope.testScheduler.currentTime)
    }

    private fun TestScope.useCase(origin: Instant = start) = ObserveCapUsage(
        budgets,
        GetMonthlySummary(transactions, profiles),
        profiles,
        ClockCurrentMonth(SchedulerClock(this, origin)),
    )

    private suspend fun ReceiveTurbine<AppResult<CapOverview>>.next(): CapOverview = (awaitItem() as AppResult.Success).data

    @Test
    fun `adding, editing and deleting expenses recalculates the cap`() = runTest {
        useCase()().test {
            assertEquals(Money.zero("LKR"), next().usages.getValue("food").spent)

            transactions.create(expense("a", "food", 8_000, start))
            next().usages.getValue("food").let {
                assertEquals(Money(8_000, "LKR"), it.spent)
                assertEquals(CapLevel.NOTICE, it.level)
            }

            transactions.update(expense("a", "food", 12_000, start))
            assertEquals(Money(2_000, "LKR"), next().usages.getValue("food").overBy)

            transactions.delete("a")
            assertEquals(CapLevel.UNDER, next().usages.getValue("food").level)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `other categories and other months don't count`() = runTest {
        transactions.create(expense("x", "fun", 9_000, start))
        transactions.create(expense("y", "food", 9_000, Instant.parse("2026-09-30T10:00:00Z")))
        useCase()().test {
            val overview = next()
            assertEquals(Money.zero("LKR"), overview.usages.getValue("food").spent)
            assertEquals(Money(9_000, "LKR"), overview.spentByCategory["fun"])
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `edited Settings thresholds are used`() = runTest {
        transactions.create(expense("a", "food", 6_000, start))
        useCase()().test {
            assertEquals(CapLevel.UNDER, next().usages.getValue("food").level)
            profiles.setCapThresholds(CapThresholds(50, 60, 80))
            next().let {
                assertEquals(CapThresholds(50, 60, 80), it.thresholds)
                assertEquals(CapLevel.WARNING, it.usages.getValue("food").level)
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a new month starts from zero`() = runTest {
        val lastHour = Instant.parse("2026-10-31T23:00:00Z")
        transactions.create(expense("a", "food", 9_500, lastHour))
        useCase(origin = lastHour)().test {
            next().let {
                assertEquals(YearMonth.of(2026, 10), it.month)
                assertEquals(CapLevel.WARNING, it.usages.getValue("food").level)
            }
            next().let {
                assertEquals(YearMonth.of(2026, 11), it.month)
                assertEquals(Money.zero("LKR"), it.usages.getValue("food").spent)
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a cap left in the previous base currency is shown without usage`() = runTest {
        profiles.setBaseCurrency("USD")
        useCase()().test {
            val overview = next()
            assertEquals("USD", overview.currencyCode)
            assertNull(overview.usages.getValue("food").spent)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
