package com.thraksha.guardian

import com.thraksha.guardian.data.config.ExecutionMode
import com.thraksha.guardian.security.PrivilegeLevel
import com.thraksha.guardian.security.engine.Finding
import com.thraksha.guardian.security.engine.FindingSource
import com.thraksha.guardian.security.events.Severity
import com.thraksha.guardian.security.inventory.DemoAppRegistry
import com.thraksha.guardian.security.network.NetworkObservation
import com.thraksha.guardian.security.network.NetworkThreatEvaluator
import com.thraksha.guardian.security.network.VpnScope
import com.thraksha.guardian.security.policy.DecisionType
import com.thraksha.guardian.security.policy.PolicyEngine
import com.thraksha.guardian.security.policy.RequiredAuthority
import com.thraksha.guardian.security.policy.SecurityAction
import com.thraksha.guardian.security.threatintel.IndicatorTypes
import com.thraksha.guardian.security.threatintel.ThreatIndicator
import com.thraksha.guardian.security.threatintel.ThreatPack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Off-device tests for network indicator matching + policy outcomes (guide §26). */
class NetworkThreatAndPolicyTest {

    private val demoIp = "203.0.113.113"

    private fun pack(vararg indicators: ThreatIndicator) = ThreatPack(
        schemaVersion = 1,
        packVersion = 2,
        generatedAt = "2026-08-13T00:00:00Z",
        indicators = indicators.toList(),
    )

    private fun networkIndicator(
        id: String = "demo-network-endpoint",
        value: String = demoIp,
        enabled: Boolean = true,
        type: String = IndicatorTypes.DESTINATION_IP,
        severity: String = "HIGH",
        weight: Int = 88,
    ) = ThreatIndicator(
        id = id, type = type, value = value,
        classification = "DEMO_TEST_NETWORK_INDICATOR", severity = severity,
        description = "controlled demo network indicator", source = "test",
        enabled = enabled, weight = weight,
    )

    private fun observation(
        destIp: String = demoIp,
        pkg: String = DemoAppRegistry.VILLAIN_CALLER,
        port: Int = 443,
    ) = NetworkObservation(
        timestamp = 1000L,
        packageName = pkg,
        ipVersion = 4,
        protocol = 17,
        protocolName = "UDP",
        sourceIp = "10.113.0.2",
        sourcePort = 40000,
        destinationIp = destIp,
        destinationPort = port,
        packetLength = 55,
    )

    // ---- evaluator ----

    @Test
    fun matchingDestinationIp_producesNetworkFinding() {
        val finding = NetworkThreatEvaluator(pack(networkIndicator())).evaluate(observation())
        assertNotNull(finding)
        assertEquals(FindingSource.NETWORK_THREAT_INTELLIGENCE, finding!!.source)
        assertEquals(DemoAppRegistry.VILLAIN_CALLER, finding.packageName)
        assertEquals(Severity.HIGH, finding.severity)
        assertEquals(88, finding.confidence)
        assertTrue(finding.evidence.any { it.contains("DEMO_TEST_NETWORK_INDICATOR") })
        assertTrue(
            "evidence must state payload was not inspected",
            finding.evidence.any { it.contains("payload contents were not") },
        )
    }

    @Test
    fun wrongDestinationIp_producesNoFinding() {
        val finding = NetworkThreatEvaluator(pack(networkIndicator()))
            .evaluate(observation(destIp = "203.0.113.200"))
        assertNull(finding)
    }

    @Test
    fun disabledNetworkIndicator_producesNoFinding() {
        val finding = NetworkThreatEvaluator(pack(networkIndicator(enabled = false)))
            .evaluate(observation())
        assertNull(finding)
    }

    @Test
    fun futureTypedIndicator_isNeverEvaluated() {
        val finding = NetworkThreatEvaluator(pack(networkIndicator(type = "DOMAIN")))
            .evaluate(observation())
        assertNull(finding)
    }

    @Test
    fun staticIndicatorTypes_areNotMatchedByNetworkEvaluator() {
        // A PACKAGE_NAME indicator must not be matched against a packet destination.
        val pkgIndicator = networkIndicator(type = IndicatorTypes.PACKAGE_NAME, value = demoIp)
        assertNull(NetworkThreatEvaluator(pack(pkgIndicator)).evaluate(observation()))
    }

    // ---- policy integration ----

