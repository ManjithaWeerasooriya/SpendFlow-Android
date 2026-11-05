package com.example.spendflow.ui.goals

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.semantics.Role
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.core.net.toUri
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.core.content.edit
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.spendflow.R
import com.example.spendflow.core.common.AppResult
import com.example.spendflow.core.notifications.AndroidGoalNotifications
import com.example.spendflow.core.notifications.GoalNotificationMonitor
import com.example.spendflow.core.ui.EventEffect
import com.example.spendflow.core.ui.UiState
import com.example.spendflow.core.ui.components.AmountFormatter
import com.example.spendflow.core.ui.components.FormDialog
import com.example.spendflow.core.ui.components.LabeledProgress
import com.example.spendflow.core.ui.components.LabeledTextField
import com.example.spendflow.core.ui.components.PrimaryButton
import com.example.spendflow.core.ui.components.SecondaryButton
import com.example.spendflow.core.ui.components.SpendFlowSnackbar
import com.example.spendflow.core.ui.components.SpendFlowSnackbarVisuals
import com.example.spendflow.core.ui.components.SpendFlowTopBar
import com.example.spendflow.core.ui.components.ThemePreviews
import com.example.spendflow.core.ui.messageRes
import com.example.spendflow.domain.model.Money
import com.example.spendflow.domain.model.SavingsGoal
import com.example.spendflow.domain.model.requiredMonthlySaving
import com.example.spendflow.ui.quickadd.QuickAddViewModel
import com.example.spendflow.ui.theme.SpendFlowTheme
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.util.Currency

private const val PERMISSION_PREFS = "goal_notification_permission"

private fun Context.notificationPermissionAsked() = getSharedPreferences(PERMISSION_PREFS, 0).getBoolean("asked", false)

/**
 * The goal dialog's subject: a goal ID, or [NEW] for a new goal. Saved by ID so the dialog survives
 * recreation and always edits the current values.
 */
private const val NEW = ""

