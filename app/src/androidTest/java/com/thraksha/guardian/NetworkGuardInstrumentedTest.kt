package com.thraksha.guardian

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thraksha.guardian.data.audit.AuditLog
import com.thraksha.guardian.data.config.ConfigStore
import com.thraksha.guardian.data.config.ExecutionMode
import com.thraksha.guardian.data.db.DatabaseProvider
import com.thraksha.guardian.security.PrivilegeLevel
import com.thraksha.guardian.security.SecurityCapability
import com.thraksha.guardian.security.engine.FindingSource
import com.thraksha.guardian.security.events.SecurityEvent
import com.thraksha.guardian.security.events.SecurityEventBus
import com.thraksha.guardian.security.inventory.DemoAppRegistry
import com.thraksha.guardian.security.network.NetworkGuard
import com.thraksha.guardian.security.network.NetworkGuardEngine
import com.thraksha.guardian.security.network.NetworkThreatEvaluator
import com.thraksha.guardian.security.network.VpnScope
import com.thraksha.guardian.security.threatintel.IndicatorTypes
import com.thraksha.guardian.security.threatintel.ThreatPackLoader
import com.thraksha.guardian.security.threatintel.ThreatPackState
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device Phase 7 tests that do NOT require VPN consent: they drive the real
 * production [NetworkGuardEngine] (which loads the signed ThreatPack through the
 * production loader) with a handcrafted TUN packet. The consent-gated end-to-end tunnel
 * path is verified manually on device and recorded in the progress report — consent is
 * never faked here (guide §27).
 */
