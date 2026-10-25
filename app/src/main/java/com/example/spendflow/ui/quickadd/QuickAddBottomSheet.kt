package com.example.spendflow.ui.quickadd

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.core.view.isVisible
import androidx.core.view.children
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.viewModels
import com.example.spendflow.core.ui.CurrencyOptions
import com.example.spendflow.R
import com.example.spendflow.core.common.AppResult
import com.example.spendflow.core.ui.collectWithLifecycle
import com.example.spendflow.core.ui.messageRes
import com.example.spendflow.databinding.SheetQuickAddBinding
import com.example.spendflow.domain.model.CategoryType
import com.example.spendflow.domain.model.PaymentMethod
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.example.spendflow.core.ui.components.CategoryChip
import dagger.hilt.android.AndroidEntryPoint
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@AndroidEntryPoint
class QuickAddBottomSheet : BottomSheetDialogFragment(R.layout.sheet_quick_add) {
    private val model: QuickAddViewModel by viewModels()

    override fun onStart() {
        super.onStart()
        (dialog as? com.google.android.material.bottomsheet.BottomSheetDialog)?.behavior?.state =
            com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val b = SheetQuickAddBinding.bind(view)
        if (model.completed) { dismiss(); return }
        b.closeButton.setOnClickListener { dismiss() }
        b.amountInput.setText(model.draft.get<String>("amount").orEmpty())
        b.noteInput.setText(model.draft.get<String>("note").orEmpty())
        b.amountInput.doAfterTextChanged { model.draft["amount"] = it.toString() }
        b.noteInput.doAfterTextChanged { model.draft["note"] = it.toString() }
        b.typeToggle.check(if (model.draft.get<Boolean>("income") == true) R.id.typeIncome else R.id.typeExpense)
        val labels = listOf(R.string.quick_none, R.string.quick_cash, R.string.quick_debit, R.string.quick_credit, R.string.quick_bank, R.string.quick_wallet, R.string.quick_other)
        b.paymentInput.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, labels.map(::getString))
        b.paymentInput.isEnabled = b.typeToggle.checkedButtonId != R.id.typeIncome
        b.details.isVisible = model.draft.get<Boolean>("details") == true
        b.moreButton.setOnClickListener {
            b.details.isVisible = !b.details.isVisible
            model.draft["details"] = b.details.isVisible
        }
        val currencies = CurrencyOptions.codes
        b.currencyInput.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, CurrencyOptions.labels)
        var renderingRate = false
        fun renderConversion() {
            val income = b.typeToggle.checkedButtonId == R.id.typeIncome
            val base = (model.profile.value as? AppResult.Success)?.data?.baseCurrency ?: "LKR"
            val currency = model.draft.get<String>("currency") ?: base
            b.currencyControls.isVisible = income
            b.rateControls.isVisible = currency != base
            b.categoryLabel.setText(if (income) R.string.income_source else R.string.quick_category)
            val position = currencies.indexOf(currency)
            if (position >= 0 && b.currencyInput.selectedItemPosition != position) b.currencyInput.setSelection(position)
            val rate = model.draft.get<String>("rate").orEmpty()
            if (b.rateInput.text.toString() != rate) {
                renderingRate = true
                b.rateInput.setText(rate)
                renderingRate = false
            }
            val converted = model.conversion(b.amountInput.text.toString(), income)?.first
            b.basePreview.text = if (converted == null) getString(R.string.income_preview_pending) else {
                val digits = java.util.Currency.getInstance(converted.currencyCode).defaultFractionDigits
                val value = java.math.BigDecimal.valueOf(converted.amountMinor).movePointLeft(digits)
                getString(R.string.income_preview, converted.currencyCode, java.text.NumberFormat.getNumberInstance().format(value))
            }
        }
        b.currencyInput.setSelection(currencies.indexOf(model.draft.get<String>("currency") ?: (model.profile.value as? AppResult.Success)?.data?.baseCurrency ?: "LKR").coerceAtLeast(0))
        b.currencyInput.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, item: View?, position: Int, id: Long) {
                val base = (model.profile.value as? AppResult.Success)?.data?.baseCurrency ?: return
                val selected = currencies[position]
                if (selected != (model.draft.get<String>("currency") ?: base)) model.selectCurrency(selected)
                renderConversion()
            }
        }
        b.rateInput.doAfterTextChanged { if (!renderingRate) { model.editRate(it.toString()); renderConversion() } }
        b.amountInput.doAfterTextChanged { renderConversion() }
        b.latestRateButton.setOnClickListener {
            if (model.draft.get<String>("currency") == null) {
                model.selectCurrency((model.profile.value as? AppResult.Success)?.data?.baseCurrency ?: "LKR")
            }
            model.fetchRate()
        }
        model.profile.collectWithLifecycle(viewLifecycleOwner) { renderConversion() }
        model.draft.getStateFlow("rate", "").collectWithLifecycle(viewLifecycleOwner) { renderConversion() }
        model.rateLoading.collectWithLifecycle(viewLifecycleOwner) { b.latestRateButton.isEnabled = !it }
        model.rateError.collectWithLifecycle(viewLifecycleOwner) { b.rateError.isVisible = it }
        fun renderCategories() {
            val result = model.categories.value
            val income = b.typeToggle.checkedButtonId == R.id.typeIncome
            val items = (result as? AppResult.Success)?.data.orEmpty().filter { it.type == if (income) CategoryType.INCOME else CategoryType.EXPENSE }
            b.categories.removeAllViews()
            items.forEach { category ->
                b.categories.addView(CategoryChip(requireContext()).apply {
                    id = View.generateViewId()
                    label = category.name
                    setIcon(if (income) R.drawable.ic_badge_income else R.drawable.ic_badge_expense)
                    isChecked = model.draft.get<String>("category") == category.id
                    setOnClickListener {
                        model.selectSource(category)
                        renderConversion()
                        b.categories.children.forEach { child -> (child as? CategoryChip)?.isChecked = child === this }
                    }
                })
            }
            b.errorText.isVisible = result is AppResult.Error || (result is AppResult.Success && items.isEmpty())
            b.errorText.setText(if (result is AppResult.Error) result.error.messageRes else R.string.quick_empty)
        }
        b.typeToggle.addOnButtonCheckedListener { _, _, checked -> if (checked) {
            model.draft["income"] = b.typeToggle.checkedButtonId == R.id.typeIncome
            model.draft["category"] = null
            b.paymentInput.isEnabled = b.typeToggle.checkedButtonId != R.id.typeIncome
            renderCategories()
            renderConversion()
        } }
        model.categories.collectWithLifecycle(viewLifecycleOwner) { renderCategories() }
        fun renderDate() {
            val date = model.draft.get<String>("date")?.let(Instant::parse)
            b.dateButton.text = getString(R.string.quick_date, date?.atZone(ZoneId.systemDefault())?.format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")) ?: getString(R.string.quick_now))
        }
        renderDate()
        b.dateButton.setOnClickListener {
            val date = (model.draft.get<String>("date")?.let(Instant::parse) ?: Instant.now()).atZone(ZoneId.systemDefault())
            DatePickerDialog(requireContext(), { _, year, month, day ->
                TimePickerDialog(requireContext(), { _, hour, minute ->
                    model.draft["date"] = date.withYear(year).withDayOfMonth(1).withMonth(month + 1).withDayOfMonth(day).withHour(hour).withMinute(minute).withSecond(0).withNano(0).toInstant().toString()
                    renderDate()
                }, date.hour, date.minute, android.text.format.DateFormat.is24HourFormat(requireContext())).show()
            }, date.year, date.monthValue - 1, date.dayOfMonth).show()
        }
        b.saveButton.setOnClickListener {
            val payment = PaymentMethod.entries.getOrNull(b.paymentInput.selectedItemPosition - 1)
            if (!model.save(b.amountInput.text.toString(), model.draft["category"], b.typeToggle.checkedButtonId == R.id.typeIncome, payment, b.noteInput.text.toString())) {
                b.errorText.setText(R.string.quick_invalid)
                b.errorText.isVisible = true
            }
        }
        model.saving.collectWithLifecycle(viewLifecycleOwner) { b.saveButton.isEnabled = !it }
        model.events.collectWithLifecycle(viewLifecycleOwner) { result ->
            when (result) {
                is AppResult.Success -> dismiss()
                is AppResult.Error -> { b.errorText.setText(result.error.messageRes); b.errorText.isVisible = true }
            }
        }
    }
}
