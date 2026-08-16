package com.thraksha.guardian

import com.thraksha.guardian.security.engine.Finding
import com.thraksha.guardian.security.engine.FindingSource
import com.thraksha.guardian.security.events.Severity
import com.thraksha.guardian.security.inventory.AppType
import com.thraksha.guardian.security.scan.IntelligenceState
import com.thraksha.guardian.security.scan.ScanClassification
import com.thraksha.guardian.security.scan.ScanClassifier
import com.thraksha.guardian.security.threatintel.IndicatorTier
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The Phase 8 classification ladder (guide §§10, 14, 17, 36): pure and deterministic.
 * The central honesty properties:
 *
 *  * only a STRONG-tier intelligence match may claim KNOWN THREAT MATCH;
 *  * a package-name (CORROBORATING) match can never carry a threat verdict alone;
 *  * missing evidence is PARTIAL, never NO KNOWN FINDINGS;
 *  * classification never depends on what an app is called.
 */
class ScanClassifierTest {

    private fun finding(
        source: FindingSource,
        severity: Severity = Severity.MEDIUM,
        tier: IndicatorTier? = null,
        packageName: String = "com.example.anything",
    ) = Finding(
        ruleId = "r",
        ruleName = "r",
        packageName = packageName,
        appName = "App",
        appType = AppType.UNKNOWN,
        severity = severity,
        confidence = 50,
        reason = "test",
        evidence = listOf("test evidence"),
        source = source,
        intelligenceTier = tier,
    )

    private fun classify(
        findings: List<Finding>,
        evidenceComplete: Boolean = true,
        intel: IntelligenceState.Status = IntelligenceState.Status.ACTIVE,
    ) = ScanClassifier.classify(findings, evidenceComplete, intel)

    // ---- the ladder ----

    @Test
    fun noFindings_completeEvidence_activeIntel_isNoKnownFindings() {
        assertEquals(ScanClassification.NO_KNOWN_FINDINGS, classify(emptyList()))
    }

    @Test
    fun strongIntelMatch_isKnownThreatMatch() {
        val f = finding(
            FindingSource.THREAT_INTELLIGENCE, Severity.HIGH, IndicatorTier.STRONG,
        )
        assertEquals(ScanClassification.KNOWN_THREAT_MATCH, classify(listOf(f)))
    }

    @Test
    fun packageNameOnlyMatch_isReview_neverKnownThreat() {
        // The §36 requirement: a weak package-name-only match must not independently
        // produce an authoritative malware verdict.
        val weak = finding(
            FindingSource.THREAT_INTELLIGENCE, Severity.HIGH, IndicatorTier.CORROBORATING,
        )
        assertEquals(ScanClassification.REVIEW, classify(listOf(weak)))
    }

    @Test
    fun multipleCorroboratingMatches_stillNotKnownThreat() {
        val weak = finding(
            FindingSource.THREAT_INTELLIGENCE, Severity.CRITICAL, IndicatorTier.CORROBORATING,
        )
        assertEquals(
            ScanClassification.REVIEW,
            classify(listOf(weak, weak.copy(ruleId = "r2"), weak.copy(ruleId = "r3"))),
        )
    }

    @Test
    fun highSeverityGenericFinding_isHighRiskProfile() {
        val f = finding(FindingSource.GENERIC_RULE, Severity.HIGH)
        assertEquals(ScanClassification.HIGH_RISK_PROFILE, classify(listOf(f)))
    }

    @Test
    fun criticalProfileFinding_isHighRiskProfile() {
        val f = finding(FindingSource.PROFILE_RULE, Severity.CRITICAL)
        assertEquals(ScanClassification.HIGH_RISK_PROFILE, classify(listOf(f)))
    }

    @Test
    fun mediumGenericFindings_areReview() {
        val f = finding(FindingSource.GENERIC_RULE, Severity.MEDIUM)
        assertEquals(ScanClassification.REVIEW, classify(listOf(f, f.copy(ruleId = "r2"))))
    }

    @Test
    fun strongIntelMatch_outranksHighStaticFindings() {
        val findings = listOf(
            finding(FindingSource.PROFILE_RULE, Severity.CRITICAL),
            finding(FindingSource.THREAT_INTELLIGENCE, Severity.HIGH, IndicatorTier.STRONG),
        )
        assertEquals(ScanClassification.KNOWN_THREAT_MATCH, classify(findings))
    }

    // ---- missing evidence is never clean (guide §10, §36) ----

    @Test
    fun noFindings_incompleteFingerprint_isPartial_notClean() {
        assertEquals(
            ScanClassification.PARTIAL,
            classify(emptyList(), evidenceComplete = false),
        )
    }

    @Test
    fun noFindings_unavailableIntelligence_isPartial_notClean() {
        assertEquals(
            ScanClassification.PARTIAL,
            classify(emptyList(), intel = IntelligenceState.Status.UNAVAILABLE),
        )
    }

    @Test
    fun noFindings_staleIntelligence_isPartial_notClean() {
        assertEquals(
            ScanClassification.PARTIAL,
            classify(emptyList(), intel = IntelligenceState.Status.STALE),
        )
    }

    @Test
    fun findings_surviveDegradedIntelligence() {
        // A REVIEW discovered without intelligence is still a REVIEW.
        val f = finding(FindingSource.GENERIC_RULE, Severity.MEDIUM)
        assertEquals(
            ScanClassification.REVIEW,
            classify(listOf(f), intel = IntelligenceState.Status.UNAVAILABLE),
        )
    }

    // ---- generalization: identical evidence, different names (guide §26) ----

    @Test
    fun classificationIsIdentical_forIdenticalEvidence_whateverThePackageName() {
        val evidences = listOf(
            listOf(finding(FindingSource.GENERIC_RULE, Severity.HIGH)),
            listOf(
                finding(
                    FindingSource.THREAT_INTELLIGENCE, Severity.HIGH, IndicatorTier.STRONG,
                ),
            ),
            emptyList(),
        )
        for (evidence in evidences) {
            val asVillain = classify(
                evidence.map { it.copy(packageName = "com.thraksha.demo.villaincaller") },
            )
            val asRandom = classify(
                evidence.map { it.copy(packageName = "org.random.package.name") },
            )
            assertEquals(
                "same evidence must classify the same for any package name",
                asVillain,
                asRandom,
            )
        }
    }
}
