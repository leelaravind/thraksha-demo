package com.thraksha.guardian.ui.screens.protect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thraksha.guardian.security.act.UserActCoordinator
import com.thraksha.guardian.security.act.UserActOption
import com.thraksha.guardian.security.act.UserActResult
import com.thraksha.guardian.security.act.UserActStatus
import com.thraksha.guardian.security.engine.FindingSource
import com.thraksha.guardian.security.evidence.CapabilityEvidence
import com.thraksha.guardian.security.evidence.StatusMapper
import com.thraksha.guardian.security.inventory.GrantState
import com.thraksha.guardian.security.policy.EnforcementVerdict
import com.thraksha.guardian.security.scan.AppScanRecord
import com.thraksha.guardian.security.scan.DeviceScanEngine
import com.thraksha.guardian.ui.design.EvidenceStage
import com.thraksha.guardian.ui.design.EvidenceStageState
import com.thraksha.guardian.ui.design.StatusTone
import com.thraksha.guardian.ui.design.ThrakshaCard
import com.thraksha.guardian.ui.design.ThrakshaDisclosure
import com.thraksha.guardian.ui.design.ThrakshaDivider
import com.thraksha.guardian.ui.design.ThrakshaEmptyState
import com.thraksha.guardian.ui.design.ThrakshaEvidenceRail
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
import com.thraksha.guardian.ui.design.ThrakshaTextAction
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * FINDING DETAILS — progressive disclosure in four layers.
 *
 *  1. What this is, in one sentence.
 *  2. What Thraksha suggests.
 *  3. The DECLARED → GRANTED → OBSERVED evidence, in plain language.
 *  4. Technical details, collapsed: rules, severities, digests, policy and enforcement.
 *
 * The ACT bar offers only options `UserActCoordinator` says are technically supported for
 * this record right now, and the outcome line renders the coordinator's verified status —
 * ACTED cannot appear from anywhere else.
 */
@Composable
fun FindingDetailScreen(
    packageName: String,
    onBack: () -> Unit,
) {
    val result by DeviceScanEngine.lastResult.collectAsStateWithLifecycle()
    val record = result?.apps?.firstOrNull { it.packageName == packageName }

    ThrakshaScaffold(title = record?.displayName ?: "App details", onBack = onBack) { padding ->
        if (record == null) {
            ThrakshaEmptyState(
                title = "No longer in the last scan",
                message = "This app was not part of the most recent scan result. " +
                    "Run a new scan to check it again.",
                icon = Icons.Default.SearchOff,
                modifier = Modifier.fillMaxWidth(),
            )
            return@ThrakshaScaffold
        }
        FindingDetailContent(record, padding)
    }
}

