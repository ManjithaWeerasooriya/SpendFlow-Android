package com.example.spendflow.data.repository

import com.example.spendflow.core.common.AppResult
import com.example.spendflow.domain.model.Category
import com.example.spendflow.domain.model.CategoryType
import kotlinx.coroutines.flow.Flow

interface CategoryRepository : UserOwnedRepository<Category> {
    /** Sorted by name, optionally only one [type]. Archived categories are left out unless [includeArchived]. */
    fun observeCategories(
        type: CategoryType? = null,
        includeArchived: Boolean = false,
    ): Flow<AppResult<List<Category>>>
}
