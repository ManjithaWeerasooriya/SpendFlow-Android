package com.example.spendflow.data.remote.mapper

import com.example.spendflow.data.remote.FirestoreFields
import com.example.spendflow.domain.model.Account
import com.example.spendflow.domain.model.AccountType
import com.example.spendflow.domain.model.Budget
import com.example.spendflow.domain.model.BudgetPeriod
import com.example.spendflow.domain.model.Category
import com.example.spendflow.domain.model.CategoryType
import com.example.spendflow.domain.model.Money
import com.example.spendflow.domain.model.RecurrenceFrequency
import com.example.spendflow.domain.model.RecurringTransaction
import com.example.spendflow.domain.model.Transaction
import com.example.spendflow.domain.model.TransactionType
import com.example.spendflow.domain.model.User
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class MapperRoundTripTest {

    private val usd = Money(12_345, "USD")

    @Test
    fun user() {
        val user = User(id = "u1", displayName = "Sam", email = "sam@example.com", defaultCurrency = "LKR")
        assertEquals(user, userFromFirestore("u1", user.toFirestore()))
    }

    @Test
    fun account() {
        val account = Account(
            id = "a1", name = "Visa", type = AccountType.CREDIT_CARD,
            balance = Money(-5_000, "USD"), isArchived = true,
        )
        assertEquals(account, accountFromFirestore("a1", account.toFirestore()))
    }

    @Test
    fun category() {
        // Opaque ARGB colors are negative ints, so this also checks the Long round trip
        val category = Category(
            id = "c1", name = "Food", type = CategoryType.EXPENSE,
            iconName = "restaurant", colorArgb = 0xFFE91E63.toInt(),
        )
        assertEquals(category, categoryFromFirestore("c1", category.toFirestore()))
    }

    @Test
    fun transaction() {
        val transaction = Transaction(
            id = "t1", type = TransactionType.TRANSFER, amount = usd,
            accountId = "a1", categoryId = null, transferAccountId = "a2",
            note = "Rent float", occurredAt = Instant.parse("2026-10-19T08:30:15.123456789Z"),
            recurringTransactionId = "r1",
        )
        assertEquals(transaction, transactionFromFirestore("t1", transaction.toFirestore()))
    }

    @Test
    fun budget() {
        val budget = Budget(
            id = "b1", name = "Groceries", limit = usd, period = BudgetPeriod.MONTHLY,
            startDate = LocalDate.of(2026, 10, 1), categoryId = "c1",
        )
        assertEquals(budget, budgetFromFirestore("b1", budget.toFirestore()))
    }

    @Test
    fun recurringTransaction() {
        val recurring = RecurringTransaction(
            id = "r1", type = TransactionType.EXPENSE, amount = usd,
            accountId = "a1", categoryId = "rent", frequency = RecurrenceFrequency.WEEKLY,
            interval = 2, startDate = LocalDate.of(2026, 1, 1), endDate = LocalDate.of(2026, 12, 31),
            nextOccurrence = LocalDate.of(2026, 10, 22), isActive = false,
        )
        assertEquals(recurring, recurringTransactionFromFirestore("r1", recurring.toFirestore()))
    }

    @Test
    fun `stores query fields under their shared names`() {
        val account = Account(id = "a1", name = "Cash", type = AccountType.CASH, balance = usd, isArchived = true)
        assertEquals(true, account.toFirestore()[FirestoreFields.IS_ARCHIVED])

        val budget = Budget(
            id = "b1", name = "All", limit = usd, period = BudgetPeriod.YEARLY,
            startDate = LocalDate.of(2026, 1, 5),
        )
        assertEquals("2026-01-05", budget.toFirestore()["startDate"])
    }
}
