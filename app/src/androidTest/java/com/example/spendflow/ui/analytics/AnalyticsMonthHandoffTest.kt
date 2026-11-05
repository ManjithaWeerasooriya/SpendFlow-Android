package com.example.spendflow.ui.analytics

import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.spendflow.R
import com.example.spendflow.core.common.AppError
import com.example.spendflow.core.common.AppResult
import com.example.spendflow.core.ui.UiState
import com.example.spendflow.data.repository.*
import com.example.spendflow.domain.model.*
import com.example.spendflow.domain.usecase.GetMonthlySummary
import com.example.spendflow.ui.main.*
import com.example.spendflow.ui.transactions.*
import com.example.spendflow.ui.theme.SpendFlowTheme
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class AnalyticsMonthHandoffTest {
    @get:Rule val compose = createComposeRule()
    private val restoration = StateRestorationTester(compose)
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val homeMonth = YearMonth.of(2024, 10)
    private val priorAnalyticsMonth = YearMonth.of(2024, 1)
    private val state = SavedStateHandle(mapOf("analytics_month" to priorAnalyticsMonth.toString()))
    private val transactions = Transactions()
    private lateinit var nav: NavHostController
    private lateinit var model: AnalyticsViewModel
    private var renderAnalytics by mutableStateOf(true)

    private fun show() {
        restoration.setContent {
            nav = rememberNavController()
            model = remember { AnalyticsViewModel(GetMonthlySummary(transactions, Profile), Categories, state) }
            SpendFlowTheme {
                NavHost(nav, startDestination = AppGraph.MAIN.route) {
                    appNavGraph { route, entry ->
                        when (route) {
                            Routes.HOME -> TransactionFeedScreen(
                                FeedUiState(UiState.Success(emptyList()), null, UiState.Success(null), "ALL", homeMonth,
                                    DashboardUiState(homeMonth, true, UiState.Success(DashboardSummary(
                                        MonthlySummary(homeMonth, Money.zero("LKR"), Money.zero("LKR")), false,
                                    ))), DashboardCapsState(homeMonth, DashboardCapsContent.Historical),
                                    dashboardExpenses(DashboardUiState(homeMonth, true, UiState.Success(DashboardSummary(
                                        MonthlySummary(homeMonth, Money.zero("LKR"), Money.zero("LKR")), false,
                                    ))), AppResult.Success(emptyList()))),
                                true, {}, {}, {}, onViewAnalytics = nav::openAnalytics,
                            )
                            Routes.ANALYTICS -> if (renderAnalytics) AnalyticsRoute(model, {}, entry.savedStateHandle) else Text("Pending Analytics")
                            else -> Text(route)
                        }
                    }
                }
            }
        }
    }
    private fun openFromHome() = compose.onNodeWithText(context.getString(R.string.dashboard_view_analytics)).performScrollTo().performClick()

    @Test fun chartActionOverridesRestoredAnalyticsOnceAndBackPreservesHomeMonth() {
        show()
        openFromHome()
        compose.onNodeWithText("October 2024").assertExists()
        compose.runOnIdle {
            assertEquals(homeMonth.toString(), model.selectedMonth.value)
            assertNull(nav.getBackStackEntry(Routes.ANALYTICS).savedStateHandle.get<String>(ANALYTICS_MONTH_REQUEST))
            model.moveMonth(-1)
        }
        compose.onNodeWithText("September 2024").assertExists()
        compose.runOnIdle { nav.popBackStack() }
        compose.onNodeWithText("October 2024").assertExists()
        // Ordinary tab navigation must not replay the chart's consumed request.
        compose.runOnIdle { nav.navigateToTab(Routes.ANALYTICS) }
        compose.onNodeWithText("September 2024").assertExists()
    }

    @Test fun pendingRequestSurvivesBackStackRestorationAndConsumedSelectionSurvivesRecreation() {
        renderAnalytics = false
        show()
        openFromHome()
        compose.runOnIdle {
            assertEquals(homeMonth.toString(), nav.getBackStackEntry(Routes.ANALYTICS).savedStateHandle.get<String>(ANALYTICS_MONTH_REQUEST))
        }
        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle {
            assertEquals(Routes.ANALYTICS, nav.currentDestination?.route)
            assertEquals(homeMonth.toString(), nav.getBackStackEntry(Routes.ANALYTICS).savedStateHandle.get<String>(ANALYTICS_MONTH_REQUEST))
            renderAnalytics = true
        }
        compose.onNodeWithText("October 2024").assertExists()
        compose.runOnIdle { model.moveMonth(-1) }
        compose.onNodeWithText("September 2024").assertExists()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("September 2024").assertExists()
    }

    @Test fun handoffCannotDisplayThePriorMonthsTotalsWhileNewQueryIsPendingOrFails() {
        val oldMoney = Money(90000, "LKR")
        transactions.rows.value = listOf(Transaction("old", TransactionType.INCOME, oldMoney, BigDecimal.ONE, oldMoney,
            priorAnalyticsMonth.atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant(), "pay", null))
        show()
        compose.runOnIdle { nav.navigateToTab(Routes.ANALYTICS) }
        compose.onNodeWithContentDescription("Income: LKR 900.00").assertExists()
        compose.runOnIdle {
            nav.popBackStack()
            transactions.pausedUntil.value = homeMonth.plusMonths(1).atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant()
        }
        openFromHome()
        compose.onNodeWithText("October 2024").assertExists()
        compose.onNodeWithText(context.getString(R.string.analytics_loading)).assertExists()
        compose.onNodeWithContentDescription("Income: LKR 900.00").assertDoesNotExist()
        compose.runOnIdle { transactions.failure.value = AppError.Network; transactions.pausedUntil.value = null }
        compose.onNodeWithText(context.getString(R.string.error_network)).assertExists()
        compose.onNodeWithText("October 2024").assertExists()
        compose.onNodeWithContentDescription("Income: LKR 900.00").assertDoesNotExist()
    }

    private class Transactions : TransactionRepository {
        val rows = MutableStateFlow<List<Transaction>>(emptyList())
        val pausedUntil = MutableStateFlow<Instant?>(null)
        val failure = MutableStateFlow<AppError?>(null)
        override fun observeTransactions(from: Instant, until: Instant) = combine(rows, pausedUntil, failure) { items, paused, error ->
            when { paused == until -> null; error != null -> AppResult.Error(error)
                else -> AppResult.Success(items.filter { it.dateTime >= from && it.dateTime < until }) }
        }.filterNotNull()
        override fun observeRecent(limit: Int) = flowOf(AppResult.Success(emptyList<Transaction>()))
        override fun newId() = error("Unused")
        override suspend fun get(id: String) = error("Unused")
        override suspend fun create(item: Transaction) = error("Unused")
        override suspend fun update(item: Transaction) = error("Unused")
        override suspend fun delete(id: String) = error("Unused")
    }
    private object Categories : CategoryRepository {
        override fun observeCategories(type: CategoryType?, includeArchived: Boolean) = flowOf(AppResult.Success(emptyList<Category>()))
        override fun newId() = error("Unused")
        override suspend fun get(id: String) = error("Unused")
        override suspend fun create(item: Category) = error("Unused")
        override suspend fun update(item: Category) = error("Unused")
        override suspend fun delete(id: String) = error("Unused")
    }
    private object Profile : UserRepository {
        override fun observeProfile() = flowOf(AppResult.Success(User("user", null, null, "LKR")))
        override suspend fun createProfile(user: User) = error("Unused")
        override suspend fun createProfileIfMissing(user: User) = error("Unused")
        override suspend fun updateProfile(user: User) = error("Unused")
    }
}
