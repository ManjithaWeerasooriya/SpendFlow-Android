package com.example.spendflow.core.ui.components

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.annotation.AttrRes
import androidx.core.content.withStyledAttributes
import androidx.core.view.isVisible
import com.example.spendflow.R
import com.example.spendflow.databinding.ViewLabeledProgressBinding
import com.example.spendflow.domain.model.Money
import com.google.android.material.color.MaterialColors
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel

/**
 * Progress bar with "current / target" above and a label plus percentage below.
 * Used for savings goals and spending caps; [status] shows a warning line such as "Over cap by LKR 500".
 */
class LabeledProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    enum class Tone(@AttrRes val color: Int) {
        VIOLET(R.attr.sfColorViolet),
        INFO(R.attr.sfColorInfo),
        WARNING(R.attr.sfColorWarning),
        OVER_CAP(R.attr.sfColorOverCap),
    }

    private val binding = ViewLabeledProgressBinding.inflate(LayoutInflater.from(context), this)

    var tone = Tone.VIOLET
        set(value) {
            field = value
            binding.bar.setIndicatorColor(MaterialColors.getColor(this, value.color))
        }

    init {
        orientation = VERTICAL
        var showCard = true
        context.withStyledAttributes(attrs, R.styleable.LabeledProgressView) {
            tone = Tone.entries[getInt(R.styleable.LabeledProgressView_sfProgressTone, 0)]
            showCard = getBoolean(R.styleable.LabeledProgressView_sfShowCard, true)
            val currency = getString(R.styleable.LabeledProgressView_sfCurrency)
            if (currency != null) {
                bind(
                    current = Money(getInt(R.styleable.LabeledProgressView_sfAmountMinor, 0).toLong(), currency),
                    target = Money(getInt(R.styleable.LabeledProgressView_sfTargetMinor, 0).toLong(), currency),
                    label = getText(R.styleable.LabeledProgressView_android_text),
                    status = getText(R.styleable.LabeledProgressView_sfStatusText),
                )
            }
        }
        if (showCard) {
            val padding = resources.getDimensionPixelSize(R.dimen.sf_space_l)
            setPadding(padding, padding, padding, padding)
            background = MaterialShapeDrawable(
                ShapeAppearanceModel.builder(context, R.style.ShapeAppearance_SpendFlow_InsetCard, 0).build(),
            ).apply {
                fillColor = colorStateList(R.attr.sfColorInsetCard)
                setStroke(resources.getDimension(R.dimen.sf_stroke_hairline), MaterialColors.getColor(this@LabeledProgressView, com.google.android.material.R.attr.colorOutlineVariant))
            }
        }
    }

    fun bind(current: Money, target: Money, label: CharSequence?, status: CharSequence? = null, showDecimals: Boolean = false) = with(binding) {
        val percent = progressPercent(current, target)
        this@with.current.text = AmountFormatter.format(current, showDecimals = showDecimals)
        this@with.target.text = AmountFormatter.format(target, showDecimals = showDecimals)
        this@with.label.text = label
        this@with.percent.text = resources.getString(R.string.percent, percent)
        bar.progress = percent.coerceIn(0, 100)
        bar.contentDescription = listOfNotNull(label, this@with.percent.text).joinToString(", ")
        this@with.status.text = status
        this@with.status.isVisible = status != null
    }
}

/** Whole percent of [target] reached; can exceed 100 when a cap is overspent. */
internal fun progressPercent(current: Money, target: Money): Int =
    if (target.amountMinor <= 0) 0 else (current.amountMinor * 100 / target.amountMinor).toInt()
