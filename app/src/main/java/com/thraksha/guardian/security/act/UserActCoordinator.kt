package com.thraksha.guardian.security.act

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import com.thraksha.guardian.security.enforcement.DeviceOwnerEnforcer
import com.thraksha.guardian.security.events.SecurityEvent
import com.thraksha.guardian.security.events.SecurityEventBus
import com.thraksha.guardian.security.network.NetworkGuard
import com.thraksha.guardian.security.policy.ActionStatus
import com.thraksha.guardian.security.policy.EnforcementResult
import com.thraksha.guardian.security.policy.EnforcementVerdict
import com.thraksha.guardian.security.policy.RecommendedAction
import com.thraksha.guardian.security.policy.SecurityAction
import com.thraksha.guardian.security.policy.SecurityResponseCoordinator
import com.thraksha.guardian.security.scan.AppScanRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Executes a user-selected ACT option (guide §14–§19) through the existing policy and
 * enforcement architecture — never around it:
 *
 *   ACT → re-validate supported options → SecurityResponseCoordinator (PolicyEngine,
 *   DECISION event) → narrow the decision to the chosen action **only if policy
 *   recommended it** → the appropriate executor (Device Owner enforcer / Network Guard
 *   flag / Android settings intent) → verify real state → [UserActResult] →
 *   [SecurityEvent.UserActionPerformed] → audit chain.
 *
 * Honesty invariants:
 *  * an option not in the freshly derived supported list is refused (UNSUPPORTED);
 *  * an action the PolicyEngine does not map to the current findings is refused;
 *  * ACTED / PARTIALLY_ACTED derive exclusively from the enforcer's verified verdict;
 *  * a settings hand-off is USER_ACTION_REQUIRED — neither success nor failure;
 *  * a declined confirmation performs no action and is recorded as CANCELLED.
 */
object UserActCoordinator {

    private const val TAG = "UserActCoordinator"

    /** Cards the user dismissed this session (UI filter only; detection unaffected). */
    private val _dismissedPackages = MutableStateFlow<Set<String>>(emptySet())
    val dismissedPackages: StateFlow<Set<String>> = _dismissedPackages.asStateFlow()

    /** The freshly derived supported options for a record, from live authority state. */
    suspend fun supportedOptions(context: Context, record: AppScanRecord): List<UserActOption> {
        val policyContext = SecurityResponseCoordinator.suspendingContext(context)
        val guardState = NetworkGuard.state.value
        return SupportedUserActions.forRecord(
            record,
            SupportedUserActions.ActContext(
                privilegeLevel = policyContext.privilegeLevel,
                networkGuardActive = guardState is NetworkGuard.State.Active,
                guardScopedPackages = (guardState as? NetworkGuard.State.Active)
                    ?.let { setOf(it.scopedPackage) } ?: emptySet(),
                userBlockedPackages = NetworkGuard.userBlockedPackages.value,
            ),
        )
    }

    suspend fun perform(
        context: Context,
        record: AppScanRecord,
        option: UserActOption,
    ): UserActResult {
        // Re-validate against live state — a stale UI cannot smuggle an unsupported action.
        val supported = supportedOptions(context, record)
        if (supported.none { it.id == option.id }) {
            return publish(
                record, option, UserActStatus.UNSUPPORTED,
                "This action is not technically supported for the current finding and " +
                    "authority state; nothing was executed.",
            )
        }

        val result = when (option) {
            is UserActOption.KeepWatching -> UserActResult(
                option, UserActStatus.KEPT_WATCHING,
                "User chose to keep watching; no action taken, monitoring continues.",
            )

            is UserActOption.Dismiss -> {
                _dismissedPackages.update { it + record.packageName }
                UserActResult(
                    option, UserActStatus.DISMISSED,
                    "Card dismissed for this session. Findings remain in the audit trail.",
                )
            }

            is UserActOption.OpenAppSettings -> openAppSettings(context, record)

            is UserActOption.BlockNetwork -> setNetworkBlock(record, blocked = true)

            is UserActOption.UnblockNetwork -> setNetworkBlock(record, blocked = false)

            is UserActOption.RestrictPermissionDirect ->
                executePrivileged(context, record, SecurityAction.DenyPermission(option.permission), option)

            is UserActOption.SuspendApp ->
                executePrivileged(context, record, SecurityAction.SuspendPackage, option)
        }

        return publish(record, result.option, result.status, result.detail, result.enforcement)
    }

    /** The user declined the confirmation dialog: no action, recorded as CANCELLED. */
    suspend fun recordCancelled(record: AppScanRecord, option: UserActOption): UserActResult =
        publish(
            record, option, UserActStatus.CANCELLED,
            "Confirmation declined by the user; no action was performed.",
        )

