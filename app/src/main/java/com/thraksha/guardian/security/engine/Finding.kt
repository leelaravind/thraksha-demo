package com.thraksha.guardian.security.engine

import com.thraksha.guardian.security.events.Severity
import com.thraksha.guardian.security.inventory.AppType

/** Which detection pillar produced a [Finding]. */
enum class FindingSource {
    /** Contextual profile detection: signed rulepack + role baseline (pillar A). */
    PROFILE_RULE,

    /** Signed threat-intelligence indicator match against installed-app identity (pillar B). */
    THREAT_INTELLIGENCE,

    /**
     * Signed threat-intelligence indicator matched against a **live intercepted packet's
     * destination metadata** (pillar C, Phase 7) — evidence of what the app actually
     * attempted on the network, never of payload semantics.
     */
    NETWORK_THREAT_INTELLIGENCE,

    /**
     * Generic static risk rule (pillar D, Phase 8): deterministic, evidence-based
     * observations about an arbitrary app's declared capabilities that warrant REVIEW.
     * A generic finding is a risk indicator, never a malware verdict.
     */
    GENERIC_RULE,
}

/**
 * One rule firing against one app.
 *
 * Carries enough context for the UI to explain itself and for a later PolicyEngine to
 * decide what, if anything, may be done — without either of them re-deriving anything.
 *
 * [evidence] is the raw observation the verdict rests on (the actual permission strings
 * read from the target's manifest). It exists so a finding can always be traced back to
 * something concrete rather than asserted.
 *
 * A `Finding` describes a **declared capability that is inappropriate for the app's
 * role**. It never asserts that the app *did* anything.
 */
data class Finding(
    val ruleId: String,
    val ruleName: String,
    val packageName: String,
    val appName: String,
    val appType: AppType,
    val severity: Severity,
    val confidence: Int,
    val reason: String,
    val evidence: List<String>,

    /**
     * The specific permissions this finding is about, when the rule is permission-based
     * (empty for aggregate rules). Structured so the policy layer can map a finding to a
     * corresponding response (e.g. deny exactly this permission) without parsing the
     * human-readable [evidence] strings.
     */
    val offendingPermissions: List<String> = emptyList(),

    /** Which detection pillar this finding came from. */
    val source: FindingSource = FindingSource.PROFILE_RULE,

    /**
     * For [FindingSource.THREAT_INTELLIGENCE] findings: the evidentiary tier of the
     * matched indicator (Phase 8 trust model). A STRONG match (exact APK digest, vetted
     * signer digest) is authoritative known-threat evidence; a CORROBORATING match
     * (package name) may only ever support review, never carry a threat verdict alone.
     * Null for non-threat-intelligence findings.
     */
    val intelligenceTier: com.thraksha.guardian.security.threatintel.IndicatorTier? = null,
) {
    /** Compact one-line form used for the audit `details` column and the dashboard feed. */
    fun summary(): String = "$appName ($packageName): $reason"
}
