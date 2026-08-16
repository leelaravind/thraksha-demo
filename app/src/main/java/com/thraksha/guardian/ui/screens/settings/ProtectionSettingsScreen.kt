package com.thraksha.guardian.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thraksha.guardian.data.config.ConfigStore
import com.thraksha.guardian.data.config.ExecutionMode
import com.thraksha.guardian.security.DemoMode
import com.thraksha.guardian.security.FoundationStatus
import com.thraksha.guardian.security.PrivilegeLevel
import com.thraksha.guardian.security.events.SecurityEvent
import com.thraksha.guardian.security.events.SecurityEventBus
import com.thraksha.guardian.security.network.NetworkGuard
import com.thraksha.guardian.ui.design.StatusTone
import com.thraksha.guardian.ui.design.ThrakshaCard
import com.thraksha.guardian.ui.design.ThrakshaDisclosure
import com.thraksha.guardian.ui.design.ThrakshaDivider
import com.thraksha.guardian.ui.design.ThrakshaFactRow
import com.thraksha.guardian.ui.design.ThrakshaFootnote
import com.thraksha.guardian.ui.design.ThrakshaNavRow
import com.thraksha.guardian.ui.design.ThrakshaPageColumn
import com.thraksha.guardian.ui.design.ThrakshaScaffold
import com.thraksha.guardian.ui.design.ThrakshaSecondaryButton
import com.thraksha.guardian.ui.design.ThrakshaSectionHeader
import com.thraksha.guardian.ui.design.ThrakshaSpacing
import com.thraksha.guardian.ui.design.ThrakshaStatusChip
import com.thraksha.guardian.ui.screens.protect.ProtectPresentation
import com.thraksha.guardian.ui.state.rememberPrivilegeLevel
import kotlinx.coroutines.launch

/**
 * PROTECTION — authority, the operator policy dial, and where the deeper checks live.
 *
 * Authority (what Android permits) and execution mode (how far policy may go with it) are
 * deliberately two separate axes here, exactly as they are in the engine. Changing the
 * mode grants no capability; it is recorded in the audit chain either way.
 */
