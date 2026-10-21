package com.example.spendflow.core.ui.components

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.navigation.NavController
import androidx.navigation.ui.setupWithNavController
import com.example.spendflow.R
import com.example.spendflow.databinding.ViewBottomBarBinding
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.MaterialShapeDrawable

/**
 * Home · Transactions · [+] · Analytics · More, with the Add button raised over the middle slot (02a).
 * The view is taller than the bar by the button's overhang; use [barHeight] to keep content clear of the bar.
 */
class SpendFlowBottomBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding = ViewBottomBarBinding.inflate(LayoutInflater.from(context), this)

    /** Height of the bar itself, including its navigation-bar inset but not the button's overhang. */
    val barHeight: Int get() = binding.navigation.height

    init {
        clipChildren = false
        with(binding.navigation) {
            isItemActiveIndicatorEnabled = false
            (background as? MaterialShapeDrawable)?.let { shape ->
                val radius = resources.getDimension(R.dimen.sf_corner_shell)
                shape.shapeAppearanceModel = shape.shapeAppearanceModel.toBuilder()
                    .setTopLeftCorner(CornerFamily.ROUNDED, radius)
                    .setTopRightCorner(CornerFamily.ROUNDED, radius)
                    .build()
            }
            // The slot only carries the "Add" label; screen readers reach the button instead
            findViewById<View>(R.id.addSlot)?.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
    }

    fun setupWithNavController(navController: NavController) {
        binding.navigation.setupWithNavController(navController)
        // The bar only shows on tab roots, so reselecting has nothing to pop; don't recreate the screen
        binding.navigation.setOnItemReselectedListener { }
    }

    fun setOnAddClickListener(listener: OnClickListener?) = binding.addButton.setOnClickListener(listener)
}
