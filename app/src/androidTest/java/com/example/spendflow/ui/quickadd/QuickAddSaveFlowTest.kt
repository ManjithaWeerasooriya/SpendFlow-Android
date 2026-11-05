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
class QuickAddSaveFlowTest {
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

        eventually { main { assertEquals("Home", activity.findViewById<TextView>(R.id.screenTitle)?.text?.toString()) } }
        val started = SystemClock.elapsedRealtime()
        main { activity.findViewById<android.view.View>(R.id.addButton).performClick() }
        fun sheet(): QuickAddBottomSheet {
            val host = activity.supportFragmentManager.findFragmentById(R.id.navHost) as NavHostFragment
            return host.childFragmentManager.fragments.filterIsInstance<QuickAddBottomSheet>().firstOrNull()
                ?: throw AssertionError("Quick Add is not open")
        }
        eventually { main {
            val form = sheet().view ?: throw AssertionError("Form is not ready")
            val chips = form.findViewById<android.view.ViewGroup>(R.id.categories)
            assertTrue(chips.children.filterIsInstance<CategoryChip>().any { it.label == "SF08 Food" })
        } }
        main {
            val form = sheet().requireView()
            assertEquals("Date: Now", form.findViewById<TextView>(R.id.dateButton).text.toString())
            assertEquals(android.view.View.GONE, form.findViewById<android.view.View>(R.id.details).visibility)
            form.findViewById<EditText>(R.id.amountInput).setText("25.50")
            form.findViewById<android.view.ViewGroup>(R.id.categories).children
                .filterIsInstance<CategoryChip>().first { it.label == "SF08 Food" }.performClick()
            form.findViewById<android.view.View>(R.id.saveButton).performClick()
        }
        eventually { main {
            assertTrue(activity.findViewById<TextView>(R.id.totals).text.contains("Expense: LKR 25.50"))
        } }
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
        main { activity.findViewById<android.view.View>(R.id.transactionsFragment).performClick() }
        eventually { main {
            val list = activity.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.list)
            assertTrue(list.children.any {
                it.findViewById<TextView>(R.id.transactionTitle)?.text?.contains("SF08 Food") == true &&
                    it.findViewById<TextView>(R.id.transactionAmount)?.text?.contains("LKR 25.50") == true
            })
        } }
        println("SF08_SAVE_FLOW_MS=$elapsed; TEST_UID=${user.uid}; amount=2550; optional details=null")
    }
}
