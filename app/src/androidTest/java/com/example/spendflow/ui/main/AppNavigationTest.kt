package com.example.spendflow.ui.main

import androidx.navigation.testing.TestNavHostController
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.spendflow.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** Back-stack rules for the auth, onboarding and main graphs (AC1, AC4, AC5), checked against the real nav graphs. */
@RunWith(AndroidJUnit4::class)
class AppNavigationTest {

    private fun navController(start: AppGraph) =
        TestNavHostController(InstrumentationRegistry.getInstrumentation().targetContext).apply { setRootGraph(start) }

    private val TestNavHostController.currentId get() = currentDestination?.id

    // NavController must be driven from the main thread; rethrow so assertion failures fail the test
    private fun onMain(block: () -> Unit) {
        var failure: Throwable? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            try {
                block()
            } catch (t: Throwable) {
                failure = t
            }
        }
        failure?.let { throw it }
    }

    @Test
    fun loggedOutStartsOnLoginAndLoggedInOnHome() = onMain {
        assertEquals(R.id.loginFragment, navController(AppGraph.AUTH).currentId)
        assertEquals(R.id.homeFragment, navController(AppGraph.MAIN).currentId)
    }

    @Test
    fun signingInReplacesLoginSoBackLeavesTheApp() = onMain {
        val nav = navController(AppGraph.AUTH)
        nav.showGraph(AppGraph.MAIN)
        assertEquals(R.id.homeFragment, nav.currentId)
        assertNull(nav.previousBackStackEntry)
    }

    @Test
    fun registeringOpensOnboardingAndFinishingItReplacesItWithHome() = onMain {
        val nav = navController(AppGraph.AUTH)
        nav.showGraph(AppGraph.ONBOARDING)
        assertEquals(R.id.onboardingFragment, nav.currentId)
        assertNull(nav.previousBackStackEntry)
        assertFalse(R.id.onboardingFragment in topLevelDestinations)

        nav.showGraph(AppGraph.MAIN)
        assertEquals(R.id.homeFragment, nav.currentId)
        assertNull(nav.previousBackStackEntry)
    }

    @Test
    fun signingOutClearsTheMainGraph() = onMain {
        val nav = navController(AppGraph.MAIN)
        nav.navigate(R.id.moreFragment)
        nav.navigate(R.id.action_more_to_goals)
        nav.showGraph(AppGraph.AUTH)
        assertEquals(R.id.loginFragment, nav.currentId)
        assertNull(nav.previousBackStackEntry)
    }

    @Test
    fun showingTheCurrentGraphKeepsTheBackStack() = onMain {
        val nav = navController(AppGraph.MAIN)
        nav.navigate(R.id.moreFragment)
        nav.showGraph(AppGraph.MAIN)
        assertEquals(R.id.moreFragment, nav.currentId)
    }

    @Test
    fun moreLinksAreOneTapAwayAndBackReturnsToMore() = onMain {
        val nav = navController(AppGraph.MAIN)
        nav.navigate(R.id.moreFragment)
        listOf(
            R.id.action_more_to_categories to R.id.categoriesFragment,
            R.id.action_more_to_goals to R.id.goalsFragment,
            R.id.action_more_to_settings to R.id.settingsFragment,
        ).forEach { (action, destination) ->
            nav.navigate(action)
            assertEquals(destination, nav.currentId)
            assertFalse(destination in topLevelDestinations)
            nav.popBackStack()
            assertEquals(R.id.moreFragment, nav.currentId)
        }
    }

    @Test
    fun quickAddOpensOverTheCurrentTabAndBackReturnsToIt() = onMain {
        val nav = navController(AppGraph.MAIN)
        nav.navigate(R.id.analyticsFragment)
        nav.openQuickAdd()
        nav.openQuickAdd()
        assertEquals(R.id.quickAddSheet, nav.currentId)
        nav.popBackStack()
        assertEquals(R.id.analyticsFragment, nav.currentId)
    }
}
