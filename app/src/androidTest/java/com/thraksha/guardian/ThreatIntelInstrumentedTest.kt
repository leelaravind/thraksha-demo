package com.thraksha.guardian

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thraksha.guardian.security.engine.AuditResult
import com.thraksha.guardian.security.engine.FindingSource
import com.thraksha.guardian.security.engine.SecurityAuditEngine
import com.thraksha.guardian.security.inventory.AppInventory
import com.thraksha.guardian.security.inventory.DemoAppRegistry
import com.thraksha.guardian.security.threatintel.AppFingerprinter
import com.thraksha.guardian.security.threatintel.ThreatPackLoader
import com.thraksha.guardian.security.threatintel.ThreatPackState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device tests for the unified two-pillar audit (guide §6.19): verified threat pack
 * on the production path, real fingerprints from `PackageManager`/the installed APKs,
 * profile + threat-intelligence findings merged, GoodCaller clean under both pillars.
 */
@RunWith(AndroidJUnit4::class)
class ThreatIntelInstrumentedTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun reset() {
        SecurityAuditEngine.resetForTest()
    }

    private fun decoysInstalled(): Boolean =
        AppInventory(context.packageManager).inventory().size == DemoAppRegistry.demoPackages.size

    @Test
    fun bundledThreatPack_loadsVerified_onDevice() {
        val state = ThreatPackLoader(context).load()
        assertTrue("expected Verified, got $state", state is ThreatPackState.Verified)
        val pack = (state as ThreatPackState.Verified).pack
        assertEquals(1, pack.schemaVersion)
        assertTrue(pack.indicators.any { it.enabled })
    }

    @Test
    fun corruptedBytes_produceVisibleUnavailableState_neverClean() {
        val loader = ThreatPackLoader(context)
        val good = context.assets.open("threatpack.json").use { it.readBytes() }
        val sig = context.assets.open("threatpack.sig").use { it.readBytes() }.decodeToString()
        val pub = context.assets.open("threatpack_public.key").use { it.readBytes() }
            .decodeToString()

        val tampered = good.copyOf()
            .also { it[it.size / 2] = (it[it.size / 2].toInt() xor 1).toByte() }
        val state = loader.verifyAndParse(tampered, sig, pub)
        assertTrue("tampered pack must be Unavailable, got $state",
            state is ThreatPackState.Unavailable)
        assertTrue((state as ThreatPackState.Unavailable).reason.contains("signature"))
    }

    @Test
    fun fingerprinter_producesStableDigests_forInstalledDecoys() {
        assumeTrue("decoy apps are not installed", decoysInstalled())
        val fingerprinter = AppFingerprinter(context.packageManager)

        val first = fingerprinter.fingerprint(DemoAppRegistry.VILLAIN_CALLER)
        val second = fingerprinter.fingerprint(DemoAppRegistry.VILLAIN_CALLER)
        assertNotNull(first)
        assertEquals(first!!.certSha256, second!!.certSha256)
        assertEquals(first.baseApkSha256, second.baseApkSha256)
        assertTrue(first.certSha256.isNotEmpty())
        assertTrue(first.baseApkSha256?.matches(Regex("^[0-9a-f]{64}$")) == true)

        // The two decoys are deliberately signed with different keys — that difference
        // is what makes the certificate indicator a usable controlled signal.
        val good = fingerprinter.fingerprint(DemoAppRegistry.GOOD_CALLER)
        assertNotNull(good)
        assertTrue(
            "GoodCaller and VillainCaller must not share signing certificates",
            good!!.certSha256.intersect(first.certSha256).isEmpty(),
        )
    }

    @Test
    fun unifiedAudit_villainMatchesBothPillars_goodCallerCleanUnderBoth() = runBlocking {
        assumeTrue("decoy apps are not installed", decoysInstalled())

        val result = SecurityAuditEngine.runAudit(context)
        assertTrue("audit should complete, got $result", result is AuditResult.Completed)
        result as AuditResult.Completed

        assertTrue(
            "threat-intel pillar must be available: ${result.threatIntel}",
            result.threatIntel.available,
        )

        val villain = result.results.first { it.packageName == DemoAppRegistry.VILLAIN_CALLER }
        val profile = villain.findings.filter { it.source == FindingSource.PROFILE_RULE }
        val intel = villain.findings.filter { it.source == FindingSource.THREAT_INTELLIGENCE }

        assertTrue("profile findings must persist alongside threat intel", profile.isNotEmpty())
        assertTrue("threat-intel findings expected", intel.isNotEmpty())
        assertTrue(
            "the package-name demo indicator must match",
            intel.any { it.ruleId == "threat-intel:demo-villain-package" },
        )
        assertTrue(
            "the signing-cert demo indicator must match (villain has its own signer)",
            intel.any { it.ruleId == "threat-intel:demo-villain-signer" },
        )
        assertTrue(
            "every intel finding must carry the demo classification in evidence",
            intel.all { f -> f.evidence.any { it.contains("DEMO_TEST_THREAT") } },
        )
        assertTrue(
            "the negative-control indicators must not match",
            intel.none { it.ruleId.contains("control") },
        )

        val good = result.results.first { it.packageName == DemoAppRegistry.GOOD_CALLER }
        assertTrue(
            "GoodCaller must be clean under BOTH pillars: ${good.findings.map { it.ruleId }}",
            good.isClean,
        )
    }

    @Test
    fun scanTimings_areMeasuredAndSane() = runBlocking {
        assumeTrue("decoy apps are not installed", decoysInstalled())
        val result = SecurityAuditEngine.runAudit(context) as AuditResult.Completed
        val timings = result.timings
        assertNotNull("timings must be recorded", timings)
        assertTrue(timings!!.totalMs >= 0)
        assertTrue(
            "stages must be measured: $timings",
            timings.inventoryMs >= 0 && timings.ruleEngineMs >= 0 &&
                timings.certFingerprintMs >= 0 && timings.apkHashMs >= 0,
        )
    }
}