@RunWith(AndroidJUnit4::class)
class NetworkGuardInstrumentedTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    /** Valid IPv4/UDP packet to the demo target, as the TUN would present it. */
    private fun demoPacket(destIp: IntArray = intArrayOf(203, 0, 113, 113)): Pair<ByteArray, Int> {
        val payload = "THRAKSHA_DEMO_NETWORK_PROBE".toByteArray()
        val total = 20 + 8 + payload.size
        val p = ByteArray(total)
        p[0] = ((4 shl 4) or 5).toByte()
        p[2] = (total shr 8).toByte(); p[3] = (total and 0xFF).toByte()
        p[9] = 17
        intArrayOf(10, 113, 0, 2).forEachIndexed { i, b -> p[12 + i] = b.toByte() }
        destIp.forEachIndexed { i, b -> p[16 + i] = b.toByte() }
        p[20] = (40000 shr 8).toByte(); p[21] = (40000 and 0xFF).toByte()
        p[22] = (443 shr 8).toByte(); p[23] = (443 and 0xFF).toByte()
        val udpLen = 8 + payload.size
        p[24] = (udpLen shr 8).toByte(); p[25] = (udpLen and 0xFF).toByte()
        payload.copyInto(p, 28)
        return p to total
    }

    @Test
    fun threatPackNetworkIndicator_loadsThroughProductionLoader() {
        val state = ThreatPackLoader(context).load()
        assertTrue("threat pack must verify on device", state is ThreatPackState.Verified)
        val pack = (state as ThreatPackState.Verified).pack
        val net = pack.indicators.firstOrNull {
            it.type == IndicatorTypes.DESTINATION_IP && it.enabled
        }
        assertNotNull("bundled pack must carry an enabled DESTINATION_IP indicator", net)

        // And the real evaluator matches the demo packet's destination.
        val evaluator = NetworkThreatEvaluator(pack)
        val finding = evaluator.evaluate(
            com.thraksha.guardian.security.network.NetworkObservation(
                timestamp = 0L,
                packageName = DemoAppRegistry.VILLAIN_CALLER,
                ipVersion = 4, protocol = 17, protocolName = "UDP",
                sourceIp = "10.113.0.2", sourcePort = 40000,
                destinationIp = "203.0.113.113", destinationPort = 443,
                packetLength = 55,
            ),
        )
        assertNotNull("demo destination must match the signed indicator", finding)
        assertEquals(FindingSource.NETWORK_THREAT_INTELLIGENCE, finding!!.source)
    }

    @Test
    fun engine_producesNetworkFindingAndDecision_forDemoPacket() = runBlocking {
        val engine = NetworkGuardEngine(context)
        assertTrue("threat intel must be available on device", engine.threatIntelAvailable)

        val (packet, len) = demoPacket()
        val verdict = engine.evaluate(packet, len)

        // Depending on the device's mode/privilege this is Forward(matched) or Block;
        // both carry the finding + decision. A non-null decision proves PolicyEngine ran.
        val decision = when (verdict) {
            is NetworkGuardEngine.Verdict.Forward -> verdict.decision
            is NetworkGuardEngine.Verdict.Block -> verdict.decision
            is NetworkGuardEngine.Verdict.Ignore ->
                error("demo packet must not be ignored: ${verdict.reason}")
            is NetworkGuardEngine.Verdict.UserBlock ->
                error("no user block is armed in this test; got UserBlock unexpectedly")
        }
        assertNotNull("a decision must be produced for the matched packet", decision)
        assertEquals(DemoAppRegistry.VILLAIN_CALLER, decision!!.packageName)
    }

    @Test
    fun nonMatchingDestination_isForwardedWithoutAThreatVerdict() = runBlocking {
        val engine = NetworkGuardEngine(context)
        val (packet, len) = demoPacket(destIp = intArrayOf(203, 0, 113, 250)) // not an indicator
        val verdict = engine.evaluate(packet, len)
        assertTrue(verdict is NetworkGuardEngine.Verdict.Forward)
        assertEquals(
            "a non-matching destination has no finding",
            null,
            (verdict as NetworkGuardEngine.Verdict.Forward).finding,
        )
    }

    @Test
    fun networkFindingFlowsThroughSecurityEventBus_andPersistsWithChainIntact() = runBlocking {
        // The app's own audit collector must be subscribed.
        val dao = DatabaseProvider.get(context).auditDao()
        val subscribed = withTimeoutOrNull(10_000) {
            while (SecurityEventBus.subscriptionCount.value == 0) delay(20)
            true
        }
        assertTrue(subscribed == true)

        val observed = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeoutOrNull(10_000) {
                SecurityEventBus.events.first { it is SecurityEvent.NetworkAttemptObserved }
            }
        }
        val before = dao.count()
        val engine = NetworkGuardEngine(context)
        val (packet, len) = demoPacket()
        launch { engine.evaluate(packet, len) }
        val event = observed.await()
        assertNotNull("a NetworkAttemptObserved event must be emitted", event)
        event as SecurityEvent.NetworkAttemptObserved
        assertEquals("203.0.113.113", event.destinationIp)
        assertEquals(443, event.destinationPort)

        // A NETWORK audit row must land, and the chain must still verify.
        withTimeoutOrNull(10_000) {
            while (dao.allOrdered().none { it.id > before && it.type == "NETWORK" }) delay(50)
        }
        val rows = dao.allOrdered()
        assertTrue(
            "a NETWORK row must record the observation",
            rows.any {
                it.id > before && it.type == "NETWORK" &&
                    it.details.contains("203.0.113.113")
            },
        )
        assertFalse(
            "payload content must never be persisted",
            rows.any { it.details.contains("THRAKSHA_DEMO_NETWORK_PROBE") },
        )
        assertTrue("audit chain must verify after network rows", AuditLog.verify(rows))
    }

    @Test
    fun networkGuardState_lifecycleTransitions() {
        NetworkGuard.transition(NetworkGuard.State.Disabled)
        assertTrue(NetworkGuard.state.value is NetworkGuard.State.Disabled)

        NetworkGuard.transition(
            NetworkGuard.State.Active(
                scopedPackage = DemoAppRegistry.VILLAIN_CALLER,
                tunnelAddress = "10.113.0.2/32",
            ),
        )
        val active = NetworkGuard.state.value as NetworkGuard.State.Active
        assertEquals(DemoAppRegistry.VILLAIN_CALLER, active.scopedPackage)

        NetworkGuard.transition(NetworkGuard.State.Error("test"))
        assertTrue(NetworkGuard.state.value is NetworkGuard.State.Error)

        NetworkGuard.transition(NetworkGuard.State.Disabled)
    }

    @Test
    fun vpnScope_onlyVillainCaller_neverGoodCallerOrGuardian() {
        assertEquals(setOf(DemoAppRegistry.VILLAIN_CALLER), VpnScope.ALLOWED_PACKAGES)
        assertFalse(VpnScope.isAllowed(DemoAppRegistry.GOOD_CALLER))
        assertFalse(VpnScope.isAllowed(context.packageName)) // Guardian itself
    }

    @Test
    fun fullPowerAutoDefend_blocksDemoPacket_withoutForwarding() = runBlocking {
        // Only meaningful where Guardian is Device Owner (the AVD). Elsewhere this asserts
        // the Advice path, which is covered above; so gate on real authority.
        if (SecurityCapability.currentLevel(context) != PrivilegeLevel.DEVICE_OWNER) return@runBlocking

        val config = ConfigStore(context)
        val previous = config.getExecutionMode()
        try {
            config.setExecutionMode(ExecutionMode.AUTO_DEFEND)
            val engine = NetworkGuardEngine(context)
            val (packet, len) = demoPacket()
            val verdict = engine.evaluate(packet, len)
            assertTrue(
                "Full Power AUTO_DEFEND must block the matched packet",
                verdict is NetworkGuardEngine.Verdict.Block,
            )
        } finally {
            config.setExecutionMode(previous)
        }
    }
}
