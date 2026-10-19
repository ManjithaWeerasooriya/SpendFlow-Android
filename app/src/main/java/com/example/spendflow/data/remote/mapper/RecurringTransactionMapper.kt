package com.example.spendflow.data.remote.mapper

import com.example.spendflow.data.remote.FirestoreFields
import com.example.spendflow.domain.model.Money
import com.example.spendflow.domain.model.RecurrenceFrequency
import com.example.spendflow.domain.model.RecurringTransaction
import com.example.spendflow.domain.model.TransactionType

private const val TYPE = "type"
private const val AMOUNT_MINOR = "amountMinor"
private const val CURRENCY_CODE = "currencyCode"
private const val TRANSFER_ACCOUNT_ID = "transferAccountId"
private const val NOTE = "note"
private const val FREQUENCY = "frequency"
private const val INTERVAL = "interval"
private const val START_DATE = "startDate"
private const val END_DATE = "endDate"

fun RecurringTransaction.toFirestore(): Map<String, Any?> = mapOf(
    TYPE to type.name,
    AMOUNT_MINOR to amount.amountMinor,
    CURRENCY_CODE to amount.currencyCode,
    FirestoreFields.ACCOUNT_ID to accountId,
    FirestoreFields.CATEGORY_ID to categoryId,
    TRANSFER_ACCOUNT_ID to transferAccountId,
    NOTE to note,
    FREQUENCY to frequency.name,
    INTERVAL to interval.toLong(),
    START_DATE to startDate.toFirestore(),
    END_DATE to endDate?.toFirestore(),
    FirestoreFields.NEXT_OCCURRENCE to nextOccurrence.toFirestore(),
    FirestoreFields.IS_ACTIVE to isActive,
)

fun recurringTransactionFromFirestore(id: String, data: Map<String, Any?>): RecurringTransaction =
    FirestoreData(id, data).build {
        RecurringTransaction(
            id = id,
            type = enum<TransactionType>(TYPE),
            amount = Money(long(AMOUNT_MINOR), string(CURRENCY_CODE)),
            accountId = string(FirestoreFields.ACCOUNT_ID),
            categoryId = optString(FirestoreFields.CATEGORY_ID),
            transferAccountId = optString(TRANSFER_ACCOUNT_ID),
            note = optString(NOTE),
            frequency = enum<RecurrenceFrequency>(FREQUENCY),
            interval = long(INTERVAL).toInt(),
            startDate = localDate(START_DATE),
            endDate = optLocalDate(END_DATE),
            nextOccurrence = localDate(FirestoreFields.NEXT_OCCURRENCE),
            isActive = boolean(FirestoreFields.IS_ACTIVE, default = true),
        )
    }
