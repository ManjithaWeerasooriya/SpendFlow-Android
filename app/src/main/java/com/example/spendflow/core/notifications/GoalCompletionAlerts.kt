package com.example.spendflow.core.notifications

import com.example.spendflow.domain.model.SavingsGoal

interface GoalAlertHistory {
    fun delivered(uid: String, goalId: String): Boolean
    fun markDelivered(uid: String, goalId: String)
}

interface GoalNotificationSender {
    /** False when notification permission or the goal channel is disabled. */
    fun show(uid: String, goal: SavingsGoal): Boolean
}

/** One completion alert per goal per user on this device, including after restarting the app. */
class GoalCompletionAlerts(private val history: GoalAlertHistory, private val sender: GoalNotificationSender) {
    fun check(uid: String, goals: List<SavingsGoal>) {
        goals.filter { it.isCompleted && !history.delivered(uid, it.id) }.forEach { goal ->
            if (sender.show(uid, goal)) history.markDelivered(uid, goal.id)
        }
    }
}
