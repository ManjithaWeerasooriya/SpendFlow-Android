package com.example.spendflow

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.example.spendflow.core.notifications.AndroidGoalNotifications
import com.example.spendflow.core.notifications.GoalNotificationMonitor
import com.example.spendflow.ui.main.MainViewModel
import com.example.spendflow.ui.main.SpendFlowShell
import com.example.spendflow.ui.quickadd.EntryFeedbackViewModel
import com.example.spendflow.ui.theme.SpendFlowTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Hosts the Compose app shell; a goal-completion notification opens Goals through [openGoals]. */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    // Firestore keeps retrying, so once this is granted the pending write goes through
    private val requestLocalNetwork =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) Log.w("SF-01", "Local network access denied; Firebase emulators unreachable")
        }

    private val viewModel: MainViewModel by viewModels()
    private val entryFeedback: EntryFeedbackViewModel by viewModels()
    @Inject lateinit var notifications: AndroidGoalNotifications
    @Inject lateinit var monitor: GoalNotificationMonitor
    private var openGoals by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (BuildConfig.DEBUG) {
            // Android 17+ treats 10.0.2.2 as local network and silently drops traffic to it
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_LOCAL_NETWORK)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestLocalNetwork.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
            }
        }
        enableEdgeToEdge()
        openGoals = intent.action == AndroidGoalNotifications.OPEN_GOALS
        setContent {
            SpendFlowTheme {
                SpendFlowShell(
                    mainViewModel = viewModel,
                    entryFeedback = entryFeedback,
                    notifications = notifications,
                    monitor = monitor,
                    openGoalsRequested = openGoals,
                    onGoalsOpened = {
                        // Clear the action so a recreated activity doesn't open Goals again
                        intent.action = null
                        openGoals = false
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == AndroidGoalNotifications.OPEN_GOALS) openGoals = true
    }
}
