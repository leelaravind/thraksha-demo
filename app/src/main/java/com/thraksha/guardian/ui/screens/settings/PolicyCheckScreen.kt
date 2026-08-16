package com.thraksha.guardian.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thraksha.guardian.data.config.ExecutionMode
import com.thraksha.guardian.security.PrivilegeLevel
import com.thraksha.guardian.security.engine.AppAuditResult
import com.thraksha.guardian.security.engine.AuditResult
import com.thraksha.guardian.security.engine.FindingSource
import com.thraksha.guardian.security.engine.SecurityAuditEngine
import com.thraksha.guardian.security.policy.ActionStatus
import com.thraksha.guardian.security.policy.DecisionType
import com.thraksha.guardian.security.policy.EnforcementVerdict
import com.thraksha.guardian.ui.design.StatusTone
import com.thraksha.guardian.ui.design.ThrakshaCard
import com.thraksha.guardian.ui.design.ThrakshaDisclosure
import com.thraksha.guardian.ui.design.ThrakshaEmptyState
import com.thraksha.guardian.ui.design.ThrakshaFactRow
import com.thraksha.guardian.ui.design.ThrakshaFootnote
import com.thraksha.guardian.ui.design.ThrakshaLoadingState
import com.thraksha.guardian.ui.design.ThrakshaPageColumn
import com.thraksha.guardian.ui.design.ThrakshaPanel
import com.thraksha.guardian.ui.design.ThrakshaPrimaryButton
import com.thraksha.guardian.ui.design.ThrakshaScaffold
import com.thraksha.guardian.ui.design.ThrakshaSecondaryButton
import com.thraksha.guardian.ui.design.ThrakshaSectionHeader
import com.thraksha.guardian.ui.design.ThrakshaSpacing
import com.thraksha.guardian.ui.design.ThrakshaStatusChip
import kotlinx.coroutines.launch

/**
 * POLICY & ENFORCEMENT CHECK.
 *
 * The Phase 5/6 pipeline: signed profile rules over the registered demo apps, the policy
 * decision each produces, and — under Device Owner in GUIDED mode — the explicit approval
 * step before any containment runs.
 *
 * This is an operator surface, not a consumer one, so it lives under Settings → Protection
 * rather than on the Protect dashboard. Its behaviour is unchanged from the previous
 * dashboard implementation; only its presentation moved.
 */
