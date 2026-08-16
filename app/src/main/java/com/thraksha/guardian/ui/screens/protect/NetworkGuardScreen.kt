package com.thraksha.guardian.ui.screens.protect

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thraksha.guardian.security.network.NetworkGuard
import com.thraksha.guardian.services.ThrakshaVpnService
import com.thraksha.guardian.ui.design.StatusTone
import com.thraksha.guardian.ui.design.ThrakshaCard
import com.thraksha.guardian.ui.design.ThrakshaDisclosure
import com.thraksha.guardian.ui.design.ThrakshaFactRow
import com.thraksha.guardian.ui.design.ThrakshaFootnote
import com.thraksha.guardian.ui.design.ThrakshaPageColumn
import com.thraksha.guardian.ui.design.ThrakshaPanel
import com.thraksha.guardian.ui.design.ThrakshaPrimaryButton
import com.thraksha.guardian.ui.design.ThrakshaScaffold
import com.thraksha.guardian.ui.design.ThrakshaSecondaryButton
import com.thraksha.guardian.ui.design.ThrakshaSectionHeader
import com.thraksha.guardian.ui.design.ThrakshaSpacing
import com.thraksha.guardian.ui.design.ThrakshaStatusChip

/**
 * NETWORK GUARD — the scoped outbound monitor.
 *
 * The toggle runs the genuine Android VPN consent flow; there is no local "enabled"
 * boolean anywhere. Counters and the last decision come from the service's own state, so
 * the screen cannot show activity the guard did not actually see.
 */
@Composable
fun NetworkGuardScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val state by NetworkGuard.state.collectAsStateWithLifecycle()
    val summary = ProtectPresentation.networkGuard(state)

    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            NetworkGuard.transition(NetworkGuard.State.Starting)
            ThrakshaVpnService.start(context)
        } else {
            NetworkGuard.transition(NetworkGuard.State.ConsentRequired)
        }
    }

    fun enable() {
        val consent = ThrakshaVpnService.prepareIntent(context)
        if (consent != null) {
            NetworkGuard.transition(NetworkGuard.State.ConsentRequired)
            consentLauncher.launch(consent)
        } else {
            NetworkGuard.transition(NetworkGuard.State.Starting)
            ThrakshaVpnService.start(context)
        }
    }

    val active = state as? NetworkGuard.State.Active
    val running = active != null || state is NetworkGuard.State.Starting

    ThrakshaScaffold(title = "Network Guard", onBack = onBack) { padding ->
        ThrakshaPageColumn(padding = padding) {
            ThrakshaCard(ribbon = summary.tone) {
                ThrakshaStatusChip(summary.status, summary.tone)
                Spacer(Modifier.height(ThrakshaSpacing.md))
                Text(
                    text = summary.supporting,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(ThrakshaSpacing.lg))
                if (running) {
                    ThrakshaSecondaryButton(
                        text = "Turn off",
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { ThrakshaVpnService.stop(context) },
                    )
                } else {
                    ThrakshaPrimaryButton(
                        text = "Turn on",
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { enable() },
                    )
                }
            }

            if (active != null) {
                Column {
                    ThrakshaSectionHeader("Activity")
                    ThrakshaCard {
                        ThrakshaFactRow("Watching", active.scopedPackage)
                        ThrakshaFactRow("Connections seen", active.packetsObserved.toString())
                        ThrakshaFactRow("Allowed through", active.packetsForwarded.toString())
                        ThrakshaFactRow(
                            "Blocked",
                            active.packetsBlocked.toString(),
                            valueTone = if (active.packetsBlocked > 0) StatusTone.OK else null,
                        )
                        ThrakshaDisclosure("Technical details") {
                            ThrakshaFactRow("Tunnel address", active.tunnelAddress)
                            ThrakshaFactRow("Scope", "single package only")
                        }
                    }
                }

                active.lastOutcome?.let { outcome ->
                    val blocked = outcome.outcome == "BLOCKED"
                    Column {
                        ThrakshaSectionHeader("Most recent connection")
                        ThrakshaCard(
                            ribbon = if (blocked) StatusTone.OK else StatusTone.WARN,
                        ) {
                            ThrakshaStatusChip(
                                if (blocked) "Blocked" else outcome.outcome.lowercase()
                                    .replaceFirstChar { it.uppercase() },
                                if (blocked) StatusTone.OK else StatusTone.WARN,
                            )
                            Spacer(Modifier.height(ThrakshaSpacing.md))
                            Text(
                                text = "${outcome.observation.protocolName} to " +
                                    outcome.observation.endpoint(),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(ThrakshaSpacing.xs))
                            Text(
                                text = outcome.detail,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            outcome.indicatorId?.let { id ->
                                Spacer(Modifier.height(ThrakshaSpacing.md))
                                ThrakshaPanel {
                                    ThrakshaFactRow("Matched indicator", id)
                                    outcome.decisionType?.let {
                                        ThrakshaFactRow("Policy decision", it)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            ThrakshaFootnote(
                "Network Guard routes one app's outbound traffic through a local tunnel on " +
                    "this phone. Thraksha reads where a connection is going — never what is " +
                    "inside it — and every decision is recorded in the audit trail.",
            )
        }
    }
}
