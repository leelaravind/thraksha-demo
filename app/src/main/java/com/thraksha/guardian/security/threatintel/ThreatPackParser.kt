package com.thraksha.guardian.security.threatintel

import kotlinx.serialization.json.Json

/**
 * Pure, fail-closed schema validation for an already-signature-verified threat pack.
 *
 * Signature verification proves the bytes are ours; this proves the *content* is sane.
 * Every rule here rejects the whole pack rather than skipping the offending record —
 * a malformed trusted artefact is a build error to surface, not data to guess around.
 *
 * Documented schema policy:
 *  * `schemaVersion` must be exactly [SUPPORTED_SCHEMA_VERSION];
 *  * indicator ids must be unique;
 *  * an indicator whose [ThreatIndicator.type] is not SUPPORTED, NETWORK_SUPPORTED or
 *    FUTURE → reject;
 *  * FUTURE-typed indicators parse but are **never evaluated**, enabled or not;
 *  * hash-typed values must be 64 lowercase hex chars; PACKAGE_NAME values must be
 *    non-blank; DESTINATION_IP values must be dotted-quad IPv4 literals;
 *  * weights must be 0..100.
 */
object ThreatPackParser {

    const val SUPPORTED_SCHEMA_VERSION = 1

    /** Classifications beginning with this prefix denote real-world sourced intelligence. */
    const val REAL_CLASSIFICATION_PREFIX = "REAL_"

    private val JSON = Json { ignoreUnknownKeys = true }
    private val SHA256_HEX = Regex("^[0-9a-f]{64}$")
    private val IPV4 = Regex("^((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)$")

    /** Parses and validates, throwing [IllegalArgumentException] on any violation. */
    fun parse(jsonBytes: ByteArray): ThreatPack {
        val pack = JSON.decodeFromString(ThreatPack.serializer(), jsonBytes.decodeToString())

        require(pack.schemaVersion == SUPPORTED_SCHEMA_VERSION) {
            "Unsupported threat pack schema version ${pack.schemaVersion} " +
                "(this build supports $SUPPORTED_SCHEMA_VERSION)"
        }
        require(pack.packVersion > 0) { "packVersion must be positive" }

        val ids = pack.indicators.map { it.id }
        require(ids.size == ids.toSet().size) {
            "Duplicate indicator ids: ${ids.groupBy { it }.filterValues { it.size > 1 }.keys}"
        }

        pack.expiresAt?.let {
            requireNotNull(parseInstantMillis(it)) {
                "Pack expiresAt '$it' is not a valid ISO-8601 instant"
            }
        }

        pack.indicators.forEach { indicator ->
            require(indicator.id.isNotBlank()) { "Indicator with blank id" }
            require(
                indicator.type in IndicatorTypes.SUPPORTED ||
                    indicator.type in IndicatorTypes.NETWORK_SUPPORTED ||
                    indicator.type in IndicatorTypes.FUTURE,
            ) {
                "Indicator ${indicator.id} has unknown type '${indicator.type}' — " +
                    "refusing the pack (fail closed)"
            }
            require(indicator.weight in 0..100) {
                "Indicator ${indicator.id} weight ${indicator.weight} outside 0..100"
            }

            // Phase 8 provenance & freshness rules (fail closed on violation):
            //  * REAL_* classifications must be traceable to a source record;
            //  * declared expiry/tier values must be well-formed, or the pack is refused
            //    rather than an indicator silently living forever / gaining strength.
            if (indicator.classification.startsWith(REAL_CLASSIFICATION_PREFIX)) {
                require(!indicator.sourceReference.isNullOrBlank()) {
                    "Indicator ${indicator.id} is classified " +
                        "'${indicator.classification}' but has no sourceReference — " +
                        "real-world intelligence without provenance is refused"
                }
            }
            indicator.expiresAt?.let {
                requireNotNull(parseInstantMillis(it)) {
                    "Indicator ${indicator.id} expiresAt '$it' is not a valid ISO-8601 instant"
                }
            }
            indicator.tier?.let { declared ->
                require(runCatching { IndicatorTier.valueOf(declared) }.isSuccess) {
                    "Indicator ${indicator.id} tier '$declared' is not one of " +
                        IndicatorTier.entries.joinToString()
                }
            }
            when (indicator.type) {
                IndicatorTypes.SIGNING_CERT_SHA256, IndicatorTypes.BASE_APK_SHA256 ->
                    require(SHA256_HEX.matches(indicator.value)) {
                        "Indicator ${indicator.id}: value is not 64 lowercase hex chars " +
                            "(a SHA-256 digest)"
                    }

                IndicatorTypes.PACKAGE_NAME ->
                    require(indicator.value.isNotBlank()) {
                        "Indicator ${indicator.id}: blank package name"
                    }

                IndicatorTypes.DESTINATION_IP ->
                    require(IPV4.matches(indicator.value)) {
                        "Indicator ${indicator.id}: value is not a dotted-quad IPv4 literal"
                    }
            }
        }

        return pack
    }
}
