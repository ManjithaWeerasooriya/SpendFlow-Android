package com.example.spendflow.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Compose and View screens must look the same while both exist, so the Kotlin palette has to match
 * colors.xml token for token. Delete this test together with the XML colours.
 */
class ThemeColorsSyncTest {

    private val tokens = listOf(
        SpendFlowColors::violet, SpendFlowColors::violetText, SpendFlowColors::violetTint,
        SpendFlowColors::lavender, SpendFlowColors::ink,
        SpendFlowColors::income, SpendFlowColors::overCap, SpendFlowColors::warning, SpendFlowColors::info,
        SpendFlowColors::incomeText, SpendFlowColors::overCapText, SpendFlowColors::warningText, SpendFlowColors::infoText,
        SpendFlowColors::destructive, SpendFlowColors::onDestructive,
        SpendFlowColors::page, SpendFlowColors::cardShell, SpendFlowColors::insetCard, SpendFlowColors::tile,
        SpendFlowColors::outline, SpendFlowColors::heroStart, SpendFlowColors::heroEnd, SpendFlowColors::heroPill,
        SpendFlowColors::snackbar, SpendFlowColors::snackbarAction,
        SpendFlowColors::textHeading, SpendFlowColors::textBody, SpendFlowColors::textMuted,
        SpendFlowColors::textOnDark, SpendFlowColors::textOnDarkMuted,
    ).associateBy { "sf_" + it.name.replace(Regex("[A-Z]")) { m -> "_" + m.value.lowercase() } }

    @Test
    fun `light palette matches values colors`() = assertMatches("src/main/res/values/colors.xml", SpendFlowLightPalette)

    @Test
    fun `dark palette matches values-night colors`() = assertMatches("src/main/res/values-night/colors.xml", SpendFlowDarkPalette)

    private fun assertMatches(path: String, palette: SpendFlowColors) {
        val xml = readColors(path).filterKeys { it.startsWith("sf_") }
        assertEquals("Token names in $path", xml.keys.sorted(), tokens.keys.sorted())
        val kotlin = tokens.mapValues { (_, token) -> hex(token.get(palette)) }
        assertEquals("Token values in $path", xml.toSortedMap(), kotlin.toSortedMap())
    }

    private fun hex(color: Color) = "%06X".format(color.toArgb() and 0xFFFFFF)

    private fun readColors(path: String): Map<String, String> {
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(path))
            .getElementsByTagName("color")
        return (0 until nodes.length).associate { i ->
            val node = nodes.item(i)
            node.attributes.getNamedItem("name").nodeValue to node.textContent.trim().removePrefix("#").takeLast(6).uppercase()
        }
    }
}
