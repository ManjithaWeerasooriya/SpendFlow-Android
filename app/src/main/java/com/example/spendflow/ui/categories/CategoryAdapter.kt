package com.example.spendflow.ui.categories

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.spendflow.R
import com.example.spendflow.databinding.ItemCategoryBinding
import com.example.spendflow.domain.model.Category
import com.example.spendflow.domain.model.CategoryType

class CategoryAdapter : ListAdapter<Category, CategoryAdapter.ViewHolder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemCategoryBinding.inflate(LayoutInflater.from(parent.context), parent, false),
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    class ViewHolder(private val binding: ItemCategoryBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(category: Category) {
            binding.name.text = category.name
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
