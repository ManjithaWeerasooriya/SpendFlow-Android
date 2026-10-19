package com.example.spendflow.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class FirestorePathsTest {

    @Test
    fun `entity collections live under the user document`() {
        assertEquals("users/u1", FirestorePaths.user("u1"))
        assertEquals("users/u1/accounts", FirestorePaths.accounts("u1"))
        assertEquals("users/u1/categories", FirestorePaths.categories("u1"))
        assertEquals("users/u1/transactions", FirestorePaths.transactions("u1"))
        assertEquals("users/u1/budgets", FirestorePaths.budgets("u1"))
        assertEquals("users/u1/recurring", FirestorePaths.recurring("u1"))
    }

    @Test
    fun `rejects uids that would change the path`() {
        listOf("", " ", "u1/accounts", "../u2").forEach { uid ->
            assertThrows(IllegalArgumentException::class.java) { FirestorePaths.accounts(uid) }
        }
    }
}
