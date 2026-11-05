package com.example.spendflow.ui.categories

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.spendflow.R
import com.example.spendflow.core.common.AppError
import com.example.spendflow.core.common.AppResult
import com.example.spendflow.domain.model.Budget
import com.example.spendflow.domain.model.BudgetPeriod
import com.example.spendflow.domain.model.Money
import java.time.LocalDate
import com.example.spendflow.core.ui.UiState
import com.example.spendflow.domain.model.Category
import com.example.spendflow.domain.model.CategoryType
import com.example.spendflow.ui.theme.SpendFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The stateless Categories screen and its dialogs: tabs, archive toggle, row actions, reorder and forms. */
@RunWith(AndroidJUnit4::class)
class CategoriesScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun text(id: Int, vararg args: Any) = context.getString(id, *args)

    private val groceries = Category("food", "Groceries", CategoryType.EXPENSE, iconName = "food", classification = "NEEDS")
    private val eatingOut = Category("fun", "Eating out", CategoryType.EXPENSE, iconName = "shopping", classification = "WANTS")
    private val gym = Category("gym", "Gym", CategoryType.EXPENSE, iconName = "health", isArchived = true)
    private val salary = Category("salary", "Salary", CategoryType.INCOME, defaultCurrency = "LKR")

    private var tab by mutableStateOf(CategoriesTab.CATEGORIES)
    private val actions = mutableListOf<Pair<String, CategoryAction>>()
    private val reorders = mutableListOf<List<String>>()
    private var retries = 0

    private fun show(
        expenses: UiState<List<Category>> = UiState.Success(listOf(groceries, eatingOut, gym)),
        incomeSources: UiState<List<Category>> = UiState.Success(listOf(salary)),
        busy: Boolean = false,
    ) = compose.setContent {
        SpendFlowTheme {
            CategoriesScreen(
                tab = tab,
                expenses = expenses,
                incomeSources = incomeSources,
                expenseBusy = busy,
                incomeBusy = busy,
                onBack = {},
                onSelectTab = { tab = it },
                onAddCategory = {},
                onAddIncomeSource = {},
                onExpenseAction = { c, a -> actions += c.id to a },
                onIncomeAction = { c, a -> actions += c.id to a },
                onReorder = { reorders += it },
                onRetry = { retries++ },
            )
        }
    }

    private fun openActions(name: String) =
        compose.onNodeWithContentDescription(text(R.string.category_actions, name)).performClick()

    @Test
    fun archivedToggleSwitchesBetweenActiveAndArchivedWithMatchingActions() {
        show()
        compose.onNodeWithText("Groceries").assertExists()
        compose.onNodeWithText(text(R.string.category_needs)).assertExists()
        compose.onNodeWithText("Gym").assertDoesNotExist()
        compose.onNodeWithText(text(R.string.category_archived, 1)).performClick()
        compose.onNodeWithText("Gym").assertExists()
        compose.onNodeWithText("Groceries").assertDoesNotExist()
        compose.onNodeWithText(text(R.string.category_reorder)).assertDoesNotExist()
        openActions("Gym")
        compose.onNodeWithText(text(R.string.category_rename)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.category_restore)).performClick()
        assertEquals(listOf("gym" to CategoryAction.RESTORE), actions)
    }

    @Test
    fun rowMenuReportsTheChosenAction() {
        show()
        openActions("Eating out")
        compose.onNodeWithText(text(R.string.category_cap)).performClick()
        openActions("Eating out")
        compose.onNodeWithText(text(R.string.category_move_up)).performClick()
        assertEquals(listOf("fun" to CategoryAction.CAP, "fun" to CategoryAction.MOVE_UP), actions)
    }

    @Test
    fun busyLocksAddArchiveToggleAndRowActions() {
        show(busy = true)
        compose.onNode(hasText(text(R.string.category_add_title)) and hasClickAction()).assertIsNotEnabled()
        compose.onNodeWithText(text(R.string.category_archived, 1)).assertIsNotEnabled()
        compose.onNodeWithContentDescription(text(R.string.category_actions, "Groceries")).assertIsNotEnabled()
    }

    @Test
    fun longPressDragReportsTheNewOrderOnce() {
        show()
        compose.onNodeWithText("Groceries").performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            repeat(10) {
                moveBy(Offset(0f, height * 0.4f))
                advanceEventTime(16)
            }
            up()
        }
        compose.waitForIdle()
        assertEquals(listOf(listOf("fun", "food")), reorders)
    }

    @Test
    fun incomeTabShowsDefaultCurrencyAndItsOwnActions() {
        show()
        compose.onNodeWithText(text(R.string.income_sources_tab)).performClick()
        compose.onNodeWithText(text(R.string.income_sources_default_currency, "LKR")).assertExists()
        compose.onNodeWithText(text(R.string.income_sources_add)).assertExists()
        openActions("Salary")
        compose.onNodeWithText(text(R.string.category_cap)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.category_archive)).performClick()
        assertEquals(listOf("salary" to CategoryAction.ARCHIVE), actions)
    }

    @Test
    fun errorStateRetriesAndEmptyStateExplains() {
        show(expenses = UiState.Error(AppError.Network), incomeSources = UiState.Success(emptyList()))
        compose.onNodeWithText(text(R.string.error_network)).assertExists()
        compose.onNodeWithText(text(R.string.action_retry)).performClick()
        assertEquals(1, retries)
        compose.onNodeWithText(text(R.string.income_sources_tab)).performClick()
        compose.onNodeWithText(text(R.string.income_sources_empty_title)).assertExists()
    }

    @Test
    fun capEditorWaitsForDataAndPreservesTheDraftAcrossObserverErrors() {
        val cap = Budget("cap", "Food cap", Money(20_000_00, "LKR"), BudgetPeriod.MONTHLY, LocalDate.of(2026, 10, 1), groceries.id)
        var caps by mutableStateOf<AppResult<List<Budget>>?>(null)
        var saved: String? = null
        compose.setContent {
            SpendFlowTheme {
                ObservedCategoryCapDialog(groceries, caps, false, null, onSave = { saved = it }, onDismiss = {})
            }
        }
        compose.onNodeWithText(text(R.string.category_cap_loading)).assertExists()
        compose.onNode(hasSetTextAction()).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.quick_save)).assertDoesNotExist()
        compose.runOnIdle { caps = AppResult.Success(listOf(cap)) }
        compose.onNode(hasSetTextAction() and hasText("20000.00")).performTextReplacement("25000")
        compose.runOnIdle { caps = AppResult.Error(AppError.Network) }
        compose.onNodeWithText(text(R.string.error_network)).assertExists()
        compose.onNodeWithText(text(R.string.quick_save)).assertDoesNotExist()
        compose.runOnIdle { caps = AppResult.Success(listOf(cap)) }
        compose.onNode(hasSetTextAction() and hasText("25000")).assertExists()
        compose.onNodeWithText(text(R.string.quick_save)).performClick()
        assertEquals("25000", saved)
    }

    @Test
    fun expenseDialogSavesNameIconAndClassification() {
        var saved: Triple<String, String, String>? = null
        compose.setContent {
            SpendFlowTheme {
                ExpenseCategoryDialog(groceries, saving = false, error = null, onSave = { n, i, c -> saved = Triple(n, i, c) }, onDismiss = {})
            }
        }
        compose.onNode(hasSetTextAction() and hasText("Groceries")).performTextReplacement("Supermarket")
        compose.onNodeWithText(text(R.string.category_needs)).performClick()
        compose.onNodeWithText(text(R.string.category_wants)).performClick()
        compose.onNodeWithText(text(R.string.quick_save)).performClick()
        assertEquals(Triple("Supermarket", "food", "WANTS"), saved)
    }

    @Test
    fun incomeDialogShowsErrorsCurrencyOnlyForNewSourcesAndLocksWhileSaving() {
        var saving by mutableStateOf(false)
        var source by mutableStateOf<Category?>(null)
        compose.setContent {
            SpendFlowTheme {
                IncomeSourceDialog(
                    source = source, saving = saving,
                    errors = IncomeSourceFormErrors(name = R.string.income_sources_name_required),
                    writeError = R.string.error_network, onSave = { _, _ -> }, onDismiss = {},
                )
            }
        }
        compose.onNodeWithText(text(R.string.income_sources_name_required)).assertExists()
        compose.onNodeWithText(text(R.string.error_network)).assertExists()
        compose.onNodeWithText(text(R.string.income_default_currency)).assertExists()
        saving = true
        compose.onNodeWithText(text(R.string.quick_save)).assertIsNotEnabled()
        compose.onNodeWithText(text(R.string.action_cancel)).assertIsNotEnabled()
        saving = false
        source = salary
        compose.onNodeWithText(text(R.string.income_default_currency)).assertDoesNotExist()
        compose.onAllNodesWithText(text(R.string.income_sources_rename_title)).fetchSemanticsNodes().let { assertEquals(1, it.size) }
    }
}
