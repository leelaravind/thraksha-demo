package com.thraksha.guardian

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thraksha.guardian.security.engine.AppAuditResult
import com.thraksha.guardian.security.engine.AuditResult
import com.thraksha.guardian.security.engine.SecurityAuditEngine
import com.thraksha.guardian.security.events.SecurityEvent
import com.thraksha.guardian.security.events.SecurityEventBus
import com.thraksha.guardian.security.inventory.AppInventory
import com.thraksha.guardian.security.inventory.AppType
import com.thraksha.guardian.security.inventory.DemoAppRegistry
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
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
 * End-to-end on-device tests for the real detection path:
 * verified rulepack → AppInventory → baseline → RuleEngine → findings.
 *
 * These run against the actually-installed decoy APKs, so they prove the pipeline against
 * real `PackageManager` data rather than fixtures.
 */
@RunWith(AndroidJUnit4::class)
class SecurityAuditEngineInstrumentedTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun reset() {
        SecurityAuditEngine.resetForTest()
    }

    private fun decoysInstalled(): Boolean =
        AppInventory(context.packageManager).inventory().size == DemoAppRegistry.demoPackages.size

    private fun completedAudit(): AuditResult.Completed = runBlocking {
        val result = SecurityAuditEngine.runAudit(context)
        assertTrue("audit should complete, got $result", result is AuditResult.Completed)
        result as AuditResult.Completed
    }

    private fun resultFor(audit: AuditResult.Completed, pkg: String): AppAuditResult? =
        audit.results.firstOrNull { it.packageName == pkg }

    @Test
    fun auditRunsOnTheVerifiedRulepack() {
        val audit = completedAudit()
        assertEquals("the signed demo pack is v2", 2, audit.rulepackVersion)
        assertTrue("at least one rule must be active", audit.rulesEvaluated > 0)
    }

    @Test
    fun bothDecoysAreInventoriedAndClassifiedAsCallerId() {
        assumeTrue("decoy apps are not installed", decoysInstalled())
        val audit = completedAudit()

        assertEquals(2, audit.appsScanned)
        for (pkg in DemoAppRegistry.demoPackages) {
            val result = resultFor(audit, pkg)
            assertNotNull("$pkg must be inventoried", result)
            assertEquals(AppType.CALLER_ID, result!!.appType)
        }
    }

    @Test
    fun goodCallerProducesZeroThreatFindings() {
        assumeTrue("decoy apps are not installed", decoysInstalled())
        val good = resultFor(completedAudit(), DemoAppRegistry.GOOD_CALLER)

        assertNotNull(good)
        assertEquals(
            "GoodCaller must be clean, got ${good!!.findings.map { it.ruleId }}",
            0,
            good.findings.size,
        )
        assertTrue(good.isClean)
    }

    @Test
    fun villainCallerProducesDeterministicThreatFindings() {
        assumeTrue("decoy apps are not installed", decoysInstalled())
        val villain = resultFor(completedAudit(), DemoAppRegistry.VILLAIN_CALLER)

        assertNotNull(villain)
        val ids = villain!!.findings.map { it.ruleId }.toSet()
        assertTrue("media mismatch must fire", ids.contains("caller-media-permission-mismatch"))
        assertTrue(
            "package-visibility mismatch must fire",
            ids.contains("caller-package-visibility-mismatch"),
        )
        assertTrue("compound profile rule must fire", ids.contains("caller-high-risk-profile"))

        // Every finding must be attributable and evidenced.
        villain.findings.forEach { finding ->
            assertEquals(DemoAppRegistry.VILLAIN_CALLER, finding.packageName)
            assertTrue("finding ${finding.ruleId} needs evidence", finding.evidence.isNotEmpty())
            assertTrue("finding ${finding.ruleId} needs a reason", finding.reason.isNotBlank())
        }
    }

    @Test
    fun repeatedScansAreDeterministic() {
        assumeTrue("decoy apps are not installed", decoysInstalled())
        val first = completedAudit()
        val second = completedAudit()

        assertEquals(first.appsScanned, second.appsScanned)
        assertEquals(first.threatsFound, second.threatsFound)
        assertEquals(
            first.allFindings.map { it.packageName to it.ruleId }.sortedBy { it.toString() },
            second.allFindings.map { it.packageName to it.ruleId }.sortedBy { it.toString() },
        )
    }

    @Test
    fun concurrentScansAreSerialisedAndDoNotCorruptState() {
        assumeTrue("decoy apps are not installed", decoysInstalled())
        val results = runBlocking {
            (1..4).map { async { SecurityAuditEngine.runAudit(context) } }.awaitAll()
        }
        assertTrue(results.all { it is AuditResult.Completed })
        val counts = results.map { (it as AuditResult.Completed).threatsFound }.toSet()
        assertEquals("every concurrent scan must agree", 1, counts.size)
    }

    @Test
    fun missingDecoyDoesNotCrashTheAudit() {
        // A package that is deliberately not installed and not in <queries>: the inventory
        // must return null for it rather than throwing.
        val observed = AppInventory(context.packageManager)
            .observe("com.thraksha.demo.doesnotexist")
        assertNull(observed)

        // And a full audit still completes.
        assertTrue(completedAudit().appsScanned >= 0)
    }

    @Test
    fun findingsAreEmittedOntoTheRealSecurityEventBus() {
        assumeTrue("decoy apps are not installed", decoysInstalled())

        val events = runBlocking {
            val collected = async(start = CoroutineStart.UNDISPATCHED) {
                withTimeoutOrNull(15_000) {
                    SecurityEventBus.events.take(2).toList()
                }
            }
            launch { SecurityAuditEngine.runAudit(context) }
            collected.await()
        }

        assertNotNull("expected events on the bus", events)
        assertTrue(
            "a real ThreatDetected must be emitted, carrying the offending package",
            events!!.filterIsInstance<SecurityEvent.ThreatDetected>()
                .any { it.packageName == DemoAppRegistry.VILLAIN_CALLER },
        )
    }
}
