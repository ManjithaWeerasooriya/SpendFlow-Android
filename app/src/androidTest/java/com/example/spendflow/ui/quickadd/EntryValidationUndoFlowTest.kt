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
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.onNodeWithContentDescription

/** Requires the debug app's Auth (9099) and Firestore (8080) emulators. Uses a new test user. */
@RunWith(AndroidJUnit4::class)
class EntryValidationUndoFlowTest {
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
    fun invalidFieldsBlockSaveAndUndoRestoresTotals() = runBlocking {
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
        val email = "sf10-entry-${System.currentTimeMillis()}@example.test"
        val user = auth.createUserWithEmailAndPassword(email, "Sf10Smoke2026").await().user!!
        val db = FirebaseFirestore.getInstance()
        val profile = db.collection("users").document(user.uid)
        val timestamps = mapOf("createdAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp())
        profile.set(mapOf("baseCurrency" to "LKR", "displayName" to null, "email" to email) + timestamps).await()
        profile.collection("categories").document("sf10-groceries").set(mapOf(
            "name" to "Groceries", "type" to "EXPENSE", "sortOrder" to 0L, "isArchived" to false,
        ) + timestamps).await()

        compose.waitUntil(15_000) { compose.onAllNodes(hasTestTag(FeedTags.TITLE) and hasText("Home")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription(context.getString(R.string.nav_add_transaction)).performClick()
        fun waitFor(matcher: SemanticsMatcher) = compose.waitUntil(15_000) { compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
        val groceriesChip = hasText("Groceries") and hasClickAction()
        waitFor(groceriesChip)
        fun save() = compose.onNodeWithTag(QuickAddTags.SAVE).performScrollTo().performClick()
        fun amount(value: String) = compose.onNodeWithTag(QuickAddTags.AMOUNT).performTextReplacement(value)
        fun screenshot(name: String) {
            SystemClock.sleep(600) // Capture the settled sheet/snackbar after Material animations.
            instrumentation.uiAutomation.executeShellCommand("screencap -p /sdcard/Download/$name").use { descriptor ->
                java.io.FileInputStream(descriptor.fileDescriptor).use { it.readBytes() }
            }
        }
        save()
        waitFor(hasTestTag(QuickAddTags.AMOUNT_ERROR))
        waitFor(hasTestTag(QuickAddTags.CATEGORY_ERROR))
        screenshot("sf10-field-errors.png")
        for (invalid in listOf("0", "-1", "1.234", "1000000000")) {
            amount(invalid)
            save()
            waitFor(hasTestTag(QuickAddTags.AMOUNT_ERROR))
        }
        assertTrue(profile.collection("transactions").get().await().isEmpty)
        compose.onNodeWithTag(QuickAddTags.TYPE_INCOME).performClick()
        amount("500")
        compose.onNodeWithTag(QuickAddTags.CURRENCY).performScrollTo().performClick()
        val usd = com.example.spendflow.core.ui.CurrencyOptions.labels[com.example.spendflow.core.ui.CurrencyOptions.codes.indexOf("USD")]
        compose.onNodeWithText(usd).performClick()
        save()
        waitFor(hasTestTag(QuickAddTags.RATE_ERROR))
        waitFor(hasTestTag(QuickAddTags.CATEGORY_ERROR))
        assertTrue(profile.collection("transactions").get().await().isEmpty)
        compose.onNodeWithTag(QuickAddTags.TYPE_EXPENSE).performScrollTo().performClick()
        amount("2450")
        compose.onNode(groceriesChip).performScrollTo().performClick()
        save()
        compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription("Expenses: LKR 2,450.00").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag(QuickAddTags.SAVE).assertDoesNotExist()
        waitFor(hasText("Saved \u00b7 Groceries LKR 2,450"))
        val undo = hasText(context.getString(R.string.entry_undo)) and hasClickAction()
        waitFor(undo)
        screenshot("sf10-saved-undo.png")
        val saved = profile.collection("transactions").get().await().documents.single()
        assertEquals(245000L, saved.getLong("amount"))
        compose.onNode(undo).performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription("Expenses: LKR 0.00").fetchSemanticsNodes().isNotEmpty() }
        waitFor(hasText(context.getString(R.string.entry_undone)))
        assertTrue(profile.collection("transactions").get().await().isEmpty)
        println("SF10_VALIDATION_SAVE_UNDO_PASSED; TEST_UID=${user.uid}")
        main { activity.finish() }
    }
}
