package com.example.spendflow.ui.theme

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Screens must take colours from theme attributes so they follow the light/dark setting (AC1).
 * A literal or a direct @color reference in a layout would look the same in both modes.
 */
class ThemeOnlyColorsTest {

    private val literal = Regex("""="#\p{XDigit}{3,8}"""")
    private val directColor = Regex("""="@(android:)?color/""")
    private val codeColor = Regex("""Color\.(parseColor|rgb|argb)\(|R\.color\.""")

    @Test
    fun `layouts use theme attributes for colours`() {
        val offenders = layoutFiles().flatMap { file ->
            file.readLines().withIndex()
                .filter { (_, line) -> literal.containsMatchIn(line) || directColor.containsMatchIn(line) }
                .map { (i, line) -> "${file.path}:${i + 1}: ${line.trim()}" }
        }
        assertTrue("Use ?attr/ colours instead:\n${offenders.joinToString("\n")}", offenders.isEmpty())
    }

    @Test
    fun `kotlin code resolves colours from the theme`() {
        val offenders = File("src").walkTopDown()
            .filter { it.extension == "kt" && "/test/" !in it.invariantSeparatorsPath && "/androidTest/" !in it.invariantSeparatorsPath }
            .flatMap { file ->
                file.readLines().withIndex()
                    .filter { (_, line) -> codeColor.containsMatchIn(line) }
                    .map { (i, line) -> "${file.path}:${i + 1}: ${line.trim()}" }
            }.toList()
        assertTrue("Resolve colours with MaterialColors.getColor instead:\n${offenders.joinToString("\n")}", offenders.isEmpty())
    }

    private fun layoutFiles(): List<File> = File("src").walkTopDown()
        .filter { it.isFile && it.extension == "xml" && it.parentFile.name.startsWith("layout") }
        .toList()
        .also { assertTrue("No layouts found; is the working directory the app module?", it.isNotEmpty()) }
}
