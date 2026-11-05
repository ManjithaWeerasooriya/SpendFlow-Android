package com.example.spendflow.ui.transactions

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.unit.IntOffset
import com.example.spendflow.core.ui.EventEffect
import kotlin.math.roundToInt
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.spendflow.R
import com.example.spendflow.core.common.AppResult
import com.example.spendflow.core.ui.UiState
import com.example.spendflow.core.ui.components.AmountFormatter
import com.example.spendflow.core.ui.components.AmountSize
import com.example.spendflow.core.ui.components.AmountText
import com.example.spendflow.core.ui.components.AmountTone
import com.example.spendflow.core.ui.components.EmptyState
import com.example.spendflow.core.ui.components.CapProgressBar
import com.example.spendflow.core.ui.components.CapWarningBanner
import com.example.spendflow.core.ui.components.OverCapLine
import com.example.spendflow.core.ui.components.LabeledProgress
import com.example.spendflow.core.ui.components.SummaryCard
import com.example.spendflow.core.ui.components.ThemePreviews
import com.example.spendflow.core.ui.messageRes
import com.example.spendflow.domain.model.Money
import com.example.spendflow.domain.model.primaryGoal
import com.example.spendflow.domain.model.CapUsage
import com.example.spendflow.domain.model.CapWarning
import com.example.spendflow.ui.categories.CategoryAppearance
import com.example.spendflow.domain.model.MonthlySummary
import com.example.spendflow.domain.model.SavingsGoal
import com.example.spendflow.domain.model.TransactionType
import com.example.spendflow.ui.theme.SpendFlowTheme
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.compose.ui.platform.LocalLocale

/** Test tags for the instrumented flow tests, which drive these screens through MainActivity. */
internal object FeedTags {
    const val TITLE = "feed_title"
    const val TOTALS = "feed_totals"
    const val ROW = "feed_row"
    fun filter(value: String) = "feed_filter_$value"
}

internal data class FeedRow(
    val id: String,
    val initial: String,
    val title: String,
    val subtitle: String,
    val amount: Money,
    val type: TransactionType,
    val categoryName: String = title,
)

/** [filter] is "ALL", "EXPENSE" or "INCOME", as stored by [TransactionFeedViewModel]. */
internal data class FeedUiState(
    val rows: UiState<List<FeedRow>>,
    val monthly: MonthlySummary?,
    val goal: SavingsGoal?,
    val filter: String,
    val month: YearMonth,
    val caps: List<HomeCap> = emptyList(),
    val warning: Pair<CapWarning, String>? = null,
)

/** A row of Home's Spending caps. */
internal data class HomeCap(val name: String, val iconName: String?, val usage: CapUsage)

private val FILTERS = listOf(
    "ALL" to R.string.ui_all,
    "EXPENSE" to R.string.category_type_expense,
    "INCOME" to R.string.category_type_income,
)

