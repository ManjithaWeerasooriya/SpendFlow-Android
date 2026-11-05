package com.example.spendflow.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.spendflow.R
import com.example.spendflow.domain.model.Money
import com.example.spendflow.ui.theme.SpendFlowTheme

enum class ProgressTone { VIOLET, INFO, WARNING, OVER_CAP }

/**
 * Progress bar with "current / target" above and a label plus percentage below.
 * Used for savings goals and spending caps; [status] shows a warning line such as "Over cap by LKR 500".
 */
@Composable
fun LabeledProgress(
    current: Money,
    target: Money,
    label: String,
    modifier: Modifier = Modifier,
    status: String? = null,
    tone: ProgressTone = ProgressTone.VIOLET,
    showCard: Boolean = true,
    showDecimals: Boolean = false,
) {
    val colors = SpendFlowTheme.colors
    val percent = progressPercent(current, target)
    val percentText = stringResource(R.string.percent, percent)
    val shape = MaterialTheme.shapes.medium
    val container = if (showCard) {
        Modifier
            .background(colors.insetCard, shape)
            .border(dimensionResource(R.dimen.sf_stroke_hairline), MaterialTheme.colorScheme.outlineVariant, shape)
            .padding(dimensionResource(R.dimen.sf_space_l))
    } else {
        Modifier
    }
    Column(modifier.then(container)) {
        Row {
            Text(
                AmountFormatter.format(current, showDecimals = showDecimals),
                Modifier.weight(1f),
                color = colors.textHeading,
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                AmountFormatter.format(target, showDecimals = showDecimals),
                color = colors.textMuted,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        LinearProgressIndicator(
            progress = { percent.coerceIn(0, 100) / 100f },
            modifier = Modifier
                .padding(vertical = dimensionResource(R.dimen.sf_space_m))
                .fillMaxWidth()
                .height(dimensionResource(R.dimen.sf_progress_track))
                .semantics { contentDescription = "$label, $percentText" },
            color = when (tone) {
                ProgressTone.VIOLET -> colors.violet
                ProgressTone.INFO -> colors.info
                ProgressTone.WARNING -> colors.warning
                ProgressTone.OVER_CAP -> colors.overCap
            },
            trackColor = colors.tile,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
        Row {
            Text(label, Modifier.weight(1f), color = colors.textHeading, style = MaterialTheme.typography.bodyLarge)
            Text(percentText, color = colors.textHeading, style = MaterialTheme.typography.titleSmall)
        }
        if (status != null) {
            Row(Modifier.padding(top = dimensionResource(R.dimen.sf_space_s)), verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_warning), contentDescription = null, tint = colors.overCapText)
                Spacer(Modifier.width(dimensionResource(R.dimen.sf_space_s)))
                Text(status, color = colors.overCapText, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@ThemePreviews
@Composable
private fun LabeledProgressPreview() = ComponentPreview {
    Column {
        LabeledProgress(Money(240_000_00, "LKR"), Money(500_000_00, "LKR"), "LKR 260,000 to go")
        Spacer(Modifier.height(16.dp))
        LabeledProgress(
            Money(20_500_00, "LKR"), Money(20_000_00, "LKR"), "Groceries",
            status = "Over cap by LKR 500", tone = ProgressTone.OVER_CAP,
        )
    }
}
