package com.example.spendflow.ui.categories

import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.spendflow.R
import com.example.spendflow.core.common.AppResult
import com.example.spendflow.core.ui.EventEffect
import com.example.spendflow.core.ui.UiState
import com.example.spendflow.core.ui.components.EmptyState
import com.example.spendflow.core.ui.components.PrimaryButton
import com.example.spendflow.core.ui.components.SecondaryButton
import com.example.spendflow.core.ui.components.SpendFlowTopBar
import com.example.spendflow.core.ui.components.SpendFlowSnackbar
import com.example.spendflow.core.ui.components.SpendFlowSnackbarVisuals
import com.example.spendflow.core.ui.components.ThemePreviews
import com.example.spendflow.core.ui.messageRes
import com.example.spendflow.domain.model.Category
import com.example.spendflow.domain.model.CategoryType
import com.example.spendflow.ui.theme.SpendFlowTheme
import kotlinx.coroutines.launch
import java.io.Serializable

internal data class CategoryCapProgress(val spent: com.example.spendflow.domain.model.Money, val limit: com.example.spendflow.domain.model.Money)

internal enum class CategoryAction(@StringRes val label: Int) {
    RENAME(R.string.category_rename),
    CAP(R.string.category_cap),
    ARCHIVE(R.string.category_archive),
    RESTORE(R.string.category_restore),
    MOVE_UP(R.string.category_move_up),
    MOVE_DOWN(R.string.category_move_down),
}

/** The open dialog, by category ID so it survives recreation and always shows current data. */
internal data class CategoriesDialog(val kind: Kind, val categoryId: String? = null) : Serializable {
    enum class Kind { EXPENSE_FORM, EXPENSE_CAP, INCOME_FORM, INCOME_ARCHIVE }
}

