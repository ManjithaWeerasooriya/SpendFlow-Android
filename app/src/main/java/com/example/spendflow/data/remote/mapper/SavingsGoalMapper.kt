package com.example.spendflow.data.remote.mapper

import com.example.spendflow.domain.model.Money
import com.example.spendflow.domain.model.SavingsGoal

fun SavingsGoal.toFirestore(): Map<String, Any?> = mapOf(
    "name" to name, "targetMinor" to target.amountMinor,
    "savedMinor" to saved.amountMinor, "currencyCode" to target.currencyCode,
)

fun savingsGoalFromFirestore(id: String, data: Map<String, Any?>): SavingsGoal = FirestoreData(id, data).build {
    val currency = string("currencyCode")
    SavingsGoal(id, string("name"), Money(long("targetMinor"), currency), Money(long("savedMinor"), currency))
}
