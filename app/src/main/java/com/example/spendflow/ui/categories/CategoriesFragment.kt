package com.example.spendflow.ui.categories

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
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
import com.example.spendflow.databinding.DialogIncomeSourceBinding
import com.example.spendflow.databinding.FragmentCategoriesBinding
import com.example.spendflow.domain.model.Category
import com.example.spendflow.domain.model.CategoryType
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import dagger.hilt.android.AndroidEntryPoint

/** Renders [CategoriesViewModel] state and forwards input. No business logic lives here. */
@AndroidEntryPoint
class CategoriesFragment : Fragment(R.layout.fragment_categories) {

    private val viewModel: CategoriesViewModel by viewModels()
    private var incomeSourceDialog: AlertDialog? = null
    private var incomeSourceForm: DialogIncomeSourceBinding? = null

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
        val incomeSourceAdapter = CategoryAdapter(showDefaultCurrency = true, onActions = ::showIncomeSourceActions)
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
        binding.addIncomeSourceButton.setOnClickListener { showIncomeSourceForm(null) }
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
            binding.addIncomeSourceButton.isVisible = showingIncomeSources
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
        viewModel.incomeSourceSaving.collectWithLifecycle(viewLifecycleOwner) { saving ->
            binding.addIncomeSourceButton.isEnabled = !saving
            renderIncomeSourceSaving(saving)
        }
        viewModel.events.collectWithLifecycle(viewLifecycleOwner) { event ->
            when (event) {
                CategoriesEvent.CategoryAdded -> binding.nameInput.text = null
                CategoriesEvent.IncomeSourceSaved -> incomeSourceDialog?.dismiss()
                is CategoriesEvent.IncomeSourceWriteFailed -> {
                    val form = incomeSourceForm
                    if (form != null) {
                        form.writeError.setText(event.error.messageRes)
                        form.writeError.isVisible = true
                    } else {
                        incomeSourceDialog?.dismiss()
                        SpendFlowSnackbar.make(view, getString(event.error.messageRes), SpendFlowSnackbar.Tone.ERROR, duration = Snackbar.LENGTH_LONG).show()
                    }
                }
                is CategoriesEvent.ShowMessage ->
                    SpendFlowSnackbar.make(view, getString(event.messageRes)).show()
                is CategoriesEvent.ShowError ->
                    SpendFlowSnackbar.make(view, getString(event.error.messageRes), SpendFlowSnackbar.Tone.ERROR, duration = Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun showIncomeSourceActions(anchor: View, source: Category) {
        if (viewModel.incomeSourceSaving.value || incomeSourceDialog != null) return
        PopupMenu(requireContext(), anchor).apply {
            inflate(R.menu.income_source_actions)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.renameIncomeSource -> showIncomeSourceForm(source)
                    R.id.archiveIncomeSource -> confirmArchiveIncomeSource(source)
                    else -> return@setOnMenuItemClickListener false
                }
                true
            }
            show()
        }
    }

    private fun showIncomeSourceForm(source: Category?) {
        if (viewModel.incomeSourceSaving.value || incomeSourceDialog != null) return
        val builder = MaterialAlertDialogBuilder(requireContext())
        val form = DialogIncomeSourceBinding.inflate(layoutInflater.cloneInContext(builder.context))
        form.sourceName.setText(source?.name.orEmpty())
        form.currencyControls.isVisible = source == null
        if (source == null) {
            form.sourceCurrency.adapter = ArrayAdapter(
                form.root.context, android.R.layout.simple_spinner_dropdown_item, CurrencyOptions.labels,
            )
            form.sourceCurrency.setSelection(CurrencyOptions.codes.indexOf("LKR"))
        }
        incomeSourceForm = form
        showIncomeSourceDialog(
            builder.setTitle(if (source == null) R.string.income_sources_add else R.string.income_sources_rename_title)
                .setView(form.root),
            R.string.quick_save,
        ) {
            form.writeError.isVisible = false
            val errors = viewModel.saveIncomeSource(
                source?.id,
                form.sourceName.text?.toString().orEmpty(),
                if (source == null) CurrencyOptions.codes.getOrNull(form.sourceCurrency.selectedItemPosition) else null,
            )
            form.sourceNameLayout.error = errors.name?.let { getString(it) }
            form.currencyError.text = errors.currency?.let { getString(it) }
            form.currencyError.isVisible = errors.currency != null
            if (errors.name != null) form.sourceName.requestFocus()
        }
    }

    private fun confirmArchiveIncomeSource(source: Category) {
        if (viewModel.incomeSourceSaving.value || incomeSourceDialog != null) return
        showIncomeSourceDialog(
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.income_sources_archive_title)
                .setMessage(getString(R.string.income_sources_archive_message, source.name)),
            R.string.income_sources_archive,
        ) { viewModel.archiveIncomeSource(source.id) }
    }

    private fun showIncomeSourceDialog(builder: MaterialAlertDialogBuilder, action: Int, confirm: () -> Unit) {
        val dialog = builder.setNegativeButton(R.string.action_cancel, null).setPositiveButton(action, null).create()
        incomeSourceDialog = dialog
        dialog.setCanceledOnTouchOutside(false)
        dialog.setOnDismissListener {
            if (incomeSourceDialog === dialog) {
                incomeSourceDialog = null
                incomeSourceForm = null
            }
        }
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { confirm() }
            renderIncomeSourceSaving(viewModel.incomeSourceSaving.value)
        }
        dialog.show()
    }

    private fun renderIncomeSourceSaving(saving: Boolean) {
        incomeSourceDialog?.let { dialog ->
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = !saving
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.isEnabled = !saving
            dialog.setCancelable(!saving)
        }
    }

    override fun onDestroyView() {
        incomeSourceDialog?.dismiss()
        incomeSourceDialog = null
        incomeSourceForm = null
        super.onDestroyView()
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
