package com.example.spendflow.core.ui

import android.os.Bundle
import android.view.View
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.spendflow.R
import com.example.spendflow.databinding.FragmentPlaceholderBinding

/**
 * Stand-in for a screen whose story hasn't landed yet, so navigation can be built and tested first.
 * Tab roots show a screen title; pushed screens ([pushed]) show a top bar with Back.
 */
abstract class PlaceholderFragment(
    @StringRes private val title: Int,
    @StringRes private val message: Int,
    @DrawableRes private val icon: Int,
    private val pushed: Boolean = false,
) : Fragment(R.layout.fragment_placeholder) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentPlaceholderBinding.bind(view)
        binding.toolbar.isVisible = pushed
        binding.screenTitle.isVisible = !pushed
        binding.toolbar.setTitle(title)
        binding.screenTitle.setText(title)
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.emptyState.setIcon(icon)
        binding.emptyState.title = getString(R.string.placeholder_title)
        binding.emptyState.message = getString(message)
    }
}
