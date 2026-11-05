package com.example.spendflow.core.notifications

import com.example.spendflow.domain.model.Money
import com.example.spendflow.domain.model.SavingsGoal
import org.junit.Assert.*
import org.junit.Test

class GoalCompletionAlertsTest {
    private class History : GoalAlertHistory {
        val keys = mutableSetOf<Pair<String, String>>()
        override fun delivered(uid: String, goalId: String) = uid to goalId in keys
        override fun markDelivered(uid: String, goalId: String) { keys += uid to goalId }
    }
    private class Sender : GoalNotificationSender {
        var allowed = true
        val sent = mutableListOf<Pair<String, String>>()
        override fun show(uid: String, goal: SavingsGoal): Boolean {
            if (!allowed) return false
            sent += uid to goal.id
            return true
        }
    }
    private fun goal(saved: Long, id: String = "laptop") = SavingsGoal(id, "Laptop", Money(10000, "LKR"), Money(saved, "LKR"))

    @Test fun `notifies at target once despite later updates or restart`() {
        val history = History()
        val sender = Sender()
        val alerts = GoalCompletionAlerts(history, sender)
        alerts.check("alice", listOf(goal(9999)))
        assertTrue(sender.sent.isEmpty())
        alerts.check("alice", listOf(goal(10000)))
        alerts.check("alice", listOf(goal(10001)))
        GoalCompletionAlerts(history, sender).check("alice", listOf(goal(10000)))
        assertEquals(listOf("alice" to "laptop"), sender.sent)
    }

    @Test fun `over target triggers and notification history is scoped to user and goal`() {
        val history = History()
        val sender = Sender()
        val alerts = GoalCompletionAlerts(history, sender)
        alerts.check("alice", listOf(goal(15000), goal(10000, "holiday")))
        alerts.check("bob", listOf(goal(15000)))
        assertEquals(listOf("alice" to "laptop", "alice" to "holiday", "bob" to "laptop"), sender.sent)
    }

    @Test fun `denied notifications are retried when permission is granted`() {
        val history = History()
        val sender = Sender().apply { allowed = false }
        val alerts = GoalCompletionAlerts(history, sender)
        alerts.check("alice", listOf(goal(10000)))
        assertFalse(history.delivered("alice", "laptop"))
        sender.allowed = true
        alerts.check("alice", listOf(goal(10000)))
        assertEquals(1, sender.sent.size)
        assertTrue(history.delivered("alice", "laptop"))
    }

    @Test fun `invalid goal amounts and mismatched currencies are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { goal(-1) }
        assertThrows(IllegalArgumentException::class.java) { SavingsGoal("g", "Goal", Money(0, "LKR"), Money(0, "LKR")) }
        assertThrows(IllegalArgumentException::class.java) { SavingsGoal("g", "Goal", Money(10, "LKR"), Money(10, "USD")) }
    }
}