@Composable
internal fun GoalsRoute(
    model: GoalsViewModel,
    notifications: AndroidGoalNotifications,
    monitor: GoalNotificationMonitor,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val result by model.goals.collectAsStateWithLifecycle()
    val saving by model.saving.collectAsStateWithLifecycle()
    val errors by model.errors.collectAsStateWithLifecycle()
    val lastMonth by model.lastMonth.collectAsStateWithLifecycle()
    val today = model.today
    var selectedGoalId by rememberSaveable { mutableStateOf<String?>(null) }

    var notificationsOn by remember { mutableStateOf(notifications.enabled()) }
    var permissionAsked by remember { mutableStateOf(context.notificationPermissionAsked()) }
    fun refreshNotifications() {
        notificationsOn = notifications.enabled()
        permissionAsked = context.notificationPermissionAsked()
        monitor.recheck()
    }
    val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refreshNotifications() }
    // Covers coming back from the system notification settings
    LifecycleResumeEffect(Unit) {
        refreshNotifications()
        onPauseOrDispose {}
    }

    var dialogGoalId by rememberSaveable { mutableStateOf<String?>(null) }
    var writeError by remember { mutableStateOf<Int?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    fun show(message: Int, tone: SpendFlowSnackbar.Tone) {
        snackbar.currentSnackbarData?.dismiss()
        scope.launch { snackbar.showSnackbar(SpendFlowSnackbarVisuals(resources.getString(message), tone)) }
    }
    EventEffect(model.events) { event ->
        when (event) {
            is AppResult.Success -> {
                dialogGoalId = null
                show(R.string.goal_saved, SpendFlowSnackbar.Tone.SUCCESS)
            }
            is AppResult.Error -> if (dialogGoalId != null) {
                writeError = event.error.messageRes
            } else {
                show(event.error.messageRes, SpendFlowSnackbar.Tone.ERROR)
            }
        }
    }

    val goals = when (val current = result) {
        null -> UiState.Loading
        is AppResult.Error -> UiState.Error(current.error)
        is AppResult.Success -> UiState.Success(
            current.data.sortedWith(compareBy<SavingsGoal> { it.isCompleted }.thenBy { it.name.lowercase() }),
        )
    }
    val selected = selectedGoalId?.let { id -> (goals as? UiState.Success)?.data?.firstOrNull { it.id == id } }
    // Clear the selection once the goal is gone
    if (selectedGoalId != null && selected == null && goals is UiState.Success) LaunchedEffect(selectedGoalId) { selectedGoalId = null }
    BackHandler(enabled = selected != null) { selectedGoalId = null }
    if (selected != null) {
        GoalDetailScreen(
            goal = selected, today = today, lastMonth = lastMonth,
            onBack = { selectedGoalId = null },
            onEdit = {
                model.resetErrors()
                writeError = null
                dialogGoalId = selected.id
            },
            onAddMoney = { amount, date, note -> model.addContribution(selected, amount, date, note) },
            onMarkCompleted = { model.markCompleted(selected) },
            onSetPrimary = { model.setPrimary(selected) },
            onDelete = {
                selectedGoalId = null
                model.delete(selected)
            },
        )
    } else GoalsScreen(
        today = today,
        lastMonth = lastMonth,
        goals = goals,
        notificationsOn = notificationsOn,
        permissionAsked = permissionAsked,
        snackbar = snackbar,
        onBack = onBack,
        onEnableNotifications = {
            if (Build.VERSION.SDK_INT >= 33 && !permissionAsked) {
                context.getSharedPreferences(PERMISSION_PREFS, 0).edit { putBoolean("asked", true) }
                requestPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                context.startActivity(
                    if (Build.VERSION.SDK_INT >= 26) {
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    } else {
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())
                    },
                )
            }
        },
        onAddGoal = {
            model.resetErrors()
            writeError = null
            dialogGoalId = NEW
        },
        onOpenGoal = { selectedGoalId = it.id },
    )

    val id = dialogGoalId ?: return
    val goal = if (id == NEW) null else (goals as? UiState.Success)?.data?.firstOrNull { it.id == id }
    if (id != NEW && goal == null) {
        // The goal was deleted while its dialog was open, or hasn't loaded yet after recreation
        if (goals is UiState.Success) LaunchedEffect(id) { dialogGoalId = null }
        return
    }
    GoalDialog(
        goal = goal,
        currency = goal?.target?.currencyCode ?: model.baseCurrency ?: "LKR",
        saving = saving,
        errors = errors,
        writeError = writeError,
        today = today,
        onSave = { name, target, saved, deadline ->
            writeError = null
            model.save(goal, name, target, saved, deadline)
        },
        onDismiss = { dialogGoalId = null },
    )
}

