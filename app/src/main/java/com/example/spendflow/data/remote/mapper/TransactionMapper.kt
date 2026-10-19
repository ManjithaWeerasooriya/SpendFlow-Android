package com.example.spendflow.data.remote.mapper

import com.example.spendflow.data.remote.FirestoreFields
import com.example.spendflow.domain.model.Money
import com.example.spendflow.domain.model.Transaction
import com.example.spendflow.domain.model.TransactionType

private const val TYPE = "type"
private const val AMOUNT_MINOR = "amountMinor"
private const val CURRENCY_CODE = "currencyCode"
private const val TRANSFER_ACCOUNT_ID = "transferAccountId"
private const val NOTE = "note"
private const val RECURRING_TRANSACTION_ID = "recurringTransactionId"

fun Transaction.toFirestore(): Map<String, Any?> = mapOf(
    TYPE to type.name,
    AMOUNT_MINOR to amount.amountMinor,
    CURRENCY_CODE to amount.currencyCode,
    FirestoreFields.ACCOUNT_ID to accountId,
    FirestoreFields.CATEGORY_ID to categoryId,
    TRANSFER_ACCOUNT_ID to transferAccountId,
    NOTE to note,
    FirestoreFields.OCCURRED_AT to occurredAt.toTimestamp(),
    RECURRING_TRANSACTION_ID to recurringTransactionId,
)

fun transactionFromFirestore(id: String, data: Map<String, Any?>): Transaction =
    FirestoreData(id, data).build {
        Transaction(
            id = id,
            type = enum<TransactionType>(TYPE),
            amount = Money(long(AMOUNT_MINOR), string(CURRENCY_CODE)),
            accountId = string(FirestoreFields.ACCOUNT_ID),
            categoryId = optString(FirestoreFields.CATEGORY_ID),
            transferAccountId = optString(TRANSFER_ACCOUNT_ID),
            note = optString(NOTE),
            occurredAt = instant(FirestoreFields.OCCURRED_AT),
            recurringTransactionId = optString(RECURRING_TRANSACTION_ID),
        )
    }
