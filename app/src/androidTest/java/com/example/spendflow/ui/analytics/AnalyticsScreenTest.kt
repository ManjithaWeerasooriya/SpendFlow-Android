package com.example.spendflow.ui.analytics

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.spendflow.R
import com.example.spendflow.domain.model.Category
import com.example.spendflow.domain.model.CategoryType
import com.example.spendflow.domain.model.Money
import com.example.spendflow.domain.model.Transaction
import com.example.spendflow.domain.model.TransactionType
import com.example.spendflow.ui.theme.SpendFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** The stateless Analytics screen: month navigation, empty and income-required states, breakdown and drill-down. */
@RunWith(AndroidJUnit4::class)
class AnalyticsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun text(id: Int, vararg args: Any) = context.getString(id, *args)

    private val zone = ZoneId.of("UTC")
    private val month = YearMonth.of(2026, 10)
    private val categories = listOf(
        Category("food", "Groceries", CategoryType.EXPENSE),
        Category("shop", "Shopping", CategoryType.EXPENSE),
        Category("salary", "Salary", CategoryType.INCOME),
    )

    private fun tx(id: String, category: String, minor: Long, day: Int, type: TransactionType = TransactionType.EXPENSE, note: String? = null) =
        Transaction(id, type, Money(minor, "LKR"), BigDecimal.ONE, Money(minor, "LKR"),
            month.atDay(day).atStartOfDay(zone).toInstant(), category, null, note = note)

    private fun report(vararg items: Transaction) =
        AnalyticsReport.build(month, "LKR", items.toList(), categories, LocalDate.of(2026, 10, 31), zone)

    private var state by mutableStateOf(AnalyticsUiState(month, canMoveNext = false, incomeMode = false, AnalyticsContent.Loading))
    private val calls = mutableListOf<String>()

    private fun show(content: AnalyticsContent, canMoveNext: Boolean = false) {
        state = AnalyticsUiState(month, canMoveNext, incomeMode = false, content)
        compose.setContent {
            SpendFlowTheme {
                AnalyticsScreen(
                    state, zone,
                    onPreviousMonth = { calls += "previous" },
                    onNextMonth = { calls += "next" },
                    onIncomeMode = { calls += "income=$it"; state = state.copy(incomeMode = it) },
                    onAddTransaction = { calls += "add" },
                )
            }
        }
    }

    @Test
    fun monthButtonsReportAndNextIsDisabledOnTheCurrentMonth() {
        show(AnalyticsContent.Loading)
        compose.onNodeWithText(text(R.string.analytics_loading)).assertExists()
        compose.onNodeWithContentDescription(text(R.string.analytics_next_month)).assertIsNotEnabled()
        compose.onNodeWithContentDescription(text(R.string.analytics_previous_month)).performClick()
        assertEquals(listOf("previous"), calls)
    }

    @Test
    fun emptyMonthOffersToAddATransaction() {
        show(AnalyticsContent.Empty, canMoveNext = true)
        compose.onNodeWithText(text(R.string.analytics_empty_month_title)).assertExists()
        compose.onNode(hasText(text(R.string.nav_add_transaction)) and hasClickAction()).performClick()
        compose.onNodeWithContentDescription(text(R.string.analytics_next_month)).performClick()
        assertEquals(listOf("add", "next"), calls)
    }

    @Test
    fun breakdownSwitchesBetweenExpensesAndIncome() {
        show(AnalyticsContent.Populated(report(
            tx("g", "food", 1_000_00, 3), tx("s", "shop", 3_000_00, 8), tx("p", "salary", 10_000_00, 1, TransactionType.INCOME),
        )))
        compose.onNode(hasText("4,000.00")).assertExists()
        compose.onAllNodesWithTag(AnalyticsTags.CATEGORY_ROW).assertCountEquals(2)
        compose.onNodeWithTag(AnalyticsTags.BREAKDOWN_INCOME).performScrollTo().performClick()
        compose.onNode(hasText("10,000.00")).assertExists()
        compose.onAllNodesWithTag(AnalyticsTags.CATEGORY_ROW).assertCountEquals(1)
        compose.onNodeWithText(text(R.string.analytics_income_breakdown)).assertExists()
        assertEquals(listOf("income=true"), calls)
    }

    @Test
    fun expensesWithoutIncomeExplainTheMissingPercentages() {
        show(AnalyticsContent.Populated(report(tx("g", "food", 1_000_00, 3))))
        compose.onNodeWithTag(AnalyticsTags.INCOME_REQUIRED).assertExists()
        compose.onNodeWithContentDescription(text(R.string.summary_savings_rate_unavailable)).assertExists()
    }

    @Test
    fun categoryRowOpensItsTransactionsAndDailyTotalsOpenTheirDialog() {
        show(AnalyticsContent.Populated(report(
            tx("g", "food", 1_000_00, 3, note = "Weekly shop"), tx("s", "shop", 3_000_00, 8),
        )))
        compose.onAllNodesWithTag(AnalyticsTags.CATEGORY_ROW)[1].performScrollTo().performClick()
        compose.onNode(hasText("Weekly shop", substring = true) and hasAnyAncestor(isDialog())).assertExists()
        compose.onNode(hasText(text(R.string.action_close)) and hasAnyAncestor(isDialog())).performClick()
        compose.onNode(isDialog()).assertDoesNotExist()
        compose.onNodeWithTag(AnalyticsTags.DAILY_DETAILS).performScrollTo().performClick()
        compose.onNode(hasText(text(R.string.analytics_cashflow)) and hasAnyAncestor(isDialog())).assertExists()
    }
}
