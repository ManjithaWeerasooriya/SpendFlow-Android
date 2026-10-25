package com.example.spendflow.domain.model

data class SavingsGoal(val id: String, val name: String, val target: Money, val saved: Money) {
    init {
        require(name.isNotBlank() && name.length <= 100)
        require(target.amountMinor in 1..99_999_999_999L)
        require(saved.currencyCode == target.currencyCode)
        require(saved.amountMinor in 0..99_999_999_999L)
    }
    val isCompleted get() = saved.amountMinor >= target.amountMinor
}
