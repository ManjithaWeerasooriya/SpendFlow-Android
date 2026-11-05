package com.example.spendflow.core.ui.components

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
import com.example.spendflow.R
import com.example.spendflow.ui.theme.SpendFlowTheme

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
