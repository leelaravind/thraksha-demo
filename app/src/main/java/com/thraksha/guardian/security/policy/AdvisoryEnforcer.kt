package com.thraksha.guardian.security.policy

import com.thraksha.guardian.security.events.SecurityEvent
import com.thraksha.guardian.security.events.SecurityEventBus

/**
 * The non-privileged enforcement path: converts a decision into a clear, successful
 * **ADVISED** result — never a failed ACT.
 *
 * This class deliberately holds no `Context`, no `DevicePolicyManager` and no Android
 * import of any kind, which is the structural proof that Advice Mode performs zero
 * privileged actions. Its only side effect is emitting an [SecurityEvent.AdviceIssued]
 * onto the existing bus so the advice is persisted in the encrypted audit trail.
 *
 * It also serves as the safe fallback for an ACT decision that reaches a build with no
 * privileged enforcer: the outcome is honestly recorded as advisory, with each
 * Device-Owner action marked [ActionStatus.NOT_AUTHORISED] rather than pretended done.
 */
class AdvisoryEnforcer : Enforcer {

    override suspend fun execute(decision: SecurityDecision): EnforcementResult {
        val actFallback = decision.decisionType == DecisionType.ACT

        val outcomes = decision.recommendedActions.map { recommended ->
            val action = recommended.action
            when {
                actFallback && action.requiredAuthority == RequiredAuthority.DEVICE_OWNER ->
                    ActionOutcome(
                        action = action,
                        status = ActionStatus.NOT_AUTHORISED,
                        detail = "Policy decided ACT but no privileged enforcer is " +
                            "available in this build — recorded as advice only. " +
                            recommended.rationale,
                    )

                action.requiredAuthority == RequiredAuthority.FUTURE_SUBSYSTEM ->
                    ActionOutcome(
                        action = action,
                        status = ActionStatus.UNSUPPORTED,
                        detail = "No subsystem in this build can perform this action; " +
                            "listed for completeness only.",
                    )

                else -> ActionOutcome(
                    action = action,
                    status = ActionStatus.ADVISED,
                    detail = "Recommended to the user; no automatic OS action was " +
                        "performed. " + recommended.rationale,
                )
            }
        }

        val result = EnforcementResult(
            decisionId = decision.decisionId,
            packageName = decision.packageName,
            appName = decision.appName,
            enforcer = NAME,
            outcomes = outcomes,
            verdict = EnforcementVerdict.ADVISED,
        )

        SecurityEventBus.emit(
            SecurityEvent.AdviceIssued(
                packageName = decision.packageName,
                appName = decision.appName,
                recommendations = decision.recommendedActions.map { it.action.label },
                explanation = decision.explanation,
            ),
        )

        return result
    }

    companion object {
        const val NAME = "AdvisoryEnforcer"
    }
}
