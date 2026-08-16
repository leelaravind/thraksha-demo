package com.thraksha.guardian.security.threatintel

import com.thraksha.guardian.security.engine.Finding
import com.thraksha.guardian.security.engine.FindingSource
import com.thraksha.guardian.security.events.Severity
import com.thraksha.guardian.security.inventory.ObservedApp

/**
 * Pure matcher: (verified threat pack, app fingerprint) → threat-intelligence findings.
 *
 * Like `RuleEngine`, this holds no Android types — the Android side is confined to
 * [AppFingerprinter]. Matching is deterministic and literal:
 *
 *  * `PACKAGE_NAME`         — exact package-name equality;
 *  * `SIGNING_CERT_SHA256`  — indicator value ∈ the app's signing-cert digests;
 *  * `BASE_APK_SHA256`      — exact digest equality against the hashed base APK.
 *
 * Phase 8 semantics:
 *
 *  * **Expiry** — an indicator past its `expiresAt` (relative to [nowMillis], fixed for
 *    the scanner's lifetime so one scan is internally consistent) never matches; it is
 *    counted in [expiredIndicators] so the result can report it rather than hide it.
 *  * **Indexing** — active indicators are indexed by (type, value) once at construction,
 *    so per-app matching cost is O(app identifiers), not O(pack size). Required for the
 *    few-thousand-indicator real corpus against hundreds of installed apps.
 *  * **Tier** — every finding carries the matched indicator's evidentiary tier
 *    ([IndicatorTier]); the classifier upstream is what turns STRONG into
 *    KNOWN THREAT MATCH and CORROBORATING into (at most) REVIEW.
 *
 * Disabled indicators never match. FUTURE-typed indicators are never evaluated. No
 * probabilistic scoring — [ThreatIndicator.weight] is an authored signed value carried
 * through as the finding's rule-weight.
 */
class ThreatIntelligenceScanner(
    private val pack: ThreatPack,
    private val nowMillis: Long = System.currentTimeMillis(),
) {

    private val active: List<ThreatIndicator> = pack.indicators
        .filter { it.type in IndicatorTypes.SUPPORTED && it.isActiveAt(nowMillis) }

    /** Enabled, supported-type indicators that were excluded because they have expired. */
    val expiredIndicators: Int = pack.indicators
        .count { it.type in IndicatorTypes.SUPPORTED && it.enabled && it.isExpiredAt(nowMillis) }

    /** Indicators this scanner will actively evaluate. */
    val activeIndicators: Int = active.size

    private val byPackageName: Map<String, List<ThreatIndicator>> =
        active.filter { it.type == IndicatorTypes.PACKAGE_NAME }.groupBy { it.value }

    private val byCertSha256: Map<String, List<ThreatIndicator>> =
        active.filter { it.type == IndicatorTypes.SIGNING_CERT_SHA256 }.groupBy { it.value }

    private val byApkSha256: Map<String, List<ThreatIndicator>> =
        active.filter { it.type == IndicatorTypes.BASE_APK_SHA256 }.groupBy { it.value }

    fun scan(app: ObservedApp, fingerprint: AppFingerprint): List<Finding> {
        val findings = mutableListOf<Finding>()

        byPackageName[app.packageName]?.forEach { indicator ->
            findings += finding(
                indicator, app,
                "package name ${app.packageName} matches indicator value",
            )
        }
        fingerprint.certSha256.sorted().forEach { cert ->
            byCertSha256[cert]?.forEach { indicator ->
                findings += finding(
                    indicator, app,
                    "signing certificate SHA-256 $cert matches a current signer of the " +
                        "installed package",
                )
            }
        }
        fingerprint.baseApkSha256?.let { apkSha ->
            byApkSha256[apkSha]?.forEach { indicator ->
                findings += finding(
                    indicator, app,
                    "installed base APK SHA-256 $apkSha matches " +
                        "(base APK only; splits are not hashed)",
                )
            }
        }

        return findings.sortedBy { it.ruleId }
    }

    private fun finding(
        indicator: ThreatIndicator,
        app: ObservedApp,
        matchedEvidence: String,
    ): Finding = Finding(
        ruleId = "threat-intel:${indicator.id}",
        ruleName = "Threat intelligence: ${indicator.id}",
        packageName = app.packageName,
        appName = app.displayName,
        appType = app.appType,
        severity = parseSeverity(indicator.severity),
        confidence = indicator.weight.coerceIn(0, 100),
        reason = indicator.description,
        evidence = buildList {
            add(matchedEvidence)
            add(
                "indicator ${indicator.id} (${indicator.type}, " +
                    "tier ${indicator.effectiveTier()}) from signed threat pack " +
                    "v${pack.packVersion}",
            )
            add("classification: ${indicator.classification}")
            indicator.family?.let { add("family (as labelled by source): $it") }
            add("source: ${indicator.source}")
            indicator.sourceReference?.let { add("source reference: $it") }
        },
        source = FindingSource.THREAT_INTELLIGENCE,
        intelligenceTier = indicator.effectiveTier(),
    )

    private fun parseSeverity(raw: String): Severity =
        runCatching { Severity.valueOf(raw.uppercase()) }.getOrDefault(Severity.MEDIUM)
}
