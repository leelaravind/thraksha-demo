package com.thraksha.guardian.ui.screens.protect

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thraksha.guardian.security.act.UserActCoordinator
import com.thraksha.guardian.security.evidence.AppDisplayStatus
import com.thraksha.guardian.security.inventory.VisibilityScope
import com.thraksha.guardian.security.scan.AppScanRecord
import com.thraksha.guardian.security.scan.DeviceScanEngine
import com.thraksha.guardian.security.scan.ScanOutcome
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
 * SCAN RESULTS — what the last real scan found.
 *
 * Counts are read straight off [com.thraksha.guardian.security.scan.DeviceScanResult];
 * there is no derived or cached total anywhere in this file. Apps with nothing to review
 * and system packages are kept behind disclosures so the list opens on what matters.
 */
@Composable
fun FindingsScreen(
    onBack: () -> Unit,
    onOpenFinding: (String) -> Unit,
) {
    val result by DeviceScanEngine.lastResult.collectAsStateWithLifecycle()
    val dismissed by UserActCoordinator.dismissedPackages.collectAsStateWithLifecycle()

    ThrakshaScaffold(title = "Scan results", onBack = onBack) { padding ->
        val scan = result
        if (scan == null) {
            Column(modifier = Modifier.fillMaxWidth()) {
                ThrakshaEmptyState(
                    title = "No scan yet",
                    message = "Run a scan from Protect to see what is installed on this device.",
                    icon = Icons.Default.Radar,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            return@ThrakshaScaffold
        }

        val attention = ProtectPresentation.attentionItems(scan, dismissed)
        val incomplete = ProtectPresentation.incompleteItems(scan)
        val watching = scan.apps.filter {
            it.displayStatus == AppDisplayStatus.WATCHING && !it.isSystemApp
        }
        val system = scan.apps.filter { it.isSystemApp }

        ThrakshaPageColumn(padding = padding) {
            // ---- Summary ----------------------------------------------------
            ThrakshaCard(
                ribbon = when (scan.outcome) {
                    ScanOutcome.COMPLETE -> if (attention.isEmpty()) StatusTone.OK else StatusTone.WARN
                    ScanOutcome.PARTIAL -> StatusTone.WARN
                    ScanOutcome.ERROR -> StatusTone.DANGER
                },
            ) {
                Text(
                    text = when (scan.outcome) {
                        ScanOutcome.COMPLETE -> "Scan complete"
                        ScanOutcome.PARTIAL -> "Scan finished with limits"
                        ScanOutcome.ERROR -> "Scan could not finish"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(ThrakshaSpacing.xs))
                Text(
                    text = buildString {
                        append("${scan.analyzedCount} apps checked")
                        append(" · ")
                        append(
                            if (attention.isEmpty()) {
                                "nothing needs attention"
                            } else {
                                ProtectPresentation.plural(
                                    attention.size,
                                    "item needs attention",
                                    "items need attention",
                                )
                            },
                        )
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Finished ${ProtectPresentation.relativeTime(scan.completedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                scan.outcomeDetail?.let { detail ->
                    Spacer(Modifier.height(ThrakshaSpacing.sm))
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (scan.visibilityScope == VisibilityScope.REDUCED) {
                    Spacer(Modifier.height(ThrakshaSpacing.sm))
                    Text(
                        text = "This build cannot see every installed package, so these " +
                            "results cover only the apps Android exposes to Thraksha.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(ThrakshaSpacing.md))
                ThrakshaDisclosure("Scan details") {
                    ThrakshaFactRow("User apps", scan.userAppCount.toString())
                    ThrakshaFactRow("System packages", scan.systemAppCount.toString())
                    ThrakshaFactRow("Signing identities checked", scan.certificatesChecked.toString())
                    ThrakshaFactRow("APK fingerprints computed", scan.apkHashesComputed.toString())
                    ThrakshaFactRow("Findings recorded", scan.totalFindings.toString())
                    ThrakshaFactRow("Known-threat matches", scan.knownThreatApps.size.toString())
                    ThrakshaFactRow("High-exposure profiles", scan.highRiskApps.size.toString())
                    ThrakshaFactRow(
                        "Duration",
                        "${(scan.timings.totalMs / 100) / 10f}s",
                    )
                    if (scan.failedPackages.isNotEmpty()) {
                        ThrakshaFactRow(
                            "Packages that failed analysis",
                            scan.failedPackages.size.toString(),
                            valueTone = StatusTone.WARN,
                        )
                    }
                }
            }

            // ---- Needs attention --------------------------------------------
            if (attention.isNotEmpty()) {
                Column {
                    ThrakshaSectionHeader("Needs attention")
                    Column(verticalArrangement = Arrangement.spacedBy(ThrakshaSpacing.cardGap)) {
                        attention.forEach { record ->
                            FindingRow(record) { onOpenFinding(record.packageName) }
                        }
                    }
                }
            } else {
                ThrakshaEmptyState(
                    title = "Nothing needs attention",
                    message = "No app on this device matched a known threat or raised a " +
                        "finding that needs your decision.",
                    icon = Icons.Default.CheckCircle,
                )
            }

            // ---- Incomplete evidence ----------------------------------------
            if (incomplete.isNotEmpty()) {
                Column {
                    ThrakshaSectionHeader("Could not be fully checked")
                    ThrakshaFootnote(
                        "Evidence for these apps is incomplete. That is not the same as " +
                            "being clean — they simply could not be checked fully.",
                    )
                    Spacer(Modifier.height(ThrakshaSpacing.sm))
                    Column(verticalArrangement = Arrangement.spacedBy(ThrakshaSpacing.cardGap)) {
                        incomplete.forEach { record ->
                            FindingRow(record) { onOpenFinding(record.packageName) }
                        }
                    }
                }
            }

            // ---- Everything else --------------------------------------------
            ThrakshaCard {
                ThrakshaDisclosure(
                    "${watching.size} other apps being watched",
                ) {
                    watching.take(60).forEach { record ->
                        CompactAppRow(record) { onOpenFinding(record.packageName) }
                    }
                    if (watching.size > 60) {
                        ThrakshaFootnote("Showing the first 60 of ${watching.size}.")
                    }
                }
                ThrakshaDisclosure("${system.size} system packages") {
                    system.take(60).forEach { record ->
                        CompactAppRow(record) { onOpenFinding(record.packageName) }
                    }
                    if (system.size > 60) {
                        ThrakshaFootnote("Showing the first 60 of ${system.size}.")
                    }
                }
            }

            ThrakshaFootnote(
                "\"Watching\" means Thraksha knows what an app is able to do and has no " +
                    "evidence that needs your decision right now. It is not a guarantee " +
                    "that the app is safe.",
            )
        }
    }
}

@Composable
private fun FindingRow(record: AppScanRecord, onClick: () -> Unit) {
    val tone = ProtectPresentation.statusTone(record.displayStatus)
    ThrakshaCard(ribbon = tone, onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ThrakshaSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(ThrakshaSpacing.xs))
                Text(
                    text = ProtectPresentation.plainSummary(record),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ThrakshaStatusChip(ProtectPresentation.statusLabel(record.displayStatus), tone)
        }
    }
}

@Composable
private fun CompactAppRow(record: AppScanRecord, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = ThrakshaSpacing.touchTarget),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = record.displayName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        ThrakshaStatusChip(
            ProtectPresentation.statusLabel(record.displayStatus),
            ProtectPresentation.statusTone(record.displayStatus),
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
