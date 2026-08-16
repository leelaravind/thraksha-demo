package com.thraksha.guardian

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thraksha.guardian.data.audit.AuditLog
import com.thraksha.guardian.data.db.DatabaseProvider
import com.thraksha.guardian.security.PrivilegeLevel
import com.thraksha.guardian.security.SecurityCapability
import com.thraksha.guardian.security.engine.AuditResult
import com.thraksha.guardian.security.engine.SecurityAuditEngine
import com.thraksha.guardian.security.events.SecurityEventBus
import com.thraksha.guardian.security.inventory.AppInventory
import com.thraksha.guardian.security.inventory.DemoAppRegistry
import com.thraksha.guardian.security.policy.ActionStatus
import com.thraksha.guardian.security.policy.DecisionType
import com.thraksha.guardian.security.policy.EnforcementVerdict
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device end-to-end test of the Phase 4 pipeline on a NON-Device-Owner device:
 *
 * `Findings → PolicyEngine → SecurityDecision(ADVISE) → AdvisoryEnforcer → ADVISED`
 *
 * and its persistence: `SCAN → THREAT → DECISION → ADVISED` rows in the encrypted,
 * hash-chained audit trail — with zero privileged action performed.
 */
@RunWith(AndroidJUnit4::class)
class PolicyPipelineInstrumentedTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun reset() {
        SecurityAuditEngine.resetForTest()
    }

    private fun decoysInstalled(): Boolean =
        AppInventory(context.packageManager).inventory().size == DemoAppRegistry.demoPackages.size

    private fun notDeviceOwner(): Boolean =
        SecurityCapability.currentLevel(context) != PrivilegeLevel.DEVICE_OWNER

    @Test
    fun villainCaller_isAdvised_underAdviceMode() = runBlocking {
        assumeTrue("decoy apps are not installed", decoysInstalled())
        assumeTrue("this test requires a non-Device-Owner device", notDeviceOwner())

        val result = SecurityAuditEngine.runAudit(context)
        assertTrue("audit should complete, got $result", result is AuditResult.Completed)
        val villain = (result as AuditResult.Completed).results
            .first { it.packageName == DemoAppRegistry.VILLAIN_CALLER }

        val decision = villain.decision
        assertNotNull("a policy decision must exist for VillainCaller", decision)
        assertEquals(DecisionType.ADVISE, decision!!.decisionType)
        assertTrue(decision.recommendedActions.isNotEmpty())

        val enforcement = villain.enforcement
        assertNotNull("an advisory enforcement result must exist", enforcement)
        assertEquals(EnforcementVerdict.ADVISED, enforcement!!.verdict)
        assertTrue(
            "no action may be reported executed in Advice Mode",
            enforcement.outcomes.none { it.status == ActionStatus.SUCCEEDED },
        )
    }

    @Test
    fun goodCaller_getsNoAdvisoryAndNoDecisionRow() = runBlocking {
        assumeTrue("decoy apps are not installed", decoysInstalled())

        val result = SecurityAuditEngine.runAudit(context) as AuditResult.Completed
        val good = result.results.first { it.packageName == DemoAppRegistry.GOOD_CALLER }

        assertTrue(good.isClean)
        assertEquals(DecisionType.OBSERVE, good.decision?.decisionType)
        assertNull("nothing is enforced or advised for a clean app", good.enforcement)
    }

    @Test
    fun adviceModePerformsZeroPrivilegedActions_villainRemainsFullyOperational() = runBlocking {
        assumeTrue("decoy apps are not installed", decoysInstalled())
        assumeTrue("this test requires a non-Device-Owner device", notDeviceOwner())

        SecurityAuditEngine.runAudit(context)

        // Ground truth from the OS: the flagged app is still installed, enabled and
        // launchable — nothing suspended it, nothing revoked anything.
        val pm = context.packageManager
        val info = pm.getApplicationInfo(DemoAppRegistry.VILLAIN_CALLER, 0)
        assertTrue("VillainCaller must remain enabled", info.enabled)
        assertNotNull(
            "VillainCaller must remain launchable",
            pm.getLaunchIntentForPackage(DemoAppRegistry.VILLAIN_CALLER),
        )
    }

    @Test
    fun decisionAndAdvisoryArePersistedIntoTheEncryptedChain() = runBlocking {
        assumeTrue("decoy apps are not installed", decoysInstalled())
        assumeTrue("this test requires a non-Device-Owner device", notDeviceOwner())

        val dao = DatabaseProvider.get(context).auditDao()
        val subscribed = withTimeoutOrNull(10_000) {
            while (SecurityEventBus.subscriptionCount.value == 0) delay(20)
            true
        }
        assertTrue("the app's audit collector must be subscribed", subscribed == true)

        val before = dao.count()
        val result = SecurityAuditEngine.runAudit(context) as AuditResult.Completed
        // threats + 1 DECISION + 1 ADVISED (VillainCaller) + 1 SCAN
        val expected = result.threatsFound + 3

        withTimeoutOrNull(15_000) {
            while (dao.count() < before + expected) delay(50)
        }

        val newRows = dao.allOrdered().filter { it.id > before }

        assertTrue(
            "a DECISION row must attribute VillainCaller (rows: ${newRows.map { it.type }})",
            newRows.any {
                it.type == "DECISION" && it.details.contains(DemoAppRegistry.VILLAIN_CALLER) &&
                    it.details.contains("ADVISE")
            },
        )
        assertTrue(
            "an ADVISED row must record the advisory outcome",
            newRows.any {
                it.type == "ADVISED" && it.details.contains(DemoAppRegistry.VILLAIN_CALLER) &&
                    it.details.contains("no automatic OS action")
            },
        )
        assertTrue(
            "GoodCaller must never appear in DECISION/ADVISED rows",
            newRows.none {
                (it.type == "DECISION" || it.type == "ADVISED") &&
                    it.details.contains(DemoAppRegistry.GOOD_CALLER)
            },
        )
        assertTrue(
            "no ACTION row may exist — nothing was executed",
            newRows.none { it.type == "ACTION" },
        )
        assertTrue(
            "the tamper-evident chain must still verify after the new row types",
            AuditLog.verify(dao.allOrdered()),
        )
    }

    @Test
    fun repeatedAudits_produceIdenticalDecisions() = runBlocking {
        assumeTrue("decoy apps are not installed", decoysInstalled())

        val first = SecurityAuditEngine.runAudit(context) as AuditResult.Completed
        val second = SecurityAuditEngine.runAudit(context) as AuditResult.Completed

        assertEquals(
            first.results.map { it.packageName to it.decision?.decisionType },
            second.results.map { it.packageName to it.decision?.decisionType },
        )
        assertEquals(
            first.results.mapNotNull { it.decision?.decisionId },
            second.results.mapNotNull { it.decision?.decisionId },
        )
    }
}
