package com.example.spendflow.ui.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.spendflow.R
import com.example.spendflow.core.ui.components.AmountFormatter
import com.example.spendflow.core.ui.components.EmptyState
import com.example.spendflow.core.ui.components.ErrorState
import com.example.spendflow.core.ui.components.SummaryCard
import com.example.spendflow.core.ui.components.ThemePreviews
import com.example.spendflow.core.ui.messageRes
import com.example.spendflow.domain.model.Money
import com.example.spendflow.domain.model.TransactionType
import com.example.spendflow.ui.theme.SpendFlowTheme
import java.text.NumberFormat
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.compose.ui.platform.LocalLocale
import java.io.Serializable
import kotlin.math.abs

/** Test tags for the instrumented flow test, which drives Analytics through MainActivity. */
internal object AnalyticsTags {
    const val REPORT = "analytics_report"
    const val EMPTY = "analytics_empty"
    const val INCOME_REQUIRED = "analytics_income_required"
    const val BREAKDOWN_AMOUNT = "analytics_breakdown_amount"
    const val BREAKDOWN_EXPENSES = "analytics_breakdown_expenses"
    const val BREAKDOWN_INCOME = "analytics_breakdown_income"
    const val CATEGORY_ROW = "analytics_category_row"
    const val DAILY_DETAILS = "analytics_daily_details"
}

@Composable
internal fun AnalyticsRoute(model: AnalyticsViewModel, onAddTransaction: () -> Unit) {
    val state by model.uiState.collectAsStateWithLifecycle()
    AnalyticsScreen(
        state = state,
        zone = model.zone,
        onPreviousMonth = { model.moveMonth(-1) },
        onNextMonth = { model.moveMonth(1) },
        onIncomeMode = model::setIncomeMode,
        onAddTransaction = onAddTransaction,
        onRetry = model::retry,
    )
}

/**
 * One month's report: summary, category breakdown with drill-down, daily cash flow and insights.
 * The open dialog (a stable category ID, or the daily totals) is saved so it survives recreation.
 */
