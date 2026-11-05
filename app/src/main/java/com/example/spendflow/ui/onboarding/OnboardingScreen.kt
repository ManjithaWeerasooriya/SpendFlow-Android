package com.example.spendflow.ui.onboarding

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.example.spendflow.R
import com.example.spendflow.ui.theme.SpendFlowTheme
import java.util.Currency

internal data class OnboardingUiState(val baseCurrency: String)

/** The existing ViewModel exposes a fixed currency and click actions, with no Flow or events. */
@Composable
internal fun OnboardingRoute(viewModel: OnboardingViewModel) {
    OnboardingScreen(
        state = OnboardingUiState(viewModel.baseCurrency),
        onSkip = viewModel::finish,
        onContinue = viewModel::finish,
    )
}

@Composable
internal fun OnboardingScreen(
    state: OnboardingUiState,
    onSkip: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalLocale.current.platformLocale
    val currencyName = stringResource(
        R.string.onboarding_currency_value,
        state.baseCurrency,
        Currency.getInstance(state.baseCurrency).getDisplayName(locale),
    )
    // The app shell already pads the NavHost for system bars and the keyboard.
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight)
                    .padding(
                        horizontal = dimensionResource(R.dimen.sf_screen_padding),
                        vertical = dimensionResource(R.dimen.sf_space_l),
                    ),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.onboarding_overline),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onSkip) {
                            Text(
                                stringResource(R.string.onboarding_skip),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                    }
                    Spacer(Modifier.height(dimensionResource(R.dimen.sf_space_xl)))
                    Text(
                        stringResource(R.string.onboarding_title),
                        modifier = Modifier.semantics { heading() },
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Spacer(Modifier.height(dimensionResource(R.dimen.sf_space_xs)))
                    Text(
                        stringResource(R.string.onboarding_message),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    Spacer(Modifier.height(dimensionResource(R.dimen.sf_space_xxl)))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    ) {
                        Column(Modifier.padding(dimensionResource(R.dimen.sf_space_l))) {
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                border = BorderStroke(
                                    dimensionResource(R.dimen.sf_stroke_hairline),
                                    MaterialTheme.colorScheme.outlineVariant,
                                ),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = dimensionResource(R.dimen.sf_link_row_height))
                                        .padding(dimensionResource(R.dimen.sf_space_l)),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Image(
                                        painter = painterResource(R.drawable.ic_currency_exchange),
                                        contentDescription = null,
                                        modifier = Modifier.size(dimensionResource(R.dimen.sf_icon_size)),
                                    )
                                    Spacer(Modifier.width(dimensionResource(R.dimen.sf_space_m)))
                                    Text(currencyName, style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                            Spacer(Modifier.height(dimensionResource(R.dimen.sf_space_s)))
                            Text(
                                stringResource(R.string.onboarding_currency_caption),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                    }
                }
                Button(
                    onClick = onContinue,
                    modifier = Modifier
                        .padding(top = dimensionResource(R.dimen.sf_space_xxl))
                        .fillMaxWidth()
                        .heightIn(min = dimensionResource(R.dimen.sf_button_height)),
                    shape = CircleShape,
                ) {
                    Text(stringResource(R.string.onboarding_continue))
                }
            }
        }
    }
}

@Preview(name = "Onboarding - light", showBackground = true)
@Preview(name = "Onboarding - dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Onboarding - large text", showBackground = true, widthDp = 320, heightDp = 480, fontScale = 2f)
@Composable
private fun OnboardingScreenPreview() {
    SpendFlowTheme {
        OnboardingScreen(OnboardingUiState(baseCurrency = "LKR"), onSkip = {}, onContinue = {})
    }
}