@Composable
internal fun TransactionFeedRoute(
    model: TransactionFeedViewModel,
    home: Boolean,
    onSeeAll: () -> Unit,
    onViewGoals: () -> Unit,
    onEdit: (String) -> Unit = {},
    onManageCaps: () -> Unit = {},
) {
    val result by (if (home) model.recent else model.history).collectAsStateWithLifecycle()
    val monthly by model.monthly.collectAsStateWithLifecycle()
    val categories by model.categories.collectAsStateWithLifecycle()
    val filter by model.filter.collectAsStateWithLifecycle()
    val goals by model.goals.collectAsStateWithLifecycle()
    val capFlow = if (home) model.caps else remember { kotlinx.coroutines.flow.MutableStateFlow(null) }
    val caps by capFlow.collectAsStateWithLifecycle()
    val warnings by model.warnings.collectAsStateWithLifecycle()
    val incomeLabel = stringResource(R.string.category_type_income)
    val expenseLabel = stringResource(R.string.category_type_expense)
    val allCategories = (categories as? AppResult.Success)?.data.orEmpty()
    val names = allCategories.associate { it.id to it.name }
    val activeCategories = allCategories.filter { !it.isArchived }.associateBy { it.id }
    // Fullest caps first, so the ones closest to their limit lead
    val homeCaps = (caps as? AppResult.Success)?.data?.usages?.values.orEmpty()
        .mapNotNull { usage ->
            val category = activeCategories[usage.categoryId] ?: return@mapNotNull null
            if (usage.spent == null) null else HomeCap(category.name, category.iconName, usage)
        }
        .sortedByDescending { it.usage.spent!!.amountMinor.toDouble() / it.usage.cap.limit.amountMinor }
    // The most serious first; dismissing it reveals the next
    val warning = if (home) warnings.filter { it.usage.categoryId in activeCategories }.maxByOrNull { it.level } else null
    val locale = LocalLocale.current.platformLocale
    val dateFormat = DateTimeFormatter.ofPattern("dd MMM, HH:mm", locale)
    val rows = when (val current = result) {
        null -> UiState.Loading
        is AppResult.Error -> UiState.Error(current.error)
        is AppResult.Success -> UiState.Success(
            current.data
                .filter { home || filter == "ALL" || it.type.name == filter }
                .map { item ->
                    val category = names[item.categoryId] ?: if (item.type == TransactionType.INCOME) incomeLabel else expenseLabel
                    FeedRow(
                        id = item.id,
                        initial = category.take(1).uppercase(locale),
                        title = item.note?.takeIf { it.isNotBlank() } ?: category,
                        subtitle = category + " - " + item.dateTime.atZone(ZoneId.systemDefault()).format(dateFormat),
                        amount = if (item.type == TransactionType.EXPENSE) -item.amount else item.amount,
                        type = item.type,
                        categoryName = category,
                    )
                },
        )
    }
    var pendingDeleteId by rememberSaveable { mutableStateOf<String?>(null) }
    val deleting by model.deleting.collectAsStateWithLifecycle()
    val deleteError by model.deleteError.collectAsStateWithLifecycle()
    EventEffect(model.deletionEvents) { if (it is AppResult.Success) pendingDeleteId = null }
    val pendingRow = (rows as? UiState.Success)?.data?.firstOrNull { it.id == pendingDeleteId }
    if (pendingRow != null) TransactionDeleteDialog(
        row = pendingRow,
        deleting = deleting != null,
        error = deleteError?.messageRes,
        onCancel = { pendingDeleteId = null; model.clearDeleteError() },
        onConfirm = { model.deleteTransaction(pendingRow.id) },
    )
    TransactionFeedScreen(
        state = FeedUiState(
            rows = rows,
            monthly = (monthly as? AppResult.Success)?.data,
            goal = if (home) (goals as? AppResult.Success)?.data?.let(::primaryGoal) else null,
            filter = filter,
            month = model.currentMonth,
            caps = homeCaps,
            warning = warning?.let { it to activeCategories.getValue(it.usage.categoryId).name },
        ),
        home = home,
        onFilter = model::setFilter,
        onSeeAll = onSeeAll,
        onViewGoals = onViewGoals,
        onEdit = onEdit,
        onDelete = { pendingDeleteId = it.id; model.clearDeleteError() },
        onManageCaps = onManageCaps,
        onDismissWarning = { warning?.let(model::dismissWarning) },
    )
}

/**
 * Home (summary, recent entries, goal preview) and Transactions (monthly totals, type filter, full history)
 * share this screen; [home] picks the variant.
 */
