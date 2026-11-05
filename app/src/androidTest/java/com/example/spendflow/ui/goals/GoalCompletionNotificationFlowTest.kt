package com.example.spendflow.ui.goals

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
import android.app.NotificationManager
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.matcher.ViewMatchers.withId
import com.example.spendflow.core.notifications.AndroidGoalNotifications
import org.junit.Test
import org.junit.runner.RunWith

/** Requires the debug app's Auth (9099) and Firestore (8080) emulators. Uses a new test user. */
@RunWith(AndroidJUnit4::class)
class GoalCompletionNotificationFlowTest {
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
    fun creatingAndCompletingGoalPostsOneNotificationAndOpensGoals() = runBlocking {
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
        val email = "goal-notification-${System.currentTimeMillis()}@example.test"
        val user = auth.createUserWithEmailAndPassword(email, "GoalSmoke2026").await().user!!
        val db = FirebaseFirestore.getInstance()
        val profile = db.collection("users").document(user.uid)
        val timestamps = mapOf("createdAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp())
        profile.set(mapOf("baseCurrency" to "LKR", "displayName" to null, "email" to email) + timestamps).await()
        if (Build.VERSION.SDK_INT >= 33) {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        eventually { main { assertEquals("Home", activity.findViewById<TextView>(R.id.screenTitle)?.text?.toString()) } }
        main { activity.findViewById<android.view.View>(R.id.moreFragment).performClick() }
        eventually { main { assertNotNull(activity.findViewById<android.view.View>(R.id.goalsRow)) } }
        main { activity.findViewById<android.view.View>(R.id.goalsRow).performClick() }
        eventually { main { assertNotNull(activity.findViewById<android.view.View>(R.id.addGoal)) } }
        onView(withId(R.id.addGoal)).perform(click())
        onView(withId(R.id.nameInput)).perform(replaceText("Laptop"))
        onView(withId(R.id.targetInput)).perform(replaceText("100"))
        onView(withId(R.id.savedInput)).perform(replaceText("0"), closeSoftKeyboard())
        onView(withId(android.R.id.button1)).perform(click())
        var goalId: String? = null
        val deadline = SystemClock.elapsedRealtime() + 15000
        while (SystemClock.elapsedRealtime() < deadline && goalId == null) {
            goalId = profile.collection("goals").get().await().documents.firstOrNull()?.id
            if (goalId == null) SystemClock.sleep(100)
        }
        val goal = profile.collection("goals").document(requireNotNull(goalId))
        assertEquals(10000L, goal.get().await().getLong("targetMinor"))
        assertTrue(manager.activeNotifications.none { it.tag == "goal:${user.uid}:$goalId" })
        goal.update(mapOf("savedMinor" to 10000L, "updatedAt" to FieldValue.serverTimestamp())).await()
        eventually {
            val posted = manager.activeNotifications.firstOrNull { it.tag == "goal:${user.uid}:$goalId" }
            assertNotNull(posted)
            assertEquals("Savings goal completed!", posted!!.notification.extras.getCharSequence("android.title").toString())
            assertTrue(posted.notification.extras.getCharSequence("android.text").toString().contains("Laptop"))
        }
        val notification = manager.activeNotifications.first { it.tag == "goal:${user.uid}:$goalId" }
        goal.update(mapOf("savedMinor" to 11000L, "updatedAt" to FieldValue.serverTimestamp())).await()
        SystemClock.sleep(800)
        assertEquals(notification.postTime, manager.activeNotifications.first { it.tag == notification.tag }.postTime)
        main {
            val host = activity.supportFragmentManager.findFragmentById(R.id.navHost) as NavHostFragment
            host.navController.popBackStack(R.id.moreFragment, false)
        }
        notification.notification.contentIntent.send()
        eventually { main {
            val host = activity.supportFragmentManager.findFragmentById(R.id.navHost) as NavHostFragment
            assertEquals(R.id.goalsFragment, host.navController.currentDestination?.id)
        } }
        SystemClock.sleep(600)
        instrumentation.uiAutomation.executeShellCommand("screencap -p /sdcard/Download/goal-completed.png").use { descriptor ->
            java.io.FileInputStream(descriptor.fileDescriptor).use { it.readBytes() }
        }
        manager.cancel(notification.tag, notification.id)
        println("GOAL_COMPLETION_NOTIFICATION_FLOW_PASSED; TEST_UID=${user.uid}")
        main { activity.finish() }
    }
}