@Composable
internal fun CategoriesRoute(
    viewModel: CategoriesViewModel,
    expenseModel: ExpenseCategoriesViewModel,
    onBack: () -> Unit,
) {
    val tab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val incomeState by viewModel.uiState.collectAsStateWithLifecycle()
    val incomeSaving by viewModel.incomeSourceSaving.collectAsStateWithLifecycle()
    val expenseResult by expenseModel.categories.collectAsStateWithLifecycle()
    val expenseSaving by expenseModel.saving.collectAsStateWithLifecycle()
    val caps by expenseModel.caps.collectAsStateWithLifecycle()
    val spending by expenseModel.spending.collectAsStateWithLifecycle()
    val summary = (spending as? AppResult.Success)?.data
    val progress = (caps as? AppResult.Success)?.data.orEmpty().mapNotNull { budget ->
        val id = budget.categoryId
        if (id == null || summary == null || budget.period != com.example.spendflow.domain.model.BudgetPeriod.MONTHLY ||
            budget.startDate > java.time.LocalDate.now() || budget.limit.currencyCode != summary.totalExpenses.currencyCode) null
        else id to CategoryCapProgress(summary.expensesByCategory[id] ?: com.example.spendflow.domain.model.Money.zero(budget.limit.currencyCode), budget.limit)
    }.toMap()
    val expenses = when (val result = expenseResult) {
        is AppResult.Success -> UiState.Success(result.data)
        is AppResult.Error -> UiState.Error(result.error)
        null -> UiState.Loading
    }
    val incomeSources = (incomeState as? UiState.Success)
        ?.let { UiState.Success(it.data.filter { c -> c.type == CategoryType.INCOME }) } ?: incomeState

    var dialog by rememberSaveable { mutableStateOf<CategoriesDialog?>(null) }
    var expenseError by remember { mutableStateOf<Int?>(null) }
    var incomeErrors by remember { mutableStateOf(IncomeSourceFormErrors()) }
    var incomeWriteError by remember { mutableStateOf<Int?>(null) }
    fun open(next: CategoriesDialog) {
        expenseError = null
        incomeErrors = IncomeSourceFormErrors()
        incomeWriteError = null
        dialog = next
    }

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    fun show(message: Int, tone: SpendFlowSnackbar.Tone = SpendFlowSnackbar.Tone.NONE, long: Boolean = false) {
        snackbar.currentSnackbarData?.dismiss()
        val duration = if (long) SnackbarDuration.Long else SnackbarDuration.Short
        scope.launch { snackbar.showSnackbar(SpendFlowSnackbarVisuals(resources.getString(message), tone, duration = duration)) }
    }
    EventEffect(expenseModel.events) { result ->
        when (result) {
            is AppResult.Success -> if (dialog?.kind == CategoriesDialog.Kind.EXPENSE_FORM ||
                dialog?.kind == CategoriesDialog.Kind.EXPENSE_CAP
            ) dialog = null
            is AppResult.Error -> {
                expenseError = result.error.messageRes
                show(result.error.messageRes, SpendFlowSnackbar.Tone.ERROR)
            }
        }
    }
    EventEffect(viewModel.events) { event ->
        when (event) {
            CategoriesEvent.CategoryAdded -> Unit
            CategoriesEvent.IncomeSourceSaved -> dialog = null
            is CategoriesEvent.IncomeSourceWriteFailed -> if (dialog?.kind == CategoriesDialog.Kind.INCOME_FORM) {
                incomeWriteError = event.error.messageRes
            } else {
                dialog = null
                show(event.error.messageRes, SpendFlowSnackbar.Tone.ERROR, long = true)
            }
            is CategoriesEvent.ShowMessage -> show(event.messageRes)
            is CategoriesEvent.ShowError -> show(event.error.messageRes, SpendFlowSnackbar.Tone.ERROR, long = true)
        }
    }

    val activeExpenses = (expenses as? UiState.Success)?.data.orEmpty().filterNot { it.isArchived }
    CategoriesScreen(
        tab = tab,
        expenses = expenses,
        incomeSources = incomeSources,
        expenseBusy = expenseSaving || dialog != null,
        incomeBusy = incomeSaving || dialog != null,
        snackbar = snackbar,
        capProgress = progress,
        onBack = onBack,
        onSelectTab = viewModel::selectTab,
        onAddCategory = { open(CategoriesDialog(CategoriesDialog.Kind.EXPENSE_FORM)) },
        onAddIncomeSource = { open(CategoriesDialog(CategoriesDialog.Kind.INCOME_FORM)) },
        onExpenseAction = { category, action ->
            when (action) {
                CategoryAction.RENAME -> open(CategoriesDialog(CategoriesDialog.Kind.EXPENSE_FORM, category.id))
                CategoryAction.CAP -> open(CategoriesDialog(CategoriesDialog.Kind.EXPENSE_CAP, category.id))
                CategoryAction.ARCHIVE, CategoryAction.RESTORE -> expenseModel.archive(category, !category.isArchived)
                CategoryAction.MOVE_UP, CategoryAction.MOVE_DOWN -> {
                    val rows = activeExpenses.toMutableList()
                    val index = rows.indexOfFirst { it.id == category.id }
                    val target = index + if (action == CategoryAction.MOVE_UP) -1 else 1
                    if (index >= 0 && target in rows.indices) {
                        rows.add(target, rows.removeAt(index))
                        expenseModel.reorder(rows.map { it.id })
                    }
                }
            }
        },
        onIncomeAction = { source, action ->
            when (action) {
                CategoryAction.RENAME -> open(CategoriesDialog(CategoriesDialog.Kind.INCOME_FORM, source.id))
                CategoryAction.ARCHIVE -> open(CategoriesDialog(CategoriesDialog.Kind.INCOME_ARCHIVE, source.id))
                else -> Unit
            }
        },
        onReorder = expenseModel::reorder,
        onRetry = {
            viewModel.retry()
            expenseModel.retry()
        },
    )

    val current = dialog ?: return
    val category = current.categoryId?.let { id ->
        ((if (current.kind.isIncome) incomeSources else expenses) as? UiState.Success)?.data?.firstOrNull { it.id == id }
    }
    // Loading or an observer failure does not prove a restored category was deleted.
    if (current.categoryId != null && category == null) {
        val source = if (current.kind.isIncome) incomeSources else expenses
        if (source is UiState.Success) LaunchedEffect(current) { dialog = null }
        return
    }
    val close = { dialog = null }
    when (current.kind) {
        CategoriesDialog.Kind.EXPENSE_FORM -> ExpenseCategoryDialog(
            category = category,
            saving = expenseSaving,
            error = expenseError,
            onSave = { name, icon, classification -> expenseError = expenseModel.save(category?.id, name, icon, classification) },
            onDismiss = close,
        )
        CategoriesDialog.Kind.EXPENSE_CAP -> ObservedCategoryCapDialog(
            category = category!!,
            caps = caps,
            saving = expenseSaving,
            error = expenseError,
            onSave = { amount -> expenseError = expenseModel.saveCap(category, amount) },
            onDismiss = close,
        )
        CategoriesDialog.Kind.INCOME_FORM -> IncomeSourceDialog(
            source = category,
            saving = incomeSaving,
            errors = incomeErrors,
            writeError = incomeWriteError,
            onSave = { name, currency ->
                incomeWriteError = null
                incomeErrors = viewModel.saveIncomeSource(category?.id, name, currency)
            },
            onDismiss = close,
        )
        CategoriesDialog.Kind.INCOME_ARCHIVE -> ArchiveIncomeSourceDialog(
            source = category!!,
            saving = incomeSaving,
            onConfirm = { viewModel.archiveIncomeSource(category.id) },
            onDismiss = close,
        )
    }
}

