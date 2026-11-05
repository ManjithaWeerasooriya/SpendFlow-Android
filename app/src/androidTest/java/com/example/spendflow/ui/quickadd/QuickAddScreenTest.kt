package com.example.spendflow.ui.quickadd

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.spendflow.R
import com.example.spendflow.core.common.AppError
import com.example.spendflow.core.ui.UiState
import com.example.spendflow.domain.model.Category
import com.example.spendflow.domain.model.CategoryType
import com.example.spendflow.ui.theme.SpendFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The stateless Quick Add form: field input, chips, income conversion controls, errors and the busy state. */
@RunWith(AndroidJUnit4::class)
class QuickAddScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun text(id: Int, vararg args: Any) = context.getString(id, *args)

    private val groceries = Category("food", "Groceries", CategoryType.EXPENSE, iconName = "food")
    private val calls = mutableListOf<String>()

    private fun show(state: QuickAddUiState) = compose.setContent {
        SpendFlowTheme {
            QuickAddForm(
                state,
                onAmount = { calls += "amount=$it" },
                onNote = { calls += "note=$it" },
                onToggleDetails = { calls += "details" },
                onType = { calls += "income=$it" },
                onCurrency = { calls += "currency=$it" },
                onRate = { calls += "rate=$it" },
                onLatestRate = { calls += "latest" },
                onCategory = { calls += "category=${it.id}" },
                onDate = { calls += "date" },
                onPayment = { calls += "payment=$it" },
                onSave = { calls += "save" },
                onClose = { calls += "close" },
            )
        }
    }

    @Test
    fun expenseFormReportsInputChipAndSave() {
        show(QuickAddUiState(categories = UiState.Success(listOf(groceries))))
        compose.onNodeWithTag(QuickAddTags.CURRENCY).assertDoesNotExist()
        compose.onNodeWithTag(QuickAddTags.DATE).assert(hasText(text(R.string.quick_date, text(R.string.quick_now))))
        compose.onNodeWithTag(QuickAddTags.AMOUNT).performTextReplacement("25.50")
        compose.onNodeWithText("Groceries").performScrollTo().performClick()
        compose.onNodeWithTag(QuickAddTags.SAVE).performScrollTo().performClick()
        compose.onNodeWithContentDescription(text(R.string.action_close)).performClick()
        assertEquals(listOf("amount=25.50", "category=food", "save", "close"), calls)
    }

    @Test
    fun fieldErrorsShowAndSavingLocksTheButton() {
        show(
            QuickAddUiState(
                categories = UiState.Success(listOf(groceries)),
                errors = EntryErrors(amount = R.string.entry_amount_required, category = R.string.entry_category_required),
                writeError = R.string.error_network,
                saving = true,
            ),
        )
        compose.onNodeWithTag(QuickAddTags.AMOUNT_ERROR).assertExists()
        compose.onNodeWithTag(QuickAddTags.CATEGORY_ERROR).assertExists()
        compose.onNodeWithText(text(R.string.error_network)).assertExists()
        compose.onNodeWithTag(QuickAddTags.SAVE).performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun foreignIncomeShowsRateControlsAndPreview() {
        show(
            QuickAddUiState(
                income = true, currency = "USD", baseCurrency = "LKR", rate = "300",
                categories = UiState.Success(emptyList()), rateLoading = true, rateFailed = true,
                errors = EntryErrors(rate = R.string.entry_rate_required), preview = "Approx. LKR 150,000",
            ),
        )
        compose.onNodeWithText(text(R.string.income_source)).assertExists()
        compose.onNodeWithTag(QuickAddTags.RATE_ERROR).assertExists()
        compose.onNodeWithText(text(R.string.income_latest_rate)).performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText(text(R.string.income_rate_error)).assertExists()
        compose.onNodeWithText("Approx. LKR 150,000").assertExists()
        compose.onNodeWithText(text(R.string.quick_empty)).assertExists()
    }

    @Test
    fun baseCurrencyIncomeHidesTheRate() {
        show(QuickAddUiState(income = true, currency = "LKR", baseCurrency = "LKR", categories = UiState.Error(AppError.Network)))
        compose.onNodeWithTag(QuickAddTags.CURRENCY).assertExists()
        compose.onNodeWithText(text(R.string.income_rate)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.income_preview_pending)).assertExists()
        compose.onNodeWithText(text(R.string.error_network)).assertExists()
    }

    @Test
    fun detailsShowPaymentAndNoteAndDateOpensThePicker() {
        show(QuickAddUiState(showDetails = true, categories = UiState.Success(listOf(groceries))))
        compose.onNodeWithTag(QuickAddTags.DETAILS).assertExists()
        compose.onNodeWithText(text(R.string.quick_payment)).assertExists()
        compose.onNodeWithText(text(R.string.quick_more)).performScrollTo().performClick()
        compose.onNodeWithTag(QuickAddTags.DATE).performScrollTo().performClick()
        compose.onNode(hasText(text(android.R.string.ok)) and hasAnyAncestor(isDialog())).assertExists()
        assertEquals(listOf("details"), calls)
    }
    @Test
    fun editingUsesTheSameFormWithStoredCurrencyAndRate() {
        show(QuickAddUiState(editing = true, income = true, amount = "500.00", currency = "USD", rate = "300.123456",
            categoryId = "salary", categories = UiState.Success(listOf(Category("salary", "Salary", CategoryType.INCOME)))))
        compose.onNodeWithText(text(R.string.transaction_edit_title)).assertExists()
        compose.onNodeWithTag(QuickAddTags.AMOUNT).assert(hasText("500.00"))
        compose.onNodeWithTag(QuickAddTags.CURRENCY).assertExists()
        compose.onNodeWithText("300.123456").assertExists()
    }
}
