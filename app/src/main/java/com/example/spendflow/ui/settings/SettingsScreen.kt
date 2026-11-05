package com.example.spendflow.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.spendflow.R
import com.example.spendflow.core.ui.CurrencyOptions
import com.example.spendflow.core.ui.EventEffect
import com.example.spendflow.core.ui.UiState
import com.example.spendflow.core.ui.components.DropdownField
import com.example.spendflow.core.ui.components.EmptyState
import com.example.spendflow.core.ui.components.FormDialog
import com.example.spendflow.core.ui.components.LabeledSwitch
import com.example.spendflow.core.ui.components.LabeledTextField
import com.example.spendflow.core.ui.components.LinkRow
import com.example.spendflow.core.ui.components.SettingsDivider
import com.example.spendflow.core.ui.components.SettingsSection
import com.example.spendflow.core.ui.components.SpendFlowSnackbar
import com.example.spendflow.core.ui.components.SpendFlowSnackbarVisuals
import com.example.spendflow.core.ui.components.ThemePreviews
import com.example.spendflow.core.ui.messageRes
import com.example.spendflow.domain.model.CapThresholds
import com.example.spendflow.domain.model.UserPreferences
import com.example.spendflow.ui.more.LogOutDialog
import com.example.spendflow.ui.theme.SpendFlowTheme
import kotlinx.coroutines.launch

private enum class SettingsDialog { CURRENCY, THRESHOLDS, LOG_OUT }

@Composable
internal fun SettingsRoute(
    viewModel: SettingsViewModel,
    onOpenCategories: () -> Unit,
    onOpenGoals: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pendingCurrency by viewModel.pendingCurrency.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    EventEffect(viewModel.events) { event ->
        val visuals = when (event) {
            is SettingsEvent.CurrencyChanged -> SpendFlowSnackbarVisuals(
                resources.getString(R.string.settings_currency_changed, event.currencyCode),
                SpendFlowSnackbar.Tone.SUCCESS,
            )
            is SettingsEvent.ShowError -> SpendFlowSnackbarVisuals(
                resources.getString(event.error.messageRes),
                SpendFlowSnackbar.Tone.ERROR,
            )
        }
        scope.launch { snackbar.showSnackbar(visuals) }
    }
    SettingsScreen(
        state = state,
        pendingCurrency = pendingCurrency,
        snackbar = snackbar,
        actions = SettingsActions(
            onRetry = viewModel::retry,
            onStreakChange = viewModel::setStreakEnabled,
            onLockScreenChange = viewModel::setLockScreenQuickEntry,
            onSelectCurrency = viewModel::selectBaseCurrency,
            onConfirmCurrency = viewModel::confirmBaseCurrency,
            onDismissCurrency = viewModel::dismissBaseCurrency,
            onSaveThresholds = viewModel::saveCapThresholds,
            onOpenCategories = onOpenCategories,
            onOpenGoals = onOpenGoals,
            onLogOut = viewModel::logOut,
        ),
    )
}

internal class SettingsActions(
    val onRetry: () -> Unit = {},
    val onStreakChange: (Boolean) -> Unit = {},
    val onLockScreenChange: (Boolean) -> Unit = {},
    val onSelectCurrency: (String) -> Unit = {},
    val onConfirmCurrency: () -> Unit = {},
    val onDismissCurrency: () -> Unit = {},
    /** Returns the error to show in the form, or null once the thresholds are saved. */
    val onSaveThresholds: (String, String, String) -> Int? = { _, _, _ -> null },
    val onOpenCategories: () -> Unit = {},
    val onOpenGoals: () -> Unit = {},
    val onLogOut: () -> Unit = {},
)

/**
 * 09: General, Quick entry and alerts, and Manage under a page title, with the bottom bar (More selected) like
 * the More tab it opens from. Switches spell out ON / OFF next to their position.
 */
