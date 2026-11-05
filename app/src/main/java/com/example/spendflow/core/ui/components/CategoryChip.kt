package com.example.spendflow.core.ui.components

import android.widget.RadioButton
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.spendflow.R
import com.example.spendflow.ui.theme.SpendFlowTheme

/**
 * Icon tile plus label for expense categories and income sources.
 * Selection comes from [selected]; tapping only reports [onClick], so the ViewModel keeps single selection.
 */
@Composable
fun CategoryChip(
    label: String,
    @DrawableRes icon: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SpendFlowTheme.colors
    val tileSize = dimensionResource(R.dimen.sf_icon_tile_size)
    val shape = MaterialTheme.shapes.small
    Column(
        modifier
            .widthIn(min = tileSize + dimensionResource(R.dimen.sf_space_m))
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(tileSize)
                .background(if (selected) colors.violetTint else colors.tile, shape)
                .then(if (selected) Modifier.border(dimensionResource(R.dimen.sf_stroke_selected), colors.violet, shape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Image(painterResource(icon), contentDescription = null, Modifier.size(28.dp))
        }
        Text(
            label,
            Modifier.padding(top = 6.dp),
            color = if (selected) colors.textHeading else colors.textMuted,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@ThemePreviews
@Composable
private fun CategoryChipPreview() = ComponentPreview {
    var selected by remember { mutableStateOf(0) }
    Row {
        listOf("Groceries" to R.drawable.ic_cat_food, "Transport" to R.drawable.ic_cat_transport, "Subscriptions" to R.drawable.ic_cat_subscriptions)
            .forEachIndexed { i, (label, icon) -> CategoryChip(label, icon, selected = i == selected, onClick = { selected = i }) }
    }
}
