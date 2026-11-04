package com.example.spendflow.ui.categories

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.core.view.SoftwareKeyboardControllerCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.spendflow.core.ui.CurrencyOptions
import com.example.spendflow.R
import com.example.spendflow.core.ui.UiState
import com.example.spendflow.core.ui.collectWithLifecycle
import com.example.spendflow.core.ui.components.SpendFlowSnackbar
import com.example.spendflow.core.ui.messageRes
import com.example.spendflow.databinding.FragmentCategoriesBinding
import com.example.spendflow.domain.model.Category
import com.example.spendflow.domain.model.CategoryType
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import dagger.hilt.android.AndroidEntryPoint

/** Renders [CategoriesViewModel] state and forwards input. No business logic lives here. */
@AndroidEntryPoint
class CategoriesFragment : Fragment(R.layout.fragment_categories) {

    private val viewModel: CategoriesViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentCategoriesBinding.bind(view)
        val currencies = CurrencyOptions.codes
        binding.defaultCurrencyInput.adapter = android.widget.ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, CurrencyOptions.labels)
        binding.defaultCurrencyInput.setSelection(currencies.indexOf("LKR"))
        binding.defaultCurrencyControls.isVisible = binding.typeToggle.checkedButtonId == R.id.typeIncome
        binding.typeToggle.addOnButtonCheckedListener { _, _, checked ->
            if (checked) binding.defaultCurrencyControls.isVisible = binding.typeToggle.checkedButtonId == R.id.typeIncome
        }
        val categoryAdapter = CategoryAdapter()
        val incomeSourceAdapter = CategoryAdapter(showDefaultCurrency = true)
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        val initialTab = viewModel.selectedTab.value
        binding.categoryForm.isVisible = initialTab == CategoriesTab.CATEGORIES
        binding.categoryTabs.getTabAt(initialTab.ordinal)?.select()
        binding.categoryTabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                viewModel.selectTab(CategoriesTab.entries[tab.position])
            }

            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })

        binding.addButton.setOnClickListener { submit(binding) }
        binding.nameInput.setOnEditorActionListener { _, actionId, _ ->
            (actionId == EditorInfo.IME_ACTION_DONE).also { if (it) submit(binding) }
        }
        binding.retryButton.setOnClickListener { viewModel.retry() }

        viewModel.screenState.collectWithLifecycle(viewLifecycleOwner) { state ->
            val showingIncomeSources = state.tab == CategoriesTab.INCOME_SOURCES
            if (showingIncomeSources && binding.nameInput.hasFocus()) {
                SoftwareKeyboardControllerCompat(binding.nameInput).hide()
                binding.nameInput.clearFocus()
            }
            binding.categoryForm.isVisible = !showingIncomeSources
            binding.progress.contentDescription = getString(
                if (showingIncomeSources) R.string.income_sources_loading else R.string.categories_loading,
            )
            binding.emptyState.title = getString(
                if (showingIncomeSources) R.string.income_sources_empty_title else R.string.categories_empty_title,
            )
            binding.emptyState.message = getString(
                if (showingIncomeSources) R.string.income_sources_empty_message else R.string.categories_empty_message,
            )
            val adapter = if (showingIncomeSources) incomeSourceAdapter else categoryAdapter
            if (binding.list.adapter !== adapter) binding.list.adapter = adapter
            render(binding, adapter, state.content)
        }
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
        if (viewModel.selectedTab.value != CategoriesTab.CATEGORIES) return
        val type = when (binding.typeToggle.checkedButtonId) {
            R.id.typeIncome -> CategoryType.INCOME
            else -> CategoryType.EXPENSE
        }
        viewModel.addCategory(binding.nameInput.text?.toString().orEmpty(), type, if (type == CategoryType.INCOME) CurrencyOptions.codes.getOrNull(binding.defaultCurrencyInput.selectedItemPosition) else null)
    }

    private fun render(binding: FragmentCategoriesBinding, adapter: CategoryAdapter, state: UiState<List<Category>>) {
        binding.progress.isVisible = state is UiState.Loading
        binding.errorGroup.isVisible = state is UiState.Error
        binding.list.isVisible = state is UiState.Success && state.data.isNotEmpty()
        binding.emptyState.isVisible = state is UiState.Success && state.data.isEmpty()
        when (state) {
            is UiState.Success -> adapter.submitList(state.data)
            is UiState.Error -> binding.errorText.setText(state.error.messageRes)
            UiState.Loading -> Unit
        }
    }
}
