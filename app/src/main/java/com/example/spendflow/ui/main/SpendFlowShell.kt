package com.example.spendflow.ui.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.spendflow.R
import com.example.spendflow.core.common.AppResult
import com.example.spendflow.core.notifications.AndroidGoalNotifications
import com.example.spendflow.core.notifications.GoalNotificationMonitor
import com.example.spendflow.core.ui.EventEffect
import com.example.spendflow.core.ui.components.AmountFormatter
import com.example.spendflow.core.ui.components.BottomBarTab
import com.example.spendflow.core.ui.components.SpendFlowBottomBar
import com.example.spendflow.core.ui.components.SpendFlowSnackbar
import com.example.spendflow.core.ui.components.SpendFlowSnackbarVisuals
import com.example.spendflow.core.ui.messageRes
import com.example.spendflow.ui.analytics.AnalyticsRoute
import com.example.spendflow.ui.auth.LoginRoute
import com.example.spendflow.ui.categories.CategoriesRoute
import com.example.spendflow.ui.goals.GoalsRoute
import com.example.spendflow.ui.more.MoreRoute
import com.example.spendflow.ui.onboarding.OnboardingRoute
import com.example.spendflow.ui.quickadd.EntryConfirmation
import com.example.spendflow.ui.quickadd.EntryFeedbackViewModel
import com.example.spendflow.ui.quickadd.QuickAddRoute
import com.example.spendflow.ui.quickadd.QuickAddViewModel
import com.example.spendflow.ui.settings.SettingsRoute
import com.example.spendflow.ui.transactions.TransactionFeedRoute
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.math.BigDecimal
import java.util.Currency
import java.util.Locale

private val TABS = listOf(
    BottomBarTab(Routes.HOME, R.string.nav_home, R.drawable.ic_home_mono),
    BottomBarTab(Routes.TRANSACTIONS, R.string.nav_transactions, R.drawable.ic_transactions_mono),
    BottomBarTab(Routes.ANALYTICS, R.string.nav_analytics, R.drawable.ic_analytics_mono),
    BottomBarTab(Routes.MORE, R.string.nav_more, R.drawable.ic_more_mono),
)

/**
 * The app shell: follows the session into the auth, onboarding or main graph, shows the bottom bar on the
 * tabs (and under Quick Add), and shows Quick Add's saved / undo / sync snackbars above it.
 * [openGoalsRequested] is set by a goal-completion notification; [onGoalsOpened] consumes it.
 */
@Composable
fun SpendFlowShell(
    mainViewModel: MainViewModel,
    entryFeedback: EntryFeedbackViewModel,
    notifications: AndroidGoalNotifications,
    monitor: GoalNotificationMonitor,
    openGoalsRequested: Boolean,
    onGoalsOpened: () -> Unit,
) {
    val nav = rememberNavController()
    val graph by mainViewModel.graph.collectAsStateWithLifecycle()
    // Only the first start matters; after recreation the restored back stack takes precedence
    val startGraph = rememberSaveable { graph.route }
    val entry by nav.currentBackStackEntryAsState()
    // Quick Add opens over a tab, and the bar stays visible under it
    val tabRoute = (if (entry?.destination?.route == Routes.QUICK_ADD) nav.previousBackStackEntry else entry)?.destination?.route
    // Settings sits under the More tab in the wireframes, so it keeps the bar with More selected
    val barRoute = if (tabRoute == Routes.SETTINGS) Routes.MORE else tabRoute
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(graph) {
        if (graph != AppGraph.MAIN) entryFeedback.clear()
        nav.showGraph(graph)
    }
    LaunchedEffect(openGoalsRequested, graph) {
        if (!openGoalsRequested || graph != AppGraph.MAIN) return@LaunchedEffect
        if (nav.currentDestination?.route != Routes.GOALS) nav.navigate(Routes.GOALS)
        onGoalsOpened()
    }
    val syncModel = hiltViewModel<SyncStatusViewModel>()
    val sync by syncModel.status.collectAsStateWithLifecycle()
    // rememberUpdatedState: read at show time, so a connectivity change doesn't restart the snackbar
    val offlineNow by rememberUpdatedState(sync.offline)
    EntryFeedbackSnackbars(entryFeedback, snackbar) { offlineNow }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) { SpendFlowSnackbar(it) } },
        bottomBar = {
            if (barRoute in topLevelDestinations) {
                SpendFlowBottomBar(
                    tabs = TABS,
                    selected = TABS.firstOrNull { it.route == barRoute },
                    // Reselecting More from Settings returns to More; on tab roots there is nothing to pop
                    onSelect = {
                        when {
                            it.route != barRoute -> nav.navigateToTab(it.route)
                            tabRoute != barRoute -> nav.popBackStack(Routes.MORE, inclusive = false)
                        }
                    },
                    onAdd = nav::openQuickAdd,
                )
            }
        },
    ) { padding ->
        // Screens leave system bars, the bottom bar and the keyboard to the shell
        Column(
            Modifier
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding(),
        ) {
            if (graph == AppGraph.MAIN && sync.offline) OfflineBanner(sync.pendingCount)
            NavHost(
                navController = nav,
                startDestination = startGraph,
                modifier = Modifier.weight(1f),
            ) {
                appNavGraph { route, _ -> Destination(route, nav, entryFeedback, notifications, monitor) }
            }
        }
    }
}

