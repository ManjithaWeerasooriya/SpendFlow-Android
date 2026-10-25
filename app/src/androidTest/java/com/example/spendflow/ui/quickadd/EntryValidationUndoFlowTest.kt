package com.example.spendflow.ui.quickadd

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
import org.junit.Test
import org.junit.runner.RunWith

/** Requires the debug app's Auth (9099) and Firestore (8080) emulators. Uses a new test user. */
@RunWith(AndroidJUnit4::class)
class EntryValidationUndoFlowTest {
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

        eventually { main { assertEquals("Home", activity.findViewById<TextView>(R.id.screenTitle)?.text?.toString()) } }
        main { activity.findViewById<android.view.View>(R.id.addButton).performClick() }
        fun sheet(): QuickAddBottomSheet {
            val host = activity.supportFragmentManager.findFragmentById(R.id.navHost) as NavHostFragment
            return host.childFragmentManager.fragments.filterIsInstance<QuickAddBottomSheet>().firstOrNull()
                ?: throw AssertionError("Quick Add is not open")
        }
        eventually { main {
            val form = sheet().view ?: throw AssertionError("Form is not ready")
            val chips = form.findViewById<android.view.ViewGroup>(R.id.categories)
            assertTrue(chips.children.filterIsInstance<CategoryChip>().any { it.label == "Groceries" })
        } }
        fun form() = sheet().requireView()
        fun save() = main { form().findViewById<android.view.View>(R.id.saveButton).performClick() }
        fun amount(value: String) = main { form().findViewById<EditText>(R.id.amountInput).setText(value) }
        fun screenshot(name: String) {
            SystemClock.sleep(600) // Capture the settled sheet/snackbar after Material animations.
            instrumentation.uiAutomation.executeShellCommand("screencap -p /sdcard/Download/$name").use { descriptor ->
                java.io.FileInputStream(descriptor.fileDescriptor).use { it.readBytes() }
            }
        }
        save()
        eventually { main {
            assertEquals(android.view.View.VISIBLE, form().findViewById<android.view.View>(R.id.amountError).visibility)
            assertEquals(android.view.View.VISIBLE, form().findViewById<android.view.View>(R.id.categoryError).visibility)
        } }
        screenshot("sf10-field-errors.png")
        for (invalid in listOf("0", "-1", "1.234", "1000000000")) {
            amount(invalid)
            save()
            eventually { main { assertEquals(android.view.View.VISIBLE, form().findViewById<android.view.View>(R.id.amountError).visibility) } }
        }
        assertTrue(profile.collection("transactions").get().await().isEmpty)
        main {
            form().findViewById<com.google.android.material.button.MaterialButtonToggleGroup>(R.id.typeToggle).check(R.id.typeIncome)
        }
        amount("500")
        main {
            val currency = form().findViewById<android.widget.Spinner>(R.id.currencyInput)
            currency.setSelection(com.example.spendflow.core.ui.CurrencyOptions.codes.indexOf("USD"))
        }
        instrumentation.waitForIdleSync()
        save()
        eventually { main {
            assertEquals(android.view.View.VISIBLE, form().findViewById<android.view.View>(R.id.rateValidationError).visibility)
            assertEquals(android.view.View.VISIBLE, form().findViewById<android.view.View>(R.id.categoryError).visibility)
        } }
        assertTrue(profile.collection("transactions").get().await().isEmpty)
        main {
            form().findViewById<com.google.android.material.button.MaterialButtonToggleGroup>(R.id.typeToggle).check(R.id.typeExpense)
        }
        amount("2450")
        main {
            form().findViewById<android.view.ViewGroup>(R.id.categories).children
                .filterIsInstance<CategoryChip>().first { it.label == "Groceries" }.performClick()
        }
        save()
        eventually { main {
            val host = activity.supportFragmentManager.findFragmentById(R.id.navHost) as NavHostFragment
            assertFalse(host.childFragmentManager.fragments.any { it is QuickAddBottomSheet })
            assertTrue(activity.findViewById<TextView>(R.id.totals).text.contains("Expense: LKR 2,450.00"))
            assertEquals("Saved \u00b7 Groceries LKR 2,450", activity.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)?.text?.toString())
            assertEquals("Undo", activity.findViewById<TextView>(com.google.android.material.R.id.snackbar_action)?.text?.toString())
        } }
        screenshot("sf10-saved-undo.png")
        val saved = profile.collection("transactions").get().await().documents.single()
        assertEquals(245000L, saved.getLong("amount"))
        main { activity.findViewById<android.view.View>(com.google.android.material.R.id.snackbar_action).performClick() }
        eventually { main {
            assertTrue(activity.findViewById<TextView>(R.id.totals).text.contains("Expense: LKR 0.00"))
            assertEquals("Entry removed", activity.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)?.text?.toString())
        } }
        assertTrue(profile.collection("transactions").get().await().isEmpty)
        println("SF10_VALIDATION_SAVE_UNDO_PASSED; TEST_UID=${user.uid}")
        main { activity.finish() }
    }
}
