package com.thraksha.guardian.security.enforcement

import com.thraksha.guardian.security.inventory.DemoAppRegistry

/**
 * The explicit allowlist of packages any privileged enforcement may ever touch.
 *
 * This is a deliberate safety boundary for the controlled demo (guide §5.4): containment
 * is scoped to the one controlled sample. Guardian itself, GoodCaller, the launcher,
 * system UI, settings, the dialer and every other package are refused *by the enforcer*,
 * before any authority check — an out-of-scope target is rejected even on a fully
 * provisioned Device Owner build.
 */
object EnforcementTargets {

    private val enforceable: Set<String> = setOf(DemoAppRegistry.VILLAIN_CALLER)

    fun isEnforceable(packageName: String): Boolean = packageName in enforceable

    /** Human-readable refusal reason for audit rows and results. */
    fun refusalReason(packageName: String): String =
        "Package $packageName is not in the demo enforcement allowlist " +
            "(${enforceable.joinToString()}); privileged action is refused by design."
}
