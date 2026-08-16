package com.thraksha.guardian.security.enforcement

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import com.thraksha.guardian.security.SecurityCapability
import com.thraksha.guardian.security.ThrakshaDeviceAdminReceiver
import com.thraksha.guardian.security.events.SecurityEvent
import com.thraksha.guardian.security.events.SecurityEventBus
import com.thraksha.guardian.security.policy.ActionOutcome
import com.thraksha.guardian.security.policy.ActionStatus
import com.thraksha.guardian.security.policy.Enforcer
import com.thraksha.guardian.security.policy.EnforcementResult
import com.thraksha.guardian.security.policy.EnforcementVerdict
import com.thraksha.guardian.security.policy.RequiredAuthority
import com.thraksha.guardian.security.policy.SecurityAction
import com.thraksha.guardian.security.policy.SecurityDecision

/**
 * The Device Owner enforcement path — the **only** class in the app that calls
 * DevicePolicyManager mutation APIs. RuleEngine, PolicyEngine, SecurityAuditEngine and
 * the UI never do (guide §5.2).
 *
 * Safety order, per action (guide sequence):
 *  1. verify the target package is in the explicit demo allowlist — refused otherwise,
 *     even with full authority;
 *  2. verify genuine Device Owner via [SecurityCapability];
 *  3. record the current OS state;
 *  4. execute the supported API call;
 *  5. **query the actual resulting OS state**;
 *  6. report success only if the queried state confirms it;
 *  7. emit an [SecurityEvent.ActionTaken] carrying the verified result, which the
 *     Application collector persists into the encrypted audit chain.
 *
 * Implemented containment (guide §5.3–§5.5): runtime-permission denial and package
 * suspension ("quarantine" == suspension, nothing more is claimed). Explicitly absent:
 * wipeData, resetPassword, lockNow, reboot, setApplicationHidden, user restrictions,
 * uninstall — none of these APIs appear anywhere in this file or the app.
 *
 * Every action is reversible via [restore] (guide §5.6).
 */
class DeviceOwnerEnforcer(context: Context) : Enforcer {

    private val appContext = context.applicationContext
    private val dpm: DevicePolicyManager =
        ThrakshaDeviceAdminReceiver.getDevicePolicyManager(appContext)
    private val admin = ThrakshaDeviceAdminReceiver.getComponentName(appContext)

    override suspend fun execute(decision: SecurityDecision): EnforcementResult {
        // 1. Target allowlist — checked before authority so an out-of-scope package is
        // refused even on a provisioned device.
        if (!EnforcementTargets.isEnforceable(decision.packageName)) {
            return refuse(decision, EnforcementTargets.refusalReason(decision.packageName))
        }

        // 2. Genuine authority, verified at execution time (not assumed from the decision).
        if (!SecurityCapability.canEnforce(appContext)) {
            return notAuthorised(decision)
        }

        val outcomes = decision.recommendedActions.map { recommended ->
            when (val action = recommended.action) {
                is SecurityAction.DenyPermission ->
                    denyPermission(decision.packageName, action)

                is SecurityAction.SuspendPackage ->
                    setSuspended(decision.packageName, suspended = true)

                is SecurityAction.BlockNetwork -> ActionOutcome(
                    action = action,
                    status = ActionStatus.UNSUPPORTED,
                    detail = "Network blocking is executed by the Network Guard's packet " +
                        "path, not by DevicePolicyManager — this enforcer cannot perform it.",
                )

                // Advisory-flavoured actions stay advisory even under Full Power.
                is SecurityAction.OpenAppSettings, is SecurityAction.NoAction -> ActionOutcome(
                    action = action,
                    status = ActionStatus.ADVISED,
                    detail = "User-facing recommendation; not an automatic OS action.",
                )
            }
        }

        val result = EnforcementResult(
            decisionId = decision.decisionId,
            packageName = decision.packageName,
            appName = decision.appName,
            enforcer = NAME,
            outcomes = outcomes,
            verdict = verdictOf(outcomes),
        )
        publish(result)
        return result
    }

