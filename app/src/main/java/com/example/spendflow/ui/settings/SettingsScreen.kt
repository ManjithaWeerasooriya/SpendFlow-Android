package com.example.spendflow.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.spendflow.R
import com.example.spendflow.core.ui.components.SpendFlowTopBar
import com.example.spendflow.ui.theme.SpendFlowTheme

internal data class SettingsUiState(
    val title: String,
    val emptyTitle: String,
    val emptyMessage: String,
)

/** Settings has no ViewModel yet; the wrapper resolves its existing static content. */
@Composable
internal fun SettingsRoute(onBack: () -> Unit) {
    SettingsScreen(
        state = SettingsUiState(
            title = stringResource(R.string.settings_title),
            emptyTitle = stringResource(R.string.placeholder_title),
            emptyMessage = stringResource(R.string.placeholder_settings),
        ),
        onBack = onBack,
    )
}

@Composable
internal fun SettingsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // MainActivity already pads the Fragment host for system bars and the keyboard.
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { SpendFlowTopBar(state.title, onBack) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(dimensionResource(R.dimen.sf_space_xxl)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(dimensionResource(R.dimen.sf_empty_icon_size))
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_settings),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                )
            }
            Spacer(Modifier.height(dimensionResource(R.dimen.sf_space_xl)))
            Text(
                text = state.emptyTitle,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(dimensionResource(R.dimen.sf_space_s)))
            Text(
                text = state.emptyMessage,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Preview(name = "Settings - light", showBackground = true)
@Preview(name = "Settings - dark", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SettingsScreenPreview() {
    SpendFlowTheme {
        SettingsScreen(
            state = SettingsUiState(
                title = stringResource(R.string.settings_title),
                emptyTitle = stringResource(R.string.placeholder_title),
                emptyMessage = stringResource(R.string.placeholder_settings),
            ),
            onBack = {},
        )
    }
}
