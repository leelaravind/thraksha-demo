package com.thraksha.guardian.ui.screens.settings

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thraksha.guardian.security.PrivilegeLevel
import com.thraksha.guardian.security.SecurityCapability
import com.thraksha.guardian.security.network.NetworkGuard
import com.thraksha.guardian.services.ThrakshaAccessibilityService
import com.thraksha.guardian.services.ThrakshaNotificationListener
import com.thraksha.guardian.ui.design.StatusTone
import com.thraksha.guardian.ui.design.ThrakshaCard
import com.thraksha.guardian.ui.design.ThrakshaFactRow
import com.thraksha.guardian.ui.design.ThrakshaFootnote
import com.thraksha.guardian.ui.design.ThrakshaPageColumn
import com.thraksha.guardian.ui.design.ThrakshaScaffold
import com.thraksha.guardian.ui.design.ThrakshaSecondaryButton
import com.thraksha.guardian.ui.design.ThrakshaSectionHeader
import com.thraksha.guardian.ui.design.ThrakshaSpacing
import kotlinx.coroutines.launch

/**
 * DEVELOPER DIAGNOSTICS.
 *
 * Service reachability checks carried over from the previous dashboard. None of these
 * produce a security finding or an audit entry — they exist so an operator can confirm
 * which optional services Android currently has running.
 *
 * The accessibility check is a **developer probe only**. Automation does not use the
 * Accessibility Service, which is why it appears here and not in Permissions & Access.
 */
@Composable
fun DiagnosticsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val guard by NetworkGuard.state.collectAsStateWithLifecycle()

    ThrakshaScaffold(title = "Developer diagnostics", onBack = onBack) { padding ->
        ThrakshaPageColumn(padding = padding) {
            ThrakshaCard(ribbon = StatusTone.NEUTRAL) {
                Text(
                    text = "Service reachability checks. These do not scan anything and " +
                        "never produce a security finding.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column {
                ThrakshaSectionHeader("Current service state")
                ThrakshaCard {
                    ThrakshaFactRow(
                        "Notification listener",
                        if (ThrakshaNotificationListener.isRunning()) "running" else "not running",
                        valueTone = if (ThrakshaNotificationListener.isRunning()) {
                            StatusTone.OK
                        } else {
                            StatusTone.NEUTRAL
                        },
                    )
                    ThrakshaFactRow(
                        "Accessibility probe service",
                        if (ThrakshaAccessibilityService.isRunning()) "running" else "not running",
                        valueTone = StatusTone.NEUTRAL,
                    )
                    ThrakshaFactRow(
                        "Device privilege",
                        runCatching { SecurityCapability.currentLevel(context) }
                            .getOrDefault(PrivilegeLevel.NORMAL).name,
                    )
                    ThrakshaFactRow(
                        "Network Guard",
                        when (guard) {
                            is NetworkGuard.State.Active -> "active"
                            is NetworkGuard.State.Starting -> "starting"
                            is NetworkGuard.State.ConsentRequired -> "consent required"
                            is NetworkGuard.State.Error -> "error"
                            is NetworkGuard.State.Disabled -> "off"
                        },
                    )
                }
            }

            Column {
                ThrakshaSectionHeader("Probes")
                Column(verticalArrangement = Arrangement.spacedBy(ThrakshaSpacing.sm)) {
                    ThrakshaSecondaryButton(
                        text = "Test accessibility gesture",
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            if (ThrakshaAccessibilityService.isRunning()) {
                                scope.launch {
                                    val outcome = ThrakshaAccessibilityService.instance
                                        ?.performTap(x = 500f, y = 1000f)
                                    val message = when (outcome) {
                                        is ThrakshaAccessibilityService.GestureResult.Success ->
                                            "Tap executed"
                                        is ThrakshaAccessibilityService.GestureResult.Failure ->
                                            "Tap failed: ${outcome.reason}"
                                        null -> "Service not ready"
                                    }
                                    context.toast(message)
                                }
                            } else {
                                context.openSetting(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            }
                        },
                    )
                    ThrakshaSecondaryButton(
                        text = "Open notification listener settings",
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            context.openSetting(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                        },
                    )
                    ThrakshaSecondaryButton(
                        text = "Open device admin settings",
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { context.openSetting(Settings.ACTION_SECURITY_SETTINGS) },
                    )
                }
            }

            ThrakshaFootnote(
                "Network monitoring is configured on the Network Guard screen, not here.",
            )
        }
    }
}

private fun Context.openSetting(action: String) {
    runCatching {
        startActivity(Intent(action).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
    }.onFailure { toast("Could not open that settings screen") }
}

private fun Context.toast(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}
