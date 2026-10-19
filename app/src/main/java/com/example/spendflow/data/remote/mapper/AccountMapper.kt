package com.example.spendflow.data.remote.mapper

import com.example.spendflow.data.remote.FirestoreFields
import com.example.spendflow.domain.model.Account
import com.example.spendflow.domain.model.AccountType
import com.example.spendflow.domain.model.Money

private const val NAME = "name"
private const val TYPE = "type"
private const val BALANCE_MINOR = "balanceMinor"
private const val CURRENCY_CODE = "currencyCode"

fun Account.toFirestore(): Map<String, Any?> = mapOf(
    NAME to name,
    TYPE to type.name,
    BALANCE_MINOR to balance.amountMinor,
    CURRENCY_CODE to balance.currencyCode,
    FirestoreFields.IS_ARCHIVED to isArchived,
)

fun accountFromFirestore(id: String, data: Map<String, Any?>): Account = FirestoreData(id, data).build {
    Account(
        id = id,
        name = string(NAME),
        type = enum<AccountType>(TYPE),
        balance = Money(long(BALANCE_MINOR), string(CURRENCY_CODE)),
        isArchived = boolean(FirestoreFields.IS_ARCHIVED, default = false),
    )
}
