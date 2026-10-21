package com.example.spendflow.ui.more

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.spendflow.R
import com.example.spendflow.databinding.FragmentMoreBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint

/** Hub for the areas without their own tab, each one tap away. */
@AndroidEntryPoint
class MoreFragment : Fragment(R.layout.fragment_more) {

    private val viewModel: MoreViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentMoreBinding.bind(view)
        val navController = findNavController()
        binding.budgetCategoriesRow.setOnClickListener { navController.navigate(R.id.action_more_to_categories) }
        binding.goalsRow.setOnClickListener { navController.navigate(R.id.action_more_to_goals) }
        binding.settingsRow.setOnClickListener { navController.navigate(R.id.action_more_to_settings) }
        binding.logOutRow.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.log_out_confirm_title)
                .setMessage(R.string.log_out_confirm_message)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.log_out) { _, _ -> viewModel.logOut() }
                .show()
        }
    }
}
