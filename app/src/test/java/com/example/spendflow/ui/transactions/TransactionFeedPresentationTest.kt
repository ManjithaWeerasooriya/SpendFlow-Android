package com.example.spendflow.ui.transactions

import com.example.spendflow.domain.model.Money
import com.example.spendflow.domain.model.Transaction
import com.example.spendflow.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

class TransactionFeedPresentationTest {
    private fun entry(id: String, instant: String) = Transaction(
        id, TransactionType.INCOME, Money(100, "LKR"), BigDecimal.ONE,
        Money(100, "LKR"), Instant.parse(instant), null, null,
    )

    @Test fun `month boundaries use local dates and sort newest first`() {
        val rows = listOf(
            entry("old", "2026-09-30T18:29:59Z"),
            entry("start", "2026-09-30T18:30:00Z"),
            entry("next", "2026-10-31T18:30:00Z"),
            entry("end", "2026-10-31T18:29:59Z"),
        )
        assertEquals(listOf("end", "start"), transactionsForMonth(
            rows, YearMonth.of(2026, 10), ZoneId.of("Asia/Colombo"),
        ).map { it.id })
        assertEquals(listOf("next"), transactionsForMonth(
            rows, YearMonth.of(2026, 11), ZoneId.of("Asia/Colombo"),
        ).map { it.id })
    }

    @Test fun `empty selected month has no rows`() {
        assertTrue(transactionsForMonth(listOf(entry("other", "2026-09-01T00:00:00Z")),
            YearMonth.of(2026, 10), ZoneId.of("UTC")).isEmpty())
    }
}