@Composable
private fun FindingDetailContent(
    record: AppScanRecord,
    padding: androidx.compose.foundation.layout.PaddingValues,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tone = ProtectPresentation.statusTone(record.displayStatus)

    var sheetOpen by remember(record.packageName) { mutableStateOf(false) }
    var options by remember(record.packageName) { mutableStateOf<List<UserActOption>>(emptyList()) }
    var confirmFor by remember(record.packageName) { mutableStateOf<UserActOption?>(null) }
    var lastResult by remember(record.packageName) { mutableStateOf<UserActResult?>(null) }

    fun execute(option: UserActOption) {
        scope.launch {
            lastResult = UserActCoordinator.perform(context, record, option)
            sheetOpen = false
            confirmFor = null
        }
    }

    ThrakshaPageColumn(padding = padding) {
        // ---- Layer 1: what this is --------------------------------------
        ThrakshaCard(ribbon = tone) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ThrakshaSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = record.displayName,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                ThrakshaStatusChip(
                    ProtectPresentation.statusLabel(record.displayStatus),
                    tone,
                )
            }
            Spacer(Modifier.height(ThrakshaSpacing.md))
            Text(
                text = ProtectPresentation.plainSummary(record),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            record.contextAssessment?.let { assessment ->
                Spacer(Modifier.height(ThrakshaSpacing.sm))
                ThrakshaStatusChip(
                    ProtectPresentation.contextLine(assessment.assessment),
                    ProtectPresentation.contextTone(assessment.assessment),
                )
            }
        }

        // ---- Layer 2: what to do ----------------------------------------
        record.displayStatus?.let { status ->
            ThrakshaCard {
                ThrakshaSectionHeader("Suggested response")
                Text(
                    text = StatusMapper.recommendation(status),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(ThrakshaSpacing.md))
                ThrakshaPrimaryButton(
                    text = "Choose an action",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        scope.launch {
                            options = UserActCoordinator.supportedOptions(context, record)
                            sheetOpen = true
                        }
                    },
                )
                lastResult?.let { outcome ->
                    Spacer(Modifier.height(ThrakshaSpacing.md))
                    ActOutcome(outcome)
                }
            }
        }

        // ---- Layer 3: evidence ------------------------------------------
        if (record.evidence.isNotEmpty()) {
            Column {
                ThrakshaSectionHeader("What Thraksha checked")
                ThrakshaCard {
                    record.evidence.forEachIndexed { index, evidence ->
                        if (index > 0) {
                            Spacer(Modifier.height(ThrakshaSpacing.md))
                            ThrakshaDivider()
                            Spacer(Modifier.height(ThrakshaSpacing.md))
                        }
                        ThrakshaEvidenceRail(
                            capability = evidence.capabilityLabel,
                            stages = evidence.toStages(),
                        )
                    }
                }
                Spacer(Modifier.height(ThrakshaSpacing.sm))
                ThrakshaFootnote(
                    "Asking for something and being allowed to do it are not the same as " +
                        "doing it. The last step is only ticked when Thraksha saw it happen " +
                        "itself.",
                )
            }
        }

        // ---- Layer 4: technical -----------------------------------------
        ThrakshaCard {
            ThrakshaDisclosure("Technical details") {
                ThrakshaFactRow("Package", record.packageName)
                ThrakshaFactRow("Type", if (record.isSystemApp) "System" else "User-installed")
                ThrakshaFactRow("Enabled", if (record.isEnabled) "Yes" else "No")
                record.versionName?.let { ThrakshaFactRow("Version", it) }
                record.installerPackage?.let { ThrakshaFactRow("Installed by", it) }
                ThrakshaFactRow(
                    "Signing certificate SHA-256",
                    record.certSha256.firstOrNull()?.abbreviate() ?: "unavailable",
                )
                ThrakshaFactRow(
                    "Base APK SHA-256",
                    record.baseApkSha256?.abbreviate() ?: "unavailable",
                )
                ThrakshaFactRow("Detection classification", record.classification.name)

                if (record.evidence.isNotEmpty()) {
                    Spacer(Modifier.height(ThrakshaSpacing.sm))
                    ThrakshaSectionHeader("Capability evidence, verbatim")
                    record.evidence.forEach { evidence ->
                        ThrakshaPanel {
                            Text(
                                text = evidence.capabilityLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            ThrakshaFactRow("Grant state", evidence.grantState.name)
                            evidence.declaredEvidence.forEach { declaration ->
                                Text(
                                    text = "• $declaration",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            // The engine's own words for why stage 3 is empty.
                            evidence.observationLimitation?.let { limitation ->
                                Text(
                                    text = limitation,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            evidence.latestObservation?.let { observation ->
                                ThrakshaFactRow("Observation source", observation.source)
                                ThrakshaFactRow(
                                    "Reliability",
                                    observation.reliability.name,
                                )
                            }
                        }
                    }
                }
                if (!record.evidenceComplete) {
                    ThrakshaFactRow(
                        "Evidence",
                        "incomplete",
                        valueTone = StatusTone.WARN,
                    )
                }

                record.contextAssessment?.let { assessment ->
                    Spacer(Modifier.height(ThrakshaSpacing.sm))
                    ThrakshaPanel {
                        Text(
                            text = "Context — ${assessment.assessment.name}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = assessment.rationale,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (record.findings.isNotEmpty()) {
                    Spacer(Modifier.height(ThrakshaSpacing.sm))
                    ThrakshaSectionHeader("Findings (${record.findings.size})")
                    record.findings.forEach { finding ->
                        ThrakshaPanel {
                            Text(
                                text = when (finding.source) {
                                    FindingSource.GENERIC_RULE -> "Generic risk rule"
                                    FindingSource.PROFILE_RULE -> "Contextual profile rule"
                                    FindingSource.THREAT_INTELLIGENCE -> "Threat intelligence match"
                                    FindingSource.NETWORK_THREAT_INTELLIGENCE ->
                                        "Network threat intelligence"
                                } + " · ${finding.severity}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = finding.reason,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            finding.evidence.forEach { line ->
                                Text(
                                    text = "• $line",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                record.decision?.let { decision ->
                    Spacer(Modifier.height(ThrakshaSpacing.sm))
                    ThrakshaPanel {
                        Text(
                            text = "Policy decision · ${decision.decisionType.name}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = decision.explanation,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                record.enforcement?.let { enforcement ->
                    ThrakshaFactRow(
                        "Response",
                        enforcement.verdict.name.replace('_', ' '),
                        valueTone = if (enforcement.verdict == EnforcementVerdict.ACTED) {
                            StatusTone.OK
                        } else {
                            StatusTone.WARN
                        },
                    )
                }
            }
        }

        // The observed-evidence caveat belongs to every finding screen.
        ThrakshaFootnote(
            "Findings describe what an app is able to do. They are not accusations of " +
                "malware unless the status says a known threat matched.",
        )
    }

    // ---- ACT option sheet -----------------------------------------------
    if (sheetOpen) {
        AlertDialog(
            onDismissRequest = { sheetOpen = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("Respond to ${record.displayName}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(ThrakshaSpacing.sm)) {
                    Text(
                        text = "Only actions that are possible right now are listed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    options.forEach { option ->
                        if (option.disruptive) {
                            ThrakshaSecondaryButton(
                                text = option.label,
                                tone = StatusTone.DANGER,
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { confirmFor = option },
                            )
                        } else {
                            ThrakshaSecondaryButton(
                                text = option.label,
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { execute(option) },
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                ThrakshaTextAction("Close", onClick = { sheetOpen = false })
            },
        )
    }

    // Disruptive actions always require a second, explicit confirmation.
    confirmFor?.let { option ->
        AlertDialog(
            onDismissRequest = {
                scope.launch { lastResult = UserActCoordinator.recordCancelled(record, option) }
                confirmFor = null
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text(option.label + "?") },
            text = {
                Text(
                    text = option.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                ThrakshaPrimaryButton(text = "Confirm", onClick = { execute(option) })
            },
            dismissButton = {
                ThrakshaTextAction("Cancel", onClick = {
                    scope.launch {
                        lastResult = UserActCoordinator.recordCancelled(record, option)
                    }
                    confirmFor = null
                })
            },
        )
    }
}

/** Renders the coordinator's verified outcome. Never rounds a status up. */
@Composable
private fun ActOutcome(outcome: UserActResult) {
    val tone = when (outcome.status) {
        UserActStatus.ACTED -> StatusTone.OK
        UserActStatus.PARTIALLY_ACTED, UserActStatus.USER_ACTION_REQUIRED -> StatusTone.WARN
        UserActStatus.FAILED, UserActStatus.UNSUPPORTED -> StatusTone.DANGER
        UserActStatus.KEPT_WATCHING, UserActStatus.DISMISSED, UserActStatus.CANCELLED ->
            StatusTone.NEUTRAL
    }
    val label = when (outcome.status) {
        UserActStatus.ACTED -> "Done and verified"
        UserActStatus.PARTIALLY_ACTED -> "Partly done"
        UserActStatus.USER_ACTION_REQUIRED -> "Needs you to finish"
        UserActStatus.FAILED -> "Did not work"
        UserActStatus.UNSUPPORTED -> "Not possible"
        UserActStatus.KEPT_WATCHING -> "Still watching"
        UserActStatus.DISMISSED -> "Hidden for now"
        UserActStatus.CANCELLED -> "Cancelled"
    }
    ThrakshaPanel {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ThrakshaSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ThrakshaStatusChip(label, tone)
            Text(
                text = outcome.option.label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = outcome.detail,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * Maps one capability's real evidence onto the three rail stages.
 *
 * Each stage reads only its own field: the GRANTED line can never be produced from
 * [CapabilityEvidence.declared], and OBSERVED is driven solely by
 * [CapabilityEvidence.observed], which the assembler sets only for VERIFIED observations.
 */
private fun CapabilityEvidence.toStages(): List<EvidenceStage> = listOf(
    EvidenceStage(
        name = "Declared",
        summary = if (declared) {
            "The app asks to be able to do this."
        } else {
            "The app does not ask for this."
        },
        state = if (declared) EvidenceStageState.CONFIRMED else EvidenceStageState.ABSENT,
    ),
    EvidenceStage(
        name = "Allowed",
        summary = when (grantState) {
            GrantState.GRANTED -> "Android is currently allowing it."
            GrantState.ENABLED -> "You turned this on."
            GrantState.DENIED -> "Android is not allowing it."
            GrantState.DISABLED -> "It is not turned on."
            GrantState.UNKNOWN -> "Thraksha could not determine this."
            GrantState.NOT_APPLICABLE -> "There is nothing to allow for this one."
        },
        state = when (grantState) {
            GrantState.GRANTED, GrantState.ENABLED -> EvidenceStageState.CONFIRMED
            GrantState.DENIED, GrantState.DISABLED, GrantState.NOT_APPLICABLE ->
                EvidenceStageState.ABSENT
            GrantState.UNKNOWN -> EvidenceStageState.UNKNOWN
        },
    ),
    EvidenceStage(
        name = "Seen happening",
        // The engine's limitation string is precise but names Android internals. The rail
        // carries the plain-language equivalent; the verbatim text stays available under
        // Technical details, so nothing is softened away — only moved.
        summary = latestObservation?.let { observation ->
            "${observation.detail} — ${formatClock(observation.observedAt)}" +
                if (observationCount > 1) " (seen $observationCount times)" else ""
        } ?: plainLimitation(observationLimitation),
        state = if (observed) EvidenceStageState.CONFIRMED_NOTABLE else EvidenceStageState.UNKNOWN,
    ),
)

/** Plain-language stand-in for an engine limitation string. Never claims an observation. */
private fun plainLimitation(limitation: String?): String = when {
    limitation == null -> "No verified sighting."
    limitation.contains("NOT OBSERVABLE") ->
        "Android does not report this to an app like Thraksha, so it cannot be confirmed " +
            "either way."
    else -> "Thraksha has not seen this happen."
}

private fun formatClock(epochMillis: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(epochMillis))

private fun String.abbreviate(): String =
    if (length <= 20) this else "${take(10)}…${takeLast(10)}"
