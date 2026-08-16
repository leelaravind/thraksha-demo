package com.thraksha.guardian

import com.thraksha.guardian.security.engine.FindingSource
import com.thraksha.guardian.security.events.Severity
import com.thraksha.guardian.security.inventory.AppType
import com.thraksha.guardian.security.inventory.DemoAppRegistry
import com.thraksha.guardian.security.inventory.ObservedApp
import com.thraksha.guardian.security.rulepack.RulepackVerifier
import com.thraksha.guardian.security.threatintel.AppFingerprint
import com.thraksha.guardian.security.threatintel.IndicatorTypes
import com.thraksha.guardian.security.threatintel.ThreatIndicator
import com.thraksha.guardian.security.threatintel.ThreatIntelligenceScanner
import com.thraksha.guardian.security.threatintel.ThreatPack
import com.thraksha.guardian.security.threatintel.ThreatPackParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Off-device tests for the threat-intelligence pillar, run against the **real signed
 * threat pack asset** (signature + schema) plus fixture packs for every fail-closed
 * validation rule and every matching path.
 */
class ThreatPackTest {

    private fun asset(name: String): File =
        listOf(File("src/main/assets/$name"), File("app/src/main/assets/$name"))
            .firstOrNull { it.exists() }
            ?: error("asset not found: $name")

    private fun packBytes() = asset("threatpack.json").readBytes()
    private fun signature() = asset("threatpack.sig").readText()
    private fun publicKey() = asset("threatpack_public.key").readText()

    // ---- signature verification (same primitive as the rulepack, its own key pair) ----

    @Test
    fun bundledThreatPack_signatureVerifies_andParses() {
        assertTrue(
            "bundled threatpack must verify — re-run tools/make_threatpack.sh",
            RulepackVerifier.verify(packBytes(), signature(), publicKey()),
        )
        val pack = ThreatPackParser.parse(packBytes())
        assertEquals(1, pack.schemaVersion)
        assertTrue(pack.indicators.isNotEmpty())
    }

    @Test
    fun oneFlippedByte_failsVerification() {
        val tampered = packBytes().copyOf()
            .also { it[it.size / 2] = (it[it.size / 2].toInt() xor 1).toByte() }
        assertFalse(RulepackVerifier.verify(tampered, signature(), publicKey()))
    }

    @Test
    fun modifiedSignature_failsVerification() {
        val brokenSig = signature().toCharArray()
            .also { it[10] = if (it[10] == 'A') 'B' else 'A' }
            .concatToString()
        assertFalse(RulepackVerifier.verify(packBytes(), brokenSig, publicKey()))
    }

    @Test
    fun wrongPublicKey_failsVerification() {
        // The rulepack's key is a valid RSA key — but not the threat pack's trust root.
        val rulepackKey = asset("rulepack_public.key").readText()
        assertFalse(RulepackVerifier.verify(packBytes(), signature(), rulepackKey))
    }

    // ---- schema validation, fail closed ----

    private fun fixture(
        schemaVersion: Int = 1,
        indicators: String,
    ): ByteArray = """
        {"schemaVersion": $schemaVersion, "packVersion": 1,
         "generatedAt": "2026-08-13T00:00:00Z", "indicators": [$indicators]}
    """.trimIndent().toByteArray()

    private fun indicatorJson(
        id: String = "i1",
        type: String = "PACKAGE_NAME",
        value: String = "com.example.app",
        enabled: Boolean = true,
        weight: Int = 80,
    ) = """
        {"id": "$id", "type": "$type", "value": "$value",
         "classification": "DEMO_TEST_THREAT", "severity": "HIGH",
         "description": "d", "source": "s", "enabled": $enabled, "weight": $weight}
    """.trimIndent()

    private fun parseFails(bytes: ByteArray): Boolean =
        runCatching { ThreatPackParser.parse(bytes) }.isFailure

