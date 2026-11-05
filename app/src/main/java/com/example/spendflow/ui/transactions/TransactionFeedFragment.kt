package com.example.spendflow.ui.transactions

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

/** Home and Transactions; each subclass gets its own [TransactionFeedViewModel]. */
abstract class TransactionFeedFragment(private val home: Boolean) : Fragment() {
    private val model: TransactionFeedViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            SpendFlowTheme {
                val navController = findNavController()
                TransactionFeedRoute(
                    model = model,
                    home = home,
                    onSeeAll = { navController.navigate(R.id.transactionsFragment) },
                    onViewGoals = { navController.navigate(R.id.goalsFragment) },
                )
            }
        }
    }
}
