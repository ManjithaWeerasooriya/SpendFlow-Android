package com.example.spendflow.data.remote

/**
 * Collection layout. Everything a user owns lives under their own document, so security rules
 * can grant access with a single `request.auth.uid == uid` check:
 *
 * ```
 * users/{uid}                          User profile
 * users/{uid}/accounts/{id}            Account
 * users/{uid}/categories/{id}          Category
 * users/{uid}/transactions/{id}        Transaction (references accountId, categoryId by ID)
 * users/{uid}/budgets/{id}             Budget
 * users/{uid}/recurring/{id}           RecurringTransaction
 * ```
 */
object FirestorePaths {
    const val USERS = "users"
    const val ACCOUNTS = "accounts"
    const val CATEGORIES = "categories"
    const val TRANSACTIONS = "transactions"
    const val BUDGETS = "budgets"
    const val RECURRING = "recurring"

    fun user(uid: String) = "$USERS/${requireSegment(uid)}"
    fun accounts(uid: String) = "${user(uid)}/$ACCOUNTS"
    fun categories(uid: String) = "${user(uid)}/$CATEGORIES"
    fun transactions(uid: String) = "${user(uid)}/$TRANSACTIONS"
    fun budgets(uid: String) = "${user(uid)}/$BUDGETS"
    fun recurring(uid: String) = "${user(uid)}/$RECURRING"

    // A blank or slash-containing ID would silently address a different path
    private fun requireSegment(id: String): String {
        require(id.isNotBlank() && '/' !in id) { "Invalid path segment: '$id'" }
        return id
    }
}

/** Field names used in queries and by the DTOs, so the two can't drift apart. */
object FirestoreFields {
    const val CREATED_AT = "createdAt"
    const val UPDATED_AT = "updatedAt"
    const val IS_ARCHIVED = "isArchived"
    const val ACCOUNT_ID = "accountId"
    const val CATEGORY_ID = "categoryId"
    const val DATE_TIME = "dateTime"
    const val NEXT_OCCURRENCE = "nextOccurrence"
    const val IS_ACTIVE = "isActive"
}
