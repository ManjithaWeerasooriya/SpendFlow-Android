package com.example.spendflow.ui.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.spendflow.R
import com.example.spendflow.core.common.AppResult
import com.example.spendflow.data.repository.GoalRepository
import com.example.spendflow.data.repository.UserRepository
import com.example.spendflow.data.repository.WriteFailures
import com.example.spendflow.domain.model.GoalContribution
import com.example.spendflow.domain.model.Money
import com.example.spendflow.domain.model.SavingsGoal
import com.example.spendflow.ui.quickadd.EntryValidation
import com.example.spendflow.ui.quickadd.QuickAddViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import com.example.spendflow.domain.usecase.GetMonthlySummary
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.util.Currency
import javax.inject.Inject

data class GoalFormErrors(
    val name: Int? = null,
    val target: Int? = null,
    val saved: Int? = null,
    val deadline: Int? = null,
    val general: Int? = null,
) {
    val hasErrors get() = name != null || target != null || saved != null || deadline != null || general != null
}

/**
 * Pure validation shared by [GoalsViewModel.save] and the live form. A past deadline is accepted
 * only when it is unchanged from the goal being edited.
 */
fun goalFormErrors(
    name: String,
    targetText: String,
    savedText: String,
    deadline: LocalDate?,
    previousDeadline: LocalDate?,
    code: String,
    today: LocalDate,
): GoalFormErrors {
    val normalizedSaved = savedText.trim().replace(',', '.')
    val savedDecimal = normalizedSaved.toBigDecimalOrNull()
    val digits = Currency.getInstance(code).defaultFractionDigits.coerceIn(0, 2)
    val validZero = normalizedSaved.matches(Regex("0+(?:\\.0{0,2})?")) && (savedDecimal?.scale() ?: -1) in 0..digits
    return GoalFormErrors(
        name = if (name.isBlank() || name.trim().length > 100) R.string.goal_name_required else null,
        target = EntryValidation.amountError(targetText, code),
        saved = if (validZero) null else EntryValidation.amountError(savedText, code),
        deadline = when {
            deadline == null -> R.string.goal_deadline_required
            deadline.isBefore(today) && deadline != previousDeadline -> R.string.goal_deadline_past
            else -> null
        },
    )
}

@HiltViewModel
class GoalsViewModel @Inject constructor(
    private val repository: GoalRepository,
    users: UserRepository,
    failures: WriteFailures,
    summary: GetMonthlySummary,
    private val clock: Clock,
) : ViewModel() {
    val today: LocalDate get() = LocalDate.now(clock)
    /** Last month's net savings; null while loading or on error. */
    val lastMonth: StateFlow<Money?> = summary(YearMonth.now(clock).minusMonths(1), clock.zone)
        .map { (it as? AppResult.Success)?.data?.netSavings }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val goals = repository.observeGoals().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    private val profile = users.observeProfile().stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val saving = MutableStateFlow(false)
    val errors = MutableStateFlow(GoalFormErrors())
    private val results = Channel<AppResult<Unit>>(Channel.BUFFERED)
    val events = results.receiveAsFlow()

    val baseCurrency get() = (profile.value as? AppResult.Success)?.data?.baseCurrency

    init {
        viewModelScope.launch {
            failures.failures.collect { failure ->
                if (failure.path.contains("/goals/")) results.send(AppResult.Error(failure.error))
            }
        }
    }

    fun resetErrors() { errors.value = GoalFormErrors() }

    fun save(existing: SavingsGoal?, name: String, targetText: String, savedText: String, deadline: LocalDate?): Boolean {
        if (saving.value) return false
        val currency = existing?.target?.currencyCode ?: (profile.value as? AppResult.Success)?.data?.baseCurrency
        val code = currency ?: "LKR"
        errors.value = goalFormErrors(name, targetText, savedText, deadline, existing?.deadline, code, today)
            .copy(general = if (currency == null) R.string.entry_not_ready else null)
        if (errors.value.hasErrors) return false
        val target = QuickAddViewModel.parseAmount(targetText, code) ?: return false
        val saved = if (errors.value.saved == null && savedText.trim().replace(',', '.').toBigDecimalOrNull()?.signum() == 0) Money.zero(code) else QuickAddViewModel.parseAmount(savedText, code) ?: return false
        if (target.amountMinor > 99_999_999_999L || saved.amountMinor > 99_999_999_999L) {
            errors.value = GoalFormErrors(
                target = if (target.amountMinor > 99_999_999_999L) R.string.entry_amount_large else null,
                saved = if (saved.amountMinor > 99_999_999_999L) R.string.entry_amount_large else null,
            )
            return false
        }
        // copy keeps contributions, primary and completed flags on edit
        val goal = existing?.copy(name = name.trim(), target = target, saved = saved, deadline = deadline)
            ?: SavingsGoal(repository.newId(), name.trim(), target, saved, deadline)
        saving.value = true
        viewModelScope.launch {
            val result = if (existing == null) repository.create(goal) else repository.update(goal)
            saving.value = false
            results.send(result)
        }
        return true
    }

    /** Returns an error string resource, or null when the contribution was accepted and is being written. */
    fun addContribution(goal: SavingsGoal, amountText: String, date: LocalDate?, note: String): Int? {
        if (saving.value) return R.string.entry_not_ready
        val code = goal.target.currencyCode
        EntryValidation.amountError(amountText, code)?.let { return it }
        if (date == null) return R.string.goal_deadline_required
        if (note.trim().length > 200) return R.string.goal_note_long
        val amount = QuickAddViewModel.parseAmount(amountText, code) ?: return R.string.entry_amount_invalid
        if (goal.saved.amountMinor + amount.amountMinor > 99_999_999_999L) return R.string.entry_amount_large
        // shortcut: read-modify-write can lose a contribution made offline on two devices, use FieldValue.increment if multi-device matters
        write(goal.copy(
            saved = goal.saved + amount,
            contributions = goal.contributions + GoalContribution(repository.newId(), amount, date, note.trim().ifEmpty { null }),
        ))
        return null
    }

    fun markCompleted(goal: SavingsGoal) = write(goal.copy(markedCompleted = true))

    fun setPrimary(goal: SavingsGoal) {
        saving.value = true
        viewModelScope.launch {
            val all = (repository.observeGoals().first() as? AppResult.Success)?.data ?: listOf(goal)
            var result: AppResult<Unit> = AppResult.Success(Unit)
            for (g in all) {
                val wanted = g.id == goal.id
                if (g.isPrimary != wanted) {
                    val r = repository.update(g.copy(isPrimary = wanted))
                    if (r is AppResult.Error) result = r
                }
            }
            saving.value = false
            results.send(result)
        }
    }

    fun delete(goal: SavingsGoal) {
        viewModelScope.launch { results.send(repository.delete(goal.id)) }
    }

    private fun write(goal: SavingsGoal) {
        saving.value = true
        viewModelScope.launch {
            val result = repository.update(goal)
            saving.value = false
            results.send(result)
        }
    }
}
