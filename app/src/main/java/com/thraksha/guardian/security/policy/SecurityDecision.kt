package com.thraksha.guardian.security.policy

import com.thraksha.guardian.data.config.ExecutionMode
import com.thraksha.guardian.security.PrivilegeLevel
import com.thraksha.guardian.security.engine.Finding
import com.thraksha.guardian.security.events.Severity

/**
 * What Thraksha should do about one app's findings.
 *
 * OBSERVE — record only; policy forbids or nothing warrants a response.
 * ADVISE  — explain, recommend, and record; perform no privileged action.
 * ACT     — execute the actionable recommendations through a privileged enforcer.
 */
enum class DecisionType { OBSERVE, ADVISE, ACT }

/**
 * The [PolicyEngine]'s verdict for one app in one audit. Carries everything a later
 * enforcer and the UI need without re-deriving anything: the originating findings, the
 * authority/policy context the decision was made under, and the recommended actions.
 *
 * A decision never overloads [Finding] — detection says *what was found*; this says
 * *what should be done about it*.
 */
data class SecurityDecision(
    /**
     * Deterministic identifier: same package + same findings + same context → same id.
     * Deliberately not random/time-based so repeated decisions are provably identical.
     */
    val decisionId: String,
    val packageName: String,
    val appName: String,
    val findings: List<Finding>,
    val highestSeverity: Severity?,
    /**
     * The strongest *rule confidence/weight* among the findings (an authored value from
     * the signed rulepack, 0..100) — not a machine-learning probability.
     */
    val maxRuleConfidence: Int,
    val privilegeLevel: PrivilegeLevel,
    val executionMode: ExecutionMode,
    val decisionType: DecisionType,
    val recommendedActions: List<RecommendedAction>,
    /** Human-readable reasoning: what was found, why it matters, what is recommended. */
    val explanation: String,
) {
    /** Recommendations an enforcer in this build could actually execute automatically. */
    val executableActions: List<RecommendedAction>
        get() = recommendedActions.filter {
            it.action.isExecutable &&
                (it.action.requiredAuthority == RequiredAuthority.DEVICE_OWNER ||
                    it.action.requiredAuthority == RequiredAuthority.VPN_GUARD)
        }
}

/**
 * Confidence thresholds gating *automatic* action, keyed by [ActionRiskTier]: the more
 * invasive the action, the more rule confidence auto-execution requires. Defaults mirror
 * the seeded `ConfigStore` values (60/80/95/99); kept as an independent type so the
 * policy layer stays free of Android/database types.
 */
data class PolicyThresholds(
    val low: Int = 60,
    val medium: Int = 80,
    val high: Int = 95,
    val critical: Int = 99,
) {
    fun forTier(tier: ActionRiskTier): Int = when (tier) {
        ActionRiskTier.LOW -> low
        ActionRiskTier.MEDIUM -> medium
        ActionRiskTier.HIGH -> high
        ActionRiskTier.CRITICAL -> critical
    }
}
