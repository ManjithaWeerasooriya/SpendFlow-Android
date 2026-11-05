package com.example.spendflow.ui.goals

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import com.example.spendflow.core.ui.components.FormDialog
import com.example.spendflow.core.ui.components.LabeledTextField
import com.example.spendflow.core.ui.components.PrimaryButton
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.FormatStyle
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

/**
 * Stateless goal detail, shown in place of the list so the navigation graph is unchanged.
 * [onAddMoney] returns an error string resource, or null when the contribution was accepted.
 */
@Composable
internal fun GoalDetailScreen(
    goal: SavingsGoal,
    today: LocalDate,
    lastMonth: Money?,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onAddMoney: (amount: String, date: LocalDate, note: String) -> Int?,
    onMarkCompleted: () -> Unit,
    onSetPrimary: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val buttons = ButtonDefaults.textButtonColors(contentColor = SpendFlowTheme.colors.violetText)
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
            Box(Modifier.align(Alignment.End)) {
                TextButton(onClick = { menuOpen = true }, colors = buttons) { Text(stringResource(R.string.goal_more_actions)) }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.goal_edit)) }, onClick = { menuOpen = false; onEdit() })
                    if (!goal.isCompleted) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.goal_mark_completed)) }, onClick = { menuOpen = false; onMarkCompleted() })
                    }
                    if (!goal.isPrimary) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.goal_set_primary)) }, onClick = { menuOpen = false; onSetPrimary() })
                    }
                    DropdownMenuItem(text = { Text(stringResource(R.string.goal_delete)) }, onClick = { menuOpen = false; confirmDelete = true })
                }
            }
            GoalSummary(goal, today, lastMonth)
            PrimaryButton(
                stringResource(R.string.goal_add_money),
                onClick = { adding = true },
                modifier = Modifier
                    .padding(top = dimensionResource(R.dimen.sf_space_l))
                    .fillMaxWidth(),
            )
        }
    }
    if (adding) AddMoneyDialog(today, onAdd = onAddMoney, onDismiss = { adding = false })
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.goal_delete_title)) },
            text = { Text(stringResource(R.string.goal_delete_message, goal.name)) },
            confirmButton = { TextButton(colors = buttons, onClick = { confirmDelete = false; onDelete() }) { Text(stringResource(R.string.goal_delete)) } },
            dismissButton = { TextButton(colors = buttons, onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun AddMoneyDialog(today: LocalDate, onAdd: (String, LocalDate, String) -> Int?, onDismiss: () -> Unit) {
    var amount by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var dateText by rememberSaveable { mutableStateOf(today.toString()) }
    var picking by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<Int?>(null) }
    val date = LocalDate.parse(dateText)
    val locale = LocalLocale.current.platformLocale
    FormDialog(
        title = stringResource(R.string.goal_add_money),
        confirm = stringResource(R.string.quick_save),
        dismiss = stringResource(R.string.action_cancel),
        saving = false,
        onConfirm = {
            error = onAdd(amount, date, note)
            if (error == null) onDismiss()
        },
        onDismiss = onDismiss,
    ) {
        LabeledTextField(
            label = stringResource(R.string.goal_contribution_amount),
            value = amount,
            onValueChange = { amount = it },
            error = error?.takeIf { it != R.string.goal_note_long }?.let { stringResource(it) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            maxLength = 40,
        )
        SecondaryButton(
            date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)),
            onClick = { picking = true },
            modifier = Modifier
                .padding(top = dimensionResource(R.dimen.sf_space_s))
                .fillMaxWidth(),
        )
        LabeledTextField(
            label = stringResource(R.string.goal_contribution_note),
            value = note,
            onValueChange = { note = it },
            error = error?.takeIf { it == R.string.goal_note_long }?.let { stringResource(it) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            maxLength = 300,
        )
    }
    if (picking) {
        val state = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        val buttons = ButtonDefaults.textButtonColors(contentColor = SpendFlowTheme.colors.violetText)
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(colors = buttons, onClick = {
                    state.selectedDateMillis?.let { dateText = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString() }
                    picking = false
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { picking = false }, colors = buttons) { Text(stringResource(R.string.action_cancel)) } },
        ) { DatePicker(state) }
    }
}

/** Progress, stat rows and the required-monthly card; shared by list rows and the detail. */
@Composable
internal fun GoalSummary(goal: SavingsGoal, today: LocalDate, lastMonth: Money?, modifier: Modifier = Modifier) {
    val colors = SpendFlowTheme.colors
    val locale = LocalLocale.current.platformLocale
    fun money(value: Money) = AmountFormatter.formatTrimmed(value, locale)
    Column(modifier) {
        if (goal.isPrimary) {
            Text(stringResource(R.string.goal_primary_marker), color = colors.violetText, style = MaterialTheme.typography.labelMedium)
        }
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
        onBack = {}, onEdit = {}, onAddMoney = { _, _, _ -> null }, onMarkCompleted = {}, onSetPrimary = {}, onDelete = {},
    )
}