@Composable
internal fun TransactionFeedScreen(
    state: FeedUiState,
    home: Boolean,
    onFilter: (String) -> Unit,
    onSeeAll: () -> Unit,
    onViewGoals: () -> Unit,
    modifier: Modifier = Modifier,
    onEdit: (String) -> Unit = {},
    onDelete: (FeedRow) -> Unit = {},
    onManageCaps: () -> Unit = {},
    onDismissWarning: () -> Unit = {},
) {
    val colors = SpendFlowTheme.colors
    val locale = LocalLocale.current.platformLocale
    val screenPadding = dimensionResource(R.dimen.sf_screen_padding)
    val rows = (state.rows as? UiState.Success)?.data.orEmpty()
    // The app shell already pads the NavHost for system bars and the bottom bar.
    LazyColumn(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(
            start = screenPadding,
            end = screenPadding,
            bottom = dimensionResource(R.dimen.sf_space_xxxl),
        ),
    ) {
        item(key = "header") {
            Column {
                Text(
                    stringResource(if (home) R.string.nav_home else R.string.nav_transactions),
                    Modifier
                        .padding(top = dimensionResource(R.dimen.sf_space_xxl))
                        .testTag(FeedTags.TITLE)
                        .semantics { heading() },
                    color = colors.textHeading,
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    if (home) state.month.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale))
                    else stringResource(R.string.ui_all_history),
                    Modifier.padding(top = dimensionResource(R.dimen.sf_space_xs), bottom = dimensionResource(R.dimen.sf_space_l)),
                    color = colors.textMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (home) {
                    state.warning?.let { (warning, name) ->
                        CapWarningBanner(warning, name, onDismissWarning, Modifier.padding(bottom = dimensionResource(R.dimen.sf_space_l)))
                    }
                    state.monthly?.let { SummaryCard(it, Modifier.fillMaxWidth()) }
                    SectionHeader(R.string.ui_recent_transactions, R.string.ui_see_all, onSeeAll)
                } else {
                    state.monthly?.let {
                        Text(
                            stringResource(
                                R.string.monthly_totals,
                                AmountFormatter.format(it.totalExpenses, showDecimals = true, locale = locale),
                                AmountFormatter.format(it.totalIncome, showDecimals = true, locale = locale),
                            ),
                            Modifier
                                .padding(vertical = dimensionResource(R.dimen.sf_space_xl))
                                .testTag(FeedTags.TOTALS),
                            color = colors.textHeading,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    FilterRow(state.filter, onFilter)
                }
                when (val status = state.rows) {
                    UiState.Loading -> StatusText(stringResource(R.string.analytics_loading))
                    is UiState.Error -> StatusText(stringResource(status.error.messageRes))
                    is UiState.Success -> if (status.data.isEmpty()) {
                        EmptyState(
                            title = stringResource(R.string.transactions_empty),
                            modifier = Modifier.fillMaxWidth(),
                            icon = R.drawable.ic_transactions,
                            message = stringResource(R.string.ui_empty_hint),
                        )
                    }
                }
            }
        }
        itemsIndexed(rows, key = { _, row -> row.id }) { index, row ->
            val corner = dimensionResource(R.dimen.sf_corner_inset)
            SwipeableTransactionRow(
                row = row,
                onEdit = { onEdit(row.id) },
                onDelete = { onDelete(row) },
                modifier = Modifier
                    .animateItem()
                    .background(
                        colors.insetCard,
                        RoundedCornerShape(
                            topStart = if (index == 0) corner else 0.dp,
                            topEnd = if (index == 0) corner else 0.dp,
                            bottomStart = if (index == rows.lastIndex) corner else 0.dp,
                            bottomEnd = if (index == rows.lastIndex) corner else 0.dp,
                        ),
                    ),
            )
        }
        if (home) {
            item(key = "goals") {
                Column {
                    SectionHeader(R.string.ui_savings_goals, R.string.ui_view_goals, onViewGoals)
                    state.goal?.let { goal ->
                        LabeledProgress(goal.saved, goal.target, goal.name, Modifier.fillMaxWidth(), showDecimals = true)
                    }
                }
            }
            item(key = "caps") {
                Column {
                    SectionHeader(R.string.home_spending_caps, R.string.home_manage_caps, onManageCaps)
                    if (state.caps.isEmpty()) {
                        StatusText(stringResource(R.string.home_caps_empty))
                    } else {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .background(colors.insetCard, RoundedCornerShape(dimensionResource(R.dimen.sf_corner_inset)))
                                .padding(dimensionResource(R.dimen.sf_space_l)),
                            verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.sf_space_xl)),
                        ) {
                            state.caps.forEach { HomeCapRow(it) }
                        }
                    }
                }
            }
        }
    }
}