private val CategoriesDialog.Kind.isIncome
    get() = this == CategoriesDialog.Kind.INCOME_FORM || this == CategoriesDialog.Kind.INCOME_ARCHIVE

/**
 * Expense categories (with archive, caps and order) and income sources on two tabs.
 * [expenses] includes archived categories; the screen filters by the Archived toggle.
 * [expenseBusy] / [incomeBusy] lock actions while a write runs or a dialog is open.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CategoriesScreen(
    tab: CategoriesTab,
    expenses: UiState<List<Category>>,
    incomeSources: UiState<List<Category>>,
    expenseBusy: Boolean,
    incomeBusy: Boolean,
    onBack: () -> Unit,
    onSelectTab: (CategoriesTab) -> Unit,
    onAddCategory: () -> Unit,
    onAddIncomeSource: () -> Unit,
    onExpenseAction: (Category, CategoryAction) -> Unit,
    onIncomeAction: (Category, CategoryAction) -> Unit,
    onReorder: (List<String>) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
    capProgress: Map<String, CategoryCapProgress> = emptyMap(),
) {
    var showingArchived by rememberSaveable { mutableStateOf(false) }
    val colors = SpendFlowTheme.colors
    // The app shell already pads the NavHost for system bars and the keyboard.
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) { SpendFlowSnackbar(it) } },
        topBar = { SpendFlowTopBar(stringResource(R.string.budget_categories_title), onBack) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(
                    start = dimensionResource(R.dimen.sf_screen_padding),
                    end = dimensionResource(R.dimen.sf_screen_padding),
                    bottom = dimensionResource(R.dimen.sf_screen_padding),
                ),
        ) {
            PrimaryTabRow(
                selectedTabIndex = tab.ordinal,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = colors.textHeading,
                indicator = {
                    TabRowDefaults.PrimaryIndicator(Modifier.tabIndicatorOffset(tab.ordinal), color = colors.violet)
                },
            ) {
                CategoriesTab.entries.forEach { option ->
                    Tab(
                        selected = tab == option,
                        onClick = { onSelectTab(option) },
                        text = {
                            Text(
                                stringResource(if (option == CategoriesTab.CATEGORIES) R.string.category_expense_tab else R.string.income_sources_tab),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        },
                        unselectedContentColor = colors.textMuted,
                    )
                }
            }
            if (tab == CategoriesTab.CATEGORIES) {
                val all = (expenses as? UiState.Success)?.data.orEmpty()
                PrimaryButton(
                    stringResource(R.string.category_add_title),
                    onClick = onAddCategory,
                    enabled = !expenseBusy && expenses is UiState.Success,
                    modifier = Modifier
                        .padding(top = dimensionResource(R.dimen.sf_space_m))
                        .fillMaxWidth(),
                )
                SecondaryButton(
                    if (showingArchived) stringResource(R.string.category_active)
                    else stringResource(R.string.category_archived, all.count { it.isArchived }),
                    onClick = { showingArchived = !showingArchived },
                    enabled = !expenseBusy,
                    modifier = Modifier
                        .padding(top = dimensionResource(R.dimen.sf_space_s))
                        .fillMaxWidth(),
                )
                if (!showingArchived) {
                    Text(
                        stringResource(R.string.category_reorder),
                        Modifier.padding(top = dimensionResource(R.dimen.sf_space_s)),
                        color = colors.textMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                val shown = (expenses as? UiState.Success)
                    ?.let { UiState.Success(it.data.filter { c -> c.isArchived == showingArchived }) } ?: expenses
                ListContent(shown, CategoriesTab.CATEGORIES, onRetry) { rows ->
                    CategoryList(
                        rows = rows,
                        badge = { stringResource(CategoryAppearance.classification(it.classification)) },
                        actions = { if (it.isArchived) listOf(CategoryAction.RESTORE) else ExpenseActions },
                        actionsEnabled = !expenseBusy,
                        onAction = onExpenseAction,
                        onReorder = onReorder.takeIf { !showingArchived && !expenseBusy },
                        capProgress = if (showingArchived) emptyMap() else capProgress,
                    )
                }
            } else {
                PrimaryButton(
                    stringResource(R.string.income_sources_add),
                    onClick = onAddIncomeSource,
                    enabled = !incomeBusy,
                    icon = R.drawable.ic_plus,
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = dimensionResource(R.dimen.sf_space_m)),
                )
                ListContent(incomeSources, CategoriesTab.INCOME_SOURCES, onRetry) { rows ->
                    CategoryList(
                        rows = rows,
                        badge = { it.defaultCurrency?.let { code -> stringResource(R.string.income_sources_default_currency, code) } },
                        actions = { IncomeActions },
                        actionsEnabled = !incomeBusy,
                        onAction = onIncomeAction,
                        onReorder = null,
                    )
                }
            }
        }
    }
}

private val ExpenseActions = listOf(
    CategoryAction.RENAME, CategoryAction.CAP, CategoryAction.ARCHIVE, CategoryAction.MOVE_UP, CategoryAction.MOVE_DOWN,
)
private val IncomeActions = listOf(CategoryAction.RENAME, CategoryAction.ARCHIVE)

@Composable
private fun ListContent(
    state: UiState<List<Category>>,
    tab: CategoriesTab,
    onRetry: () -> Unit,
    list: @Composable (List<Category>) -> Unit,
) {
    val income = tab == CategoriesTab.INCOME_SOURCES
    Box(
        Modifier
            .padding(top = dimensionResource(R.dimen.sf_space_l))
            .fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            UiState.Loading -> {
                val description = stringResource(if (income) R.string.income_sources_loading else R.string.categories_loading)
                CircularProgressIndicator(Modifier.semantics { contentDescription = description }, color = SpendFlowTheme.colors.violet)
            }
            is UiState.Error -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(state.error.messageRes),
                    Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
                SecondaryButton(
                    stringResource(R.string.action_retry),
                    onClick = onRetry,
                    modifier = Modifier.padding(top = dimensionResource(R.dimen.sf_space_s)),
                )
            }
            is UiState.Success -> if (state.data.isEmpty()) {
                EmptyState(
                    title = stringResource(if (income) R.string.income_sources_empty_title else R.string.categories_empty_title),
                    icon = R.drawable.ic_budget,
                    message = stringResource(if (income) R.string.income_sources_empty_message else R.string.categories_empty_message),
                )
            } else {
                list(state.data)
            }
        }
    }
}

/**
 * Rows with an actions menu each. With [onReorder], a long press starts a drag; the new order is
 * reported once on release, and incoming data is ignored until then, as the RecyclerView did.
 */
