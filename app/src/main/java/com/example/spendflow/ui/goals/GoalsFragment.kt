package com.example.spendflow.ui.goals

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.spendflow.R
import com.example.spendflow.core.common.AppResult
import com.example.spendflow.core.notifications.AndroidGoalNotifications
import com.example.spendflow.core.notifications.GoalNotificationMonitor
import com.example.spendflow.core.ui.collectWithLifecycle
import com.example.spendflow.core.ui.components.LabeledProgressView
import com.example.spendflow.core.ui.components.SpendFlowSnackbar
import com.example.spendflow.core.ui.messageRes
import com.example.spendflow.databinding.DialogGoalBinding
import com.example.spendflow.databinding.FragmentGoalsBinding
import com.example.spendflow.domain.model.SavingsGoal
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import java.math.BigDecimal
import java.util.Currency
import javax.inject.Inject

@AndroidEntryPoint
class GoalsFragment : Fragment(R.layout.fragment_goals) {
    private val model: GoalsViewModel by viewModels()
    @Inject lateinit var notifications: AndroidGoalNotifications
    @Inject lateinit var monitor: GoalNotificationMonitor
    private var dialog: AlertDialog? = null
    private var form: DialogGoalBinding? = null
    private val requestNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        updateNotificationStatus()
        monitor.recheck()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = FragmentGoalsBinding.bind(view)
        b.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        b.addGoal.setOnClickListener { showGoalDialog(null) }
        b.enableNotifications.setOnClickListener {
            if (Build.VERSION.SDK_INT >= 33 && !permissionAsked()) {
                requireContext().getSharedPreferences("goal_notification_permission", 0).edit().putBoolean("asked", true).apply()
                requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                val settings = if (Build.VERSION.SDK_INT >= 26) Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
                else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${requireContext().packageName}"))
                startActivity(settings)
            }
        }
        model.goals.collectWithLifecycle(viewLifecycleOwner) { result ->
            b.goalsList.removeAllViews()
            val goals = (result as? AppResult.Success)?.data.orEmpty().sortedWith(compareBy<SavingsGoal> { it.isCompleted }.thenBy { it.name.lowercase() })
            b.status.isVisible = result !is AppResult.Success || goals.isEmpty()
            b.status.setText(when (result) {
                null -> R.string.goals_loading
                is AppResult.Error -> result.error.messageRes
                is AppResult.Success -> R.string.goals_empty
            })
            goals.forEach { goal ->
                b.goalsList.addView(LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.VERTICAL
                    val spacing = resources.getDimensionPixelSize(R.dimen.sf_space_l)
                    setPadding(0, spacing, 0, spacing)
                    addView(LabeledProgressView(context).apply {
                        bind(goal.saved, goal.target, if (goal.isCompleted) getString(R.string.goal_completed_label, goal.name) else goal.name, showDecimals = true)
                    })
                    addView(MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                        text = getString(R.string.goal_update)
                        contentDescription = "${getString(R.string.goal_update)}: ${goal.name}"
                        setOnClickListener { showGoalDialog(goal) }
                    })
                })
            }
        }
        model.errors.collectWithLifecycle(viewLifecycleOwner) { errors ->
            form?.let { f ->
                f.nameLayout.error = errors.name?.let(::getString)
                f.targetLayout.error = errors.target?.let(::getString)
                f.savedLayout.error = errors.saved?.let(::getString)
                f.errorText.text = errors.general?.let(::getString)
            }
        }
        model.saving.collectWithLifecycle(viewLifecycleOwner) { dialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = !it }
        model.events.collectWithLifecycle(viewLifecycleOwner) { result ->
            when (result) {
                is AppResult.Success -> {
                    dialog?.dismiss()
                    SpendFlowSnackbar.make(view, getString(R.string.goal_saved), SpendFlowSnackbar.Tone.SUCCESS).show()
                }
                is AppResult.Error -> {
                    if (form != null) form?.errorText?.setText(result.error.messageRes)
                    else SpendFlowSnackbar.make(view, getString(result.error.messageRes), SpendFlowSnackbar.Tone.ERROR).show()
                }
            }
        }
    }

    private fun permissionAsked() = requireContext().getSharedPreferences("goal_notification_permission", 0).getBoolean("asked", false)

    override fun onResume() {
        super.onResume()
        updateNotificationStatus()
        monitor.recheck()
    }

    private fun updateNotificationStatus() {
        val b = view?.let(FragmentGoalsBinding::bind) ?: return
        val enabled = notifications.enabled()
        b.notificationStatus.setText(if (enabled) R.string.goals_notifications_on else R.string.goals_notifications_off)
        b.enableNotifications.isVisible = !enabled
        b.enableNotifications.setText(if (permissionAsked()) R.string.goals_notification_settings else R.string.goals_enable_notifications)
    }

    private fun showGoalDialog(goal: SavingsGoal?) {
        if (dialog?.isShowing == true) return
        model.resetErrors()
        val f = DialogGoalBinding.inflate(layoutInflater)
        form = f
        val currency = goal?.target?.currencyCode ?: model.baseCurrency ?: "LKR"
        val digits = Currency.getInstance(currency).defaultFractionDigits
        f.nameInput.setText(goal?.name.orEmpty())
        f.targetInput.setText(goal?.let { BigDecimal.valueOf(it.target.amountMinor).movePointLeft(digits).toPlainString() }.orEmpty())
        f.savedInput.setText(goal?.let { BigDecimal.valueOf(it.saved.amountMinor).movePointLeft(digits).toPlainString() } ?: "0")
        f.currencyLabel.text = getString(R.string.goal_currency, currency)
        dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (goal == null) R.string.goals_add else R.string.goal_edit)
            .setView(f.root).setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.quick_save, null).create().apply {
                setOnDismissListener { form = null; dialog = null }
                setOnShowListener {
                    getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = !model.saving.value
                    getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        model.save(goal, f.nameInput.text.toString(), f.targetInput.text.toString(), f.savedInput.text.toString())
                    }
                }
                show()
            }
    }

    override fun onDestroyView() {
        dialog?.dismiss()
        dialog = null
        form = null
        super.onDestroyView()
    }
}
