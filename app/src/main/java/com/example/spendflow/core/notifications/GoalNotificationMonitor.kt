package com.example.spendflow.core.notifications

import com.example.spendflow.core.common.AppResult
import com.example.spendflow.data.repository.GoalRepository
import com.example.spendflow.data.session.UserSession
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

/** A process-lifetime listener; completions received while closed are checked on next launch. */
@Singleton
class GoalNotificationMonitor @Inject constructor(
    private val goals: GoalRepository,
    private val session: UserSession,
    private val notifications: AndroidGoalNotifications,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val refresh = MutableStateFlow(0)
    private var started = false

    fun start() {
        if (started) return
        started = true
        notifications.createChannel()
        val alerts = GoalCompletionAlerts(notifications, notifications)
        scope.launch {
            var previousUid = session.currentUid()
            session.uid.distinctUntilChanged().collectLatest { uid ->
                if (uid == null || uid != previousUid) notifications.clearVisible()
                previousUid = uid
                if (uid != null) combine(goals.observeConfirmedGoals(), refresh) { result, _ -> result }
                    .collect { result ->
                        if (session.currentUid() == uid && result is AppResult.Success) alerts.check(uid, result.data)
                    }
            }
        }
    }

    fun recheck() { refresh.value++ }
}
