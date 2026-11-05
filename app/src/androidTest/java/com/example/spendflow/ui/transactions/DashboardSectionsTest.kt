package com.example.spendflow.ui.transactions

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.navigation.compose.*
import androidx.navigation.testing.TestNavHostController
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.spendflow.R
import com.example.spendflow.core.common.AppError
import com.example.spendflow.core.ui.UiState
import com.example.spendflow.domain.model.*
import com.example.spendflow.ui.main.*
import com.example.spendflow.ui.theme.SpendFlowTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.YearMonth

@RunWith(AndroidJUnit4::class)
class DashboardSectionsTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun text(id: Int, vararg args: Any) = context.getString(id, *args)
    private val month = YearMonth.now()
    private val goal = SavingsGoal("g", "Lifetime goal", Money(100000, "LKR"), Money(60000, "LKR"))
    private fun cap(id: String = "food", fraction: String = "1.2") = DashboardCap(
        id, "Cap $id", Money((BigDecimal(fraction) * BigDecimal(10000)).toLong(), "LKR"), Money(10000, "LKR"), BigDecimal(fraction),
    )
    private fun ready(vararg caps: DashboardCap, overall: DashboardCap? = null) =
        DashboardCapsState(month, DashboardCapsContent.Ready(CapPreviews(overall, caps.toList())))
    private fun feed(
        rows: UiState<List<FeedRow>> = UiState.Success(emptyList()),
        goalState: UiState<SavingsGoal?> = UiState.Success(goal),
        caps: DashboardCapsState = ready(),
        selected: YearMonth = month,
    ) = FeedUiState(rows, null, goalState, "ALL", month,
        DashboardUiState(selected, selected < month, UiState.Success(DashboardSummary(
            MonthlySummary(selected, Money(100000, "LKR"), Money(12000, "LKR")), true,
        ))), caps)

    @Test fun goalLoadingEmptyErrorsUpdatesAndCompletionAreExplicit() {
        var state by mutableStateOf<UiState<SavingsGoal?>>(UiState.Loading)
        compose.setContent { SpendFlowTheme { DashboardGoal(state) } }
        compose.onNodeWithText(text(R.string.goals_loading)).assertExists()
        compose.runOnIdle { state = UiState.Error(AppError.Network) }
        compose.onNodeWithText(text(R.string.error_network)).assertExists()
        compose.runOnIdle { state = UiState.Success(null) }
        compose.onNodeWithText(text(R.string.dashboard_goals_empty)).assertExists()
        compose.runOnIdle { state = UiState.Success(goal) }
        compose.onNodeWithText(goal.name).assertExists()
        compose.runOnIdle { state = UiState.Success(goal.copy(saved = goal.target)) }
        compose.onNodeWithText(text(R.string.goal_completed_label, goal.name)).assertExists()
        compose.runOnIdle { state = UiState.Success(null) }
        compose.onNodeWithText(text(R.string.dashboard_goals_empty)).assertExists()
    }

    @Test fun capPercentagesBelowAtAndAboveLimitKeepTrueValuesAndCapOnlyTheVisualBar() {
        var value by mutableStateOf("0.5")
        compose.setContent { SpendFlowTheme { DashboardCapsSection(month, ready(cap(fraction = value)), {}) } }
        fun check(percent: String, visual: Float) {
            compose.onNodeWithContentDescription("Cap food, $percent").assertRangeInfoEquals(androidx.compose.ui.semantics.ProgressBarRangeInfo(visual, 0f..1f))
            compose.onNodeWithText(percent).assertExists()
        }
        check("50%", .5f)
        compose.runOnIdle { value = "1" }
        check("100%", 1f)
        compose.runOnIdle { value = "1.2" }
        check("120%", 1f)
        compose.onNodeWithText(text(R.string.dashboard_cap_over, "LKR 20.00")).assertExists()
    }

    @Test fun overallAndThreeCategoryCapsRenderSeparatelyWithArchivedAndMissingLabelsInDarkMode() {
        val rows = ready(cap("a"), cap("b").copy(categoryLabel = CapCategoryLabel.ARCHIVED),
            cap("c").copy(categoryLabel = CapCategoryLabel.MISSING), overall = cap().copy(categoryId = null, name = "Overall"))
        compose.setContent { SpendFlowTheme(darkTheme = true) {
            Column(Modifier.verticalScroll(rememberScrollState())) { DashboardCapsSection(month, rows, {}) }
        } }
        compose.onNodeWithText(text(R.string.dashboard_overall_cap)).assertExists()
        compose.onAllNodesWithTag("dashboard_cap_row").assertCountEquals(4)
        compose.onNodeWithText(text(R.string.dashboard_cap_archived)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.dashboard_cap_missing)).performScrollTo().assertIsDisplayed()
    }

    @Test fun unavailableCapsExplainTheReasonWithoutPercentagesOrProgressBars() {
        var issue by mutableStateOf(CapIssue.DUPLICATE)
        compose.setContent { SpendFlowTheme { DashboardCapsSection(month, ready(cap().copy(fraction = null, issue = issue)), {}) } }
        for ((reason, resource) in listOf(
            CapIssue.DUPLICATE to R.string.dashboard_cap_duplicate,
            CapIssue.START_DATE to R.string.dashboard_cap_start_date,
            CapIssue.CURRENCY to R.string.dashboard_cap_currency,
            CapIssue.INVALID_LIMIT to R.string.dashboard_cap_invalid,
            CapIssue.CATEGORY_TYPE to R.string.dashboard_cap_information,
        )) {
            compose.runOnIdle { issue = reason }
            compose.onNodeWithText(text(resource)).assertExists()
            compose.onAllNodes(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo)).assertCountEquals(0)
            compose.onNodeWithText("0%").assertDoesNotExist()
        }
    }

    @Test fun loadingErrorEmptyAndHistoricalCapsAreDistinctAndStaleMonthRowsAreHidden() {
        var selected by mutableStateOf(month)
        var state by mutableStateOf(ready(cap()))
        compose.setContent { SpendFlowTheme { DashboardCapsSection(selected, state, {}) } }
        compose.onNodeWithText("Cap food").assertExists()
        compose.runOnIdle { selected = month.minusMonths(1) }
        compose.onNodeWithText(text(R.string.category_cap_loading)).assertExists()
        compose.onNodeWithText("Cap food").assertDoesNotExist()
        compose.runOnIdle { state = DashboardCapsState(selected, DashboardCapsContent.Historical) }
        compose.onNodeWithText(text(R.string.dashboard_caps_historical)).assertExists()
        compose.runOnIdle { state = DashboardCapsState(selected, DashboardCapsContent.Error(AppError.Network)) }
        compose.onNodeWithText(text(R.string.error_network)).assertExists()
        compose.runOnIdle { state = state.copy(content = DashboardCapsContent.Ready(CapPreviews(null, emptyList()))) }
        compose.onNodeWithText(text(R.string.dashboard_caps_empty)).assertExists()
    }

    @Test fun recentEmptyAndErrorDoNotHideGoalsOrSummary() {
        var rows by mutableStateOf<UiState<List<FeedRow>>>(UiState.Loading)
        compose.setContent { SpendFlowTheme { TransactionFeedScreen(feed(rows), true, {}, {}, {}) } }
        compose.onNodeWithContentDescription("Income: LKR 1,000.00").assertExists()
        compose.runOnIdle { rows = UiState.Success(emptyList()) }
        compose.onNodeWithText(text(R.string.dashboard_recent_empty)).performScrollTo().assertIsDisplayed()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("dashboard_goal"))
        compose.onNodeWithTag("dashboard_goal").assertIsDisplayed()
        compose.runOnIdle { rows = UiState.Error(AppError.Network) }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(text(R.string.error_network)))
        compose.onNodeWithText(text(R.string.error_network)).assertIsDisplayed()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("dashboard_goal"))
        compose.onNodeWithTag("dashboard_goal").assertIsDisplayed()
    }

    @Test fun homeLinksUseExistingDestinationsAndBackPreservesTheHistoricalMonth() {
        val selected = month.minusMonths(1)
        lateinit var nav: TestNavHostController
        compose.setContent {
            val context = LocalContext.current
            nav = remember { TestNavHostController(context).apply {
                navigatorProvider.addNavigator(ComposeNavigator()); navigatorProvider.addNavigator(DialogNavigator())
            } }
            SpendFlowTheme { NavHost(nav, startDestination = AppGraph.MAIN.route) {
                appNavGraph { route, _ ->
                    if (route == Routes.HOME) TransactionFeedScreen(
                        feed(selected = selected, caps = DashboardCapsState(selected, DashboardCapsContent.Historical)), true,
                        onFilter = {}, onSeeAll = { nav.navigateToTab(Routes.TRANSACTIONS) },
                        onViewGoals = { nav.navigate(Routes.GOALS) }, onManageCaps = { nav.navigate(Routes.CATEGORIES) },
                    ) else Text(route)
                }
            } }
        }
        for ((label, route) in listOf(R.string.dashboard_manage_caps to Routes.CATEGORIES,
            R.string.ui_view_goals to Routes.GOALS, R.string.ui_see_all to Routes.TRANSACTIONS)) {
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(text(label)))
            compose.onNodeWithText(text(label)).performClick()
            compose.runOnIdle { assertEquals(route, nav.currentDestination?.route); nav.popBackStack() }
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(text(R.string.dashboard_caps_historical)))
            compose.onNodeWithText(text(R.string.dashboard_caps_historical)).assertIsDisplayed()
        }
    }
}
