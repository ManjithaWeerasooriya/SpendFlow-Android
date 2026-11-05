package com.example.spendflow.ui.main

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import androidx.navigation.compose.navigation
import java.time.YearMonth

internal const val ANALYTICS_MONTH_REQUEST = "analytics_month_request"

/** Destination routes. The four tabs are the destinations that show the bottom bar. */
object Routes {
    const val LOGIN = "login"
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val TRANSACTIONS = "transactions"
    const val ANALYTICS = "analytics"
    const val MORE = "more"
    const val CATEGORIES = "categories"
    const val GOALS = "goals"
    const val SETTINGS = "settings"
    const val QUICK_ADD = "quick_add"
}

enum class AppGraph(val route: String, val start: String) {
    AUTH("auth_graph", Routes.LOGIN),
    ONBOARDING("onboarding_graph", Routes.ONBOARDING),
    MAIN("main_graph", Routes.HOME),
}

val topLevelDestinations = setOf(Routes.HOME, Routes.TRANSACTIONS, Routes.ANALYTICS, Routes.MORE)

/**
 * The auth, onboarding and main graphs. [content] draws each destination; the app passes the real screens,
 * and AppNavigationTest passes placeholders so it can check the back-stack rules without Hilt.
 */
fun NavGraphBuilder.appNavGraph(content: @Composable (route: String, entry: NavBackStackEntry) -> Unit) {
    navigation(startDestination = AppGraph.AUTH.start, route = AppGraph.AUTH.route) {
        composable(Routes.LOGIN) { content(Routes.LOGIN, it) }
    }
    // Its own graph so finishing it can clear the back stack
    navigation(startDestination = AppGraph.ONBOARDING.start, route = AppGraph.ONBOARDING.route) {
        composable(Routes.ONBOARDING) { content(Routes.ONBOARDING, it) }
    }
    navigation(startDestination = AppGraph.MAIN.start, route = AppGraph.MAIN.route) {
        listOf(
            Routes.HOME, Routes.TRANSACTIONS, Routes.ANALYTICS, Routes.MORE,
            Routes.CATEGORIES, Routes.GOALS, Routes.SETTINGS,
        ).forEach { route -> composable(route) { content(route, it) } }
        // Full-window, so the bottom sheet drawn inside it can span the screen
        dialog(Routes.QUICK_ADD, dialogProperties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            content(Routes.QUICK_ADD, it)
        }
    }
}

/** Moves to [target] and clears the back stack, so Back can't return to the other graph. */
fun NavController.showGraph(target: AppGraph) {
    if (currentDestination?.hierarchy?.any { it.route == target.route } == true) return
    navigate(target.route) { popUpTo(graph.id) { inclusive = true } }
}

/** Switches tabs the way the bottom bar did: one entry per tab, each tab keeping its own state. */
fun NavController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(Routes.HOME) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Durable, one-shot request on the existing destination, including when restoring its tab. */
fun NavController.openAnalytics(month: YearMonth) {
    if (currentDestination?.route != Routes.ANALYTICS) navigateToTab(Routes.ANALYTICS)
    getBackStackEntry(Routes.ANALYTICS).savedStateHandle[ANALYTICS_MONTH_REQUEST] = month.toString()
}

/** Ignore duplicate or late callbacks after the sheet or session has already closed. */
fun NavController.dismissQuickAdd() {
    if (currentDestination?.route == Routes.QUICK_ADD) popBackStack()
}

fun NavController.openQuickAdd() {
    // A fast double tap would otherwise stack two sheets
    if (currentDestination?.route != Routes.QUICK_ADD) navigate(Routes.QUICK_ADD)
}
