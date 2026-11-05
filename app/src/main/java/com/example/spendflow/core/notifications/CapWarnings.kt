package com.example.spendflow.core.notifications

import android.content.Context
import androidx.core.content.edit
import com.example.spendflow.data.session.UserSession
import com.example.spendflow.domain.model.CapEvaluator
import com.example.spendflow.domain.model.CapLevel
import com.example.spendflow.domain.model.CapUsage
import com.example.spendflow.domain.model.CapWarning
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

/** The highest cap level already warned about, per user, month and cap. */
interface CapAlertHistory {
    fun lastShown(uid: String, month: YearMonth, budgetId: String): CapLevel
    fun record(uid: String, month: YearMonth, budgetId: String, level: CapLevel)
}

@Singleton
class SharedPreferencesCapAlertHistory @Inject constructor(@ApplicationContext context: Context) : CapAlertHistory {
    private val preferences = context.getSharedPreferences("cap_alerts", Context.MODE_PRIVATE)

    override fun lastShown(uid: String, month: YearMonth, budgetId: String): CapLevel =
        preferences.getString(key(uid, month, budgetId), null)
            ?.let { name -> CapLevel.entries.firstOrNull { it.name == name } } ?: CapLevel.UNDER

    override fun record(uid: String, month: YearMonth, budgetId: String, level: CapLevel) {
        preferences.edit { putString(key(uid, month, budgetId), level.name) }
    }

    private fun key(uid: String, month: YearMonth, budgetId: String) = "$uid/$month/$budgetId"
}

/**
 * In-app cap warnings shared by Home and Budget. Whichever screen sees new usage first calls [check];
 * a warning is recorded as shown right away, so it appears once per threshold per month on this device,
 * and stays in [pending] until dismissed.
 */
@Singleton
class CapWarnings @Inject constructor(
    private val history: CapAlertHistory,
    private val session: UserSession,
) {
    private val _pending = MutableStateFlow<List<CapWarning>>(emptyList())
    val pending: StateFlow<List<CapWarning>> = _pending.asStateFlow()
    private var pendingUid: String? = null

    @Synchronized
    fun check(month: YearMonth, usages: Collection<CapUsage>) {
        val uid = session.currentUid() ?: return
        if (uid != pendingUid) {
            pendingUid = uid
            _pending.value = emptyList()
        }
        val fresh = CapEvaluator.newWarnings(usages, month) { history.lastShown(uid, month, it) }
        fresh.forEach { history.record(uid, month, it.budgetId, it.level) }
        val latest = usages.associateBy { it.cap.id }
        val replaced = fresh.map { it.budgetId }.toSet()
        _pending.update { current ->
            // Shown warnings follow the current figures, and go once an edit or delete takes spending back
            // below their level or the cap is removed; a newer warning for the same cap replaces the older one
            val kept = current.mapNotNull { warning ->
                val usage = latest[warning.budgetId] ?: return@mapNotNull null
                if (warning.month != month || warning.budgetId in replaced || CapEvaluator.alertLevel(usage) < warning.level) {
                    null
                } else {
                    warning.copy(usage = usage)
                }
            }
            kept + fresh
        }
    }

    // By identity, not figures: the screen may hold a copy from before the latest refresh
    fun dismiss(warning: CapWarning) {
        _pending.update { current ->
            current.filterNot { it.budgetId == warning.budgetId && it.month == warning.month && it.level == warning.level }
        }
    }
}
