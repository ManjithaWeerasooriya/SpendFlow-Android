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
import androidx.core.content.withStyledAttributes
import com.example.spendflow.R
import com.example.spendflow.databinding.ViewCategoryChipBinding
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
