package com.example.spendflow.ui.auth

import com.example.spendflow.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CredentialsValidatorTest {

    @Test
    fun `accepts well-formed emails`() {
        listOf("name@mail.com", "first.last+tag@sub.example.co", "a_b%c-d@x-y.lk").forEach {
            assertNull(it, CredentialsValidator.email(it))
        }
    }

    @Test
    fun `rejects emails the Firestore rules would reject`() {
        assertEquals(R.string.auth_error_email_required, CredentialsValidator.email(""))
        listOf("david@mail", "david", "@mail.com", "david@.c", "da vid@mail.com", "david@mail.c", "a@b.c0m")
            .forEach { assertEquals(it, R.string.auth_error_email_invalid, CredentialsValidator.email(it)) }
        val tooLong = "a".repeat(250) + "@m.com"
        assertEquals(R.string.auth_error_email_invalid, CredentialsValidator.email(tooLong))
    }

    @Test
    fun `new password needs at least 6 characters`() {
        assertEquals(R.string.auth_error_password_required, CredentialsValidator.newPassword(""))
        assertEquals(R.string.auth_error_password_short, CredentialsValidator.newPassword("12345"))
        assertNull(CredentialsValidator.newPassword("123456"))
    }

    @Test
    fun `login password only needs to be present`() {
        assertEquals(R.string.auth_error_password_required, CredentialsValidator.loginPassword(""))
        assertNull(CredentialsValidator.loginPassword("1"))
    }

    @Test
    fun `confirmation must match exactly`() {
        assertEquals(R.string.auth_error_confirm_required, CredentialsValidator.confirmation("secret1", ""))
        assertEquals(R.string.auth_error_confirm_mismatch, CredentialsValidator.confirmation("secret1", "Secret1"))
        assertNull(CredentialsValidator.confirmation("secret1", "secret1"))
    }
}
