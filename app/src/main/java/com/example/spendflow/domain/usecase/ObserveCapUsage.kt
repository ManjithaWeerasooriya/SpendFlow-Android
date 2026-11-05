package com.example.spendflow.domain.usecase

import com.example.spendflow.core.common.AppResult
import com.example.spendflow.data.repository.BudgetRepository
import com.example.spendflow.data.repository.PreferencesRepository
import com.example.spendflow.domain.model.CapThresholds
import com.example.spendflow.domain.model.CapUsage
import com.example.spendflow.domain.model.CategoryCaps
import com.example.spendflow.domain.model.Money
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import java.time.Clock
import java.time.Duration
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

/** This month's spending per expense category and each cap's usage, in the base currency. */
data class CapOverview(
    val month: YearMonth,
    val usages: Map<String, CapUsage>,
    val spentByCategory: Map<String?, Money>,
    val currencyCode: String,
    val thresholds: CapThresholds,
)

/** The current calendar month, emitting again when a new one starts. */
interface CurrentMonth {
    val zone: ZoneId
    fun months(): Flow<YearMonth>
}

class ClockCurrentMonth @Inject constructor(private val clock: Clock) : CurrentMonth {
    override val zone: ZoneId get() = clock.zone

    override fun months(): Flow<YearMonth> = flow {
        while (true) {
            val month = YearMonth.now(clock)
            emit(month)
            val nextMonth = month.plusMonths(1).atDay(1).atStartOfDay(clock.zone).toInstant()
            // Woken at least hourly, so a changed device clock is noticed too
            val wait = Duration.between(clock.instant(), nextMonth).coerceIn(Duration.ofSeconds(1), Duration.ofHours(1))
            delay(wait.toMillis())
        }
    }.distinctUntilChanged()
}

/**
 * Cap usage for the current month, recalculated on every change to transactions, caps or the Settings
 * thresholds, and again when a new month starts.
 */
class ObserveCapUsage @Inject constructor(
    private val budgets: BudgetRepository,
    private val summaries: GetMonthlySummary,
    private val preferences: PreferencesRepository,
    private val currentMonth: CurrentMonth,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<AppResult<CapOverview>> = currentMonth.months().flatMapLatest { month ->
        combine(
            budgets.observeBudgets(),
            summaries(month, currentMonth.zone),
            preferences.observePreferences(),
        ) { caps, summary, prefs ->
            when {
                caps is AppResult.Error -> caps
                summary is AppResult.Error -> summary
                prefs is AppResult.Error -> prefs
                else -> {
                    val monthly = (summary as AppResult.Success).data
                    val thresholds = (prefs as AppResult.Success).data.capThresholds
                    AppResult.Success(
                        CapOverview(
                            month = month,
                            usages = CategoryCaps.usage((caps as AppResult.Success).data, monthly, thresholds),
                            spentByCategory = monthly.expensesByCategory,
                            currencyCode = monthly.totalExpenses.currencyCode,
                            thresholds = thresholds,
                        ),
                    )
                }
            }
        }
    }
}
