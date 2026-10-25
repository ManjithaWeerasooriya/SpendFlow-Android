package com.example.spendflow.ui.analytics

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.example.spendflow.R
import com.example.spendflow.core.common.AppResult
import com.example.spendflow.core.ui.collectWithLifecycle
import com.example.spendflow.core.ui.components.AmountFormatter
import com.example.spendflow.core.ui.components.SummaryCardView
import com.example.spendflow.core.ui.messageRes
import com.example.spendflow.databinding.FragmentAnalyticsBinding
import com.example.spendflow.databinding.ItemAnalyticsCategoryBinding
import com.example.spendflow.domain.model.Money
import com.example.spendflow.domain.model.TransactionType
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import java.text.NumberFormat
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

@AndroidEntryPoint
class AnalyticsFragment : Fragment(R.layout.fragment_analytics) {
    private val model: AnalyticsViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = FragmentAnalyticsBinding.bind(view)
        b.summary.compact = true
        b.summary.showSavingsRate = false
        b.previousMonth.setOnClickListener { model.moveMonth(-1) }
        b.nextMonth.setOnClickListener { model.moveMonth(1) }
        b.breakdownToggle.check(if (model.incomeMode.value) R.id.breakdownIncome else R.id.breakdownExpenses)
        b.breakdownToggle.addOnButtonCheckedListener { _, id, checked -> if (checked) model.setIncomeMode(id == R.id.breakdownIncome) }
        fun render() {
            val month = YearMonth.parse(model.month.value)
            b.monthLabel.text = month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()))
            b.nextMonth.isEnabled = month < YearMonth.now()
            val result = model.report.value
            b.reportContent.isVisible = result is AppResult.Success
            b.status.isVisible = result !is AppResult.Success
            b.status.setText(if (result is AppResult.Error) result.error.messageRes else R.string.analytics_loading)
            if (result !is AppResult.Success) return
            val report = result.data
            fun money(minor: Long) = Money(minor, report.currency)
            fun formatted(minor: Long) = AmountFormatter.format(money(minor), showDecimals = true)
            b.summary.setSummary(SummaryCardView.Summary(money(report.incomeMinor), resources.getQuantityString(R.plurals.analytics_source_count, report.incomeSources, report.incomeSources),
                money(report.expenseMinor), resources.getQuantityString(R.plurals.analytics_entry_count, report.entries.count { it.type == TransactionType.EXPENSE }, report.entries.count { it.type == TransactionType.EXPENSE }), money(report.netMinor), report.savingsRate))
            b.comparison.text = if (report.previousExpenseMinor == 0L) getString(R.string.analytics_no_comparison) else {
                val change = (report.expenseMinor - report.previousExpenseMinor).toDouble() / report.previousExpenseMinor
                val percent = NumberFormat.getPercentInstance().apply { maximumFractionDigits = 1 }.format(abs(change))
                getString(if (change > 0) R.string.analytics_spending_up else R.string.analytics_spending_down, percent)
            }
            val income = model.incomeMode.value
            val categories = if (income) report.incomeBySource else report.expensesByCategory
            b.breakdownTitle.setText(if (income) R.string.analytics_income_breakdown else R.string.analytics_breakdown)
            val total = if (income) report.incomeMinor else report.expenseMinor
            b.categoryChart.slices = categories
            b.categoryChart.selectedSlice = null
            b.breakdownAmount.text = AmountFormatter.parts(money(total), true, false).number
            b.breakdownCaption.text = getString(if (income) R.string.analytics_received else R.string.analytics_spent, report.currency)
            b.breakdownEmpty.isVisible = categories.isEmpty()
            b.categoryRows.removeAllViews()
            val palette = listOf(R.attr.sfColorViolet, R.attr.sfColorIncome, R.attr.sfColorInfo, R.attr.sfColorWarning, R.attr.sfColorOverCap)
            categories.forEachIndexed { index, category ->
                val row = ItemAnalyticsCategoryBinding.inflate(layoutInflater, b.categoryRows, false)
                val name = category.name ?: getString(R.string.analytics_uncategorized)
                val share = if (total > 0) category.amountMinor.toDouble() / total else 0.0
                val color = MaterialColors.getColor(view, palette[index % palette.size])
                row.colorDot.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }
                row.categoryName.text = name
                row.categoryCount.text = resources.getQuantityString(R.plurals.analytics_entry_count, category.count, category.count)
                row.categoryAmount.text = formatted(category.amountMinor)
                row.categoryPercent.text = NumberFormat.getPercentInstance().apply { maximumFractionDigits = 1 }.format(share)
                row.categoryProgress.setIndicatorColor(color)
                row.categoryProgress.progress = (share * 1000).toInt()
                row.root.setOnClickListener {
                    b.categoryChart.selectedSlice = index
                    val entries = report.entries.filter { it.categoryId == category.categoryId && it.type == if (income) TransactionType.INCOME else TransactionType.EXPENSE }
                    val details = entries.map { entry ->
                        val date = entry.dateTime.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd MMM, HH:mm"))
                        val original = AmountFormatter.format(entry.amount, true)
                        val converted = if (entry.amount.currencyCode != report.currency) " (${formatted(entry.baseAmount.amountMinor)})" else ""
                        "$date\n${entry.note?.takeIf { it.isNotBlank() } ?: name}\n$original$converted"
                    }
                    MaterialAlertDialogBuilder(requireContext()).setTitle(name).setItems(details.toTypedArray(), null)
                        .setPositiveButton(R.string.action_close, null).setOnDismissListener { b.categoryChart.selectedSlice = null }.show()
                }
                b.categoryRows.addView(row.root)
            }
            b.cashflowChart.days = report.days
            b.cashflowChart.contentDescription = getString(R.string.analytics_cashflow_accessibility, formatted(report.incomeMinor), formatted(report.expenseMinor))
            b.dailyDetails.setOnClickListener {
                val values = report.days.map { day ->
                    getString(R.string.analytics_daily_detail, day.day, formatted(day.incomeMinor), formatted(day.expenseMinor))
                }
                MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.analytics_cashflow).setItems(values.toTypedArray(), null)
                    .setPositiveButton(R.string.action_close, null).show()
            }
            b.dayAxis.text = getString(R.string.analytics_day_axis, report.month.lengthOfMonth())
            b.dailyAverage.text = getString(R.string.analytics_daily_average, formatted(report.averageDailyExpenseMinor))
            b.busiestDay.text = report.biggestSpendingDay?.let { day ->
                getString(R.string.analytics_biggest_day, report.month.atDay(day).format(DateTimeFormatter.ofPattern("EEE, dd MMM")), formatted(report.days[day - 1].expenseMinor))
            } ?: getString(R.string.analytics_no_expenses)
            b.entryCount.text = getString(R.string.analytics_recorded, report.transactionCount)
        }
        model.month.collectWithLifecycle(viewLifecycleOwner) { render() }
        model.report.collectWithLifecycle(viewLifecycleOwner) { render() }
        model.incomeMode.collectWithLifecycle(viewLifecycleOwner) { render() }
    }
}
