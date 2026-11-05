package com.example.spendflow.ui.main

import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.DialogNavigator
import androidx.navigation.compose.NavHost
import androidx.navigation.testing.TestNavHostController
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Back-stack rules for the auth, onboarding and main graphs (AC1, AC4, AC5), checked against the real graph with placeholder screens. */
@RunWith(AndroidJUnit4::class)
class AppNavigationTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var nav: TestNavHostController

    private fun start(graph: AppGraph) {
        compose.setContent {
            nav = TestNavHostController(LocalContext.current).apply {
                navigatorProvider.addNavigator(ComposeNavigator())
                navigatorProvider.addNavigator(DialogNavigator())
            }
            NavHost(nav, startDestination = graph.route) { appNavGraph { route, _ -> Text(route) } }
        }
    }

    private val currentRoute get() = nav.currentDestination?.route

    // NavController must be driven from the main thread; runOnIdle also lets the NavHost settle first
    private fun onMain(block: () -> Unit) = compose.runOnIdle(block)

    @Test
    fun loggedOutStartsOnLogin() {
        start(AppGraph.AUTH)
        onMain { assertEquals(Routes.LOGIN, currentRoute) }
    }

    @Test
    fun loggedInStartsOnHome() {
        start(AppGraph.MAIN)
        onMain { assertEquals(Routes.HOME, currentRoute) }
    }

    @Test
    fun signingInReplacesLoginSoBackLeavesTheApp() {
        start(AppGraph.AUTH)
        onMain { nav.showGraph(AppGraph.MAIN) }
        onMain {
            assertEquals(Routes.HOME, currentRoute)
            assertNull(nav.previousBackStackEntry)
        }
    }

    @Test
    fun registeringOpensOnboardingAndFinishingItReplacesItWithHome() {
        start(AppGraph.AUTH)
        onMain { nav.showGraph(AppGraph.ONBOARDING) }
        onMain {
            assertEquals(Routes.ONBOARDING, currentRoute)
            assertNull(nav.previousBackStackEntry)
            assertFalse(Routes.ONBOARDING in topLevelDestinations)
            nav.showGraph(AppGraph.MAIN)
        }
        onMain {
            assertEquals(Routes.HOME, currentRoute)
            assertNull(nav.previousBackStackEntry)
        }
    }

    @Test
    fun signingOutClearsTheMainGraph() {
        start(AppGraph.MAIN)
        onMain {
            nav.navigateToTab(Routes.MORE)
            nav.navigate(Routes.GOALS)
            nav.showGraph(AppGraph.AUTH)
        }
        onMain {
            assertEquals(Routes.LOGIN, currentRoute)
            assertNull(nav.previousBackStackEntry)
        }
    }

    @Test
    fun showingTheCurrentGraphKeepsTheBackStack() {
        start(AppGraph.MAIN)
        onMain {
            nav.navigateToTab(Routes.MORE)
            nav.showGraph(AppGraph.MAIN)
        }
        onMain { assertEquals(Routes.MORE, currentRoute) }
    }

    @Test
    fun moreLinksAreOneTapAwayAndBackReturnsToMore() {
        start(AppGraph.MAIN)
        onMain { nav.navigateToTab(Routes.MORE) }
        listOf(Routes.CATEGORIES, Routes.GOALS, Routes.SETTINGS).forEach { destination ->
            onMain { nav.navigate(destination) }
            onMain {
                assertEquals(destination, currentRoute)
                assertFalse(destination in topLevelDestinations)
                nav.popBackStack()
            }
            onMain { assertEquals(Routes.MORE, currentRoute) }
        }
    }

    @Test
    fun quickAddOpensOverTheCurrentTabAndBackReturnsToIt() {
        start(AppGraph.MAIN)
        onMain { nav.navigateToTab(Routes.ANALYTICS) }
        onMain {
            nav.openQuickAdd()
            nav.openQuickAdd()
        }
        onMain {
            assertEquals(Routes.QUICK_ADD, currentRoute)
            assertEquals(Routes.ANALYTICS, nav.previousBackStackEntry?.destination?.route)
            nav.popBackStack()
        }
        onMain { assertEquals(Routes.ANALYTICS, currentRoute) }
    }

    @Test
    fun tabsKeepOneEntryEachAboveHome() {
        start(AppGraph.MAIN)
        onMain {
            nav.navigateToTab(Routes.TRANSACTIONS)
            nav.navigateToTab(Routes.ANALYTICS)
            nav.navigateToTab(Routes.MORE)
        }
        onMain {
            assertEquals(Routes.MORE, currentRoute)
            assertEquals(Routes.HOME, nav.previousBackStackEntry?.destination?.route)
        }
    }
}
