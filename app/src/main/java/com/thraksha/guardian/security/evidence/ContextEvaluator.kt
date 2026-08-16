package com.thraksha.guardian.security.evidence

import com.thraksha.guardian.security.engine.Finding
import com.thraksha.guardian.security.engine.FindingSource
import com.thraksha.guardian.security.events.Severity
import com.thraksha.guardian.security.inventory.AppType
import com.thraksha.guardian.security.inventory.GrantState
import com.thraksha.guardian.security.threatintel.IndicatorTier

/**
 * Deterministic context assessment (guide §9): after the evidence stages are assembled,
 * does the app's capability picture make sense?
 *
 * Pure and package-name-blind — identical inputs produce identical assessments whatever
 * the app is called (the §26 rule carried forward from Phase 8). No app intent is ever
 * invented: without a baseline the honest answer is UNKNOWN, not EXPECTED.
 *
 * The sensitive-capability set for observed-behavior anomalies mirrors the catalog's
 * privacy-sensitive groups; NETWORK observation alone is deliberately not anomalous
 * (every networked app produces it) — network behavior becomes SUSPICIOUS only through
 * a signed indicator match, which arrives as a finding, not as raw observation.
 */
object ContextEvaluator {

    private val SENSITIVE_OBSERVED_CAPABILITIES = setOf(
        CapabilityCatalog.CONTACTS,
        CapabilityCatalog.LOCATION,
        CapabilityCatalog.CAMERA,
        CapabilityCatalog.MICROPHONE,
        CapabilityCatalog.MEDIA,
        CapabilityCatalog.SMS,
    )

    fun evaluate(
        appType: AppType,
        isSystemApp: Boolean,
        evidence: List<CapabilityEvidence>,
        findings: List<Finding>,
    ): AppContextAssessment {
        val hasBaseline = appType != AppType.UNKNOWN

        // 1. Authoritative threat intelligence: independent evidence dimension (guide
        // §24), and the strongest context signal there is.
        val strongIntel = findings.any {
            it.source == FindingSource.THREAT_INTELLIGENCE &&
                it.intelligenceTier == IndicatorTier.STRONG
        }
        if (strongIntel) {
            return AppContextAssessment(
                ContextAssessment.SUSPICIOUS,
                "Signed threat intelligence carries a strong match for this app's " +
                    "identity — suspicious regardless of runtime observation.",
            )
        }

        // 2. Verified runtime behavior that matched a signed network indicator.
        val observedIndicatorMatch = findings.any {
            it.source == FindingSource.NETWORK_THREAT_INTELLIGENCE
        }
        if (observedIndicatorMatch) {
            return AppContextAssessment(
                ContextAssessment.SUSPICIOUS,
                "An actually observed outbound network attempt matched a signed threat " +
                    "indicator — verified behavior, not a static profile.",
            )
        }

        // 3. Critical contextual profile mismatch against a deterministic baseline.
        val criticalProfileMismatch = findings.any {
            it.source == FindingSource.PROFILE_RULE && it.severity == Severity.CRITICAL
        }
        if (hasBaseline && criticalProfileMismatch) {
            return AppContextAssessment(
                ContextAssessment.SUSPICIOUS,
                "The app's declared capability profile critically contradicts the " +
                    "deterministic baseline for its type (${appType.name}).",
            )
        }

        // 4. Verified use of a privacy-sensitive capability with no baseline to judge
        // it against (guide §9's unknown-flashlight example). Unusual, not condemned.
        val sensitiveObserved = evidence.any {
            it.observed && it.capabilityId in SENSITIVE_OBSERVED_CAPABILITIES
        }
        if (!hasBaseline && sensitiveObserved) {
            return AppContextAssessment(
                ContextAssessment.UNUSUAL,
                "Verified use of a privacy-sensitive capability was observed, and no " +
                    "baseline exists for this app's type to explain it.",
            )
        }

        // 5. Typed app conforming to its baseline.
        if (hasBaseline && findings.isEmpty()) {
            return AppContextAssessment(
                ContextAssessment.EXPECTED,
                "The app matches the deterministic baseline for its type (${appType.name}).",
            )
        }

        // 6. System-image package without findings: platform provenance is a real,
        // deterministic context signal (not an exemption — findings still count above).
        if (isSystemApp && findings.isEmpty()) {
            return AppContextAssessment(
                ContextAssessment.EXPECTED,
                "System-image package with no findings; capability set consistent with " +
                    "platform provenance.",
            )
        }

        // 7. Everything else: no baseline → no invented intent.
        val exposure = evidence.count {
            it.grantState == GrantState.GRANTED || it.grantState == GrantState.ENABLED
        }
        return AppContextAssessment(
            ContextAssessment.UNKNOWN,
            if (exposure > 0) {
                "Powerful or sensitive capabilities are present ($exposure currently " +
                    "granted/enabled) but no baseline exists for this app type and no " +
                    "suspicious behavior has been observed — context is unknown, not bad."
            } else {
                "No baseline exists for this app type; declared capabilities are not " +
                    "currently granted and no behavior has been observed."
            },
        )
    }
}
