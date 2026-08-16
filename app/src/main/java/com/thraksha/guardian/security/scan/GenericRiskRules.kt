package com.thraksha.guardian.security.scan

import com.thraksha.guardian.security.engine.Finding
import com.thraksha.guardian.security.engine.FindingSource
import com.thraksha.guardian.security.events.Severity
import com.thraksha.guardian.security.inventory.Capability
import com.thraksha.guardian.security.inventory.GrantState
import com.thraksha.guardian.security.inventory.ObservedApp

/**
 * Generic static risk rules (Phase 8, guide §9): deterministic, evidence-based checks
 * that apply to ANY app without knowing its category. Pure — no Android types — so every
 * rule is unit-testable off-device.
 *
 * Ground rules, enforced by construction:
 *
 *  * every finding cites the concrete manifest-declared evidence it rests on;
 *  * a finding means **REVIEW / capability exposure**, never "malware" — severities are
 *    LOW/MEDIUM except the capability-combination rule, which reaches HIGH only when the
 *    combined sensitive areas are *currently granted* (Phase 8.1 calibration, guide §11:
 *    declared-only breadth is exposure, not live risk), and no generic rule alone can
 *    produce a KNOWN THREAT verdict (the classifier reserves that for STRONG-tier
 *    threat-intelligence matches);
 *  * missing evidence never fires a rule — absence of a permission/component is treated
 *    as absence of the capability, not as suspicion;
 *  * nothing here reads a package name: two apps with identical declared evidence get
 *    identical findings, whatever they are called (guide §26).
 *
 * Scope note (guide §19): the scan engine applies these rules to **user-installed apps
 * only**. System-image packages legitimately declare platform-privileged capabilities;
 * flagging them for it would produce noise, not signal. System apps remain fully
 * inventoried, fingerprinted and threat-intelligence-matched — they are not auto-trusted,
 * they are just not profiled by rules written for user-installable software.
 */
object GenericRiskRules {

    // Sensitive permission groups used by the breadth/combination rules. A group counts
    // once however many of its member permissions are requested.
    private val SENSITIVE_GROUPS: Map<String, Set<String>> = mapOf(
        "SMS" to setOf(
            "android.permission.READ_SMS",
            "android.permission.RECEIVE_SMS",
            "android.permission.SEND_SMS",
        ),
        "CONTACTS" to setOf(
            "android.permission.READ_CONTACTS",
            "android.permission.WRITE_CONTACTS",
        ),
        "CALL_LOG" to setOf(
            "android.permission.READ_CALL_LOG",
            "android.permission.WRITE_CALL_LOG",
        ),
        "PHONE" to setOf(
            "android.permission.CALL_PHONE",
            "android.permission.ANSWER_PHONE_CALLS",
            "android.permission.READ_PHONE_NUMBERS",
            "android.permission.READ_PHONE_STATE",
        ),
        "LOCATION" to setOf(
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.ACCESS_BACKGROUND_LOCATION",
        ),
        "CAMERA" to setOf("android.permission.CAMERA"),
        "MICROPHONE" to setOf("android.permission.RECORD_AUDIO"),
        "MEDIA_STORAGE" to setOf(
            "android.permission.READ_MEDIA_IMAGES",
            "android.permission.READ_MEDIA_VIDEO",
            "android.permission.READ_MEDIA_AUDIO",
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.MANAGE_EXTERNAL_STORAGE",
        ),
    )

    private const val PERMISSION_QUERY_ALL = "android.permission.QUERY_ALL_PACKAGES"
    private const val PERMISSION_OVERLAY = "android.permission.SYSTEM_ALERT_WINDOW"

    /** Groups threshold for the breadth rule. 5+ distinct sensitive areas is unusual. */
    private const val BREADTH_THRESHOLD = 5

    /** Unprotected exported non-activity components before the surface rule fires. */
    private const val EXPORT_SURFACE_THRESHOLD = 4

    /** Special-access capabilities + sensitive breadth needed for the combination rule. */
    private const val COMBINATION_MIN_SPECIAL_ACCESS = 2
    private const val COMBINATION_MIN_GROUPS = 4

