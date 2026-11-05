package com.example.spendflow.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.dimensionResource
import com.example.spendflow.R

/** Brand theme for Compose screens hosted alongside the existing XML UI. */
@Composable
fun SpendFlowTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    // Match values/shapes.xml rather than Material's default corner sizes.
    val shapes = Shapes(
        extraSmall = RoundedCornerShape(dimensionResource(R.dimen.sf_corner_tile)),
        small = RoundedCornerShape(dimensionResource(R.dimen.sf_corner_tile)),
        medium = RoundedCornerShape(dimensionResource(R.dimen.sf_corner_inset)),
        large = RoundedCornerShape(dimensionResource(R.dimen.sf_corner_shell)),
        extraLarge = RoundedCornerShape(dimensionResource(R.dimen.sf_corner_sheet)),
    )
    MaterialTheme(
        colorScheme = if (darkTheme) SpendFlowDarkColors else SpendFlowLightColors,
        typography = SpendFlowTypography,
        shapes = shapes,
        content = content,
    )
}
