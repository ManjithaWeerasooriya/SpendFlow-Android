package com.example.spendflow.ui.goals

import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
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
import java.time.LocalDate
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

    private val laptop = SavingsGoal("laptop", "Laptop", Money(500_000_00, "LKR"), Money(240_000_00, "LKR"), LocalDate.of(2027, 3, 31))
    private val trip = SavingsGoal("trip", "Trip", Money(100_000_00, "LKR"), Money(100_000_00, "LKR"))

    private fun show(
        goals: UiState<List<SavingsGoal>> = UiState.Success(listOf(laptop, trip)),
        notificationsOn: Boolean = false,
        permissionAsked: Boolean = false,
        onEnable: () -> Unit = {},
        onOpen: (SavingsGoal) -> Unit = {},
    ) = compose.setContent {
        SpendFlowTheme {
            GoalsScreen(LocalDate.of(2026, 10, 11), Money(45_000_00, "LKR"), goals, notificationsOn, permissionAsked, onBack = {}, onEnableNotifications = onEnable, onAddGoal = {}, onOpenGoal = onOpen)
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
    fun rowsShowCompletionAndOpenTheirOwnGoal() {
        val updated = mutableListOf<String>()
        show(onOpen = { updated += it.id })
        compose.onNodeWithText(text(R.string.goal_completed_label, "Trip")).assertExists()
        compose.onNodeWithText("Laptop").assertExists()
        compose.onNode(SemanticsMatcher("Open details action for Laptop") {
            it.config.getOrNull(SemanticsActions.OnClick)?.label == text(R.string.goal_open_detail, "Laptop")
        }).performClick()
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
        var saved: List<Any?>? = null
        compose.setContent {
            SpendFlowTheme {
                GoalDialog(laptop, "LKR", saving = false, errors = GoalFormErrors(), writeError = null, today = LocalDate.of(2026, 10, 11),
                    onSave = { n, t, s, d -> saved = listOf(n, t, s, d) }, onDismiss = {})
            }
        }
        compose.onNodeWithText(text(R.string.goal_currency, "LKR")).assertExists()
        compose.onNode(hasSetTextAction() and hasText("500000.00")).assertExists()
        compose.onNode(hasSetTextAction() and hasText("240000.00")).performTextReplacement("250000")
        compose.onNodeWithText(text(R.string.quick_save)).performClick()
        assertEquals(listOf<Any?>("Laptop", "500000.00", "250000", LocalDate.of(2027, 3, 31)), saved)
    }

    @Test
    fun dialogShowsFieldAndWriteErrorsAndLocksWhileSaving() {
        compose.setContent {
            SpendFlowTheme {
                GoalDialog(null, "LKR", saving = true,
                    errors = GoalFormErrors(name = R.string.goal_name_required), writeError = R.string.error_network, today = LocalDate.of(2026, 10, 11),
                    onSave = { _, _, _, _ -> }, onDismiss = {})
            }
        }
        compose.onNodeWithText(text(R.string.goal_name_required)).assertExists()
        compose.onNodeWithText(text(R.string.error_network)).assertExists()
        compose.onNode(hasSetTextAction() and hasText("0")).assertExists()
        compose.onNode(hasText(text(R.string.goal_create)) and hasClickAction()).assertIsNotEnabled()
    }

    @Test
    fun newGoalNeedsValidTargetAndFutureDeadlineBeforeCreate() {
        compose.setContent {
            SpendFlowTheme {
                GoalDialog(null, "LKR", saving = false, errors = GoalFormErrors(), writeError = null,
                    today = LocalDate.of(2026, 10, 11), onSave = { _, _, _, _ -> }, onDismiss = {})
            }
        }
        compose.onNode(hasText(text(R.string.goal_create)) and hasClickAction()).assertIsNotEnabled()
        compose.onAllNodes(hasSetTextAction())[1].performTextReplacement("-5")
        compose.onNodeWithText(text(R.string.entry_amount_positive)).assertExists()
    }

    @Test
    fun detailOffersAddMoneyAndNamesGoalOnDelete() {
        var deleted = false
        compose.setContent {
            SpendFlowTheme {
                GoalDetailScreen(laptop, LocalDate.of(2026, 10, 11), null, onBack = {}, onEdit = {},
                    onAddMoney = { _, _, _ -> null }, onMarkCompleted = {}, onSetPrimary = {}, onDelete = { deleted = true })
            }
        }
        compose.onNodeWithText(text(R.string.goal_add_money)).assertExists()
        compose.onNodeWithText(text(R.string.goal_more_actions)).performClick()
        compose.onNodeWithText(text(R.string.goal_delete)).performClick()
        compose.onNodeWithText(text(R.string.goal_delete_message, "Laptop")).assertExists()
        compose.onNode(hasText(text(R.string.goal_delete)) and hasClickAction()).performClick()
        assertEquals(true, deleted)
    }
}
