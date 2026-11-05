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
    private val expenseModel: ExpenseCategoriesViewModel by viewModels()
    private var expenseDialog: AlertDialog? = null
    private var showingArchived = false
    private var expenseError: com.google.android.material.textfield.TextInputLayout? = null
    private var dragOrder: MutableList<Category>? = null
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
        val categoryAdapter = CategoryAdapter(onActions = ::showExpenseActions)
        val incomeSourceAdapter = CategoryAdapter(showDefaultCurrency = true, onActions = ::showIncomeSourceActions)
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        val initialTab = viewModel.selectedTab.value
        binding.categoryForm.isVisible = false
        binding.categoryTabs.getTabAt(initialTab.ordinal)?.select()
        binding.categoryTabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                viewModel.selectTab(CategoriesTab.entries[tab.position])
            }

            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })

        showingArchived = savedInstanceState?.getBoolean("expense_archived") ?: false
        fun renderExpenses() {
            if (viewModel.selectedTab.value != CategoriesTab.CATEGORIES || dragOrder != null) return
            val result = expenseModel.categories.value
            val all = (result as? com.example.spendflow.core.common.AppResult.Success)?.data.orEmpty()
            binding.archivedCategories.text = if (showingArchived) getString(R.string.category_active)
                else getString(R.string.category_archived, all.count { it.isArchived })
            render(binding, categoryAdapter, when (result) {
                is com.example.spendflow.core.common.AppResult.Success -> UiState.Success(all.filter { it.isArchived == showingArchived })
                is com.example.spendflow.core.common.AppResult.Error -> UiState.Error(result.error)
                null -> UiState.Loading
            })
            binding.addExpenseCategory.isEnabled = !expenseModel.saving.value && result is com.example.spendflow.core.common.AppResult.Success
            binding.archivedCategories.isEnabled = !expenseModel.saving.value
            binding.reorderHint.isVisible = !showingArchived
        }
        binding.addExpenseCategory.setOnClickListener { showExpenseForm(null) }
        binding.archivedCategories.setOnClickListener {
            showingArchived = !showingArchived
            renderExpenses()
        }
        androidx.recyclerview.widget.ItemTouchHelper(object : androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback(
            androidx.recyclerview.widget.ItemTouchHelper.UP or androidx.recyclerview.widget.ItemTouchHelper.DOWN, 0,
        ) {
            override fun isLongPressDragEnabled() = viewModel.selectedTab.value == CategoriesTab.CATEGORIES &&
                !showingArchived && !expenseModel.saving.value
            override fun onMove(list: androidx.recyclerview.widget.RecyclerView, from: androidx.recyclerview.widget.RecyclerView.ViewHolder,
                to: androidx.recyclerview.widget.RecyclerView.ViewHolder): Boolean {
                val a = from.bindingAdapterPosition
                val b = to.bindingAdapterPosition
                if (a < 0 || b < 0) return false
                val rows = dragOrder ?: categoryAdapter.currentList.toMutableList().also { dragOrder = it }
                rows.add(b, rows.removeAt(a))
                categoryAdapter.submitList(rows.toList())
                return true
            }
            override fun onSwiped(holder: androidx.recyclerview.widget.RecyclerView.ViewHolder, direction: Int) = Unit
            override fun clearView(list: androidx.recyclerview.widget.RecyclerView, holder: androidx.recyclerview.widget.RecyclerView.ViewHolder) {
                super.clearView(list, holder)
                val ids = dragOrder?.map { it.id }
                dragOrder = null
                if (ids != null) expenseModel.reorder(ids)
            }
        }).attachToRecyclerView(binding.list)
        expenseModel.categories.collectWithLifecycle(viewLifecycleOwner) { renderExpenses() }
        expenseModel.saving.collectWithLifecycle(viewLifecycleOwner) { saving ->
            expenseDialog?.apply {
                getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = !saving
                getButton(AlertDialog.BUTTON_NEGATIVE)?.isEnabled = !saving
                setCancelable(!saving)
            }
            renderExpenses()
        }
        expenseModel.events.collectWithLifecycle(viewLifecycleOwner) { result ->
            when (result) {
                is com.example.spendflow.core.common.AppResult.Success -> expenseDialog?.dismiss()
                is com.example.spendflow.core.common.AppResult.Error -> {
                    expenseError?.error = getString(result.error.messageRes)
                    SpendFlowSnackbar.make(view, getString(result.error.messageRes), SpendFlowSnackbar.Tone.ERROR).show()
                }
            }
            renderExpenses()
        }
        binding.addButton.setOnClickListener { submit(binding) }
        binding.addIncomeSourceButton.setOnClickListener { showIncomeSourceForm(null) }
        binding.nameInput.setOnEditorActionListener { _, actionId, _ ->
            (actionId == EditorInfo.IME_ACTION_DONE).also { if (it) submit(binding) }
        }
        binding.retryButton.setOnClickListener { viewModel.retry(); expenseModel.retry() }

        viewModel.screenState.collectWithLifecycle(viewLifecycleOwner) { state ->
            val showingIncomeSources = state.tab == CategoriesTab.INCOME_SOURCES
            if (showingIncomeSources && binding.nameInput.hasFocus()) {
                SoftwareKeyboardControllerCompat(binding.nameInput).hide()
                binding.nameInput.clearFocus()
            }
            binding.categoryForm.isVisible = false
            binding.addExpenseCategory.isVisible = !showingIncomeSources
            binding.archivedCategories.isVisible = !showingIncomeSources
            binding.reorderHint.isVisible = !showingIncomeSources && !showingArchived
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
            if (showingIncomeSources) render(binding, adapter, state.content) else renderExpenses()
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

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("expense_archived", showingArchived)
        super.onSaveInstanceState(outState)
    }

    private fun showExpenseActions(anchor: View, category: Category) {
        if (expenseModel.saving.value || expenseDialog != null) return
        PopupMenu(requireContext(), anchor).apply {
            if (category.isArchived) {
                menu.add(0, 3, 0, R.string.category_restore)
            } else {
                menu.add(0, 1, 0, R.string.category_rename)
                menu.add(0, 2, 1, R.string.category_cap)
                menu.add(0, 3, 2, R.string.category_archive)
                menu.add(0, 4, 3, R.string.category_move_up)
                menu.add(0, 5, 4, R.string.category_move_down)
            }
            setOnMenuItemClickListener {
                when (it.itemId) {
                    1 -> showExpenseForm(category)
                    2 -> showExpenseForm(category, cap = true)
                    3 -> expenseModel.archive(category, !category.isArchived)
                    4, 5 -> {
                        val rows = (expenseModel.categories.value as? com.example.spendflow.core.common.AppResult.Success)
                            ?.data.orEmpty().filterNot { row -> row.isArchived }.toMutableList()
                        val index = rows.indexOfFirst { row -> row.id == category.id }
                        val target = index + if (it.itemId == 4) -1 else 1
                        if (index >= 0 && target in rows.indices) {
                            rows.add(target, rows.removeAt(index))
                            expenseModel.reorder(rows.map { row -> row.id })
                        }
                    }
                }
                true
            }
            show()
        }
    }

    private fun showExpenseForm(category: Category?, cap: Boolean = false) {
        if (expenseModel.saving.value || expenseDialog != null) return
        val builder = MaterialAlertDialogBuilder(requireContext())
        val context = builder.context
        val form = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            val padding = resources.getDimensionPixelSize(R.dimen.sf_screen_padding)
            setPadding(padding, padding, padding, padding)
        }
        val inputLayout = com.google.android.material.textfield.TextInputLayout(context).apply {
            hint = getString(if (cap) R.string.category_cap_hint else R.string.categories_name_hint)
        }
        val input = com.google.android.material.textfield.TextInputEditText(context).apply {
            inputType = if (cap) android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                else android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            if (cap && category != null) {
                expenseModel.cap(category)?.limit?.let { money ->
                    val digits = java.util.Currency.getInstance(money.currencyCode).defaultFractionDigits
                    setText(java.math.BigDecimal.valueOf(money.amountMinor, digits).toPlainString())
                }
            } else setText(category?.name.orEmpty())
        }
        inputLayout.addView(input)
        form.addView(inputLayout)
        fun selector(label: Int, values: List<Int>): android.widget.Spinner {
            val spinner = android.widget.Spinner(context).apply {
                id = View.generateViewId()
                adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, values.map { getString(it) })
                minimumHeight = (48 * resources.displayMetrics.density).toInt()
            }
            form.addView(android.widget.TextView(context).apply { setText(label); labelFor = spinner.id })
            form.addView(spinner)
            return spinner
        }
        val icon = if (!cap) selector(R.string.category_icon, CategoryAppearance.iconLabels).apply {
            setSelection(CategoryAppearance.icons.indexOf(category?.iconName).coerceAtLeast(0))
        } else null
        val classification = if (!cap) selector(R.string.category_classification, CategoryAppearance.classificationLabels).apply {
            setSelection(CategoryAppearance.classifications.indexOf(category?.classification).coerceAtLeast(0))
        } else null
        val dialog = builder.setTitle(if (cap) R.string.category_cap else if (category == null) R.string.category_add_title else R.string.category_edit_title)
            .setView(form).setNegativeButton(R.string.action_cancel, null).setPositiveButton(R.string.quick_save, null).create()
        expenseDialog = dialog
        expenseError = inputLayout
        dialog.setOnDismissListener { expenseDialog = null; expenseError = null }
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val error = if (cap && category != null) expenseModel.saveCap(category, input.text.toString())
                    else expenseModel.save(category?.id, input.text.toString(), CategoryAppearance.icons[icon!!.selectedItemPosition],
                        CategoryAppearance.classifications[classification!!.selectedItemPosition])
                inputLayout.error = error?.let { getString(it) }
                if (error != null) input.requestFocus()
            }
        }
        dialog.show()
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
        expenseDialog?.dismiss()
        expenseDialog = null
        expenseError = null
        dragOrder = null
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
