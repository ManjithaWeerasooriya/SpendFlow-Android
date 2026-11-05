package com.example.spendflow.ui.transactions

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.spendflow.R
import com.example.spendflow.core.ui.UiState
import com.example.spendflow.core.ui.components.EmptyState
import com.example.spendflow.core.ui.components.SummaryCard
import com.example.spendflow.core.ui.messageRes
import com.example.spendflow.ui.theme.SpendFlowTheme
import java.time.format.DateTimeFormatter

internal object DashboardTags {
    const val EMPTY = "dashboard_empty"
    const val SOURCES = "dashboard_sources"
    const val INCOME_REQUIRED = "dashboard_income_required"
}

@Composable
internal fun DashboardSummaryPanel(
    state: DashboardUiState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onAddTransaction: () -> Unit,
) {
    val monthLabel = state.month.format(DateTimeFormatter.ofPattern("MMMM yyyy", LocalLocale.current.platformLocale))
    val colors = SpendFlowTheme.colors
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(vertical = dimensionResource(R.dimen.sf_space_m)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalIconButton(onClick = onPrevious, modifier = Modifier.size(48.dp)) {
                Icon(painterResource(R.drawable.ic_chevron_left), stringResource(R.string.dashboard_previous_month))
            }
            Text(
                monthLabel,
                Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                color = colors.textHeading,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            FilledTonalIconButton(onClick = onNext, enabled = state.canMoveNext, modifier = Modifier.size(48.dp)) {
                Icon(painterResource(R.drawable.ic_chevron_right), stringResource(R.string.dashboard_next_month))
            }
        }
        when (val content = state.content) {
            UiState.Loading -> DashboardStatus(stringResource(R.string.dashboard_loading))
            is UiState.Error -> DashboardStatus(stringResource(content.error.messageRes))
            is UiState.Success -> {
                val data = content.data
                val hasIncome = data.summary.totalIncome.amountMinor != 0L
                SummaryCard(data.summary, Modifier.fillMaxWidth(), showRatios = hasIncome)
                Text(
                    pluralStringResource(R.plurals.analytics_source_count, data.incomeSourceCount, data.incomeSourceCount),
                    Modifier.padding(top = dimensionResource(R.dimen.sf_space_m)).testTag(DashboardTags.SOURCES),
                    color = colors.textHeading,
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (!data.hasTransactions) {
                    EmptyState(
                        title = stringResource(R.string.analytics_empty_month_title),
                        modifier = Modifier.fillMaxWidth().testTag(DashboardTags.EMPTY),
                        icon = R.drawable.ic_transactions,
                        message = stringResource(R.string.analytics_empty_month_message, monthLabel) +
                            if (state.canMoveNext) "\n" + stringResource(R.string.dashboard_historical_add_hint) else "",
                        actionText = stringResource(R.string.nav_add_transaction),
                        onAction = onAddTransaction,
                    )
                } else if (!hasIncome) {
                    DashboardStatus(stringResource(R.string.analytics_income_required), Modifier.testTag(DashboardTags.INCOME_REQUIRED))
                }
            }
        }
    }
}

@Composable
private fun DashboardStatus(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier.padding(vertical = dimensionResource(R.dimen.sf_space_s)).semantics { liveRegion = LiveRegionMode.Polite },
        color = SpendFlowTheme.colors.textMuted,
        style = MaterialTheme.typography.bodyMedium,
    )
}