    /**
     * Reverses every demo containment on [packageName]: unsuspends it and returns the
     * demo-denied runtime permissions to DEFAULT (user-controllable) state. Each step is
     * verified against actual OS state and audited, like enforcement itself.
     */
    suspend fun restore(packageName: String, appName: String): EnforcementResult {
        val decisionId = "restore/$packageName"
        if (!EnforcementTargets.isEnforceable(packageName)) {
            val outcome = ActionOutcome(
                action = SecurityAction.NoAction,
                status = ActionStatus.NOT_AUTHORISED,
                detail = EnforcementTargets.refusalReason(packageName),
            )
            return EnforcementResult(
                decisionId, packageName, appName, NAME, listOf(outcome),
                EnforcementVerdict.NOT_ACTED,
            )
        }
        if (!SecurityCapability.canEnforce(appContext)) {
            val outcome = ActionOutcome(
                action = SecurityAction.NoAction,
                status = ActionStatus.NOT_AUTHORISED,
                detail = "Restore requires Device Owner authority.",
            )
            return EnforcementResult(
                decisionId, packageName, appName, NAME, listOf(outcome),
                EnforcementVerdict.NOT_ACTED,
            )
        }

        val outcomes = mutableListOf<ActionOutcome>()
        outcomes += setSuspended(packageName, suspended = false)
        RESTORABLE_PERMISSIONS.forEach { permission ->
            outcomes += resetPermissionToDefault(packageName, permission)
        }

        val result = EnforcementResult(
            decisionId = decisionId,
            packageName = packageName,
            appName = appName,
            enforcer = NAME,
            outcomes = outcomes,
            verdict = verdictOf(outcomes),
        )
        publish(result, restoring = true)
        return result
    }

    // ---- individual verified actions ----

    private fun denyPermission(
        packageName: String,
        action: SecurityAction.DenyPermission,
    ): ActionOutcome = runCatching {
        val before = dpm.getPermissionGrantState(admin, packageName, action.permission)
        if (before == DevicePolicyManager.PERMISSION_GRANT_STATE_DENIED) {
            return@runCatching ActionOutcome(
                action, ActionStatus.ALREADY_IN_STATE,
                "Verified OS state: ${action.permission} already DENIED for $packageName.",
            )
        }
        val accepted = dpm.setPermissionGrantState(
            admin, packageName, action.permission,
            DevicePolicyManager.PERMISSION_GRANT_STATE_DENIED,
        )
        // The API returning is NOT success — re-query the actual state (guide §1.3).
        val after = dpm.getPermissionGrantState(admin, packageName, action.permission)
        if (accepted && after == DevicePolicyManager.PERMISSION_GRANT_STATE_DENIED) {
            ActionOutcome(
                action, ActionStatus.SUCCEEDED,
                "Verified OS state: ${action.permission} now DENIED for $packageName " +
                    "(was ${grantStateName(before)}).",
            )
        } else {
            ActionOutcome(
                action, ActionStatus.FAILED,
                "setPermissionGrantState accepted=$accepted but verified state is " +
                    "${grantStateName(after)} — not reporting success.",
            )
        }
    }.getOrElse {
        Log.e(TAG, "denyPermission failed for $packageName/${action.permission}", it)
        ActionOutcome(action, ActionStatus.FAILED, "Exception: ${it.message}")
    }

    private fun setSuspended(packageName: String, suspended: Boolean): ActionOutcome {
        val action = SecurityAction.SuspendPackage
        return runCatching {
            val before = isPackageSuspended(packageName)
            if (before == suspended) {
                return@runCatching ActionOutcome(
                    action,
                    ActionStatus.ALREADY_IN_STATE,
                    "Verified OS state: $packageName suspended=$before already.",
                )
            }
            val failedPackages =
                dpm.setPackagesSuspended(admin, arrayOf(packageName), suspended)
            val after = isPackageSuspended(packageName)
            if (failedPackages.isEmpty() && after == suspended) {
                ActionOutcome(
                    action, ActionStatus.SUCCEEDED,
                    "Verified OS state: $packageName suspended=$after " +
                        (if (suspended) "(quarantined by package suspension)."
                        else "(suspension lifted)."),
                )
            } else {
                ActionOutcome(
                    action, ActionStatus.FAILED,
                    "setPackagesSuspended failedPackages=${failedPackages.toList()}, " +
                        "verified suspended=$after (wanted $suspended) — not reporting success.",
                )
            }
        }.getOrElse {
            Log.e(TAG, "setSuspended($suspended) failed for $packageName", it)
            ActionOutcome(action, ActionStatus.FAILED, "Exception: ${it.message}")
        }
    }

