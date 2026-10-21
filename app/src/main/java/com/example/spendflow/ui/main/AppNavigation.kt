package com.example.spendflow.ui.main

import androidx.annotation.IdRes
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.navOptions
import com.example.spendflow.R

enum class AppGraph(@IdRes val id: Int) {
    AUTH(R.id.auth_graph),
    MAIN(R.id.main_graph),
}

/** Destinations that show the bottom bar; their ids match the items in menu/bottom_bar.xml. */
val topLevelDestinations = setOf(
    R.id.homeFragment,
    R.id.transactionsFragment,
    R.id.analyticsFragment,
    R.id.moreFragment,
)

/** Sets nav_root starting at [start]. After recreation the restored back stack takes precedence. */
fun NavController.setRootGraph(start: AppGraph) {
    graph = navInflater.inflate(R.navigation.nav_root).apply { setStartDestination(start.id) }
}

/** Moves to [target] and clears the back stack, so Back can't return to the other graph. */
fun NavController.showGraph(target: AppGraph) {
    if (currentDestination?.hierarchy?.any { it.id == target.id } == true) return
    navigate(target.id, null, navOptions { popUpTo(R.id.nav_root) { inclusive = true } })
}

fun NavController.openQuickAdd() {
    // A fast double tap would otherwise stack two sheets
    if (currentDestination?.id != R.id.quickAddSheet) navigate(R.id.quickAddSheet)
}
