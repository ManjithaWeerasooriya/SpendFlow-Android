package com.example.spendflow.core.ui.components

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.withStyledAttributes
import androidx.core.view.isVisible
import com.example.spendflow.R
import com.example.spendflow.databinding.ViewLabeledSwitchBinding
import com.example.spendflow.ui.theme.SpendFlowTheme

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

/** Settings row with a switch that also spells out its state as ON / OFF text. The whole row toggles. */
@Composable
fun LabeledSwitch(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
    @DrawableRes icon: Int? = null,
    enabled: Boolean = true,
) {
    val colors = SpendFlowTheme.colors
    Row(
        modifier
            .heightIn(min = dimensionResource(R.dimen.sf_button_height))
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = dimensionResource(R.dimen.sf_space_m)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Image(painterResource(icon), contentDescription = null, Modifier.size(dimensionResource(R.dimen.sf_icon_size)))
            Spacer(Modifier.width(dimensionResource(R.dimen.sf_space_l)))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = colors.textHeading, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp))
            if (!message.isNullOrEmpty()) {
                Text(message, Modifier.padding(top = 2.dp), color = colors.textMuted, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Text(
            stringResource(if (checked) R.string.switch_on else R.string.switch_off),
            Modifier
                .padding(horizontal = dimensionResource(R.dimen.sf_space_m))
                .clearAndSetSemantics {},
            color = colors.textMuted,
            style = MaterialTheme.typography.labelMedium,
        )
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@ThemePreviews
@Composable
private fun LabeledSwitchPreview() = ComponentPreview {
    var checked by remember { mutableStateOf(true) }
    Column {
        LabeledSwitch(
            "Tracking streak", checked, { checked = it },
            message = "Off hides streak badges and reminders everywhere", icon = R.drawable.ic_streak,
        )
        LabeledSwitch("Goal reminders", checked = false, onCheckedChange = {}, enabled = false)
    }
}