@Composable
fun ProtectionSettingsScreen(
    onBack: () -> Unit,
    onOpenPolicyCheck: () -> Unit,
    onOpenNetworkGuard: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val privilege by rememberPrivilegeLevel()
    val foundation by FoundationStatus.state.collectAsStateWithLifecycle()
    val guard by NetworkGuard.state.collectAsStateWithLifecycle()
    val demoMode = DemoMode.from(privilege)

    var mode by remember { mutableStateOf<ExecutionMode?>(null) }
    LaunchedEffect(Unit) {
        mode = runCatching { ConfigStore(context).getExecutionMode() }.getOrNull()
    }

    ThrakshaScaffold(title = "Protection", onBack = onBack) { padding ->
        ThrakshaPageColumn(padding = padding) {
            // ---- Foundation --------------------------------------------------
            ThrakshaCard(
                ribbon = when (foundation) {
                    is FoundationStatus.State.Ready -> StatusTone.OK
                    is FoundationStatus.State.Failed -> StatusTone.DANGER
                    else -> StatusTone.NEUTRAL
                },
            ) {
                ThrakshaStatusChip(
                    when (foundation) {
                        is FoundationStatus.State.Ready -> "Running"
                        is FoundationStatus.State.Failed -> "Not running"
                        else -> "Starting"
                    },
                    when (foundation) {
                        is FoundationStatus.State.Ready -> StatusTone.OK
                        is FoundationStatus.State.Failed -> StatusTone.DANGER
                        else -> StatusTone.NEUTRAL
                    },
                )
                Spacer(Modifier.height(ThrakshaSpacing.md))
                Text(
                    text = when (val f = foundation) {
                        is FoundationStatus.State.Ready ->
                            "Encrypted storage is open and every event is being recorded."
                        is FoundationStatus.State.Failed -> f.message
                        else -> "Opening encrypted storage…"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                (foundation as? FoundationStatus.State.Failed)?.cause?.let { cause ->
                    Spacer(Modifier.height(ThrakshaSpacing.sm))
                    ThrakshaDisclosure("Technical details") {
                        ThrakshaFactRow("Cause", cause)
                    }
                }
            }

            // ---- Authority ---------------------------------------------------
            Column {
                ThrakshaSectionHeader("What Thraksha is allowed to do")
                ThrakshaCard(
                    ribbon = if (privilege == PrivilegeLevel.DEVICE_OWNER) {
                        StatusTone.OK
                    } else {
                        StatusTone.NEUTRAL
                    },
                ) {
                    ThrakshaStatusChip(
                        if (privilege == PrivilegeLevel.DEVICE_OWNER) "Can act" else "Advice only",
                        if (privilege == PrivilegeLevel.DEVICE_OWNER) {
                            StatusTone.OK
                        } else {
                            StatusTone.NEUTRAL
                        },
                    )
                    Spacer(Modifier.height(ThrakshaSpacing.md))
                    Text(
                        text = demoMode.explanation,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(ThrakshaSpacing.sm))
                    ThrakshaDisclosure("Technical details") {
                        ThrakshaFactRow("Android privilege level", privilege.name)
                        ThrakshaFactRow("Authority mode", demoMode.label)
                        ThrakshaFactRow(
                            "Enforcement",
                            if (privilege == PrivilegeLevel.DEVICE_OWNER) {
                                "Available, gated by execution mode"
                            } else {
                                "Advisory only"
                            },
                        )
                    }
                }
            }

            // ---- Execution mode ----------------------------------------------
            Column {
                ThrakshaSectionHeader("How far Thraksha may go")
                ThrakshaCard {
                    Text(
                        text = "This setting limits what the policy layer may do with " +
                            "whatever authority it has. It never grants authority by itself.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(ThrakshaSpacing.md))
                    ExecutionModes.forEach { (value, description) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = mode == value,
                                    role = Role.RadioButton,
                                    onClick = {
                                        scope.launch {
                                            runCatching {
                                                ConfigStore(context).setExecutionMode(value)
                                                SecurityEventBus.emit(
                                                    SecurityEvent.ModeChanged(value.name),
                                                )
                                            }
                                            mode = value
                                        }
                                    },
                                )
                                .heightIn(min = ThrakshaSpacing.touchTarget)
                                .padding(vertical = ThrakshaSpacing.sm),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(ThrakshaSpacing.md),
                        ) {
                            RadioButton(selected = mode == value, onClick = null)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = value.friendly(),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(ThrakshaSpacing.sm))
                    Text(
                        text = "Every change is recorded in the audit trail.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // ---- Related surfaces --------------------------------------------
            Column {
                ThrakshaSectionHeader("Checks")
                ThrakshaCard(
                    contentPadding = PaddingValues(0.dp),
                ) {
                    ThrakshaNavRow(
                        label = "Network Guard",
                        supporting = ProtectPresentation.networkGuard(guard).supporting,
                        onClick = onOpenNetworkGuard,
                    )
                    ThrakshaDivider()
                    ThrakshaNavRow(
                        label = "Policy & enforcement check",
                        supporting = "Runs the signed profile rules over the registered " +
                            "demo apps",
                        onClick = onOpenPolicyCheck,
                    )
                }
            }

            ThrakshaFootnote(
                "Scanning, Network Guard and routines are independent. Turning one off " +
                    "never silently changes another.",
            )
        }
    }
}

private val ExecutionModes = listOf(
    ExecutionMode.OBSERVE to "Detect and record only. Never acts.",
    ExecutionMode.GUIDED to "Detect, then ask you before acting.",
    ExecutionMode.AUTO_DEFEND to "Detect and act automatically, up to the configured tier.",
)

private fun ExecutionMode.friendly(): String = when (this) {
    ExecutionMode.OBSERVE -> "Observe"
    ExecutionMode.GUIDED -> "Ask me first"
    ExecutionMode.AUTO_DEFEND -> "Act automatically"
    ExecutionMode.LOCKDOWN -> "Lockdown"
}
