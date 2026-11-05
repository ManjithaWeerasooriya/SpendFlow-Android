package com.example.spendflow.ui.more

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.spendflow.R
import com.example.spendflow.ui.theme.SpendFlowTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MoreFragment : Fragment() {

    private val viewModel: MoreViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            SpendFlowTheme {
                val navController = findNavController()
                MoreRoute(
                    viewModel = viewModel,
                    onOpenCategories = { navController.navigate(R.id.action_more_to_categories) },
                    onOpenGoals = { navController.navigate(R.id.action_more_to_goals) },
                    onOpenSettings = { navController.navigate(R.id.action_more_to_settings) },
                )
            }
        }
    }
}
