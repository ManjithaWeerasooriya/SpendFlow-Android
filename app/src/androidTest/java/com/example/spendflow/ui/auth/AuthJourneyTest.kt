package com.example.spendflow.ui.auth

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.navigation.fragment.NavHostFragment
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.spendflow.BuildConfig
import com.example.spendflow.MainActivity
import com.example.spendflow.R
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the real auth UI and repositories against the debug Firebase emulators. */
@RunWith(AndroidJUnit4::class)
class AuthJourneyTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private fun eventually(check: () -> Unit) {
        val deadline = SystemClock.elapsedRealtime() + 20_000
        var failure: AssertionError? = null
        while (SystemClock.elapsedRealtime() < deadline) {
            try {
                check()
                return
            } catch (error: AssertionError) {
                failure = error
            }
            SystemClock.sleep(100)
        }
        throw AssertionError("Auth journey did not reach the expected state", failure)
    }

    @Test
    fun registrationOnboardingCategoriesLogoutResetAndLoginSurviveRecreation() = runBlocking {
        assertTrue("Requires debug Firebase emulators", BuildConfig.DEBUG)
        val context = instrumentation.targetContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN) {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.ACCESS_LOCAL_NETWORK)
        }
        val auth = FirebaseAuth.getInstance()
        auth.signOut()
        val email = "auth-journey-${System.currentTimeMillis()}@example.test"
        val password = "Journey2026!"
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            fun destination(expected: Int) = eventually {
                scenario.onActivity { activity ->
                    val host = activity.supportFragmentManager.findFragmentById(R.id.navHost) as NavHostFragment
                    assertEquals(expected, host.navController.currentDestination?.id)
                }
            }
            fun click(id: Int) {
                scenario.onActivity { activity ->
                    val view = activity.findViewById<View>(id)
                    assertNotNull("Missing click target $id", view)
                    assertTrue("Disabled click target $id", view.isEnabled)
                    view.performClick()
                }
                instrumentation.waitForIdleSync()
            }
            fun text(id: Int, value: String) {
                scenario.onActivity { it.findViewById<EditText>(id).setText(value) }
            }
            fun logout() {
                click(R.id.moreFragment)
                destination(R.id.moreFragment)
                click(R.id.logOutRow)
                // Dialog windows are separate from the Activity's content view.
                androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withId(android.R.id.button1))
                    .perform(androidx.test.espresso.action.ViewActions.click())
                destination(R.id.loginFragment)
                assertNull(auth.currentUser)
            }

            destination(R.id.loginFragment)
            click(R.id.submitButton)
            scenario.onActivity {
                assertNotNull(it.findViewById<TextInputLayout>(R.id.emailField).error)
                assertNotNull(it.findViewById<TextInputLayout>(R.id.passwordField).error)
            }
            click(R.id.createAccountLink)
            text(R.id.emailInput, email)
            text(R.id.passwordInput, password)
            text(R.id.confirmInput, "mismatch")
            click(R.id.submitButton)
            scenario.onActivity { assertNotNull(it.findViewById<TextInputLayout>(R.id.confirmField).error) }
            assertNull(auth.currentUser)
            text(R.id.confirmInput, password)
            click(R.id.submitButton)
            destination(R.id.onboardingFragment)
            scenario.onActivity { assertTrue(it.findViewById<TextView>(R.id.currencyName).text.contains("LKR")) }
            click(R.id.continueButton)
            destination(R.id.homeFragment)
            val uid = requireNotNull(auth.currentUser).uid
            val profile = FirebaseFirestore.getInstance().collection("users").document(uid)
            withTimeout(20_000) { FirebaseFirestore.getInstance().waitForPendingWrites().await() }
            assertEquals("LKR", withTimeout(20_000) { profile.get().await() }.getString("baseCurrency"))
            scenario.recreate()
            destination(R.id.homeFragment)
            click(R.id.moreFragment)
            click(R.id.budgetCategoriesRow)
            destination(R.id.categoriesFragment)
            text(R.id.nameInput, "Journey groceries")
            click(R.id.addButton)
            eventually {
                scenario.onActivity {
                    val list = it.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.list)
                    assertTrue((list.adapter?.itemCount ?: 0) > 0)
                }
            }
            withTimeout(20_000) { FirebaseFirestore.getInstance().waitForPendingWrites().await() }
            assertTrue(withTimeout(20_000) { profile.collection("categories").get().await() }.documents.any { it.getString("name") == "Journey groceries" })
            scenario.onActivity {
                val host = it.supportFragmentManager.findFragmentById(R.id.navHost) as NavHostFragment
                assertTrue(host.navController.navigateUp())
            }
            destination(R.id.moreFragment)
            click(R.id.settingsRow)
            destination(R.id.settingsFragment)
            scenario.onActivity {
                val host = it.supportFragmentManager.findFragmentById(R.id.navHost) as NavHostFragment
                assertTrue(host.navController.navigateUp())
            }
            destination(R.id.moreFragment)
            logout()
            text(R.id.emailInput, email)
            click(R.id.forgotPassword)
            eventually {
                scenario.onActivity {
                    assertEquals(it.getString(R.string.auth_reset_sent, email), it.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)?.text?.toString())
                }
            }
            text(R.id.passwordInput, "WrongPassword2026!")
            click(R.id.submitButton)
            eventually { scenario.onActivity { assertNotNull(it.findViewById<TextInputLayout>(R.id.passwordField).error) } }
            assertNull(auth.currentUser)
            text(R.id.passwordInput, password)
            click(R.id.submitButton)
            destination(R.id.homeFragment)
            assertEquals(uid, auth.currentUser?.uid)
            scenario.recreate()
            destination(R.id.homeFragment)
            logout()
        }
    }
}
