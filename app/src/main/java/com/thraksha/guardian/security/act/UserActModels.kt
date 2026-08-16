package com.thraksha.guardian.security.act

import com.thraksha.guardian.security.PrivilegeLevel
import com.thraksha.guardian.security.enforcement.EnforcementTargets
import com.thraksha.guardian.security.evidence.AppDisplayStatus
import com.thraksha.guardian.security.policy.ActionOutcome
import com.thraksha.guardian.security.policy.ActionStatus
import com.thraksha.guardian.security.policy.EnforcementResult
import com.thraksha.guardian.security.policy.EnforcementVerdict
import com.thraksha.guardian.security.policy.PolicyEngine
import com.thraksha.guardian.security.scan.AppScanRecord

/**
 * The verified outcome vocabulary for a user ACT (guide §19). ACTED appears only after
 * the underlying enforcer confirmed real Android state; a settings hand-off is
 * USER_ACTION_REQUIRED, never a fake success and never a fake failure (guide §16).
 */
enum class UserActStatus {
    ACTED,
    PARTIALLY_ACTED,
    USER_ACTION_REQUIRED,
    FAILED,
    UNSUPPORTED,
    CANCELLED,
    KEPT_WATCHING,
    DISMISSED,
}

/** The result of one user ACT: what was chosen, what verifiably happened. */
data class UserActResult(
    val option: UserActOption,
    val status: UserActStatus,
    val detail: String,
    /** Present when a privileged enforcer ran (its per-action verified outcomes). */
    val enforcement: EnforcementResult? = null,
)

/**
 * One response the ACT sheet may offer. Options are *constructed only when technically
 * supported for the finding at hand* (guide §14: unavailable actions are not shown) —
 * [SupportedUserActions.forRecord] is the single builder, and the coordinator re-derives
 * the list at execution time so a stale UI can never smuggle an unsupported action.
 */
sealed interface UserActOption {
    val id: String
    val label: String
    val description: String

    /** True → the UI must show an explicit confirmation first (guide §18). */
    val disruptive: Boolean

    /**
     * Deny one runtime permission directly via Device Owner authority. Offered only
     * when policy maps the permission to the findings AND the target is enforceable.
     */
    data class RestrictPermissionDirect(val permission: String) : UserActOption {
        override val id = "restrict-direct:$permission"
        override val label = "Restrict ${permission.substringAfterLast('.')}"
        override val description =
            "Deny this runtime permission through Device Owner authority. Reversible " +
                "via Restore. The resulting Android state is verified before ACTED is shown."
        override val disruptive = true
    }

    /** Open the app's system settings page so the user can restrict/uninstall manually. */
    data object OpenAppSettings : UserActOption {
        override val id = "open-settings"
        override val label = "Open Android app settings"
        override val description =
            "Review or restrict this app's permissions manually. Thraksha cannot verify " +
                "what you do there, so this is recorded as a user hand-off, not as ACTED."
        override val disruptive = false
    }

    /** Arm the Network Guard user block for a package scoped into the active tunnel. */
    data object BlockNetwork : UserActOption {
        override val id = "block-network"
        override val label = "Block network (Network Guard)"
        override val description =
            "Every outbound attempt Network Guard intercepts from this app will be " +
                "dropped before forwarding. Applies only while the guard is active and " +
                "only to the tunnel-scoped app; each drop is individually audited."
        override val disruptive = true
    }

    /** Disarm a previously armed user block. */
    data object UnblockNetwork : UserActOption {
        override val id = "unblock-network"
        override val label = "Stop blocking network"
        override val description = "Remove the user-ordered Network Guard block."
        override val disruptive = false
    }

    /** Suspend (quarantine) the package via Device Owner authority. Allowlisted targets only. */
    data object SuspendApp : UserActOption {
        override val id = "suspend"
        override val label = "Suspend app (quarantine)"
        override val description =
            "Package suspension through Device Owner authority — the app's icon greys " +
                "out and it cannot run until restored. Reversible. State is verified " +
                "before ACTED is shown."
        override val disruptive = true
    }

