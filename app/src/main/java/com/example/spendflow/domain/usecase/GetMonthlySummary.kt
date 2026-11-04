package com.example.spendflow.domain.usecase

import com.example.spendflow.core.common.AppError
import com.example.spendflow.core.common.AppResult
import com.example.spendflow.core.common.appResultOf
import com.example.spendflow.core.time.MonthBounds
import com.example.spendflow.data.repository.TransactionRepository
import com.example.spendflow.data.repository.UserRepository
import com.example.spendflow.domain.model.Money
import com.example.spendflow.domain.model.MonthlySummary
import com.example.spendflow.domain.model.Transaction
import com.example.spendflow.domain.model.TransactionType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

class GetMonthlySummary @Inject constructor(
    private val transactions: TransactionRepository,
    private val users: UserRepository,
) {
    /**
     * Observes a calendar month in [zone]. A missing profile emits [AppError.NotFound] until
     * the profile supplies a base currency; an empty month then emits zero totals in that currency.
     */
    operator fun invoke(
        month: YearMonth,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Flow<AppResult<MonthlySummary>> = observeForProfile { currency ->
        transactions.observeMonth(month, zone).calculate { summarize(month, currency, it) }
    }

    /** Keeps both summaries and the chart entries in sync from one Firestore snapshot. */
    internal fun observeComparison(month: YearMonth, zone: ZoneId): Flow<AppResult<Comparison>> =
        observeForProfile { currency ->
            val previous = MonthBounds(month.minusMonths(1), zone)
            val current = MonthBounds(month, zone)
            transactions.observeTransactions(previous.startInclusive, current.endExclusive)
                .calculate { compare(month, currency, it, zone) }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun <T> observeForProfile(observe: (String) -> Flow<AppResult<T>>): Flow<AppResult<T>> =
        users.observeProfile().flatMapLatest { result ->
            when (result) {
                is AppResult.Error -> flowOf(result)
                is AppResult.Success -> {
                    val profile = result.data ?: return@flatMapLatest flowOf(AppResult.Error(AppError.NotFound))
                    observe(profile.baseCurrency)
                }
            }
        }

    private fun <T> Flow<AppResult<List<Transaction>>>.calculate(
        build: (List<Transaction>) -> T,
    ): Flow<AppResult<T>> = map { result ->
        when (result) {
            is AppResult.Error -> result
            is AppResult.Success -> appResultOf { build(result.data) }
        }
    }

    internal data class Comparison(
        val current: MonthlySummary,
        val previous: MonthlySummary,
        val currentEntries: List<Transaction>,
        val hasCurrentTransactions: Boolean,
    )

    internal companion object {
        fun compare(month: YearMonth, currency: String, rows: List<Transaction>, zone: ZoneId): Comparison {
            val previousMonth = month.minusMonths(1)
            val previousBounds = MonthBounds(previousMonth, zone)
            val currentBounds = MonthBounds(month, zone)
            val currentRows = rows.filter { it.dateTime in currentBounds }
            return Comparison(
                current = summarize(month, currency, currentRows),
                previous = summarize(previousMonth, currency, rows.filter { it.dateTime in previousBounds }),
                currentEntries = currentRows.filter {
                    it.type != TransactionType.TRANSFER && it.baseAmount.currencyCode == currency
                },
                hasCurrentTransactions = currentRows.isNotEmpty(),
            )
        }

        private fun summarize(month: YearMonth, currency: String, rows: List<Transaction>): MonthlySummary {
            val expensesByCategory = mutableMapOf<String?, Long>()
            val incomeBySource = mutableMapOf<String?, Long>()
            val totalsByClassification = mutableMapOf<TransactionType, Long>()
            for (transaction in rows) {
                val amount = transaction.baseAmount
                // Never sum different base currencies.
                if (amount.currencyCode != currency) continue
                totalsByClassification.addAmount(transaction.type, amount.amountMinor)
                when (transaction.type) {
                    TransactionType.INCOME -> incomeBySource.addAmount(transaction.categoryId, amount.amountMinor)
                    TransactionType.EXPENSE -> expensesByCategory.addAmount(transaction.categoryId, amount.amountMinor)
                    TransactionType.TRANSFER -> Unit
                }
            }

            val classificationAmounts = totalsByClassification.mapValues { Money(it.value, currency) }
            val zero = Money.zero(currency)
            return MonthlySummary(
                month = month,
                totalIncome = classificationAmounts[TransactionType.INCOME] ?: zero,
                totalExpenses = classificationAmounts[TransactionType.EXPENSE] ?: zero,
                expensesByCategory = expensesByCategory.mapValues { Money(it.value, currency) },
                incomeBySource = incomeBySource.mapValues { Money(it.value, currency) },
                totalsByClassification = classificationAmounts,
            )
        }

        private fun <K> MutableMap<K, Long>.addAmount(key: K, amount: Long) {
            this[key] = Math.addExact(this[key] ?: 0L, amount)
        }
    }
}
