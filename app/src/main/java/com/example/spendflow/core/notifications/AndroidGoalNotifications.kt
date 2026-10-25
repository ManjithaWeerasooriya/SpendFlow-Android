package com.example.spendflow.core.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.spendflow.MainActivity
import com.example.spendflow.R
import com.example.spendflow.core.ui.components.AmountFormatter
import com.example.spendflow.domain.model.SavingsGoal
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidGoalNotifications @Inject constructor(@ApplicationContext private val context: Context) :
    GoalNotificationSender, GoalAlertHistory {
    private val history = context.getSharedPreferences("goal_completion_alerts", Context.MODE_PRIVATE)

    fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.goal_notification_channel), NotificationManager.IMPORTANCE_DEFAULT))
    }

    fun enabled(): Boolean {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        return Build.VERSION.SDK_INT < 26 || context.getSystemService(NotificationManager::class.java)
            .getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
    }

    override fun delivered(uid: String, goalId: String) = history.getBoolean("$uid/$goalId", false)
    override fun markDelivered(uid: String, goalId: String) { history.edit().putBoolean("$uid/$goalId", true).apply() }

    override fun show(uid: String, goal: SavingsGoal): Boolean {
        createChannel()
        if (!enabled()) return false
        val intent = Intent(context, MainActivity::class.java).setAction(OPEN_GOALS)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val text = context.getString(R.string.goal_notification_body, goal.name, AmountFormatter.format(goal.target, showDecimals = true))
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_goal_notification)
            .setContentTitle(context.getString(R.string.goal_completed_title))
            .setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending).setAutoCancel(true).setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build()
        return try {
            NotificationManagerCompat.from(context).notify("goal:$uid:${goal.id}", 0, notification)
            true
        } catch (_: SecurityException) { false }
    }

    fun clearVisible() { NotificationManagerCompat.from(context).cancelAll() }

    companion object {
        const val CHANNEL = "savings_goal_completion"
        const val OPEN_GOALS = "com.example.spendflow.OPEN_GOALS"
    }
}
