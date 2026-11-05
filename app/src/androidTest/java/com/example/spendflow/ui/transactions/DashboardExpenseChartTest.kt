package com.example.spendflow.ui.transactions

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.intl.LocaleList
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.spendflow.R
import com.example.spendflow.core.common.AppError
import com.example.spendflow.core.common.AppResult
import com.example.spendflow.core.ui.UiState
import com.example.spendflow.domain.model.*
import com.example.spendflow.ui.theme.SpendFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.YearMonth

@RunWith(AndroidJUnit4::class)
class DashboardExpenseChartTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun text(id: Int, vararg args: Any) = context.getString(id, *args)
    private val month = YearMonth.of(2024, 10)
    private fun state(amounts: Map<String?, Long>, categories: AppResult<List<Category>>? = AppResult.Success(emptyList())) =
        dashboardExpenses(DashboardUiState(month, true, UiState.Success(DashboardSummary(MonthlySummary(
            month, Money(0, "LKR"), Money(amounts.values.sum(), "LKR"), expensesByCategory = amounts.mapValues { Money(it.value, "LKR") },
        ), amounts.isNotEmpty()))), categories)
    private var selected by mutableStateOf(month)
    private var chart by mutableStateOf(DashboardExpenseState(month))
    private var dark by mutableStateOf(false)
    private var language by mutableStateOf("en-US")
    private val opened = mutableListOf<YearMonth>()
    private fun show(initial: DashboardExpenseState) {
        chart = initial
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.Locales(LocaleList(language))) {
                SpendFlowTheme(darkTheme = dark) {
                    Column(Modifier.verticalScroll(rememberScrollState())) { DashboardExpenseChart(selected, chart) { opened += it } }
                }
            }
        }
    }

    @Test fun readOnlyLegendExposesAllAmountsPercentagesAndCategoryLabelsInBothThemes() {
        show(state(mapOf("old" to 5000, "missing-one" to 2000, "missing-two" to 2000, null to 1000),
            AppResult.Success(listOf(Category("old", "Old food", CategoryType.EXPENSE, isArchived = true)))))
        val descriptions = listOf(
            text(R.string.dashboard_expense_legend, text(R.string.dashboard_expense_archived, "Old food"), "LKR 50.00", "50%"),
            text(R.string.dashboard_expense_legend, text(R.string.dashboard_expense_unknown, "missing-one"), "LKR 20.00", "20%"),
            text(R.string.dashboard_expense_legend, text(R.string.dashboard_expense_unknown, "missing-two"), "LKR 20.00", "20%"),
            text(R.string.dashboard_expense_legend, text(R.string.analytics_uncategorized), "LKR 10.00", "10%"),
        )
        descriptions.forEach { compose.onNodeWithContentDescription(it).performScrollTo().assertIsDisplayed().assertHasNoClickAction() }
        compose.onAllNodesWithTag("dashboard_expense_row").assertCountEquals(4)
        compose.runOnIdle { dark = true }
        descriptions.forEach { compose.onNodeWithContentDescription(it).assertExists() }
    }

    @Test fun allCategoriesRemainInTheLegendBeyondThePaletteSize() {
        show(state((1..12).associate { "id-$it" to 100L }))
        compose.onAllNodesWithTag("dashboard_expense_row").assertCountEquals(12)
        compose.onNodeWithContentDescription(text(R.string.dashboard_expense_legend,
            text(R.string.dashboard_expense_unknown, "id-12"), "LKR 1.00", "8%" )).performScrollTo().assertIsDisplayed()
    }

    @Test fun zeroExpenseMonthsShowExplanationInsteadOfDonutOrInvalidPercentages() {
        show(state(emptyMap()))
        compose.onNodeWithText(text(R.string.dashboard_expense_none)).assertExists()
        compose.onNodeWithTag("dashboard_expense_donut").assertDoesNotExist()
        compose.onAllNodesWithTag("dashboard_expense_row").assertCountEquals(0)
        compose.runOnIdle { chart = state(mapOf("tiny-fx" to 0L)) }
        compose.onNodeWithText(text(R.string.dashboard_expense_none)).assertExists()
        compose.onNodeWithTag("dashboard_expense_donut").assertDoesNotExist()
    }

    @Test fun loadingErrorsAndStaleMonthsDoNotRenderOldLegend() {
        show(state(mapOf("old" to 100L)))
        compose.onAllNodesWithTag("dashboard_expense_row").assertCountEquals(1)
        compose.runOnIdle { selected = month.minusMonths(1) }
        compose.onNodeWithText(text(R.string.dashboard_expense_loading)).assertExists()
        compose.onAllNodesWithTag("dashboard_expense_row").assertCountEquals(0)
        compose.runOnIdle { chart = DashboardExpenseState(selected, UiState.Error(AppError.Network)) }
        compose.onNodeWithText(text(R.string.error_network)).assertExists()
        compose.onNodeWithText(text(R.string.dashboard_expense_none)).assertDoesNotExist()
    }

    @Test fun metadataLoadingFailureAndRecoveryNeverHideAmounts() {
        show(state(mapOf("food" to 100L), null))
        val pending = text(R.string.dashboard_expense_legend, text(R.string.dashboard_expense_pending, "food"), "LKR 1.00", "100%")
        compose.onNodeWithContentDescription(pending).assertExists()
        compose.onNodeWithText(text(R.string.dashboard_expense_names_loading)).assertExists()
        compose.runOnIdle { chart = state(mapOf("food" to 100L), AppResult.Error(AppError.Network)) }
        compose.onNodeWithContentDescription(pending).assertExists()
        compose.onNodeWithText(text(R.string.dashboard_expense_names_error, text(R.string.error_network))).assertExists()
        compose.runOnIdle { chart = state(mapOf("food" to 100L), AppResult.Success(listOf(Category("food", "Renamed food", CategoryType.EXPENSE)))) }
        compose.onNodeWithContentDescription(text(R.string.dashboard_expense_legend, "Renamed food", "LKR 1.00", "100%" )).assertExists()
    }

    @Test fun analyticsActionPassesSelectedMonthEvenWhileChartIsLoading() {
        show(DashboardExpenseState(month))
        compose.onNodeWithText(text(R.string.dashboard_view_analytics)).performClick()
        assertEquals(listOf(month), opened)
        compose.runOnIdle { selected = month.minusMonths(1) }
        compose.onNodeWithText(text(R.string.dashboard_view_analytics)).performClick()
        assertEquals(listOf(month, month.minusMonths(1)), opened)
    }

    @Test fun localeUpdatesAccessibleAmountsAndPercentages() {
        show(state(mapOf("food" to 100000), AppResult.Success(listOf(Category("food", "Food", CategoryType.EXPENSE)))))
        compose.runOnIdle { language = "de-DE" }
        val percent = com.example.spendflow.core.ui.components.PercentFormatter.formatFraction(java.math.BigDecimal.ONE, java.util.Locale.GERMANY)
        compose.onNodeWithContentDescription(text(R.string.dashboard_expense_legend, "Food", "LKR 1.000,00", percent)).assertExists()
    }
}
