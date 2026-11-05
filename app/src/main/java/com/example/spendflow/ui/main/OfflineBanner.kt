package com.example.spendflow.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.example.spendflow.R
import com.example.spendflow.core.ui.components.ThemePreviews
import com.example.spendflow.ui.theme.SpendFlowTheme

/** "You're offline · 2 changes pending sync"; just "You're offline" when nothing is waiting. */
@Composable
fun OfflineBanner(pendingCount: Int, modifier: Modifier = Modifier) {
    val colors = SpendFlowTheme.colors
    val text = if (pendingCount > 0) {
        pluralStringResource(R.plurals.sync_offline_pending, pendingCount, pendingCount)
    } else {
        stringResource(R.string.sync_offline)
    }
    Text(
        text,
        modifier
            .fillMaxWidth()
            .background(colors.warning)
            .padding(horizontal = dimensionResource(R.dimen.sf_space_l), vertical = dimensionResource(R.dimen.sf_space_s))
            .semantics { liveRegion = LiveRegionMode.Polite },
        color = colors.ink,
        style = MaterialTheme.typography.labelLarge,
    )
}

@ThemePreviews
@Composable
private fun OfflineBannerPreview() = SpendFlowTheme { OfflineBanner(pendingCount = 2) }
