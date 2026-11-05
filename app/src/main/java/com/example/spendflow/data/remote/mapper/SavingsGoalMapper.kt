package com.example.spendflow.data.remote.mapper

import com.example.spendflow.domain.model.GoalContribution
import com.example.spendflow.domain.model.Money
import com.example.spendflow.domain.model.SavingsGoal
import java.time.LocalDate

fun SavingsGoal.toFirestore(): Map<String, Any?> = mapOf(
    "name" to name, "targetMinor" to target.amountMinor,
    "savedMinor" to saved.amountMinor, "currencyCode" to target.currencyCode,
    "deadline" to deadline?.toFirestore(),
    "contributions" to contributions.map {
        mapOf("id" to it.id, "amountMinor" to it.amount.amountMinor, "date" to it.date.toFirestore(), "note" to it.note)
    },
    "isPrimary" to isPrimary, "markedCompleted" to markedCompleted,
)

fun savingsGoalFromFirestore(id: String, data: Map<String, Any?>): SavingsGoal = FirestoreData(id, data).build {
    val currency = string("currencyCode")
    val contributions = (data["contributions"] as? List<*>).orEmpty().map { raw ->
        @Suppress("UNCHECKED_CAST")
        val row = FirestoreData(id, raw as? Map<String, Any?> ?: fail("Field 'contributions' should hold maps"))
        GoalContribution(row.string("id"), Money(row.long("amountMinor"), currency), row.localDate("date"), row.optString("note"))
    }
    SavingsGoal(
        id, string("name"), Money(long("targetMinor"), currency), Money(long("savedMinor"), currency), optLocalDate("deadline"),
        contributions, boolean("isPrimary", false), boolean("markedCompleted", false),
    )
}