    // ---- executors ----

    private fun openAppSettings(context: Context, record: AppScanRecord): UserActResult {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", record.packageName, null),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return if (intent.resolveActivity(context.packageManager) == null) {
            UserActResult(
                UserActOption.OpenAppSettings, UserActStatus.FAILED,
                "No system activity resolves the app-settings intent on this device.",
            )
        } else {
            runCatching { context.startActivity(intent) }.fold(
                onSuccess = {
                    UserActResult(
                        UserActOption.OpenAppSettings, UserActStatus.USER_ACTION_REQUIRED,
                        "Android app settings opened for ${record.packageName}. Completion " +
                            "is in the user's hands and is not claimed as ACTED.",
                    )
                },
                onFailure = {
                    UserActResult(
                        UserActOption.OpenAppSettings, UserActStatus.FAILED,
                        "Settings activity could not be started: ${it.message}",
                    )
                },
            )
        }
    }

    private fun setNetworkBlock(record: AppScanRecord, blocked: Boolean): UserActResult {
        val option = if (blocked) UserActOption.BlockNetwork else UserActOption.UnblockNetwork
        NetworkGuard.setUserBlocked(record.packageName, blocked)
        // Verify the real resulting state — the flag *is* the enforcement state here:
        // the engine's UserBlock branch never constructs a forwarding socket, and each
        // dropped packet is individually audited as BLOCKED.
        val verified = NetworkGuard.isUserBlocked(record.packageName) == blocked
        val guardStillActive = NetworkGuard.state.value is NetworkGuard.State.Active
        return when {
            !verified -> UserActResult(
                option, UserActStatus.FAILED,
                "Network Guard block state could not be verified after the change.",
            )
            !guardStillActive -> UserActResult(
                option, UserActStatus.FAILED,
                "Network Guard is no longer active; the block cannot take effect.",
            )
            blocked -> UserActResult(
                option, UserActStatus.ACTED,
                "Verified: user block armed in the active Network Guard. Every " +
                    "intercepted outbound attempt from ${record.packageName} now takes " +
                    "the drop path (no forwarding socket) and is audited as BLOCKED.",
            )
            else -> UserActResult(
                option, UserActStatus.ACTED,
                "Verified: user block removed; intercepted traffic returns to " +
                    "policy-driven evaluation.",
            )
        }
    }

    /**
     * Direct Device Owner response: the policy decision is computed for the record's
     * findings, then narrowed to exactly the chosen action. If the PolicyEngine did not
     * recommend that action for these findings, the request is refused — user initiation
     * does not bypass policy (guide §15).
     */
    private suspend fun executePrivileged(
        context: Context,
        record: AppScanRecord,
        chosen: SecurityAction,
        option: UserActOption,
    ): UserActResult {
        val decision = SecurityResponseCoordinator.decide(
            context = context,
            packageName = record.packageName,
            appName = record.displayName,
            findings = record.findings,
        )
        val matching: List<RecommendedAction> =
            decision.recommendedActions.filter { it.action == chosen }
        if (matching.isEmpty()) {
            return UserActResult(
                option, UserActStatus.UNSUPPORTED,
                "PolicyEngine does not map '${chosen.label}' to the current findings " +
                    "for ${record.displayName}; the request was refused, not executed.",
            )
        }

        val narrowed = decision.copy(recommendedActions = matching)
        val enforcement = runCatching { DeviceOwnerEnforcer(context).execute(narrowed) }
            .onFailure { Log.e(TAG, "User ACT enforcement failed", it) }
            .getOrNull()
            ?: return UserActResult(
                option, UserActStatus.FAILED,
                "The enforcer threw before any state could be verified; not reporting ACTED.",
            )

        val status = UserActOutcomeMapper.fromEnforcement(
            enforcement.verdict, enforcement.outcomes,
        )
        val detail = enforcement.outcomes.joinToString("; ") { "${it.status}: ${it.detail}" }
        return UserActResult(option, status, detail, enforcement)
    }

    // ---- events ----

    private suspend fun publish(
        record: AppScanRecord,
        option: UserActOption,
        status: UserActStatus,
        detail: String,
        enforcement: EnforcementResult? = null,
    ): UserActResult {
        SecurityEventBus.emit(
            SecurityEvent.UserActionPerformed(
                packageName = record.packageName,
                appName = record.displayName,
                actionLabel = option.label,
                status = status.name,
                detail = detail,
            ),
        )
        return UserActResult(option, status, detail, enforcement)
    }

    /** Test-only reset. */
    internal fun resetForTest() {
        _dismissedPackages.value = emptySet()
    }
}
