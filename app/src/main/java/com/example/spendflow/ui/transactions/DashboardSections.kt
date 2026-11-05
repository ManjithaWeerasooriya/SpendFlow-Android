package com.example.spendflow.ui.transactions

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.example.spendflow.R
import com.example.spendflow.core.ui.UiState
import com.example.spendflow.core.ui.components.*
import com.example.spendflow.core.ui.messageRes
import com.example.spendflow.domain.model.SavingsGoal
import com.example.spendflow.ui.theme.SpendFlowTheme
import java.math.BigDecimal
import java.time.YearMonth

@Composable
internal fun DashboardGoal(state: UiState<SavingsGoal?>) {
    when (state) {
        UiState.Loading -> SectionStatus(stringResource(R.string.goals_loading))
        is UiState.Error -> SectionStatus(stringResource(state.error.messageRes))
        is UiState.Success -> state.data?.let { goal ->
            LabeledProgress(
                goal.saved, goal.target,
                if (goal.isCompleted) stringResource(R.string.goal_completed_label, goal.name) else goal.name,
                Modifier.fillMaxWidth().testTag("dashboard_goal"), showDecimals = true,
            )
        } ?: EmptyState(
            title = stringResource(R.string.dashboard_goals_empty),
            message = stringResource(R.string.dashboard_goals_empty_hint),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
internal fun DashboardCapsSection(selectedMonth: YearMonth, state: DashboardCapsState, onManage: () -> Unit) {
    Column(Modifier.fillMaxWidth().testTag("dashboard_caps")) {
        SectionHeader(R.string.dashboard_caps_title, R.string.dashboard_manage_caps, onManage)
        // The two StateFlows may recompose separately. Never show old caps under a new month.
        val content = if (state.month == selectedMonth) state.content else DashboardCapsContent.Loading
        when (content) {
            DashboardCapsContent.Loading -> SectionStatus(stringResource(R.string.category_cap_loading))
            DashboardCapsContent.Historical -> SectionStatus(stringResource(R.string.dashboard_caps_historical))
            is DashboardCapsContent.Error -> SectionStatus(stringResource(content.error.messageRes))
            is DashboardCapsContent.Ready -> {
                val rows = content.previews
                if (rows.overall == null && rows.categories.isEmpty()) {
                    EmptyState(
                        title = stringResource(R.string.dashboard_caps_empty),
                        message = stringResource(R.string.dashboard_caps_empty_hint),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                rows.overall?.let { cap ->
                    SectionStatus(stringResource(R.string.dashboard_overall_cap))
                    CapRow(cap)
                }
                rows.categories.forEach { CapRow(it) }
            }
        }
    }
}

@Composable
private fun CapRow(cap: DashboardCap) {
    val locale = LocalLocale.current.platformLocale
    Column(Modifier.padding(vertical = dimensionResource(R.dimen.sf_space_s)).testTag("dashboard_cap_row")) {
        cap.categoryLabel?.let { label ->
            SectionStatus(stringResource(if (label == CapCategoryLabel.ARCHIVED) R.string.dashboard_cap_archived else R.string.dashboard_cap_missing))
        }
        val fraction = cap.fraction
        val limit = cap.limit
        if (fraction != null && limit != null) {
            val over = fraction > BigDecimal.ONE
            LabeledProgress(
                current = cap.spending, target = limit, label = cap.name,
                modifier = Modifier.fillMaxWidth(), showDecimals = true, fraction = fraction,
                tone = if (over) ProgressTone.OVER_CAP else ProgressTone.VIOLET,
                status = if (over) stringResource(
                    R.string.dashboard_cap_over,
                    AmountFormatter.format(cap.spending - limit, showDecimals = true, locale = locale),
                ) else null,
            )
        } else {
            Text(cap.name, color = SpendFlowTheme.colors.textHeading, style = MaterialTheme.typography.titleSmall)
            SectionStatus(stringResource(R.string.dashboard_cap_spending, AmountFormatter.format(cap.spending, true, locale = locale)))
            limit?.let { SectionStatus(stringResource(R.string.dashboard_cap_limit, AmountFormatter.format(it, true, locale = locale))) }
            SectionStatus(stringResource(when (cap.issue) {
                CapIssue.DUPLICATE -> R.string.dashboard_cap_duplicate
                CapIssue.START_DATE -> R.string.dashboard_cap_start_date
                CapIssue.CURRENCY -> R.string.dashboard_cap_currency
                CapIssue.INVALID_LIMIT -> R.string.dashboard_cap_invalid
                CapIssue.CATEGORY_TYPE, null -> R.string.dashboard_cap_information
            }))
        }
    }
}

@Composable
private fun SectionStatus(text: String) {
    Text(
        text,
        Modifier.padding(vertical = dimensionResource(R.dimen.sf_space_s)).semantics { liveRegion = LiveRegionMode.Polite },
        color = SpendFlowTheme.colors.textMuted,
        style = MaterialTheme.typography.bodyMedium,
    )
}
