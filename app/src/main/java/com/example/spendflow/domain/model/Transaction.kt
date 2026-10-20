package com.example.spendflow.domain.model

import java.math.BigDecimal
import java.time.Instant

/**
 * A single money movement. [amount] is what was paid or received, in its own currency, and is
 * always positive; [type] gives the direction.
 *
 * [baseAmount] is the same value in the user's base currency, fixed when the transaction is saved
 * using [rate] (1 unit of [amount]'s currency = [rate] units of the base currency), so history
 * doesn't move when rates change. Reports and budgets sum [baseAmount]. In the base currency
 * itself, [rate] is 1 and [baseAmount] equals [amount].
 *
 * Expenses need a [paymentMethod]. A TRANSFER moves money from [accountId] to [transferAccountId]
 * and has no category or payment method.
 */
data class Transaction(
    val id: String,
    val type: TransactionType,
    val amount: Money,
    val rate: BigDecimal,
    val baseAmount: Money,
    val dateTime: Instant,
    val categoryId: String?,
    val paymentMethod: PaymentMethod?,
    val note: String? = null,
    val accountId: String? = null,
    val transferAccountId: String? = null,
    val recurringTransactionId: String? = null,
    /** Set by the server. Null only before a new transaction is first written. */
    val createdAt: Instant? = null,
    val updatedAt: Instant? = null,
) {
    init {
        requireValidMovement(type, amount, rate, baseAmount, accountId, categoryId, transferAccountId, paymentMethod)
    }
}

internal fun requireValidMovement(
    type: TransactionType,
    amount: Money,
    rate: BigDecimal,
    baseAmount: Money,
    accountId: String?,
    categoryId: String?,
    transferAccountId: String?,
    paymentMethod: PaymentMethod?,
) {
    require(amount.amountMinor > 0) { "Amount must be positive; use type for direction" }
    require(rate.signum() > 0) { "Rate must be positive" }
    // A tiny amount at a small rate can round to zero in the base currency
    require(baseAmount.amountMinor >= 0) { "Base amount must not be negative" }
    if (amount.currencyCode == baseAmount.currencyCode) {
        require(rate.compareTo(BigDecimal.ONE) == 0 && baseAmount.amountMinor == amount.amountMinor) {
            "In the base currency the rate is 1 and the base amount equals the amount"
        }
    }
    require((type == TransactionType.EXPENSE) == (paymentMethod != null)) {
        "Expenses need a payment method; income and transfers have none"
    }
    if (type == TransactionType.TRANSFER) {
        require(accountId != null && transferAccountId != null && transferAccountId != accountId) {
            "A transfer needs a source and a different destination account"
        }
        require(categoryId == null) { "A transfer has no category" }
    } else {
        require(transferAccountId == null) { "Only transfers have a destination account" }
    }
}
