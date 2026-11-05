package com.example.spendflow.ui.theme

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Screens must take colours from the theme so they follow the light/dark setting (AC1).
 * A colour literal or a direct R.color reference would look the same in both modes.
 */
class ThemeOnlyColorsTest {

    private val codeColor = Regex("""Color\.(parseColor|rgb|argb)\(|R\.color\.""")
    private val composeLiteral = Regex("""\bColor\(\s*0x""")

    @Test
    fun `kotlin code resolves colours from the theme`() {
        val offenders = File("src").walkTopDown()
            .filter { it.extension == "kt" && "/test/" !in it.invariantSeparatorsPath && "/androidTest/" !in it.invariantSeparatorsPath }
            .flatMap { file ->
                file.readLines().withIndex()
                    .filter { (_, line) -> codeColor.containsMatchIn(line) }
                    .map { (i, line) -> "${file.path}:${i + 1}: ${line.trim()}" }
            }.toList()
        assertTrue("Take colours from MaterialTheme.colorScheme or SpendFlowTheme.colors instead:\n${offenders.joinToString("\n")}", offenders.isEmpty())
    }

    @Test
    fun `composables take colours from the theme`() {
        val offenders = File("src/main").walkTopDown()
            .filter { it.extension == "kt" && "/ui/theme/" !in it.invariantSeparatorsPath }
            .flatMap { file ->
                file.readLines().withIndex()
                    .filter { (_, line) -> composeLiteral.containsMatchIn(line) }
                    .map { (i, line) -> "${file.path}:${i + 1}: ${line.trim()}" }
            }.toList()
        assertTrue("Use MaterialTheme.colorScheme or SpendFlowTheme.colors instead:\n${offenders.joinToString("\n")}", offenders.isEmpty())
    }
}
