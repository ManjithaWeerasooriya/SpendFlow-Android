package com.example.spendflow.core.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.spendflow.R
import com.example.spendflow.ui.theme.SpendFlowTheme

/** Violet pill, the main action on a screen (Widget.SpendFlow.Button). */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @DrawableRes icon: Int? = null,
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = dimensionResource(R.dimen.sf_button_height)),
        enabled = enabled,
        shape = CircleShape,
        contentPadding = buttonPadding(),
    ) { ButtonContent(text, icon) }
}

/** Outlined pill for secondary actions (Widget.SpendFlow.Button.Outlined). */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @DrawableRes icon: Int? = null,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = dimensionResource(R.dimen.sf_button_height)),
        enabled = enabled,
        shape = CircleShape,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = SpendFlowTheme.colors.textHeading),
        border = BorderStroke(dimensionResource(R.dimen.sf_stroke_hairline), MaterialTheme.colorScheme.outlineVariant),
        contentPadding = buttonPadding(),
    ) { ButtonContent(text, icon) }
}

/** Filled pill for irreversible actions such as delete or log out (Widget.SpendFlow.Button.Destructive). */
@Composable
fun DestructiveButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @DrawableRes icon: Int? = null,
) {
    val colors = SpendFlowTheme.colors
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = dimensionResource(R.dimen.sf_button_height)),
        enabled = enabled,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = colors.destructive, contentColor = colors.onDestructive),
        contentPadding = buttonPadding(),
    ) { ButtonContent(text, icon) }
}

@Composable
private fun buttonPadding() = PaddingValues(horizontal = dimensionResource(R.dimen.sf_space_xxl))

@Composable
private fun RowScope.ButtonContent(text: String, @DrawableRes icon: Int?) {
    if (icon != null) {
        Icon(painterResource(icon), contentDescription = null, Modifier.size(20.dp))
        Spacer(Modifier.width(dimensionResource(R.dimen.sf_space_s)))
    }
    Text(text, style = MaterialTheme.typography.labelLarge)
}

@ThemePreviews
@Composable
private fun ButtonsPreview() = ComponentPreview {
    Column {
        PrimaryButton("Save entry", onClick = {}, icon = R.drawable.ic_plus)
        PrimaryButton("Saving…", onClick = {}, enabled = false)
        SecondaryButton("Cancel", onClick = {})
        DestructiveButton("Log out", onClick = {})
    }
}
