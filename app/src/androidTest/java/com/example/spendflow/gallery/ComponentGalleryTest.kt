package com.example.spendflow.gallery

import android.content.res.Configuration
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.core.content.ContextCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.example.spendflow.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Every component renders in both themes, with the matching deck palette (AC1, AC4). */
@RunWith(Parameterized::class)
class ComponentGalleryTest(private val dark: Boolean) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "dark={0}")
        fun modes() = listOf(false, true)
    }

    @get:Rule
    val compose = createEmptyComposeRule()

    private fun launch() = ActivityScenario.launch<ComponentGalleryActivity>(
        ComponentGalleryActivity.intent(InstrumentationRegistry.getInstrumentation().targetContext, dark),
    )

    @Test
    fun usesThePaletteForTheMode() {
        launch().use { scenario ->
            scenario.onActivity { activity ->
                val night = activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                assertEquals(dark, night == Configuration.UI_MODE_NIGHT_YES)
                val page = ContextCompat.getColor(activity, R.color.sf_page)
                assertEquals(if (dark) 0xFF0B0E14.toInt() else 0xFFECEDF0.toInt(), page)
            }
        }
    }

    @Test
    fun rendersEveryComponent() {
        launch().use {
            ComponentGalleryActivity.SECTIONS.forEach { compose.onNodeWithTag(it).performScrollTo().assertIsDisplayed() }
        }
    }

    @Test
    fun switchTogglesFromTheWholeRow() {
        launch().use {
            compose.onNodeWithTag("streakSwitch").performScrollTo().assertIsOn()
            compose.onNodeWithTag("streakSwitch").performClick()
            compose.onNodeWithTag("streakSwitch").assertIsOff()
        }
    }

    @Test
    fun snackbarShowsMessageAndAction() {
        launch().use {
            compose.onNodeWithText("Show success snackbar").performScrollTo().performClick()
            // The gallery shows the snackbar in place, inside the scrolling list
            compose.onNodeWithText(ComponentGalleryActivity.SAVED_MESSAGE).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Undo").assertIsDisplayed()
        }
    }
}
