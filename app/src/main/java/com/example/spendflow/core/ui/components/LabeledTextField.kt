package com.example.spendflow.core.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.input.VisualTransformation
import com.example.spendflow.R
import com.example.spendflow.ui.theme.SpendFlowTheme

/**
 * Label above an outlined inset-card field, with any error as text underneath (Widget.SpendFlow.TextField).
 * [fieldModifier] reaches the text field itself, for autofill content types and test tags.
 */
@Composable
fun LabeledTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    fieldModifier: Modifier = Modifier,
    placeholder: String? = null,
    error: String? = null,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: (@Composable () -> Unit)? = null,
    maxLength: Int? = null,
    singleLine: Boolean = true,
) {
    val colors = SpendFlowTheme.colors
    Column(modifier.padding(top = dimensionResource(R.dimen.sf_space_l))) {
        Text(label, color = colors.textHeading, style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = value,
            onValueChange = { if (maxLength == null || it.length <= maxLength) onValueChange(it) },
            modifier = fieldModifier.semantics { contentDescription = label }
                .padding(top = dimensionResource(R.dimen.sf_space_s))
                .fillMaxWidth(),
            enabled = enabled,
            textStyle = MaterialTheme.typography.bodyLarge,
            placeholder = placeholder?.let { { Text(it) } },
            trailingIcon = trailingIcon,
            supportingText = if (error != null || maxLength != null) {
                {
                    Row {
                        Text(error.orEmpty(), Modifier.weight(1f))
                        if (maxLength != null) Text("${value.length}/$maxLength")
                    }
                }
            } else {
                null
            },
            isError = error != null,
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            singleLine = singleLine,
            shape = MaterialTheme.shapes.medium,
            colors = spendFlowFieldColors(),
        )
    }
}

/** Inset-card fill with a violet focus outline, shared by the outlined fields. */
@Composable
internal fun spendFlowFieldColors(): TextFieldColors {
    val colors = SpendFlowTheme.colors
    return OutlinedTextFieldDefaults.colors(
        focusedContainerColor = colors.insetCard,
        unfocusedContainerColor = colors.insetCard,
        disabledContainerColor = colors.insetCard,
        errorContainerColor = colors.insetCard,
        focusedBorderColor = colors.violet,
        unfocusedTextColor = colors.textHeading,
        focusedTextColor = colors.textHeading,
        focusedPlaceholderColor = colors.textMuted,
        unfocusedPlaceholderColor = colors.textMuted,
        cursorColor = colors.violet,
    )
}

@ThemePreviews
@Composable
private fun LabeledTextFieldPreview() = ComponentPreview {
    var email by remember { mutableStateOf("") }
    Column {
        LabeledTextField("Email", email, { email = it }, placeholder = "name@mail.com")
        LabeledTextField("Password", "abc", {}, error = "Use at least 6 characters")
        LabeledTextField("Category name", "Groceries", {}, maxLength = 100)
    }
}