/** 02a: icon, name, "10,500 / 10,000", the notched bar and, past the cap, "Over cap by LKR 500". */
@Composable
private fun HomeCapRow(cap: HomeCap) {
    val colors = SpendFlowTheme.colors
    val locale = LocalLocale.current.platformLocale
    val usage = cap.usage
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painterResource(CategoryAppearance.icon(cap.iconName)),
                contentDescription = null,
                Modifier
                    .size(40.dp)
                    .background(colors.tile, RoundedCornerShape(dimensionResource(R.dimen.sf_corner_tile)))
                    .padding(dimensionResource(R.dimen.sf_space_s)),
            )
            Text(
                cap.name,
                Modifier
                    .weight(1f)
                    .padding(horizontal = dimensionResource(R.dimen.sf_space_m)),
                color = colors.textHeading,
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                stringResource(
                    R.string.cap_compact,
                    AmountFormatter.parts(usage.spent!!, showDecimals = false, signed = false, locale = locale).number,
                    AmountFormatter.parts(usage.cap.limit, showDecimals = false, signed = false, locale = locale).number,
                ),
                color = colors.textHeading,
                style = MaterialTheme.typography.titleSmall,
            )
        }
        CapProgressBar(usage, cap.name, Modifier.padding(top = dimensionResource(R.dimen.sf_space_m)))
        usage.overBy?.let { OverCapLine(it, Modifier.padding(top = dimensionResource(R.dimen.sf_space_s))) }
    }
}

