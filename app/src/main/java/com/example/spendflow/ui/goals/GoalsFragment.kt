package com.example.spendflow.ui.goals

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.spendflow.core.notifications.AndroidGoalNotifications
import com.example.spendflow.core.notifications.GoalNotificationMonitor
import com.example.spendflow.ui.theme.SpendFlowTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class GoalsFragment : Fragment() {
    private val model: GoalsViewModel by viewModels()
    @Inject lateinit var notifications: AndroidGoalNotifications
    @Inject lateinit var monitor: GoalNotificationMonitor

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            SpendFlowTheme {
                GoalsRoute(model, notifications, monitor, onBack = { findNavController().navigateUp() })
            }
        }
    }
}
