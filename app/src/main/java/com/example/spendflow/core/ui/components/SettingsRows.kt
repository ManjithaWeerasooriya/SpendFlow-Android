package com.example.spendflow.core.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spendflow.R
import com.example.spendflow.ui.theme.SpendFlowTheme

/** Uppercase heading over an inset card that groups [rows], as on More and Settings. */
@Composable
fun SettingsSection(title: String, modifier: Modifier = Modifier, rows: @Composable ColumnScope.() -> Unit) {
    Column(modifier) {
        Text(
            title.uppercase(),
            Modifier
                .padding(top = dimensionResource(R.dimen.sf_space_xxl), bottom = dimensionResource(R.dimen.sf_space_s))
                .semantics { heading() },
            color = SpendFlowTheme.colors.textMuted,
            style = MaterialTheme.typography.labelSmall,
        )
        Column(
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(SpendFlowTheme.colors.insetCard),
            content = rows,
        )
    }
}

/** Divider between rows of a [SettingsSection], inset past the row icons. */
@Composable
fun SettingsDivider() {
    HorizontalDivider(
        Modifier.padding(start = 56.dp, end = dimensionResource(R.dimen.sf_space_l)),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

/**
 * Tappable row inside a [SettingsSection]: icon, title, an optional current [value] and a chevron.
 * [iconTint] null draws a full-colour icon as is.
 */
@Composable
fun LinkRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    value: String? = null,
    titleColor: Color = SpendFlowTheme.colors.textHeading,
    iconTint: Color? = SpendFlowTheme.colors.textHeading,
    showChevron: Boolean = icon != null,
) {
    val colors = SpendFlowTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = dimensionResource(R.dimen.sf_link_row_height))
            .padding(horizontal = dimensionResource(R.dimen.sf_space_l)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                painterResource(icon),
                contentDescription = null,
                Modifier.size(dimensionResource(R.dimen.sf_icon_size)),
                tint = iconTint ?: Color.Unspecified,
            )
            Spacer(Modifier.width(dimensionResource(R.dimen.sf_space_l)))
        }
        Text(
            title,
            Modifier.weight(1f),
            color = titleColor,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
        )
        if (value != null) {
            Text(
                value,
                Modifier.padding(start = dimensionResource(R.dimen.sf_space_m)),
                color = colors.textMuted,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (showChevron) {
            Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = colors.textMuted)
        }
    }
}

@ThemePreviews
@Composable
private fun SettingsSectionPreview() = ComponentPreview {
    SettingsSection("General") {
        LinkRow("Base currency", onClick = {}, icon = R.drawable.ic_currency_exchange, value = "LKR", iconTint = null)
        SettingsDivider()
        LinkRow("Log out", onClick = {}, titleColor = SpendFlowTheme.colors.overCapText)
    }
}
