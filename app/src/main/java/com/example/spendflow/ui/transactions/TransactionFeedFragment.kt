package com.example.spendflow.ui.transactions

import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.spendflow.R
import com.example.spendflow.core.common.AppResult
import com.example.spendflow.core.ui.collectWithLifecycle
import com.example.spendflow.core.ui.components.AmountFormatter
import com.example.spendflow.core.ui.components.AmountView
import com.example.spendflow.core.ui.components.SummaryCardView
import com.example.spendflow.core.ui.messageRes
import com.example.spendflow.databinding.FragmentTransactionFeedBinding
import com.example.spendflow.databinding.ItemTransactionRefinedBinding
import com.example.spendflow.domain.model.*
import com.google.android.material.color.MaterialColors
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

abstract class TransactionFeedFragment(private val home: Boolean) : Fragment(R.layout.fragment_transaction_feed) {
    private val model: TransactionFeedViewModel by viewModels()
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = FragmentTransactionFeedBinding.bind(view)
        b.screenTitle.setText(if (home) R.string.nav_home else R.string.nav_transactions)
        b.monthSubtitle.text = if (home) YearMonth.now().format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())) else getString(R.string.ui_all_history)
        b.summary.isVisible = home
        b.summary.compact = true
        b.recentHeader.isVisible = home
        b.feedFilter.isVisible = !home
        b.goalsHeader.isVisible = home
        b.seeAll.setOnClickListener { findNavController().navigate(R.id.transactionsFragment) }
        b.viewGoals.setOnClickListener { findNavController().navigate(R.id.goalsFragment) }
        b.feedFilter.check(when(model.filter.value) { "EXPENSE" -> R.id.filterExpense; "INCOME" -> R.id.filterIncome; else -> R.id.filterAll })
        b.feedFilter.addOnButtonCheckedListener { _, id, checked -> if (checked) model.setFilter(when(id) { R.id.filterExpense -> "EXPENSE"; R.id.filterIncome -> "INCOME"; else -> "ALL" }) }
        val adapter = FeedAdapter()
        b.list.layoutManager = LinearLayoutManager(requireContext())
        b.list.adapter = adapter
        if (!home) {
            b.list.layoutParams.height = (resources.displayMetrics.heightPixels * .65).toInt()
            b.list.isNestedScrollingEnabled = true
        }
        b.list.background = MaterialShapeDrawable(ShapeAppearanceModel.builder(requireContext(), R.style.ShapeAppearance_SpendFlow_InsetCard, 0).build()).apply {
            fillColor = ColorStateList.valueOf(MaterialColors.getColor(view, android.R.attr.colorBackground))
        }
        b.list.clipToOutline = true
        fun render() {
            val result = if (home) model.recent.value else model.history.value
            val names = (model.categories.value as? AppResult.Success)?.data.orEmpty().associate { it.id to it.name }
            val items = (result as? AppResult.Success)?.data.orEmpty().filter { home || model.filter.value == "ALL" || it.type.name == model.filter.value }
            adapter.submitList(items.map { item ->
                val fallback = getString(if (item.type == TransactionType.INCOME) R.string.category_type_income else R.string.category_type_expense)
                FeedRow(item, names[item.categoryId] ?: fallback)
            })
            b.status.isVisible = result is AppResult.Error || result == null
            b.status.setText(if (result is AppResult.Error) result.error.messageRes else R.string.analytics_loading)
            b.emptyState.isVisible = result is AppResult.Success && items.isEmpty()
            b.list.isVisible = items.isNotEmpty()
            val monthly = model.monthly.value
            b.totals.isVisible = !home && monthly is AppResult.Success
            if (monthly is AppResult.Success) {
                val currency = (model.profile.value as? AppResult.Success)?.data?.baseCurrency ?: monthly.data.firstOrNull()?.baseAmount?.currencyCode ?: User.DEFAULT_BASE_CURRENCY
                val rows = monthly.data.filter { it.baseAmount.currencyCode == currency }
                val income = rows.filter { it.type == TransactionType.INCOME }.sumOf { it.baseAmount.amountMinor }
                val expense = rows.filter { it.type == TransactionType.EXPENSE }.sumOf { it.baseAmount.amountMinor }
                fun amount(value: Long) = Money(value, currency)
                b.totals.text = getString(R.string.monthly_totals, AmountFormatter.format(amount(expense), true), AmountFormatter.format(amount(income), true))
                b.summary.setSummary(SummaryCardView.Summary(amount(income), getString(R.string.analytics_sources, rows.filter { it.type == TransactionType.INCOME }.map { it.categoryId }.distinct().size),
                    amount(expense), getString(R.string.analytics_entries, rows.count { it.type == TransactionType.EXPENSE }), amount(income - expense), if (income > 0) ((income - expense).toDouble() / income * 100).toInt() else null))
            }
            val goal = (model.goals.value as? AppResult.Success)?.data?.sortedBy { it.isCompleted }?.firstOrNull()
            b.goalPreview.isVisible = home && goal != null
            goal?.let { b.goalPreview.bind(it.saved, it.target, it.name, showDecimals = true) }
        }
        (if (home) model.recent else model.history).collectWithLifecycle(viewLifecycleOwner) { render() }
        model.monthly.collectWithLifecycle(viewLifecycleOwner) { render() }
        model.categories.collectWithLifecycle(viewLifecycleOwner) { render() }
        model.profile.collectWithLifecycle(viewLifecycleOwner) { render() }
        model.filter.collectWithLifecycle(viewLifecycleOwner) { render() }
        if (home) model.goals.collectWithLifecycle(viewLifecycleOwner) { render() }
    }

    private data class FeedRow(val transaction: Transaction, val category: String)
    private class FeedAdapter : ListAdapter<FeedRow, FeedAdapter.Holder>(object : DiffUtil.ItemCallback<FeedRow>() {
        override fun areItemsTheSame(old: FeedRow, new: FeedRow) = old.transaction.id == new.transaction.id
        override fun areContentsTheSame(old: FeedRow, new: FeedRow) = old == new
    }) {
        class Holder(val binding: ItemTransactionRefinedBinding) : RecyclerView.ViewHolder(binding.root)
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(ItemTransactionRefinedBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        override fun onBindViewHolder(holder: Holder, position: Int) = with(holder.binding) {
            val row = getItem(position)
            val item = row.transaction
            categoryInitial.text = row.category.take(1).uppercase(Locale.getDefault())
            categoryInitial.background = GradientDrawable().apply {
                cornerRadius = root.resources.getDimension(R.dimen.sf_corner_tile)
                setColor(MaterialColors.getColor(root, R.attr.sfColorVioletTint))
            }
            transactionTitle.text = item.note?.takeIf { it.isNotBlank() } ?: row.category
            transactionSubtitle.text = row.category + " - " + item.dateTime.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd MMM, HH:mm"))
            transactionAmount.size = AmountView.Size.INLINE
            transactionAmount.showDecimals = true
            transactionAmount.signed = item.type != TransactionType.TRANSFER
            transactionAmount.tone = if (item.type == TransactionType.INCOME) AmountView.Tone.INCOME else AmountView.Tone.NEUTRAL
            transactionAmount.amount = if (item.type == TransactionType.EXPENSE) -item.amount else item.amount
        }
    }
}
