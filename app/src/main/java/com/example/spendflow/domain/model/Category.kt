package com.example.spendflow.domain.model

/** [iconName] and [colorArgb] are optional display hints; the UI picks a fallback when null. */
data class Category(
    val id: String,
    val name: String,
    val type: CategoryType,
    val iconName: String? = null,
    val colorArgb: Int? = null,
    val isArchived: Boolean = false,
    val sortOrder: Long = 0,
) {
    init {
        require(name.isNotBlank()) { "Category name must not be blank" }
    }
}
