package com.example.spendflow.core.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.spendflow.R
import com.example.spendflow.ui.theme.SpendFlowTheme

/** Icon, title, message and an optional primary action pointing to the next step. */
@Composable
fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    message: String? = null,
    actionText: String? = null,
    @DrawableRes actionIcon: Int? = null,
    onAction: () -> Unit = {},
) {
    val colors = SpendFlowTheme.colors
    Column(
        modifier.padding(dimensionResource(R.dimen.sf_space_xxl)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (icon != null) {
            Box(
                Modifier
                    .size(dimensionResource(R.dimen.sf_empty_icon_size))
                    .background(colors.violetTint, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Image(painterResource(icon), contentDescription = null, Modifier.size(32.dp))
            }
            Spacer(Modifier.height(dimensionResource(R.dimen.sf_space_xl)))
        }
        Text(title, color = colors.textHeading, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        if (!message.isNullOrEmpty()) {
            Spacer(Modifier.height(dimensionResource(R.dimen.sf_space_s)))
            Text(message, color = colors.textMuted, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        }
        if (!actionText.isNullOrEmpty()) {
            Spacer(Modifier.height(dimensionResource(R.dimen.sf_space_xxl)))
            PrimaryButton(actionText, onClick = onAction, icon = actionIcon)
        }
    }
}

@ThemePreviews
@Composable
private fun EmptyStatePreview() = ComponentPreview {
    EmptyState(
        title = "No transactions yet",
        icon = R.drawable.ic_transactions,
        message = "Entries you add for October will show up here.",
        actionText = "Add transaction",
        actionIcon = R.drawable.ic_plus,
    )
}
