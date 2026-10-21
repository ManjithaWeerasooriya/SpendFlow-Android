package com.example.spendflow.core.ui.components

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import androidx.core.content.withStyledAttributes
import androidx.core.view.isVisible
import com.example.spendflow.R
import com.example.spendflow.databinding.ViewLabeledSwitchBinding

/**
 * Settings row with a switch that also spells out its state as ON / OFF text.
 * Setting [isChecked] does not call [onCheckedChange]; only user taps do, so state can be rendered without loops.
 */
class LabeledSwitch @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    private val binding = ViewLabeledSwitchBinding.inflate(LayoutInflater.from(context), this)
    private var notify = true

    var onCheckedChange: ((Boolean) -> Unit)? = null

    var isChecked: Boolean
        get() = binding.toggle.isChecked
        set(value) {
            notify = false
            binding.toggle.isChecked = value
            notify = true
            updateState()
        }

    var title: CharSequence?
        get() = binding.title.text
        set(value) {
            binding.title.text = value
            binding.toggle.contentDescription = value
        }

    var message: CharSequence?
        get() = binding.message.text
        set(value) {
            binding.message.text = value
            binding.message.isVisible = !value.isNullOrEmpty()
        }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = resources.getDimensionPixelSize(R.dimen.sf_button_height)
        val vertical = resources.getDimensionPixelSize(R.dimen.sf_space_m)
        setPadding(paddingLeft, vertical, paddingRight, vertical)
        binding.toggle.setOnCheckedChangeListener { _, checked ->
            updateState()
            if (notify) onCheckedChange?.invoke(checked)
        }
        // The whole row is the touch target, as in Settings
        setOnClickListener { binding.toggle.toggle() }
        context.withStyledAttributes(attrs, R.styleable.LabeledSwitch) {
            getResourceId(R.styleable.LabeledSwitch_sfIcon, 0).takeIf { it != 0 }?.let(::setIcon)
            title = getText(R.styleable.LabeledSwitch_sfTitle)
            message = getText(R.styleable.LabeledSwitch_sfMessage)
            isChecked = getBoolean(R.styleable.LabeledSwitch_android_checked, false)
        }
        updateState()
    }

    fun setIcon(@DrawableRes icon: Int) {
        binding.icon.setImageResource(icon)
        binding.icon.isVisible = true
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        binding.toggle.isEnabled = enabled
    }

    private fun updateState() {
        binding.state.setText(if (binding.toggle.isChecked) R.string.switch_on else R.string.switch_off)
    }
}
