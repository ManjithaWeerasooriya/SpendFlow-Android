package com.example.spendflow.gallery

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.spendflow.core.ui.components.CategoryChip
import com.example.spendflow.core.ui.components.SpendFlowSnackbar
import com.example.spendflow.core.ui.components.SummaryCardView
import com.example.spendflow.databinding.ActivityComponentGalleryBinding
import com.example.spendflow.domain.model.Money

/**
 * Debug-only gallery of the design-system components in every state.
 * Follows the system theme unless started with [EXTRA_DARK]; "Switch theme" flips it.
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
        val binding = ActivityComponentGalleryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        binding.toggleTheme.setOnClickListener {
            startActivity(intent(this, dark = !isDark()))
            finish()
        }

        val chips = with(binding.chips.root) { (0 until childCount).map { getChildAt(it) as CategoryChip } }
        chips.forEach { chip -> chip.setOnClickListener { chips.forEach { it.isChecked = it == chip } } }

        binding.summary.summaryCard.setSummary(
            SummaryCardView.Summary(
                income = Money(185_000_00, "LKR"),
                incomeCaption = "3 sources",
                expenses = Money(112_400_00, "LKR"),
                expensesCaption = "61% of income",
                net = Money(72_600_00, "LKR"),
                savedRatePercent = 39,
            ),
        )

        binding.snackbars.showSuccessSnackbar.setOnClickListener {
            SpendFlowSnackbar.make(binding.root, SAVED_MESSAGE, SpendFlowSnackbar.Tone.SUCCESS, "Undo").show()
        }
        binding.snackbars.showErrorSnackbar.setOnClickListener {
            SpendFlowSnackbar.make(binding.root, "Couldn't save. Check your connection.", SpendFlowSnackbar.Tone.ERROR, "Retry").show()
        }
    }

    private fun isDark() =
        resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

    companion object {
        const val EXTRA_DARK = "dark"
        const val SAVED_MESSAGE = "Saved · Groceries LKR 2,450"

        fun intent(context: Context, dark: Boolean): Intent =
            Intent(context, ComponentGalleryActivity::class.java).putExtra(EXTRA_DARK, dark)
    }
}
