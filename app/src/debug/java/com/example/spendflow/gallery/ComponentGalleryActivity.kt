package com.example.spendflow.gallery

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.spendflow.R
import com.example.spendflow.core.ui.components.AmountSize
import com.example.spendflow.core.ui.components.AmountText
import com.example.spendflow.core.ui.components.AmountTone
import com.example.spendflow.core.ui.components.BottomBarTab
import com.example.spendflow.core.ui.components.CategoryChip
import com.example.spendflow.core.ui.components.DestructiveButton
import com.example.spendflow.core.ui.components.EmptyState
import com.example.spendflow.core.ui.components.LabeledProgress
import com.example.spendflow.core.ui.components.LabeledSwitch
import com.example.spendflow.core.ui.components.PrimaryButton
import com.example.spendflow.core.ui.components.ProgressTone
import com.example.spendflow.core.ui.components.SecondaryButton
import com.example.spendflow.core.ui.components.SpendFlowBottomBar
import com.example.spendflow.core.ui.components.SpendFlowSnackbar
import com.example.spendflow.core.ui.components.SpendFlowSnackbarVisuals
import com.example.spendflow.core.ui.components.SummaryCard
import com.example.spendflow.domain.model.Money
import com.example.spendflow.domain.model.MonthlySummary
import com.example.spendflow.ui.theme.SpendFlowTheme
import kotlinx.coroutines.launch
import java.time.YearMonth

/**
 * Debug-only gallery of the design-system components in every state, on one screen.
 * Follows the system theme unless started with [EXTRA_DARK].
 *
 *   adb shell am start -n com.example.spendflow/.gallery.ComponentGalleryActivity --ez dark true
 */
class ComponentGalleryActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        if (intent.hasExtra(EXTRA_DARK)) {
            delegate.localNightMode =
                if (intent.getBooleanExtra(EXTRA_DARK, false)) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { SpendFlowTheme { Gallery() } }
    }

    companion object {
        const val EXTRA_DARK = "dark"
        const val SAVED_MESSAGE = "Saved · Groceries LKR 2,450"

        /** Test tags, one per component section. */
        val SECTIONS = listOf("buttons", "amounts", "chips", "summary", "progress", "switches", "snackbars", "empty", "bottomBars")

        fun intent(context: Context, dark: Boolean): Intent =
            Intent(context, ComponentGalleryActivity::class.java).putExtra(EXTRA_DARK, dark)
    }
}

@Composable
private fun Gallery() {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Section("buttons") {
                PrimaryButton("Save entry", onClick = {}, icon = R.drawable.ic_plus)
                PrimaryButton("Saving…", onClick = {}, enabled = false)
                SecondaryButton("Cancel", onClick = {})
                DestructiveButton("Log out", onClick = {})
            }
            Section("amounts") {
                AmountText(Money(72_600_00, "LKR"))
                AmountText(Money(2_450_50, "LKR"), size = AmountSize.TITLE, tone = AmountTone.INCOME, showDecimals = true, signed = true)
                AmountText(Money(-1_200_00, "USD"), size = AmountSize.INLINE)
            }
            Section("chips") {
                var selected by remember { mutableIntStateOf(0) }
                Row {
                    listOf("Groceries" to R.drawable.ic_cat_food, "Transport" to R.drawable.ic_cat_transport)
                        .forEachIndexed { i, (label, icon) -> CategoryChip(label, icon, i == selected, onClick = { selected = i }) }
                }
            }
            Section("summary") {
                SummaryCard(
                    MonthlySummary(YearMonth.of(2026, 10), Money(185_000_00, "LKR"), Money(112_400_00, "LKR")),
                    Modifier.fillMaxWidth(),
                    showExpenseRatioBar = true,
                )
            }
            Section("progress") {
                LabeledProgress(Money(240_000_00, "LKR"), Money(500_000_00, "LKR"), "LKR 260,000 to go", Modifier.fillMaxWidth())
                LabeledProgress(
                    Money(20_500_00, "LKR"), Money(20_000_00, "LKR"), "Groceries", Modifier.fillMaxWidth(),
                    status = "Over cap by LKR 500", tone = ProgressTone.OVER_CAP,
                )
            }
            Section("switches") {
                var on by remember { mutableStateOf(true) }
                LabeledSwitch("Tracking streak", on, { on = it }, Modifier.testTag("streakSwitch"), icon = R.drawable.ic_streak)
            }
            Section("snackbars") {
                PrimaryButton("Show success snackbar", onClick = {
                    scope.launch {
                        snackbar.showSnackbar(SpendFlowSnackbarVisuals(ComponentGalleryActivity.SAVED_MESSAGE, SpendFlowSnackbar.Tone.SUCCESS, "Undo"))
                    }
                })
                SnackbarHost(snackbar) { SpendFlowSnackbar(it) }
            }
            Section("empty") {
                EmptyState("No transactions yet", icon = R.drawable.ic_transactions, message = "Entries you add will show up here.", actionText = "Add transaction")
            }
            Section("bottomBars") {
                val tabs = listOf(
                    BottomBarTab("home", R.string.nav_home, R.drawable.ic_home_mono),
                    BottomBarTab("transactions", R.string.nav_transactions, R.drawable.ic_transactions_mono),
                    BottomBarTab("analytics", R.string.nav_analytics, R.drawable.ic_analytics_mono),
                    BottomBarTab("more", R.string.nav_more, R.drawable.ic_more_mono),
                )
                var selected by remember { mutableStateOf(tabs.first()) }
                SpendFlowBottomBar(tabs, selected, onSelect = { selected = it }, onAdd = {})
            }
        }
    }
}

@Composable
private fun Section(tag: String, content: @Composable () -> Unit) {
    Column(Modifier.testTag(tag), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(tag, style = MaterialTheme.typography.labelSmall)
        content()
    }
}
