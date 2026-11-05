package com.example.spendflow.ui.analytics

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.widget.EditText
import android.widget.TextView
import androidx.core.view.children
import androidx.navigation.fragment.NavHostFragment
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.spendflow.BuildConfig
import com.example.spendflow.MainActivity
import com.example.spendflow.R
import com.example.spendflow.core.ui.components.CategoryChip
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Assert.*
import androidx.test.espresso.Espresso.pressBack
import com.example.spendflow.domain.model.*
import com.example.spendflow.data.remote.mapper.toFirestore
import com.example.spendflow.data.remote.mapper.withCreateTimestamps
import java.math.BigDecimal
import java.time.YearMonth
import java.time.ZoneId
import androidx.appcompat.app.AppCompatDelegate
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import org.junit.Test
import org.junit.runner.RunWith
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.spendflow.ui.transactions.FeedTags
import org.junit.Rule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performScrollTo

/** Requires the debug app's Auth (9099) and Firestore (8080) emulators. Uses a new test user. */
@RunWith(AndroidJUnit4::class)
class AnalyticsRefinementFlowTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private fun <T> main(block: () -> T): T {
        var result: Result<T>? = null
        instrumentation.runOnMainSync { result = runCatching(block) }
        return result!!.getOrThrow()
    }

    private fun eventually(block: () -> Unit) {
        val deadline = SystemClock.elapsedRealtime() + 15_000
        var failure: Throwable? = null
        while (SystemClock.elapsedRealtime() < deadline) {
            try { block(); return } catch (e: AssertionError) { failure = e }
            SystemClock.sleep(100)
        }
        throw AssertionError("UI did not reach the expected state", failure)
    }

    @Test
    fun monthlyAnalyticsFiltersAndDetailsUseStoredBaseAmounts() = runBlocking {
        assertTrue("Only run against the debug Firebase emulators", BuildConfig.DEBUG)
        val context = instrumentation.targetContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN) {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.ACCESS_LOCAL_NETWORK)
        }
        val activity = instrumentation.startActivitySync(Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }) as MainActivity
        instrumentation.waitForIdleSync()

        val auth = FirebaseAuth.getInstance()
        val email = "analytics-preview-${System.currentTimeMillis()}@example.test"
        val user = auth.createUserWithEmailAndPassword(email, "Analytics2026").await().user!!
        val db = FirebaseFirestore.getInstance()
        val profile = db.collection("users").document(user.uid)
        val timestamps = mapOf("createdAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp())
        profile.set(mapOf("baseCurrency" to "LKR", "displayName" to null, "email" to email) + timestamps).await()
        val month = YearMonth.now()
        val zone = ZoneId.systemDefault()
        val categories = listOf(
            Category("food", "Food", CategoryType.EXPENSE), Category("shopping", "Shopping", CategoryType.EXPENSE),
            Category("transport", "Transport", CategoryType.EXPENSE), Category("freelance", "Freelance", CategoryType.INCOME)
        )
        for (category in categories) profile.collection("categories").document(category.id).set(category.toFirestore().withCreateTimestamps()).await()
        fun tx(id: String, category: String, minor: Long, day: Int, income: Boolean = false): Transaction =
            Transaction(id, if(income) TransactionType.INCOME else TransactionType.EXPENSE,
                Money(if(income) 50000 else minor, if(income) "USD" else "LKR"), if(income) BigDecimal("300") else BigDecimal.ONE,
                Money(minor, "LKR"), month.atDay(day).atStartOfDay(zone).toInstant(), category, null,
                note = if(id == "groceries") "Groceries" else null)
        val entries = listOf(tx("groceries", "food", 620000, 3), tx("shop", "shopping", 1280000, 8), tx("bus", "transport", 185000, 12), tx("freelance", "freelance", 15000000, 2, true))
        for (entry in entries) profile.collection("transactions").document(entry.id).set(entry.toFirestore().withCreateTimestamps()).await()
        profile.collection("goals").document("laptop").set(SavingsGoal("laptop", "MacBook", Money(45000000,"LKR"), Money(21600000,"LKR")).toFirestore().withCreateTimestamps()).await()
        compose.waitUntil(15_000) { compose.onAllNodes(hasTestTag(FeedTags.TITLE) and hasText("Home")).fetchSemanticsNodes().isNotEmpty() }
        fun screenshot(name: String) {
            SystemClock.sleep(600)
            instrumentation.uiAutomation.executeShellCommand("screencap -p /sdcard/Download/$name").use { fd -> java.io.FileInputStream(fd.fileDescriptor).use { it.readBytes() } }
        }
        screenshot("refined-home.png")
        main { activity.findViewById<android.view.View>(R.id.analyticsFragment).performClick() }
        val close = context.getString(R.string.action_close)
        fun breakdownAmountIs(value: String) = compose.waitUntil(15_000) {
            compose.onAllNodes(hasTestTag(AnalyticsTags.BREAKDOWN_AMOUNT) and hasText(value)).fetchSemanticsNodes().isNotEmpty()
        }
        fun waitForTag(tag: String) = compose.waitUntil(15_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
        breakdownAmountIs("20,850.00")
        compose.onAllNodesWithTag(AnalyticsTags.CATEGORY_ROW).assertCountEquals(3)
        screenshot("refined-analytics.png")
        compose.onNodeWithTag(AnalyticsTags.DAILY_DETAILS).performScrollTo()
        screenshot("refined-cashflow.png")
        compose.onNodeWithTag(AnalyticsTags.DAILY_DETAILS).performClick()
        compose.onNode(hasText(close) and hasAnyAncestor(isDialog())).performClick()
        compose.onNode(isDialog()).assertDoesNotExist()
        compose.onNodeWithTag(AnalyticsTags.BREAKDOWN_INCOME).performScrollTo().performClick()
        breakdownAmountIs("150,000.00")
        compose.onAllNodesWithTag(AnalyticsTags.CATEGORY_ROW)[0].performScrollTo().performClick()
        compose.onNode(hasText("Freelance") and hasAnyAncestor(isDialog())).assertIsDisplayed()
        compose.onNode(hasText(close) and hasAnyAncestor(isDialog())).performClick()
        compose.onNode(isDialog()).assertDoesNotExist()
        compose.onNodeWithContentDescription(context.getString(R.string.analytics_previous_month)).performScrollTo().performClick()
        waitForTag(AnalyticsTags.EMPTY)
        compose.onNodeWithTag(AnalyticsTags.REPORT).assertDoesNotExist()
        compose.onNode(hasText(context.getString(R.string.nav_add_transaction)) and hasClickAction()).performClick()
        eventually { main {
            val host = activity.supportFragmentManager.findFragmentById(R.id.navHost) as NavHostFragment
            assertEquals(R.id.quickAddSheet, host.navController.currentDestination?.id)
        } }
        pressBack()
        val previousExpense = tx("previous-expense", "food", 5_000, 4).copy(
            dateTime = month.minusMonths(1).atDay(4).atStartOfDay(zone).toInstant(),
        )
        val previousExpenseDoc = profile.collection("transactions").document(previousExpense.id)
        previousExpenseDoc.set(previousExpense.toFirestore().withCreateTimestamps()).await()
        waitForTag(AnalyticsTags.INCOME_REQUIRED)
        compose.onNodeWithTag(AnalyticsTags.REPORT).assertExists()
        // Without income the ratio bar is replaced by its "unavailable" announcement, and so is the savings rate
        compose.onNodeWithContentDescription(context.getString(R.string.summary_expense_to_income_unavailable)).assertExists()
        compose.onNodeWithContentDescription(context.getString(R.string.summary_savings_rate_unavailable)).assertExists()
        previousExpenseDoc.delete().await()
        waitForTag(AnalyticsTags.EMPTY)
        compose.onNodeWithContentDescription(context.getString(R.string.analytics_next_month)).performClick()
        breakdownAmountIs("150,000.00")
        compose.onNodeWithTag(AnalyticsTags.BREAKDOWN_EXPENSES).performScrollTo().performClick()
        main { AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES) }
        eventually { main {
            assertNotNull(ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().firstOrNull())
        } }
        breakdownAmountIs("20,850.00")
        screenshot("refined-analytics-dark.png")
        main {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
        eventually { main {
            assertNotNull(ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().firstOrNull())
        } }
        main {
            val resumed = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().first()
            resumed.findViewById<android.view.View>(R.id.transactionsFragment).performClick()
        }
        screenshot("refined-transactions.png")
        compose.waitUntil(15_000) { compose.onAllNodesWithTag(FeedTags.filter("INCOME")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag(FeedTags.filter("INCOME")).performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithTag(FeedTags.ROW).fetchSemanticsNodes().size == 1 }
        println("ANALYTICS_REFINEMENT_FLOW_PASSED")
    }
}
