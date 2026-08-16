package com.thraksha.guardian

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thraksha.guardian.data.config.ConfigStore
import com.thraksha.guardian.data.config.ExecutionMode
import com.thraksha.guardian.security.engine.FindingSource
import com.thraksha.guardian.security.inventory.AppInventory
import com.thraksha.guardian.security.inventory.AppType
import com.thraksha.guardian.security.inventory.DemoAppRegistry
import com.thraksha.guardian.security.inventory.VisibilityScope
import com.thraksha.guardian.security.scan.DeviceScanEngine
import com.thraksha.guardian.security.scan.DeviceScanResult
import com.thraksha.guardian.security.scan.IntelligenceState
import com.thraksha.guardian.security.scan.ScanClassification
import com.thraksha.guardian.security.scan.ScanOutcome
import com.thraksha.guardian.security.threatintel.AppFingerprinter
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phase 8 on-device proof (guide §§33, 36, 37): the generalized scanner genuinely
 * discovers, fingerprints and analyzes applications that were unknown when Thraksha was
 * built, classifies them from evidence, and treats the controlled samples through the
 * SAME pipeline.
 *
 * Runs under OBSERVE mode so a scan during the suite can never mutate device state
 * (no automatic enforcement), and restores the previous mode afterwards.
 */
@RunWith(AndroidJUnit4::class)
class DeviceScanInstrumentedTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private var previousMode: ExecutionMode? = null

    @Before
    fun observeModeAndReset() {
        runBlocking {
            DeviceScanEngine.resetForTest()
            previousMode = runCatching { ConfigStore(context).getExecutionMode() }.getOrNull()
            ConfigStore(context).setExecutionMode(ExecutionMode.OBSERVE)
        }
    }

    @After
    fun restoreMode() {
        runBlocking {
            previousMode?.let { ConfigStore(context).setExecutionMode(it) }
        }
    }

    private fun scan(): DeviceScanResult = runBlocking { DeviceScanEngine.scanDevice(context) }

    // ---- discovery (guide §33: arbitrary installed package observation) ----

    @Test
    // Phase 12.1 Task A: renamed from `debugBuild_holdsFullVisibility_…`. Broad visibility
    // is no longer a debug-only property — QUERY_ALL_PACKAGES moved to the main manifest,
    // so debug and release hold the same scope. A test name asserting otherwise is exactly
    // the kind of stale build-type assumption that made RC1 half-blind.
    fun build_holdsFullVisibility_andDiscoversTheRegistry() {
        val inventory = AppInventory(context.packageManager)
        assertEquals(VisibilityScope.FULL, inventory.visibilityScope())

        val discovered = inventory.inventoryDevice()
        assertTrue(
            "expected a substantial registry view, got ${discovered.apps.size}",
            discovered.apps.size > 50,
        )
        assertTrue(
            "Guardian itself must be discovered",
            discovered.apps.any { it.packageName == "com.thraksha.guardian" },
        )
        val unknownAtCompileTime = discovered.apps.filter {
            it.packageName != "com.thraksha.guardian" &&
                !DemoAppRegistry.isDemoPackage(it.packageName)
        }
        assertTrue(
            "packages never named in any Thraksha source must be discovered",
            unknownAtCompileTime.size > 40,
        )
        // Label/version extraction from arbitrary packages:
        assertTrue(unknownAtCompileTime.all { it.displayName.isNotBlank() })
    }

    @Test
    fun arbitraryPackages_areFingerprintable() {
        val inventory = AppInventory(context.packageManager).inventoryDevice()
        val fingerprinter = AppFingerprinter(context.packageManager)
        val hex64 = Regex("^[0-9a-f]{64}$")

        // Guardian plus the first few packages unknown at compile time.
        val subjects = listOf("com.thraksha.guardian") + inventory.apps
            .map { it.packageName }
            .filter { it != "com.thraksha.guardian" && !DemoAppRegistry.isDemoPackage(it) }
            .sorted()
            .take(5)

        subjects.forEach { pkg ->
            val fp = fingerprinter.fingerprint(pkg)
            assertNotNull("fingerprint expected for $pkg", fp)
            assertTrue(
                "$pkg must expose at least one signing cert digest",
                fp!!.certSha256.isNotEmpty() && fp.certSha256.all { hex64.matches(it) },
            )
            assertTrue(
                "$pkg base APK digest must be a sha256 hex or null",
                fp.baseApkSha256 == null || hex64.matches(fp.baseApkSha256!!),
            )
        }
    }

    // ---- the full generalized scan (guide §§36–37) ----

    @Test
    fun scanDevice_analyzesUnknownApps_withHonestClassifications() {
        val result = scan()

        assertTrue(
            "scan must complete or be honestly partial, got ${result.outcome}",
            result.outcome != ScanOutcome.ERROR,
        )
        assertTrue("apps must be analyzed", result.analyzedCount > 50)
        assertEquals(result.analyzedCount, result.apps.size)
        assertEquals(
            result.totalDiscovered,
            result.analyzedCount + result.failedPackages.size,
        )
        assertTrue(
            "most apps must be certificate-checked",
            result.certificatesChecked > result.analyzedCount / 2,
        )
        assertTrue("APK fingerprints must be computed", result.apkHashesComputed > 20)
        assertEquals(
            IntelligenceState.Status.ACTIVE,
            result.intelligence.status,
        )
        assertTrue(
            "the real corpus must be active (found ${result.intelligence.activeIndicators})",
            result.intelligence.activeIndicators >= 1000,
        )

        // Every analyzed app has a classification; nothing is 'clean by omission'.
        result.apps.forEach { record ->
            assertNotNull(record.classification)
            if (record.classification == ScanClassification.NO_KNOWN_FINDINGS) {
                assertTrue(
                    "${record.packageName} may only be NO KNOWN FINDINGS with complete " +
                        "evidence",
                    record.evidenceComplete,
                )
            }
        }

        // Apps unknown at compile time were genuinely analyzed.
        val unknown = result.apps.filter {
            !DemoAppRegistry.isDemoPackage(it.packageName) &&
                it.packageName != "com.thraksha.guardian"
        }
        assertTrue(unknown.size > 40)

        // §36: unknown apps receive generic analysis, never a forced CALLER_ID baseline.
        unknown.forEach { record ->
            assertEquals(
                "${record.packageName} must not be forced into a demo baseline",
                AppType.UNKNOWN,
                record.appType,
            )
            assertTrue(
                "${record.packageName} must have no contextual profile findings",
                record.findings.none { it.source == FindingSource.PROFILE_RULE },
            )
        }
    }

    @Test
    fun guardianItself_isScannedByTheSameRules_notSpecialCased() {
        val result = scan()
        val self = result.apps.firstOrNull { it.packageName == "com.thraksha.guardian" }
        assertNotNull("Guardian must scan itself", self)

        // The debug build declares accessibility, notification-listener, VPN, device
        // admin and QUERY_ALL_PACKAGES — the generic rules must say so, exactly as they
        // would for any unknown app with that profile. Honest self-reporting is the
        // no-special-casing proof.
        assertTrue(
            "Guardian's own capability profile must produce generic findings",
            self!!.findings.any { it.source == FindingSource.GENERIC_RULE },
        )
        assertTrue(
            "Guardian must not be a known threat (no indicator matches it)",
            self.classification == ScanClassification.REVIEW ||
                self.classification == ScanClassification.HIGH_RISK_PROFILE,
        )
    }

    @Test
    fun controlledSamples_goThroughTheSamePipeline() {
        val villainInstalled = AppInventory(context.packageManager)
            .observe(DemoAppRegistry.VILLAIN_CALLER) != null
        val result = scan()

        if (!villainInstalled) {
            // §36: the scanner must work without the decoys.
            assertTrue(
                "scan must succeed without the demo samples",
                result.analyzedCount > 50,
            )
            return
        }

        val villain = result.apps.first { it.packageName == DemoAppRegistry.VILLAIN_CALLER }
        // The controlled positive: strong indicator matches (cert and/or base APK) from
        // the signed pack — through the same scanner as every other app.
        assertEquals(ScanClassification.KNOWN_THREAT_MATCH, villain.classification)
        assertTrue(
            villain.findings.any { it.source == FindingSource.THREAT_INTELLIGENCE },
        )
        assertNotNull("threat-tier apps get a policy decision", villain.decision)

        val good = result.apps.firstOrNull { it.packageName == DemoAppRegistry.GOOD_CALLER }
        good?.let {
            assertTrue(
                "GoodCaller must never be a known threat: ${it.findings.map { f -> f.ruleId }}",
                it.classification != ScanClassification.KNOWN_THREAT_MATCH,
            )
        }
    }

    @Test
    fun repeatScan_isDeterministic_whenDeviceStateUnchanged() {
        val first = scan()
        val second = scan()

        val firstMap = first.apps.associate { it.packageName to it.classification }
        val secondMap = second.apps.associate { it.packageName to it.classification }
        // Device state can legitimately change between runs (background installs);
        // compare the intersection, which must agree exactly.
        val common = firstMap.keys intersect secondMap.keys
        assertTrue(common.size > 50)
        common.forEach { pkg ->
            assertEquals(
                "classification for $pkg must be deterministic",
                firstMap[pkg],
                secondMap[pkg],
            )
        }
    }

    @Test
    fun timings_areMeasured() {
        val result = scan()
        val t = result.timings
        assertTrue("total time must be measured", t.totalMs > 0)
        assertTrue(
            "per-stage timings must be recorded: $t",
            t.inventoryMs >= 0 && t.certFingerprintMs >= 0 && t.apkHashMs >= 0 &&
                t.genericRulesMs >= 0 && t.threatIntelMs >= 0,
        )
    }
}
