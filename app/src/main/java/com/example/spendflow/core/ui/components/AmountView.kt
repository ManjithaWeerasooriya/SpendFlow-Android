package com.example.spendflow.core.ui.components

import android.content.Context
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.util.AttributeSet
import androidx.annotation.AttrRes
import androidx.annotation.StyleRes
import androidx.core.content.withStyledAttributes
import androidx.core.text.inSpans
import androidx.core.widget.TextViewCompat
import com.example.spendflow.R
import com.example.spendflow.domain.model.Money
import com.google.android.material.color.MaterialColors
import com.google.android.material.textview.MaterialTextView

/**
 * Large amount display: "LKR 72,600". In [Size.DISPLAY] the currency code is smaller and muted,
 * as on the Home hero and Quick Add.
 */
class AmountView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : MaterialTextView(context, attrs, defStyleAttr) {

    enum class Size(@StyleRes val appearance: Int, val codeScale: Float) {
        DISPLAY(R.style.TextAppearance_SpendFlow_Amount, 0.55f),
        TITLE(R.style.TextAppearance_SpendFlow_Amount_Title, 1f),
        INLINE(R.style.TextAppearance_SpendFlow_Amount_Inline, 1f),
    }

    enum class Tone(@AttrRes val color: Int, @AttrRes val codeColor: Int) {
        NEUTRAL(R.attr.sfColorTextHeading, R.attr.sfColorTextMuted),
        INCOME(R.attr.sfColorIncomeText, R.attr.sfColorIncomeText),
        EXPENSE(R.attr.sfColorTextHeading, R.attr.sfColorTextMuted),
        ON_DARK(R.attr.sfColorTextOnDark, R.attr.sfColorTextOnDarkMuted),
    }

    var size = Size.DISPLAY
        set(value) { field = value; render() }
    var tone = Tone.NEUTRAL
        set(value) { field = value; render() }
    var showDecimals = false
        set(value) { field = value; render() }
    var signed = false
        set(value) { field = value; render() }
    var amount: Money? = null
        set(value) { field = value; render() }

    init {
        context.withStyledAttributes(attrs, R.styleable.AmountView) {
            size = Size.entries[getInt(R.styleable.AmountView_sfAmountSize, 0)]
            tone = Tone.entries[getInt(R.styleable.AmountView_sfAmountTone, 0)]
            showDecimals = getBoolean(R.styleable.AmountView_sfShowDecimals, false)
            signed = getBoolean(R.styleable.AmountView_sfSigned, false)
            val currency = getString(R.styleable.AmountView_sfCurrency)
            if (currency != null && hasValue(R.styleable.AmountView_sfAmountMinor)) {
                amount = Money(getInt(R.styleable.AmountView_sfAmountMinor, 0).toLong(), currency)
            }
        }
        render()
    }

    private fun render() {
        TextViewCompat.setTextAppearance(this, size.appearance)
        fontFeatureSettings = "tnum"
        val money = amount ?: run { text = null; return }
        val parts = AmountFormatter.parts(money, showDecimals, signed)
        val color = MaterialColors.getColor(this, tone.color)
        val codeColor = MaterialColors.getColor(this, if (size == Size.DISPLAY) tone.codeColor else tone.color)
        setTextColor(color)
        text = SpannableStringBuilder().apply {
            append(parts.sign)
            inSpans(RelativeSizeSpan(size.codeScale), ForegroundColorSpan(codeColor)) { append(parts.currencyCode) }
            append(' ')
            append(parts.number)
        }
    }
}
