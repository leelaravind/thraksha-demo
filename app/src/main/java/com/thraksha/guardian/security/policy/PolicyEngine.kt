package com.thraksha.guardian.security.policy

import com.thraksha.guardian.data.config.ExecutionMode
import com.thraksha.guardian.security.PrivilegeLevel
import com.thraksha.guardian.security.engine.Finding
import com.thraksha.guardian.security.engine.FindingSource
import com.thraksha.guardian.security.events.Severity

/**
 * Pure, deterministic mapping from (findings, privilege, execution mode, thresholds) to a
 * [SecurityDecision]. Contains **no Android framework calls** — the Android-observed
 * inputs (privilege level, configured mode) are handed in by the caller, which is what
 * lets every policy path be unit-tested off-device.
 *
 * Semantics (guide §4.4):
 *  * no findings                            → OBSERVE
 *  * findings, not Device Owner             → ADVISE (regardless of ExecutionMode —
 *                                             without authority there is nothing to gate)
 *  * findings, Device Owner + OBSERVE mode  → OBSERVE (operator forbids automatic action)
 *  * findings, Device Owner + GUIDED        → ADVISE (act only on explicit user approval)
 *  * findings, Device Owner + AUTO_DEFEND   → ACT when an eligible HIGH/CRITICAL finding
 *    (or LOCKDOWN — at least as aggressive)   backs an executable action whose risk-tier
 *                                             confidence threshold is met; else ADVISE
 *
 * Confidence values are authored rule weights from the signed rulepack, not probabilities;
 * they gate how invasive an *automatic* action may be (see [PolicyThresholds]).
 */
object PolicyEngine {

    /**
     * The runtime permissions a Device Owner could meaningfully deny for the demo's
     * finding set. A permission not in this table is never turned into a DENY_PERMISSION
     * recommendation — e.g. QUERY_ALL_PACKAGES is an install-time capability that cannot
     * be revoked at runtime, so recommending its denial would be recommending the
     * impossible.
     */
    val RUNTIME_DENIABLE_PERMISSIONS = setOf(
        "android.permission.READ_MEDIA_IMAGES",
        "android.permission.READ_MEDIA_VIDEO",
        "android.permission.READ_EXTERNAL_STORAGE",
        "android.permission.ACCESS_FINE_LOCATION",
        "android.permission.ACCESS_BACKGROUND_LOCATION",
        "android.permission.RECORD_AUDIO",
        "android.permission.CAMERA",
    )

    fun decide(
        packageName: String,
        appName: String,
        findings: List<Finding>,
        privilegeLevel: PrivilegeLevel,
        executionMode: ExecutionMode,
        thresholds: PolicyThresholds = PolicyThresholds(),
    ): SecurityDecision {
        val highestSeverity = findings.maxByOrNull { it.severity.ordinal }?.severity
        val maxConfidence = findings.maxOfOrNull { it.confidence } ?: 0
        val recommendations = recommendActions(findings)

        val decisionType = decideType(
            findings, privilegeLevel, executionMode, recommendations, maxConfidence, thresholds,
        )

        return SecurityDecision(
            decisionId = deterministicId(packageName, findings, privilegeLevel, executionMode),
            packageName = packageName,
            appName = appName,
            findings = findings,
            highestSeverity = highestSeverity,
            maxRuleConfidence = maxConfidence,
            privilegeLevel = privilegeLevel,
            executionMode = executionMode,
            decisionType = decisionType,
            recommendedActions = recommendations,
            explanation = explain(
                appName, findings, highestSeverity, privilegeLevel, executionMode, decisionType,
            ),
        )
    }

    private fun decideType(
        findings: List<Finding>,
        privilegeLevel: PrivilegeLevel,
        executionMode: ExecutionMode,
        recommendations: List<RecommendedAction>,
        maxConfidence: Int,
        thresholds: PolicyThresholds,
    ): DecisionType {
        if (findings.isEmpty()) return DecisionType.OBSERVE

        // Without Device Owner authority the only honest response is advice.
        if (privilegeLevel != PrivilegeLevel.DEVICE_OWNER) return DecisionType.ADVISE

        return when (executionMode) {
            ExecutionMode.OBSERVE -> DecisionType.OBSERVE
            ExecutionMode.GUIDED -> DecisionType.ADVISE
            ExecutionMode.AUTO_DEFEND, ExecutionMode.LOCKDOWN -> {
                // LOCKDOWN's broader Safe Mode behaviour is future work (Phase 8 owns
                // Safe Mode); for automatic containment it is treated as
                // at-least-AUTO_DEFEND, never as less, and introduces nothing wider.
                val eligibleSeverity = findings.any { it.severity >= Severity.HIGH }
                val actionable = recommendations.any {
                    it.action.isExecutable &&
                        (it.action.requiredAuthority == RequiredAuthority.DEVICE_OWNER ||
                            it.action.requiredAuthority == RequiredAuthority.VPN_GUARD) &&
                        maxConfidence >= thresholds.forTier(it.action.riskTier)
                }
                if (eligibleSeverity && actionable) DecisionType.ACT else DecisionType.ADVISE
            }
        }
    }