    fun evaluate(app: ObservedApp): List<Finding> {
        val findings = mutableListOf<Finding>()

        if (Capability.ACCESSIBILITY_SERVICE in app.capabilities) {
            findings += finding(
                app, "generic-accessibility-service", Severity.MEDIUM, 60,
                reason = "Declares an accessibility service — capability exposure: once " +
                    "enabled by the user it can read screen content and inject input " +
                    "across apps",
                evidence = listOf(
                    "declares a service requiring android.permission.BIND_ACCESSIBILITY_SERVICE",
                ) + stateEvidence("accessibility access", app.specialAccess.accessibility),
            )
        }
        if (Capability.DEVICE_ADMIN in app.capabilities) {
            findings += finding(
                app, "generic-device-admin", Severity.MEDIUM, 55,
                reason = "Declares a device-admin receiver — capability exposure: can " +
                    "hold device policies if activated by the user",
                evidence = listOf(
                    "declares a receiver requiring android.permission.BIND_DEVICE_ADMIN",
                ) + stateEvidence("device admin", app.specialAccess.deviceAdmin),
            )
        }
        if (Capability.NOTIFICATION_LISTENER in app.capabilities) {
            findings += finding(
                app, "generic-notification-listener", Severity.MEDIUM, 55,
                reason = "Declares a notification listener — capability exposure: once " +
                    "enabled it can read the content of all notifications",
                evidence = listOf(
                    "declares a service requiring " +
                        "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE",
                ) + stateEvidence("notification access", app.specialAccess.notificationListener),
            )
        }
        if (Capability.VPN_SERVICE in app.capabilities) {
            findings += finding(
                app, "generic-vpn-service", Severity.MEDIUM, 50,
                reason = "Declares a VpnService — with user consent it can route and " +
                    "observe device network traffic",
                evidence = listOf(
                    "declares a service requiring android.permission.BIND_VPN_SERVICE",
                ),
            )
        }
        if (app.requests(PERMISSION_QUERY_ALL)) {
            findings += finding(
                app, "generic-broad-package-visibility", Severity.MEDIUM, 55,
                reason = "Requests visibility of every installed application " +
                    "(QUERY_ALL_PACKAGES)",
                evidence = listOf("declares $PERMISSION_QUERY_ALL"),
                offending = listOf(PERMISSION_QUERY_ALL),
            )
        }
        if (app.requests(PERMISSION_OVERLAY)) {
            findings += finding(
                app, "generic-overlay-capability", Severity.MEDIUM, 50,
                reason = "Requests draw-over-other-apps capability " +
                    "(SYSTEM_ALERT_WINDOW), usable for overlay attacks as well as " +
                    "legitimate floating UI",
                evidence = listOf("declares $PERMISSION_OVERLAY") +
                    stateEvidence("overlay access", app.specialAccess.overlay),
                offending = listOf(PERMISSION_OVERLAY),
            )
        }

        val groups = sensitiveGroupsRequested(app)
        if (groups.size >= BREADTH_THRESHOLD) {
            findings += finding(
                app, "generic-sensitive-permission-breadth", Severity.MEDIUM, 60,
                reason = "Requests an unusually broad sensitive-permission footprint " +
                    "(${groups.size} distinct sensitive areas)",
                evidence = groups.map { (group, permissions) ->
                    "$group: ${permissions.sorted().joinToString()}"
                }.sorted(),
                offending = groups.values.flatten().sorted(),
            )
        }

        if (app.exportedComponents.unprotectedNonActivity >= EXPORT_SURFACE_THRESHOLD) {
            val c = app.exportedComponents
            findings += finding(
                app, "generic-unprotected-exported-surface", Severity.LOW, 40,
                reason = "Exposes ${c.unprotectedNonActivity} exported non-activity " +
                    "components without permission protection — attack surface worth review",
                evidence = listOf(
                    "${c.unprotectedServices} exported service(s) without a permission guard",
                    "${c.unprotectedReceivers} exported receiver(s) without a permission guard",
                    "${c.unprotectedProviders} exported provider(s) without a permission guard",
                ),
            )
        }

        val specialAccess = specialAccessCapabilities(app)
        if (specialAccess.size >= COMBINATION_MIN_SPECIAL_ACCESS &&
            groups.size >= COMBINATION_MIN_GROUPS
        ) {
            // Phase 8.1 calibration (guide §10/§11): the combination rule grades on
            // evidence stage, not on declarations alone. Declared-but-ungranted
            // capability breadth is *capability exposure* (MEDIUM, review); the HIGH
            // grade requires the exposure to be live — the sensitive areas actually
            // granted right now. General and package-name-blind, like every rule here.
            val grantedGroups = groups.filterValues { permissions ->
                permissions.any { app.isGranted(it) }
            }
            val live = grantedGroups.size >= COMBINATION_MIN_GROUPS
            findings += finding(
                app, "generic-high-risk-capability-combination",
                severity = if (live) Severity.HIGH else Severity.MEDIUM,
                confidence = if (live) 75 else 60,
                reason = if (live) {
                    "High live capability exposure: ${specialAccess.size} special-access " +
                        "capabilities combined with ${grantedGroups.size} sensitive " +
                        "permission areas currently granted — close review warranted"
                } else {
                    "Broad declared capability exposure: ${specialAccess.size} " +
                        "special-access capabilities and ${groups.size} sensitive areas " +
                        "declared, but only ${grantedGroups.size} currently granted — " +
                        "exposure to keep watching, not observed behavior"
                },
                evidence = specialAccess.map { "special access: $it" }.sorted() +
                    groups.keys.sorted().map { group ->
                        val granted = group in grantedGroups
                        "sensitive area: $group [" +
                            (if (granted) "DECLARED + GRANTED" else "DECLARED only") + "]"
                    },
            )
        }

        return findings.sortedBy { it.ruleId }
    }

