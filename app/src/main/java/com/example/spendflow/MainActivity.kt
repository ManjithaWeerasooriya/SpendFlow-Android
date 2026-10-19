package com.example.spendflow

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.spendflow.core.common.AppResult
import com.example.spendflow.data.repository.CategoryRepository
import com.example.spendflow.data.session.UserSession
import com.example.spendflow.domain.model.Category
import com.example.spendflow.domain.model.CategoryType
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject
    lateinit var session: UserSession

    @Inject
    lateinit var categories: CategoryRepository

    // Firestore keeps retrying, so once this is granted the pending write goes through
    private val requestLocalNetwork =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) Log.w("SF-01", "Local network access denied; Firebase emulators unreachable")
        }

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
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Temporary end-to-end check of session + repository; replaced by the reference feature
        lifecycleScope.launch {
            val signIn = session.ensureSignedIn()
            if (signIn !is AppResult.Success) {
                Log.w("SF-01", "Sign-in failed: $signIn")
                return@launch
            }
            categories.create(Category(id = "smoke-test", name = "Smoke test", type = CategoryType.EXPENSE))
            val listed = categories.observeCategories().first { it is AppResult.Success && it.data.isNotEmpty() }
            Log.d("SF-01", "uid=${signIn.data} categories=$listed get=${categories.get("smoke-test")}")
        }
    }
}