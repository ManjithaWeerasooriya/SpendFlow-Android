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
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
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

/** Requires the debug app's Auth (9099) and Firestore (8080) emulators. Uses a new test user. */
@RunWith(AndroidJUnit4::class)
class AnalyticsRefinementFlowTest {
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
        eventually { main { assertEquals("Home", activity.findViewById<TextView>(R.id.screenTitle)?.text?.toString()) } }
        fun screenshot(name: String) {
            SystemClock.sleep(600)
            instrumentation.uiAutomation.executeShellCommand("screencap -p /sdcard/Download/$name").use { fd -> java.io.FileInputStream(fd.fileDescriptor).use { it.readBytes() } }
        }
        screenshot("refined-home.png")
        main { activity.findViewById<android.view.View>(R.id.analyticsFragment).performClick() }
        eventually { main {
            assertEquals("20,850.00", activity.findViewById<TextView>(R.id.breakdownAmount)?.text?.toString())
            assertEquals(3, activity.findViewById<android.view.ViewGroup>(R.id.categoryRows).childCount)
        } }
        screenshot("refined-analytics.png")
        onView(withId(R.id.dailyDetails)).perform(androidx.test.espresso.action.ViewActions.scrollTo())
        screenshot("refined-cashflow.png")
        onView(withId(R.id.dailyDetails)).perform(click())
        onView(withText(R.string.action_close)).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(click())
        eventually { main { assertTrue(activity.hasWindowFocus()) } }
        main { activity.findViewById<androidx.core.widget.NestedScrollView>(R.id.analyticsScroll).scrollTo(0, 0) }
        main { activity.findViewById<android.view.View>(R.id.breakdownIncome).performClick() }
        eventually { main { assertEquals("150,000.00", activity.findViewById<TextView>(R.id.breakdownAmount)?.text?.toString()) } }
        main { activity.findViewById<android.view.ViewGroup>(R.id.categoryRows).getChildAt(0).performClick() }
        onView(withText("Freelance")).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog())
            .check(androidx.test.espresso.assertion.ViewAssertions.matches(androidx.test.espresso.matcher.ViewMatchers.isDisplayed()))
        onView(withText(R.string.action_close)).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(click())
        eventually { main { assertTrue(activity.hasWindowFocus()) } }
        main { activity.findViewById<android.view.View>(R.id.previousMonth).performClick() }
        eventually { main { assertEquals("0.00", activity.findViewById<TextView>(R.id.breakdownAmount)?.text?.toString()) } }
        main { activity.findViewById<android.view.View>(R.id.nextMonth).performClick() }
        eventually { main { assertEquals("150,000.00", activity.findViewById<TextView>(R.id.breakdownAmount)?.text?.toString()) } }
        main { activity.findViewById<android.view.View>(R.id.breakdownExpenses).performClick() }
        main { AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES) }
        eventually { main {
            val resumed = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().firstOrNull()
            assertNotNull(resumed?.findViewById<TextView>(R.id.breakdownAmount))
        } }
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
        main {
            val resumed = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().first()
            resumed.findViewById<android.view.View>(R.id.filterIncome).performClick()
        }
        eventually { main {
            val resumed = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().first()
            val list = resumed.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.list)
            assertEquals(1, list.adapter?.itemCount)
        } }
        println("ANALYTICS_REFINEMENT_FLOW_PASSED")
    }
}