    /** Explicit "no action, keep monitoring" — recorded, so the choice is auditable. */
    data object KeepWatching : UserActOption {
        override val id = "keep-watching"
        override val label = "Keep watching"
        override val description = "Take no action now; Thraksha keeps monitoring."
        override val disruptive = false
    }

    /** Hide this finding card for the session. Recorded; detection is unaffected. */
    data object Dismiss : UserActOption {
        override val id = "dismiss"
        override val label = "Dismiss"
        override val description =
            "Hide this card for this session. The finding itself remains recorded in " +
                "the audit trail and will reappear on the next scan."
        override val disruptive = false
    }
}

/**
 * Pure mapping from an enforcer's verified result to the user-facing ACT status
 * (guide §19). The honesty rule is structural: ACTED/PARTIALLY_ACTED can only come from
 * the corresponding verified enforcement verdicts; every failure path maps to FAILED or
 * USER_ACTION_REQUIRED — never rounded up.
 */
object UserActOutcomeMapper {
    fun fromEnforcement(
        verdict: EnforcementVerdict,
        outcomes: List<ActionOutcome>,
    ): UserActStatus = when (verdict) {
        EnforcementVerdict.ACTED -> UserActStatus.ACTED
        EnforcementVerdict.PARTIALLY_ACTED -> UserActStatus.PARTIALLY_ACTED
        EnforcementVerdict.ADVISED -> UserActStatus.USER_ACTION_REQUIRED
        EnforcementVerdict.NOT_ACTED ->
            if (outcomes.any { it.status == ActionStatus.NOT_AUTHORISED }) {
                UserActStatus.USER_ACTION_REQUIRED
            } else {
                UserActStatus.FAILED
            }
    }
}

/**
 * Pure builder of the technically supported ACT options for one scan record
 * (guide §14/§15). Inputs are the live authority/guard facts the caller observed;
 * nothing here talks to Android.
 */
object SupportedUserActions {

    data class ActContext(
        val privilegeLevel: PrivilegeLevel,
        val networkGuardActive: Boolean,
        /** Packages currently scoped into the active tunnel. */
        val guardScopedPackages: Set<String>,
        val userBlockedPackages: Set<String>,
    )

    fun forRecord(record: AppScanRecord, context: ActContext): List<UserActOption> {
        val options = mutableListOf<UserActOption>()
        val enforceable = EnforcementTargets.isEnforceable(record.packageName)
        val deviceOwner = context.privilegeLevel == PrivilegeLevel.DEVICE_OWNER

        // Direct Device Owner responses: only for allowlisted targets under genuine
        // authority, and only for actions policy maps to the actual findings — the
        // coordinator narrows the policy decision to the chosen action and refuses
        // anything the PolicyEngine did not recommend.
        if (deviceOwner && enforceable && record.findings.isNotEmpty()) {
            record.findings
                .flatMap { it.offendingPermissions }
                .distinct()
                .filter { it in PolicyEngine.RUNTIME_DENIABLE_PERMISSIONS }
                .sorted()
                .forEach { options += UserActOption.RestrictPermissionDirect(it) }
            options += UserActOption.SuspendApp
        }

        // Network Guard block: only while the guard is genuinely active AND the package
        // is scoped into the tunnel — for anything else the option simply is not shown.
        if (context.networkGuardActive && record.packageName in context.guardScopedPackages) {
            options += if (record.packageName in context.userBlockedPackages) {
                UserActOption.UnblockNetwork
            } else {
                UserActOption.BlockNetwork
            }
        }

        // Manual path: always available, and the only path that touches arbitrary real
        // apps (guide §17: real apps → settings/manual restriction).
        options += UserActOption.OpenAppSettings
        options += UserActOption.KeepWatching
        if (record.displayStatus != AppDisplayStatus.KNOWN_THREAT_MATCH) {
            options += UserActOption.Dismiss
        }
        return options
    }
}
