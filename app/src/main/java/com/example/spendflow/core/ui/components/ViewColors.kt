package com.example.spendflow.core.ui.components

import android.content.res.ColorStateList
import android.view.View
import androidx.annotation.AttrRes
import com.google.android.material.color.MaterialColors

internal fun View.colorStateList(@AttrRes attr: Int): ColorStateList =
    ColorStateList.valueOf(MaterialColors.getColor(this, attr))
