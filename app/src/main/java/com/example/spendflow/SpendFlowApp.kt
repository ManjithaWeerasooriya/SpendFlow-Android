package com.example.spendflow

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SpendFlowApp : Application() {
    @javax.inject.Inject lateinit var goalNotifications: com.example.spendflow.core.notifications.GoalNotificationMonitor
    override fun onCreate() {
        super.onCreate()
        goalNotifications.start()
    }
}
