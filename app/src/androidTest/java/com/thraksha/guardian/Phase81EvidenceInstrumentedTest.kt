package com.thraksha.guardian

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thraksha.guardian.data.config.ConfigStore
import com.thraksha.guardian.data.config.ExecutionMode
import com.thraksha.guardian.data.db.DatabaseProvider
import com.thraksha.guardian.security.PrivilegeLevel
import com.thraksha.guardian.security.SecurityCapability
import com.thraksha.guardian.security.act.UserActCoordinator
import com.thraksha.guardian.security.act.UserActOption
import com.thraksha.guardian.security.act.UserActStatus
import com.thraksha.guardian.security.evidence.AppDisplayStatus
import com.thraksha.guardian.security.evidence.CapabilityCatalog
import com.thraksha.guardian.security.evidence.CapabilityObservation
import com.thraksha.guardian.security.evidence.EvidenceAssembler
import com.thraksha.guardian.security.evidence.ObservationReliability
import com.thraksha.guardian.security.inventory.AppInventory
import com.thraksha.guardian.security.inventory.DemoAppRegistry
import com.thraksha.guardian.security.inventory.GrantState
import com.thraksha.guardian.security.observe.RuntimeObservationProvider
import com.thraksha.guardian.security.observe.RuntimeObservationStore
import com.thraksha.guardian.security.scan.DeviceScanEngine
import com.thraksha.guardian.security.scan.ScanClassification
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phase 8.1 on-device proofs (guide §31):
 *
 *  * grant/enablement state queries genuinely work at Thraksha's privilege — requested
 *    vs granted is a real distinction read from the OS, special access states are
 *    determined (not fabricated), and an unanswerable query is UNKNOWN;
 *  * Stage 3 stays empty on a real scan unless a genuine observation source recorded
 *    something — no OS API is faked;
 *  * the user ACT surface exposes only supported actions at the device's real privilege
 *    and never reports ACTED for anything unverified.
 *
 * Runs under OBSERVE so nothing mutates device state.
 */
