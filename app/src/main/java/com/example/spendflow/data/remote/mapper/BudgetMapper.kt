package com.example.spendflow.data.remote.mapper

import com.example.spendflow.data.remote.FirestoreFields
import com.example.spendflow.domain.model.Budget
import com.example.spendflow.domain.model.BudgetPeriod
import com.example.spendflow.domain.model.CapLevel
import com.example.spendflow.domain.model.Money

private const val NAME = "name"
private const val LIMIT_MINOR = "limitMinor"
private const val CURRENCY_CODE = "currencyCode"
private const val PERIOD = "period"
private const val START_DATE = "startDate"
private const val ALERTS = "alerts"

fun Budget.toFirestore(): Map<String, Any?> = mapOf(
    NAME to name,
    LIMIT_MINOR to limit.amountMinor,
    CURRENCY_CODE to limit.currencyCode,
    PERIOD to period.name,
    START_DATE to startDate.toFirestore(),
    FirestoreFields.CATEGORY_ID to categoryId,
    ALERTS to CapLevel.ALERTS.filter { it in alerts }.map { it.name },
)

fun budgetFromFirestore(id: String, data: Map<String, Any?>): Budget = FirestoreData(id, data).build {
    Budget(
        id = id,
        name = string(NAME),
        limit = Money(long(LIMIT_MINOR), string(CURRENCY_CODE)),
        period = enum<BudgetPeriod>(PERIOD),
        startDate = localDate(START_DATE),
        categoryId = optString(FirestoreFields.CATEGORY_ID),
        // Caps saved before per-cap alerts existed alert at every threshold
        alerts = optEnumList<CapLevel>(ALERTS)?.toSet() ?: CapLevel.ALERTS,
    )
}
