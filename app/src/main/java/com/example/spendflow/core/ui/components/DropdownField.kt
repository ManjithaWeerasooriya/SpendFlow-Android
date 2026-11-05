package com.example.spendflow.core.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import com.example.spendflow.R
import com.example.spendflow.ui.theme.SpendFlowTheme

/** Read-only field that opens a menu of [options], styled like [LabeledTextField]. Replaces XML spinners. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DropdownField(
    label: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier.padding(top = dimensionResource(R.dimen.sf_space_l))) {
        Text(label, color = SpendFlowTheme.colors.textHeading, style = MaterialTheme.typography.bodyMedium)
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it && enabled }) {
            OutlinedTextField(
                value = options.getOrElse(selectedIndex) { "" },
                onValueChange = {},
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled)
                    .padding(top = dimensionResource(R.dimen.sf_space_s))
                    .fillMaxWidth(),
                enabled = enabled,
                readOnly = true,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                supportingText = error?.let { { Text(it) } },
                isError = error != null,
                shape = MaterialTheme.shapes.medium,
                colors = spendFlowFieldColors(),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEachIndexed { i, option ->
                    DropdownMenuItem(
                        text = { Text(option, style = MaterialTheme.typography.bodyLarge) },
                        onClick = {
                            onSelect(i)
                            expanded = false
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                    )
                }
            }
        }
    }
}

@ThemePreviews
@Composable
private fun DropdownFieldPreview() = ComponentPreview {
    var selected by remember { mutableIntStateOf(0) }
    Column {
        DropdownField("Default currency", listOf("LKR — Sri Lankan Rupee", "USD — US Dollar"), selected, { selected = it })
        DropdownField("Icon", listOf("Food"), 0, {}, error = "Select a supported default currency.")
    }
}
