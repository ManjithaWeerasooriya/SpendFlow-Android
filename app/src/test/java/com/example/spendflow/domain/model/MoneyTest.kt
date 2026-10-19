package com.example.spendflow.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MoneyTest {

    @Test
    fun `adds and subtracts same currency`() {
        val a = Money(1_050, "USD")
        val b = Money(250, "USD")

        assertEquals(Money(1_300, "USD"), a + b)
        assertEquals(Money(800, "USD"), a - b)
        assertEquals(Money(-1_050, "USD"), -a)
    }

    @Test
    fun `rejects arithmetic across currencies`() {
        assertThrows(IllegalArgumentException::class.java) {
            Money(100, "USD") + Money(100, "LKR")
        }
    }

    @Test
    fun `rejects invalid currency codes`() {
        listOf("usd", "US", "USDX", "").forEach { code ->
            assertThrows(IllegalArgumentException::class.java) { Money(0, code) }
        }
    }
}
