package com.example.spendflow.domain.model

/** Where money is held. [balance] may be negative, e.g. for a credit card. */
data class Account(
    val id: String,
    val name: String,
    val type: AccountType,
    val balance: Money,
    val isArchived: Boolean = false,
) {
    init {
        require(name.isNotBlank()) { "Account name must not be blank" }
    }
}