@Composable
private fun Destination(
    route: String,
    nav: NavHostController,
    entryFeedback: EntryFeedbackViewModel,
    notifications: AndroidGoalNotifications,
    monitor: GoalNotificationMonitor,
) {
    when (route) {
        Routes.LOGIN -> LoginRoute(hiltViewModel())
        Routes.ONBOARDING -> OnboardingRoute(hiltViewModel())
        Routes.HOME -> TransactionFeedRoute(
            hiltViewModel(),
            home = true,
            onSeeAll = { nav.navigateToTab(Routes.TRANSACTIONS) },
            onViewGoals = { nav.navigate(Routes.GOALS) },
            onManageCaps = { nav.navigate(Routes.CATEGORIES) },
        )
        Routes.TRANSACTIONS -> TransactionFeedRoute(hiltViewModel(), home = false, onSeeAll = {}, onViewGoals = {})
        Routes.ANALYTICS -> AnalyticsRoute(hiltViewModel(), onAddTransaction = nav::openQuickAdd)
        Routes.MORE -> MoreRoute(
            hiltViewModel(),
            onOpenCategories = { nav.navigate(Routes.CATEGORIES) },
            onOpenGoals = { nav.navigate(Routes.GOALS) },
            onOpenSettings = { nav.navigate(Routes.SETTINGS) },
        )
        Routes.CATEGORIES -> CategoriesRoute(hiltViewModel(), hiltViewModel(), onBack = { nav.navigateUp() })
        Routes.GOALS -> GoalsRoute(hiltViewModel(), notifications, monitor, onBack = { nav.navigateUp() })
        Routes.SETTINGS -> SettingsRoute(
            hiltViewModel(),
            onOpenCategories = { nav.navigate(Routes.CATEGORIES) },
            onOpenGoals = { nav.navigate(Routes.GOALS) },
        )
        Routes.QUICK_ADD -> {
            val model = hiltViewModel<QuickAddViewModel>()
            QuickAddSheet(onDismissed = nav::dismissQuickAdd) { close ->
                // Saved just before a recreation: hand the entry to the undo snackbar and close
                LaunchedEffect(Unit) {
                    if (model.completed) {
                        model.takeSavedEntry()?.let(entryFeedback::saved)
                        close()
                    }
                }
                QuickAddRoute(model, onSaved = entryFeedback::saved, onClose = close)
            }
        }
    }
}

/**
 * Material 3 bottom sheet inside the Quick Add dialog destination. The sheet draws its own scrim, so the
 * destination's window stays undimmed. [content] gets a close action that animates the sheet away first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickAddSheet(onDismissed: () -> Unit, content: @Composable (close: () -> Unit) -> Unit) {
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window
    SideEffect { window?.setDimAmount(0f) }
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var closing by remember { mutableStateOf(false) }
    val close: () -> Unit = {
        if (!closing) {
            closing = true
            scope.launch {
                // A cancelled animation must never pop a different destination.
                try {
                    state.hide()
                    if (!state.isVisible) onDismissed()
                } finally {
                    closing = false
                }
            }
        }
    }
    ModalBottomSheet(onDismissRequest = onDismissed, sheetState = state) { content(close) }
}

/** "Saved · Groceries LKR 2,450 [Undo]" for 5 s after a save, plus undo results and rejected writes. */
@Composable
private fun EntryFeedbackSnackbars(
    entryFeedback: EntryFeedbackViewModel,
    snackbar: SnackbarHostState,
    isOffline: () -> Boolean,
) {
    val resources = LocalResources.current
    val locale = LocalLocale.current.platformLocale
    val scope = rememberCoroutineScope()
    val confirmation by entryFeedback.confirmation.collectAsStateWithLifecycle()
    // Restarting on a new or cleared confirmation cancels showSnackbar, which removes the old snackbar
    LaunchedEffect(confirmation, resources, locale) {
        val current = confirmation ?: return@LaunchedEffect
        val remaining = current.expiresAt - System.currentTimeMillis()
        if (remaining <= 0) return@LaunchedEffect
        val visuals = SpendFlowSnackbarVisuals(
            if (isOffline()) resources.getString(R.string.sync_saved_offline)
            else resources.getString(R.string.entry_saved, current.entry.categoryName, savedAmount(current, locale)),
            SpendFlowSnackbar.Tone.SUCCESS,
            actionLabel = resources.getString(R.string.entry_undo),
            duration = SnackbarDuration.Indefinite,
        )
        val result = withTimeoutOrNull(remaining.coerceAtMost(5000)) { snackbar.showSnackbar(visuals) }
        if (result == SnackbarResult.ActionPerformed) entryFeedback.undo(current)
    }
    EventEffect(entryFeedback.rejectedWrites) { error ->
        val message = resources.getString(R.string.entry_sync_failed, resources.getString(error.messageRes))
        scope.launch {
            snackbar.showSnackbar(SpendFlowSnackbarVisuals(message, SpendFlowSnackbar.Tone.ERROR, duration = SnackbarDuration.Long))
        }
    }
    EventEffect(entryFeedback.events) { result ->
        val visuals = when (result) {
            is AppResult.Success -> SpendFlowSnackbarVisuals(resources.getString(R.string.entry_undone), SpendFlowSnackbar.Tone.SUCCESS)
            is AppResult.Error -> SpendFlowSnackbarVisuals(
                resources.getString(R.string.entry_undo_failed, resources.getString(result.error.messageRes)),
                SpendFlowSnackbar.Tone.ERROR,
            )
        }
        scope.launch { snackbar.showSnackbar(visuals) }
    }
}

/** Whole amounts without decimals ("LKR 2,450"), fractional ones with them. */
private fun savedAmount(confirmation: EntryConfirmation, locale: Locale): String {
    val money = confirmation.entry.transaction.amount
    val digits = Currency.getInstance(money.currencyCode).defaultFractionDigits
    val hasFraction = BigDecimal.valueOf(money.amountMinor).movePointLeft(digits).stripTrailingZeros().scale() > 0
    return AmountFormatter.format(money, showDecimals = hasFraction, locale = locale)
}
