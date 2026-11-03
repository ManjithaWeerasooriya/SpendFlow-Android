package com.example.spendflow.domain.usecase

import com.example.spendflow.core.common.AppError
import com.example.spendflow.core.common.AppResult
import com.example.spendflow.core.common.appResultOf
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
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(
        month: YearMonth,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Flow<AppResult<MonthlySummary>> = users.observeProfile().flatMapLatest { result ->
        when (result) {
            is AppResult.Error -> flowOf(result)
            is AppResult.Success -> {
                val profile = result.data ?: return@flatMapLatest flowOf(AppResult.Error(AppError.NotFound))
                transactions.observeMonth(month, zone).map { monthResult ->
                    when (monthResult) {
                        is AppResult.Error -> monthResult
                        is AppResult.Success -> appResultOf {
                            summarize(month, profile.baseCurrency, monthResult.data)
                        }
                    }
                }
            }
        }
    }

    private fun summarize(month: YearMonth, currency: String, rows: List<Transaction>): MonthlySummary {
        val expensesByCategory = mutableMapOf<String?, Long>()
        val incomeBySource = mutableMapOf<String?, Long>()
        val totalsByClassification = mutableMapOf<TransactionType, Long>()
        for (transaction in rows) {
            val amount = transaction.baseAmount
            // Like the existing Analytics report, never sum different base currencies.
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
