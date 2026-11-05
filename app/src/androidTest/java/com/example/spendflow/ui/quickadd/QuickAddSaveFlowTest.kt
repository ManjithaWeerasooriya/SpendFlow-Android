package com.example.spendflow.ui.quickadd

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.spendflow.BuildConfig
import com.example.spendflow.MainActivity
import com.example.spendflow.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import com.example.spendflow.ui.transactions.FeedTags
import org.junit.Rule
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.onNodeWithContentDescription
import com.example.spendflow.ui.main.Routes

/** Requires the debug app's Auth (9099) and Firestore (8080) emulators. Uses a new test user. */
@RunWith(AndroidJUnit4::class)
class QuickAddSaveFlowTest {
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
    fun amountAndCategorySaveUpdatesTotalsAndHistory() = runBlocking {
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
        val email = "sf08-saveflow-${System.currentTimeMillis()}@example.test"
        val user = auth.createUserWithEmailAndPassword(email, "Sf08Smoke2026").await().user!!
        val db = FirebaseFirestore.getInstance()
        val profile = db.collection("users").document(user.uid)
        val timestamps = mapOf("createdAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp())
        profile.set(mapOf("baseCurrency" to "LKR", "displayName" to null, "email" to email) + timestamps).await()
        profile.collection("categories").document("sf08-food").set(mapOf(
            "name" to "SF08 Food", "type" to "EXPENSE", "sortOrder" to 0L, "isArchived" to false,
        ) + timestamps).await()

        compose.waitUntil(15_000) { compose.onAllNodes(hasTestTag(FeedTags.TITLE) and hasText("Home")).fetchSemanticsNodes().isNotEmpty() }
        val started = SystemClock.elapsedRealtime()
        compose.onNodeWithContentDescription(context.getString(R.string.nav_add_transaction)).performClick()
        fun waitFor(matcher: SemanticsMatcher) = compose.waitUntil(15_000) { compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
        val foodChip = hasText("SF08 Food") and hasClickAction()
        waitFor(foodChip)
        compose.onNodeWithTag(QuickAddTags.DATE).assert(hasText("Date: Now"))
        compose.onNodeWithTag(QuickAddTags.DETAILS).assertDoesNotExist()
        compose.onNodeWithTag(QuickAddTags.AMOUNT).performTextReplacement("25.50")
        compose.onNode(foodChip).performScrollTo().performClick()
        compose.onNodeWithTag(QuickAddTags.SAVE).performScrollTo().performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription("Expenses: LKR 25.50").fetchSemanticsNodes().isNotEmpty() }
        val elapsed = SystemClock.elapsedRealtime() - started
        assertTrue("Save flow took ${elapsed}ms", elapsed <= 10_000)

        val transactions = profile.collection("transactions").get().await().documents
        assertEquals(1, transactions.size)
        val saved = transactions.single()
        assertEquals(2550L, saved.getLong("amount"))
        assertEquals("EXPENSE", saved.getString("type"))
        assertEquals("sf08-food", saved.getString("categoryId"))
        assertNull(saved.get("paymentMethod"))
        assertNull(saved.get("note"))
        val age = System.currentTimeMillis() - saved.getTimestamp("dateTime")!!.toDate().time
        assertTrue("Default date should be now", age in 0..30_000)
        compose.onNodeWithTag("tab_${Routes.TRANSACTIONS}").performClick()
        compose.waitUntil(15_000) {
            compose.onAllNodes(hasTestTag(FeedTags.ROW) and hasText("SF08 Food", substring = true) and hasText("LKR 25.50", substring = true))
                .fetchSemanticsNodes().isNotEmpty()
        }
        println("SF08_SAVE_FLOW_MS=$elapsed; TEST_UID=${user.uid}; amount=2550; optional details=null")
    }
}
