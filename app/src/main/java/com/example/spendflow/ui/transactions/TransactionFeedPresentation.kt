package com.example.spendflow.ui.transactions

import com.example.spendflow.domain.model.Transaction
import java.time.YearMonth
import java.time.ZoneId

/** Calendar boundaries follow the same local time zone as the displayed day headers. */
internal fun transactionsForMonth(
    transactions: List<Transaction>,
    month: YearMonth,
    zone: ZoneId = ZoneId.systemDefault(),
): List<Transaction> = transactions.filter {
    YearMonth.from(it.dateTime.atZone(zone)) == month
}.sortedWith(compareByDescending<Transaction> { it.dateTime }.thenBy { it.id })
