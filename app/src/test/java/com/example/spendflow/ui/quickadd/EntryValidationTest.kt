package com.example.spendflow.ui.quickadd

import com.example.spendflow.R
import com.example.spendflow.domain.model.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EntryValidationTest {
    private fun error(text: String, currency: String = "USD") = EntryValidation.amountError(text, currency)

    @Test
    fun `empty or blank amount is required`() {
        assertEquals(R.string.entry_amount_required, error(""))
        assertEquals(R.string.entry_amount_required, error("   "))
    }

    @Test
    fun `non numeric text is invalid`() {
        assertEquals(R.string.entry_amount_invalid, error("abc"))
        assertEquals(R.string.entry_amount_invalid, error("1.2.3"))
        assertEquals(R.string.entry_amount_invalid, error("."))
        assertEquals(R.string.entry_amount_invalid, error("1e3"))
    }

    @Test
    fun `zero and negative amounts must be positive`() {
        assertEquals(R.string.entry_amount_positive, error("0"))
        assertEquals(R.string.entry_amount_positive, error("0.00"))
        assertEquals(R.string.entry_amount_positive, error("-5"))
    }

    @Test
    fun `precision follows the currency minor units`() {
        assertEquals(R.string.entry_amount_precision, error("1.234"))
        assertEquals(R.string.entry_amount_precision, error("1.5", "JPY"))
        assertNull(error("1.50"))
        assertNull(error("100", "JPY"))
    }

    @Test
    fun `amount above the maximum is too large`() {
        assertNull(error("999999999.99"))
        assertEquals(R.string.entry_amount_large, error("1000000000"))
    }

    @Test
    fun `comma decimal and leading or trailing dot forms are valid`() {
        assertNull(error("12,34"))
        assertNull(error(" 5. "))
        assertNull(error(".5"))
    }

    @Test
    fun `parseAmount converts to minor units`() {
        assertEquals(Money(1_234, "USD"), QuickAddViewModel.parseAmount("12,34", "USD"))
        assertEquals(Money(1_200, "USD"), QuickAddViewModel.parseAmount(" 12 ", "USD"))
        assertEquals(Money(500, "JPY"), QuickAddViewModel.parseAmount("500", "JPY"))
    }

    @Test
    fun `parseAmount returns null for invalid input`() {
        assertNull(QuickAddViewModel.parseAmount("", "USD"))
        assertNull(QuickAddViewModel.parseAmount("-1", "USD"))
        assertNull(QuickAddViewModel.parseAmount("1.234", "USD"))
        assertNull(QuickAddViewModel.parseAmount("x", "USD"))
    }
}
