package com.example.spendflow.ui.goals

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.example.spendflow.R
import com.example.spendflow.core.ui.components.AmountFormatter
import com.example.spendflow.core.ui.components.LabeledProgress
import com.example.spendflow.core.ui.components.SecondaryButton
import com.example.spendflow.core.ui.components.SpendFlowTopBar
import com.example.spendflow.core.ui.components.ThemePreviews
import com.example.spendflow.domain.model.Money
import com.example.spendflow.domain.model.SavingsGoal
import com.example.spendflow.ui.theme.SpendFlowTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val deadlineFormat = DateTimeFormatter.ofPattern("d MMM yyyy")

/** Stateless goal detail, shown in place of the list so the navigation graph is unchanged. */
@Composable
internal fun GoalDetailScreen(
    goal: SavingsGoal,
    today: LocalDate,
    lastMonth: Money?,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { SpendFlowTopBar(goal.name, onBack) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(dimensionResource(R.dimen.sf_screen_padding)),
        ) {
            GoalSummary(goal, today, lastMonth)
            SecondaryButton(
                stringResource(R.string.goal_edit),
                onClick = onEdit,
                modifier = Modifier
                    .padding(top = dimensionResource(R.dimen.sf_space_l))
                    .fillMaxWidth(),
            )
        }
    }
}

/** Progress, stat rows and the required-monthly card; shared by list rows and the detail. */
@Composable
internal fun GoalSummary(goal: SavingsGoal, today: LocalDate, lastMonth: Money?, modifier: Modifier = Modifier) {
    val colors = SpendFlowTheme.colors
    val locale = LocalLocale.current.platformLocale
    fun money(value: Money) = AmountFormatter.formatTrimmed(value, locale)
    Column(modifier) {
        LabeledProgress(
            goal.saved,
            goal.target,
            if (goal.isCompleted) stringResource(R.string.goal_completed_label, goal.name) else goal.name,
            Modifier.fillMaxWidth(),
        )
        Column(Modifier.padding(top = dimensionResource(R.dimen.sf_space_s))) {
            StatRow(stringResource(R.string.goal_stat_saved), money(goal.saved))
            StatRow(stringResource(R.string.goal_stat_remaining), money(goal.remaining))
            StatRow(stringResource(R.string.goal_stat_target), money(goal.target))
            val deadline = goal.deadline
            if (deadline != null) {
                val months = goal.monthsRemaining(today)
                StatRow(
                    stringResource(R.string.goal_stat_time),
                    when {
                        goal.isCompleted -> stringResource(R.string.goal_complete)
                        deadline.isBefore(today) -> stringResource(R.string.goal_past_deadline)
                        else -> pluralStringResource(R.plurals.goal_months_left, months ?: 1, months ?: 1)
                    },
                )
                StatRow(stringResource(R.string.goal_stat_deadline), deadline.format(deadlineFormat))
            }
        }
        val required = goal.requiredMonthly(today)
        if (required != null && !goal.isCompleted) {
            Column(
                Modifier
                    .padding(top = dimensionResource(R.dimen.sf_space_m))
                    .fillMaxWidth()
                    .background(colors.insetCard, MaterialTheme.shapes.medium)
                    .padding(dimensionResource(R.dimen.sf_space_l)),
            ) {
                Text(stringResource(R.string.goal_required_title), color = colors.textMuted, style = MaterialTheme.typography.bodyMedium)
                Text(money(required), color = colors.textHeading, style = MaterialTheme.typography.titleMedium)
                if (lastMonth != null && lastMonth.currencyCode == required.currencyCode) {
                    Text(
                        stringResource(R.string.goal_last_month, money(lastMonth)),
                        Modifier.padding(top = dimensionResource(R.dimen.sf_space_s)),
                        color = colors.textHeading,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    val short = required.amountMinor - lastMonth.amountMinor
                    Text(
                        if (short <= 0) stringResource(R.string.goal_on_track)
                        else stringResource(R.string.goal_short_by, money(Money(short, required.currencyCode))),
                        color = colors.textMuted,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    val colors = SpendFlowTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = dimensionResource(R.dimen.sf_space_xs)),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = colors.textMuted, style = MaterialTheme.typography.bodyMedium)
        Text(value, color = colors.textHeading, style = MaterialTheme.typography.bodyMedium)
    }
}

@ThemePreviews
@Composable
private fun GoalDetailPreview() = SpendFlowTheme {
    GoalDetailScreen(
        goal = SavingsGoal("mac", "MacBook", Money(500_000_00, "LKR"), Money(240_000_00, "LKR"), LocalDate.of(2027, 3, 31)),
        today = LocalDate.of(2026, 10, 11),
        lastMonth = Money(45_000_00, "LKR"),
        onBack = {}, onEdit = {},
    )
}
