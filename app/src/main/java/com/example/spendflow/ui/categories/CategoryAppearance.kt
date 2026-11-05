package com.example.spendflow.ui.categories

import com.example.spendflow.R

internal object CategoryAppearance {
    val icons = listOf("food", "home", "transport", "shopping", "health", "other")
    val iconLabels = listOf(R.string.category_icon_food, R.string.category_icon_home,
        R.string.category_icon_transport, R.string.category_icon_shopping, R.string.category_icon_health, R.string.category_icon_other)
    val classifications = listOf("NEEDS", "WANTS", "SAVINGS")
    val classificationLabels = listOf(R.string.category_needs, R.string.category_wants, R.string.category_savings)
    fun icon(name: String?) = when (name) {
        "food" -> R.drawable.ic_cat_food
        "home" -> R.drawable.ic_cat_housing
        "transport" -> R.drawable.ic_cat_transport
        "shopping" -> R.drawable.ic_cat_shopping
        "health" -> R.drawable.ic_cat_health
        else -> R.drawable.ic_cat_other
    }
    fun classification(name: String?): Int = classificationLabels.getOrNull(classifications.indexOf(name)) ?: R.string.category_unclassified
}