@Composable
internal fun SettingsScreen(
    state: UiState<SettingsContent>,
    pendingCurrency: String?,
    actions: SettingsActions,
    modifier: Modifier = Modifier,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    var dialog by rememberSaveable { mutableStateOf<SettingsDialog?>(null) }
    // The app shell already pads the NavHost for system bars, the bottom bar and the keyboard.
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) { SpendFlowSnackbar(it) } },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                UiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                is UiState.Error -> EmptyState(
                    title = stringResource(state.error.messageRes),
                    modifier = Modifier.align(Alignment.Center),
                    icon = R.drawable.ic_settings,
                    actionText = stringResource(R.string.action_retry),
                    onAction = actions.onRetry,
                )
                is UiState.Success -> SettingsList(state.data, actions, openDialog = { dialog = it })
            }
        }
    }

    val content = (state as? UiState.Success)?.data
    if (content != null) {
        when (dialog) {
            SettingsDialog.CURRENCY -> CurrencyDialog(
                current = content.baseCurrency,
                onSelect = {
                    dialog = null
                    actions.onSelectCurrency(it)
                },
                onDismiss = { dialog = null },
            )
            SettingsDialog.THRESHOLDS -> ThresholdsDialog(
                current = content.preferences.capThresholds,
                onSave = actions.onSaveThresholds,
                onDismiss = { dialog = null },
            )
            SettingsDialog.LOG_OUT -> LogOutDialog(
                onConfirm = {
                    dialog = null
                    actions.onLogOut()
                },
                onDismiss = { dialog = null },
            )
            null -> Unit
        }
        if (pendingCurrency != null) {
            CurrencyChangeDialog(content.baseCurrency, pendingCurrency, actions.onConfirmCurrency, actions.onDismissCurrency)
        }
    }
}

@Composable
private fun SettingsList(content: SettingsContent, actions: SettingsActions, openDialog: (SettingsDialog) -> Unit) {
    val colors = SpendFlowTheme.colors
    val switchModifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = dimensionResource(R.dimen.sf_space_l))
    val thresholds = content.preferences.capThresholds
    Column(
        Modifier
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
            stringResource(R.string.settings_title),
            Modifier.semantics { heading() },
            color = colors.textHeading,
            style = MaterialTheme.typography.headlineMedium,
        )
        SettingsSection(stringResource(R.string.settings_section_general)) {
            LinkRow(
                stringResource(R.string.settings_base_currency),
                onClick = { openDialog(SettingsDialog.CURRENCY) },
                modifier = Modifier.testTag("baseCurrencyRow"),
                icon = R.drawable.ic_currency_exchange,
                value = content.baseCurrency,
                iconTint = null,
            )
            SettingsDivider()
            LabeledSwitch(
                stringResource(R.string.settings_streak),
                content.preferences.streakEnabled,
                actions.onStreakChange,
                switchModifier.testTag("streakSwitch"),
                message = stringResource(R.string.settings_streak_message),
                icon = R.drawable.ic_streak,
            )
        }
        SettingsSection(stringResource(R.string.settings_section_quick_entry)) {
            LabeledSwitch(
                stringResource(R.string.settings_lock_screen),
                content.preferences.lockScreenQuickEntry,
                actions.onLockScreenChange,
                switchModifier.testTag("lockScreenSwitch"),
                message = stringResource(R.string.settings_lock_screen_message),
                icon = R.drawable.ic_lock,
            )
            SettingsDivider()
            LinkRow(
                stringResource(R.string.settings_cap_thresholds),
                onClick = { openDialog(SettingsDialog.THRESHOLDS) },
                modifier = Modifier.testTag("capThresholdsRow"),
                icon = R.drawable.ic_budget,
                value = stringResource(R.string.settings_cap_thresholds_value, thresholds.notice, thresholds.warning, thresholds.over),
                iconTint = null,
            )
        }
        SettingsSection(stringResource(R.string.settings_section_manage)) {
            LinkRow(
                stringResource(R.string.budget_categories_title),
                actions.onOpenCategories,
                icon = R.drawable.ic_filter,
                iconTint = null,
            )
            SettingsDivider()
            LinkRow(stringResource(R.string.goals_title), actions.onOpenGoals, icon = R.drawable.ic_savings_goal, iconTint = null)
            SettingsDivider()
            LinkRow(
                stringResource(R.string.log_out),
                onClick = { openDialog(SettingsDialog.LOG_OUT) },
                titleColor = colors.overCapText,
            )
        }
    }
}

