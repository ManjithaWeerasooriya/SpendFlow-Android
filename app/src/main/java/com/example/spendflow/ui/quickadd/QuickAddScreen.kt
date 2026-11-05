package com.example.spendflow.ui.quickadd

import android.text.format.DateFormat
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.spendflow.R
import com.example.spendflow.core.common.AppResult
import com.example.spendflow.core.ui.CurrencyOptions
import com.example.spendflow.core.ui.EventEffect
import com.example.spendflow.core.ui.UiState
import com.example.spendflow.core.ui.components.CategoryChip
import com.example.spendflow.core.ui.components.DropdownField
import com.example.spendflow.core.ui.components.LabeledTextField
import com.example.spendflow.core.ui.components.PrimaryButton
import com.example.spendflow.core.ui.components.ThemePreviews
import com.example.spendflow.core.ui.messageRes
import com.example.spendflow.domain.model.Category
import com.example.spendflow.domain.model.CategoryType
import com.example.spendflow.domain.model.PaymentMethod
import com.example.spendflow.ui.categories.CategoryAppearance
import com.example.spendflow.ui.theme.SpendFlowTheme
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Test tags for the instrumented flow tests, which drive Quick Add through MainActivity. */
internal object QuickAddTags {
    const val AMOUNT = "quick_amount"
    const val AMOUNT_ERROR = "quick_amount_error"
    const val CATEGORY_ERROR = "quick_category_error"
    const val RATE_ERROR = "quick_rate_error"
    const val CURRENCY = "quick_currency"
    const val TYPE_EXPENSE = "quick_type_expense"
    const val TYPE_INCOME = "quick_type_income"
    const val DATE = "quick_date"
    const val DETAILS = "quick_details"
    const val SAVE = "quick_save"
}

private val PAYMENT_LABELS = listOf(
    R.string.quick_none, R.string.quick_cash, R.string.quick_debit, R.string.quick_credit,
    R.string.quick_bank, R.string.quick_wallet, R.string.quick_other,
)

internal data class QuickAddUiState(
    val amount: String = "",
    val note: String = "",
    val showDetails: Boolean = false,
    val income: Boolean = false,
    /** The entry's currency: the selected income currency, or the base currency. */
    val currency: String = "LKR",
    val baseCurrency: String = "LKR",
    val rate: String = "",
    val categoryId: String? = null,
    val date: Instant? = null,
    /** Index into [PAYMENT_LABELS]; 0 is "not specified". */
    val payment: Int = 0,
    val categories: UiState<List<Category>> = UiState.Loading,
    val errors: EntryErrors = EntryErrors(),
    @StringRes val writeError: Int? = null,
    val saving: Boolean = false,
    val rateLoading: Boolean = false,
    val rateFailed: Boolean = false,
    /** The converted base amount, already formatted, or null while it can't be worked out. */
    val preview: String? = null,
)

/**
 * The form keeps every field in [QuickAddViewModel.draft], as the View sheet did, so a recreated or
 * restored sheet shows exactly what was typed.
 */
