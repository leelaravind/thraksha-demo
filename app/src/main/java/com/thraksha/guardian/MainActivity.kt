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
import com.thraksha.guardian.services.CompanionForegroundService
import com.thraksha.guardian.ui.components.StatusBarComponent
import com.thraksha.guardian.ui.components.WhitelistingDialog
import com.thraksha.guardian.ui.screens.DashboardScreen
import com.thraksha.guardian.ui.screens.SplashScreen
import com.thraksha.guardian.ui.theme.ThrakshaTheme

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

    @Composable
    private fun MainContent() {
        LaunchedEffect(Unit) {
            checkSamsungAndStart()
        }

        Scaffold(
            topBar = { StatusBarComponent() },
        ) { paddingValues ->
            Box(modifier = Modifier.padding(paddingValues)) {
                DashboardScreen()

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