    private fun resetPermissionToDefault(packageName: String, permission: String): ActionOutcome {
        val action = SecurityAction.DenyPermission(permission)
        return runCatching {
            val before = dpm.getPermissionGrantState(admin, packageName, permission)
            if (before == DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT) {
                return@runCatching ActionOutcome(
                    action, ActionStatus.ALREADY_IN_STATE,
                    "Verified OS state: $permission already DEFAULT for $packageName.",
                )
            }
            dpm.setPermissionGrantState(
                admin, packageName, permission,
                DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT,
            )
            val after = dpm.getPermissionGrantState(admin, packageName, permission)
            if (after == DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT) {
                ActionOutcome(
                    action, ActionStatus.SUCCEEDED,
                    "Verified OS state: $permission restored to DEFAULT for $packageName.",
                )
            } else {
                ActionOutcome(
                    action, ActionStatus.FAILED,
                    "Verified state after reset is ${grantStateName(after)} — not DEFAULT.",
                )
            }
        }.getOrElse {
            ActionOutcome(action, ActionStatus.FAILED, "Exception: ${it.message}")
        }
    }

    /** Ground truth for suspension, via supported package APIs (guide §5.7). */
    private fun isPackageSuspended(packageName: String): Boolean {
        val pm = appContext.packageManager
        return runCatching { pm.isPackageSuspended(packageName) }.getOrElse {
            val info = pm.getApplicationInfo(packageName, 0)
            (info.flags and ApplicationInfo.FLAG_SUSPENDED) != 0
        }
    }

    // ---- refusals, verdicts, events ----

    private suspend fun refuse(decision: SecurityDecision, reason: String): EnforcementResult {
        Log.w(TAG, "Refusing enforcement: $reason")
        val outcomes = decision.recommendedActions.map {
            ActionOutcome(it.action, ActionStatus.NOT_AUTHORISED, reason)
        }.ifEmpty {
            listOf(ActionOutcome(SecurityAction.NoAction, ActionStatus.NOT_AUTHORISED, reason))
        }
        val result = EnforcementResult(
            decision.decisionId, decision.packageName, decision.appName, NAME,
            outcomes, EnforcementVerdict.NOT_ACTED,
        )
        publish(result)
        return result
    }

    private suspend fun notAuthorised(decision: SecurityDecision): EnforcementResult {
        val detail = "Thraksha is not Device Owner; privileged containment cannot run."
        val outcomes = decision.recommendedActions.map {
            ActionOutcome(it.action, ActionStatus.NOT_AUTHORISED, detail)
        }
        val result = EnforcementResult(
            decision.decisionId, decision.packageName, decision.appName, NAME,
            outcomes, EnforcementVerdict.NOT_ACTED,
        )
        publish(result)
        return result
    }

    /**
     * Aggregate honestly (guide §5.8): ACTED only when every *attempted* privileged
     * action is verified in-state; a mix is PARTIALLY_ACTED; nothing verified is
     * NOT_ACTED. Advisory-flavoured outcomes don't count as attempts.
     */
    private fun verdictOf(outcomes: List<ActionOutcome>): EnforcementVerdict {
        val attempted = outcomes.filter {
            it.action.requiredAuthority == RequiredAuthority.DEVICE_OWNER
        }
        if (attempted.isEmpty()) return EnforcementVerdict.NOT_ACTED
        val good = attempted.count {
            it.status == ActionStatus.SUCCEEDED || it.status == ActionStatus.ALREADY_IN_STATE
        }
        return when {
            good == attempted.size -> EnforcementVerdict.ACTED
            good > 0 -> EnforcementVerdict.PARTIALLY_ACTED
            else -> EnforcementVerdict.NOT_ACTED
        }
    }

    /** Every action and its verified result goes through the bus into the audit chain. */
    private suspend fun publish(result: EnforcementResult, restoring: Boolean = false) {
        result.outcomes.forEach { outcome ->
            SecurityEventBus.emit(
                SecurityEvent.ActionTaken(
                    action = (if (restoring) "RESTORE " else "") + outcome.action.label,
                    target = "${result.appName} (${result.packageName})",
                    result = "${outcome.status}: ${outcome.detail}",
                ),
            )
        }
    }

    private fun grantStateName(state: Int): String = when (state) {
        DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT -> "DEFAULT"
        DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED -> "GRANTED"
        DevicePolicyManager.PERMISSION_GRANT_STATE_DENIED -> "DENIED"
        else -> "UNKNOWN($state)"
    }

    companion object {
        const val NAME = "DeviceOwnerEnforcer"
        private const val TAG = "DeviceOwnerEnforcer"

        /** The demo permissions [restore] returns to DEFAULT. */
        val RESTORABLE_PERMISSIONS = listOf(
            "android.permission.READ_MEDIA_IMAGES",
            "android.permission.ACCESS_FINE_LOCATION",
        )
    }
}
