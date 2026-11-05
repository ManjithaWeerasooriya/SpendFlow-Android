package com.example.spendflow.ui.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.spendflow.R
import com.example.spendflow.core.common.AppResult
import com.example.spendflow.data.repository.GoalRepository
import com.example.spendflow.data.repository.UserRepository
import com.example.spendflow.data.repository.WriteFailures
import com.example.spendflow.domain.model.Money
import com.example.spendflow.domain.model.SavingsGoal
import com.example.spendflow.ui.quickadd.EntryValidation
import com.example.spendflow.ui.quickadd.QuickAddViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Currency
import javax.inject.Inject

data class GoalFormErrors(val name: Int? = null, val target: Int? = null, val saved: Int? = null, val general: Int? = null) {
    val hasErrors get() = name != null || target != null || saved != null || general != null
}

@HiltViewModel
class GoalsViewModel @Inject constructor(private val repository: GoalRepository, users: UserRepository, failures: WriteFailures) : ViewModel() {
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

    fun save(existing: SavingsGoal?, name: String, targetText: String, savedText: String): Boolean {
        if (saving.value) return false
        val currency = existing?.target?.currencyCode ?: (profile.value as? AppResult.Success)?.data?.baseCurrency
        val code = currency ?: "LKR"
        val normalizedSaved = savedText.trim().replace(',', '.')
        val savedDecimal = normalizedSaved.toBigDecimalOrNull()
        val digits = Currency.getInstance(code).defaultFractionDigits.coerceIn(0, 2)
        val validZero = normalizedSaved.matches(Regex("0+(?:\\.0{0,2})?")) && (savedDecimal?.scale() ?: -1) in 0..digits
        errors.value = GoalFormErrors(
            name = if (name.isBlank() || name.trim().length > 100) R.string.goal_name_required else null,
            target = EntryValidation.amountError(targetText, code),
            saved = if (validZero) null else EntryValidation.amountError(savedText, code),
            general = if (currency == null) R.string.entry_not_ready else null,
        )
        if (errors.value.hasErrors) return false
        val target = QuickAddViewModel.parseAmount(targetText, code) ?: return false
        val saved = if (validZero) Money.zero(code) else QuickAddViewModel.parseAmount(savedText, code) ?: return false
        if (target.amountMinor > 99_999_999_999L || saved.amountMinor > 99_999_999_999L) {
            errors.value = GoalFormErrors(
                target = if (target.amountMinor > 99_999_999_999L) R.string.entry_amount_large else null,
                saved = if (saved.amountMinor > 99_999_999_999L) R.string.entry_amount_large else null,
            )
            return false
        }
        val goal = SavingsGoal(existing?.id ?: repository.newId(), name.trim(), target, saved)
        saving.value = true
        viewModelScope.launch {
            val result = if (existing == null) repository.create(goal) else repository.update(goal)
            saving.value = false
            results.send(result)
        }
        return true
    }
}
