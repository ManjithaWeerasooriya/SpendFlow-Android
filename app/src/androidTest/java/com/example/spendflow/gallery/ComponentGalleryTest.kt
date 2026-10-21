package com.example.spendflow.gallery

import android.content.res.Configuration
import androidx.core.content.ContextCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import com.example.spendflow.R
import org.hamcrest.Matchers.allOf
import org.junit.Assert.assertEquals
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

        // <include> ids in activity_component_gallery.xml, one per component preview
        private val previews = listOf(
            R.id.buttons,
            R.id.amounts,
            R.id.chips,
            R.id.summary,
            R.id.progress,
            R.id.switches,
            R.id.snackbars,
            R.id.empty,
            R.id.bottomBars,
        )
    }

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
            previews.forEach { id -> onView(withId(id)).perform(scrollTo()).check(matches(isDisplayed())) }
        }
    }

    @Test
    fun switchSpellsOutItsState() {
        launch().use {
            val state = allOf(withId(R.id.state), isDescendantOfA(withId(R.id.streakSwitch)))
            onView(withId(R.id.streakSwitch)).perform(scrollTo())
            onView(state).check(matches(withText(R.string.switch_on)))
            onView(withId(R.id.streakSwitch)).perform(click())
            onView(state).check(matches(withText(R.string.switch_off)))
        }
    }

    @Test
    fun snackbarShowsMessageAndAction() {
        launch().use {
            onView(withId(R.id.showSuccessSnackbar)).perform(scrollTo(), click())
            onView(withText(ComponentGalleryActivity.SAVED_MESSAGE)).check(matches(isDisplayed()))
            onView(withText("Undo")).check(matches(isDisplayed()))
        }
    }
}
