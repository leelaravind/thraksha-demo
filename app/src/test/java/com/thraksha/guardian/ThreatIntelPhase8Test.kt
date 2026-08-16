package com.thraksha.guardian

import com.thraksha.guardian.security.inventory.AppType
import com.thraksha.guardian.security.inventory.ObservedApp
import com.thraksha.guardian.security.network.NetworkObservation
import com.thraksha.guardian.security.network.NetworkThreatEvaluator
import com.thraksha.guardian.security.threatintel.AppFingerprint
import com.thraksha.guardian.security.threatintel.IndicatorTier
import com.thraksha.guardian.security.threatintel.IndicatorTypes
import com.thraksha.guardian.security.threatintel.ThreatIndicator
import com.thraksha.guardian.security.threatintel.ThreatIntelligenceScanner
import com.thraksha.guardian.security.threatintel.ThreatPack
import com.thraksha.guardian.security.threatintel.ThreatPackParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Phase 8 threat-intelligence semantics (guide §§13–17, 35): provenance enforcement,
 * per-indicator expiry, pack-level staleness, evidentiary tiers, and backwards
 * compatibility of the extended schema.
 */
class ThreatIntelPhase8Test {

    private val now: Long = Instant.parse("2026-08-14T12:00:00Z").toEpochMilli()
    private val past = "2026-08-01T00:00:00Z"
    private val future = "2027-08-01T00:00:00Z"

    private fun indicator(
        id: String = "i1",
        type: String = IndicatorTypes.BASE_APK_SHA256,
        value: String = "ab".repeat(32),
        classification: String = "REAL_WORLD_THREAT",
        enabled: Boolean = true,
        sourceReference: String? = "https://example.invalid/ioc/1",
        expiresAt: String? = future,
        tier: String? = null,
    ) = ThreatIndicator(
        id = id,
        type = type,
        value = value,
        classification = classification,
        severity = "HIGH",
        description = "test",
        source = "test-source",
        enabled = enabled,
        weight = 90,
        sourceReference = sourceReference,
        expiresAt = expiresAt,
        tier = tier,
    )

    private fun pack(vararg indicators: ThreatIndicator, expiresAt: String? = null) =
        ThreatPack(
            schemaVersion = 1,
            packVersion = 3,
            generatedAt = past,
            expiresAt = expiresAt,
            indicators = indicators.toList(),
        )

    private fun app() = ObservedApp(
        packageName = "com.example.subject",
        displayName = "Subject",
        appType = AppType.UNKNOWN,
        requestedPermissions = emptySet(),
        grantedPermissions = emptySet(),
        capabilities = emptySet(),
        versionName = "1.0",
        firstInstallTime = 0L,
        lastUpdateTime = 0L,
        isDebuggable = false,
        isSystemApp = false,
    )

    private fun fingerprint(apkSha: String? = "ab".repeat(32)) = AppFingerprint(
        packageName = "com.example.subject",
        certSha256 = setOf("cd".repeat(32)),
        baseApkSha256 = apkSha,
        certDurationMs = 0,
        apkHashDurationMs = 0,
    )

    // ---- expiry semantics ----

    @Test
    fun unexpiredIndicator_matches() {
        val findings = ThreatIntelligenceScanner(pack(indicator()), now)
            .scan(app(), fingerprint())
        assertEquals(1, findings.size)
    }

    @Test
    fun expiredIndicator_neverMatches_butIsCountedAsExpired() {
        val scanner = ThreatIntelligenceScanner(
            pack(indicator(expiresAt = past)), now,
        )
        assertTrue(scanner.scan(app(), fingerprint()).isEmpty())
        assertEquals(1, scanner.expiredIndicators)
        assertEquals(0, scanner.activeIndicators)
    }

    @Test
    fun indicatorWithoutExpiry_neverExpires() {
        val scanner = ThreatIntelligenceScanner(
            pack(indicator(expiresAt = null)), now,
        )
        assertEquals(1, scanner.scan(app(), fingerprint()).size)
    }

    @Test
    fun packLevelStaleness_isDetected() {
        assertTrue(pack(indicator(), expiresAt = past).isStaleAt(now))
        assertFalse(pack(indicator(), expiresAt = future).isStaleAt(now))
        assertFalse(pack(indicator(), expiresAt = null).isStaleAt(now))
    }

    @Test
    fun expiredNetworkIndicator_neverMatchesLiveTraffic() {
        val observation = NetworkObservation(
            timestamp = now,
            packageName = "com.thraksha.demo.villaincaller",
            ipVersion = 4,
            protocol = 17,
            protocolName = "UDP",
            sourceIp = "10.0.0.2",
            sourcePort = 40000,
            destinationIp = "203.0.113.113",
            destinationPort = 443,
            packetLength = 55,
        )
        val live = indicator(
            id = "net", type = IndicatorTypes.DESTINATION_IP, value = "203.0.113.113",
            classification = "DEMO_TEST_NETWORK_INDICATOR", sourceReference = null,
            expiresAt = future,
        )
        val dead = live.copy(id = "net-dead", expiresAt = past)

        assertTrue(
            NetworkThreatEvaluator(pack(live), now).evaluate(observation) != null,
        )
        assertTrue(
            "an expired network indicator must not fire",
            NetworkThreatEvaluator(pack(dead), now).evaluate(observation) == null,
        )
    }

