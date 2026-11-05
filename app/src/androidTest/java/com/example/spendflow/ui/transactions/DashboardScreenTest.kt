package com.example.spendflow.ui.transactions

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.intl.LocaleList
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.DialogNavigator
import androidx.navigation.compose.NavHost
import androidx.navigation.testing.TestNavHostController
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.spendflow.R
import com.example.spendflow.core.common.AppError
import com.example.spendflow.core.ui.UiState
import com.example.spendflow.domain.model.Money
import com.example.spendflow.domain.model.MonthlySummary
import com.example.spendflow.domain.model.TransactionType
import com.example.spendflow.ui.main.*
import com.example.spendflow.ui.theme.SpendFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.YearMonth

@RunWith(AndroidJUnit4::class)
class DashboardScreenTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun text(id: Int, vararg args: Any) = context.getString(id, *args)
    private val month = YearMonth.of(2026, 10)
    private var dashboard by mutableStateOf(DashboardUiState(month, true))
    private var dark by mutableStateOf(false)
    private val calls = mutableListOf<String>()
    private fun summary(income: Long = 18500000, expenses: Long = 11240000) = MonthlySummary(
        month, Money(income, "LKR"), Money(expenses, "LKR"),
        incomeBySource = if (income == 0L) emptyMap() else mapOf("salary" to Money(income - 100, "LKR"), null to Money(100, "LKR")),
    )
    private fun ready(summary: MonthlySummary = summary(), hasTransactions: Boolean = true) =
        DashboardUiState(month, true, UiState.Success(DashboardSummary(summary, hasTransactions)))
    private fun feed(rows: List<FeedRow> = emptyList()) = FeedUiState(
        UiState.Success(rows), null, UiState.Success(null), "ALL", month, dashboard,
    )
    private fun show(initial: DashboardUiState, rows: List<FeedRow> = emptyList()) {
        dashboard = initial
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.Locales(LocaleList("en-US"))) {
                SpendFlowTheme(darkTheme = dark) {
                    TransactionFeedScreen(
                        feed(rows), home = true, onFilter = {}, onSeeAll = {}, onViewGoals = {},
                        onPreviousMonth = { calls += "previous" }, onNextMonth = { calls += "next" },
                        onAddTransaction = { calls += "add" },
                    )
                }
            }
        }
    }
    private fun metric(label: Int, value: String) = compose.onNodeWithContentDescription(text(R.string.summary_metric_value, text(label), value))

    @Test fun totalsAndDistinctSourceCountRenderInBothThemes() {
        show(ready())
        metric(R.string.summary_income, "LKR 185,000.00").assertExists()
        metric(R.string.summary_expenses, "LKR 112,400.00").assertExists()
        metric(R.string.summary_net_savings, "LKR 72,600.00").assertExists()
        compose.onNodeWithTag(DashboardTags.SOURCES).assertTextEquals("2 income sources")
        compose.onNodeWithContentDescription(text(R.string.summary_savings_rate_value, "39%")).assertExists()
        compose.runOnIdle { dark = true }
        metric(R.string.summary_net_savings, "LKR 72,600.00").assertExists()
        compose.onNodeWithTag(DashboardTags.SOURCES).assertTextEquals("2 income sources")
    }

    @Test fun monthButtonsAreAccessibleAndNextIsDisabledForCurrentMonth() {
        show(DashboardUiState(YearMonth.of(2026, 12), false))
        compose.onNodeWithText("December 2026").assertExists()
        compose.onNodeWithContentDescription(text(R.string.dashboard_next_month)).assertIsNotEnabled()
        compose.onNodeWithContentDescription(text(R.string.dashboard_previous_month)).performClick()
        compose.runOnIdle { dashboard = DashboardUiState(YearMonth.of(2026, 11), true) }
        compose.onNodeWithText("November 2026").assertExists()
        compose.onNodeWithContentDescription(text(R.string.dashboard_next_month)).performClick()
        assertEquals(listOf("previous", "next"), calls)
    }

    @Test fun expenseOnlyExplainsIncomeRequirementWithoutPercentageIndicators() {
        show(ready(summary(0, 500000)))
        metric(R.string.summary_expenses, "LKR 5,000.00").assertExists()
        metric(R.string.summary_net_savings, "−LKR 5,000.00").assertExists()
        compose.onNodeWithTag(DashboardTags.INCOME_REQUIRED).assertTextEquals(text(R.string.analytics_income_required))
        compose.onNodeWithContentDescription(text(R.string.summary_savings_rate_unavailable)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.summary_saved_ratio, text(R.string.summary_ratio_unavailable))).assertDoesNotExist()
        compose.onNodeWithTag(DashboardTags.EMPTY).assertDoesNotExist()
    }

    @Test fun emptySelectedMonthOffersAddEvenWhenRecentFeedContainsOtherMonths() {
        show(ready(summary(0, 0), false), listOf(FeedRow("other", "O", "Older transaction", "Other month", Money(-100, "LKR"), TransactionType.EXPENSE)))
        compose.onNodeWithTag(DashboardTags.EMPTY).assertExists()
        compose.onNodeWithText(text(R.string.dashboard_historical_add_hint), substring = true).assertExists()
        compose.onNode(hasAnyAncestor(hasTestTag(DashboardTags.EMPTY)) and hasText(text(R.string.nav_add_transaction))).performScrollTo().performClick()
        assertEquals(listOf("add"), calls)
    }

    @Test fun currentEmptyMonthDoesNotShowHistoricalDateHint() {
        show(ready(summary(0, 0), false).copy(canMoveNext = false))
        compose.onNodeWithTag(DashboardTags.EMPTY).assertExists()
        compose.onNodeWithText(text(R.string.dashboard_historical_add_hint), substring = true).assertDoesNotExist()
    }

    @Test fun loadingAndErrorReplacePriorSummaryWithoutShowingEmpty() {
        show(ready())
        compose.runOnIdle { dashboard = DashboardUiState(month.minusMonths(1), true) }
        compose.onNodeWithText("September 2026").assertExists()
        compose.onNodeWithText(text(R.string.dashboard_loading)).assertExists()
        metric(R.string.summary_income, "LKR 185,000.00").assertDoesNotExist()
        compose.onNodeWithTag(DashboardTags.EMPTY).assertDoesNotExist()
        compose.runOnIdle { dashboard = dashboard.copy(content = UiState.Error(AppError.Network)) }
        compose.onNodeWithText(text(R.string.error_network)).assertExists()
        compose.onNodeWithTag(DashboardTags.EMPTY).assertDoesNotExist()
        compose.runOnIdle { dashboard = ready(summary(0, 0), false) }
        compose.onNodeWithTag(DashboardTags.EMPTY).assertExists()
        compose.runOnIdle { dashboard = ready() }
        compose.onNodeWithTag(DashboardTags.EMPTY).assertDoesNotExist()
        metric(R.string.summary_income, "LKR 185,000.00").assertExists()
    }

    @Test fun transferOnlyMonthShowsZeroTotalsAndExplanationWithoutEmptyAction() {
        show(ready(summary(0, 0), true))
        metric(R.string.summary_income, "LKR 0.00").assertExists()
        compose.onNodeWithTag(DashboardTags.SOURCES).assertTextEquals("0 income sources")
        compose.onNodeWithTag(DashboardTags.INCOME_REQUIRED).assertExists()
        compose.onNodeWithTag(DashboardTags.EMPTY).assertDoesNotExist()
    }

    @Test fun transactionsRetainTotalsAndFiltersWithoutDashboardControls() {
        compose.setContent {
            SpendFlowTheme {
                TransactionFeedScreen(
                    feed().copy(monthly = summary(), filter = "EXPENSE"), false,
                    onFilter = { calls += it }, onSeeAll = {}, onViewGoals = {},
                )
            }
        }
        compose.onNodeWithTag(FeedTags.TOTALS).assertExists()
        compose.onNodeWithTag(FeedTags.filter("EXPENSE")).assertIsSelected()
        compose.onNodeWithTag(FeedTags.filter("INCOME")).performClick()
        compose.onNodeWithContentDescription(text(R.string.dashboard_previous_month)).assertDoesNotExist()
        compose.onNodeWithTag(DashboardTags.SOURCES).assertDoesNotExist()
        assertEquals(listOf("INCOME"), calls)
    }

    @Test fun emptyActionUsesExistingQuickAddDestinationAndReturnsToSelectedMonth() {
        dashboard = ready(summary(0, 0), false)
        lateinit var nav: TestNavHostController
        compose.setContent {
            val context = LocalContext.current
            nav = remember {
                TestNavHostController(context).apply {
                    navigatorProvider.addNavigator(ComposeNavigator())
                    navigatorProvider.addNavigator(DialogNavigator())
                }
            }
            SpendFlowTheme {
                NavHost(nav, startDestination = AppGraph.MAIN.route) {
                    appNavGraph { route, _ ->
                        if (route == Routes.HOME) TransactionFeedScreen(
                            feed(), true, onFilter = {}, onSeeAll = {}, onViewGoals = {}, onAddTransaction = nav::openQuickAdd,
                        ) else Text(route)
                    }
                }
            }
        }
        compose.onNode(hasAnyAncestor(hasTestTag(DashboardTags.EMPTY)) and hasText(text(R.string.nav_add_transaction))).performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(Routes.QUICK_ADD, nav.currentDestination?.route)
            assertEquals(Routes.HOME, nav.previousBackStackEntry?.destination?.route)
            nav.dismissQuickAdd()
        }
        compose.onNodeWithText("October 2026").assertExists()
    }
}
