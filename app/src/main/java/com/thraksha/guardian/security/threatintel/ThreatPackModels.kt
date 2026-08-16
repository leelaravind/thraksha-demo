package com.thraksha.guardian.security.threatintel

import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * The OFFLINE signed threat-intelligence artefact — detection pillar B, conceptually
 * separate from the behavioural rulepack (pillar A). Same cryptographic trust model
 * (RSA-2048 / SHA-256 over the exact asset bytes), different key pair and different
 * content: threat *indicators*, not behaviour rules.
 *
 * Phase 8 extends the schema with provenance and freshness fields. All additions are
 * optional with safe defaults, so schemaVersion stays 1 and every pre-Phase-8 pack
 * still parses; the *semantics* added are:
 *
 *  * an indicator past [ThreatIndicator.expiresAt] is disabled for active matching but
 *    retained (visibly) for audit/history — expiry never silently deletes provenance;
 *  * a pack past [expiresAt] is STALE: the UI must say "THREAT INTELLIGENCE STALE",
 *    never silently treat the device as clean;
 *  * an indicator classified `REAL_*` (real-world intelligence) MUST carry a
 *    [ThreatIndicator.sourceReference] — enforced by the parser, so an untraceable
 *    real-world indicator cannot exist in a valid pack.
 */
@Serializable
data class ThreatPack(
    val schemaVersion: Int,
    val packVersion: Int,
    /** ISO-8601 instant the pack was generated. Informational. */
    val generatedAt: String,
    /** Optional ISO-8601 expiry for the whole pack. Past it, the pack is STALE. */
    val expiresAt: String? = null,
    val indicators: List<ThreatIndicator>,
) {
    /** True when the pack itself has expired. A stale pack must be surfaced, not trusted. */
    fun isStaleAt(nowMillis: Long): Boolean =
        expiresAt?.let { parseInstantMillis(it)?.let { t -> nowMillis > t } } == true
}

/**
 * One threat-intelligence record.
 *
 * [type] is one of [IndicatorTypes.SUPPORTED] (static scanner), [IndicatorTypes.NETWORK_SUPPORTED]
 * (live Network Guard) or [IndicatorTypes.FUTURE] (representable, never evaluated — see the
 * fail-closed policy in [ThreatPackParser]). [weight] is an authored rule-weight (0..100) in
 * the same sense as rulepack confidence values — a deterministic signed number, not a
 * probability.
 */
@Serializable
data class ThreatIndicator(
    val id: String,
    val type: String,
    val value: String,
    /**
     * e.g. DEMO_TEST_THREAT for the controlled benign samples; REAL_WORLD_THREAT for
     * genuinely sourced intelligence (which then must carry [sourceReference]).
     * Never a fabricated family name.
     */
    val classification: String,
    val severity: String,
    val description: String,
    val source: String,
    val enabled: Boolean,
    val weight: Int = 80,

    // --- Phase 8 provenance & freshness (optional; absent = pre-Phase-8 semantics) ---

    /** URL/identifier of the origin record at the source. MANDATORY for `REAL_*` records. */
    val sourceReference: String? = null,

    /** Malware family label exactly as supplied by the source. Never invented. */
    val family: String? = null,

    /** ISO-8601 first-seen at the source, verbatim provenance. */
    val firstSeen: String? = null,

    /** ISO-8601 last-seen at the source, verbatim provenance. */
    val lastSeen: String? = null,

    /**
     * ISO-8601 expiry for this indicator. Past it the indicator is excluded from active
     * matching (deterministically, based on the scan-time clock the engine passes in)
     * but stays in the pack for audit/history.
     */
    val expiresAt: String? = null,

    /**
     * Evidentiary strength: [IndicatorTier.STRONG] or [IndicatorTier.CORROBORATING].
     * Absent → the default tier for the indicator [type] applies
     * ([IndicatorTier.defaultForType]): APK/cert digests are strong, package names are
     * only ever corroborating.
     */
    val tier: String? = null,
) {
    /** True when this indicator is past its own expiry at [nowMillis]. */
    fun isExpiredAt(nowMillis: Long): Boolean =
        expiresAt?.let { parseInstantMillis(it)?.let { t -> nowMillis > t } } == true

    /** True when this indicator may actively match at [nowMillis]. */
    fun isActiveAt(nowMillis: Long): Boolean = enabled && !isExpiredAt(nowMillis)

    /** The effective evidentiary tier, defaulting by type. */
    fun effectiveTier(): IndicatorTier =
        tier?.let { runCatching { IndicatorTier.valueOf(it) }.getOrNull() }
            ?: IndicatorTier.defaultForType(type)
}

/**
 * Deterministic evidentiary strength of an indicator — the Phase 8 trust model. Not a
 * probability: a tier decides what a match may *claim*, per the threat-intel research:
 * an exact APK digest or vetted signer digest is strong known-threat evidence; a package
 * name is mutable/spoofable and can only ever corroborate.
 */
enum class IndicatorTier {
    /** An exact match is authoritative known-threat evidence (eligible for KNOWN THREAT MATCH). */
    STRONG,

    /** An exact match is supporting evidence only (REVIEW at most, never a threat verdict alone). */
    CORROBORATING,
    ;

    companion object {
        fun defaultForType(type: String): IndicatorTier = when (type) {
            IndicatorTypes.BASE_APK_SHA256, IndicatorTypes.SIGNING_CERT_SHA256 -> STRONG
            // Package names and network destinations are mutable/reassignable.
            else -> CORROBORATING
        }
    }
}

/** Lenient ISO-8601 instant parse; null (never a throw) on malformed input at read time. */
internal fun parseInstantMillis(iso: String): Long? =
    runCatching { Instant.parse(iso).toEpochMilli() }.getOrNull()

object IndicatorTypes {
    const val PACKAGE_NAME = "PACKAGE_NAME"
    const val SIGNING_CERT_SHA256 = "SIGNING_CERT_SHA256"

    /**
     * SHA-256 of the installed base APK (`ApplicationInfo.sourceDir`) only. Split APKs
     * are NOT hashed — the name says exactly what is fingerprinted (guide §6.9).
     */
    const val BASE_APK_SHA256 = "BASE_APK_SHA256"

    /**
     * IPv4 destination address of a live outbound attempt, matched by the Network Guard
     * (Phase 7). The canonical representation is a dotted-quad IPv4 literal. Evaluated
     * ONLY against packets read from the per-app tunnel — never by the static
     * [installed-app] ThreatIntelligenceScanner.
     */
    const val DESTINATION_IP = "DESTINATION_IP"

    /** Types the static installed-app scanner evaluates. */
    val SUPPORTED: Set<String> = setOf(PACKAGE_NAME, SIGNING_CERT_SHA256, BASE_APK_SHA256)

    /** Types the live network evaluator evaluates. */
    val NETWORK_SUPPORTED: Set<String> = setOf(DESTINATION_IP)

    /** Types the schema may carry but which are never evaluated in this build. */
    val FUTURE: Set<String> = setOf("DOMAIN", "IP", "URL", "FILE_HASH", "MALWARE_FAMILY")
}
