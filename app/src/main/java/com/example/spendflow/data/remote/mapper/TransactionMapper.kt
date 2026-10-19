package com.example.spendflow.data.remote.mapper

import com.example.spendflow.data.remote.FirestoreFields
import com.example.spendflow.domain.model.PaymentMethod
import com.example.spendflow.domain.model.Transaction
import com.example.spendflow.domain.model.TransactionType

private const val RECURRING_TRANSACTION_ID = "recurringTransactionId"

/** createdAt/updatedAt are left out: the server sets them (see withCreateTimestamps). */
fun Transaction.toFirestore(): Map<String, Any?> =
    movementToFirestore(type, amount, rate, baseAmount, accountId, categoryId, transferAccountId, paymentMethod, note) +
        mapOf(
            FirestoreFields.DATE_TIME to dateTime.toTimestamp(),
            RECURRING_TRANSACTION_ID to recurringTransactionId,
        )

fun transactionFromFirestore(id: String, data: Map<String, Any?>): Transaction =
    FirestoreData(id, data).build {
        Transaction(
            id = id,
            type = enum<TransactionType>(MovementFields.TYPE),
            amount = amount(),
            rate = decimal(MovementFields.RATE),
            baseAmount = baseAmount(),
            dateTime = instant(FirestoreFields.DATE_TIME),
            categoryId = optString(FirestoreFields.CATEGORY_ID),
            paymentMethod = optEnum<PaymentMethod>(MovementFields.PAYMENT_METHOD),
            note = optString(MovementFields.NOTE),
            accountId = optString(FirestoreFields.ACCOUNT_ID),
            transferAccountId = optString(MovementFields.TRANSFER_ACCOUNT_ID),
            recurringTransactionId = optString(RECURRING_TRANSACTION_ID),
            createdAt = optInstant(FirestoreFields.CREATED_AT),
            updatedAt = optInstant(FirestoreFields.UPDATED_AT),
        )
    }