@Composable
internal fun QuickAddRoute(model: QuickAddViewModel, onSaved: (SavedEntry) -> Unit, onClose: () -> Unit) {
    val draft = model.draft
    val amount by draft.getStateFlow("amount", "").collectAsStateWithLifecycle()
    val note by draft.getStateFlow("note", "").collectAsStateWithLifecycle()
    val details by draft.getStateFlow("details", false).collectAsStateWithLifecycle()
    val income by draft.getStateFlow("income", false).collectAsStateWithLifecycle()
    val currency by draft.getStateFlow<String?>("currency", null).collectAsStateWithLifecycle()
    val rate by draft.getStateFlow("rate", "").collectAsStateWithLifecycle()
    val categoryId by draft.getStateFlow<String?>("category", null).collectAsStateWithLifecycle()
    val date by draft.getStateFlow<String?>("date", null).collectAsStateWithLifecycle()
    val payment by draft.getStateFlow("payment", 0).collectAsStateWithLifecycle()
    val profile by model.profile.collectAsStateWithLifecycle()
    val categories by model.categories.collectAsStateWithLifecycle()
    val errors by model.errors.collectAsStateWithLifecycle()
    val saving by model.saving.collectAsStateWithLifecycle()
    val rateLoading by model.rateLoading.collectAsStateWithLifecycle()
    val rateFailed by model.rateError.collectAsStateWithLifecycle()
    var writeError by remember { mutableStateOf<Int?>(null) }
    val focus = remember { QuickAddFocus() }

    EventEffect(model.events) { result ->
        when (result) {
            is AppResult.Success -> {
                model.takeSavedEntry()?.let(onSaved)
                onClose()
            }
            is AppResult.Error -> writeError = result.error.messageRes
        }
    }

    val base = (profile as? AppResult.Success)?.data?.baseCurrency ?: "LKR"
    val type = if (income) CategoryType.INCOME else CategoryType.EXPENSE
    val preview = model.conversion(amount, income)?.first?.let { converted ->
        val digits = java.util.Currency.getInstance(converted.currencyCode).defaultFractionDigits
        stringResource(
            R.string.income_preview,
            converted.currencyCode,
            NumberFormat.getNumberInstance().format(BigDecimal.valueOf(converted.amountMinor).movePointLeft(digits)),
        )
    }
    QuickAddForm(
        state = QuickAddUiState(
            amount = amount,
            note = note,
            showDetails = details,
            income = income,
            currency = currency ?: base,
            baseCurrency = base,
            rate = rate,
            categoryId = categoryId,
            date = date?.let(Instant::parse),
            payment = payment,
            categories = when (val result = categories) {
                null -> UiState.Loading
                is AppResult.Error -> UiState.Error(result.error)
                is AppResult.Success -> UiState.Success(result.data.filter { it.type == type })
            },
            errors = errors,
            writeError = writeError,
            saving = saving,
            rateLoading = rateLoading,
            rateFailed = rateFailed,
            preview = preview,
        ),
        focus = focus,
        onAmount = { draft["amount"] = it },
        onNote = { draft["note"] = it },
        onToggleDetails = { draft["details"] = !details },
        onType = {
            draft["income"] = it
            draft["category"] = null
        },
        onCurrency = { if (it != (currency ?: base)) model.selectCurrency(it) },
        onRate = model::editRate,
        onLatestRate = {
            if (currency == null) model.selectCurrency(base)
            model.fetchRate()
        },
        onCategory = model::selectSource,
        onDate = { draft["date"] = it.toString() },
        onPayment = { draft["payment"] = it },
        onSave = {
            writeError = null
            model.save(amount, categoryId, income, PaymentMethod.entries.getOrNull(payment - 1), note)
            val result = model.errors.value
            when {
                result.amount != null -> focus.amount.requestFocus()
                result.rate != null -> focus.rate.requestFocus()
            }
        },
        onClose = onClose,
    )
}

