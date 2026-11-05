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
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

abstract class TransactionFeedFragment(private val home: Boolean) : Fragment(R.layout.fragment_transaction_feed) {
    private val model: TransactionFeedViewModel by viewModels()
    private var selectedMonth = YearMonth.now()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selectedMonth = savedInstanceState?.getString("feed_month")?.let(YearMonth::parse) ?: YearMonth.now()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("feed_month", selectedMonth.toString())
        super.onSaveInstanceState(outState)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = FragmentTransactionFeedBinding.bind(view)
        b.screenTitle.setText(if (home) R.string.nav_home else R.string.nav_transactions)
        b.monthSubtitle.text = if (home) YearMonth.now().format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())) else selectedMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()))
        b.summary.isVisible = home
        b.summary.compact = true
        b.recentHeader.isVisible = home
        b.feedFilter.isVisible = !home
        b.goalsHeader.isVisible = home
        b.seeAll.setOnClickListener { findNavController().navigate(R.id.transactionsFragment) }
        b.viewGoals.setOnClickListener { findNavController().navigate(R.id.goalsFragment) }
        b.feedFilter.check(when(model.filter.value) { "EXPENSE" -> R.id.filterExpense; "INCOME" -> R.id.filterIncome; else -> R.id.filterAll })
        b.feedFilter.addOnButtonCheckedListener { _, id, checked -> if (checked) model.setFilter(when(id) { R.id.filterExpense -> "EXPENSE"; R.id.filterIncome -> "INCOME"; else -> "ALL" }) }
        b.emptyState.actionText = getString(R.string.nav_add_transaction)
        b.emptyState.setOnActionClickListener { findNavController().navigate(R.id.quickAddSheet) }
        val adapter = FeedAdapter { transaction ->
            // SF-17 can consume this request without changing the feed's navigation destination.
            parentFragmentManager.setFragmentResult("edit_transaction", Bundle().apply {
                putString("transactionId", transaction.id)
            })
        }
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
            val allItems = (result as? AppResult.Success)?.data.orEmpty()
            val monthItems = if (home) allItems else transactionsForMonth(allItems, selectedMonth)
            val items = monthItems.filter { home || model.filter.value == "ALL" || it.type.name == model.filter.value }
                .sortedWith(compareByDescending<Transaction> { it.dateTime }.thenBy { it.id })
            val rows = items.map { item ->
                val fallback = getString(if (item.type == TransactionType.INCOME) R.string.category_type_income else R.string.category_type_expense)
                FeedRow(item, names[item.categoryId] ?: fallback)
            }
            adapter.submitList(if (home) rows else rows.groupBy { it.transaction.dateTime.atZone(ZoneId.systemDefault()).toLocalDate() }
                .flatMap { (day, entries) ->
                    val currency = (model.profile.value as? AppResult.Success)?.data?.baseCurrency ?: User.DEFAULT_BASE_CURRENCY
                    val net = entries.filter { it.transaction.baseAmount.currencyCode == currency }.sumOf {
                        when (it.transaction.type) {
                            TransactionType.INCOME -> it.transaction.baseAmount.amountMinor
                            TransactionType.EXPENSE -> -it.transaction.baseAmount.amountMinor
                            TransactionType.TRANSFER -> 0L
                        }
                    }
                    val date = day.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())).uppercase(Locale.getDefault())
                    val label = if (day == LocalDate.now()) getString(R.string.feed_today, date) else
                        day.format(DateTimeFormatter.ofPattern("EEEE \u00b7 d MMM", Locale.getDefault())).uppercase(Locale.getDefault())
                    listOf<FeedItem>(DayRow(day, label, Money(net, currency))) + entries
                })
            b.status.isVisible = result is AppResult.Error || result == null
            b.status.setText(if (result is AppResult.Error) result.error.messageRes else R.string.analytics_loading)
            b.emptyState.isVisible = result is AppResult.Success && items.isEmpty()
            b.list.isVisible = items.isNotEmpty()
            val monthly = if (home) model.monthly.value else (result as? AppResult.Success)?.let { AppResult.Success(monthItems) }
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
        if (!home) {
            b.monthSubtitle.apply {
                isClickable = true
                isFocusable = true
                minHeight = (48 * resources.displayMetrics.density).toInt()
                contentDescription = getString(R.string.feed_choose_month)
                setOnClickListener {
                    android.app.DatePickerDialog(requireContext(), { _, year, month, _ ->
                        selectedMonth = YearMonth.of(year, month + 1)
                        text = selectedMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()))
                        render()
                        b.list.scrollToPosition(0)
                    }, selectedMonth.year, selectedMonth.monthValue - 1, 1).apply {
                        setTitle(R.string.feed_choose_month)
                    }.show()
                }
            }
        }
        (if (home) model.recent else model.history).collectWithLifecycle(viewLifecycleOwner) { render() }
        model.monthly.collectWithLifecycle(viewLifecycleOwner) { render() }
        model.categories.collectWithLifecycle(viewLifecycleOwner) { render() }
        model.profile.collectWithLifecycle(viewLifecycleOwner) { render() }
        model.filter.collectWithLifecycle(viewLifecycleOwner) { render() }
        if (home) model.goals.collectWithLifecycle(viewLifecycleOwner) { render() }
    }

    private sealed interface FeedItem { val key: String }
    private data class FeedRow(val transaction: Transaction, val category: String) : FeedItem {
        override val key get() = "transaction:${transaction.id}"
    }
    private data class DayRow(val day: LocalDate, val label: String, val net: Money) : FeedItem {
        override val key get() = "day:$day"
    }
    private class FeedAdapter(private val onClick: (Transaction) -> Unit) : ListAdapter<FeedItem, RecyclerView.ViewHolder>(object : DiffUtil.ItemCallback<FeedItem>() {
        override fun areItemsTheSame(old: FeedItem, new: FeedItem) = old.key == new.key
        override fun areContentsTheSame(old: FeedItem, new: FeedItem) = old == new
    }) {
        init { stateRestorationPolicy = StateRestorationPolicy.PREVENT_WHEN_EMPTY }
        class Holder(val binding: ItemTransactionRefinedBinding) : RecyclerView.ViewHolder(binding.root)
        class Header(val binding: com.example.spendflow.databinding.ItemTransactionDayBinding) : RecyclerView.ViewHolder(binding.root)
        override fun getItemViewType(position: Int) = if (getItem(position) is DayRow) 1 else 0
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return if (viewType == 1) Header(com.example.spendflow.databinding.ItemTransactionDayBinding.inflate(inflater, parent, false))
            else Holder(ItemTransactionRefinedBinding.inflate(inflater, parent, false))
        }
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val row = getItem(position)
            if (holder is Header && row is DayRow) {
                holder.binding.dayLabel.text = row.label
                holder.binding.dayNet.text = AmountFormatter.format(row.net, true, true)
                androidx.core.view.ViewCompat.setAccessibilityHeading(holder.binding.root, true)
                return
            }
            if (holder !is Holder || row !is FeedRow) return
            with(holder.binding) {
                val item = row.transaction
                categoryInitial.setImageResource(if (item.type == TransactionType.INCOME) R.drawable.ic_badge_income else R.drawable.ic_badge_expense)
                categoryInitial.background = GradientDrawable().apply {
                    cornerRadius = root.resources.getDimension(R.dimen.sf_corner_tile)
                    setColor(MaterialColors.getColor(root, R.attr.sfColorVioletTint))
                }
                transactionTitle.text = row.category
                transactionSubtitle.text = item.note
                transactionSubtitle.isVisible = !item.note.isNullOrBlank()
                transactionAmount.size = AmountView.Size.INLINE
                transactionAmount.showDecimals = true
                transactionAmount.signed = item.type != TransactionType.TRANSFER
                transactionAmount.tone = if (item.type == TransactionType.INCOME) AmountView.Tone.INCOME else AmountView.Tone.NEUTRAL
                transactionAmount.amount = if (item.type == TransactionType.EXPENSE) -item.baseAmount else item.baseAmount
                originalAmount.isVisible = item.type == TransactionType.INCOME && item.amount.currencyCode != item.baseAmount.currencyCode
                originalAmount.text = AmountFormatter.format(item.amount, true)
                root.setOnClickListener { onClick(item) }
            }
        }
    }
}
