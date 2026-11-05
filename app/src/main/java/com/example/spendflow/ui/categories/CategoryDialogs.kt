package com.example.spendflow.ui.categories

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.window.DialogProperties
import com.example.spendflow.R
import com.example.spendflow.core.ui.CurrencyOptions
import com.example.spendflow.core.ui.components.DropdownField
import com.example.spendflow.core.ui.components.LabeledTextField
import com.example.spendflow.core.ui.components.ThemePreviews
import com.example.spendflow.domain.model.Category
import com.example.spendflow.domain.model.CategoryType
import com.example.spendflow.ui.theme.SpendFlowTheme

private const val NAME_MAX_LENGTH = 100

/** Add or rename an expense category with its icon and classification. */
@Composable
internal fun ExpenseCategoryDialog(
    category: Category?,
    saving: Boolean,
    @StringRes error: Int?,
    onSave: (name: String, icon: String, classification: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(category?.name.orEmpty()) }
    var icon by rememberSaveable { mutableIntStateOf(CategoryAppearance.icons.indexOf(category?.iconName).coerceAtLeast(0)) }
    var classification by rememberSaveable {
        mutableIntStateOf(CategoryAppearance.classifications.indexOf(category?.classification).coerceAtLeast(0))
    }
    FormDialog(
        title = if (category == null) R.string.category_add_title else R.string.category_edit_title,
        confirm = R.string.quick_save,
        saving = saving,
        onConfirm = { onSave(name, CategoryAppearance.icons[icon], CategoryAppearance.classifications[classification]) },
        onDismiss = onDismiss,
    ) {
        LabeledTextField(
            label = stringResource(R.string.categories_name_hint),
            value = name,
            onValueChange = { name = it },
            error = error?.let { stringResource(it) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            maxLength = NAME_MAX_LENGTH,
        )
        DropdownField(
            label = stringResource(R.string.category_icon),
            options = CategoryAppearance.iconLabels.map { stringResource(it) },
            selectedIndex = icon,
            onSelect = { icon = it },
        )
        DropdownField(
            label = stringResource(R.string.category_classification),
            options = CategoryAppearance.classificationLabels.map { stringResource(it) },
            selectedIndex = classification,
            onSelect = { classification = it },
        )
    }
}

/** Monthly cap in the base currency; [initialAmount] pre-fills an existing cap. */
@Composable
internal fun CategoryCapDialog(
    initialAmount: String,
    saving: Boolean,
    @StringRes error: Int?,
    onSave: (amount: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var amount by rememberSaveable { mutableStateOf(initialAmount) }
    FormDialog(R.string.category_cap, R.string.quick_save, saving, onConfirm = { onSave(amount) }, onDismiss = onDismiss) {
        LabeledTextField(
            label = stringResource(R.string.category_cap_hint),
            value = amount,
            onValueChange = { amount = it },
            error = error?.let { stringResource(it) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
    }
}

/** Add an income source with its default currency, or rename one (the currency is then fixed). */
@Composable
internal fun IncomeSourceDialog(
    source: Category?,
    saving: Boolean,
    errors: IncomeSourceFormErrors,
    @StringRes writeError: Int?,
    onSave: (name: String, currency: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(source?.name.orEmpty()) }
    var currency by rememberSaveable { mutableIntStateOf(CurrencyOptions.codes.indexOf("LKR")) }
    FormDialog(
        title = if (source == null) R.string.income_sources_add else R.string.income_sources_rename_title,
        confirm = R.string.quick_save,
        saving = saving,
        onConfirm = { onSave(name, if (source == null) CurrencyOptions.codes.getOrNull(currency) else null) },
        onDismiss = onDismiss,
        dismissOnClickOutside = false,
    ) {
        LabeledTextField(
            label = stringResource(R.string.income_sources_name),
            value = name,
            onValueChange = { name = it },
            error = errors.name?.let { stringResource(it) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            maxLength = NAME_MAX_LENGTH,
        )
        if (source == null) {
            DropdownField(
                label = stringResource(R.string.income_default_currency),
                options = CurrencyOptions.labels,
                selectedIndex = currency,
                onSelect = { currency = it },
                error = errors.currency?.let { stringResource(it) },
            )
        }
        if (writeError != null) {
            Text(
                stringResource(writeError),
                Modifier
                    .padding(top = dimensionResource(R.dimen.sf_space_s))
                    .semantics { liveRegion = LiveRegionMode.Polite },
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
internal fun ArchiveIncomeSourceDialog(source: Category, saving: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    FormDialog(
        title = R.string.income_sources_archive_title,
        confirm = R.string.income_sources_archive,
        saving = saving,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        dismissOnClickOutside = false,
    ) {
        Text(stringResource(R.string.income_sources_archive_message, source.name))
    }
}

/** Dialog whose buttons and dismissal are locked while [saving], matching the View dialogs. */
@Composable
private fun FormDialog(
    @StringRes title: Int,
    @StringRes confirm: Int,
    saving: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissOnClickOutside: Boolean = true,
    content: @Composable () -> Unit,
) {
    // Violet is too dark as text on the dark dialog surface
    val buttonColors = ButtonDefaults.textButtonColors(contentColor = SpendFlowTheme.colors.violetText)
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(stringResource(title)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) { content() } },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !saving, colors = buttonColors) { Text(stringResource(confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving, colors = buttonColors) { Text(stringResource(R.string.action_cancel)) }
        },
        properties = DialogProperties(dismissOnBackPress = !saving, dismissOnClickOutside = dismissOnClickOutside && !saving),
    )
}

@ThemePreviews
@Composable
private fun ExpenseCategoryDialogPreview() = SpendFlowTheme {
    ExpenseCategoryDialog(
        Category("food", "Groceries", CategoryType.EXPENSE, iconName = "food", classification = "NEEDS"),
        saving = false, error = R.string.category_duplicate, onSave = { _, _, _ -> }, onDismiss = {},
    )
}

@ThemePreviews
@Composable
private fun IncomeSourceDialogPreview() = SpendFlowTheme {
    IncomeSourceDialog(
        source = null, saving = false,
        errors = IncomeSourceFormErrors(name = R.string.income_sources_name_required),
        writeError = R.string.error_network, onSave = { _, _ -> }, onDismiss = {},
    )
}
