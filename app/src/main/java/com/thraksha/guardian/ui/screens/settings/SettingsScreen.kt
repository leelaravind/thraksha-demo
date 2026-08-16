package com.thraksha.guardian.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thraksha.guardian.ai.LocalModelRepository
import com.thraksha.guardian.ai.ModelPhase
import com.thraksha.guardian.security.DemoMode
import com.thraksha.guardian.ui.design.StatusTone
import com.thraksha.guardian.ui.design.ThrakshaCard
import com.thraksha.guardian.ui.design.ThrakshaDivider
import com.thraksha.guardian.ui.design.ThrakshaFootnote
import com.thraksha.guardian.ui.design.ThrakshaNavRow
import com.thraksha.guardian.ui.design.ThrakshaPageColumn
import com.thraksha.guardian.ui.design.ThrakshaScaffold
import com.thraksha.guardian.ui.design.ThrakshaSectionHeader
import com.thraksha.guardian.ui.design.ThemePreference
import com.thraksha.guardian.ui.nav.Route
import com.thraksha.guardian.ui.state.rememberPrivilegeLevel

/**
 * SETTINGS — grouped exactly as Guardian Prime specifies, minus everything the app does
 * not actually have.
 *
 * There is no account, no profile and no sign-out: Thraksha is entirely local, so those
 * rows would be fiction. Each row that carries a status reads it from the real subsystem.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpen: (Route) -> Unit,
) {
    val theme by ThemePreference.current.collectAsStateWithLifecycle()
    val model by LocalModelRepository.state.collectAsStateWithLifecycle()
    val privilege by rememberPrivilegeLevel()
    val demoMode = DemoMode.from(privilege)

    ThrakshaScaffold(title = "Settings", onBack = onBack) { padding ->
        ThrakshaPageColumn(padding = padding) {
            Column {
                ThrakshaSectionHeader("General")
                ThrakshaCard(contentPadding = PaddingValues(0.dp)) {
                    ThrakshaNavRow(
                        label = "Appearance",
                        icon = Icons.Default.Palette,
                        trailing = theme.label,
                        onClick = { onOpen(Route.Appearance) },
                    )
                }
            }

            Column {
                ThrakshaSectionHeader("Protection")
                ThrakshaCard(contentPadding = PaddingValues(0.dp)) {
                    ThrakshaNavRow(
                        label = "Protection",
                        supporting = demoMode.label.lowercase().replaceFirstChar { it.uppercase() },
                        icon = Icons.Default.Shield,
                        onClick = { onOpen(Route.Protection) },
                    )
                    ThrakshaDivider()
                    ThrakshaNavRow(
                        label = "On-device AI",
                        icon = Icons.Default.Memory,
                        trailing = when (model.phase) {
                            ModelPhase.LOADED -> "Ready"
                            ModelPhase.READY -> "Available"
                            ModelPhase.LOADING, ModelPhase.VERIFYING -> "Checking"
                            ModelPhase.MISSING -> "Not installed"
                            ModelPhase.UNSUPPORTED -> "Unsupported"
                            ModelPhase.ERROR -> "Unavailable"
                        },
                        trailingTone = when (model.phase) {
                            ModelPhase.LOADED, ModelPhase.READY -> StatusTone.OK
                            ModelPhase.LOADING, ModelPhase.VERIFYING -> StatusTone.WARN
                            else -> StatusTone.NEUTRAL
                        },
                        onClick = { onOpen(Route.OnDeviceAi) },
                    )
                }
            }

            Column {
                ThrakshaSectionHeader("Access & data")
                ThrakshaCard(contentPadding = PaddingValues(0.dp)) {
                    ThrakshaNavRow(
                        label = "Permissions & access",
                        icon = Icons.Default.VpnKey,
                        onClick = { onOpen(Route.Permissions) },
                    )
                    ThrakshaDivider()
                    ThrakshaNavRow(
                        label = "Privacy & data",
                        icon = Icons.Default.Lock,
                        onClick = { onOpen(Route.PrivacyData) },
                    )
                }
            }

            Column {
                ThrakshaSectionHeader("System")
                ThrakshaCard(contentPadding = PaddingValues(0.dp)) {
                    ThrakshaNavRow(
                        label = "About Thraksha",
                        icon = Icons.Default.Info,
                        onClick = { onOpen(Route.About) },
                    )
                    ThrakshaDivider()
                    ThrakshaNavRow(
                        label = "Developer diagnostics",
                        supporting = "Service checks — produces no security findings",
                        icon = Icons.Default.BugReport,
                        onClick = { onOpen(Route.Diagnostics) },
                    )
                }
            }

            ThrakshaFootnote(
                "Thraksha has no account and no cloud service, so there is nothing to sign " +
                    "in or out of. Everything it stores stays on this phone.",
            )
        }
    }
}
