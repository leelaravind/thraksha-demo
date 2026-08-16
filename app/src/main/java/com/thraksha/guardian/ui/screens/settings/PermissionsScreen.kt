package com.thraksha.guardian.ui.screens.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.thraksha.guardian.automation.ActionSupport
import com.thraksha.guardian.automation.AutomationCapability
import com.thraksha.guardian.automation.AutomationEngine
import com.thraksha.guardian.security.PrivilegeLevel
import com.thraksha.guardian.security.SecurityCapability
import com.thraksha.guardian.services.ThrakshaNotificationListener
import com.thraksha.guardian.services.ThrakshaVpnService
import com.thraksha.guardian.ui.design.StatusTone
import com.thraksha.guardian.ui.design.ThrakshaCard
import com.thraksha.guardian.ui.design.ThrakshaFootnote
import com.thraksha.guardian.ui.design.ThrakshaPageColumn
import com.thraksha.guardian.ui.design.ThrakshaScaffold
import com.thraksha.guardian.ui.design.ThrakshaSecondaryButton
import com.thraksha.guardian.ui.design.ThrakshaSpacing
import com.thraksha.guardian.ui.design.ThrakshaStatusChip

/**
 * PERMISSIONS & ACCESS — the accesses Thraksha genuinely uses.
 *
 * There is **no Accessibility Service entry**. The Stitch designs asked for one and
 * described it as the mechanism behind automation; the automation engine does not use
 * Accessibility at all — routines work through Notification Policy access and Modify
 * System Settings. Inventing that row would have been a fake permission request.
 *
 * Every state is read live from Android on each resume, so returning from a system
 * settings screen shows the real result rather than an optimistic assumption.
 */
@Composable
fun PermissionsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val refresh by rememberResumeTicker()

    val accesses = remember(refresh) { buildAccessList(context) }

    ThrakshaScaffold(title = "Permissions & access", onBack = onBack) { padding ->
        ThrakshaPageColumn(padding = padding) {
            Text(
                text = "Thraksha asks for as little as it can. Each item below says why it " +
                    "is needed and what happens without it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Column(verticalArrangement = Arrangement.spacedBy(ThrakshaSpacing.cardGap)) {
                accesses.forEach { access -> AccessCard(access) }
            }

            ThrakshaFootnote(
                "Thraksha does not use Android's Accessibility Service, and does not ask " +
                    "for location, contacts, camera, microphone or storage.",
            )
        }
    }
}

/** One real access requirement, already resolved against the device. */
private data class Access(
    val title: String,
    val why: String,
    val state: AccessState,
    val actionLabel: String?,
    val onAction: () -> Unit,
)

private enum class AccessState(val label: String, val tone: StatusTone) {
    GRANTED("Granted", StatusTone.OK),
    NOT_GRANTED("Not granted", StatusTone.WARN),
    OPTIONAL("Optional", StatusTone.NEUTRAL),
    UNAVAILABLE("Not available", StatusTone.NEUTRAL),
}

@Composable
private fun AccessCard(access: Access) {
    ThrakshaCard(ribbon = access.state.tone) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ThrakshaSpacing.md),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = access.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            ThrakshaStatusChip(access.state.label, access.state.tone)
        }
        Spacer(Modifier.height(ThrakshaSpacing.xs))
        Text(
            text = access.why,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (access.actionLabel != null) {
            Spacer(Modifier.height(ThrakshaSpacing.md))
            ThrakshaSecondaryButton(
                text = access.actionLabel,
                modifier = Modifier.fillMaxWidth(),
                onClick = access.onAction,
            )
        }
    }
}

/**
 * Builds the list from live Android state.
 *
 * Each entry corresponds to a capability the app actually exercises. Where Android offers
 * no in-app grant path, the action deep-links to the exact system screen rather than
 * pretending the app can toggle it.
 */
