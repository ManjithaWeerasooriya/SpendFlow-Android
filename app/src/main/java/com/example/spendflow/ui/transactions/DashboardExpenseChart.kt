package com.example.spendflow.ui.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.example.spendflow.R
import com.example.spendflow.core.ui.UiState
import com.example.spendflow.core.ui.components.AmountFormatter
import com.example.spendflow.core.ui.components.DonutChart
import com.example.spendflow.core.ui.components.DonutSlice
import com.example.spendflow.core.ui.components.PercentFormatter
import com.example.spendflow.core.ui.messageRes
import com.example.spendflow.ui.theme.SpendFlowTheme
import java.time.YearMonth

@Composable
internal fun DashboardExpenseChart(selectedMonth: YearMonth, state: DashboardExpenseState, onViewAnalytics: (YearMonth) -> Unit) {
    val colors = SpendFlowTheme.colors
    val locale = LocalLocale.current.platformLocale
    // Same contrast-aware theme tokens as Analytics, assigned by stable ID rather than rank.
    val palette = listOf(colors.violet, colors.income, colors.info, colors.warning, colors.overCap)
    Column(Modifier.fillMaxWidth().testTag("dashboard_expenses")) {
        SectionHeader(R.string.dashboard_expense_title, R.string.dashboard_view_analytics) { onViewAnalytics(selectedMonth) }
        val content = if (state.month == selectedMonth) state.content else UiState.Loading
        when (content) {
            UiState.Loading -> ChartStatus(stringResource(R.string.dashboard_expense_loading))
            is UiState.Error -> ChartStatus(stringResource(content.error.messageRes))
            is UiState.Success -> {
                val data = content.data
                if (data.total.amountMinor == 0L) {
                    ChartStatus(stringResource(R.string.dashboard_expense_none))
                } else {
                    Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                        DonutChart(
                            data.slices.map { DonutSlice(it.fraction.toDouble(), palette[it.colorIndex]) },
                            colors.tile, selected = null,
                            modifier = Modifier.fillMaxSize().testTag("dashboard_expense_donut").clearAndSetSemantics {},
                        )
                        Text(
                            AmountFormatter.format(data.total, showDecimals = true, locale = locale),
                            color = colors.textHeading, style = MaterialTheme.typography.titleLarge,
                        )
                    }
                    if (data.metadataLoading) ChartStatus(stringResource(R.string.dashboard_expense_names_loading))
                    data.metadataError?.let { error ->
                        ChartStatus(stringResource(R.string.dashboard_expense_names_error, stringResource(error.messageRes)))
                    }
                    data.slices.forEach { slice ->
                        val label = when {
                            slice.categoryId == null -> stringResource(R.string.analytics_uncategorized)
                            slice.label == ExpenseCategoryLabel.ARCHIVED -> stringResource(R.string.dashboard_expense_archived, slice.name.orEmpty())
                            slice.name != null -> slice.name
                            slice.label == ExpenseCategoryLabel.UNKNOWN -> stringResource(R.string.dashboard_expense_unknown, slice.categoryId)
                            else -> stringResource(R.string.dashboard_expense_pending, slice.categoryId)
                        }
                        val amount = AmountFormatter.format(slice.amount, showDecimals = true, locale = locale)
                        val percent = PercentFormatter.formatFraction(slice.fraction, locale)
                        val description = stringResource(R.string.dashboard_expense_legend, label, amount, percent)
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = dimensionResource(R.dimen.sf_space_s))
                                .testTag("dashboard_expense_row").clearAndSetSemantics { contentDescription = description },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(12.dp).background(palette[slice.colorIndex], CircleShape))
                            Text(label, Modifier.weight(1f).padding(horizontal = dimensionResource(R.dimen.sf_space_s)),
                                color = colors.textHeading, style = MaterialTheme.typography.bodyMedium)
                            Column(horizontalAlignment = Alignment.End) {
                                Text(amount, color = colors.textHeading, style = MaterialTheme.typography.bodyMedium)
                                Text(percent, color = colors.textMuted, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChartStatus(text: String) {
    Text(text, Modifier.padding(vertical = dimensionResource(R.dimen.sf_space_s)).semantics { liveRegion = LiveRegionMode.Polite },
        color = SpendFlowTheme.colors.textMuted, style = MaterialTheme.typography.bodyMedium)
}
