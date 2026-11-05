package com.example.spendflow.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow
import kotlin.reflect.KProperty1

private typealias Token = KProperty1<SpendFlowColors, Color>

/** WCAG AA for every text/background pair the components use, in both themes (AC3). */
class ColorContrastTest {

    private val surfaces = listOf(SpendFlowColors::page, SpendFlowColors::cardShell, SpendFlowColors::insetCard, SpendFlowColors::tile)
    private val signalSurfaces = listOf(SpendFlowColors::page, SpendFlowColors::cardShell, SpendFlowColors::insetCard)
    private val darkSurfaces = listOf(
        SpendFlowColors::heroStart, SpendFlowColors::heroEnd, SpendFlowColors::heroPill,
        SpendFlowColors::snackbar, SpendFlowColors::snackbarAction,
    )

    // Body-size text needs 4.5:1
    private val textPairs: List<Pair<Token, Token>> =
        listOf(SpendFlowColors::textHeading, SpendFlowColors::textBody, SpendFlowColors::textMuted, SpendFlowColors::violetText)
            .flatMap { fg -> surfaces.map { fg to it } } +
            listOf(SpendFlowColors::incomeText, SpendFlowColors::overCapText, SpendFlowColors::warningText, SpendFlowColors::infoText)
                .flatMap { fg -> signalSurfaces.map { fg to it } } +
            darkSurfaces.map { SpendFlowColors::textOnDark to it } +
            listOf(SpendFlowColors::heroStart, SpendFlowColors::heroEnd, SpendFlowColors::snackbar)
                .map { SpendFlowColors::textOnDarkMuted to it } +
            listOf(
                SpendFlowColors::onDestructive to SpendFlowColors::destructive,
                SpendFlowColors::textHeading to SpendFlowColors::violetTint,
                SpendFlowColors::violetText to SpendFlowColors::violetTint,
                SpendFlowColors::ink to SpendFlowColors::lavender,
            )

    // Selected outlines, switch tracks and progress indicators that carry meaning on their own need 3:1.
    // Signal fills (income/warning bars and badges) always sit next to a text label, so 1.4.11 doesn't apply.
    private val nonTextPairs: List<Pair<Token, Token>> =
        signalSurfaces.map { SpendFlowColors::violet to it } +
            listOf(SpendFlowColors::overCap to SpendFlowColors::insetCard)

    @Test
    fun `text pairs meet AA in light theme`() = assertPairs(SpendFlowLightPalette, textPairs, 4.5, "light")

    @Test
    fun `text pairs meet AA in dark theme`() = assertPairs(SpendFlowDarkPalette, textPairs, 4.5, "dark")

    @Test
    fun `white on violet meets AA in both themes`() {
        listOf(SpendFlowLightPalette, SpendFlowDarkPalette).forEach {
            val ratio = contrast(Color.White, it.violet)
            assertTrue("white on violet = %.2f".format(ratio), ratio >= 4.5)
        }
    }

    @Test
    fun `non-text pairs meet 3 to 1 in light theme`() = assertPairs(SpendFlowLightPalette, nonTextPairs, 3.0, "light")

    @Test
    fun `non-text pairs meet 3 to 1 in dark theme`() = assertPairs(SpendFlowDarkPalette, nonTextPairs, 3.0, "dark")

    private fun assertPairs(colors: SpendFlowColors, pairs: List<Pair<Token, Token>>, min: Double, theme: String) {
        val failures = pairs.mapNotNull { (fg, bg) ->
            val ratio = contrast(fg.get(colors), bg.get(colors))
            if (ratio < min) "${fg.name} on ${bg.name} = %.2f".format(ratio) else null
        }
        assertTrue("$theme pairs below $min:1: $failures", failures.isEmpty())
    }

    private fun contrast(a: Color, b: Color): Double {
        val (hi, lo) = listOf(luminance(a.toArgb()), luminance(b.toArgb())).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun luminance(argb: Int): Double {
        fun channel(shift: Int): Double {
            val c = ((argb shr shift) and 0xFF) / 255.0
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }
}
