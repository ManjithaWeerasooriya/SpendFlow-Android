package com.example.spendflow.data.repository

import com.example.spendflow.core.common.AppResult
import com.example.spendflow.domain.model.SavingsGoal
import kotlinx.coroutines.flow.Flow

interface GoalRepository : UserOwnedRepository<SavingsGoal> {
    fun observeGoals(): Flow<AppResult<List<SavingsGoal>>>
    /** Only server-acknowledged completions may generate notifications. */
    fun observeConfirmedGoals(): Flow<AppResult<List<SavingsGoal>>>
}
