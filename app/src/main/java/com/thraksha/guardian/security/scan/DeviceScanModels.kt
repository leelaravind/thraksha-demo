package com.thraksha.guardian.security.scan

import com.thraksha.guardian.security.engine.Finding
import com.thraksha.guardian.security.evidence.AppContextAssessment
import com.thraksha.guardian.security.evidence.AppDisplayStatus
import com.thraksha.guardian.security.evidence.CapabilityEvidence
import com.thraksha.guardian.security.inventory.AppType
import com.thraksha.guardian.security.inventory.VisibilityScope
import com.thraksha.guardian.security.policy.EnforcementResult
import com.thraksha.guardian.security.policy.SecurityDecision

/**
 * The honest per-app result vocabulary for the generalized device scan (guide §10).
 *
 * Deliberately NOT a binary clean/threat: an arbitrary real application is never
 * pronounced "clean" (unprovable) and never called malware merely for requesting
 * sensitive permissions.
 */
enum class ScanClassification {
    /**
     * No current rule or threat-intelligence match. NOT a proof of safety — the label
     * says exactly what was checked and found.
     */
    NO_KNOWN_FINDINGS,

    /** One or more generic/contextual risk indicators warrant human review. */
    REVIEW,

    /**
     * Strong static/contextual findings (HIGH+ severity from profile or generic rules)
     * exist, but no authoritative known-threat intelligence match.
     */
    HIGH_RISK_PROFILE,

    /**
     * A STRONG-tier indicator from the verified threat pack matched (exact APK digest or
     * vetted signer digest). The only classification that may claim a known threat.
     */
    KNOWN_THREAT_MATCH,

    /**
     * Required evidence could not be obtained for this app (fingerprint failure, missing
     * or stale intelligence). Missing evidence is never presented as clean.
     */
    PARTIAL,
}

/** The threat-intelligence pillar's state for one scan. */
data class IntelligenceState(
    val status: Status,
    val packVersion: Int?,
    val activeIndicators: Int,
    val expiredIndicators: Int,
    val detail: String,
) {
    enum class Status {
        /** Verified, in-date pack: matching ran. */
        ACTIVE,

        /** Verified pack past its own expiry: matching DISABLED, surfaced as stale. */
        STALE,

        /** Missing/corrupt/bad-signature pack: matching disabled, surfaced. */
        UNAVAILABLE,
    }
}

/** Observable scan lifecycle for the UI (guide §20). */
sealed interface ScanProgress {
    data object Idle : ScanProgress
    data object Discovering : ScanProgress
    data class Analyzing(
        val analyzed: Int,
        val total: Int,
        val currentPackage: String,
    ) : ScanProgress

    data class Finished(val outcome: ScanOutcome) : ScanProgress
}

/** How the scan run itself ended (distinct from any per-app classification). */
enum class ScanOutcome { COMPLETE, PARTIAL, ERROR }

/** Per-stage durations in milliseconds (guide §21). */
data class DeviceScanTimings(
    val inventoryMs: Long,
    val certFingerprintMs: Long,
    val apkHashMs: Long,
    val genericRulesMs: Long,
    val contextualRulesMs: Long,
    val threatIntelMs: Long,
    val totalMs: Long,

    /**
     * Phase 8.1: grant-state queries + evidence assembly + context evaluation, summed
     * across apps (guide §36 overhead measurement). Defaulted so Phase 8 call sites and
     * tests are unchanged.
     */
    val evidenceMs: Long = 0,
)

/** Everything the scan observed and concluded about one installed app. */
data class AppScanRecord(
    val packageName: String,
    val displayName: String,
    val appType: AppType,
    val isSystemApp: Boolean,
    val isEnabled: Boolean,
    val versionName: String?,
    val installerPackage: String?,

    /** SHA-256 digests of the current signing certificates (may be empty on failure). */
    val certSha256: Set<String>,

    /** SHA-256 of the installed base APK, or null when it could not be hashed. */
    val baseApkSha256: String?,

    val findings: List<Finding>,
    val classification: ScanClassification,

    /** False when fingerprinting was incomplete for this app. */
    val evidenceComplete: Boolean,

    /** Set only for KNOWN_THREAT_MATCH / HIGH_RISK_PROFILE apps routed through policy. */
    val decision: SecurityDecision? = null,
    val enforcement: EnforcementResult? = null,

    // --- Phase 8.1 evidence progression (defaults keep Phase 8 call sites unchanged) ---

    /** Per-capability DECLARED / GRANTED / OBSERVED progression (guide §3, §12). */
    val evidence: List<CapabilityEvidence> = emptyList(),

    /** Deterministic context assessment over the assembled evidence (guide §9). */
    val contextAssessment: AppContextAssessment? = null,

    /**
     * The honest user-facing status (guide §13/§22/§23). Null only on records produced
     * before the evidence stage could run (per-app analysis failure → PARTIAL).
     */
    val displayStatus: AppDisplayStatus? = null,
)

/** The outcome of one complete SCAN THIS DEVICE run. */
data class DeviceScanResult(
    val startedAt: Long,
    val completedAt: Long,
    val visibilityScope: VisibilityScope,
    val outcome: ScanOutcome,
    /** Present when [outcome] != COMPLETE: why the scan is partial or failed. */
    val outcomeDetail: String?,

    val totalDiscovered: Int,
    val userAppCount: Int,
    val systemAppCount: Int,
    val analyzedCount: Int,
    val failedPackages: List<String>,

    val certificatesChecked: Int,
    val apkHashesComputed: Int,

    val intelligence: IntelligenceState,
    val apps: List<AppScanRecord>,
    val timings: DeviceScanTimings,
) {
    val knownThreatApps: List<AppScanRecord>
        get() = apps.filter { it.classification == ScanClassification.KNOWN_THREAT_MATCH }
    val highRiskApps: List<AppScanRecord>
        get() = apps.filter { it.classification == ScanClassification.HIGH_RISK_PROFILE }
    val reviewApps: List<AppScanRecord>
        get() = apps.filter { it.classification == ScanClassification.REVIEW }
    val partialApps: List<AppScanRecord>
        get() = apps.filter { it.classification == ScanClassification.PARTIAL }
    val totalFindings: Int get() = apps.sumOf { it.findings.size }
}