    @Test
    fun unsupportedSchemaVersion_isRejected() {
        assertTrue(parseFails(fixture(schemaVersion = 2, indicators = indicatorJson())))
    }

    @Test
    fun duplicateIndicatorIds_areRejected() {
        assertTrue(
            parseFails(fixture(indicators = "${indicatorJson("dup")}, ${indicatorJson("dup")}")),
        )
    }

    @Test
    fun malformedHashLength_isRejected() {
        assertTrue(
            parseFails(
                fixture(indicators = indicatorJson(type = "BASE_APK_SHA256", value = "abc123")),
            ),
        )
    }

    @Test
    fun nonHexHash_isRejected() {
        val notHex = "z".repeat(64)
        assertTrue(
            parseFails(
                fixture(indicators = indicatorJson(type = "SIGNING_CERT_SHA256", value = notHex)),
            ),
        )
    }

    @Test
    fun uppercaseHexHash_isRejected_digestsAreCanonicalLowercase() {
        val upper = "A".repeat(64)
        assertTrue(
            parseFails(
                fixture(indicators = indicatorJson(type = "BASE_APK_SHA256", value = upper)),
            ),
        )
    }

    @Test
    fun unknownIndicatorType_rejectsTheWholePack() {
        assertTrue(parseFails(fixture(indicators = indicatorJson(type = "TOTALLY_NEW_TYPE"))))
    }

    @Test
    fun futureType_parses_butIsNeverEvaluated() {
        val pack = ThreatPackParser.parse(
            fixture(
                indicators = indicatorJson(
                    type = "DOMAIN",
                    value = "evil.example.invalid",
                ),
            ),
        )
        assertEquals(1, pack.indicators.size)
        // and the scanner never evaluates it:
        val findings = ThreatIntelligenceScanner(pack)
            .scan(app(DemoAppRegistry.VILLAIN_CALLER), fingerprint())
        assertTrue("FUTURE types must never match: $findings", findings.isEmpty())
    }

    @Test
    fun weightOutsideRange_isRejected() {
        assertTrue(parseFails(fixture(indicators = indicatorJson(weight = 101))))
    }

    @Test
    fun malformedJson_isRejected() {
        assertTrue(parseFails("{not json".toByteArray()))
    }

    // ---- matching (pure scanner) ----

    private companion object {
        val VILLAIN_CERT = "cc".repeat(32)
        val VILLAIN_APK = "ab".repeat(32)
    }

    private fun app(packageName: String) = ObservedApp(
        packageName = packageName,
        displayName = "App",
        appType = AppType.CALLER_ID,
        requestedPermissions = emptySet(),
        grantedPermissions = emptySet(),
        capabilities = emptySet(),
        versionName = "1.0",
        firstInstallTime = 0L,
        lastUpdateTime = 0L,
        isDebuggable = true,
        isSystemApp = false,
    )

    private fun fingerprint(
        packageName: String = DemoAppRegistry.VILLAIN_CALLER,
        certs: Set<String> = setOf(VILLAIN_CERT),
        apkSha: String? = VILLAIN_APK,
    ) = AppFingerprint(
        packageName = packageName,
        certSha256 = certs,
        baseApkSha256 = apkSha,
        certDurationMs = 0,
        apkHashDurationMs = 0,
    )

    private fun demoPack(vararg indicators: ThreatIndicator) = ThreatPack(
        schemaVersion = 1,
        packVersion = 7,
        generatedAt = "2026-08-13T00:00:00Z",
        indicators = indicators.toList(),
    )

    private fun indicator(
        id: String,
        type: String,
        value: String,
        enabled: Boolean = true,
        severity: String = "HIGH",
        weight: Int = 90,
    ) = ThreatIndicator(
        id = id, type = type, value = value,
        classification = "DEMO_TEST_THREAT", severity = severity,
        description = "demo record $id", source = "test", enabled = enabled, weight = weight,
    )

