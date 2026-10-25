package com.example.spendflow.data.remote.mapper

import com.example.spendflow.data.remote.FirestoreFields
import com.example.spendflow.domain.model.Account
import com.example.spendflow.domain.model.AccountType
import com.example.spendflow.domain.model.Budget
import com.example.spendflow.domain.model.BudgetPeriod
import com.example.spendflow.domain.model.Category
import com.example.spendflow.domain.model.CategoryType
import com.example.spendflow.domain.model.Money
import com.example.spendflow.domain.model.PaymentMethod
import com.example.spendflow.domain.model.RecurrenceFrequency
import com.example.spendflow.domain.model.RecurringTransaction
import com.example.spendflow.domain.model.Transaction
import com.example.spendflow.domain.model.TransactionType
import com.example.spendflow.domain.model.User
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

class MapperRoundTripTest {

    private val usd = Money(12_345, "USD")

    @Test
    fun user() {
        val user = User(id = "u1", displayName = "Sam", email = "sam@example.com", baseCurrency = "LKR")
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
            iconName = "restaurant", colorArgb = 0xFFE91E63.toInt(), sortOrder = 3,
        )
        assertEquals(category, categoryFromFirestore("c1", category.toFirestore()))
    }

    @Test
    fun `legacy category defaults to zero order`() {
        val category = Category("food", "Food", CategoryType.EXPENSE)
        assertEquals(category, categoryFromFirestore("food", category.toFirestore() - "sortOrder"))
    }

    @Test
    fun `expense without optional details round trips`() {
        val transaction = Transaction(
            id = "quick", type = TransactionType.EXPENSE, amount = Money(2500, "LKR"),
            rate = BigDecimal.ONE, baseAmount = Money(2500, "LKR"),
            dateTime = Instant.parse("2026-10-22T10:00:00Z"), categoryId = "food", paymentMethod = null,
        )
        assertEquals(transaction, transactionFromFirestore("quick", transaction.toFirestore()))
    }

    @Test
    fun transaction() {
        val transaction = Transaction(
            id = "t1", type = TransactionType.EXPENSE, amount = Money(20_00, "EUR"),
            // Scale is kept, so "325.10" must read back as "325.10", not "325.1"
            rate = BigDecimal("325.10"), baseAmount = Money(650_200, "LKR"),
            dateTime = Instant.parse("2026-10-19T08:30:15.123456789Z"), categoryId = "c1",
            paymentMethod = PaymentMethod.CREDIT_CARD, note = "Dinner in Paris", accountId = "a1",
            recurringTransactionId = "r1",
        )
        assertEquals(transaction, transactionFromFirestore("t1", transaction.toFirestore()))
    }

    @Test
    fun `transaction reads server timestamps`() {
        val transaction = Transaction(
            id = "t2", type = TransactionType.TRANSFER, amount = usd, rate = BigDecimal.ONE, baseAmount = usd,
            dateTime = Instant.parse("2026-10-19T08:30:00Z"), categoryId = null, paymentMethod = null,
            accountId = "a1", transferAccountId = "a2",
        )
        val created = Instant.parse("2026-10-19T08:31:00Z")
        val stored = transaction.toFirestore() + mapOf(
            FirestoreFields.CREATED_AT to created.toTimestamp(),
            FirestoreFields.UPDATED_AT to created.toTimestamp(),
        )
        assertEquals(
            transaction.copy(createdAt = created, updatedAt = created),
            transactionFromFirestore("t2", stored),
        )
    }

    @Test
    fun `transaction uses the field names from the acceptance criteria`() {
        val stored = Transaction(
            id = "t3", type = TransactionType.INCOME, amount = Money(100_00, "USD"), rate = BigDecimal("300.25"),
            baseAmount = Money(3_002_500, "LKR"), dateTime = Instant.parse("2026-10-19T08:30:00Z"),
            categoryId = "salary", paymentMethod = null,
        ).toFirestore()
        listOf("amount", "currency", "rate", "baseAmount", "categoryId", "dateTime", "paymentMethod", "note")
            .forEach { assertTrue("missing $it", it in stored) }
        assertEquals("300.25", stored["rate"])
        assertEquals(10_000L, stored["amount"])
        assertEquals(3_002_500L, stored["baseAmount"])
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
            id = "r1", type = TransactionType.EXPENSE, amount = usd, rate = BigDecimal("300.5"),
            baseAmount = Money(3_709_672, "LKR"), categoryId = "rent", paymentMethod = PaymentMethod.BANK_TRANSFER,
            frequency = RecurrenceFrequency.WEEKLY, interval = 2, startDate = LocalDate.of(2026, 1, 1),
            endDate = LocalDate.of(2026, 12, 31), nextOccurrence = LocalDate.of(2026, 10, 22), isActive = false,
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