private fun buildAccessList(context: Context): List<Access> {
    val probe = AutomationEngine.probe(context)
    val dnd = probe.support(AutomationCapability.DO_NOT_DISTURB).first == ActionSupport.SUPPORTED
    val write = probe.support(AutomationCapability.SCREEN_BRIGHTNESS).first == ActionSupport.SUPPORTED
    val listener = ThrakshaNotificationListener.isRunning()
    val privilege = runCatching { SecurityCapability.currentLevel(context) }
        .getOrDefault(PrivilegeLevel.NORMAL)
    val vpnReady = runCatching { ThrakshaVpnService.prepareIntent(context) == null }
        .getOrDefault(false)
    val postNotifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    } else {
        true
    }

    return listOf(
        Access(
            title = "Do Not Disturb",
            why = "Lets a routine turn Do Not Disturb on and put your previous setting " +
                "back afterwards. Without it, Meeting and Focus can still run, but the " +
                "quiet step is left for you.",
            state = if (dnd) AccessState.GRANTED else AccessState.NOT_GRANTED,
            actionLabel = if (dnd) "Change in Android settings" else "Allow",
            onAction = {
                context.openSettings(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            },
        ),
        Access(
            title = "Modify system settings",
            why = "Lets a routine adjust screen brightness and screen timeout, and restore " +
                "the exact previous values when it ends.",
            state = if (write) AccessState.GRANTED else AccessState.NOT_GRANTED,
            actionLabel = if (write) "Change in Android settings" else "Allow",
            onAction = {
                context.openSettings(
                    Settings.ACTION_MANAGE_WRITE_SETTINGS,
                    Uri.parse("package:${context.packageName}"),
                )
            },
        ),
        Access(
            title = "Notifications",
            why = "Lets Thraksha tell you when a scan finds something that needs you, and " +
                "keeps its background service visible while it is running.",
            state = if (postNotifications) AccessState.GRANTED else AccessState.NOT_GRANTED,
            actionLabel = "Open notification settings",
            onAction = {
                context.openSettings(
                    Settings.ACTION_APP_NOTIFICATION_SETTINGS,
                    extras = mapOf(Settings.EXTRA_APP_PACKAGE to context.packageName),
                )
            },
        ),
        Access(
            title = "Notification access",
            why = "Optional. When enabled, Thraksha can observe that a notification was " +
                "delivered, which is one of the few things it can verify actually happened.",
            state = if (listener) AccessState.GRANTED else AccessState.OPTIONAL,
            actionLabel = if (listener) "Change in Android settings" else "Enable",
            onAction = {
                context.openSettings(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            },
        ),
        Access(
            title = "Local network monitoring",
            why = "Network Guard routes one app's outbound traffic through a local tunnel " +
                "on this phone. Android asks for your consent the first time it starts.",
            state = if (vpnReady) AccessState.GRANTED else AccessState.NOT_GRANTED,
            actionLabel = null,
            onAction = {},
        ),
        Access(
            title = "Device administration",
            why = when (privilege) {
                PrivilegeLevel.DEVICE_OWNER ->
                    "Thraksha is the device owner, so it can act directly on a flagged app " +
                        "when you tell it to."
                PrivilegeLevel.DEVICE_ADMIN ->
                    "Thraksha is a device admin. That is not enough to act on other apps — " +
                        "it can detect and advise only."
                PrivilegeLevel.NORMAL ->
                    "Without it, Thraksha detects and advises but cannot act on another " +
                        "app for you. This is the normal state for an installed app."
            },
            state = when (privilege) {
                PrivilegeLevel.DEVICE_OWNER -> AccessState.GRANTED
                PrivilegeLevel.DEVICE_ADMIN, PrivilegeLevel.NORMAL -> AccessState.OPTIONAL
            },
            actionLabel = if (privilege == PrivilegeLevel.NORMAL) {
                "Open security settings"
            } else {
                null
            },
            onAction = { context.openSettings(Settings.ACTION_SECURITY_SETTINGS) },
        ),
    )
}

private fun Context.openSettings(
    action: String,
    data: Uri? = null,
    extras: Map<String, String> = emptyMap(),
) {
    runCatching {
        val intent = Intent(action).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            if (data != null) this.data = data
            extras.forEach { (key, value) -> putExtra(key, value) }
        }
        startActivity(intent)
    }.onFailure {
        Toast.makeText(this, "Could not open that settings screen", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Increments on every ON_RESUME. Permission state can only change outside the app, so a
 * resume is exactly when it must be re-read.
 */
@Composable
private fun rememberResumeTicker(): State<Int> {
    val lifecycleOwner = LocalLifecycleOwner.current
    val ticker = remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) ticker.intValue++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return ticker
}