/** Savings goals with progress, the completion-notification switch and the add/update form. */
@Composable
internal fun GoalsScreen(
    today: LocalDate,
    lastMonth: Money?,
    goals: UiState<List<SavingsGoal>>,
    notificationsOn: Boolean,
    permissionAsked: Boolean,
    onBack: () -> Unit,
    onEnableNotifications: () -> Unit,
    onAddGoal: () -> Unit,
    onOpenGoal: (SavingsGoal) -> Unit,
    modifier: Modifier = Modifier,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    val colors = SpendFlowTheme.colors
    // The app shell already pads the NavHost for system bars and the keyboard.
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) { SpendFlowSnackbar(it) } },
        topBar = { SpendFlowTopBar(stringResource(R.string.goals_title), onBack) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(dimensionResource(R.dimen.sf_screen_padding)),
        ) {
            Text(stringResource(R.string.goals_description), color = colors.textHeading, style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(if (notificationsOn) R.string.goals_notifications_on else R.string.goals_notifications_off),
                Modifier
                    .padding(top = dimensionResource(R.dimen.sf_space_l))
                    .semantics { liveRegion = LiveRegionMode.Polite },
                color = colors.textHeading,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (!notificationsOn) {
                SecondaryButton(
                    stringResource(if (permissionAsked) R.string.goals_notification_settings else R.string.goals_enable_notifications),
                    onClick = onEnableNotifications,
                    modifier = Modifier
                        .padding(top = dimensionResource(R.dimen.sf_space_s))
                        .fillMaxWidth(),
                )
            }
            PrimaryButton(
                stringResource(R.string.goals_add),
                onClick = onAddGoal,
                modifier = Modifier
                    .padding(top = dimensionResource(R.dimen.sf_space_l))
                    .fillMaxWidth(),
            )
            val status = when (goals) {
                UiState.Loading -> R.string.goals_loading
                is UiState.Error -> goals.error.messageRes
                is UiState.Success -> if (goals.data.isEmpty()) R.string.goals_empty else null
            }
            if (status != null) {
                Text(
                    stringResource(status),
                    Modifier
                        .padding(top = dimensionResource(R.dimen.sf_space_l))
                        .semantics { liveRegion = LiveRegionMode.Polite },
                    color = colors.textMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            (goals as? UiState.Success)?.data?.forEach { goal -> GoalRow(goal, today, lastMonth, onOpen = { onOpenGoal(goal) }) }
        }
    }
}

@Composable
private fun GoalRow(goal: SavingsGoal, today: LocalDate, lastMonth: Money?, onOpen: () -> Unit) {
    val label = stringResource(R.string.goal_open_detail, goal.name)
    GoalSummary(
        goal, today, lastMonth,
        Modifier
            .padding(vertical = dimensionResource(R.dimen.sf_space_l))
            .clickable(onClickLabel = label, role = Role.Button, onClick = onOpen),
    )
}

@Composable
internal fun GoalDialog(
    goal: SavingsGoal?,
    currency: String,
    saving: Boolean,
    errors: GoalFormErrors,
    @StringRes writeError: Int?,
    today: LocalDate,
    onSave: (name: String, target: String, saved: String, deadline: LocalDate?) -> Unit,
    onDismiss: () -> Unit,
) {
    val digits = Currency.getInstance(currency).defaultFractionDigits
    fun plain(money: Money) = BigDecimal.valueOf(money.amountMinor).movePointLeft(digits).toPlainString()
    var name by rememberSaveable { mutableStateOf(goal?.name.orEmpty()) }
    var target by rememberSaveable { mutableStateOf(goal?.let { plain(it.target) }.orEmpty()) }
    var saved by rememberSaveable { mutableStateOf(goal?.let { plain(it.saved) } ?: "0") }
    var deadlineText by rememberSaveable { mutableStateOf(goal?.deadline?.toString().orEmpty()) }
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    val deadline = deadlineText.takeIf { it.isNotEmpty() }?.let(LocalDate::parse)
    val locale = LocalLocale.current.platformLocale
    val resources = LocalResources.current
    val live = remember(name, target, saved, deadline, goal, currency, today) {
        goalFormErrors(name, target, saved, deadline, goal?.deadline, currency, today)
    }
    val required = remember(target, saved, deadline, today) {
        val t = QuickAddViewModel.parseAmount(target, currency)
        val s = if (saved.isBlank()) Money.zero(currency) else QuickAddViewModel.parseAmount(saved, currency)
        if (t != null && s != null && deadline != null && live.target == null && live.saved == null) {
            Money(requiredMonthlySaving(t.amountMinor, s.amountMinor, deadline, today), currency)
        } else null
    }
    val amountKeyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    FormDialog(
        title = stringResource(if (goal == null) R.string.goals_add else R.string.goal_edit),
        confirm = stringResource(if (goal == null) R.string.goal_create else R.string.quick_save),
        confirmEnabled = !live.hasErrors,
        dismiss = stringResource(R.string.action_cancel),
        saving = saving,
        onConfirm = { onSave(name, target, saved, deadline) },
        onDismiss = onDismiss,
    ) {
        LabeledTextField(
            label = stringResource(R.string.goal_name_label),
            value = name,
            onValueChange = { name = it },
            error = (errors.name ?: live.name.takeIf { name.isNotEmpty() })?.let { stringResource(it) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            maxLength = 100,
        )
        Text(
            stringResource(R.string.goal_currency, currency),
            Modifier.padding(top = dimensionResource(R.dimen.sf_space_s)),
            color = SpendFlowTheme.colors.textMuted,
            style = MaterialTheme.typography.bodyMedium,
        )
        LabeledTextField(
            label = stringResource(R.string.goal_target_label),
            value = target,
            onValueChange = { target = it },
            error = (errors.target ?: live.target.takeIf { target.isNotEmpty() })?.let { stringResource(it) },
            keyboardOptions = amountKeyboard,
            maxLength = 40,
        )
        LabeledTextField(
            label = stringResource(R.string.goal_saved_label),
            value = saved,
            onValueChange = { saved = it },
            error = (errors.saved ?: live.saved.takeIf { saved.isNotEmpty() })?.let { stringResource(it) },
            keyboardOptions = amountKeyboard,
            maxLength = 40,
        )
        val deadlineError = errors.deadline ?: live.deadline.takeIf { deadline != null }
        SecondaryButton(
            deadline?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
                ?: stringResource(R.string.goal_deadline_choose),
            onClick = { pickingDate = true },
            modifier = Modifier
                .padding(top = dimensionResource(R.dimen.sf_space_s))
                .fillMaxWidth()
                .semantics { contentDescription = "${resources.getString(R.string.goal_deadline_label)}: ${deadline ?: resources.getString(R.string.goal_deadline_choose)}" },
        )
        deadlineError?.let {
            Text(stringResource(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        required?.let {
            Text(
                stringResource(R.string.goal_required_live, AmountFormatter.formatTrimmed(it, locale)),
                Modifier
                    .padding(top = dimensionResource(R.dimen.sf_space_s))
                    .semantics { liveRegion = LiveRegionMode.Polite },
                color = SpendFlowTheme.colors.textHeading,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        (writeError ?: errors.general)?.let {
            Text(
                stringResource(it),
                Modifier
                    .padding(top = dimensionResource(R.dimen.sf_space_s))
                    .semantics { liveRegion = LiveRegionMode.Polite },
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
    if (pickingDate) {
        // Dates are not restricted so the past-deadline error stays reachable
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = (deadline ?: today).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        val buttons = ButtonDefaults.textButtonColors(contentColor = SpendFlowTheme.colors.violetText)
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(colors = buttons, onClick = {
                    dateState.selectedDateMillis?.let {
                        deadlineText = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()
                    }
                    pickingDate = false
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }, colors = buttons) { Text(stringResource(R.string.action_cancel)) } },
        ) { DatePicker(dateState) }
    }
}

private val previewGoals = listOf(
    SavingsGoal("laptop", "Laptop", Money(500_000_00, "LKR"), Money(240_000_00, "LKR"), LocalDate.of(2027, 3, 31)),
    SavingsGoal("trip", "Trip", Money(100_000_00, "LKR"), Money(100_000_00, "LKR")),
)

@ThemePreviews
@Composable
private fun GoalsScreenPreview() = SpendFlowTheme {
    GoalsScreen(
        today = LocalDate.of(2026, 10, 11), lastMonth = Money(45_000_00, "LKR"),
        goals = UiState.Success(previewGoals),
        notificationsOn = false,
        permissionAsked = false,
        onBack = {}, onEnableNotifications = {}, onAddGoal = {}, onOpenGoal = {},
    )
}

@ThemePreviews
@Composable
private fun GoalDialogPreview() = SpendFlowTheme {
    GoalDialog(
        goal = previewGoals.first(), currency = "LKR", saving = false,
        errors = GoalFormErrors(target = R.string.goal_name_required), writeError = null, today = LocalDate.of(2026, 10, 11),
        onSave = { _, _, _, _ -> }, onDismiss = {},
    )
}
