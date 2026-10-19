package com.example.spendflow.data.remote.mapper

import com.example.spendflow.data.remote.FirestoreFields
import com.example.spendflow.domain.model.PaymentMethod
import com.example.spendflow.domain.model.RecurrenceFrequency
import com.example.spendflow.domain.model.RecurringTransaction
import com.example.spendflow.domain.model.TransactionType

private const val FREQUENCY = "frequency"
private const val INTERVAL = "interval"
private const val START_DATE = "startDate"
private const val END_DATE = "endDate"

fun RecurringTransaction.toFirestore(): Map<String, Any?> =
    movementToFirestore(type, amount, rate, baseAmount, accountId, categoryId, transferAccountId, paymentMethod, note) +
        mapOf(
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
            type = enum<TransactionType>(MovementFields.TYPE),
            amount = amount(),
            rate = decimal(MovementFields.RATE),
            baseAmount = baseAmount(),
            categoryId = optString(FirestoreFields.CATEGORY_ID),
            paymentMethod = optEnum<PaymentMethod>(MovementFields.PAYMENT_METHOD),
            note = optString(MovementFields.NOTE),
            accountId = optString(FirestoreFields.ACCOUNT_ID),
            transferAccountId = optString(MovementFields.TRANSFER_ACCOUNT_ID),
            frequency = enum<RecurrenceFrequency>(FREQUENCY),
            interval = long(INTERVAL).toInt(),
            startDate = localDate(START_DATE),
            endDate = optLocalDate(END_DATE),
            nextOccurrence = localDate(FirestoreFields.NEXT_OCCURRENCE),
            isActive = boolean(FirestoreFields.IS_ACTIVE, default = true),
        )
    }
