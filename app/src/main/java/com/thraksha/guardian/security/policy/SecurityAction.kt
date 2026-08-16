package com.thraksha.guardian.security.policy

/**
 * What a [SecurityAction] needs before it may be *executed* (as opposed to recommended).
 *
 * Declared per action so the policy layer can distinguish "we recommend this and could do
 * it" from "we recommend this but only the user / a future subsystem can do it" — an
 * action must never be represented as executable when it is not.
 */
enum class RequiredAuthority {
    /** No special authority — informational, or something the user does manually. */
    NONE,

    /** Requires Android Device Owner (DevicePolicyManager) authority. */
    DEVICE_OWNER,

    /**
     * Requires the consent-granted, per-app-scoped Network Guard tunnel (Phase 7).
     * Technically this is VPN consent, not Device Owner — but *automatic* execution
     * remains gated by the same Full Power product policy as every other action, so
     * there is no second authority model (guide §15).
     */
    VPN_GUARD,

    /** Requires a subsystem that does not exist in this build (e.g. network enforcement). */
    FUTURE_SUBSYSTEM,
}

/**
 * How invasive an action is. Maps onto the configured confidence-tier thresholds
 * (60/80/95/99): the more invasive the action, the more rule confidence automatic
 * execution requires.
 */
enum class ActionRiskTier { LOW, MEDIUM, HIGH, CRITICAL }

/**
 * A structured, recommendable response to findings. These are domain objects, not API
 * calls: the policy layer recommends them and an [Enforcer] decides what Android actually
 * allows.
 */
sealed interface SecurityAction {
    val requiredAuthority: RequiredAuthority
    val riskTier: ActionRiskTier

    /** True when some enforcer in this build could genuinely execute the action. */
    val isExecutable: Boolean

    /** Stable human-readable label used in events, audit rows and the UI. */
    val label: String

    /** Deny (revoke and block) one runtime permission on the target package. Device Owner only. */
    data class DenyPermission(val permission: String) : SecurityAction {
        override val requiredAuthority = RequiredAuthority.DEVICE_OWNER
        override val riskTier = ActionRiskTier.MEDIUM
        override val isExecutable = true
        override val label = "Deny permission ${permission.substringAfterLast('.')}"
    }

    /** Suspend (quarantine) the target package. Device Owner only. */
    data object SuspendPackage : SecurityAction {
        override val requiredAuthority = RequiredAuthority.DEVICE_OWNER
        override val riskTier = ActionRiskTier.HIGH
        override val isExecutable = true
        override val label = "Suspend package (quarantine)"
    }

    /**
     * Drop an intercepted outbound attempt before forwarding it (Phase 7). Executed by
     * the Network Guard's packet path, never by DevicePolicyManager. MEDIUM risk tier:
     * it removes exactly one observed network attempt, is inherently reversible (the
     * app can retry), and touches nothing else on the device.
     */
    data object BlockNetwork : SecurityAction {
        override val requiredAuthority = RequiredAuthority.VPN_GUARD
        override val riskTier = ActionRiskTier.MEDIUM
        override val isExecutable = true
        override val label = "Block outbound network attempt"
    }

    /** Point the user at the target app's system settings page to review/uninstall it. */
    data object OpenAppSettings : SecurityAction {
        override val requiredAuthority = RequiredAuthority.NONE
        override val riskTier = ActionRiskTier.LOW
        override val isExecutable = true
        override val label = "Review app in system settings"
    }

    /** Explicit no-op, so "policy decided to do nothing" is representable and auditable. */
    data object NoAction : SecurityAction {
        override val requiredAuthority = RequiredAuthority.NONE
        override val riskTier = ActionRiskTier.LOW
        override val isExecutable = true
        override val label = "No action"
    }
}

/** One recommended action plus the finding-derived rationale that justifies it. */
data class RecommendedAction(
    val action: SecurityAction,
    /** The rule whose finding motivated this recommendation. */
    val sourceRuleId: String,
    /** Why this action corresponds to the finding — never generic punishment. */
    val rationale: String,
)