@Composable
private fun CurrencyDialog(current: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    val locale = LocalLocale.current.platformLocale
    val labels = remember(locale) { CurrencyOptions.labels(locale) }
    var selected by rememberSaveable { mutableIntStateOf(CurrencyOptions.codes.indexOf(current).coerceAtLeast(0)) }
    FormDialog(
        title = stringResource(R.string.settings_base_currency),
        confirm = stringResource(R.string.action_save),
        dismiss = stringResource(R.string.action_cancel),
        saving = false,
        onConfirm = { onSelect(CurrencyOptions.codes[selected]) },
        onDismiss = onDismiss,
    ) {
        DropdownField(
            label = stringResource(R.string.settings_base_currency),
            options = labels,
            selectedIndex = selected,
            onSelect = { selected = it },
            fieldModifier = Modifier.testTag("baseCurrencyField"),
        )
    }
}

/** AC4: changing the base currency never converts what is already stored. */
@Composable
private fun CurrencyChangeDialog(current: String, pending: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    // Violet is too dark as text on the dark dialog surface
    val buttonColors = ButtonDefaults.textButtonColors(contentColor = SpendFlowTheme.colors.violetText)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_currency_change_title, pending)) },
        text = { Text(stringResource(R.string.settings_currency_change_message, current, pending)) },
        confirmButton = {
            TextButton(onClick = onConfirm, colors = buttonColors) { Text(stringResource(R.string.settings_currency_change_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss, colors = buttonColors) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun ThresholdsDialog(
    current: CapThresholds,
    onSave: (String, String, String) -> Int?,
    onDismiss: () -> Unit,
) {
    var notice by rememberSaveable { mutableStateOf(current.notice.toString()) }
    var warning by rememberSaveable { mutableStateOf(current.warning.toString()) }
    var over by rememberSaveable { mutableStateOf(current.over.toString()) }
    var error by rememberSaveable { mutableStateOf<Int?>(null) }
    val number = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next)
    FormDialog(
        title = stringResource(R.string.settings_cap_thresholds),
        confirm = stringResource(R.string.action_save),
        dismiss = stringResource(R.string.action_cancel),
        saving = false,
        onConfirm = {
            error = onSave(notice, warning, over)
            if (error == null) onDismiss()
        },
        onDismiss = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.sf_space_xs))) {
            Text(
                stringResource(R.string.settings_cap_thresholds_message),
                color = SpendFlowTheme.colors.textMuted,
                style = MaterialTheme.typography.bodyMedium,
            )
            LabeledTextField(stringResource(R.string.settings_cap_notice), notice, { notice = it }, keyboardOptions = number)
            LabeledTextField(stringResource(R.string.settings_cap_warning), warning, { warning = it }, keyboardOptions = number)
            LabeledTextField(
                stringResource(R.string.settings_cap_over), over, { over = it },
                keyboardOptions = number.copy(imeAction = ImeAction.Done),
                error = error?.let { stringResource(it) },
            )
        }
    }
}

private val previewContent = SettingsContent(
    baseCurrency = "LKR",
    preferences = UserPreferences(streakEnabled = true),
    hasTransactions = true,
)

@ThemePreviews
@Composable
private fun SettingsScreenPreview() = SpendFlowTheme {
    SettingsScreen(UiState.Success(previewContent), pendingCurrency = null, actions = SettingsActions())
}

@ThemePreviews
@Composable
private fun CurrencyChangeDialogPreview() = SpendFlowTheme {
    CurrencyChangeDialog("LKR", "USD", onConfirm = {}, onDismiss = {})
}
