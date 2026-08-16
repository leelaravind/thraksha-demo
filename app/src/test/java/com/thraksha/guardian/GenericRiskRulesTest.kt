package com.thraksha.guardian

import com.thraksha.guardian.security.events.Severity
import com.thraksha.guardian.security.inventory.AppType
import com.thraksha.guardian.security.inventory.Capability
import com.thraksha.guardian.security.inventory.ExportedComponents
import com.thraksha.guardian.security.inventory.ObservedApp
import com.thraksha.guardian.security.scan.GenericRiskRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Generic static risk rules (guide §§9, 34): positive, negative, boundary and
 * missing-evidence fixtures for every rule, plus the §26 generalization proof that
 * findings never depend on the package name.
 */
class GenericRiskRulesTest {

    private fun app(
        packageName: String = "com.example.subject",
        requested: Set<String> = emptySet(),
        capabilities: Set<Capability> = emptySet(),
        exported: ExportedComponents = ExportedComponents.NONE,
    ) = ObservedApp(
        packageName = packageName,
        displayName = "Subject",
        appType = AppType.UNKNOWN,
        requestedPermissions = requested,
        grantedPermissions = emptySet(),
        capabilities = capabilities,
        versionName = "1.0",
        firstInstallTime = 0L,
        lastUpdateTime = 0L,
        isDebuggable = false,
        isSystemApp = false,
        exportedComponents = exported,
    )

    private fun ruleIds(app: ObservedApp) = GenericRiskRules.evaluate(app).map { it.ruleId }

    // ---- missing evidence fires nothing ----

    @Test
    fun emptyProfile_producesNoFindings() {
        assertTrue(
            "an app declaring nothing must trip nothing",
            GenericRiskRules.evaluate(app()).isEmpty(),
        )
    }

    @Test
    fun ordinaryProfile_producesNoFindings() {
        val ordinary = app(
            requested = setOf(
                "android.permission.INTERNET",
                "android.permission.ACCESS_NETWORK_STATE",
                "android.permission.POST_NOTIFICATIONS",
                "android.permission.CAMERA",
            ),
        )
        assertTrue(GenericRiskRules.evaluate(ordinary).isEmpty())
    }

    // ---- capability-declaration rules: positive / negative ----

    @Test
    fun accessibilityDeclaration_firesItsRule() {
        val ids = ruleIds(app(capabilities = setOf(Capability.ACCESSIBILITY_SERVICE)))
        assertEquals(listOf("generic-accessibility-service"), ids)
    }

    @Test
    fun deviceAdminDeclaration_firesItsRule() {
        val ids = ruleIds(app(capabilities = setOf(Capability.DEVICE_ADMIN)))
        assertEquals(listOf("generic-device-admin"), ids)
    }

    @Test
    fun notificationListenerDeclaration_firesItsRule() {
        val ids = ruleIds(app(capabilities = setOf(Capability.NOTIFICATION_LISTENER)))
        assertEquals(listOf("generic-notification-listener"), ids)
    }

    @Test
    fun vpnServiceDeclaration_firesItsRule() {
        val ids = ruleIds(app(capabilities = setOf(Capability.VPN_SERVICE)))
        assertEquals(listOf("generic-vpn-service"), ids)
    }

    @Test
    fun broadPackageVisibility_firesItsRule() {
        val ids = ruleIds(
            app(requested = setOf("android.permission.QUERY_ALL_PACKAGES")),
        )
        assertEquals(listOf("generic-broad-package-visibility"), ids)
    }

    @Test
    fun overlayPermission_firesItsRule() {
        val ids = ruleIds(
            app(requested = setOf("android.permission.SYSTEM_ALERT_WINDOW")),
        )
        assertEquals(listOf("generic-overlay-capability"), ids)
    }

    // ---- breadth rule: boundary conditions ----

    private fun sensitiveSet(groups: Int): Set<String> = listOf(
        "android.permission.READ_SMS",
        "android.permission.READ_CONTACTS",
        "android.permission.READ_CALL_LOG",
        "android.permission.ACCESS_FINE_LOCATION",
        "android.permission.CAMERA",
        "android.permission.RECORD_AUDIO",
        "android.permission.READ_MEDIA_IMAGES",
        "android.permission.READ_PHONE_STATE",
    ).take(groups).toSet()

    @Test
    fun fourSensitiveAreas_isBelowBreadthThreshold() {
        assertTrue(
            "4 areas must not fire the breadth rule",
            ruleIds(app(requested = sensitiveSet(4)))
                .none { it == "generic-sensitive-permission-breadth" },
        )
    }

    @Test
    fun fiveSensitiveAreas_firesBreadthRule() {
        assertTrue(
            ruleIds(app(requested = sensitiveSet(5)))
                .contains("generic-sensitive-permission-breadth"),
        )
    }

    @Test
    fun manyPermissionsInOneArea_countOnce() {
        // 3 SMS permissions are ONE sensitive area, not three.
        val ids = ruleIds(
            app(
                requested = setOf(
                    "android.permission.READ_SMS",
                    "android.permission.RECEIVE_SMS",
                    "android.permission.SEND_SMS",
                    "android.permission.READ_CONTACTS",
                    "android.permission.READ_CALL_LOG",
                    "android.permission.CAMERA",
                ),
            ),
        )
        assertTrue(ids.none { it == "generic-sensitive-permission-breadth" })
    }

