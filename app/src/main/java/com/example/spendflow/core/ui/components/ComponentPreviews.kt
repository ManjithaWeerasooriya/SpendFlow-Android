package com.example.spendflow.core.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.tooling.preview.Preview
import com.example.spendflow.R
import com.example.spendflow.ui.theme.SpendFlowTheme

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
annotation class ThemePreviews

/** Page-coloured backdrop so previews show components on the surface they sit on. */
@Composable
internal fun ComponentPreview(content: @Composable () -> Unit) {
    SpendFlowTheme {
        Surface(color = SpendFlowTheme.colors.page) {
            Box(Modifier.padding(dimensionResource(R.dimen.sf_space_l))) { content() }
        }
    }
}
