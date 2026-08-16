package com.thraksha.guardian.security.evidence

import com.thraksha.guardian.security.engine.Finding
import com.thraksha.guardian.security.inventory.GrantState
import com.thraksha.guardian.security.scan.ScanClassification

/**
 * The honest user-facing status vocabulary (guide §13, §22, §23).
 *
 * Distinct from [ScanClassification] on purpose: classification is the detection-side
 * truth (what was found), the display status is what that means for the user right now.
 * Phase 8 tests and audit semantics for classification are untouched.
 */
enum class AppDisplayStatus {
    /**
     * Thraksha knows the app's capability exposure and currently has no evidence
     * requiring action. Covers no-findings apps and declared-only capability exposure
     * (guide §22: "Stage 1 only → WATCHING").
     */
    WATCHING,

    /** Granted/enabled capability exposure (or corroborating signals) warrant review. */
    REVIEW,

    /**
     * A policy decision recommended concrete responses (verified behavior or a critical
     * profile mismatch) — advice has been issued.
     */
    ADVISED,

    /** A STRONG-tier signed threat-intelligence match. */
    KNOWN_THREAT_MATCH,

    /** Required evidence could not be obtained; missing evidence is never clean. */
    PARTIAL,
}

/**
 * Pure classification → display mapping.
 *
 * The core Phase 8.1 recalibration lives here (guide §11, §23): a status escalates past
 * WATCHING only on evidence *beyond* Stage 1. A pile of declared-but-ungranted
 * capabilities is capability exposure to keep watching, not a red card.
 */
object StatusMapper {

    /**
     * Capability rows whose GRANTED stage should not, alone, escalate WATCHING → REVIEW.
     * `INTERNET` is granted at install to essentially every networked app; treating it
     * as elevated exposure would make WATCHING unreachable. (QUERY_ALL_PACKAGES, also
     * install-time, IS treated as elevated — broad visibility is a real exposure.)
     */
    private val UNIVERSAL_GRANTS = setOf(CapabilityCatalog.NETWORK)

    fun map(
        classification: ScanClassification,
        findings: List<Finding>,
        evidence: List<CapabilityEvidence>,
        context: AppContextAssessment,
    ): AppDisplayStatus = when (classification) {
        ScanClassification.KNOWN_THREAT_MATCH -> AppDisplayStatus.KNOWN_THREAT_MATCH

        ScanClassification.HIGH_RISK_PROFILE ->
            // Suspicious context (critical profile mismatch / observed indicator match)
            // means advice was genuinely issued; a purely static high-exposure profile
            // is a REVIEW with "high capability exposure" framing, never fake behavior.
            if (context.assessment == ContextAssessment.SUSPICIOUS) {
                AppDisplayStatus.ADVISED
            } else {
                AppDisplayStatus.REVIEW
            }

        ScanClassification.REVIEW ->
            if (hasEvidenceBeyondDeclaration(evidence)) {
                AppDisplayStatus.REVIEW
            } else {
                AppDisplayStatus.WATCHING
            }

        ScanClassification.NO_KNOWN_FINDINGS -> AppDisplayStatus.WATCHING

        ScanClassification.PARTIAL -> AppDisplayStatus.PARTIAL
    }

    /** True when any capability row reaches GRANTED/ENABLED (beyond universal grants) or OBSERVED. */
    fun hasEvidenceBeyondDeclaration(evidence: List<CapabilityEvidence>): Boolean =
        evidence.any { row ->
            row.observed ||
                (
                    (row.grantState == GrantState.GRANTED || row.grantState == GrantState.ENABLED) &&
                        row.capabilityId !in UNIVERSAL_GRANTS
                    )
        }

    /** The §34-style one-line recommendation for each status. */
    fun recommendation(status: AppDisplayStatus): String = when (status) {
        AppDisplayStatus.WATCHING ->
            "Keep watching — capability exposure is known; no evidence requires action."
        AppDisplayStatus.REVIEW ->
            "Review recommended — granted or enabled capabilities are broad; check the " +
                "app's permissions in settings if it is not trusted."
        AppDisplayStatus.ADVISED ->
            "Advice issued — see the recommended responses for this app."
        AppDisplayStatus.KNOWN_THREAT_MATCH ->
            "Known threat intelligence match — act on this app."
        AppDisplayStatus.PARTIAL ->
            "Evidence incomplete — this result must not be read as clean."
    }
}
