package com.example.spendflow.domain.model

import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class TransactionTest {

    private val usd10 = Money(1_000, "USD")
    private val now = Instant.parse("2026-10-19T10:00:00Z")

    private fun transaction(
        type: TransactionType = TransactionType.EXPENSE,
        amount: Money = usd10,
        categoryId: String? = "food",
        transferAccountId: String? = null,
    ) = Transaction(
        id = "t1",
        type = type,
        amount = amount,
        accountId = "wallet",
        categoryId = categoryId,
        transferAccountId = transferAccountId,
        occurredAt = now,
    )

    @Test
    fun `accepts valid expense and transfer`() {
        transaction()
        transaction(type = TransactionType.TRANSFER, categoryId = null, transferAccountId = "bank")
    }

    @Test
    fun `rejects non-positive amount`() {
        assertThrows(IllegalArgumentException::class.java) { transaction(amount = Money(0, "USD")) }
        assertThrows(IllegalArgumentException::class.java) { transaction(amount = Money(-1, "USD")) }
    }

    @Test
    fun `rejects transfer without a different destination`() {
        assertThrows(IllegalArgumentException::class.java) {
            transaction(type = TransactionType.TRANSFER, categoryId = null)
        }
        assertThrows(IllegalArgumentException::class.java) {
            transaction(type = TransactionType.TRANSFER, categoryId = null, transferAccountId = "wallet")
        }
    }

    @Test
    fun `rejects category on transfer and destination on expense`() {
        assertThrows(IllegalArgumentException::class.java) {
            transaction(type = TransactionType.TRANSFER, transferAccountId = "bank")
        }
        assertThrows(IllegalArgumentException::class.java) {
            transaction(transferAccountId = "bank")
        }
    }

    @Test
    fun `recurring rejects bad interval and dates`() {
        val start = LocalDate.of(2026, 10, 1)
        fun recurring(interval: Int = 1, endDate: LocalDate? = null, next: LocalDate = start) =
            RecurringTransaction(
                id = "r1",
                type = TransactionType.EXPENSE,
                amount = usd10,
                accountId = "wallet",
                categoryId = "rent",
                frequency = RecurrenceFrequency.MONTHLY,
                interval = interval,
                startDate = start,
                endDate = endDate,
                nextOccurrence = next,
            )

        recurring()
        assertThrows(IllegalArgumentException::class.java) { recurring(interval = 0) }
        assertThrows(IllegalArgumentException::class.java) { recurring(endDate = start.minusDays(1)) }
        assertThrows(IllegalArgumentException::class.java) { recurring(next = start.minusDays(1)) }
    }
}