    @Test
    fun packageCertAndApkIndicators_allMatchTheirOwnDimension() {
        val pack = demoPack(
            indicator("pkg", IndicatorTypes.PACKAGE_NAME, DemoAppRegistry.VILLAIN_CALLER),
            indicator("cert", IndicatorTypes.SIGNING_CERT_SHA256, VILLAIN_CERT),
            indicator("apk", IndicatorTypes.BASE_APK_SHA256, VILLAIN_APK),
        )
        val findings = ThreatIntelligenceScanner(pack)
            .scan(app(DemoAppRegistry.VILLAIN_CALLER), fingerprint())

        assertEquals(3, findings.size)
        assertEquals(
            setOf("threat-intel:pkg", "threat-intel:cert", "threat-intel:apk"),
            findings.map { it.ruleId }.toSet(),
        )
        assertTrue(findings.all { it.source == FindingSource.THREAT_INTELLIGENCE })
        assertTrue(findings.all { it.severity == Severity.HIGH })
        assertTrue(
            "classification must be carried as evidence",
            findings.all { f -> f.evidence.any { it.contains("DEMO_TEST_THREAT") } },
        )
    }

    @Test
    fun cleanApp_matchesNothing() {
        val pack = demoPack(
            indicator("pkg", IndicatorTypes.PACKAGE_NAME, DemoAppRegistry.VILLAIN_CALLER),
            indicator("cert", IndicatorTypes.SIGNING_CERT_SHA256, VILLAIN_CERT),
            indicator("apk", IndicatorTypes.BASE_APK_SHA256, VILLAIN_APK),
        )
        val cleanPrint = fingerprint(
            packageName = DemoAppRegistry.GOOD_CALLER,
            certs = setOf("dd".repeat(32)),
            apkSha = "ee".repeat(32),
        )
        val findings = ThreatIntelligenceScanner(pack)
            .scan(app(DemoAppRegistry.GOOD_CALLER), cleanPrint)
        assertTrue("a clean app must produce no matches: $findings", findings.isEmpty())
    }

    @Test
    fun wrongHash_doesNotMatch() {
        val pack = demoPack(indicator("apk", IndicatorTypes.BASE_APK_SHA256, "12".repeat(32)))
        assertTrue(
            ThreatIntelligenceScanner(pack)
                .scan(app(DemoAppRegistry.VILLAIN_CALLER), fingerprint()).isEmpty(),
        )
    }

    @Test
    fun disabledIndicator_neverMatches() {
        val pack = demoPack(
            indicator(
                "pkg", IndicatorTypes.PACKAGE_NAME, DemoAppRegistry.VILLAIN_CALLER,
                enabled = false,
            ),
        )
        assertTrue(
            ThreatIntelligenceScanner(pack)
                .scan(app(DemoAppRegistry.VILLAIN_CALLER), fingerprint()).isEmpty(),
        )
    }

    @Test
    fun missingApkHash_failsSafe_certAndPackageStillEvaluated() {
        val pack = demoPack(
            indicator("pkg", IndicatorTypes.PACKAGE_NAME, DemoAppRegistry.VILLAIN_CALLER),
            indicator("apk", IndicatorTypes.BASE_APK_SHA256, VILLAIN_APK),
        )
        val findings = ThreatIntelligenceScanner(pack)
            .scan(app(DemoAppRegistry.VILLAIN_CALLER), fingerprint(apkSha = null))
        assertEquals(listOf("threat-intel:pkg"), findings.map { it.ruleId })
    }

    @Test
    fun indicatorWeight_isCarriedAsRuleConfidence_notInvented() {
        val pack = demoPack(
            indicator(
                "pkg", IndicatorTypes.PACKAGE_NAME, DemoAppRegistry.VILLAIN_CALLER,
                weight = 42,
            ),
        )
        val finding = ThreatIntelligenceScanner(pack)
            .scan(app(DemoAppRegistry.VILLAIN_CALLER), fingerprint()).single()
        assertEquals(42, finding.confidence)
    }

    // ---- the real bundled pack's content (demo indicators + Phase 8 real corpus) ----

