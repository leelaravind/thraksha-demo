package com.thraksha.guardian.security.engine

import com.thraksha.guardian.security.baseline.AppBaseline
import com.thraksha.guardian.security.events.Severity
import com.thraksha.guardian.security.inventory.ObservedApp
import com.thraksha.guardian.security.rulepack.Rule
import com.thraksha.guardian.security.rulepack.Rulepack
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * Evaluates a **verified** [Rulepack] against an [ObservedApp] and its [AppBaseline].
 *
 * The engine holds no Android types, no Compose, no database and no `DevicePolicyManager`.
 * It is a pure function from (rules, observation, baseline) to findings, which is what
 * lets it be unit-tested off-device and what keeps detection separate from enforcement.
 *
 * Rule behaviour is driven entirely by the signed `params` block, so changing detection
 * means editing and re-signing the rulepack, not editing Kotlin. Two checks are
 * implemented:
 *
 *  * `PERMISSION_NOT_IN_BASELINE` — the app requests a permission the rule targets, and
 *    that permission is not in its app type's baseline.
 *  * `PROFILE_MISMATCH_COUNT` — an aggregate that fires when at least `minimumMismatches`
 *    of the above already fired for this app.
 *
 * A rule naming any other `check` is **skipped**, not guessed at. That is deliberate: the
 * pack still carries the original generic catalogue (`sim-swap`, `c2-beaconing`, …) as
 * `enabled: false` with no `check`, and no detector exists for them. Nothing may appear to
 * have been evaluated when it was not.
 */
class RuleEngine(private val rulepack: Rulepack) {

    fun evaluate(app: ObservedApp, baseline: AppBaseline): List<Finding> {
        val applicable = rulepack.rules.filter { it.enabled && it.appliesTo(app) }

        val mismatches = applicable
            .filter { it.check == CHECK_PERMISSION }
            .mapNotNull { rule -> evaluatePermissionRule(rule, app, baseline) }

        val aggregates = applicable
            .filter { it.check == CHECK_PROFILE }
            .mapNotNull { rule -> evaluateProfileRule(rule, app, mismatches) }

        return mismatches + aggregates
    }

    /**
     * Fires when the app requests one of the rule's target permissions **and** that
     * permission is absent from the baseline for its type.
     *
     * The baseline check is what makes the verdict role-aware rather than a blocklist: a
     * type whose baseline legitimately includes the permission produces no finding from
     * the very same rule and the very same observation.
     */
    private fun evaluatePermissionRule(
        rule: Rule,
        app: ObservedApp,
        baseline: AppBaseline,
    ): Finding? {
        val offending = rule.stringList(PARAM_PERMISSIONS)
            .filter { app.requests(it) && !baseline.isExpected(it) }
            .sorted()

        if (offending.isEmpty()) return null

        val evidence = offending.map { permission ->
            val grantState = if (app.isGranted(permission)) "granted" else "requested, not granted"
            "declares $permission ($grantState)"
        } + "baseline for ${app.appType.name} does not include ${offending.joinToString(", ")}"

        return rule.toFinding(app, evidence, offendingPermissions = offending)
    }

    /** Fires when enough independent role mismatches have already been found for this app. */
    private fun evaluateProfileRule(
        rule: Rule,
        app: ObservedApp,
        mismatches: List<Finding>,
    ): Finding? {
        val minimum = rule.int(PARAM_MINIMUM_MISMATCHES, default = 2)
        if (mismatches.size < minimum) return null

        val evidence = listOf(
            "${mismatches.size} distinct capability mismatches for app type ${app.appType.name} " +
                "(threshold $minimum)",
        ) + mismatches.map { "fired: ${it.ruleId}" }

        return rule.toFinding(app, evidence)
    }

    private fun Rule.toFinding(
        app: ObservedApp,
        evidence: List<String>,
        offendingPermissions: List<String> = emptyList(),
    ) = Finding(
        ruleId = id,
        ruleName = name,
        packageName = app.packageName,
        appName = app.displayName,
        appType = app.appType,
        severity = parseSeverity(severity),
        confidence = int(PARAM_CONFIDENCE, default = DEFAULT_CONFIDENCE).coerceIn(0, 100),
        reason = string(PARAM_REASON) ?: name,
        evidence = evidence,
        offendingPermissions = offendingPermissions,
    )

    /** A rule with no `appTypes` list applies to every type; otherwise it must name this one. */
    private fun Rule.appliesTo(app: ObservedApp): Boolean {
        val types = stringList(PARAM_APP_TYPES)
        return types.isEmpty() || types.contains(app.appType.name)
    }

    private val Rule.check: String? get() = string(PARAM_CHECK)

    private fun Rule.string(key: String): String? =
        runCatching { params[key]?.jsonPrimitive?.content }.getOrNull()

    private fun Rule.int(key: String, default: Int): Int =
        runCatching { params[key]?.jsonPrimitive?.content?.toInt() }.getOrNull() ?: default

    private fun Rule.stringList(key: String): List<String> = runCatching {
        params[key]?.jsonArray?.mapNotNull { (it as? JsonPrimitive)?.content }.orEmpty()
    }.getOrDefault(emptyList())

    private fun parseSeverity(raw: String): Severity =
        runCatching { Severity.valueOf(raw.uppercase()) }.getOrDefault(Severity.MEDIUM)

    private companion object {
        const val CHECK_PERMISSION = "PERMISSION_NOT_IN_BASELINE"
        const val CHECK_PROFILE = "PROFILE_MISMATCH_COUNT"

        const val PARAM_CHECK = "check"
        const val PARAM_APP_TYPES = "appTypes"
        const val PARAM_PERMISSIONS = "permissions"
        const val PARAM_CONFIDENCE = "confidence"
        const val PARAM_REASON = "reason"
        const val PARAM_MINIMUM_MISMATCHES = "minimumMismatches"

        const val DEFAULT_CONFIDENCE = 50
    }
}
