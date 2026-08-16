package com.thraksha.guardian.security.scan

import com.thraksha.guardian.security.engine.Finding
import com.thraksha.guardian.security.engine.FindingSource
import com.thraksha.guardian.security.events.Severity
import com.thraksha.guardian.security.threatintel.IndicatorTier

/**
 * Pure, deterministic mapping from one app's evidence to its [ScanClassification]
 * (guide §§10, 14, 17). No Android types, no clock, no package names — classification
 * depends only on what was found and whether the evidence base was complete.
 *
 * The ladder, strongest claim first:
 *
 *  1. **KNOWN_THREAT_MATCH** — only a STRONG-tier threat-intelligence match (exact APK
 *     digest / vetted signer digest) can claim it. A package-name (CORROBORATING) match
 *     can never reach this rung on its own, however many there are (guide §14: package
 *     names are mutable/spoofable corroboration).
 *  2. **HIGH_RISK_PROFILE** — HIGH/CRITICAL contextual (profile) or generic findings,
 *     without an authoritative intelligence match.
 *  3. **REVIEW** — any remaining findings, including CORROBORATING-tier intelligence
 *     matches.
 *  4. **PARTIAL** — nothing found, but the evidence base was incomplete (fingerprint
 *     failure, or intelligence not ACTIVE). Missing evidence is never "clean".
 *  5. **NO_KNOWN_FINDINGS** — nothing found, with complete evidence and active
 *     intelligence.
 *
 * Findings-bearing apps classify by their findings even when intelligence is degraded —
 * a REVIEW discovered without intelligence is still a REVIEW; only the *absence* of
 * findings is downgraded to PARTIAL.
 */
object ScanClassifier {

    fun classify(
        findings: List<Finding>,
        evidenceComplete: Boolean,
        intelligenceStatus: IntelligenceState.Status,
    ): ScanClassification {
        val strongIntelMatch = findings.any {
            it.source == FindingSource.THREAT_INTELLIGENCE &&
                it.intelligenceTier == IndicatorTier.STRONG
        }
        if (strongIntelMatch) return ScanClassification.KNOWN_THREAT_MATCH

        val highStaticFinding = findings.any {
            it.source != FindingSource.THREAT_INTELLIGENCE && it.severity >= Severity.HIGH
        }
        if (highStaticFinding) return ScanClassification.HIGH_RISK_PROFILE

        if (findings.isNotEmpty()) return ScanClassification.REVIEW

        val evidenceBaseSound =
            evidenceComplete && intelligenceStatus == IntelligenceState.Status.ACTIVE
        return if (evidenceBaseSound) {
            ScanClassification.NO_KNOWN_FINDINGS
        } else {
            ScanClassification.PARTIAL
        }
    }
}
