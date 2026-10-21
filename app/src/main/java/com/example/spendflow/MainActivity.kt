package com.example.spendflow

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.navigation.fragment.DialogFragmentNavigator
import androidx.navigation.fragment.NavHostFragment
import com.example.spendflow.core.ui.collectWithLifecycle
import com.example.spendflow.databinding.ActivityMainBinding
import com.example.spendflow.ui.main.MainViewModel
import com.example.spendflow.ui.main.openQuickAdd
import com.example.spendflow.ui.main.setRootGraph
import com.example.spendflow.ui.main.showGraph
import com.example.spendflow.ui.main.topLevelDestinations
import dagger.hilt.android.AndroidEntryPoint

/** Hosts the auth and main navigation graphs and the bottom bar shown on the main tabs. */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    // Firestore keeps retrying, so once this is granted the pending write goes through
    private val requestLocalNetwork =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) Log.w("SF-01", "Local network access denied; Firebase emulators unreachable")
        }

    private val viewModel: MainViewModel by viewModels()
    private lateinit var binding: ActivityMainBinding
    private var bottomInset = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (BuildConfig.DEBUG) {
            // Android 17+ treats 10.0.2.2 as local network and silently drops traffic to it
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_LOCAL_NETWORK)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestLocalNetwork.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
            }
        }
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navController = binding.navHost.getFragment<NavHostFragment>().navController
        navController.setRootGraph(viewModel.graph.value)
        binding.bottomBar.setupWithNavController(navController)
        binding.bottomBar.setOnAddClickListener { navController.openQuickAdd() }
        navController.addOnDestinationChangedListener { _, destination, _ ->
            // Keep the bar under Quick Add instead of hiding it behind the sheet
            if (destination !is DialogFragmentNavigator.Destination) {
                binding.bottomBar.isVisible = destination.id in topLevelDestinations
                applyContentInset()
            }
        }
        viewModel.graph.collectWithLifecycle(this) { navController.showGraph(it) }

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, 0)
            // Include the keyboard so the input stays visible while typing (edge-to-edge disables adjustResize)
            bottomInset = maxOf(bars.bottom, insets.getInsets(WindowInsetsCompat.Type.ime()).bottom)
            applyContentInset()
            insets
        }
        // The bar pads itself for the navigation bar, so its height is only known after layout
        binding.bottomBar.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> applyContentInset() }
    }

    /** Keeps screen content clear of the bottom bar, or of the system bars and keyboard when it's hidden. */
    private fun applyContentInset() {
        val bottom = if (binding.bottomBar.isVisible) binding.bottomBar.barHeight else bottomInset
        if (binding.navHost.paddingBottom != bottom) binding.navHost.updatePadding(bottom = bottom)
    }
}
