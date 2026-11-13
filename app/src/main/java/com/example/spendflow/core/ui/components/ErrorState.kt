package com.example.spendflow.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.spendflow.R

/** Shared load-error state: message plus Retry. Lives inside the shell so navigation stays usable. */
@Composable
fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    EmptyState(title = message, modifier = modifier, actionText = stringResource(R.string.action_retry), onAction = onRetry)
}

@ThemePreviews
@Composable
private fun ErrorStatePreview() = ComponentPreview {
    ErrorState(message = "Couldn't load your data", onRetry = {})
}