@Composable
private fun CategoryList(
    rows: List<Category>,
    badge: @Composable (Category) -> String?,
    actions: (Category) -> List<CategoryAction>,
    actionsEnabled: Boolean,
    onAction: (Category, CategoryAction) -> Unit,
    onReorder: ((List<String>) -> Unit)?,
    capProgress: Map<String, CategoryCapProgress> = emptyMap(),
) {
    val listState = rememberLazyListState()
    // The drag gesture outlives recompositions, so it reads the latest rows and callback through these
    val currentRows by rememberUpdatedState(rows)
    val currentOnReorder by rememberUpdatedState(onReorder)
    var dragOrder by remember { mutableStateOf<List<Category>?>(null) }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val shown = dragOrder ?: rows

    fun endDrag() {
        val order = dragOrder
        dragOrder = null
        draggingId = null
        dragOffset = 0f
        if (order != null && order.map { it.id } != currentRows.map { it.id }) currentOnReorder?.invoke(order.map { it.id })
    }

    LazyColumn(Modifier.fillMaxSize(), state = listState) {
        items(shown, key = { it.id }) { category ->
            val dragging = category.id == draggingId
            val dragModifier = if (onReorder == null) Modifier else Modifier.pointerInput(category.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        dragOrder = currentRows
                        draggingId = category.id
                        dragOffset = 0f
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        dragOffset += amount.y
                        val items = listState.layoutInfo.visibleItemsInfo
                        val dragged = items.firstOrNull { it.key == category.id } ?: return@detectDragGesturesAfterLongPress
                        val center = dragged.offset + dragOffset + dragged.size / 2
                        val target = items.firstOrNull { it.key != category.id && center.toInt() in it.offset until it.offset + it.size }
                        val order = dragOrder
                        if (target != null && order != null) {
                            val from = order.indexOfFirst { it.id == category.id }
                            val to = order.indexOfFirst { it.id == target.key }
                            if (from >= 0 && to >= 0) {
                                dragOrder = order.toMutableList().apply { add(to, removeAt(from)) }
                                // Keep the row under the finger now that its slot moved
                                dragOffset += dragged.offset - target.offset
                            }
                        }
                    },
                    onDragEnd = ::endDrag,
                    onDragCancel = ::endDrag,
                )
            }
            CategoryRow(
                category = category,
                badge = badge(category),
                cap = capProgress[category.id],
                actions = actions(category),
                actionsEnabled = actionsEnabled && draggingId == null,
                onAction = { onAction(category, it) },
                modifier = Modifier
                    .then(if (dragging) Modifier else Modifier.animateItem())
                    .zIndex(if (dragging) 1f else 0f)
                    .graphicsLayer { translationY = if (dragging) dragOffset else 0f }
                    .then(
                        if (dragging) {
                            Modifier
                                .shadow(8.dp, MaterialTheme.shapes.medium)
                                .background(SpendFlowTheme.colors.insetCard, MaterialTheme.shapes.medium)
                        } else {
                            Modifier
                        },
                    )
                    .then(dragModifier),
            )
        }
    }
}

