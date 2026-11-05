package com.example.spendflow.core.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.em
import com.example.spendflow.domain.model.Money
import com.example.spendflow.ui.theme.SpendFlowAmountInline
import com.example.spendflow.ui.theme.SpendFlowAmountTitle
import com.example.spendflow.ui.theme.SpendFlowTheme

enum class AmountSize { DISPLAY, TITLE, INLINE }

enum class AmountTone { NEUTRAL, INCOME, EXPENSE, ON_DARK }

/**
 * Amount display: "LKR 72,600". At [AmountSize.DISPLAY] the currency code is smaller and muted,
 * as on the Home hero and Quick Add.
 */
@Composable
fun AmountText(
    amount: Money,
    modifier: Modifier = Modifier,
    size: AmountSize = AmountSize.DISPLAY,
    tone: AmountTone = AmountTone.NEUTRAL,
    showDecimals: Boolean = false,
    signed: Boolean = false,
    maxLines: Int = Int.MAX_VALUE,
) {
    val parts = AmountFormatter.parts(amount, showDecimals, signed, LocalLocale.current.platformLocale)
    val (color, mutedColor) = tone.colors()
    val text = buildAnnotatedString {
        append(parts.sign)
        if (size == AmountSize.DISPLAY) {
            withStyle(SpanStyle(fontSize = 0.55.em, color = mutedColor)) { append(parts.currencyCode) }
        } else {
            append(parts.currencyCode)
        }
        append(' ')
        append(parts.number)
    }
    Text(text, modifier, color = color, style = size.style(), maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

@Composable
@ReadOnlyComposable
private fun AmountSize.style(): TextStyle = when (this) {
    AmountSize.DISPLAY -> MaterialTheme.typography.displaySmall
    AmountSize.TITLE -> SpendFlowAmountTitle
    AmountSize.INLINE -> SpendFlowAmountInline
}

/** Amount colour and the colour of a display-size currency code. */
@Composable
@ReadOnlyComposable
private fun AmountTone.colors(): Pair<Color, Color> {
    val colors = SpendFlowTheme.colors
    return when (this) {
        AmountTone.NEUTRAL, AmountTone.EXPENSE -> colors.textHeading to colors.textMuted
        AmountTone.INCOME -> colors.incomeText to colors.incomeText
        AmountTone.ON_DARK -> colors.textOnDark to colors.textOnDarkMuted
    }
}

@ThemePreviews
@Composable
private fun AmountTextPreview() = ComponentPreview {
    Column {
        AmountText(Money(72_600_00, "LKR"))
        AmountText(Money(2_450_50, "LKR"), size = AmountSize.TITLE, tone = AmountTone.INCOME, showDecimals = true, signed = true)
        AmountText(Money(-1_200_00, "USD"), size = AmountSize.INLINE, tone = AmountTone.EXPENSE)
    }
}