@Composable
private fun SectionHeader(@StringRes title: Int, @StringRes action: Int, onAction: () -> Unit) {
    Row(Modifier.padding(top = dimensionResource(R.dimen.sf_space_l)), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(title),
            Modifier
                .weight(1f)
                .semantics { heading() },
            color = SpendFlowTheme.colors.textHeading,
            style = MaterialTheme.typography.titleMedium,
        )
        TextButton(onClick = onAction, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(action), color = SpendFlowTheme.colors.violetText, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterRow(selected: String, onFilter: (String) -> Unit) {
    val colors = SpendFlowTheme.colors
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        FILTERS.forEachIndexed { i, (value, label) ->
            SegmentedButton(
                selected = selected == value,
                onClick = { onFilter(value) },
                shape = SegmentedButtonDefaults.itemShape(i, FILTERS.size),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag(FeedTags.filter(value)),
                icon = {},
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = colors.tile,
                    activeContentColor = colors.textHeading,
                    activeBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    inactiveContainerColor = Color.Transparent,
                    inactiveContentColor = colors.textHeading,
                    inactiveBorderColor = MaterialTheme.colorScheme.outlineVariant,
                ),
            ) {
                Text(stringResource(label), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun StatusText(text: String) {
    Text(
        text,
        Modifier
            .padding(vertical = dimensionResource(R.dimen.sf_space_s))
            .semantics { liveRegion = LiveRegionMode.Polite },
        color = SpendFlowTheme.colors.textMuted,
        style = MaterialTheme.typography.bodyMedium,
    )
}

/** Swiping reveals an action; it never removes a record by itself. */
@Composable
private fun SwipeableTransactionRow(row: FeedRow, onEdit: () -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    val revealWidth = with(LocalDensity.current) { 96.dp.toPx() }
    var offset by rememberSaveable(row.id) { mutableFloatStateOf(0f) }
    Box(modifier.clipToBounds()) {
        TextButton(
            onClick = onDelete,
            modifier = Modifier.align(Alignment.CenterEnd).width(96.dp).heightIn(min = 48.dp)
                .testTag("delete_${row.id}"),
        ) { Text(stringResource(R.string.transaction_delete), color = MaterialTheme.colorScheme.error) }
        val deleteLabel = stringResource(R.string.transaction_delete_action, row.categoryName)
        TransactionRow(row, Modifier
            .offset { IntOffset(offset.roundToInt(), 0) }
            .background(SpendFlowTheme.colors.insetCard)
            .draggable(
                state = rememberDraggableState { delta -> offset = (offset + delta).coerceIn(-revealWidth, 0f) },
                orientation = Orientation.Horizontal,
                onDragStopped = { offset = if (offset < -revealWidth / 2) -revealWidth else 0f },
            )
            .clickable(onClick = onEdit)
            .semantics { customActions = listOf(CustomAccessibilityAction(deleteLabel) { onDelete(); true }) },
        )
    }
}

@Composable
internal fun TransactionDeleteDialog(
    row: FeedRow,
    deleting: Boolean,
    @StringRes error: Int?,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    val locale = LocalLocale.current.platformLocale
    val digits = java.util.Currency.getInstance(row.amount.currencyCode).defaultFractionDigits.coerceAtLeast(0)
    val hasFraction = java.math.BigDecimal.valueOf(row.amount.amountMinor, digits).stripTrailingZeros().scale() > 0

    AlertDialog(
        onDismissRequest = { if (!deleting) onCancel() },
        title = { Text(stringResource(R.string.transaction_delete_confirm, row.categoryName,
            AmountFormatter.format(row.amount, showDecimals = hasFraction,
                signed = row.type != TransactionType.TRANSFER, locale = locale))) },
        text = { error?.let { StatusText(stringResource(it)) } },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !deleting) {
                Text(stringResource(R.string.transaction_delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel, enabled = !deleting) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun TransactionRow(row: FeedRow, modifier: Modifier = Modifier) {
    val colors = SpendFlowTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .testTag(FeedTags.ROW)
            .semantics(mergeDescendants = true) {}
            .padding(dimensionResource(R.dimen.sf_space_l)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .background(colors.violetTint, MaterialTheme.shapes.small)
                .clearAndSetSemantics {},
            contentAlignment = Alignment.Center,
        ) {
            Text(row.initial, color = colors.violetText, style = MaterialTheme.typography.titleMedium)
        }
        Column(
            Modifier
                .weight(1f)
                .padding(start = dimensionResource(R.dimen.sf_space_m)),
        ) {
            Text(
                row.title,
                color = colors.textHeading,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                row.subtitle,
                Modifier.padding(top = dimensionResource(R.dimen.sf_space_xs)),
                color = colors.textMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        AmountText(
            row.amount,
            Modifier
                .padding(start = dimensionResource(R.dimen.sf_space_s))
                .widthIn(max = 160.dp),
            size = AmountSize.INLINE,
            tone = if (row.type == TransactionType.INCOME) AmountTone.INCOME else AmountTone.NEUTRAL,
            showDecimals = true,
            signed = row.type != TransactionType.TRANSFER,
        )
    }
}

private val previewRows = listOf(
    FeedRow("1", "G", "Weekly shop", "Groceries - 04 Nov, 18:20", Money(-2_450_00, "LKR"), TransactionType.EXPENSE),
    FeedRow("2", "S", "Salary", "Salary - 01 Nov, 09:00", Money(185_000_00, "LKR"), TransactionType.INCOME),
)

@ThemePreviews
@Composable
private fun HomePreview() = SpendFlowTheme {
    TransactionFeedScreen(
        FeedUiState(
            rows = UiState.Success(previewRows),
            monthly = MonthlySummary(YearMonth.of(2026, 11), Money(185_000_00, "LKR"), Money(2_450_00, "LKR")),
            goal = SavingsGoal("laptop", "Laptop", Money(500_000_00, "LKR"), Money(240_000_00, "LKR")),
            filter = "ALL",
            month = YearMonth.of(2026, 11),
        ),
        home = true, onFilter = {}, onSeeAll = {}, onViewGoals = {},
    )
}

@ThemePreviews
@Composable
private fun TransactionsPreview() = SpendFlowTheme {
    TransactionFeedScreen(
        FeedUiState(
            rows = UiState.Success(previewRows),
            monthly = MonthlySummary(YearMonth.of(2026, 11), Money(185_000_00, "LKR"), Money(2_450_00, "LKR")),
            goal = null,
            filter = "EXPENSE",
            month = YearMonth.of(2026, 11),
        ),
        home = false, onFilter = {}, onSeeAll = {}, onViewGoals = {},
    )
}