@Composable
private fun CategoryRow(
    category: Category,
    badge: String?,
    actions: List<CategoryAction>,
    actionsEnabled: Boolean,
    onAction: (CategoryAction) -> Unit,
    modifier: Modifier = Modifier,
    cap: CategoryCapProgress? = null,
) {
    val colors = SpendFlowTheme.colors
    var menuOpen by remember { mutableStateOf(false) }
    Column(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(vertical = dimensionResource(R.dimen.sf_space_s)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painterResource(CategoryAppearance.icon(category.iconName)),
                contentDescription = null,
                Modifier
                    .size(40.dp)
                    .padding(dimensionResource(R.dimen.sf_space_s)),
            )
            Text(category.name, Modifier.weight(1f), color = colors.textHeading, style = MaterialTheme.typography.bodyLarge)
            if (badge != null) {
                Text(
                    badge,
                    Modifier
                        .background(colors.violetTint, MaterialTheme.shapes.small)
                        .padding(horizontal = dimensionResource(R.dimen.sf_space_s), vertical = dimensionResource(R.dimen.sf_space_xs)),
                    color = colors.textMuted,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }, enabled = actionsEnabled) {
                    Icon(
                        painterResource(R.drawable.ic_more_mono),
                        contentDescription = stringResource(R.string.category_actions, category.name),
                        Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                DropdownMenu(expanded = menuOpen && actionsEnabled, onDismissRequest = { menuOpen = false }) {
                    actions.forEach { action ->
                        DropdownMenuItem(
                            text = { Text(stringResource(action.label)) },
                            onClick = {
                                menuOpen = false
                                onAction(action)
                            },
                        )
                    }
                }
            }
        }
        if (cap != null) com.example.spendflow.core.ui.components.LabeledProgress(
            current = cap.spent, target = cap.limit,
            label = stringResource(R.string.transaction_cap_progress, category.name),
            modifier = Modifier.fillMaxWidth(),
            showCard = false, showDecimals = true,
            tone = if (cap.spent.amountMinor > cap.limit.amountMinor) com.example.spendflow.core.ui.components.ProgressTone.OVER_CAP
                else com.example.spendflow.core.ui.components.ProgressTone.VIOLET,
        )
    }
}

@ThemePreviews
@Composable
private fun CategoriesScreenPreview() = SpendFlowTheme {
    val expenses = listOf(
        Category("food", "Groceries", CategoryType.EXPENSE, iconName = "food", classification = "NEEDS"),
        Category("fun", "Eating out", CategoryType.EXPENSE, iconName = "shopping", classification = "WANTS"),
        Category("old", "Gym", CategoryType.EXPENSE, iconName = "health", isArchived = true),
    )
    CategoriesScreen(
        tab = CategoriesTab.CATEGORIES,
        expenses = UiState.Success(expenses),
        incomeSources = UiState.Loading,
        expenseBusy = false,
        incomeBusy = false,
        onBack = {}, onSelectTab = {}, onAddCategory = {}, onAddIncomeSource = {},
        onExpenseAction = { _, _ -> }, onIncomeAction = { _, _ -> }, onReorder = {}, onRetry = {},
    )
}

@ThemePreviews
@Composable
private fun IncomeSourcesScreenPreview() = SpendFlowTheme {
    CategoriesScreen(
        tab = CategoriesTab.INCOME_SOURCES,
        expenses = UiState.Loading,
        incomeSources = UiState.Success(listOf(Category("salary", "Salary", CategoryType.INCOME, defaultCurrency = "LKR"))),
        expenseBusy = false,
        incomeBusy = false,
        onBack = {}, onSelectTab = {}, onAddCategory = {}, onAddIncomeSource = {},
        onExpenseAction = { _, _ -> }, onIncomeAction = { _, _ -> }, onReorder = {}, onRetry = {},
    )
}
