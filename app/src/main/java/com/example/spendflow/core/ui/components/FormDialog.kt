package com.example.spendflow.core.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties
import com.example.spendflow.ui.theme.SpendFlowTheme

/**
 * Dialog with a confirm and cancel action whose buttons and dismissal are locked while [saving],
 * so a write can't be abandoned halfway. [content] scrolls on small screens.
 */
@Composable
fun FormDialog(
    title: String,
    confirm: String,
    dismiss: String,
    saving: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissOnClickOutside: Boolean = true,
    confirmEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    // Violet is too dark as text on the dark dialog surface
    val buttonColors = ButtonDefaults.textButtonColors(contentColor = SpendFlowTheme.colors.violetText)
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(title) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) { content() } },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !saving && confirmEnabled, colors = buttonColors) { Text(confirm) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving, colors = buttonColors) { Text(dismiss) }
        },
        properties = DialogProperties(dismissOnBackPress = !saving, dismissOnClickOutside = dismissOnClickOutside && !saving),
    )
}

@ThemePreviews
@Composable
private fun FormDialogPreview() = SpendFlowTheme {
    FormDialog("Rename category", "Save", "Cancel", saving = false, onConfirm = {}, onDismiss = {}) {
        Text("Dialog content")
    }
}
