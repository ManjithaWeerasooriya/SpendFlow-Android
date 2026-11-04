package com.example.spendflow.ui.categories

import android.view.View
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.spendflow.R
import com.example.spendflow.databinding.ItemCategoryBinding
import com.example.spendflow.domain.model.Category
import com.example.spendflow.domain.model.CategoryType

class CategoryAdapter(
    private val showDefaultCurrency: Boolean = false,
    private val onActions: ((View, Category) -> Unit)? = null,
) : ListAdapter<Category, CategoryAdapter.ViewHolder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemCategoryBinding.inflate(LayoutInflater.from(parent.context), parent, false),
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position), showDefaultCurrency, onActions)

    class ViewHolder(private val binding: ItemCategoryBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(category: Category, showDefaultCurrency: Boolean, onActions: ((View, Category) -> Unit)?) {
            binding.name.text = category.name
            binding.incomeSourceActions.isVisible = onActions != null
            if (onActions != null) {
                binding.incomeSourceActions.contentDescription =
                    binding.root.context.getString(R.string.income_sources_actions, category.name)
                binding.incomeSourceActions.setOnClickListener { onActions(it, category) }
            }
            if (showDefaultCurrency) {
                val currency = category.defaultCurrency
                binding.type.isVisible = currency != null
                binding.type.text = currency?.let { binding.root.context.getString(R.string.income_sources_default_currency, it) }
                return
            }
            binding.type.isVisible = true
            binding.type.setText(
                when (category.type) {
                    CategoryType.EXPENSE -> R.string.category_type_expense
                    CategoryType.INCOME -> R.string.category_type_income
                },
            )
        }
    }

    private object Diff : DiffUtil.ItemCallback<Category>() {
        override fun areItemsTheSame(oldItem: Category, newItem: Category) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Category, newItem: Category) = oldItem == newItem
    }
}