    // ---- tiers ----

    @Test
    fun tierDefaults_digestsStrong_packageNamesCorroborating() {
        assertEquals(
            IndicatorTier.STRONG,
            indicator(type = IndicatorTypes.BASE_APK_SHA256).effectiveTier(),
        )
        assertEquals(
            IndicatorTier.STRONG,
            indicator(type = IndicatorTypes.SIGNING_CERT_SHA256).effectiveTier(),
        )
        assertEquals(
            IndicatorTier.CORROBORATING,
            indicator(type = IndicatorTypes.PACKAGE_NAME, value = "a.b").effectiveTier(),
        )
        assertEquals(
            IndicatorTier.CORROBORATING,
            indicator(type = IndicatorTypes.DESTINATION_IP, value = "1.2.3.4").effectiveTier(),
        )
    }

    @Test
    fun declaredTier_overridesDefault() {
        assertEquals(
            IndicatorTier.CORROBORATING,
            indicator(tier = "CORROBORATING").effectiveTier(),
        )
    }

    @Test
    fun scannerFindings_carryTheTier() {
        val finding = ThreatIntelligenceScanner(pack(indicator()), now)
            .scan(app(), fingerprint())
            .single()
        assertEquals(IndicatorTier.STRONG, finding.intelligenceTier)
    }

    // ---- parser: provenance & freshness fail closed (guide §§13, 35) ----

    private fun fixtureJson(indicatorJson: String, packExpiry: String? = null) = """
        {"schemaVersion": 1, "packVersion": 3,
         "generatedAt": "2026-08-13T00:00:00Z",
         ${packExpiry?.let { "\"expiresAt\": \"$it\"," } ?: ""}
         "indicators": [$indicatorJson]}
    """.trimIndent().toByteArray()

    private fun realIndicatorJson(
        sourceReference: String? = "\"https://example.invalid/1\"",
        expiresAt: String = "\"2027-01-01T00:00:00Z\"",
        tier: String = "\"STRONG\"",
    ) = """
        {"id": "r1", "type": "BASE_APK_SHA256", "value": "${"ab".repeat(32)}",
         "classification": "REAL_WORLD_THREAT", "severity": "HIGH",
         "description": "d", "source": "s", "enabled": true, "weight": 95,
         ${sourceReference?.let { "\"sourceReference\": $it," } ?: ""}
         "expiresAt": $expiresAt, "tier": $tier}
    """.trimIndent()

    private fun parseFails(bytes: ByteArray) =
        runCatching { ThreatPackParser.parse(bytes) }.isFailure

    @Test
    fun realIndicator_withProvenance_parses() {
        val pack = ThreatPackParser.parse(fixtureJson(realIndicatorJson()))
        assertEquals(1, pack.indicators.size)
    }

    @Test
    fun realIndicator_withoutSourceReference_isRejected() {
        assertTrue(parseFails(fixtureJson(realIndicatorJson(sourceReference = null))))
    }

    @Test
    fun malformedIndicatorExpiry_isRejected() {
        assertTrue(
            parseFails(fixtureJson(realIndicatorJson(expiresAt = "\"not-a-date\""))),
        )
    }

    @Test
    fun malformedPackExpiry_isRejected() {
        assertTrue(
            parseFails(fixtureJson(realIndicatorJson(), packExpiry = "whenever")),
        )
    }

    @Test
    fun unknownTier_isRejected() {
        assertTrue(
            parseFails(fixtureJson(realIndicatorJson(tier = "\"PROBABLY_FINE\""))),
        )
    }

    @Test
    fun prePhase8Pack_withoutNewFields_stillParses() {
        // Backwards compatibility: the Phase 6/7 schema (no provenance fields) is valid.
        val legacy = """
            {"schemaVersion": 1, "packVersion": 2,
             "generatedAt": "2026-08-13T00:00:00Z",
             "indicators": [
               {"id": "demo", "type": "PACKAGE_NAME", "value": "com.example.x",
                "classification": "DEMO_TEST_THREAT", "severity": "HIGH",
                "description": "d", "source": "s", "enabled": true, "weight": 85}]}
        """.trimIndent().toByteArray()
        val pack = ThreatPackParser.parse(legacy)
        assertEquals(1, pack.indicators.size)
        assertFalse(pack.indicators[0].isExpiredAt(now))
    }
}
