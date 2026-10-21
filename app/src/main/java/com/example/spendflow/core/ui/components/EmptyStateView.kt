package com.example.spendflow.core.ui.components

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import androidx.core.content.withStyledAttributes
import androidx.core.view.isVisible
import com.example.spendflow.R
import com.example.spendflow.databinding.ViewEmptyStateBinding

/** Icon, title, message and an optional primary action pointing to the next step. */
class EmptyStateView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    private val binding = ViewEmptyStateBinding.inflate(LayoutInflater.from(context), this)

    var title: CharSequence?
        get() = binding.title.text
        set(value) { binding.title.text = value }

    var message: CharSequence?
        get() = binding.message.text
        set(value) {
            binding.message.text = value
            binding.message.isVisible = !value.isNullOrEmpty()
        }

    var actionText: CharSequence?
        get() = binding.action.text
        set(value) {
            binding.action.text = value
            binding.action.isVisible = !value.isNullOrEmpty()
        }

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        val padding = resources.getDimensionPixelSize(R.dimen.sf_space_xxl)
        setPadding(padding, padding, padding, padding)
        context.withStyledAttributes(attrs, R.styleable.EmptyStateView) {
            getResourceId(R.styleable.EmptyStateView_sfIcon, 0).takeIf { it != 0 }?.let(::setIcon)
            title = getText(R.styleable.EmptyStateView_sfTitle)
            message = getText(R.styleable.EmptyStateView_sfMessage)
            actionText = getText(R.styleable.EmptyStateView_sfActionText)
            getResourceId(R.styleable.EmptyStateView_sfActionIcon, 0).takeIf { it != 0 }?.let(binding.action::setIconResource)
        }
    }

    fun setIcon(@DrawableRes icon: Int) = binding.icon.setImageResource(icon)

    fun setOnActionClickListener(listener: View.OnClickListener?) = binding.action.setOnClickListener(listener)
}
