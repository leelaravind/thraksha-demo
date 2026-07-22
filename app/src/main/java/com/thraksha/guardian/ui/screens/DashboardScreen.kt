package com.thraksha.guardian.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thraksha.guardian.security.ThrakshaDeviceAdminReceiver
import com.thraksha.guardian.services.ThrakshaAccessibilityService
import com.thraksha.guardian.services.ThrakshaNotificationListener
import com.thraksha.guardian.services.ThrakshaVpnService
import com.thraksha.guardian.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private fun openSettingsPage(context: Context, action: String) {
    try {
        val intent = Intent(action).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Log.e("DashboardScreen", "Failed to open settings: ${e.message}")
        Toast.makeText(context, "Could not open Settings", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var vpnRunning by remember { mutableStateOf(false) }

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            ThrakshaVpnService.start(context)
            vpnRunning = true
            Toast.makeText(
                context,
                "VPN started — check notification",
                Toast.LENGTH_SHORT,
            ).show()
        } else {
            Toast.makeText(
                context,
                "VPN permission denied",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    var pulseScale by remember { mutableStateOf(1f) }

    val animatedPulse by animateFloatAsState(
        targetValue = pulseScale,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    LaunchedEffect(Unit) {
        while (true) {
            pulseScale = 1.05f
            delay(2000)
            pulseScale = 1f
            delay(2000)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkForestGreen)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Protection Status Badge
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(32.dp))
                .background(CardSurfaceDark)
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .scale(animatedPulse)
                        .clip(CircleShape)
                        .border(4.dp, GoldenYellow, CircleShape)
                        .background(GoldenYellow.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Setup incomplete",
                        tint = GoldenYellow,
                        modifier = Modifier.size(64.dp)
                    )
                }

                Text(
                    text = "SETUP INCOMPLETE",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = GoldenYellow,
                    letterSpacing = 2.sp
                )

                Text(
                    text = "Complete Day 4 setup to activate",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextSecondaryDark
                )
            }
        }

        // Security metrics unavailable until Day 6
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(CardSurfaceDark)
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Waiting for Security Guardian (Day 6)",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = TextSecondaryDark
            )
        }

        // Debug Controls Section
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(CardSurfaceDark)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "DEBUG CONTROLS",
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                color = GoldenYellow,
                letterSpacing = 1.5.sp
            )

            // === SERVICE TESTING SECTION ===
            Text(
                text = "SERVICE TESTS",
                style = MaterialTheme.typography.titleMedium,
                color = GoldenYellow,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
            )

            Button(
                onClick = {
                    if (ThrakshaAccessibilityService.isRunning()) {
                        scope.launch {
                            val result = ThrakshaAccessibilityService.instance?.performTap(
                                x = 500f,
                                y = 1000f,
                            )
                            val message = when (result) {
                                is ThrakshaAccessibilityService.GestureResult.Success ->
                                    "Tap executed successfully"
                                is ThrakshaAccessibilityService.GestureResult.Failure ->
                                    "Tap failed: ${result.reason}"
                                null -> "Accessibility service not ready"
                            }
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        openSettingsPage(
                            context,
                            android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS,
                        )
                        Toast.makeText(
                            context,
                            "Enable 'Thraksha UI Monitor' in Accessibility",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = StrongGreen),
            ) {
                Text("Test Accessibility (Tap Gesture)")
            }

            Button(
                onClick = {
                    if (!vpnRunning) {
                        val prepareIntent = ThrakshaVpnService.prepareIntent(context)
                        if (prepareIntent != null) {
                            vpnPermissionLauncher.launch(prepareIntent)
                        } else {
                            ThrakshaVpnService.start(context)
                            vpnRunning = true
                            Toast.makeText(
                                context,
                                "VPN started — check notification",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    } else {
                        ThrakshaVpnService.stop(context)
                        vpnRunning = false
                        Toast.makeText(
                            context,
                            "VPN stopped",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = CardSurfaceLight),
            ) {
                Text(
                    if (vpnRunning) "Stop VPN Monitor" else "Start VPN Monitor",
                    color = TextDarkGreen,
                )
            }

            Button(
                onClick = {
                    val isDeviceOwner = ThrakshaDeviceAdminReceiver.isDeviceOwner(context)
                    val dpm = ThrakshaDeviceAdminReceiver.getDevicePolicyManager(context)
                    val isAdminActive = dpm.isAdminActive(
                        ThrakshaDeviceAdminReceiver.getComponentName(context),
                    )
                    when {
                        isDeviceOwner -> {
                            Toast.makeText(
                                context,
                                "Device Owner (Level 4) - Lockdown ready",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                        isAdminActive -> {
                            Toast.makeText(
                                context,
                                "Device Admin (Level 3) - Active",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                        else -> {
                            openSettingsPage(
                                context,
                                android.provider.Settings.ACTION_SECURITY_SETTINGS,
                            )
                            Toast.makeText(
                                context,
                                "Go to Device admin apps → Enable Thraksha",
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = CardSurfaceLight),
            ) {
                Text("Check Device Admin Status", color = TextDarkGreen)
            }

            Button(
                onClick = {
                    if (ThrakshaNotificationListener.isRunning()) {
                        Toast.makeText(
                            context,
                            "Notification Listener active",
                            Toast.LENGTH_SHORT,
                        ).show()
                    } else {
                        openSettingsPage(
                            context,
                            android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS,
                        )
                        Toast.makeText(
                            context,
                            "Enable 'Thraksha Notification Monitor'",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = CardSurfaceLight),
            ) {
                Text("Check Notification Listener", color = TextDarkGreen)
            }

            Button(
                onClick = {
                    scope.launch {
                        val accessibility = ThrakshaAccessibilityService.isRunning()
                        val notification = ThrakshaNotificationListener.isRunning()
                        val deviceAdmin = ThrakshaDeviceAdminReceiver.isDeviceOwner(context) ||
                            ThrakshaDeviceAdminReceiver.getDevicePolicyManager(context)
                                .isAdminActive(
                                    ThrakshaDeviceAdminReceiver.getComponentName(context),
                                )
                        val status = """
                            Accessibility: ${if (accessibility) "OK" else "OFF"}
                            Notification: ${if (notification) "OK" else "OFF"}
                            Device Admin: ${if (deviceAdmin) "OK" else "OFF"}
                            VPN: ${if (vpnRunning) "OK" else "OFF"}
                        """.trimIndent()
                        Toast.makeText(context, status, Toast.LENGTH_LONG).show()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                ),
            ) {
                Text("Check All Services Status")
            }
        }

        // Activity Feed
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "RECENT ACTIVITY",
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                color = TextSecondaryDark,
                letterSpacing = 1.5.sp
            )

            ActivityItem(
                title = "Accessibility Service - Not Configured",
                time = "Setup required",
                icon = Icons.Default.Warning,
                iconColor = GoldenYellow
            )
        }
    }
}

@Composable
fun ActivityItem(
    title: String,
    time: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardSurfaceDark)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(iconColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(20.dp)
            )
        }

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextWhite
            )

            Text(
                text = time,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = TextSecondaryDark
            )
        }
    }
}
