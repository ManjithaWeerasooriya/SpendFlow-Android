package com.example.spendflow.core.ui.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.example.spendflow.R
import com.example.spendflow.domain.model.Money
import com.example.spendflow.domain.model.MonthlySummary
import com.example.spendflow.ui.theme.SpendFlowTheme
import java.time.YearMonth

/**
 * Monthly income, expenses and net savings on the dark hero card, with the savings rate pill.
 * [showExpenseRatioBar] replaces the "x% of income" caption with a bar, as on Analytics.
 * [showRatios] lets the dashboard replace unavailable percentage indicators with an explanation.
 */
@Composable
fun SummaryCard(
    summary: MonthlySummary,
    modifier: Modifier = Modifier,
    showExpenseRatioBar: Boolean = false,
    showRatios: Boolean = true,
) {
    val colors = SpendFlowTheme.colors
    val locale = LocalLocale.current.platformLocale
    val shape = MaterialTheme.shapes.large
    val unavailable = stringResource(R.string.summary_ratio_unavailable)
    val ratio = summary.expenseToIncomeRatio
    val saved = summary.savingsRate?.let { PercentFormatter.formatFraction(it, locale) }
    Column(
        modifier
            .background(Brush.linearGradient(listOf(colors.heroStart, colors.heroEnd)), shape)
            .padding(dimensionResource(R.dimen.sf_space_xl)),
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Metric(
                R.string.summary_income, R.drawable.ic_badge_income, summary.totalIncome,
                caption = null, modifier = Modifier.weight(1f),
            )
            VerticalDivider(
                Modifier
                    .padding(horizontal = dimensionResource(R.dimen.sf_space_l))
                    .fillMaxHeight(),
                thickness = dimensionResource(R.dimen.sf_stroke_hairline),
                color = colors.heroPill,
            )
            Metric(
                R.string.summary_expenses, R.drawable.ic_badge_expense, summary.totalExpenses,
                caption = if (showExpenseRatioBar || !showRatios) null else stringResource(
                    R.string.summary_spent_ratio,
                    ratio?.let { PercentFormatter.formatFraction(it, locale) } ?: unavailable,
                ),
                modifier = Modifier.weight(1f),
            )
        }
        HorizontalDivider(
            Modifier.padding(vertical = dimensionResource(R.dimen.sf_space_l)),
            thickness = dimensionResource(R.dimen.sf_stroke_hairline),
            color = colors.heroPill,
        )
        val netLabel = stringResource(R.string.summary_net_savings)
        Text(netLabel, Modifier.clearAndSetSemantics {}, color = colors.textOnDarkMuted, style = MaterialTheme.typography.bodyLarge)
        val netText = AmountFormatter.format(summary.netSavings, showDecimals = true, locale = locale)
        val netDescription = stringResource(R.string.summary_metric_value, netLabel, netText)
        AmountText(
            summary.netSavings,
            Modifier.clearAndSetSemantics { contentDescription = netDescription },
            size = AmountSize.TITLE, tone = AmountTone.ON_DARK, showDecimals = true, maxLines = 2,
        )
        if (showRatios) {
            val savedDescription = if (saved == null) {
                stringResource(R.string.summary_savings_rate_unavailable)
            } else {
                stringResource(R.string.summary_savings_rate_value, saved)
            }
            Row(
                Modifier
                    .padding(top = dimensionResource(R.dimen.sf_space_s))
                    .background(colors.heroPill, CircleShape)
                    .padding(horizontal = dimensionResource(R.dimen.sf_space_s), vertical = dimensionResource(R.dimen.sf_space_xs))
                    .clearAndSetSemantics { contentDescription = savedDescription },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.sf_space_s)),
            ) {
                Box(Modifier.size(8.dp).background(colors.income, CircleShape))
                Text(
                    stringResource(R.string.summary_saved_ratio, saved ?: unavailable),
                    color = colors.textOnDark,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (showExpenseRatioBar) ExpenseRatio(ExpenseRatioDisplay.from(ratio, locale), unavailable)
        }
    }
}

@Composable
private fun Metric(@StringRes label: Int, @DrawableRes badge: Int, amount: Money, caption: String?, modifier: Modifier) {
    val colors = SpendFlowTheme.colors
    val locale = LocalLocale.current.platformLocale
    val labelText = stringResource(label)
    val description = stringResource(R.string.summary_metric_value, labelText, AmountFormatter.format(amount, showDecimals = true, locale = locale))
    Column(modifier) {
        Row(Modifier.clearAndSetSemantics {}, verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(badge), contentDescription = null)
            Spacer(Modifier.width(dimensionResource(R.dimen.sf_space_m)))
            Text(labelText, color = colors.textOnDarkMuted, style = MaterialTheme.typography.bodyLarge)
        }
        AmountText(
            amount,
            Modifier
                .padding(top = dimensionResource(R.dimen.sf_space_s))
                .clearAndSetSemantics { contentDescription = description },
            size = AmountSize.INLINE, tone = AmountTone.ON_DARK, showDecimals = true, maxLines = 2,
        )
        if (!caption.isNullOrEmpty()) {
            Text(
                caption,
                Modifier.padding(top = dimensionResource(R.dimen.sf_space_xs)),
                color = colors.textOnDarkMuted,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ExpenseRatio(display: ExpenseRatioDisplay?, unavailable: String) {
    val colors = SpendFlowTheme.colors
    val label = stringResource(R.string.summary_expense_to_income_value, display?.percent ?: unavailable)
    // Announce the real value, not the capped percentage of the visual bar.
    val description = if (display == null) stringResource(R.string.summary_expense_to_income_unavailable) else label
    Column(
        Modifier
            .padding(top = dimensionResource(R.dimen.sf_space_l))
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = description },
    ) {
        Text(label, color = colors.textOnDark, style = MaterialTheme.typography.bodyMedium)
        if (display != null) {
            LinearProgressIndicator(
                progress = { display.progress / 100f },
                modifier = Modifier
                    .padding(top = dimensionResource(R.dimen.sf_space_s))
                    .fillMaxWidth()
                    .height(dimensionResource(R.dimen.sf_progress_track)),
                color = if (display.overIncome) colors.overCap else colors.textOnDark,
                trackColor = colors.heroPill,
                strokeCap = StrokeCap.Round,
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
        }
    }
}

@ThemePreviews
@Composable
private fun SummaryCardPreview() = ComponentPreview {
    val summary = MonthlySummary(
        month = YearMonth.of(2026, 10),
        totalIncome = Money(185_000_00, "LKR"),
        totalExpenses = Money(112_400_00, "LKR"),
        incomeBySource = mapOf("salary" to Money(150_000_00, "LKR"), "freelance" to Money(35_000_00, "LKR")),
    )
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SummaryCard(summary)
        SummaryCard(summary.copy(totalExpenses = Money(210_000_00, "LKR")), showExpenseRatioBar = true)
        SummaryCard(summary.copy(totalIncome = Money(0, "LKR")))
    }
}
