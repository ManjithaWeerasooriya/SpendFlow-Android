package com.example.spendflow.ui.goals

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.spendflow.R
import com.example.spendflow.core.common.AppError
import com.example.spendflow.core.ui.UiState
import com.example.spendflow.domain.model.Money
import com.example.spendflow.domain.model.SavingsGoal
import com.example.spendflow.ui.theme.SpendFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The stateless Goals screen and goal dialog: notification prompt, progress rows, updates and form errors. */
@RunWith(AndroidJUnit4::class)
class GoalsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun text(id: Int, vararg args: Any) = context.getString(id, *args)

    private val laptop = SavingsGoal("laptop", "Laptop", Money(500_000_00, "LKR"), Money(240_000_00, "LKR"))
    private val trip = SavingsGoal("trip", "Trip", Money(100_000_00, "LKR"), Money(100_000_00, "LKR"))

    private fun show(
        goals: UiState<List<SavingsGoal>> = UiState.Success(listOf(laptop, trip)),
        notificationsOn: Boolean = false,
        permissionAsked: Boolean = false,
        onEnable: () -> Unit = {},
        onUpdate: (SavingsGoal) -> Unit = {},
    ) = compose.setContent {
        SpendFlowTheme {
            GoalsScreen(goals, notificationsOn, permissionAsked, onBack = {}, onEnableNotifications = onEnable, onAddGoal = {}, onUpdateGoal = onUpdate)
        }
    }

    @Test
    fun notificationPromptFollowsPermissionState() {
        var taps = 0
        show(onEnable = { taps++ })
        compose.onNodeWithText(text(R.string.goals_notifications_off)).assertExists()
        compose.onNodeWithText(text(R.string.goals_enable_notifications)).performClick()
        assertEquals(1, taps)
    }

    @Test
    fun askedPermissionPointsToSettings() {
        show(permissionAsked = true)
        compose.onNodeWithText(text(R.string.goals_notification_settings)).assertExists()
    }

    @Test
    fun enabledNotificationsHideTheButton() {
        show(notificationsOn = true)
        compose.onNodeWithText(text(R.string.goals_notifications_on)).assertExists()
        compose.onNodeWithText(text(R.string.goals_notification_settings)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.goals_enable_notifications)).assertDoesNotExist()
    }

    @Test
    fun rowsShowCompletionAndUpdateTheirOwnGoal() {
        val updated = mutableListOf<String>()
        show(onUpdate = { updated += it.id })
        compose.onNodeWithText(text(R.string.goal_completed_label, "Trip")).assertExists()
        compose.onNodeWithText("Laptop").assertExists()
        compose.onNodeWithContentDescription("${text(R.string.goal_update)}: Laptop").performClick()
        assertEquals(listOf("laptop"), updated)
    }

    @Test
    fun errorStateShowsTheMessage() {
        show(goals = UiState.Error(AppError.Network))
        compose.onNodeWithText(text(R.string.error_network)).assertExists()
    }

    @Test
    fun emptyStateInvitesAFirstGoal() {
        show(goals = UiState.Success(emptyList()))
        compose.onNodeWithText(text(R.string.goals_empty)).assertExists()
    }

    @Test
    fun dialogPrefillsTheGoalAndSavesEditedValues() {
        var saved: Triple<String, String, String>? = null
        compose.setContent {
            SpendFlowTheme {
                GoalDialog(laptop, "LKR", saving = false, errors = GoalFormErrors(), writeError = null,
                    onSave = { n, t, s -> saved = Triple(n, t, s) }, onDismiss = {})
            }
        }
        compose.onNodeWithText(text(R.string.goal_currency, "LKR")).assertExists()
        compose.onNode(hasSetTextAction() and hasText("500000.00")).assertExists()
        compose.onNode(hasSetTextAction() and hasText("240000.00")).performTextReplacement("250000")
        compose.onNodeWithText(text(R.string.quick_save)).performClick()
        assertEquals(Triple("Laptop", "500000.00", "250000"), saved)
    }

    @Test
    fun dialogShowsFieldAndWriteErrorsAndLocksWhileSaving() {
        compose.setContent {
            SpendFlowTheme {
                GoalDialog(null, "LKR", saving = true,
                    errors = GoalFormErrors(name = R.string.goal_name_required), writeError = R.string.error_network,
                    onSave = { _, _, _ -> }, onDismiss = {})
            }
        }
        compose.onNodeWithText(text(R.string.goal_name_required)).assertExists()
        compose.onNodeWithText(text(R.string.error_network)).assertExists()
        compose.onNode(hasSetTextAction() and hasText("0")).assertExists()
        compose.onNode(hasText(text(R.string.quick_save)) and hasClickAction()).assertIsNotEnabled()
    }
}
