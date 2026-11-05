package com.example.spendflow.core.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spendflow.R
import com.example.spendflow.ui.theme.SpendFlowTheme

/** Back chevron and a centred title for pushed screens (Widget.SpendFlow.TopBar). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpendFlowTopBar(title: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    CenterAlignedTopAppBar(
        title = {
            Text(
                title,
                Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 19.sp, lineHeight = 24.sp),
            )
        },
        modifier = modifier,
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_chevron_left), stringResource(R.string.action_back))
            }
        },
        expandedHeight = 56.dp,
        // MainActivity already pads the Fragment host for the status bar
        windowInsets = WindowInsets(0, 0, 0, 0),
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

@ThemePreviews
@Composable
private fun SpendFlowTopBarPreview() = SpendFlowTheme {
    SpendFlowTopBar("Goals", onBack = {})
}
