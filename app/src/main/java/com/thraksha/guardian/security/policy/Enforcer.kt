package com.thraksha.guardian.security.policy

/**
 * The status of one attempted (or deliberately not attempted) action. Calling an Android
 * API is not success — SUCCEEDED may be reported only after the resulting OS state has
 * been queried and confirmed (guide §1.3).
 */
enum class ActionStatus {
    /** The action was recommended to the user; no automatic execution was attempted. */
    ADVISED,

    /** Executed AND the resulting OS state was verified. */
    SUCCEEDED,

    /** Executed but the OS state does not confirm it, or the call threw. */
    FAILED,

    /** No enforcer in this build can execute this action. */
    UNSUPPORTED,

    /** The enforcer lacks the authority the action requires. */
    NOT_AUTHORISED,

    /** The OS was already in the requested state; nothing was changed. */
    ALREADY_IN_STATE,
}

/** One action's outcome, with enough detail to audit it. */
data class ActionOutcome(
    val action: SecurityAction,
    val status: ActionStatus,
    /** What actually happened / why — including the verified state where applicable. */
    val detail: String,
)

/** Aggregate verdict over all outcomes. Partial success is represented, never rounded up. */
enum class EnforcementVerdict {
    /** All outcomes are advisory — nothing privileged was attempted. */
    ADVISED,

    /** Every attempted action succeeded with verified state. */
    ACTED,

    /** Some attempted actions succeeded (verified), some did not. */
    PARTIALLY_ACTED,

    /** Nothing succeeded: not authorised, unsupported, or all attempts failed. */
    NOT_ACTED,
}

/** The result of executing (or advising on) one [SecurityDecision]. */
data class EnforcementResult(
    val decisionId: String,
    val packageName: String,
    val appName: String,
    /** Which enforcer produced this (e.g. "AdvisoryEnforcer"). */
    val enforcer: String,
    val outcomes: List<ActionOutcome>,
    val verdict: EnforcementVerdict,
)

/**
 * Executes a [SecurityDecision] and reports what Android actually allowed. Detection
 * (RuleEngine) and policy (PolicyEngine) never touch Android enforcement APIs; the only
 * place a DevicePolicyManager may appear is inside a privileged [Enforcer] implementation.
 */
interface Enforcer {
    suspend fun execute(decision: SecurityDecision): EnforcementResult
}
