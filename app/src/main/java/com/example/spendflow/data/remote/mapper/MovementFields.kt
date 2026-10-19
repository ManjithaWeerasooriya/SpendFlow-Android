package com.example.spendflow.data.remote.mapper

import com.example.spendflow.data.remote.FirestoreFields
import com.example.spendflow.domain.model.Money
import com.example.spendflow.domain.model.PaymentMethod
import com.example.spendflow.domain.model.TransactionType
import java.math.BigDecimal

/** Field names for the money movement shared by transactions and recurring templates. */
internal object MovementFields {
    const val TYPE = "type"
    const val AMOUNT = "amount"
    const val CURRENCY = "currency"
    const val RATE = "rate"
    const val BASE_AMOUNT = "baseAmount"
    const val BASE_CURRENCY = "baseCurrency"
    const val TRANSFER_ACCOUNT_ID = "transferAccountId"
    const val PAYMENT_METHOD = "paymentMethod"
    const val NOTE = "note"
}

internal fun movementToFirestore(
    type: TransactionType,
    amount: Money,
    rate: BigDecimal,
    baseAmount: Money,
    accountId: String?,
    categoryId: String?,
    transferAccountId: String?,
    paymentMethod: PaymentMethod?,
    note: String?,
): Map<String, Any?> = mapOf(
    MovementFields.TYPE to type.name,
    MovementFields.AMOUNT to amount.amountMinor,
    MovementFields.CURRENCY to amount.currencyCode,
    MovementFields.RATE to rate.toFirestore(),
    MovementFields.BASE_AMOUNT to baseAmount.amountMinor,
    MovementFields.BASE_CURRENCY to baseAmount.currencyCode,
    FirestoreFields.ACCOUNT_ID to accountId,
    FirestoreFields.CATEGORY_ID to categoryId,
    MovementFields.TRANSFER_ACCOUNT_ID to transferAccountId,
    MovementFields.PAYMENT_METHOD to paymentMethod?.name,
    MovementFields.NOTE to note,
)

internal fun FirestoreData.amount() = Money(long(MovementFields.AMOUNT), string(MovementFields.CURRENCY))
internal fun FirestoreData.baseAmount() = Money(long(MovementFields.BASE_AMOUNT), string(MovementFields.BASE_CURRENCY))
