package com.example.spendflow.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.spendflow.R
import com.example.spendflow.domain.model.Budget
import com.example.spendflow.domain.model.BudgetPeriod
import com.example.spendflow.domain.model.CapLevel
import com.example.spendflow.domain.model.CapThresholds
import com.example.spendflow.domain.model.CapUsage
import com.example.spendflow.domain.model.CapWarning
import com.example.spendflow.domain.model.Money
import com.example.spendflow.ui.theme.SpendFlowTheme
import java.time.LocalDate
import java.time.YearMonth

/** Bar colour for a cap: violet while under the first threshold, orange from it, red at the cap. */
@Composable
fun capColor(level: CapLevel): Color = when (level) {
    CapLevel.UNDER -> SpendFlowTheme.colors.violet
    CapLevel.NOTICE, CapLevel.WARNING -> SpendFlowTheme.colors.warning
    CapLevel.OVER -> SpendFlowTheme.colors.overCap
}

/**
 * A cap's usage bar, full at the limit, with notches at the two warning thresholds. [gapColor] is the
 * surface behind it, so the notches read as gaps. Announced as "[label], N% of cap".
 */
@Composable
fun CapProgressBar(
    usage: CapUsage,
    label: String,
    modifier: Modifier = Modifier,
    gapColor: Color = SpendFlowTheme.colors.insetCard,
) {
    val spent = usage.spent?.amountMinor ?: 0L
    val limit = usage.cap.limit.amountMinor
    val percent = (spent * 100 / limit).toInt()
    val fraction = (spent.toFloat() / limit).coerceIn(0f, 1f)
    val track = SpendFlowTheme.colors.tile
    val fill = capColor(usage.level)
    val notches = listOf(usage.thresholds.notice, usage.thresholds.warning).map { it / 100f }
    val description = stringResource(R.string.cap_progress_description, label, percent)
    Canvas(
        modifier
            .fillMaxWidth()
            .height(dimensionResource(R.dimen.sf_progress_track))
            .semantics {
                contentDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
            },
    ) {
        val radius = CornerRadius(size.height / 2)
        drawRoundRect(track, cornerRadius = radius)
        if (fraction > 0f) drawRoundRect(fill, size = Size(size.width * fraction, size.height), cornerRadius = radius)
        val notch = 2.dp.toPx()
        notches.forEach { position ->
            drawRect(gapColor, topLeft = Offset(size.width * position - notch / 2, 0f), size = Size(notch, size.height))
        }
    }
}

/** "Over cap by LKR 500" with a warning icon, so the state never relies on colour alone (AC3). */
@Composable
fun OverCapLine(overBy: Money, modifier: Modifier = Modifier) {
    val locale = LocalLocale.current.platformLocale
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(R.drawable.ic_warning), contentDescription = null, tint = SpendFlowTheme.colors.overCapText)
        Spacer(Modifier.width(dimensionResource(R.dimen.sf_space_xs)))
        Text(
            stringResource(R.string.cap_over_by, AmountFormatter.format(overBy, locale = locale)),
            color = SpendFlowTheme.colors.overCapText,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** In-app cap warning (AC2), announced when it appears; [onDismiss] hides it for good. */
@Composable
fun CapWarningBanner(warning: CapWarning, categoryName: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val colors = SpendFlowTheme.colors
    val locale = LocalLocale.current.platformLocale
    val usage = warning.usage
    val over = warning.level == CapLevel.OVER
    val message = when {
        usage.overBy != null -> stringResource(R.string.cap_warning_over, categoryName, AmountFormatter.format(usage.overBy, locale = locale))
        over -> stringResource(R.string.cap_warning_reached, categoryName)
        else -> stringResource(
            R.string.cap_warning_percent,
            categoryName,
            if (warning.level == CapLevel.NOTICE) usage.thresholds.notice else usage.thresholds.warning,
        )
    }
    val tone = if (over) colors.overCapText else colors.warningText
    Row(
        modifier
            .fillMaxWidth()
            .background(colors.insetCard, MaterialTheme.shapes.medium)
            .padding(start = dimensionResource(R.dimen.sf_space_l))
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_warning), contentDescription = null, Modifier.size(20.dp), tint = tone)
        Spacer(Modifier.width(dimensionResource(R.dimen.sf_space_m)))
        Text(
            message,
            Modifier
                .weight(1f)
                .padding(vertical = dimensionResource(R.dimen.sf_space_m)),
            color = tone,
            style = MaterialTheme.typography.bodyMedium,
        )
        IconButton(onClick = onDismiss) {
            Icon(
                painterResource(R.drawable.ic_close),
                contentDescription = stringResource(R.string.cap_warning_dismiss),
                tint = colors.textMuted,
            )
        }
    }
}

private fun previewUsage(spent: Long, alerts: Set<CapLevel> = CapLevel.ALERTS) = CapUsage(
    "shopping",
    Budget("cap", "Shopping", Money(10_000_00, "LKR"), BudgetPeriod.MONTHLY, LocalDate.of(2026, 10, 1), "shopping", alerts),
    Money(spent, "LKR"),
    CapThresholds.DEFAULT,
)

@ThemePreviews
@Composable
private fun CapProgressPreview() = ComponentPreview {
    Column {
        CapProgressBar(previewUsage(5_700_00), "Bills")
        Spacer(Modifier.height(16.dp))
        CapProgressBar(previewUsage(7_500_00), "Groceries")
        Spacer(Modifier.height(16.dp))
        val over = previewUsage(10_500_00)
        CapProgressBar(over, "Shopping")
        OverCapLine(over.overBy!!, Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(16.dp))
        CapWarningBanner(CapWarning(YearMonth.of(2026, 10), CapLevel.OVER, over), "Shopping", onDismiss = {})
        Spacer(Modifier.height(8.dp))
        CapWarningBanner(CapWarning(YearMonth.of(2026, 10), CapLevel.WARNING, previewUsage(9_000_00)), "Groceries", onDismiss = {})
    }
}