    @Test
    fun bundledPack_targetsOnlyVillainCaller_withDemoClassification() {
        val pack = ThreatPackParser.parse(packBytes())
        val active = pack.indicators.filter {
            it.enabled && it.type in IndicatorTypes.SUPPORTED
        }
        assertTrue(active.isNotEmpty())

        // The only enabled PACKAGE_NAME record must point at the controlled sample —
        // the Phase 8 research bans package-name indicators without a legitimate source,
        // and no legitimate public source for them exists.
        val packageIndicators = active.filter { it.type == IndicatorTypes.PACKAGE_NAME }
        assertTrue(
            packageIndicators.all { it.value == DemoAppRegistry.VILLAIN_CALLER },
        )
        // Every matchable record about the sample is honestly classified.
        assertNotNull(
            active.firstOrNull { it.classification == "DEMO_TEST_THREAT" },
        )
        // Phase 8: classifications are either the controlled demo set or real-world
        // sourced intelligence. A REAL_* record without traceable provenance, or any
        // other classification, must not exist. (The original intent — no fabricated
        // malware family may be claimed — is preserved: real families are carried only
        // on records with a sourceReference to the origin intelligence record.)
        val demoClassifications = setOf(
            "DEMO_TEST_THREAT",
            "DEMO_NEGATIVE_CONTROL",
            "DEMO_TEST_NETWORK_INDICATOR",
        )
        pack.indicators.forEach {
            assertTrue(
                "unexpected classification ${it.classification} on ${it.id}",
                it.classification in demoClassifications ||
                    it.classification.startsWith("REAL_"),
            )
            if (it.classification.startsWith("REAL_")) {
                assertTrue(
                    "real-world indicator ${it.id} must carry provenance",
                    !it.sourceReference.isNullOrBlank(),
                )
            }
        }
    }

    @Test
    fun bundledPack_carriesTheRealWorldCorpus_withProvenanceAndExpiry() {
        val pack = ThreatPackParser.parse(packBytes())
        val real = pack.indicators.filter { it.classification.startsWith("REAL_") }
        assertTrue(
            "the Phase 8 pack must carry a real corpus (found ${real.size})",
            real.size >= 1000,
        )
        assertTrue(
            "every real indicator needs provenance",
            real.all { !it.sourceReference.isNullOrBlank() && it.source.isNotBlank() },
        )
        assertTrue(
            "every real indicator needs an expiry",
            real.all { it.expiresAt != null },
        )
        // Strong evidence only from digests; network IOCs must be corroborating-tier.
        assertTrue(
            real.filter { it.type == IndicatorTypes.DESTINATION_IP }
                .all {
                    it.effectiveTier() ==
                        com.thraksha.guardian.security.threatintel.IndicatorTier.CORROBORATING
                },
        )
    }

    @Test
    fun bundledPack_carriesTheEnabledDemoNetworkIndicator() {
        val pack = ThreatPackParser.parse(packBytes())
        assertTrue("pack must be at least v2 with the network indicator", pack.packVersion >= 2)
        val net = pack.indicators.firstOrNull {
            it.id == "demo-network-endpoint" && it.type == IndicatorTypes.DESTINATION_IP
        }
        assertNotNull(net)
        assertTrue(net!!.enabled)
        assertEquals("DEMO_TEST_NETWORK_INDICATOR", net.classification)
        assertEquals("203.0.113.113", net.value)
    }

    @Test
    fun malformedDestinationIp_isRejected() {
        val bad = fixture(
            indicators = indicatorJson(type = "DESTINATION_IP", value = "999.1.1.1"),
        )
        assertTrue(parseFails(bad))
    }

    @Test
    fun validDestinationIp_parses() {
        val pack = ThreatPackParser.parse(
            fixture(indicators = indicatorJson(type = "DESTINATION_IP", value = "203.0.113.5")),
        )
        assertEquals(1, pack.indicators.size)
    }
}
