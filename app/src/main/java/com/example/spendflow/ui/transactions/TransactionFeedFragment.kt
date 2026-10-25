package com.example.spendflow.ui.transactions

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.spendflow.R
import com.example.spendflow.core.common.AppResult
import com.example.spendflow.core.ui.collectWithLifecycle
import com.example.spendflow.core.ui.components.AmountFormatter
import com.example.spendflow.core.ui.messageRes
import com.example.spendflow.databinding.FragmentTransactionFeedBinding
import com.example.spendflow.domain.model.*
import java.time.ZoneId
import java.time.format.DateTimeFormatter

abstract class TransactionFeedFragment(private val home: Boolean) : Fragment(R.layout.fragment_transaction_feed) {
    private val model: TransactionFeedViewModel by viewModels()
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = FragmentTransactionFeedBinding.bind(view)
        b.screenTitle.setText(if (home) R.string.nav_home else R.string.nav_transactions)
        val adapter = FeedAdapter()
        b.list.layoutManager = LinearLayoutManager(requireContext())
        b.list.adapter = adapter
        fun render() {
            val result = if (home) model.recent.value else model.history.value
            val names = (model.categories.value as? AppResult.Success)?.data.orEmpty().associate { it.id to it.name }
            val items = (result as? AppResult.Success)?.data.orEmpty()
            adapter.rows = items.map {
                val date = it.dateTime.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm"))
                val type = getString(if (it.type == TransactionType.INCOME) R.string.category_type_income else R.string.category_type_expense)
                "${names[it.categoryId] ?: type} \u2022 $date\n${AmountFormatter.format(it.amount, showDecimals = true)}"
            }
            b.status.isVisible = result is AppResult.Error || (result is AppResult.Success && items.isEmpty())
            b.status.setText(if (result is AppResult.Error) result.error.messageRes else R.string.transactions_empty)
            val monthly = model.monthly.value
            b.totals.isVisible = monthly is AppResult.Success
            if (monthly is AppResult.Success) {
                val currency = (model.profile.value as? AppResult.Success)?.data?.baseCurrency ?: monthly.data.firstOrNull()?.baseAmount?.currencyCode ?: User.DEFAULT_BASE_CURRENCY
                fun total(type: TransactionType) = AmountFormatter.format(Money(monthly.data.filter { it.type == type && it.baseAmount.currencyCode == currency }.sumOf { it.baseAmount.amountMinor }, currency), showDecimals = true)
                b.totals.text = getString(R.string.monthly_totals, total(TransactionType.EXPENSE), total(TransactionType.INCOME))
            }
        }
        (if (home) model.recent else model.history).collectWithLifecycle(viewLifecycleOwner) { render() }
        model.monthly.collectWithLifecycle(viewLifecycleOwner) { render() }
        model.categories.collectWithLifecycle(viewLifecycleOwner) { render() }
        model.profile.collectWithLifecycle(viewLifecycleOwner) { render() }
    }
    private class FeedAdapter : RecyclerView.Adapter<FeedAdapter.Holder>() {
        var rows: List<String> = emptyList()
            set(value) { field = value; notifyDataSetChanged() }
        class Holder(val text: TextView) : RecyclerView.ViewHolder(text)
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder = Holder(TextView(parent.context).apply {
            layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            val padding = (16 * resources.displayMetrics.density).toInt()
            setPadding(0, padding, 0, padding)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
        })
        override fun getItemCount() = rows.size
        override fun onBindViewHolder(holder: Holder, position: Int) { holder.text.text = rows[position] }
    }
}
