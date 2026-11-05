package com.example.spendflow.core.ui.components

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.core.view.isVisible
import com.example.spendflow.R
import com.example.spendflow.databinding.ViewSummaryCardBinding
import com.example.spendflow.domain.model.Money
import com.example.spendflow.domain.model.MonthlySummary
import com.google.android.material.color.MaterialColors

/** Monthly totals and savings rate, with an optional expense-to-income bar. */
class SummaryCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    /** [savedRatePercent] is null when there is no income, which hides the pill. */
    data class Summary(
        val income: Money,
        val incomeCaption: CharSequence,
        val expenses: Money,
        val expensesCaption: CharSequence,
        val net: Money,
        val savedRatePercent: Int?,
    )

    var compact = false
    var showSavingsRate = true

    private val binding = ViewSummaryCardBinding.inflate(LayoutInflater.from(context), this)

    init {
        orientation = VERTICAL
        val padding = resources.getDimensionPixelSize(R.dimen.sf_space_xl)
        setPadding(padding, padding, padding, padding)
        background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(
                MaterialColors.getColor(this, R.attr.sfColorHeroStart),
                MaterialColors.getColor(this, R.attr.sfColorHeroEnd),
            ),
        ).apply { cornerRadius = resources.getDimension(R.dimen.sf_corner_shell) }
        if (isInEditMode) {
            setSummary(
                Summary(
                    income = Money(185_000_00, "LKR"),
                    incomeCaption = "3 sources",
                    expenses = Money(112_400_00, "LKR"),
                    expensesCaption = "61% of income",
                    net = Money(72_600_00, "LKR"),
                    savedRatePercent = 39,
                ),
            )
        }
    }

    fun setSummary(summary: Summary) {
        render(summary, summary.savedRatePercent?.let { resources.getString(R.string.summary_saved_rate, it) })
        binding.expenseRatio.isVisible = false
    }

    fun setMonthlySummary(summary: MonthlySummary, showExpenseRatioBar: Boolean = false) {
        val unavailable = resources.getString(R.string.summary_ratio_unavailable)
        val saved = summary.savingsRate?.let { PercentFormatter.formatFraction(it) }
        val expenseRatio = summary.expenseToIncomeRatio
        val expenseCaption = if (showExpenseRatioBar) "" else resources.getString(
            R.string.summary_spent_ratio,
            expenseRatio?.let { PercentFormatter.formatFraction(it) } ?: unavailable,
        )
        val sources = summary.incomeBySource.size
        render(
            Summary(
                income = summary.totalIncome,
                incomeCaption = resources.getQuantityString(R.plurals.analytics_source_count, sources, sources),
                expenses = summary.totalExpenses,
                expensesCaption = expenseCaption,
                net = summary.netSavings,
                savedRatePercent = null,
            ),
            resources.getString(R.string.summary_saved_ratio, saved ?: unavailable),
            showCompactExpenseCaption = true,
        )
        binding.savedRate.contentDescription = if (saved == null) {
            resources.getString(R.string.summary_savings_rate_unavailable)
        } else {
            resources.getString(R.string.summary_savings_rate_value, saved)
        }
        binding.expenseRatio.isVisible = showExpenseRatioBar
        if (showExpenseRatioBar) bindExpenseRatio(ExpenseRatioDisplay.from(expenseRatio), unavailable)
    }

    private fun bindExpenseRatio(display: ExpenseRatioDisplay?, unavailable: String) = with(binding) {
        val label = resources.getString(R.string.summary_expense_to_income_value, display?.percent ?: unavailable)
        expenseRatioLabel.text = label
        // The container announces the real value, not the capped percentage of the visual bar.
        expenseRatio.contentDescription =
            if (display == null) resources.getString(R.string.summary_expense_to_income_unavailable) else label
        expenseRatioBar.isVisible = display != null
        expenseRatioBar.progress = display?.progress ?: 0
        val color = if (display?.overIncome == true) R.attr.sfColorOverCap else R.attr.sfColorTextOnDark
        expenseRatioBar.setIndicatorColor(MaterialColors.getColor(expenseRatioBar, color))
    }

    private fun render(summary: Summary, savingsLabel: String?, showCompactExpenseCaption: Boolean = false) = with(binding) {
        incomeAmount.size = AmountView.Size.INLINE
        expensesAmount.size = AmountView.Size.INLINE
        netAmount.size = if (compact) AmountView.Size.TITLE else AmountView.Size.DISPLAY
        listOf(incomeAmount, expensesAmount, netAmount).forEach { it.maxLines = 2; it.showDecimals = true }
        if (compact) {
            androidx.core.widget.TextViewCompat.setTextAppearance(savedRate, R.style.TextAppearance_SpendFlow_Caption)
            val padding = resources.getDimensionPixelSize(R.dimen.sf_space_s)
            savedRate.setPadding(padding, padding / 2, padding, padding / 2)
            savedRate.setTextColor(MaterialColors.getColor(this@SummaryCardView, R.attr.sfColorTextOnDark))
        }
        incomeCaption.isVisible = !compact
        expensesCaption.isVisible = summary.expensesCaption.isNotEmpty() && (!compact || showCompactExpenseCaption)
        incomeAmount.amount = summary.income
        incomeAmount.contentDescription = metricDescription(R.string.summary_income, incomeAmount.text)
        incomeCaption.text = summary.incomeCaption
        expensesAmount.amount = summary.expenses
        expensesAmount.contentDescription = metricDescription(R.string.summary_expenses, expensesAmount.text)
        expensesCaption.text = summary.expensesCaption
        netAmount.amount = summary.net
        netAmount.contentDescription = metricDescription(R.string.summary_net_savings, netAmount.text)
        savedRate.isVisible = savingsLabel != null && showSavingsRate
        savedRate.text = savingsLabel
        savedRate.contentDescription = null
    }

    private fun metricDescription(label: Int, amount: CharSequence): String =
        resources.getString(R.string.summary_metric_value, resources.getString(label), amount)
}
