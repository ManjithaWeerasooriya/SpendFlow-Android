package com.example.spendflow.ui.more

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.example.spendflow.R
import com.example.spendflow.core.ui.components.ComponentPreview
import com.example.spendflow.core.ui.components.LinkRow
import com.example.spendflow.core.ui.components.SettingsDivider
import com.example.spendflow.core.ui.components.SettingsSection
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
    // The app shell already pads the NavHost for system bars and the bottom bar.
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
        SettingsSection(stringResource(R.string.more_section_manage)) {
            LinkRow(stringResource(R.string.budget_categories_title), onOpenCategories, icon = R.drawable.ic_budget_mono)
            SettingsDivider()
            LinkRow(stringResource(R.string.goals_title), onOpenGoals, icon = R.drawable.ic_savings_goal_mono)
        }
        SettingsSection(stringResource(R.string.more_section_app)) {
            LinkRow(stringResource(R.string.settings_title), onOpenSettings, icon = R.drawable.ic_settings_mono)
        }
        SettingsSection(stringResource(R.string.more_section_account)) {
            LinkRow(stringResource(R.string.log_out), onClick = { confirmingLogOut = true }, titleColor = colors.overCapText)
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

/** Shared with Settings, which offers Log out too. */
@Composable
internal fun LogOutDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
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
