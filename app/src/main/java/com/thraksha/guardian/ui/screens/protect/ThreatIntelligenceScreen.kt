package com.thraksha.guardian.ui.screens.protect

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thraksha.guardian.security.engine.AuditResult
import com.thraksha.guardian.security.engine.SecurityAuditEngine
import com.thraksha.guardian.security.scan.DeviceScanEngine
import com.thraksha.guardian.security.scan.IntelligenceState
import com.thraksha.guardian.ui.design.StatusTone
import com.thraksha.guardian.ui.design.ThrakshaCard
import com.thraksha.guardian.ui.design.ThrakshaDisclosure
import com.thraksha.guardian.ui.design.ThrakshaEmptyState
import com.thraksha.guardian.ui.design.ThrakshaFactRow
import com.thraksha.guardian.ui.design.ThrakshaFootnote
import com.thraksha.guardian.ui.design.ThrakshaPageColumn
import com.thraksha.guardian.ui.design.ThrakshaScaffold
import com.thraksha.guardian.ui.design.ThrakshaSectionHeader
import com.thraksha.guardian.ui.design.ThrakshaSpacing
import com.thraksha.guardian.ui.design.ThrakshaStatusChip

/**
 * THREAT INTELLIGENCE — the real state of the signed offline packs.
 *
 * Two independent packs are surfaced: the threat pack (exact package / signing certificate
 * / APK digest indicators) whose state comes from the last device scan, and the rulepack
 * (contextual profile rules) whose state comes from the last security check. Neither is
 * ever summarised as "protected"; an unverifiable pack reads as unavailable, which is the
 * whole point of showing it.
 */
@Composable
fun ThreatIntelligenceScreen(onBack: () -> Unit) {
    val result by DeviceScanEngine.lastResult.collectAsStateWithLifecycle()
    val audit by SecurityAuditEngine.state.collectAsStateWithLifecycle()

    ThrakshaScaffold(title = "Threat Intelligence", onBack = onBack) { padding ->
        val intel = result?.intelligence
        ThrakshaPageColumn(padding = padding) {
            if (intel == null) {
                ThrakshaEmptyState(
                    title = "Not checked yet",
                    message = "Run a scan from Protect. Thraksha verifies the signed threat " +
                        "pack at scan time and reports exactly what it found.",
                    icon = Icons.Default.Language,
                )
            } else {
                val summary = ProtectPresentation.threatIntelligence(result)
                ThrakshaCard(ribbon = summary.tone) {
                    ThrakshaStatusChip(summary.status, summary.tone)
                    Spacer(Modifier.height(ThrakshaSpacing.md))
                    Text(
                        text = when (intel.status) {
                            IntelligenceState.Status.ACTIVE ->
                                "Known-threat matching ran during the last scan."
                            IntelligenceState.Status.STALE ->
                                "The pack is past its own expiry date, so matching was " +
                                    "switched off. Scan results are treated as partial."
                            IntelligenceState.Status.UNAVAILABLE ->
                                "The pack could not be verified, so no known-threat " +
                                    "matching ran. Results are not clean — they are unchecked."
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(ThrakshaSpacing.md))
                    ThrakshaDisclosure("Pack details") {
                        ThrakshaFactRow("Pack version", intel.packVersion?.toString() ?: "unavailable")
                        ThrakshaFactRow("Active indicators", intel.activeIndicators.toString())
                        ThrakshaFactRow(
                            "Expired indicators",
                            intel.expiredIndicators.toString(),
                            valueTone = if (intel.expiredIndicators > 0) StatusTone.WARN else null,
                        )
                        ThrakshaFactRow("Status detail", intel.detail)
                        ThrakshaFactRow(
                            "Matches in last scan",
                            (result?.knownThreatApps?.size ?: 0).toString(),
                            valueTone = if ((result?.knownThreatApps?.size ?: 0) > 0) {
                                StatusTone.DANGER
                            } else {
                                null
                            },
                        )
                    }
                }
            }

            // ---- Rulepack ----------------------------------------------------
            Column {
                ThrakshaSectionHeader("Profile rules")
                when (val a = audit) {
                    is AuditResult.Completed -> ThrakshaCard(ribbon = StatusTone.OK) {
                        ThrakshaStatusChip("Verified", StatusTone.OK)
                        Spacer(Modifier.height(ThrakshaSpacing.md))
                        Text(
                            text = "The signed rule set was verified and applied during the " +
                                "last security check.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(ThrakshaSpacing.md))
                        ThrakshaDisclosure("Rule details") {
                            ThrakshaFactRow("Rulepack version", a.rulepackVersion.toString())
                            ThrakshaFactRow("Rules evaluated", a.rulesEvaluated.toString())
                            ThrakshaFactRow("Apps audited", a.appsScanned.toString())
                            ThrakshaFactRow("Findings", a.threatsFound.toString())
                        }
                    }

                    is AuditResult.Failed -> ThrakshaCard(ribbon = StatusTone.DANGER) {
                        ThrakshaStatusChip("Unavailable", StatusTone.DANGER)
                        Spacer(Modifier.height(ThrakshaSpacing.md))
                        Text(
                            text = a.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    is AuditResult.NotRun -> ThrakshaCard {
                        ThrakshaStatusChip("Not run", StatusTone.NEUTRAL)
                        Spacer(Modifier.height(ThrakshaSpacing.md))
                        Text(
                            text = "The profile rule check has not run in this session. " +
                                "It lives under Settings → Protection.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            ThrakshaFootnote(
                "Both packs are shipped with the app and cryptographically signed. " +
                    "Thraksha never downloads intelligence and never sends anything about " +
                    "this device anywhere.",
            )
        }
    }
}
