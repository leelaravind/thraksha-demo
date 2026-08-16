package com.thraksha.guardian

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thraksha.guardian.data.config.ExecutionMode
import com.thraksha.guardian.security.PrivilegeLevel
import com.thraksha.guardian.security.SecurityCapability
import com.thraksha.guardian.security.enforcement.DeviceOwnerEnforcer
import com.thraksha.guardian.security.engine.Finding
import com.thraksha.guardian.security.events.Severity
import com.thraksha.guardian.security.inventory.AppInventory
import com.thraksha.guardian.security.inventory.AppType
import com.thraksha.guardian.security.inventory.DemoAppRegistry
import com.thraksha.guardian.security.policy.ActionStatus
import com.thraksha.guardian.security.policy.EnforcementVerdict
import com.thraksha.guardian.security.policy.PolicyEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Safety tests for the Device Owner enforcement boundary (guide §5.13).
 *
 * The refusal tests run in ANY privilege state — the allowlist is checked before
 * authority, so a bad target is refused even on a provisioned device. The containment
 * round-trip tests require genuine Device Owner and skip otherwise.
 */
@RunWith(AndroidJUnit4::class)
class DeviceOwnerEnforcementInstrumentedTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun isDeviceOwner(): Boolean = SecurityCapability.canEnforce(context)

    private fun decoysInstalled(): Boolean =
        AppInventory(context.packageManager).inventory().size == DemoAppRegistry.demoPackages.size

    private fun finding(pkg: String, appName: String) = Finding(
        ruleId = "caller-high-risk-profile",
        ruleName = "compound",
        packageName = pkg,
        appName = appName,
        appType = AppType.CALLER_ID,
        severity = Severity.CRITICAL,
        confidence = 95,
        reason = "test compound finding",
        evidence = listOf("test"),
    )

    private fun mediaFinding(pkg: String, appName: String) = Finding(
        ruleId = "caller-media-permission-mismatch",
        ruleName = "media",
        packageName = pkg,
        appName = appName,
        appType = AppType.CALLER_ID,
        severity = Severity.HIGH,
        confidence = 92,
        reason = "test media finding",
        evidence = listOf("test"),
        offendingPermissions = listOf("android.permission.READ_MEDIA_IMAGES"),
    )

    private fun actDecision(pkg: String, appName: String) = PolicyEngine.decide(
        packageName = pkg,
        appName = appName,
        findings = listOf(mediaFinding(pkg, appName), finding(pkg, appName)),
        privilegeLevel = PrivilegeLevel.DEVICE_OWNER,
        executionMode = ExecutionMode.AUTO_DEFEND,
    )

    // ---- Refusals: valid in every privilege state ----

    @Test
    fun goodCaller_isRefusedAsTarget_regardlessOfAuthority() = runBlocking {
        val result = DeviceOwnerEnforcer(context)
            .execute(actDecision(DemoAppRegistry.GOOD_CALLER, "GoodCaller"))

        assertEquals(EnforcementVerdict.NOT_ACTED, result.verdict)
        assertTrue(result.outcomes.all { it.status == ActionStatus.NOT_AUTHORISED })

        // OS ground truth: GoodCaller untouched.
        val info = context.packageManager.getApplicationInfo(DemoAppRegistry.GOOD_CALLER, 0)
        assertTrue(info.enabled)
        assertFalse(
            runCatching {
                context.packageManager.isPackageSuspended(DemoAppRegistry.GOOD_CALLER)
            }.getOrDefault(false),
        )
    }

    @Test
    fun guardian_cannotTargetItself() = runBlocking {
        val result = DeviceOwnerEnforcer(context)
            .execute(actDecision("com.thraksha.guardian", "Thraksha Guardian"))
        assertEquals(EnforcementVerdict.NOT_ACTED, result.verdict)
        assertTrue(result.outcomes.none { it.status == ActionStatus.SUCCEEDED })
    }

    @Test
    fun arbitraryPackage_isRefused() = runBlocking {
        val result = DeviceOwnerEnforcer(context)
            .execute(actDecision("com.android.settings", "Settings"))
        assertEquals(EnforcementVerdict.NOT_ACTED, result.verdict)
        assertTrue(result.outcomes.none { it.status == ActionStatus.SUCCEEDED })
    }

    @Test
    fun restore_refusesNonAllowlistedTargets() = runBlocking {
        val result = DeviceOwnerEnforcer(context)
            .restore(DemoAppRegistry.GOOD_CALLER, "GoodCaller")
        assertEquals(EnforcementVerdict.NOT_ACTED, result.verdict)
    }

    // ---- Without Device Owner: nothing executes ----

    @Test
    fun withoutDeviceOwner_villainDecisionIsNotAuthorised_andNothingChanges() = runBlocking {
        assumeFalse("requires a NON-Device-Owner state", isDeviceOwner())
        assumeTrue("decoy apps are not installed", decoysInstalled())

        val result = DeviceOwnerEnforcer(context)
            .execute(actDecision(DemoAppRegistry.VILLAIN_CALLER, "VillainCaller"))

        assertEquals(EnforcementVerdict.NOT_ACTED, result.verdict)
        assertTrue(result.outcomes.none { it.status == ActionStatus.SUCCEEDED })

        val info = context.packageManager.getApplicationInfo(DemoAppRegistry.VILLAIN_CALLER, 0)
        assertTrue("VillainCaller must remain enabled", info.enabled)
    }

    // ---- With Device Owner: verified containment + restoration ----

    @Test
    fun deviceOwner_containsVillain_verifiesState_andRestores() = runBlocking {
        assumeTrue("requires Device Owner", isDeviceOwner())
        assumeTrue("decoy apps are not installed", decoysInstalled())

        val enforcer = DeviceOwnerEnforcer(context)
        val pm = context.packageManager

        try {
            val result = enforcer.execute(
                actDecision(DemoAppRegistry.VILLAIN_CALLER, "VillainCaller"),
            )

            assertEquals(
                "all attempted actions must verify: ${result.outcomes}",
                EnforcementVerdict.ACTED,
                result.verdict,
            )
            // OS ground truth, not the API return value:
            assertTrue(
                "VillainCaller must actually be suspended",
                pm.isPackageSuspended(DemoAppRegistry.VILLAIN_CALLER),
            )

            // GoodCaller collateral check.
            assertFalse(
                "GoodCaller must never be suspended",
                runCatching { pm.isPackageSuspended(DemoAppRegistry.GOOD_CALLER) }
                    .getOrDefault(false),
            )

            // Idempotency: a second identical enforcement is safe and still verified.
            val second = enforcer.execute(
                actDecision(DemoAppRegistry.VILLAIN_CALLER, "VillainCaller"),
            )
            assertEquals(EnforcementVerdict.ACTED, second.verdict)
            assertTrue(
                second.outcomes.any { it.status == ActionStatus.ALREADY_IN_STATE },
            )
        } finally {
            // Restoration must always run so the device is left demo-ready.
            val restore = enforcer.restore(DemoAppRegistry.VILLAIN_CALLER, "VillainCaller")
            assertEquals(
                "restore must verify: ${restore.outcomes}",
                EnforcementVerdict.ACTED,
                restore.verdict,
            )
            assertFalse(
                "VillainCaller must be unsuspended after restore",
                pm.isPackageSuspended(DemoAppRegistry.VILLAIN_CALLER),
            )
            assertNotNull(
                "VillainCaller must be launchable after restore",
                pm.getLaunchIntentForPackage(DemoAppRegistry.VILLAIN_CALLER),
            )

            // Repeated restore is safe.
            val again = enforcer.restore(DemoAppRegistry.VILLAIN_CALLER, "VillainCaller")
            assertEquals(EnforcementVerdict.ACTED, again.verdict)
        }
    }
}
