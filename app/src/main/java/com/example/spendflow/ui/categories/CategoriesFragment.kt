package com.example.spendflow.ui.categories

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.example.spendflow.R
import com.example.spendflow.core.ui.UiState
import com.example.spendflow.core.ui.collectWithLifecycle
import com.example.spendflow.core.ui.components.SpendFlowSnackbar
import com.example.spendflow.core.ui.messageRes
import com.example.spendflow.databinding.FragmentCategoriesBinding
import com.example.spendflow.domain.model.Category
import com.example.spendflow.domain.model.CategoryType
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint

/** Renders [CategoriesViewModel] state and forwards input. No business logic lives here. */
@AndroidEntryPoint
class CategoriesFragment : Fragment(R.layout.fragment_categories) {

    private val viewModel: CategoriesViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentCategoriesBinding.bind(view)
        val adapter = CategoryAdapter()
        binding.list.adapter = adapter

        binding.addButton.setOnClickListener { submit(binding) }
        binding.nameInput.setOnEditorActionListener { _, actionId, _ ->
            (actionId == EditorInfo.IME_ACTION_DONE).also { if (it) submit(binding) }
        }
        binding.retryButton.setOnClickListener { viewModel.retry() }

        viewModel.uiState.collectWithLifecycle(viewLifecycleOwner) { render(binding, adapter, it) }
        viewModel.events.collectWithLifecycle(viewLifecycleOwner) { event ->
            when (event) {
                CategoriesEvent.CategoryAdded -> binding.nameInput.text = null
                is CategoriesEvent.ShowMessage ->
                    SpendFlowSnackbar.make(view, getString(event.messageRes)).show()
                is CategoriesEvent.ShowError ->
                    SpendFlowSnackbar.make(view, getString(event.error.messageRes), SpendFlowSnackbar.Tone.ERROR, duration = Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun submit(binding: FragmentCategoriesBinding) {
        val type = when (binding.typeToggle.checkedButtonId) {
            R.id.typeIncome -> CategoryType.INCOME
            else -> CategoryType.EXPENSE
        }
        viewModel.addCategory(binding.nameInput.text?.toString().orEmpty(), type)
    }

    private fun render(binding: FragmentCategoriesBinding, adapter: CategoryAdapter, state: UiState<List<Category>>) {
        binding.progress.isVisible = state is UiState.Loading
        binding.errorGroup.isVisible = state is UiState.Error
        binding.list.isVisible = state is UiState.Success
        binding.emptyState.isVisible = state is UiState.Success && state.data.isEmpty()
        when (state) {
            is UiState.Success -> adapter.submitList(state.data)
            is UiState.Error -> binding.errorText.setText(state.error.messageRes)
            UiState.Loading -> Unit
        }
    }
}