@Composable
internal fun AnalyticsScreen(
    state: AnalyticsUiState,
    zone: ZoneId,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onIncomeMode: (Boolean) -> Unit,
    onAddTransaction: () -> Unit,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit = {},
) {
    val colors = SpendFlowTheme.colors
    val locale = LocalLocale.current.platformLocale
    val monthLabel = state.month.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale))
    // The app shell already pads the NavHost for system bars and the bottom bar.
    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(
                start = dimensionResource(R.dimen.sf_screen_padding),
                end = dimensionResource(R.dimen.sf_screen_padding),
                top = dimensionResource(R.dimen.sf_space_xxl),
                bottom = dimensionResource(R.dimen.sf_space_xxxl),
            ),
    ) {
        Text(
            stringResource(R.string.nav_analytics),
            Modifier.semantics { heading() },
            color = colors.textHeading,
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            stringResource(R.string.analytics_subtitle),
            Modifier.padding(top = dimensionResource(R.dimen.sf_space_xs)),
            color = colors.textMuted,
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(
            Modifier.padding(vertical = dimensionResource(R.dimen.sf_space_l)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MonthButton(R.drawable.ic_chevron_left, R.string.analytics_previous_month, enabled = true, onPreviousMonth)
            Text(
                monthLabel,
                Modifier
                    .weight(1f)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                color = colors.textHeading,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            MonthButton(R.drawable.ic_chevron_right, R.string.analytics_next_month, enabled = state.canMoveNext, onNextMonth)
        }
        when (val content = state.content) {
            AnalyticsContent.Loading -> StatusText(stringResource(R.string.analytics_loading))
            is AnalyticsContent.Error -> ErrorState(stringResource(content.error.messageRes), onRetry, Modifier.fillMaxWidth())
            AnalyticsContent.Empty -> EmptyState(
                title = stringResource(R.string.analytics_empty_month_title),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(AnalyticsTags.EMPTY),
                icon = R.drawable.ic_transactions,
                message = stringResource(R.string.analytics_empty_month_message, monthLabel),
                actionText = stringResource(R.string.nav_add_transaction),
                onAction = onAddTransaction,
            )
            is AnalyticsContent.Populated -> Report(content, state.incomeMode, zone, onIncomeMode)
        }
    }
}

@Composable
private fun MonthButton(icon: Int, description: Int, enabled: Boolean, onClick: () -> Unit) {
    FilledTonalIconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(48.dp)) {
        Icon(painterResource(icon), stringResource(description))
    }
}

@Composable
private fun StatusText(text: String) {
    Text(
        text,
        Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        color = SpendFlowTheme.colors.textMuted,
        style = MaterialTheme.typography.bodyMedium,
    )
}

/** Null is a valid historical category ID, so selection needs its own presence marker. */
private data class CategorySelection(val categoryId: String?) : Serializable

@Composable
private fun Report(content: AnalyticsContent.Populated, income: Boolean, zone: ZoneId, onIncomeMode: (Boolean) -> Unit) {
    val report = content.report
    val colors = SpendFlowTheme.colors
    val locale = LocalLocale.current.platformLocale
    var drillDown by rememberSaveable(report.month, income) { mutableStateOf<CategorySelection?>(null) }
    var showDaily by rememberSaveable(report.month) { mutableStateOf(false) }
    fun formatted(minor: Long) = AmountFormatter.format(Money(minor, report.currency), showDecimals = true, locale = locale)
    val percent = NumberFormat.getPercentInstance(locale).apply { maximumFractionDigits = 1 }
    val categories = if (income) report.incomeBySource else report.expensesByCategory
    val total = if (income) report.incomeMinor else report.expenseMinor
    val uncategorized = stringResource(R.string.analytics_uncategorized)

    Column(Modifier.testTag(AnalyticsTags.REPORT)) {
        SummaryCard(report.summary, Modifier.fillMaxWidth(), showExpenseRatioBar = true)
        if (content.showIncomeRequired) {
            Text(
                stringResource(R.string.analytics_income_required),
                Modifier
                    .padding(top = dimensionResource(R.dimen.sf_space_m))
                    .testTag(AnalyticsTags.INCOME_REQUIRED),
                color = colors.textMuted,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Text(
            if (report.previousExpenseMinor == 0L) {
                stringResource(R.string.analytics_no_comparison)
            } else {
                val change = (report.expenseMinor - report.previousExpenseMinor).toDouble() / report.previousExpenseMinor
                stringResource(if (change > 0) R.string.analytics_spending_up else R.string.analytics_spending_down, percent.format(abs(change)))
            },
            Modifier.padding(vertical = dimensionResource(R.dimen.sf_space_l)),
            color = colors.textMuted,
            style = MaterialTheme.typography.bodyMedium,
        )
        InsetCard {
            CardTitle(if (income) R.string.analytics_income_breakdown else R.string.analytics_breakdown)
            BreakdownToggle(income, onIncomeMode)
            Box(
                Modifier
                    .padding(vertical = dimensionResource(R.dimen.sf_space_s))
                    .fillMaxWidth()
                    .height(240.dp),
                contentAlignment = Alignment.Center,
            ) {
                val selectedIndex = drillDown?.let { selection -> categories.indexOfFirst { it.categoryId == selection.categoryId }.takeIf { it >= 0 } }
                DonutChart(categories, selectedIndex, Modifier.fillMaxSize())
                Column(
                    Modifier.padding(horizontal = dimensionResource(R.dimen.sf_space_xxl)),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        AmountFormatter.parts(Money(total, report.currency), showDecimals = true, signed = false, locale = locale).number,
                        Modifier.testTag(AnalyticsTags.BREAKDOWN_AMOUNT),
                        color = colors.textHeading,
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        stringResource(if (income) R.string.analytics_received else R.string.analytics_spent, report.currency),
                        color = colors.textMuted,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            if (categories.isEmpty()) {
                Text(
                    stringResource(R.string.analytics_no_breakdown),
                    Modifier.fillMaxWidth(),
                    color = colors.textHeading,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }
            val palette = analyticsPalette()
            categories.forEachIndexed { index, category ->
                CategoryRow(
                    name = category.name ?: uncategorized,
                    count = category.count,
                    amount = formatted(category.amountMinor),
                    share = if (total > 0) category.amountMinor.toDouble() / total else 0.0,
                    color = palette[index % palette.size],
                    percent = percent,
                    onClick = { drillDown = CategorySelection(category.categoryId) },
                )
            }
            Text(
                stringResource(R.string.analytics_category_hint),
                Modifier.padding(top = dimensionResource(R.dimen.sf_space_s)),
                color = colors.textMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        InsetCard(Modifier.padding(top = dimensionResource(R.dimen.sf_space_l))) {
            CardTitle(R.string.analytics_cashflow)
            Text(
                stringResource(R.string.analytics_chart_legend),
                Modifier.padding(top = dimensionResource(R.dimen.sf_space_s)),
                color = colors.textMuted,
                style = MaterialTheme.typography.bodyMedium,
            )
            val cashflowDescription = stringResource(R.string.analytics_cashflow_accessibility, formatted(report.incomeMinor), formatted(report.expenseMinor))
            CashFlowChart(
                report.days,
                Modifier
                    .padding(top = dimensionResource(R.dimen.sf_space_l))
                    .fillMaxWidth()
                    .height(160.dp)
                    .semantics { contentDescription = cashflowDescription },
            )
            Text(
                stringResource(R.string.analytics_day_axis, report.month.lengthOfMonth()),
                Modifier.fillMaxWidth(),
                color = colors.textMuted,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            TextButton(
                onClick = { showDaily = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag(AnalyticsTags.DAILY_DETAILS),
                colors = ButtonDefaults.textButtonColors(contentColor = colors.violetText),
            ) { Text(stringResource(R.string.analytics_daily_details), style = MaterialTheme.typography.labelLarge) }
        }
        InsetCard(Modifier.padding(top = dimensionResource(R.dimen.sf_space_l))) {
            CardTitle(R.string.analytics_insights)
            InsightText(stringResource(R.string.analytics_daily_average, formatted(report.averageDailyExpenseMinor)), R.dimen.sf_space_l)
            InsightText(
                report.biggestSpendingDay?.let { day ->
                    stringResource(
                        R.string.analytics_biggest_day,
                        report.month.atDay(day).format(DateTimeFormatter.ofPattern("EEE, dd MMM", locale)),
                        formatted(report.days[day - 1].expenseMinor),
                    )
                } ?: stringResource(R.string.analytics_no_expenses),
                R.dimen.sf_space_m,
            )
            InsightText(stringResource(R.string.analytics_recorded, report.transactionCount), R.dimen.sf_space_m)
        }
        Text(
            stringResource(R.string.analytics_currency_note),
            Modifier.padding(top = dimensionResource(R.dimen.sf_space_l)),
            color = colors.textMuted,
            style = MaterialTheme.typography.bodySmall,
        )
    }

    drillDown?.let { selection -> categories.firstOrNull { it.categoryId == selection.categoryId } }?.let { category ->
        val name = category.name ?: uncategorized
        val type = if (income) TransactionType.INCOME else TransactionType.EXPENSE
        val dateFormat = DateTimeFormatter.ofPattern("dd MMM, HH:mm", locale)
        ListDialog(
            title = name,
            items = report.entries.filter { it.categoryId == category.categoryId && it.type == type }.map { entry ->
                val original = AmountFormatter.format(entry.amount, true, locale = locale)
                val converted = if (entry.amount.currencyCode != report.currency) " (${formatted(entry.baseAmount.amountMinor)})" else ""
                "${entry.dateTime.atZone(zone).format(dateFormat)}\n${entry.note?.takeIf { it.isNotBlank() } ?: name}\n$original$converted"
            },
            onDismiss = { drillDown = null },
        )
    }
    if (showDaily) {
        ListDialog(
            title = stringResource(R.string.analytics_cashflow),
            items = report.days.map { day ->
                stringResource(R.string.analytics_daily_detail, day.day, formatted(day.incomeMinor), formatted(day.expenseMinor))
            },
            onDismiss = { showDaily = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BreakdownToggle(income: Boolean, onIncomeMode: (Boolean) -> Unit) {
    val colors = SpendFlowTheme.colors
    SingleChoiceSegmentedButtonRow(
        Modifier
            .padding(top = dimensionResource(R.dimen.sf_space_m))
            .fillMaxWidth(),
    ) {
        listOf(false to R.string.summary_expenses, true to R.string.summary_income).forEachIndexed { i, (value, label) ->
            SegmentedButton(
                selected = income == value,
                onClick = { onIncomeMode(value) },
                shape = SegmentedButtonDefaults.itemShape(i, 2),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag(if (value) AnalyticsTags.BREAKDOWN_INCOME else AnalyticsTags.BREAKDOWN_EXPENSES),
                icon = {},
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = colors.tile,
                    activeContentColor = colors.textHeading,
                    activeBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    inactiveContainerColor = Color.Transparent,
                    inactiveContentColor = colors.textHeading,
                    inactiveBorderColor = MaterialTheme.colorScheme.outlineVariant,
                ),
            ) { Text(stringResource(label), style = MaterialTheme.typography.labelLarge) }
        }
    }
}

@Composable
private fun CategoryRow(
    name: String,
    count: Int,
    amount: String,
    share: Double,
    color: Color,
    percent: NumberFormat,
    onClick: () -> Unit,
) {
    val colors = SpendFlowTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .testTag(AnalyticsTags.CATEGORY_ROW)
            .clickable(onClick = onClick)
            .padding(vertical = dimensionResource(R.dimen.sf_space_m)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(10.dp)
                    .background(color, CircleShape),
            )
            Spacer(Modifier.width(dimensionResource(R.dimen.sf_space_m)))
            Column(Modifier.weight(1f)) {
                Text(name, color = colors.textHeading, style = MaterialTheme.typography.titleSmall)
                Text(
                    pluralStringResource(R.plurals.analytics_entry_count, count, count),
                    color = colors.textMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Column(Modifier.padding(start = dimensionResource(R.dimen.sf_space_s)), horizontalAlignment = Alignment.End) {
                Text(amount, color = colors.textHeading, style = MaterialTheme.typography.titleSmall)
                Text(percent.format(share), color = colors.textMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
        LinearProgressIndicator(
            progress = { share.toFloat() },
            modifier = Modifier
                .padding(top = dimensionResource(R.dimen.sf_space_s))
                .fillMaxWidth()
                .height(4.dp)
                .clearAndSetSemantics {},
            color = color,
            trackColor = colors.tile,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
    }
}

@Composable
private fun InsetCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = MaterialTheme.shapes.medium
    Column(
        modifier
            .fillMaxWidth()
            .background(SpendFlowTheme.colors.insetCard, shape)
            .border(dimensionResource(R.dimen.sf_stroke_hairline), MaterialTheme.colorScheme.outlineVariant, shape)
            .padding(dimensionResource(R.dimen.sf_space_l)),
        content = content,
    )
}

@Composable
private fun CardTitle(text: Int) {
    Text(
        stringResource(text),
        Modifier.semantics { heading() },
        color = SpendFlowTheme.colors.textHeading,
        style = MaterialTheme.typography.titleMedium,
    )
}

@Composable
private fun InsightText(text: String, top: Int) {
    Text(
        text,
        Modifier.padding(top = dimensionResource(top)),
        color = SpendFlowTheme.colors.textHeading,
        style = MaterialTheme.typography.bodyMedium,
    )
}

/** Read-only list with a Close button, for the category drill-down and the daily totals. */
@Composable
private fun ListDialog(title: String, items: List<String>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                items.forEach { Text(it, Modifier.padding(vertical = dimensionResource(R.dimen.sf_space_s))) }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, colors = ButtonDefaults.textButtonColors(contentColor = SpendFlowTheme.colors.violetText)) {
                Text(stringResource(R.string.action_close))
            }
        },
    )
}

@ThemePreviews
@Composable
private fun AnalyticsEmptyPreview() = SpendFlowTheme {
    AnalyticsScreen(
        AnalyticsUiState(YearMonth.of(2026, 10), canMoveNext = true, incomeMode = false, AnalyticsContent.Empty),
        ZoneId.of("UTC"), {}, {}, {}, {},
    )
}
