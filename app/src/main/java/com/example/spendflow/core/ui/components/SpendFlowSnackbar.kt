package com.example.spendflow.core.ui.components

import android.view.View
import android.widget.TextView
import androidx.annotation.DrawableRes
import com.example.spendflow.R
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