@Composable
fun PolicyCheckScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val result by SecurityAuditEngine.state.collectAsStateWithLifecycle()
    val running by SecurityAuditEngine.isScanning.collectAsStateWithLifecycle()

    ThrakshaScaffold(title = "Policy & enforcement", onBack = onBack) { padding ->
        ThrakshaPageColumn(padding = padding) {
            ThrakshaCard {
                Text(
                    text = "Runs the signed profile rules against the demo apps registered " +
                        "for this build, then shows the policy decision and what — if " +
                        "anything — was actually done.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(ThrakshaSpacing.lg))
                ThrakshaPrimaryButton(
                    text = if (running) "Running…" else "Run check",
                    enabled = !running,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { scope.launch { SecurityAuditEngine.runAudit(context) } },
                )
                if (running) {
                    Spacer(Modifier.height(ThrakshaSpacing.md))
                    ThrakshaLoadingState(message = "Evaluating rules…")
                }
            }

            when (val current = result) {
                is AuditResult.NotRun -> ThrakshaEmptyState(
                    title = "Not run yet",
                    message = "Run the check to see the rule pack, the decisions it " +
                        "produced and the outcome of each one.",
                    icon = Icons.Default.Policy,
                )

                is AuditResult.Failed -> ThrakshaCard(ribbon = StatusTone.DANGER) {
                    ThrakshaStatusChip("Failed", StatusTone.DANGER)
                    Spacer(Modifier.height(ThrakshaSpacing.md))
                    Text(
                        text = current.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                is AuditResult.Completed -> {
                    ThrakshaCard {
                        ThrakshaFactRow("Apps audited", current.appsScanned.toString())
                        ThrakshaFactRow("Findings", current.threatsFound.toString())
                        ThrakshaFactRow(
                            "Rulepack",
                            "v${current.rulepackVersion} · ${current.rulesEvaluated} rules",
                        )
                        if (current.threatIntel.available) {
                            ThrakshaFactRow(
                                "Threat pack",
                                "v${current.threatIntel.packVersion} · " +
                                    "${current.threatIntel.indicatorsEvaluated} indicators",
                            )
                        } else {
                            ThrakshaFactRow(
                                "Threat pack",
                                "unavailable",
                                valueTone = StatusTone.DANGER,
                            )
                            Text(
                                text = current.threatIntel.detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    if (current.results.isEmpty()) {
                        ThrakshaEmptyState(
                            title = "No registered demo app installed",
                            message = "This check covers the demo apps this build knows " +
                                "about. None of them is installed on this device.",
                            icon = Icons.Default.Policy,
                        )
                    } else {
                        Column {
                            ThrakshaSectionHeader("Results")
                            Column(
                                verticalArrangement = Arrangement.spacedBy(ThrakshaSpacing.cardGap),
                            ) {
                                current.results.forEach { app ->
                                    AuditedAppCard(
                                        app = app,
                                        onApprove = {
                                            scope.launch {
                                                SecurityAuditEngine.approveAndEnforce(
                                                    context,
                                                    app.packageName,
                                                )
                                            }
                                        },
                                        onRestore = {
                                            scope.launch {
                                                SecurityAuditEngine.restoreDemoState(
                                                    context,
                                                    app.packageName,
                                                )
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            ThrakshaFootnote(
                "Profile findings describe what an app declares in its manifest. " +
                    "Threat-intelligence findings are exact package, certificate or APK " +
                    "fingerprint matches against the signed offline pack. In advice mode no " +
                    "automatic action is taken; every decision is recorded either way.",
            )
        }
    }
}

@Composable
private fun AuditedAppCard(
    app: AppAuditResult,
    onApprove: () -> Unit,
    onRestore: () -> Unit,
) {
    val flagged = !app.isClean
    val tone = if (flagged) StatusTone.DANGER else StatusTone.OK

    ThrakshaCard(ribbon = tone) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ThrakshaSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.appName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ThrakshaStatusChip(if (flagged) "Findings" else "No findings", tone)
        }

        app.enforcement?.let { enforcement ->
            Spacer(Modifier.height(ThrakshaSpacing.sm))
            ThrakshaStatusChip(
                enforcement.verdict.name.replace('_', ' '),
                when (enforcement.verdict) {
                    EnforcementVerdict.ACTED -> StatusTone.OK
                    EnforcementVerdict.NOT_ACTED -> StatusTone.DANGER
                    else -> StatusTone.WARN
                },
            )
        }

        if (!flagged) {
            Spacer(Modifier.height(ThrakshaSpacing.sm))
            Text(
                text = "Profile matches the ${app.appType.name.lowercase()} baseline.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@ThrakshaCard
        }

        Spacer(Modifier.height(ThrakshaSpacing.md))
        ThrakshaDisclosure("Findings and decision") {
            app.findings.forEach { finding ->
                ThrakshaPanel {
                    Text(
                        text = when (finding.source) {
                            FindingSource.PROFILE_RULE -> "Profile rule"
                            FindingSource.GENERIC_RULE -> "Generic risk rule"
                            FindingSource.THREAT_INTELLIGENCE -> "Threat intelligence"
                            FindingSource.NETWORK_THREAT_INTELLIGENCE -> "Network intelligence"
                        } + " · ${finding.severity} · confidence ${finding.confidence}",
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

            app.decision?.let { decision ->
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
                    decision.recommendedActions.forEach { recommended ->
                        Text(
                            text = "• ${recommended.action.label} — ${recommended.rationale}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // "Full power would…" is stated only for actions Phase 5 genuinely
                    // implements, and only when the app does not currently have them.
                    if (decision.privilegeLevel != PrivilegeLevel.DEVICE_OWNER &&
                        decision.executableActions.isNotEmpty()
                    ) {
                        Text(
                            text = "With device-owner authority Thraksha would be able to: " +
                                decision.executableActions.joinToString("; ") {
                                    it.action.label.lowercase()
                                } + ".",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            app.enforcement?.let { enforcement ->
                ThrakshaPanel {
                    Text(
                        text = when (enforcement.verdict) {
                            EnforcementVerdict.ADVISED ->
                                "Advised — recommendations recorded. No automatic action ran."
                            EnforcementVerdict.ACTED ->
                                "Acted — every action below ran and the resulting state was " +
                                    "verified."
                            EnforcementVerdict.PARTIALLY_ACTED ->
                                "Partially acted — only the verified actions below succeeded."
                            EnforcementVerdict.NOT_ACTED ->
                                "Not acted — no action could be executed."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    enforcement.outcomes
                        .filter {
                            it.status == ActionStatus.SUCCEEDED ||
                                it.status == ActionStatus.ALREADY_IN_STATE
                        }
                        .forEach { outcome ->
                            Text(
                                text = "✓ ${outcome.action.label} — ${outcome.detail}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                }
            }
        }

        // GUIDED under device owner: containment requires an explicit human approval.
        app.decision?.let { decision ->
            if (decision.privilegeLevel == PrivilegeLevel.DEVICE_OWNER &&
                decision.decisionType == DecisionType.ADVISE &&
                decision.executionMode == ExecutionMode.GUIDED &&
                app.enforcement?.verdict != EnforcementVerdict.ACTED &&
                app.enforcement?.verdict != EnforcementVerdict.PARTIALLY_ACTED
            ) {
                Spacer(Modifier.height(ThrakshaSpacing.md))
                ThrakshaSecondaryButton(
                    text = "Approve containment",
                    tone = StatusTone.DANGER,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onApprove,
                )
            }
        }

        if (app.enforcement?.verdict == EnforcementVerdict.ACTED ||
            app.enforcement?.verdict == EnforcementVerdict.PARTIALLY_ACTED
        ) {
            Spacer(Modifier.height(ThrakshaSpacing.sm))
            ThrakshaSecondaryButton(
                text = "Restore demo state",
                modifier = Modifier.fillMaxWidth(),
                onClick = onRestore,
            )
        }
    }
}
