package com.example.spendflow.ui.transactions

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.spendflow.R
import com.example.spendflow.core.ui.UiState
import com.example.spendflow.domain.model.*
import com.example.spendflow.ui.theme.SpendFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.YearMonth

@RunWith(AndroidJUnit4::class)
class TransactionMutationScreenTest {
    @get:Rule val compose = createComposeRule()
    private val row = FeedRow("stored", "G", "Weekly shop", "Groceries", Money(-245000, "LKR"), TransactionType.EXPENSE, "Groceries")
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var edits = mutableListOf<String>()
    private var deletions = 0

    private fun show() {
        compose.setContent {
            var pending by remember { mutableStateOf<FeedRow?>(null) }
            SpendFlowTheme {
                TransactionFeedScreen(
                    FeedUiState(UiState.Success(listOf(row)), null, null, "ALL", YearMonth.now()),
                    home = false, onFilter = {}, onSeeAll = {}, onViewGoals = {},
                    onEdit = { edits += it }, onDelete = { pending = it },
                )
                pending?.let {
                    TransactionDeleteDialog(it, deleting = false, error = null,
                        onCancel = { pending = null }, onConfirm = { deletions++; pending = null })
                }
            }
        }
    }

    @Test fun tapRequestsEditForTheSelectedTransaction() {
        show()
        compose.onNodeWithTag(FeedTags.ROW).performClick()
        assertEquals(listOf("stored"), edits)
        assertEquals(0, deletions)
    }

    @Test fun swipeOnlyRevealsDeleteAndCancelDoesNotDelete() {
        show()
        compose.onNodeWithTag(FeedTags.ROW).performTouchInput { swipeLeft() }
        compose.onNodeWithTag("delete_stored").performClick()
        compose.onNode(hasText("Groceries", substring = true) and hasAnyAncestor(isDialog())).assertExists()
        assertEquals(0, deletions)
        compose.onNodeWithText(context.getString(R.string.action_cancel)).performClick()
        assertEquals(0, deletions)
        compose.onNodeWithTag(FeedTags.ROW).assertExists()
        compose.onNodeWithTag("delete_stored").performClick()
        compose.onNode(hasText(context.getString(R.string.transaction_delete)) and hasAnyAncestor(isDialog())).performClick()
        assertEquals(1, deletions)
    }
}
