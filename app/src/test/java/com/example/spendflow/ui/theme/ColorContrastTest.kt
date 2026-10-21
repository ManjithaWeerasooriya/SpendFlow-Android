package com.example.spendflow.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.pow

/** WCAG AA for every text/background pair the components use, in both themes (AC3). */
class ColorContrastTest {

    private val light = readColors("src/main/res/values/colors.xml")
    private val dark = readColors("src/main/res/values-night/colors.xml")

    private val surfaces = listOf("sf_page", "sf_card_shell", "sf_inset_card", "sf_tile")
    private val darkSurfaces = listOf("sf_hero_start", "sf_hero_end", "sf_hero_pill", "sf_snackbar", "sf_snackbar_action")

    // Body-size text needs 4.5:1
    private val textPairs: List<Pair<String, String>> =
        listOf("sf_text_heading", "sf_text_body", "sf_text_muted", "sf_violet_text").flatMap { fg -> surfaces.map { fg to it } } +
            listOf("sf_income_text", "sf_over_cap_text", "sf_warning_text", "sf_info_text")
                .flatMap { fg -> listOf("sf_page", "sf_card_shell", "sf_inset_card").map { fg to it } } +
            darkSurfaces.map { "sf_text_on_dark" to it } +
            listOf("sf_hero_start", "sf_hero_end", "sf_snackbar").map { "sf_text_on_dark_muted" to it } +
            listOf(
                "white" to "sf_violet",
                "sf_on_destructive" to "sf_destructive",
                "sf_text_heading" to "sf_violet_tint",
                "sf_violet_text" to "sf_violet_tint",
                "sf_ink" to "sf_lavender",
            )

    // Selected outlines, switch tracks and progress indicators that carry meaning on their own need 3:1.
    // Signal fills (income/warning bars and badges) always sit next to a text label, so 1.4.11 doesn't apply.
    private val nonTextPairs: List<Pair<String, String>> =
        listOf("sf_page", "sf_card_shell", "sf_inset_card").map { "sf_violet" to it } +
            listOf("sf_over_cap" to "sf_inset_card")

    @Test
    fun `light and dark define the same tokens`() {
        assertEquals(light.keys, dark.keys)
    }

    @Test
    fun `text pairs meet AA in light theme`() = assertPairs(light, textPairs, 4.5, "light")

    @Test
    fun `text pairs meet AA in dark theme`() = assertPairs(dark, textPairs, 4.5, "dark")

    @Test
    fun `non-text pairs meet 3 to 1 in light theme`() = assertPairs(light, nonTextPairs, 3.0, "light")

    @Test
    fun `non-text pairs meet 3 to 1 in dark theme`() = assertPairs(dark, nonTextPairs, 3.0, "dark")

    private fun assertPairs(colors: Map<String, Int>, pairs: List<Pair<String, String>>, min: Double, theme: String) {
        val failures = pairs.mapNotNull { (fg, bg) ->
            val ratio = contrast(colors.getValue(fg), colors.getValue(bg))
            if (ratio < min) "$fg on $bg = %.2f".format(ratio) else null
        }
        assertTrue("$theme pairs below $min:1: $failures", failures.isEmpty())
    }

    private fun contrast(a: Int, b: Int): Double {
        val (hi, lo) = listOf(luminance(a), luminance(b)).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun luminance(argb: Int): Double {
        fun channel(shift: Int): Double {
            val c = ((argb shr shift) and 0xFF) / 255.0
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }

    private fun readColors(path: String): Map<String, Int> {
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(path))
            .getElementsByTagName("color")
        return (0 until nodes.length).associate { i ->
            val node = nodes.item(i)
            val hex = node.textContent.trim().removePrefix("#").takeLast(6)
            node.attributes.getNamedItem("name").nodeValue to hex.toInt(16)
        }
    }
}
