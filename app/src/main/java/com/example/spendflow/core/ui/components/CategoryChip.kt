package com.example.spendflow.core.ui.components

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Checkable
import android.widget.LinearLayout
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
import androidx.core.content.withStyledAttributes
import com.example.spendflow.R
import com.example.spendflow.databinding.ViewCategoryChipBinding
import com.example.spendflow.ui.theme.SpendFlowTheme
import com.google.android.material.color.MaterialColors
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel

/**
 * Icon tile plus label, used for expense categories and income sources in Quick Add and Budget.
 * Clicking does not toggle it: the screen sets [isChecked] from state, which keeps single selection in the ViewModel.
 */
class CategoryChip @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr), Checkable {

    private val binding = ViewCategoryChipBinding.inflate(LayoutInflater.from(context), this)
    private val tileBackground = MaterialShapeDrawable(
        ShapeAppearanceModel.builder(context, R.style.ShapeAppearance_SpendFlow_Tile, 0).build(),
    )
    private var checked = false

    var label: CharSequence?
        get() = binding.label.text
        set(value) { binding.label.text = value }

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        minimumWidth = resources.getDimensionPixelSize(R.dimen.sf_icon_tile_size) + resources.getDimensionPixelSize(R.dimen.sf_space_m)
        isClickable = true
        isFocusable = true
        binding.tile.background = tileBackground
        context.withStyledAttributes(attrs, R.styleable.CategoryChip) {
            getResourceId(R.styleable.CategoryChip_sfIcon, 0).takeIf { it != 0 }?.let(::setIcon)
            label = getText(R.styleable.CategoryChip_android_text)
            checked = getBoolean(R.styleable.CategoryChip_android_checked, false)
        }
        refresh()
    }

    fun setIcon(@DrawableRes icon: Int) = binding.icon.setImageResource(icon)

    override fun isChecked() = checked

    override fun setChecked(checked: Boolean) {
        if (this.checked == checked) return
        this.checked = checked
        refresh()
    }

    override fun toggle() {
        isChecked = !checked
    }

    private fun refresh() {
        tileBackground.fillColor = colorStateList(if (checked) R.attr.sfColorVioletTint else R.attr.sfColorTile)
        tileBackground.strokeWidth = if (checked) resources.getDimension(R.dimen.sf_stroke_selected) else 0f
        tileBackground.strokeColor = colorStateList(R.attr.sfColorViolet)
        binding.label.setTextColor(MaterialColors.getColor(this, if (checked) R.attr.sfColorTextHeading else R.attr.sfColorTextMuted))
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = RadioButton::class.java.name
        info.isCheckable = true
        info.isChecked = checked
    }
}

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