    private fun networkFinding() = Finding(
        ruleId = "threat-intel:demo-network-endpoint",
        ruleName = "network",
        packageName = DemoAppRegistry.VILLAIN_CALLER,
        appName = "Villaincaller",
        appType = DemoAppRegistry.typeOf(DemoAppRegistry.VILLAIN_CALLER),
        severity = Severity.HIGH,
        confidence = 88,
        reason = "demo network indicator",
        evidence = listOf("e"),
        source = FindingSource.NETWORK_THREAT_INTELLIGENCE,
    )

    private fun decide(privilege: PrivilegeLevel, mode: ExecutionMode) = PolicyEngine.decide(
        packageName = DemoAppRegistry.VILLAIN_CALLER,
        appName = "Villaincaller",
        findings = listOf(networkFinding()),
        privilegeLevel = privilege,
        executionMode = mode,
    )

    @Test
    fun adviceMode_networkFinding_advises_recommendsBlock_notSuspensionOrPermission() {
        val decision = decide(PrivilegeLevel.NORMAL, ExecutionMode.OBSERVE)
        assertEquals(DecisionType.ADVISE, decision.decisionType)
        assertTrue(decision.recommendedActions.any { it.action is SecurityAction.BlockNetwork })
        // §31: a network-only finding must not trigger permission denial or suspension.
        assertFalse(decision.recommendedActions.any { it.action is SecurityAction.DenyPermission })
        assertFalse(decision.recommendedActions.any { it.action is SecurityAction.SuspendPackage })
    }

    @Test
    fun fullPower_observeMode_doesNotBlock() {
        assertEquals(
            DecisionType.OBSERVE,
            decide(PrivilegeLevel.DEVICE_OWNER, ExecutionMode.OBSERVE).decisionType,
        )
    }

    @Test
    fun fullPower_guidedMode_advises() {
        assertEquals(
            DecisionType.ADVISE,
            decide(PrivilegeLevel.DEVICE_OWNER, ExecutionMode.GUIDED).decisionType,
        )
    }

    @Test
    fun fullPower_autoDefend_actsWithBlockNetwork() {
        val decision = decide(PrivilegeLevel.DEVICE_OWNER, ExecutionMode.AUTO_DEFEND)
        assertEquals(DecisionType.ACT, decision.decisionType)
        assertTrue(
            decision.executableActions.any { it.action is SecurityAction.BlockNetwork },
        )
    }

    @Test
    fun blockNetwork_usesVpnGuardAuthority_notDeviceOwner() {
        assertEquals(RequiredAuthority.VPN_GUARD, SecurityAction.BlockNetwork.requiredAuthority)
        assertTrue(SecurityAction.BlockNetwork.isExecutable)
    }

    // ---- VPN scope safety invariant (guide §28) ----

    @Test
    fun vpnScope_allowsOnlyVillainCaller() {
        assertEquals(setOf(DemoAppRegistry.VILLAIN_CALLER), VpnScope.ALLOWED_PACKAGES)
        assertTrue(VpnScope.isAllowed(DemoAppRegistry.VILLAIN_CALLER))
    }

    @Test
    fun vpnScope_refusesGoodCallerGuardianAndArbitrary() {
        assertFalse(VpnScope.isAllowed(DemoAppRegistry.GOOD_CALLER))
        assertFalse(VpnScope.isAllowed("com.thraksha.guardian"))
        assertFalse(VpnScope.isAllowed("com.android.chrome"))
        assertFalse(VpnScope.isAllowed(""))
    }

    @Test
    fun vpnScope_routeIsTestNetOnly_notDefaultRoute() {
        // Never 0.0.0.0/0 — the device-wide capture state is unrepresentable.
        assertEquals("203.0.113.0", VpnScope.ROUTE_ADDRESS)
        assertEquals(24, VpnScope.ROUTE_PREFIX)
        assertFalse(VpnScope.ROUTE_ADDRESS == "0.0.0.0")
    }

    @Test
    fun goodCaller_cannotBecomeANetworkTarget_evenWithAMatchingPacket() {
        // Even if a packet were attributed to GoodCaller, VpnScope keeps it out of the
        // tunnel entirely — but defense in depth: the evaluator still attributes to the
        // observation's package, and policy for GoodCaller network findings is unreachable
        // because GoodCaller never enters the tunnel. Assert the scope guarantee directly.
        assertFalse(VpnScope.isAllowed(DemoAppRegistry.GOOD_CALLER))
    }
}