    /**
     * Translates findings into responses that correspond to them (guide §4.6) — never
     * arbitrary punishment because severity is high:
     *
     *  * an offending **runtime-deniable** permission → recommend denying that permission;
     *  * an offending install-time capability (e.g. QUERY_ALL_PACKAGES) → advise reviewing
     *    the app in settings, because no runtime revocation exists;
     *  * a CRITICAL compound finding → recommend suspension/quarantine of the package.
     */
    private fun recommendActions(findings: List<Finding>): List<RecommendedAction> {
        if (findings.isEmpty()) return emptyList()

        val recommendations = mutableListOf<RecommendedAction>()
        val recommendedPermissions = mutableSetOf<String>()

        // A live network finding maps to exactly one proportionate response: block that
        // outbound attempt (guide §31 — never permission denial or suspension from a
        // network-only signal). Only NETWORK_THREAT_INTELLIGENCE findings produce this.
        findings.filter { it.source == FindingSource.NETWORK_THREAT_INTELLIGENCE }
            .sortedBy { it.ruleId }
            .take(1)
            .forEach { finding ->
                recommendations += RecommendedAction(
                    action = SecurityAction.BlockNetwork,
                    sourceRuleId = finding.ruleId,
                    rationale = "The observed outbound attempt matched a signed network " +
                        "indicator; dropping the packet before forwarding stops exactly " +
                        "this attempt and nothing else.",
                )
            }

        findings.sortedBy { it.ruleId }.forEach { finding ->
            finding.offendingPermissions.sorted().forEach { permission ->
                if (permission in RUNTIME_DENIABLE_PERMISSIONS &&
                    recommendedPermissions.add(permission)
                ) {
                    recommendations += RecommendedAction(
                        action = SecurityAction.DenyPermission(permission),
                        sourceRuleId = finding.ruleId,
                        rationale = "${finding.appName} declares $permission, which is " +
                            "outside the ${finding.appType.name} baseline. Denying it removes " +
                            "the capability without uninstalling the app.",
                    )
                }
            }
        }

        findings.filter { it.severity == Severity.CRITICAL }
            .sortedBy { it.ruleId }
            .take(1)
            .forEach { finding ->
                recommendations += RecommendedAction(
                    action = SecurityAction.SuspendPackage,
                    sourceRuleId = finding.ruleId,
                    rationale = "The compound risk profile (${finding.reason}) warrants " +
                        "quarantining the app by package suspension until reviewed.",
                )
            }

        // Always give the user a manual path — it is the only executable response
        // without Device Owner authority.
        recommendations += RecommendedAction(
            action = SecurityAction.OpenAppSettings,
            sourceRuleId = findings.minOf { it.ruleId },
            rationale = "Review the app's permissions in system settings and uninstall it " +
                "if it is not trusted.",
        )

        return recommendations
    }

    private fun explain(
        appName: String,
        findings: List<Finding>,
        highestSeverity: Severity?,
        privilegeLevel: PrivilegeLevel,
        executionMode: ExecutionMode,
        decisionType: DecisionType,
    ): String {
        if (findings.isEmpty()) {
            return "$appName matches its role baseline; nothing to respond to."
        }
        val what = "${findings.size} finding(s) against $appName, " +
            "strongest severity ${highestSeverity?.name}."
        val why = when (decisionType) {
            DecisionType.OBSERVE ->
                "Execution mode ${executionMode.name} forbids automatic action; " +
                    "findings are recorded only."
            DecisionType.ADVISE ->
                if (privilegeLevel != PrivilegeLevel.DEVICE_OWNER) {
                    "Thraksha is not Device Owner (privilege ${privilegeLevel.name}), so it " +
                        "cannot act on another app; it advises instead."
                } else {
                    "Execution mode ${executionMode.name} requires explicit approval " +
                        "before acting."
                }
            DecisionType.ACT ->
                "Device Owner authority and execution mode ${executionMode.name} permit " +
                    "automatic containment of the eligible findings."
        }
        return "$what $why"
    }

    private fun deterministicId(
        packageName: String,
        findings: List<Finding>,
        privilegeLevel: PrivilegeLevel,
        executionMode: ExecutionMode,
    ): String {
        val ruleIds = findings.map { it.ruleId }.sorted().joinToString("+").ifEmpty { "clean" }
        return "$packageName/$ruleIds/${privilegeLevel.name}/${executionMode.name}"
    }
}
