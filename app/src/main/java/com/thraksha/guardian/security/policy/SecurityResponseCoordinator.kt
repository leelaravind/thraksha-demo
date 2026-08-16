package com.thraksha.guardian.security.policy

import android.content.Context
import com.thraksha.guardian.data.config.ConfigStore
import com.thraksha.guardian.data.config.ExecutionMode
import com.thraksha.guardian.security.PrivilegeLevel
import com.thraksha.guardian.security.SecurityCapability
import com.thraksha.guardian.security.engine.Finding
import com.thraksha.guardian.security.events.SecurityEvent
import com.thraksha.guardian.security.events.SecurityEventBus

/**
 * The single decision entry point shared by the static audit path
 * (`SecurityAuditEngine`) and the live network path (`NetworkGuardEngine`) — the
 * smallest extraction that lets both reuse the pure [PolicyEngine] without duplicating
 * its invocation context (guide §14).
 *
 * Responsibilities: read the *current* privilege level, execution mode and thresholds,
 * invoke [PolicyEngine.decide], and emit the [SecurityEvent.DecisionMade] event for
 * findings-bearing decisions so every decision — static or live — lands in the same
 * encrypted audit chain. Enforcement routing stays with the callers, which own their
 * respective response mechanisms.
 */
object SecurityResponseCoordinator {

    /** The Android-observed policy context a decision is made under. */
    data class PolicyContext(
        val privilegeLevel: PrivilegeLevel,
        val executionMode: ExecutionMode,
        val thresholds: PolicyThresholds,
    )

    /**
     * Decides and (for findings-bearing decisions) emits the DECISION event. Callers
     * route the returned decision to their own response mechanism afterwards.
     */
    suspend fun decide(
        context: Context,
        packageName: String,
        appName: String,
        findings: List<Finding>,
    ): SecurityDecision {
        val policyContext = suspendingContext(context)
        val decision = PolicyEngine.decide(
            packageName = packageName,
            appName = appName,
            findings = findings,
            privilegeLevel = policyContext.privilegeLevel,
            executionMode = policyContext.executionMode,
            thresholds = policyContext.thresholds,
        )
        if (findings.isNotEmpty()) {
            SecurityEventBus.emit(
                SecurityEvent.DecisionMade(
                    packageName = decision.packageName,
                    appName = decision.appName,
                    decisionType = decision.decisionType.name,
                    highestSeverity = decision.highestSeverity ?: findings.first().severity,
                    findingCount = decision.findings.size,
                    maxRuleConfidence = decision.maxRuleConfidence,
                    explanation = decision.explanation,
                ),
            )
        }
        return decision
    }

    /** Reads the live policy context; safe defaults if the encrypted store is down. */
    suspend fun suspendingContext(context: Context): PolicyContext {
        val appContext = context.applicationContext
        val privilege = runCatching { SecurityCapability.currentLevel(appContext) }
            .getOrDefault(PrivilegeLevel.NORMAL)
        val configStore = runCatching { ConfigStore(appContext) }.getOrNull()
        return PolicyContext(
            privilegeLevel = privilege,
            executionMode = configStore
                ?.let { runCatching { it.getExecutionMode() }.getOrNull() }
                ?: ExecutionMode.OBSERVE,
            thresholds = configStore
                ?.let { runCatching { it.getTierThresholds() }.getOrNull() }
                ?.let { PolicyThresholds(it.low, it.medium, it.high, it.critical) }
                ?: PolicyThresholds(),
        )
    }
}
