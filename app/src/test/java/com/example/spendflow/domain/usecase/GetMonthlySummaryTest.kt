package com.example.spendflow.domain.usecase

import app.cash.turbine.test
import com.example.spendflow.core.common.AppError
import com.example.spendflow.core.common.AppResult
import com.example.spendflow.data.repository.TransactionRepository
import com.example.spendflow.data.repository.UserRepository
import com.example.spendflow.domain.model.Category
import com.example.spendflow.domain.model.CategoryType
import com.example.spendflow.domain.model.Money
import com.example.spendflow.domain.model.MonthlySummary
import com.example.spendflow.domain.model.Transaction
import com.example.spendflow.domain.model.TransactionType
import com.example.spendflow.domain.model.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

class GetMonthlySummaryTest {
    private val month = YearMonth.of(2026, 10)
    private val zone = ZoneId.of("Asia/Colombo")
    private val user = User("user", null, null, "LKR")
    private val users = Profiles(user)
    private val transactions = Transactions()
    private val useCase = GetMonthlySummary(transactions, users)

    @Test
    fun `uses saved base amounts and the repository month query without counting transfers`() = runTest {
        val income = transaction("salary", TransactionType.INCOME, 18_500_000).copy(
            amount = Money(50_000, "USD"),
            rate = BigDecimal("370"),
        )
        val expense = transaction("rent", TransactionType.EXPENSE, 11_240_000)
        val transfer = transaction("transfer", TransactionType.TRANSFER, 250_000)
        val otherBaseCurrency = transaction("other", TransactionType.INCOME, 500).copy(
            amount = Money(500, "USD"),
            baseAmount = Money(500, "USD"),
        )
        transactions.rows.value = AppResult.Success(listOf(income, expense, transfer, otherBaseCurrency))

        useCase(month, zone).test {
            val summary = (awaitItem() as AppResult.Success).data
            assertEquals(month, summary.month)
            assertEquals(Money(18_500_000, "LKR"), summary.totalIncome)
            assertEquals(Money(11_240_000, "LKR"), summary.totalExpenses)
            assertEquals(Money(7_260_000, "LKR"), summary.netSavings)
            assertEquals(
                Instant.parse("2026-09-30T18:30:00Z") to Instant.parse("2026-10-31T18:30:00Z"),
                transactions.observedRange,
            )
        }
    }

    @Test
    fun `breakdowns retain historical IDs and reconcile with totals in the profile currency`() = runTest {
        val archived = Category("archived-food", "Food", CategoryType.EXPENSE, isArchived = true)
        val deletedSourceId = "deleted-freelance"
        // Deliberately differs from a fresh conversion using amount and rate.
        val foreignIncome = transaction("foreign", TransactionType.INCOME, 28_750, deletedSourceId).copy(
            amount = Money(100, "USD"),
            rate = BigDecimal("300"),
        )
        val wrongBaseCurrency = transaction("wrong", TransactionType.EXPENSE, 100, "wrong-currency").copy(
            amount = Money(100, "USD"),
            baseAmount = Money(100, "USD"),
        )
        transactions.rows.value = AppResult.Success(
            listOf(
                transaction("meal", TransactionType.EXPENSE, 500, archived.id),
                transaction("snack", TransactionType.EXPENSE, 300, archived.id),
                transaction("bus", TransactionType.EXPENSE, 200, "transport"),
                transaction("misc", TransactionType.EXPENSE, 100),
                transaction("pay", TransactionType.INCOME, 50_000, "salary"),
                transaction("bonus", TransactionType.INCOME, 10_000, "salary"),
                foreignIncome,
                transaction("gift", TransactionType.INCOME, 750),
                transaction("move", TransactionType.TRANSFER, 500_000),
                wrongBaseCurrency,
            ),
        )

        useCase(month, zone).test {
            val summary = (awaitItem() as AppResult.Success).data
            assertEquals(
                mapOf(archived.id to Money(800, "LKR"), "transport" to Money(200, "LKR"), null to Money(100, "LKR")),
                summary.expensesByCategory,
            )
            assertEquals(
                mapOf("salary" to Money(60_000, "LKR"), deletedSourceId to Money(28_750, "LKR"), null to Money(750, "LKR")),
                summary.incomeBySource,
            )
            assertEquals(
                mapOf(
                    TransactionType.INCOME to Money(89_500, "LKR"),
                    TransactionType.EXPENSE to Money(1_100, "LKR"),
                    TransactionType.TRANSFER to Money(500_000, "LKR"),
                ),
                summary.totalsByClassification,
            )
            assertEquals(summary.totalExpenses.amountMinor, summary.expensesByCategory.values.sumOf { it.amountMinor })
            assertEquals(summary.totalIncome.amountMinor, summary.incomeBySource.values.sumOf { it.amountMinor })
            assertEquals(Money(88_400, "LKR"), summary.netSavings)
        }
    }