    /** The sensitive groups this app requests at least one member of. */
    private fun sensitiveGroupsRequested(app: ObservedApp): Map<String, List<String>> =
        SENSITIVE_GROUPS.mapNotNull { (group, permissions) ->
            val requested = permissions.filter { app.requests(it) }
            if (requested.isEmpty()) null else group to requested
        }.toMap()

    /**
     * Evidence line stating a special access's live enablement state, when it was
     * actually determined. UNKNOWN is stated as unknown, never suppressed into a claim.
     */
    private fun stateEvidence(what: String, state: GrantState): List<String> = when (state) {
        GrantState.ENABLED -> listOf("current $what state: ENABLED by the user")
        GrantState.DISABLED -> listOf("current $what state: not enabled")
        GrantState.UNKNOWN, GrantState.NOT_APPLICABLE,
        GrantState.GRANTED, GrantState.DENIED,
        -> emptyList()
    }

    /** Special-access capabilities: privileged channels a user must explicitly enable. */
    private fun specialAccessCapabilities(app: ObservedApp): List<String> = buildList {
        if (Capability.ACCESSIBILITY_SERVICE in app.capabilities) add("accessibility service")
        if (Capability.DEVICE_ADMIN in app.capabilities) add("device admin")
        if (Capability.NOTIFICATION_LISTENER in app.capabilities) add("notification listener")
        if (app.requests(PERMISSION_OVERLAY)) add("overlay (SYSTEM_ALERT_WINDOW)")
        if (app.requests(PERMISSION_QUERY_ALL)) add("broad package visibility")
    }

    private fun finding(
        app: ObservedApp,
        ruleId: String,
        severity: Severity,
        confidence: Int,
        reason: String,
        evidence: List<String>,
        offending: List<String> = emptyList(),
    ) = Finding(
        ruleId = ruleId,
        ruleName = ruleId.removePrefix("generic-").replace('-', ' ')
            .replaceFirstChar { it.uppercase() },
        packageName = app.packageName,
        appName = app.displayName,
        appType = app.appType,
        severity = severity,
        confidence = confidence,
        reason = reason,
        evidence = evidence,
        offendingPermissions = offending,
        source = FindingSource.GENERIC_RULE,
    )
}