@RunWith(AndroidJUnit4::class)
class Phase81EvidenceInstrumentedTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private var previousMode: ExecutionMode? = null

    private val wearablePackage = "com.samsung.android.app.watchmanager"

    @Before
    fun setUp() {
        runBlocking {
            DeviceScanEngine.resetForTest()
            RuntimeObservationStore.resetForTest()
            UserActCoordinator.resetForTest()
            previousMode = runCatching { ConfigStore(context).getExecutionMode() }.getOrNull()
            ConfigStore(context).setExecutionMode(ExecutionMode.OBSERVE)
        }
    }

    @After
    fun tearDown() {
        runBlocking {
            RuntimeObservationStore.resetForTest()
            UserActCoordinator.resetForTest()
            previousMode?.let { ConfigStore(context).setExecutionMode(it) }
        }
    }

    // ---- special-access / grant-state queries (guide §5, §31) ----

    @Test
    fun specialAccessStates_areDetermined_forOwnPackage() {
        val provider = RuntimeObservationProvider(context)
        val self = AppInventory(context.packageManager).observe("com.thraksha.guardian")
        assertNotNull(self)
        val snapshot = provider.specialAccessFor(self!!)

        // These queries are supported public APIs — on a healthy device they must
        // resolve to a real state, not UNKNOWN.
        assertNotEquals(
            "notification-listener enablement must be determinable",
            GrantState.UNKNOWN, snapshot.notificationListener,
        )
        assertNotEquals(
            "accessibility enablement must be determinable",
            GrantState.UNKNOWN, snapshot.accessibility,
        )
        assertNotEquals(
            "device-admin activity must be determinable",
            GrantState.UNKNOWN, snapshot.deviceAdmin,
        )
    }

    @Test
    fun overlayOpQuery_isAnswerable_forAnotherPackage() {
        // The research doc's AppOps probe: a normal app may query another package's
        // SYSTEM_ALERT_WINDOW op mode; combined with the target's own permission grant
        // flag the state must resolve — an unanswerable query would surface as UNKNOWN
        // and this assertion would fail, which is exactly the honest signal we want.
        val inventory = AppInventory(context.packageManager)
        val target = inventory.observe(wearablePackage)
        assumeTrue(
            "no overlay-declaring target installed",
            target != null && target.requests("android.permission.SYSTEM_ALERT_WINDOW"),
        )
        val snapshot = RuntimeObservationProvider(context).specialAccessFor(target!!)
        assertNotEquals(
            "overlay enablement must be determinable for a declaring app",
            GrantState.UNKNOWN, snapshot.overlay,
        )
    }

    @Test
    fun requestedVersusGranted_isARealDistinction_onThisDevice() {
        // The wearable companion (if present) declares dozens of runtime permissions;
        // whatever their current grant state, requested ⊇ granted must hold, and the
        // evidence rows must carry the OS-reported state, never assume it.
        val inventory = AppInventory(context.packageManager)
        val target = inventory.observe(wearablePackage)
            ?: inventory.observe(DemoAppRegistry.VILLAIN_CALLER)
        assumeTrue("no suitable target installed", target != null)

        assertTrue(target!!.grantedPermissions.all { it in target.requestedPermissions })

        val declaredNotGranted = target.requestedPermissions - target.grantedPermissions
        assumeTrue(
            "target has no declared-but-ungranted permission to prove the distinction",
            declaredNotGranted.isNotEmpty(),
        )

        val evidence = EvidenceAssembler.assemble(target, emptyList())
        val declaredOnlyRows = evidence.filter {
            it.declared && it.grantState == GrantState.DENIED
        }
        assertTrue(
            "at least one capability must show DECLARED without GRANTED",
            declaredOnlyRows.isNotEmpty(),
        )
        declaredOnlyRows.forEach {
            assertFalse("declared-only must never be observed", it.observed)
            assertNotNull(it.observationLimitation)
        }
    }

    @Test
    fun unsupportedObservation_staysUnavailable_onRealEvidence() {
        val inventory = AppInventory(context.packageManager)
        val target = inventory.observe(wearablePackage)
        assumeTrue("Galaxy Wearable not installed", target != null)

        val provider = RuntimeObservationProvider(context)
        val enriched = target!!.copy(specialAccess = provider.specialAccessFor(target))
        val evidence = EvidenceAssembler.assemble(enriched, emptyList())

        // Location/contacts/camera/mic/media: Android exposes no per-app usage to
        // Thraksha — every such row must say NOT OBSERVABLE and must not be observed.
        val sensitive = evidence.filter {
            it.capabilityId in setOf(
                CapabilityCatalog.CONTACTS, CapabilityCatalog.LOCATION,
                CapabilityCatalog.CAMERA, CapabilityCatalog.MICROPHONE,
                CapabilityCatalog.MEDIA,
            )
        }
        assumeTrue(sensitive.isNotEmpty())
        sensitive.forEach { row ->
            assertFalse(row.observed)
            assertTrue(
                "limitation must state non-observability, got: ${row.observationLimitation}",
                row.observationLimitation!!.contains("NOT OBSERVABLE"),
            )
        }
    }

    @Test
    fun verifiedStoreObservation_reachesStage3_forTheRightPackageOnly() {
        val inventory = AppInventory(context.packageManager)
        val villain = inventory.observe(DemoAppRegistry.VILLAIN_CALLER)
        assumeTrue("VillainCaller not installed", villain != null)

        RuntimeObservationStore.record(
            CapabilityObservation(
                capabilityId = CapabilityCatalog.NETWORK,
                packageName = DemoAppRegistry.VILLAIN_CALLER,
                observedAt = System.currentTimeMillis(),
                source = "Network Guard TUN interception",
                reliability = ObservationReliability.VERIFIED,
                detail = "outbound UDP 32 B → 203.0.113.113:443",
            ),
        )

        val villainEvidence = EvidenceAssembler.assemble(
            villain!!, RuntimeObservationStore.forPackage(DemoAppRegistry.VILLAIN_CALLER),
        )
        val network = villainEvidence.first { it.capabilityId == CapabilityCatalog.NETWORK }
        assertTrue("a genuine recorded observation must reach Stage 3", network.observed)

        val good = inventory.observe(DemoAppRegistry.GOOD_CALLER)
        assumeTrue(good != null)
        val goodEvidence = EvidenceAssembler.assemble(
            good!!, RuntimeObservationStore.forPackage(DemoAppRegistry.GOOD_CALLER),
        )
        assertTrue(
            "another package must not inherit the observation",
            goodEvidence.none { it.observed },
        )
    }

    // ---- full-scan integration (guide §31: observation provider behavior) ----

    @Test
    fun deviceScan_carriesEvidence_andNeverInventsStage3() {
        val result = runBlocking { DeviceScanEngine.scanDevice(context) }
        assertTrue(result.apps.isNotEmpty())

        // With an empty observation store, no app may show observed evidence.
        result.apps.forEach { record ->
            record.evidence.forEach { row ->
                assertFalse(
                    "${record.packageName}/${row.capabilityId} claims OBSERVED with an " +
                        "empty observation store",
                    row.observed,
                )
            }
        }

        // Every analyzed record carries the Phase 8.1 additions.
        val analyzed = result.apps.filter { it.classification != ScanClassification.PARTIAL }
        assumeTrue(analyzed.isNotEmpty())
        analyzed.forEach { record ->
            assertNotNull("displayStatus missing for ${record.packageName}", record.displayStatus)
            assertNotNull("context missing for ${record.packageName}", record.contextAssessment)
        }

        // The controlled positive remains a KNOWN THREAT MATCH independent of Stage 3
        // (guide §24) — via the same pipeline.
        val villain = result.apps.firstOrNull { it.packageName == DemoAppRegistry.VILLAIN_CALLER }
        assumeTrue("VillainCaller not installed", villain != null)
        assertEquals(ScanClassification.KNOWN_THREAT_MATCH, villain!!.classification)
        assertEquals(AppDisplayStatus.KNOWN_THREAT_MATCH, villain.displayStatus)

        // The primary calibration case: a legitimate powerful app must not present as
        // proven malicious behavior from static capabilities alone (guide §40).
        val wearable = result.apps.firstOrNull { it.packageName == wearablePackage }
        if (wearable != null) {
            assertNotEquals(AppDisplayStatus.KNOWN_THREAT_MATCH, wearable.displayStatus)
            assertNotEquals(AppDisplayStatus.ADVISED, wearable.displayStatus)
        }
    }

    // ---- user ACT (guide §31: ACT flow, settings intent, audit persistence) ----

    @Test
    fun actOptions_matchRealPrivilege_andRefuseUnsupported() = runBlocking {
        val result = runBlocking { DeviceScanEngine.scanDevice(context) }
        val realApp = result.apps.firstOrNull {
            !it.isSystemApp && it.packageName != DemoAppRegistry.VILLAIN_CALLER &&
                it.findings.isNotEmpty()
        }
        assumeTrue("no findings-bearing real user app on device", realApp != null)

        val options = UserActCoordinator.supportedOptions(context, realApp!!)
        assertTrue(options.any { it is UserActOption.OpenAppSettings })

        if (SecurityCapability.currentLevel(context) != PrivilegeLevel.DEVICE_OWNER) {
            assertTrue(
                "no direct enforcement may be offered without Device Owner",
                options.none { it is UserActOption.SuspendApp } &&
                    options.none { it is UserActOption.RestrictPermissionDirect },
            )
        } else {
            assertTrue(
                "arbitrary real apps must not be offered direct enforcement even as " +
                    "Device Owner (guide §17)",
                options.none { it is UserActOption.SuspendApp } &&
                    options.none { it is UserActOption.RestrictPermissionDirect },
            )
        }

        // An unsupported action is refused and never executes anything.
        val refused = UserActCoordinator.perform(context, realApp, UserActOption.SuspendApp)
        assertEquals(UserActStatus.UNSUPPORTED, refused.status)
        assertFalse(
            "the app must not actually be suspended by a refused request",
            runCatching {
                context.packageManager.isPackageSuspended(realApp.packageName)
            }.getOrDefault(false),
        )
    }

    @Test
    fun keepWatching_isRecorded_inTheAuditChain() = runBlocking {
        val result = runBlocking { DeviceScanEngine.scanDevice(context) }
        val target = result.apps.firstOrNull { it.findings.isNotEmpty() }
        assumeTrue(target != null)

        val dao = DatabaseProvider.get(context).auditDao()
        val before = dao.count()

        val act = UserActCoordinator.perform(context, target!!, UserActOption.KeepWatching)
        assertEquals(UserActStatus.KEPT_WATCHING, act.status)

        // The USER event lands in the encrypted chain via the Application collector.
        // Poll for the specific row — other event types may be landing concurrently.
        var userRow: com.thraksha.guardian.data.db.AuditEntity? = null
        var waited = 0
        while (userRow == null && waited < 5000) {
            userRow = dao.allOrdered().lastOrNull {
                it.id > before && it.type == "USER" && it.details.contains("Keep watching")
            }
            if (userRow == null) {
                Thread.sleep(200)
                waited += 200
            }
        }
        assertNotNull("user ACT must append a USER audit row", userRow)
    }

    @Test
    fun appSettingsIntent_resolves_onThisDevice() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", "com.thraksha.guardian", null),
        )
        assertNotNull(
            "the manual-restriction path requires a resolvable settings activity",
            intent.resolveActivity(context.packageManager),
        )
    }

    @Test
    fun dismiss_hidesForSessionOnly_andIsRecorded() = runBlocking {
        val result = runBlocking { DeviceScanEngine.scanDevice(context) }
        val target = result.apps.firstOrNull {
            it.findings.isNotEmpty() &&
                it.displayStatus != AppDisplayStatus.KNOWN_THREAT_MATCH
        }
        assumeTrue(target != null)

        val act = UserActCoordinator.perform(context, target!!, UserActOption.Dismiss)
        assertEquals(UserActStatus.DISMISSED, act.status)
        assertTrue(target.packageName in UserActCoordinator.dismissedPackages.value)

        // Detection is unaffected: a fresh scan still finds the same app.
        val rescan = runBlocking { DeviceScanEngine.scanDevice(context) }
        assertTrue(rescan.apps.any { it.packageName == target.packageName })
    }
}
