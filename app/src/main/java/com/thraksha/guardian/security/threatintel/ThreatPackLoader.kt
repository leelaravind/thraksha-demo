package com.thraksha.guardian.security.threatintel

import android.content.Context
import android.util.Log
import com.thraksha.guardian.security.rulepack.RulepackVerifier

/**
 * The state of the threat-intelligence pillar after a load attempt. Deliberately a
 * sealed result rather than an exception: an invalid pack must not abort the profile
 * pillar, but it must surface as a **visible unavailable state** — the app never
 * silently treats a missing/corrupt threat database as "everything is clean".
 */
sealed interface ThreatPackState {
    data class Verified(val pack: ThreatPack) : ThreatPackState
    data class Unavailable(val reason: String) : ThreatPackState
}

/**
 * Loads and integrity-checks the bundled threat pack from assets, mirroring the working
 * `RulepackLoader`: exact bytes → RSA-2048/SHA-256 signature verification → schema
 * validation → immutable data. Verification reuses the same [RulepackVerifier]
 * primitive (identical signature scheme) but against the threat pack's **own key pair**
 * (`threatpack_public.key`) — the two artefacts share a framework, not a trust root.
 *
 * Fail-closed at every step: unreadable assets, a bad signature, or invalid schema all
 * produce [ThreatPackState.Unavailable] with the reason. The loader is also shaped for
 * a future update flow (verify-then-activate over candidate bytes via [verifyAndParse])
 * — but this build is strictly OFFLINE: assets only, no networking.
 */
class ThreatPackLoader(private val context: Context) {

    fun load(): ThreatPackState {
        val (jsonBytes, signature, publicKey) = try {
            val assets = context.assets
            Triple(
                assets.open(ASSET_JSON).use { it.readBytes() },
                assets.open(ASSET_SIG).use { it.readBytes() }.decodeToString(),
                assets.open(ASSET_PUB).use { it.readBytes() }.decodeToString(),
            )
        } catch (error: Exception) {
            Log.e(TAG, "Threat pack assets unreadable", error)
            return ThreatPackState.Unavailable(
                "Threat pack assets could not be read: ${error.message}",
            )
        }
        return verifyAndParse(jsonBytes, signature, publicKey)
    }

    /** Verification + validation over candidate bytes; shared by [load] and tests. */
    fun verifyAndParse(
        jsonBytes: ByteArray,
        signatureBase64: String,
        publicKeyBase64: String,
    ): ThreatPackState {
        if (!RulepackVerifier.verify(jsonBytes, signatureBase64, publicKeyBase64)) {
            Log.e(TAG, "Threat pack signature verification FAILED — pillar disabled")
            return ThreatPackState.Unavailable(
                "Threat pack signature verification failed. Threat-intelligence " +
                    "scanning is disabled rather than run on unverified data.",
            )
        }
        return try {
            ThreatPackState.Verified(ThreatPackParser.parse(jsonBytes))
        } catch (error: Exception) {
            Log.e(TAG, "Threat pack schema validation failed", error)
            ThreatPackState.Unavailable("Threat pack schema invalid: ${error.message}")
        }
    }

    companion object {
        private const val TAG = "ThreatPackLoader"
        private const val ASSET_JSON = "threatpack.json"
        private const val ASSET_SIG = "threatpack.sig"
        private const val ASSET_PUB = "threatpack_public.key"
    }
}
