package com.example.spendflow.domain.model

import org.junit.Assert.assertThrows
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

class TransactionTest {

    private val lkr = Money(250_000, "LKR")
    private val now = Instant.parse("2026-10-19T10:00:00Z")

    private fun transaction(
        type: TransactionType = TransactionType.EXPENSE,
        amount: Money = lkr,
        rate: String = "1",
        baseAmount: Money = lkr,
        categoryId: String? = "food",
        paymentMethod: PaymentMethod? = PaymentMethod.CASH,
        accountId: String? = null,
        transferAccountId: String? = null,
    ) = Transaction(
        id = "t1",
        type = type,
        amount = amount,
        rate = BigDecimal(rate),
        baseAmount = baseAmount,
        dateTime = now,
        categoryId = categoryId,
        paymentMethod = paymentMethod,
        accountId = accountId,
        transferAccountId = transferAccountId,
    )

    private fun assertRejected(block: () -> Unit) {
        assertThrows(IllegalArgumentException::class.java) { block() }
    }

    @Test
    fun `accepts base-currency expense, foreign income and transfer`() {
        transaction()
        transaction(
            type = TransactionType.INCOME, amount = Money(100_00, "USD"), rate = "300.25",
            baseAmount = Money(3_002_500, "LKR"), paymentMethod = null,
        )
        transaction(
            type = TransactionType.TRANSFER, categoryId = null, paymentMethod = null,
            accountId = "wallet", transferAccountId = "bank",
        )
    }

    @Test
    fun `foreign expense is allowed`() {
        transaction(amount = Money(20_00, "EUR"), rate = "325.10", baseAmount = Money(650_200, "LKR"))
    }

    @Test
    fun `rejects non-positive amount or rate and negative base amount`() {
        assertRejected { transaction(amount = Money(0, "LKR"), baseAmount = Money(0, "LKR")) }
        assertRejected { transaction(amount = Money(100, "USD"), rate = "0", baseAmount = Money(0, "LKR")) }
        assertRejected { transaction(amount = Money(100, "USD"), rate = "-2", baseAmount = Money(100, "LKR")) }
        assertRejected { transaction(amount = Money(100, "USD"), rate = "3", baseAmount = Money(-300, "LKR")) }
    }

    @Test
    fun `base currency entries need rate one and matching base amount`() {
        assertRejected { transaction(rate = "1.5") }
        assertRejected { transaction(baseAmount = Money(1, "LKR")) }
        transaction(rate = "1.00")
    }

    @Test
    fun `payment method is required for expenses only`() {
        assertRejected { transaction(paymentMethod = null) }
        assertRejected { transaction(type = TransactionType.INCOME, paymentMethod = PaymentMethod.CASH) }
    }

    @Test
    fun `transfer rules`() {
        fun transfer(accountId: String?, transferAccountId: String?, categoryId: String? = null) = transaction(
            type = TransactionType.TRANSFER, paymentMethod = null, categoryId = categoryId,
            accountId = accountId, transferAccountId = transferAccountId,
        )
        assertRejected { transfer(accountId = null, transferAccountId = "bank") }
        assertRejected { transfer(accountId = "wallet", transferAccountId = null) }
        assertRejected { transfer(accountId = "wallet", transferAccountId = "wallet") }
        assertRejected { transfer(accountId = "wallet", transferAccountId = "bank", categoryId = "food") }
        assertRejected { transaction(transferAccountId = "bank") }
    }

    @Test
    fun `recurring rejects bad interval and dates`() {
        val start = LocalDate.of(2026, 10, 1)
        fun recurring(interval: Int = 1, endDate: LocalDate? = null, next: LocalDate = start) =
            RecurringTransaction(
                id = "r1",
                type = TransactionType.EXPENSE,
                amount = lkr,
                rate = BigDecimal.ONE,
                baseAmount = lkr,
                categoryId = "rent",
                paymentMethod = PaymentMethod.BANK_TRANSFER,
                frequency = RecurrenceFrequency.MONTHLY,
                interval = interval,
                startDate = start,
                endDate = endDate,
                nextOccurrence = next,
            )

        recurring()
        assertRejected { recurring(interval = 0) }
        assertRejected { recurring(endDate = start.minusDays(1)) }
        assertRejected { recurring(next = start.minusDays(1)) }
    }
}