    // ---- exported-surface rule: boundary conditions ----

    @Test
    fun threeUnprotectedComponents_isBelowSurfaceThreshold() {
        val ids = ruleIds(
            app(exported = ExportedComponents(5, 2, 1, 0, 2, 1, 0)),
        )
        assertTrue(ids.none { it == "generic-unprotected-exported-surface" })
    }

    @Test
    fun fourUnprotectedComponents_firesSurfaceRule() {
        val ids = ruleIds(
            app(exported = ExportedComponents(5, 2, 2, 0, 2, 2, 0)),
        )
        assertEquals(listOf("generic-unprotected-exported-surface"), ids)
    }

    @Test
    fun protectedExportedComponents_doNotCount() {
        // 6 exported services, all permission-guarded → no unprotected surface.
        val ids = ruleIds(
            app(exported = ExportedComponents(1, 6, 0, 0, 0, 0, 0)),
        )
        assertTrue(ids.isEmpty())
    }

    // ---- combination rule ----

    // Phase 8.1 calibration (guide §11): the combination rule still FIRES on declared
    // breadth, but reaches HIGH only when the sensitive areas are currently GRANTED —
    // declared-only breadth is capability exposure (MEDIUM), not live risk.

    @Test
    fun declaredOnlyCombination_firesAtMediumExposure() {
        val findings = GenericRiskRules.evaluate(
            app(
                requested = sensitiveSet(4) + "android.permission.SYSTEM_ALERT_WINDOW",
                capabilities = setOf(Capability.ACCESSIBILITY_SERVICE),
            ),
        )
        val combo = findings.first { it.ruleId == "generic-high-risk-capability-combination" }
        assertEquals(
            "declared-but-ungranted breadth must not look like live high risk",
            Severity.MEDIUM, combo.severity,
        )
        assertTrue(
            "combination evidence must cite the concrete capabilities and their stage",
            combo.evidence.any { it.contains("accessibility") } &&
                combo.evidence.any { it.contains("DECLARED only") },
        )
    }

    @Test
    fun grantedCombination_firesHigh() {
        val granted = sensitiveSet(4)
        val findings = GenericRiskRules.evaluate(
            app(
                requested = granted + "android.permission.SYSTEM_ALERT_WINDOW",
                capabilities = setOf(Capability.ACCESSIBILITY_SERVICE),
            ).copy(grantedPermissions = granted),
        )
        val combo = findings.first { it.ruleId == "generic-high-risk-capability-combination" }
        assertEquals(Severity.HIGH, combo.severity)
        assertTrue(
            combo.evidence.any { it.contains("DECLARED + GRANTED") },
        )
    }

    @Test
    fun oneSpecialAccess_doesNotFireCombination() {
        val ids = ruleIds(
            app(
                requested = sensitiveSet(5),
                capabilities = setOf(Capability.ACCESSIBILITY_SERVICE),
            ),
        )
        assertTrue(ids.none { it == "generic-high-risk-capability-combination" })
    }

    @Test
    fun specialAccessWithNarrowPermissions_doesNotFireCombination() {
        val ids = ruleIds(
            app(
                requested = sensitiveSet(3) + "android.permission.SYSTEM_ALERT_WINDOW",
                capabilities = setOf(Capability.ACCESSIBILITY_SERVICE),
            ),
        )
        assertTrue(ids.none { it == "generic-high-risk-capability-combination" })
    }

    // ---- honesty properties ----

    @Test
    fun noGenericRuleAlone_reachesCriticalSeverity() {
        // Generic rules are review indicators, not malware verdicts: nothing may exceed HIGH.
        val everything = GenericRiskRules.evaluate(
            app(
                requested = sensitiveSet(8) +
                    "android.permission.QUERY_ALL_PACKAGES" +
                    "android.permission.SYSTEM_ALERT_WINDOW",
                capabilities = Capability.entries.toSet(),
                exported = ExportedComponents(9, 9, 9, 9, 9, 9, 9),
            ),
        )
        assertTrue(everything.isNotEmpty())
        assertTrue(
            "generic rules must never exceed HIGH severity",
            everything.all { it.severity <= Severity.HIGH },
        )
        assertTrue(
            "every generic finding must carry concrete evidence",
            everything.all { it.evidence.isNotEmpty() },
        )
    }

    @Test
    fun identicalEvidence_identicalFindings_whateverThePackageName() {
        // §26: a package with VillainCaller-like *name* but clean evidence is clean, and
        // a package with villain-like *evidence* but any other name gets the same findings.
        val evidence: (String) -> ObservedApp = { name ->
            app(
                packageName = name,
                requested = sensitiveSet(5) + "android.permission.QUERY_ALL_PACKAGES",
                capabilities = setOf(Capability.ACCESSIBILITY_SERVICE),
            )
        }
        val villainNamed = GenericRiskRules.evaluate(evidence("com.thraksha.demo.villaincaller"))
        val randomNamed = GenericRiskRules.evaluate(evidence("io.wholesome.notepad"))

        assertEquals(
            villainNamed.map { it.ruleId to it.severity },
            randomNamed.map { it.ruleId to it.severity },
        )

        val cleanButVillainNamed = GenericRiskRules.evaluate(
            app(packageName = "com.thraksha.demo.villaincaller"),
        )
        assertTrue(
            "a clean profile is clean whatever the package is called",
            cleanButVillainNamed.isEmpty(),
        )
    }
}
