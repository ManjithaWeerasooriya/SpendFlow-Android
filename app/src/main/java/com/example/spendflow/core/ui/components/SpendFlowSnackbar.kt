package com.example.spendflow.core.ui.components

import android.view.View
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import com.example.spendflow.R
import com.example.spendflow.ui.theme.SpendFlowTheme
import com.google.android.material.snackbar.Snackbar

/**
 * Snackbar with an optional status icon and action ("Saved · Groceries LKR 2,450  [Undo]").
 * Colours and shape come from snackbarStyle in the theme.
 */
object SpendFlowSnackbar {

    enum class Tone(@DrawableRes val icon: Int?) {
        NONE(null),
        SUCCESS(R.drawable.ic_status_success),
        ERROR(R.drawable.ic_status_error),
    }

    fun make(
        view: View,
        message: CharSequence,
        tone: Tone = Tone.NONE,
        actionText: CharSequence? = null,
        onAction: (() -> Unit)? = null,
        duration: Int = if (actionText != null) Snackbar.LENGTH_LONG else Snackbar.LENGTH_SHORT,
    ): Snackbar {
        val snackbar = Snackbar.make(view, message, duration)
        if (actionText != null) snackbar.setAction(actionText) { onAction?.invoke() }
        tone.icon?.let { icon ->
            snackbar.view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text).apply {
                setCompoundDrawablesRelativeWithIntrinsicBounds(icon, 0, 0, 0)
                compoundDrawablePadding = resources.getDimensionPixelSize(R.dimen.sf_space_m)
            }
        }
        return snackbar
    }
}

/** Pass to `SnackbarHostState.showSnackbar` so [SpendFlowSnackbar] can draw the status icon. */
data class SpendFlowSnackbarVisuals(
    override val message: String,
    val tone: SpendFlowSnackbar.Tone = SpendFlowSnackbar.Tone.NONE,
    override val actionLabel: String? = null,
    override val duration: SnackbarDuration = if (actionLabel != null) SnackbarDuration.Long else SnackbarDuration.Short,
) : SnackbarVisuals {
    override val withDismissAction = false
}

/** Dark pill with an optional status icon and filled action. Use as the `SnackbarHost` content. */
@Composable
fun SpendFlowSnackbar(data: SnackbarData, modifier: Modifier = Modifier) {
    val colors = SpendFlowTheme.colors
    val scheme = MaterialTheme.colorScheme
    val visuals = data.visuals
    val tone = (visuals as? SpendFlowSnackbarVisuals)?.tone ?: SpendFlowSnackbar.Tone.NONE
    Snackbar(
        modifier = modifier.padding(dimensionResource(R.dimen.sf_space_l)),
        action = visuals.actionLabel?.let { label ->
            {
                Button(
                    onClick = data::performAction,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = colors.snackbarAction, contentColor = scheme.inverseOnSurface),
                    contentPadding = PaddingValues(horizontal = dimensionResource(R.dimen.sf_space_xl)),
                ) { Text(label, style = MaterialTheme.typography.labelLarge) }
            }
        },
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = scheme.inverseSurface,
        contentColor = scheme.inverseOnSurface,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            tone.icon?.let {
                Image(painterResource(it), contentDescription = null, Modifier.size(dimensionResource(R.dimen.sf_icon_size)))
                Spacer(Modifier.width(dimensionResource(R.dimen.sf_space_m)))
            }
            Text(visuals.message, style = MaterialTheme.typography.bodyLarge, maxLines = 3)
        }
    }
}

@ThemePreviews
@Composable
private fun SpendFlowSnackbarPreview() = ComponentPreview {
    Column {
        SpendFlowSnackbar(previewData(SpendFlowSnackbarVisuals("Saved · Groceries LKR 2,450", SpendFlowSnackbar.Tone.SUCCESS, "Undo")))
        SpendFlowSnackbar(previewData(SpendFlowSnackbarVisuals("Couldn't sync. Check your connection.", SpendFlowSnackbar.Tone.ERROR)))
        SpendFlowSnackbar(previewData(SpendFlowSnackbarVisuals("Category archived")))
    }
}

private fun previewData(visuals: SnackbarVisuals) = object : SnackbarData {
    override val visuals = visuals
    override fun performAction() = Unit
    override fun dismiss() = Unit
}
