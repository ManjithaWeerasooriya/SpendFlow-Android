package com.example.spendflow.ui.more

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.spendflow.R
import com.example.spendflow.databinding.FragmentMoreBinding

/** Hub for the areas without their own tab, each one tap away. */
class MoreFragment : Fragment(R.layout.fragment_more) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentMoreBinding.bind(view)
        val navController = findNavController()
        binding.budgetCategoriesRow.setOnClickListener { navController.navigate(R.id.action_more_to_categories) }
        binding.goalsRow.setOnClickListener { navController.navigate(R.id.action_more_to_goals) }
        binding.settingsRow.setOnClickListener { navController.navigate(R.id.action_more_to_settings) }
    }
}