    @Test
    fun `recalculates breakdowns after add edit and delete and preserves repository errors`() = runTest {
        val expense = transaction("expense", TransactionType.EXPENSE, 500, "food")
        useCase(month, zone).test {
            val empty = MonthlySummary(month, Money.zero("LKR"), Money.zero("LKR"))
            assertEquals(AppResult.Success(empty), awaitItem())

            transactions.rows.value = AppResult.Success(listOf(expense))
            val added = (awaitItem() as AppResult.Success).data
            assertEquals(Money(-500, "LKR"), added.netSavings)
            assertEquals(mapOf("food" to Money(500, "LKR")), added.expensesByCategory)

            val editedExpense = transaction(expense.id, TransactionType.EXPENSE, 700, "transport")
            transactions.rows.value = AppResult.Success(listOf(editedExpense))
            val edited = (awaitItem() as AppResult.Success).data
            assertEquals(Money(700, "LKR"), edited.totalExpenses)
            assertEquals(mapOf("transport" to Money(700, "LKR")), edited.expensesByCategory)
            assertEquals(mapOf(TransactionType.EXPENSE to Money(700, "LKR")), edited.totalsByClassification)

            transactions.rows.value = AppResult.Success(emptyList())
            assertEquals(AppResult.Success(empty), awaitItem())

            transactions.rows.value = AppResult.Error(AppError.Network)
            assertEquals(AppResult.Error(AppError.Network), awaitItem())
        }
    }

    @Test
    fun `does not guess a base currency before the profile exists`() = runTest {
        users.profile.value = AppResult.Error(AppError.PermissionDenied)
        useCase(month, zone).test {
            assertEquals(AppResult.Error(AppError.PermissionDenied), awaitItem())
            users.profile.value = AppResult.Success(null)
            assertEquals(AppResult.Error(AppError.NotFound), awaitItem())
            assertNull(transactions.observedRange)

            users.profile.value = AppResult.Success(user.copy(baseCurrency = "EUR"))
            val summary = (awaitItem() as AppResult.Success).data
            assertEquals(Money.zero("EUR"), summary.totalIncome)
            assertEquals(Money.zero("EUR"), summary.totalExpenses)
        }
    }

    private fun transaction(id: String, type: TransactionType, minor: Long, categoryId: String? = null) = Transaction(
        id = id,
        type = type,
        amount = Money(minor, "LKR"),
        rate = BigDecimal.ONE,
        baseAmount = Money(minor, "LKR"),
        dateTime = Instant.parse("2026-10-15T12:00:00Z"),
        categoryId = categoryId,
        paymentMethod = null,
        accountId = if (type == TransactionType.TRANSFER) "source" else null,
        transferAccountId = if (type == TransactionType.TRANSFER) "destination" else null,
    )

    private class Profiles(user: User) : UserRepository {
        val profile = MutableStateFlow<AppResult<User?>>(AppResult.Success(user))
        override fun observeProfile() = profile
        override suspend fun createProfile(user: User) = error("Unexpected profile write")
        override suspend fun createProfileIfMissing(user: User) = error("Unexpected profile write")
        override suspend fun updateProfile(user: User) = error("Unexpected profile write")
    }

    private class Transactions : TransactionRepository {
        val rows = MutableStateFlow<AppResult<List<Transaction>>>(AppResult.Success(emptyList()))
        var observedRange: Pair<Instant, Instant>? = null

        override fun observeTransactions(from: Instant, until: Instant): MutableStateFlow<AppResult<List<Transaction>>> {
            observedRange = from to until
            return rows
        }

        override fun observeRecent(limit: Int) = error("Unexpected recent query")
        override fun newId() = error("Unexpected transaction write")
        override suspend fun get(id: String) = error("Unexpected transaction lookup")
        override suspend fun create(item: Transaction) = error("Unexpected transaction write")
        override suspend fun update(item: Transaction) = error("Unexpected transaction write")
        override suspend fun delete(id: String) = error("Unexpected transaction write")
    }
}
