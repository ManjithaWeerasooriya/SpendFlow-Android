package com.example.spendflow.testing

import com.example.spendflow.core.notifications.CapAlertHistory
import com.example.spendflow.core.notifications.CapWarnings
import com.example.spendflow.data.repository.BudgetRepository
import com.example.spendflow.domain.model.CapLevel
import com.example.spendflow.domain.model.User
import com.example.spendflow.domain.usecase.CurrentMonth
import com.example.spendflow.domain.usecase.GetMonthlySummary
import com.example.spendflow.domain.usecase.ObserveCapUsage
import kotlinx.coroutines.flow.flowOf
import java.time.YearMonth
import java.time.ZoneId

internal fun dashboardCapUsage(budgets: BudgetRepository, summaries: GetMonthlySummary) = ObserveCapUsage(
    budgets, summaries, FakeProfileRepository(User("user", null, null, "LKR")),
    object : CurrentMonth {
        override val zone: ZoneId = ZoneId.systemDefault()
        override fun months() = flowOf(YearMonth.now(zone))
    },
)

internal fun dashboardCapWarnings(): CapWarnings {
    val shown = mutableMapOf<Triple<String, YearMonth, String>, CapLevel>()
    return CapWarnings(object : CapAlertHistory {
        override fun lastShown(uid: String, month: YearMonth, budgetId: String) =
            shown[Triple(uid, month, budgetId)] ?: CapLevel.UNDER
        override fun record(uid: String, month: YearMonth, budgetId: String, level: CapLevel) {
            shown[Triple(uid, month, budgetId)] = level
        }
    }, FakeUserSession("user"))
}
