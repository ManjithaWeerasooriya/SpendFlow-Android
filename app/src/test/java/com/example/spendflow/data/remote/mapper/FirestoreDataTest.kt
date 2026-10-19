package com.example.spendflow.data.remote.mapper

import com.example.spendflow.data.remote.FirestoreFields
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FirestoreDataTest {

    private val validAccount = mapOf<String, Any?>(
        "name" to "Cash",
        "type" to "CASH",
        "balanceMinor" to 100L,
        "currencyCode" to "USD",
    )

    private fun assertMappingError(data: Map<String, Any?>, messagePart: String) {
        val error = assertThrows(FirestoreMappingException::class.java) { accountFromFirestore("a1", data) }
        assertTrue("'${error.message}' should mention '$messagePart'", messagePart in error.message!!)
        assertTrue("should name the document", "a1" in error.message!!)
    }

    @Test
    fun `missing optional boolean uses default`() {
        assertFalse(accountFromFirestore("a1", validAccount).isArchived)
    }

    @Test
    fun `missing required field`() {
        assertMappingError(validAccount - "name", "'name'")
    }

    @Test
    fun `wrong field type`() {
        assertMappingError(validAccount + ("balanceMinor" to 1.5), "'balanceMinor'")
        assertMappingError(validAccount + ("name" to 42L), "'name'")
    }

    @Test
    fun `unknown enum value`() {
        assertMappingError(validAccount + ("type" to "CRYPTO"), "CRYPTO")
    }

    @Test
    fun `domain invariant failure becomes a mapping error`() {
        assertMappingError(validAccount + ("currencyCode" to "usd"), "usd")
    }

    @Test
    fun `invalid stored date`() {
        val budget = mapOf<String, Any?>(
            "name" to "All", "limitMinor" to 1L, "currencyCode" to "USD",
            "period" to "MONTHLY", "startDate" to "19/10/2026",
        )
        val error = assertThrows(FirestoreMappingException::class.java) { budgetFromFirestore("b1", budget) }
        assertTrue("startDate" in error.message!!)
    }

    @Test
    fun `invalid rate and unknown payment method`() {
        val expense = mapOf<String, Any?>(
            "type" to "EXPENSE", "amount" to 100L, "currency" to "USD", "rate" to "300.25",
            "baseAmount" to 30_025L, "baseCurrency" to "LKR", "paymentMethod" to "CASH",
            FirestoreFields.DATE_TIME to Timestamp(1_760_000_000, 0),
        )
        transactionFromFirestore("t1", expense)

        val badRate = assertThrows(FirestoreMappingException::class.java) {
            transactionFromFirestore("t1", expense + ("rate" to "3,00"))
        }
        assertTrue("rate" in badRate.message!!)

        val badMethod = assertThrows(FirestoreMappingException::class.java) {
            transactionFromFirestore("t1", expense + ("paymentMethod" to "BITCOIN"))
        }
        assertTrue("BITCOIN" in badMethod.message!!)
    }

    @Test
    fun `timestamp helpers add audit fields`() {
        val create = validAccount.withCreateTimestamps()
        assertTrue(FirestoreFields.CREATED_AT in create && FirestoreFields.UPDATED_AT in create)

        val update = validAccount.withUpdateTimestamp()
        assertFalse(FirestoreFields.CREATED_AT in update)
        assertTrue(FirestoreFields.UPDATED_AT in update)
        assertEquals(validAccount.size + 1, update.size)
    }
}
