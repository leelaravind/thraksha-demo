package com.thraksha.guardian.security.rulepack

import android.content.Context
import kotlinx.serialization.json.Json

/**
 * Loads and integrity-checks the bundled rulepack from assets. Rejects a tampered or
 * unsigned rulepack by throwing [IllegalArgumentException] — callers get either a
 * verified [Rulepack] or an exception, never unverified rules.
 */
class RulepackLoader(private val context: Context) {

    fun load(): Rulepack {
        val assets = context.assets
        val jsonBytes = assets.open(ASSET_JSON).use { it.readBytes() }
        val signature = assets.open(ASSET_SIG).use { it.readBytes() }.decodeToString()
        val publicKey = assets.open(ASSET_PUB).use { it.readBytes() }.decodeToString()

        require(RulepackVerifier.verify(jsonBytes, signature, publicKey)) {
            "Rulepack signature verification failed — refusing to load."
        }
        return JSON.decodeFromString(Rulepack.serializer(), jsonBytes.decodeToString())
    }

    companion object {
        private const val ASSET_JSON = "rulepack.json"
        private const val ASSET_SIG = "rulepack.sig"
        private const val ASSET_PUB = "rulepack_public.key"
        private val JSON = Json { ignoreUnknownKeys = true }
    }
}