/** Fields that take focus after a failed save, as the View sheet did. */
internal class QuickAddFocus {
    val amount = FocusRequester()
    val rate = FocusRequester()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun QuickAddForm(
    state: QuickAddUiState,
    onAmount: (String) -> Unit,
    onNote: (String) -> Unit,
    onToggleDetails: () -> Unit,
    onType: (Boolean) -> Unit,
    onCurrency: (String) -> Unit,
    onRate: (String) -> Unit,
    onLatestRate: () -> Unit,
    onCategory: (Category) -> Unit,
    onDate: (Instant) -> Unit,
    onPayment: (Int) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    focus: QuickAddFocus = remember { QuickAddFocus() },
) {
    val colors = SpendFlowTheme.colors
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    var pickedDay by rememberSaveable { mutableStateOf<Long?>(null) }
    Column(
        modifier
            .fillMaxWidth()
            // Lets the bottom sheet's drag and this scroll share gestures
            .nestedScroll(rememberNestedScrollInteropConnection())
            .verticalScroll(rememberScrollState())
            .padding(
                start = dimensionResource(R.dimen.sf_screen_padding),
                end = dimensionResource(R.dimen.sf_screen_padding),
                bottom = dimensionResource(R.dimen.sf_space_xxxl),
            ),
    ) {
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .padding(vertical = dimensionResource(R.dimen.sf_space_l))
                .size(width = 32.dp, height = 4.dp)
                .background(MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.shapes.extraSmall)
                .clearAndSetSemantics {},
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.quick_add_title),
                Modifier
                    .weight(1f)
                    .semantics { heading() },
                color = colors.textHeading,
                style = MaterialTheme.typography.titleLarge,
            )
            FilledTonalIconButton(
                onClick = onClose,
                colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = colors.tile, contentColor = colors.textHeading),
            ) { Icon(painterResource(R.drawable.ic_close), stringResource(R.string.action_close)) }
        }
        SingleChoiceSegmentedButtonRow(Modifier.padding(top = dimensionResource(R.dimen.sf_space_s))) {
            listOf(false to R.string.category_type_expense, true to R.string.category_type_income).forEachIndexed { i, (value, label) ->
                SegmentedButton(
                    selected = state.income == value,
                    onClick = { if (state.income != value) onType(value) },
                    shape = SegmentedButtonDefaults.itemShape(i, 2),
                    modifier = Modifier.testTag(if (value) QuickAddTags.TYPE_INCOME else QuickAddTags.TYPE_EXPENSE),
                    icon = {},
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = colors.tile,
                        activeContentColor = colors.textHeading,
                        activeBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        inactiveContainerColor = Color.Transparent,
                        inactiveContentColor = colors.textHeading,
                        inactiveBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    ),
                ) { Text(stringResource(label), style = MaterialTheme.typography.labelLarge) }
            }
        }
        TextField(
            value = state.amount,
            onValueChange = { if (it.length <= 40) onAmount(it) },
            modifier = Modifier
                .padding(top = dimensionResource(R.dimen.sf_space_s))
                .fillMaxWidth()
                .focusRequester(focus.amount)
                .testTag(QuickAddTags.AMOUNT),
            textStyle = MaterialTheme.typography.displaySmall.copy(fontSize = 40.sp, color = colors.textHeading),
            placeholder = { Text(stringResource(R.string.quick_amount), style = MaterialTheme.typography.displaySmall.copy(fontSize = 40.sp)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            isError = state.errors.amount != null,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                errorContainerColor = Color.Transparent,
                focusedIndicatorColor = colors.violet,
                cursorColor = colors.violet,
                focusedPlaceholderColor = colors.textMuted,
                unfocusedPlaceholderColor = colors.textMuted,
            ),
        )
        ErrorText(state.errors.amount, QuickAddTags.AMOUNT_ERROR)
        if (state.income) {
            DropdownField(
                label = stringResource(R.string.income_currency),
                options = CurrencyOptions.labels,
                selectedIndex = CurrencyOptions.codes.indexOf(state.currency).coerceAtLeast(0),
                onSelect = { onCurrency(CurrencyOptions.codes[it]) },
                fieldModifier = Modifier.testTag(QuickAddTags.CURRENCY),
            )
            if (state.currency != state.baseCurrency) {
                LabeledTextField(
                    label = stringResource(R.string.income_rate),
                    value = state.rate,
                    onValueChange = onRate,
                    fieldModifier = Modifier.focusRequester(focus.rate),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    maxLength = 24,
                )
                ErrorText(state.errors.rate, QuickAddTags.RATE_ERROR)
                TextButton(
                    onClick = onLatestRate,
                    enabled = !state.rateLoading,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColors(contentColor = colors.violetText),
                ) { Text(stringResource(R.string.income_latest_rate), style = MaterialTheme.typography.labelLarge) }
                if (state.rateFailed) StatusText(stringResource(R.string.income_rate_error))
                AttributionText(stringResource(R.string.income_rate_attribution))
            }
            StatusText(state.preview ?: stringResource(R.string.income_preview_pending))
        }
        Text(
            stringResource(if (state.income) R.string.income_source else R.string.quick_category),
            Modifier
                .padding(top = dimensionResource(R.dimen.sf_space_l))
                .semantics { heading() },
            color = colors.textHeading,
            style = MaterialTheme.typography.titleMedium,
        )
        val categories = (state.categories as? UiState.Success)?.data.orEmpty()
        FlowRow(
            Modifier.padding(top = dimensionResource(R.dimen.sf_space_s)),
            horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.sf_space_s)),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.sf_space_s)),
        ) {
            categories.forEach { category ->
                CategoryChip(
                    label = category.name,
                    icon = if (state.income) R.drawable.ic_badge_income else CategoryAppearance.icon(category.iconName),
                    selected = state.categoryId == category.id,
                    onClick = { onCategory(category) },
                )
            }
        }
        ErrorText(state.errors.category, QuickAddTags.CATEGORY_ERROR)
        val message = state.writeError ?: state.errors.general ?: when (val loaded = state.categories) {
            is UiState.Error -> loaded.error.messageRes
            is UiState.Success -> if (loaded.data.isEmpty()) R.string.quick_empty else null
            UiState.Loading -> null
        }
        message?.let { StatusText(stringResource(it)) }
        TextButton(
            onClick = { pickingDate = true },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(QuickAddTags.DATE),
            colors = ButtonDefaults.textButtonColors(contentColor = colors.violetText),
        ) {
            Text(
                stringResource(
                    R.string.quick_date,
                    state.date?.atZone(ZoneId.systemDefault())?.format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm"))
                        ?: stringResource(R.string.quick_now),
                ),
                style = MaterialTheme.typography.labelLarge,
            )
        }
        TextButton(
            onClick = onToggleDetails,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.textButtonColors(contentColor = colors.violetText),
        ) {
            Icon(painterResource(R.drawable.ic_chevron_down), contentDescription = null, Modifier.size(20.dp))
            Spacer(Modifier.width(dimensionResource(R.dimen.sf_space_s)))
            Text(stringResource(R.string.quick_more), style = MaterialTheme.typography.labelLarge)
        }
        if (state.showDetails) {
            Column(Modifier.testTag(QuickAddTags.DETAILS)) {
                DropdownField(
                    label = stringResource(R.string.quick_payment),
                    options = PAYMENT_LABELS.map { stringResource(it) },
                    selectedIndex = state.payment,
                    onSelect = onPayment,
                    enabled = !state.income,
                )
                LabeledTextField(
                    label = stringResource(R.string.quick_note),
                    value = state.note,
                    onValueChange = onNote,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    maxLength = 500,
                    singleLine = false,
                )
            }
        }
        PrimaryButton(
            stringResource(R.string.quick_save),
            onClick = onSave,
            enabled = !state.saving,
            modifier = Modifier
                .padding(top = dimensionResource(R.dimen.sf_space_l))
                .fillMaxWidth()
                .testTag(QuickAddTags.SAVE),
        )
    }

    val zone = ZoneId.systemDefault()
    // Violet is too dark as text on the dark dialog surface
    val dialogButtons = ButtonDefaults.textButtonColors(contentColor = colors.violetText)
    val current = (state.date ?: Instant.now()).atZone(zone)
    if (pickingDate) {
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = current.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(colors = dialogButtons, onClick = {
                    pickedDay = dateState.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay() }
                    pickingDate = false
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }, colors = dialogButtons) { Text(stringResource(R.string.action_cancel)) } },
        ) { DatePicker(dateState) }
    }
    pickedDay?.let { epochDay ->
        val timeState = rememberTimePickerState(current.hour, current.minute, DateFormat.is24HourFormat(LocalContext.current))
        AlertDialog(
            onDismissRequest = { pickedDay = null },
            text = { TimePicker(timeState) },
            confirmButton = {
                TextButton(colors = dialogButtons, onClick = {
                    onDate(LocalDate.ofEpochDay(epochDay).atTime(timeState.hour, timeState.minute).atZone(zone).toInstant())
                    pickedDay = null
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { pickedDay = null }, colors = dialogButtons) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun ErrorText(@StringRes message: Int?, tag: String) {
    if (message == null) return
    Text(
        stringResource(message),
        Modifier
            .testTag(tag)
            .semantics { liveRegion = LiveRegionMode.Polite },
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun StatusText(text: String) {
    Text(
        text,
        Modifier
            .padding(top = dimensionResource(R.dimen.sf_space_s))
            .semantics { liveRegion = LiveRegionMode.Polite },
        color = SpendFlowTheme.colors.textHeading,
        style = MaterialTheme.typography.bodyMedium,
    )
}

/** Plain text with any https link in it made tappable, replacing the View's autoLink="web". */
@Composable
private fun AttributionText(text: String) {
    val url = Regex("https?://\\S+").find(text)
    Text(
        buildAnnotatedString {
            if (url == null) {
                append(text)
            } else {
                append(text.substring(0, url.range.first))
                withLink(LinkAnnotation.Url(url.value)) { append(url.value) }
                append(text.substring(url.range.last + 1))
            }
        },
        color = SpendFlowTheme.colors.textMuted,
        style = MaterialTheme.typography.bodySmall,
    )
}

@ThemePreviews
@Composable
private fun QuickAddFormPreview() = SpendFlowTheme {
    QuickAddForm(
        QuickAddUiState(
            amount = "2450",
            categories = UiState.Success(listOf(Category("food", "Groceries", CategoryType.EXPENSE, iconName = "food"))),
            categoryId = "food",
            errors = EntryErrors(category = R.string.entry_category_required),
        ),
        {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {},
    )
}
