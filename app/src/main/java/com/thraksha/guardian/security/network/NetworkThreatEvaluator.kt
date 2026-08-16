package com.thraksha.guardian.security.network

import com.thraksha.guardian.security.engine.Finding
import com.thraksha.guardian.security.engine.FindingSource
import com.thraksha.guardian.security.events.Severity
import com.thraksha.guardian.security.inventory.AppType
import com.thraksha.guardian.security.inventory.DemoAppRegistry
import com.thraksha.guardian.security.threatintel.IndicatorTypes
import com.thraksha.guardian.security.threatintel.ThreatPack

/**
 * Pure matcher: (live [NetworkObservation], verified [ThreatPack]) → optional [Finding].
 *
 * Evaluates only [IndicatorTypes.NETWORK_SUPPORTED] indicators (DESTINATION_IP), by
 * literal destination-address equality. The packet payload is **never** an input —
 * detection is destination metadata plus signed intelligence, nothing else (guide §3).
 * Like the other pure evaluators it holds no Android types and renders findings, not
 * responses.
 */
class NetworkThreatEvaluator(
    private val pack: ThreatPack,
    private val nowMillis: Long = System.currentTimeMillis(),
) {

    fun evaluate(observation: NetworkObservation): Finding? {
        // Phase 8: expired network indicators are disabled for active matching (§16) —
        // network destinations decay fast, and a stale IP must not keep firing.
        val indicator = pack.indicators.firstOrNull {
            it.isActiveAt(nowMillis) &&
                it.type == IndicatorTypes.DESTINATION_IP &&
                it.value == observation.destinationIp
        } ?: return null

        return Finding(
            ruleId = "threat-intel:${indicator.id}",
            ruleName = "Network threat intelligence: ${indicator.id}",
            packageName = observation.packageName,
            // The live path has no PackageManager; a display name derived from the
            // package id is sufficient for events/audit (the package name is canonical).
            appName = observation.packageName.substringAfterLast('.')
                .replaceFirstChar { it.uppercase() },
            appType = DemoAppRegistry.typeOf(observation.packageName),
            severity = parseSeverity(indicator.severity),
            confidence = indicator.weight.coerceIn(0, 100),
            reason = indicator.description,
            evidence = listOf(
                "outbound ${observation.protocolName} attempt to ${observation.endpoint()} " +
                    "matched signed network indicator (${observation.packetLength} B packet)",
                "indicator ${indicator.id} (${indicator.type}) from signed threat pack " +
                    "v${pack.packVersion}",
                "classification: ${indicator.classification}",
                "detection is destination metadata only — payload contents were not " +
                    "inspected or persisted",
            ),
            source = FindingSource.NETWORK_THREAT_INTELLIGENCE,
        )
    }

    private fun parseSeverity(raw: String): Severity =
        runCatching { Severity.valueOf(raw.uppercase()) }.getOrDefault(Severity.MEDIUM)
}
