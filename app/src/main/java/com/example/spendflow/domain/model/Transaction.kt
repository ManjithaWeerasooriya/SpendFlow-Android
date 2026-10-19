package com.example.spendflow.domain.model

import java.time.Instant

/**
 * A single money movement. [amount] is always positive; [type] gives the direction.
 * A TRANSFER moves money from [accountId] to [transferAccountId] and has no category.
 */
data class Transaction(
    val id: String,
    val type: TransactionType,
    val amount: Money,
    val accountId: String,
    val categoryId: String?,
    val transferAccountId: String? = null,
    val note: String? = null,
    val occurredAt: Instant,
    val recurringTransactionId: String? = null,
) {
    init {
        requireValidMovement(type, amount, accountId, categoryId, transferAccountId)
    }
}

internal fun requireValidMovement(
    type: TransactionType,
    amount: Money,
    accountId: String,
    categoryId: String?,
    transferAccountId: String?,
) {
    require(amount.amountMinor > 0) { "Amount must be positive; use type for direction" }
    if (type == TransactionType.TRANSFER) {
        require(transferAccountId != null && transferAccountId != accountId) {
            "A transfer needs a different destination account"
        }
        require(categoryId == null) { "A transfer has no category" }
    } else {
        require(transferAccountId == null) { "Only transfers have a destination account" }
    }
}
