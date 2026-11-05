package com.example.spendflow.ui.more

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spendflow.R
import com.example.spendflow.core.ui.components.ComponentPreview
import com.example.spendflow.core.ui.components.ThemePreviews
import com.example.spendflow.ui.theme.SpendFlowTheme

@Composable
internal fun MoreRoute(
    viewModel: MoreViewModel,
    onOpenCategories: () -> Unit,
    onOpenGoals: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    MoreScreen(
        onOpenCategories = onOpenCategories,
        onOpenGoals = onOpenGoals,
        onOpenSettings = onOpenSettings,
        onLogOut = viewModel::logOut,
    )
}

/** Hub for the areas without their own tab, each one tap away. Log out asks for confirmation first. */
@Composable
internal fun MoreScreen(
    onOpenCategories: () -> Unit,
    onOpenGoals: () -> Unit,
    onOpenSettings: () -> Unit,
    onLogOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmingLogOut by rememberSaveable { mutableStateOf(false) }
    val colors = SpendFlowTheme.colors
    // MainActivity already pads the Fragment host for system bars and the bottom bar.
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                start = dimensionResource(R.dimen.sf_screen_padding),
                end = dimensionResource(R.dimen.sf_screen_padding),
                top = dimensionResource(R.dimen.sf_space_xxl),
                bottom = dimensionResource(R.dimen.sf_add_button_overhang),
            ),
    ) {
        Text(
            stringResource(R.string.nav_more),
            Modifier.semantics { heading() },
            color = colors.textHeading,
            style = MaterialTheme.typography.headlineMedium,
        )
        Section(R.string.more_section_manage) {
            LinkRow(R.string.budget_categories_title, R.drawable.ic_budget_mono, onOpenCategories)
            HorizontalDivider(
                Modifier.padding(start = 56.dp, end = dimensionResource(R.dimen.sf_space_l)),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            LinkRow(R.string.goals_title, R.drawable.ic_savings_goal_mono, onOpenGoals)
        }
        Section(R.string.more_section_app) {
            LinkRow(R.string.settings_title, R.drawable.ic_settings_mono, onOpenSettings)
        }
        Section(R.string.more_section_account) {
            LinkRow(R.string.log_out, icon = null, onClick = { confirmingLogOut = true }, titleColor = colors.overCapText)
        }
    }
    if (confirmingLogOut) {
        LogOutDialog(
            onConfirm = {
                confirmingLogOut = false
                onLogOut()
            },
            onDismiss = { confirmingLogOut = false },
        )
    }
}

@Composable
private fun Section(@StringRes title: Int, rows: @Composable ColumnScope.() -> Unit) {
    Text(
        stringResource(title).uppercase(),
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

@Composable
private fun LinkRow(
    @StringRes title: Int,
    @DrawableRes icon: Int?,
    onClick: () -> Unit,
    titleColor: Color = SpendFlowTheme.colors.textHeading,
) {
    val colors = SpendFlowTheme.colors
    Row(
        Modifier
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
                tint = colors.textHeading,
            )
            Spacer(Modifier.width(dimensionResource(R.dimen.sf_space_l)))
        }
        Text(
            stringResource(title),
            Modifier.weight(1f),
            color = titleColor,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
        )
        if (icon != null) {
            Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = colors.textMuted)
        }
    }
}

@Composable
private fun LogOutDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    // Violet is too dark as text on the dark dialog surface
    val buttonColors = ButtonDefaults.textButtonColors(contentColor = SpendFlowTheme.colors.violetText)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.log_out_confirm_title)) },
        text = { Text(stringResource(R.string.log_out_confirm_message)) },
        confirmButton = { TextButton(onClick = onConfirm, colors = buttonColors) { Text(stringResource(R.string.log_out)) } },
        dismissButton = { TextButton(onClick = onDismiss, colors = buttonColors) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@ThemePreviews
@Composable
private fun MoreScreenPreview() = SpendFlowTheme {
    Column(Modifier.background(SpendFlowTheme.colors.page)) {
        MoreScreen(onOpenCategories = {}, onOpenGoals = {}, onOpenSettings = {}, onLogOut = {})
    }
}

@ThemePreviews
@Composable
private fun LogOutDialogPreview() = ComponentPreview {
    LogOutDialog(onConfirm = {}, onDismiss = {})
}
