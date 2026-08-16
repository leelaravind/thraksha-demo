package com.thraksha.guardian

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.thraksha.guardian.ai.LocalModelRepository
import com.thraksha.guardian.services.CompanionForegroundService
import com.thraksha.guardian.ui.components.WhitelistingDialog
import com.thraksha.guardian.ui.design.ThemePreference
import com.thraksha.guardian.ui.design.ThrakshaTheme
import com.thraksha.guardian.ui.nav.ThrakshaNavHost
import com.thraksha.guardian.ui.screens.SplashScreen

class MainActivity : ComponentActivity() {

    private var isSplashFinished by mutableStateOf(false)
    private var showSamsungDialog by mutableStateOf(false)

    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allGranted = permissions.entries.all { it.value }
            if (allGranted) {
                startCompanionService()
            } else {
                Toast.makeText(this, "Notification permission recommended", Toast.LENGTH_SHORT).show()
                // Start anyway — the foreground service can run without POST_NOTIFICATIONS.
                startCompanionService()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        consumeFocusExtra(intent)

        // The appearance preference lives in the encrypted config table; read it before
        // the first frame so an explicit Light/Dark choice does not flash the other theme.
        lifecycleScope.launch { ThemePreference.load(this@MainActivity) }

        // Verify the on-device model once per process, at startup rather than only when
        // Automate is opened. Without this, every other surface reports the model as
        // "not installed" simply because nothing had checked yet — which is not the same
        // thing, and Settings would state it as fact.
        lifecycleScope.launch { LocalModelRepository.verify(this@MainActivity) }

        setContent {
            ThrakshaTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    if (!isSplashFinished) {
                        SplashScreen(onComplete = { isSplashFinished = true })
                    } else {
                        MainContent()
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        consumeFocusExtra(intent)
    }

    /** Security-notification deep link (Phase 8.1): focus the named app's evidence card. */
    private fun consumeFocusExtra(intent: Intent?) {
        intent?.getStringExtra(
            com.thraksha.guardian.security.notify.SecurityNotifier.EXTRA_FOCUS_PACKAGE,
        )?.let { com.thraksha.guardian.ui.state.UiFocus.requestFocus(it) }
    }

    @Composable
    private fun MainContent() {
        LaunchedEffect(Unit) {
            checkSamsungAndStart()
        }

        // The shell owns its own scaffolding and system-bar insets; the activity only
        // hosts it and the one start-up dialog.
        Box(modifier = Modifier.fillMaxSize()) {
            ThrakshaNavHost()

            if (showSamsungDialog) {
                WhitelistingDialog(
                    onConfirm = {
                        showSamsungDialog = false
                        openSamsungBatterySettings()
                        // Continue the start-up chain so the service still runs
                        // whether or not the user changes the battery setting.
                        handlePermissionsAndStart()
                    },
                    onDismiss = {
                        showSamsungDialog = false
                        handlePermissionsAndStart()
                    },
                )
            }
        }
    }

    private fun checkSamsungAndStart() {
        val isSamsung = Build.MANUFACTURER.contains("samsung", ignoreCase = true)
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val isWhitelisted = pm.isIgnoringBatteryOptimizations(packageName)

        if (isSamsung && !isWhitelisted) {
            showSamsungDialog = true
        } else {
            handlePermissionsAndStart()
        }
    }

    private fun openSamsungBatterySettings() {
        try {
            val intent = Intent()
            intent.component = ComponentName(
                "com.samsung.android.lool",
                "com.samsung.android.sm.ui.battery.BatteryActivity",
            )
            startActivity(intent)
        } catch (e: Exception) {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            startActivity(intent)
        }
    }

    private fun handlePermissionsAndStart() {
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionsLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            startCompanionService()
        }
    }

    private fun startCompanionService() {
        try {
            val intent = Intent(this, CompanionForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            Toast.makeText(this, "Thraksha Guardian Started", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error starting service: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
