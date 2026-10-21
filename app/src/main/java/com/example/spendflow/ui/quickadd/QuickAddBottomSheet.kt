package com.example.spendflow.ui.quickadd

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.spendflow.databinding.SheetQuickAddBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/** Opened by the central Add button from any tab. The entry form arrives with the Quick Add story (03). */
class QuickAddBottomSheet : BottomSheetDialogFragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        SheetQuickAddBinding.inflate(inflater, container, false).also { binding ->
            binding.closeButton.setOnClickListener { dismiss() }
        }.root
}
