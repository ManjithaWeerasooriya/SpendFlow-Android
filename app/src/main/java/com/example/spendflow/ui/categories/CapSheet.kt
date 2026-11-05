package com.example.spendflow.ui.categories

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.spendflow.R
import com.example.spendflow.core.common.AppResult
import com.example.spendflow.core.ui.components.LabeledTextField
import com.example.spendflow.core.ui.components.PrimaryButton
import com.example.spendflow.core.ui.components.ThemePreviews
import com.example.spendflow.core.ui.messageRes
import com.example.spendflow.domain.model.Budget
import com.example.spendflow.domain.model.BudgetPeriod
import com.example.spendflow.domain.model.CapLevel
import com.example.spendflow.domain.model.CapThresholds
import com.example.spendflow.domain.model.Category
import com.example.spendflow.domain.model.CategoryCaps
import com.example.spendflow.domain.model.CategoryType
import com.example.spendflow.ui.theme.SpendFlowTheme
import java.math.BigDecimal
import java.util.Currency

/**
 * 07b Set / edit cap, as a bottom sheet. It waits for cap data before filling the form, since an unresolved
 * cap must not look like a new one, and keeps the draft while the data briefly errors.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ObservedCapSheet(
    category: Category,
    caps: AppResult<List<Budget>>?,
    currencyCode: String,
    thresholds: CapThresholds,
    saving: Boolean,
    @StringRes error: Int?,
    onSave: (amount: String, alerts: Set<CapLevel>) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val formState = rememberSaveableStateHolder()
    ModalBottomSheet(
        onDismissRequest = { if (!saving) onDismiss() },
        sheetState = sheet,
        containerColor = SpendFlowTheme.colors.insetCard,
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(
                    start = dimensionResource(R.dimen.sf_screen_padding),
                    end = dimensionResource(R.dimen.sf_screen_padding),
                    bottom = dimensionResource(R.dimen.sf_space_l),
                ),
        ) {
            SheetHeader(category.name, saving, onDismiss)
            if (caps is AppResult.Success) {
                val cap = CategoryCaps.active(caps.data)[category.id]
                formState.SaveableStateProvider(category.id) {
                    CapForm(cap, cap?.limit?.currencyCode ?: currencyCode, thresholds, saving, error, onSave, onRemove)
                }
            } else {
                Row(
                    Modifier.padding(vertical = dimensionResource(R.dimen.sf_space_xxl)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (caps == null) {
                        CircularProgressIndicator(Modifier.padding(end = dimensionResource(R.dimen.sf_space_m)))
                    }
                    Text(
                        stringResource(if (caps is AppResult.Error) caps.error.messageRes else R.string.category_cap_loading),
                        Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        color = SpendFlowTheme.colors.textBody,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun SheetHeader(name: String, saving: Boolean, onClose: () -> Unit) {
    val colors = SpendFlowTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.cap_sheet_title, name),
            Modifier
                .weight(1f)
                .semantics { heading() },
            color = colors.textHeading,
            style = MaterialTheme.typography.headlineSmall,
        )
        IconButton(
            onClick = onClose,
            enabled = !saving,
            modifier = Modifier.background(colors.tile, CircleShape),
        ) {
            Icon(painterResource(R.drawable.ic_close), stringResource(R.string.action_close), tint = colors.textHeading)
        }
    }
    Text(
        stringResource(R.string.cap_sheet_subtitle),
        Modifier.padding(top = dimensionResource(R.dimen.sf_space_xs)),
        color = colors.textMuted,
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun ColumnScope.CapForm(
    cap: Budget?,
    currencyCode: String,
    thresholds: CapThresholds,
    saving: Boolean,
    @StringRes error: Int?,
    onSave: (amount: String, alerts: Set<CapLevel>) -> Unit,
    onRemove: () -> Unit,
) {
    val colors = SpendFlowTheme.colors
    val alerts = cap?.alerts ?: CapLevel.ALERTS
    var amount by rememberSaveable { mutableStateOf(cap?.limit?.let(::plainAmount).orEmpty()) }
    var notice by rememberSaveable { mutableStateOf(CapLevel.NOTICE in alerts) }
    var warning by rememberSaveable { mutableStateOf(CapLevel.WARNING in alerts) }
    var over by rememberSaveable { mutableStateOf(CapLevel.OVER in alerts) }

    LabeledTextField(
        label = stringResource(R.string.cap_amount),
        value = amount,
        onValueChange = { amount = it },
        fieldModifier = Modifier.testTag(CapSheetTags.AMOUNT),
        error = error?.let { stringResource(it) },
        enabled = !saving,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        trailingIcon = { Text(currencyCode, color = colors.textMuted, style = MaterialTheme.typography.bodyLarge) },
    )
    Text(
        stringResource(R.string.cap_alert_me_at),
        Modifier.padding(top = dimensionResource(R.dimen.sf_space_l), bottom = dimensionResource(R.dimen.sf_space_xs)),
        color = colors.textHeading,
        style = MaterialTheme.typography.titleSmall,
    )
    AlertOption(stringResource(R.string.cap_alert_used, thresholds.notice), notice, !saving) { notice = it }
    AlertOption(stringResource(R.string.cap_alert_used, thresholds.warning), warning, !saving) { warning = it }
    AlertOption(stringResource(R.string.cap_alert_reached, thresholds.over), over, !saving) { over = it }
    Row(
        Modifier
            .padding(top = dimensionResource(R.dimen.sf_space_m))
            .fillMaxWidth()
            .background(colors.violetTint, MaterialTheme.shapes.medium)
            .padding(dimensionResource(R.dimen.sf_space_l)),
    ) {
        Icon(painterResource(R.drawable.ic_info), contentDescription = null, tint = colors.violetText)
        Spacer(Modifier.width(dimensionResource(R.dimen.sf_space_m)))
        Text(stringResource(R.string.cap_never_blocks), color = colors.violetText, style = MaterialTheme.typography.bodyMedium)
    }
    PrimaryButton(
        stringResource(R.string.quick_save),
        onClick = {
            val chosen = buildSet {
                if (notice) add(CapLevel.NOTICE)
                if (warning) add(CapLevel.WARNING)
                if (over) add(CapLevel.OVER)
            }
            onSave(amount, chosen)
        },
        enabled = !saving,
        modifier = Modifier
            .padding(top = dimensionResource(R.dimen.sf_space_l))
            .fillMaxWidth(),
    )
    if (cap != null) {
        TextButton(
            onClick = onRemove,
            enabled = !saving,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = dimensionResource(R.dimen.sf_space_xs))
                .heightIn(min = 48.dp),
        ) {
            Text(stringResource(R.string.cap_remove), color = colors.textHeading, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun AlertOption(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors = CheckboxDefaults.colors(checkedColor = SpendFlowTheme.colors.violet),
        )
        Spacer(Modifier.width(dimensionResource(R.dimen.sf_space_m)))
        Text(label, color = SpendFlowTheme.colors.textHeading, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun plainAmount(money: com.example.spendflow.domain.model.Money): String =
    BigDecimal.valueOf(money.amountMinor, Currency.getInstance(money.currencyCode).defaultFractionDigits.coerceAtLeast(0))
        .toPlainString()

internal object CapSheetTags {
    const val AMOUNT = "cap_amount"
}

@ThemePreviews
@Composable
private fun CapSheetPreview() = SpendFlowTheme {
    val shopping = Category("shopping", "Shopping", CategoryType.EXPENSE, iconName = "shopping")
    val cap = Budget(
        "cap", "Shopping", com.example.spendflow.domain.model.Money(10_000_00, "LKR"), BudgetPeriod.MONTHLY,
        java.time.LocalDate.of(2026, 10, 1), shopping.id,
    )
    ObservedCapSheet(
        shopping, AppResult.Success(listOf(cap)), "LKR", CapThresholds.DEFAULT, saving = false,
        error = R.string.category_cap_positive, onSave = { _, _ -> }, onRemove = {}, onDismiss = {},
    )
}
