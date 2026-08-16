package com.thraksha.guardian.data.config

import android.content.Context
import com.thraksha.guardian.data.db.ConfigEntity
import com.thraksha.guardian.data.db.DatabaseProvider

/**
 * Typed accessors over the encrypted `config` table: the execution mode, the confidence
 * tier thresholds (60/80/95/99), and feature flags. First-run defaults are seeded by
 * [ensureDefaults]. Values + accessors only — the gating logic that uses them is Phase 4.
 */
class ConfigStore(context: Context) {

    private val dao = DatabaseProvider.get(context.applicationContext).configDao()

    /** Seeds defaults on first run (no-op once the table is populated). */
    suspend fun ensureDefaults() {
        if (dao.all().isNotEmpty()) return
        setExecutionMode(ExecutionMode.OBSERVE)
        putInt(KEY_TIER_LOW, DEFAULT_TIER_LOW)
        putInt(KEY_TIER_MEDIUM, DEFAULT_TIER_MEDIUM)
        putInt(KEY_TIER_HIGH, DEFAULT_TIER_HIGH)
        putInt(KEY_TIER_CRITICAL, DEFAULT_TIER_CRITICAL)
    }

    suspend fun getExecutionMode(): ExecutionMode =
        dao.get(KEY_MODE)?.value
            ?.let { runCatching { ExecutionMode.valueOf(it) }.getOrNull() }
            ?: ExecutionMode.OBSERVE

    suspend fun setExecutionMode(mode: ExecutionMode) = putString(KEY_MODE, mode.name)

    suspend fun getTierThresholds(): TierThresholds = TierThresholds(
        low = getInt(KEY_TIER_LOW, DEFAULT_TIER_LOW),
        medium = getInt(KEY_TIER_MEDIUM, DEFAULT_TIER_MEDIUM),
        high = getInt(KEY_TIER_HIGH, DEFAULT_TIER_HIGH),
        critical = getInt(KEY_TIER_CRITICAL, DEFAULT_TIER_CRITICAL),
    )

    /**
     * Raw string storage in the encrypted config table (Phase 9: automation run/snapshot
     * persistence, guide §10/§22 — survives process death without a schema migration).
     * A blank value means "cleared": the config DAO is deliberately append/replace-only.
     */
    suspend fun putRawValue(key: String, value: String) = putString(key, value)

    suspend fun getRawValue(key: String): String? =
        dao.get(key)?.value?.takeIf { it.isNotBlank() }

    suspend fun isFeatureEnabled(flag: String, default: Boolean = false): Boolean =
        dao.get(featureKey(flag))?.value?.toBooleanStrictOrNull() ?: default

    suspend fun setFeatureEnabled(flag: String, enabled: Boolean) =
        putString(featureKey(flag), enabled.toString())

    private suspend fun putString(key: String, value: String) = dao.put(ConfigEntity(key, value))
    private suspend fun putInt(key: String, value: Int) = putString(key, value.toString())
    private suspend fun getInt(key: String, default: Int): Int =
        dao.get(key)?.value?.toIntOrNull() ?: default

    private fun featureKey(flag: String) = "$FEATURE_PREFIX$flag"

    /** The four confidence-tier thresholds for low → critical risk actions. */
    data class TierThresholds(
        val low: Int,
        val medium: Int,
        val high: Int,
        val critical: Int,
    )

    companion object {
        const val DEFAULT_TIER_LOW = 60
        const val DEFAULT_TIER_MEDIUM = 80
        const val DEFAULT_TIER_HIGH = 95
        const val DEFAULT_TIER_CRITICAL = 99

        private const val KEY_MODE = "execution_mode"
        private const val KEY_TIER_LOW = "tier_low"
        private const val KEY_TIER_MEDIUM = "tier_medium"
        private const val KEY_TIER_HIGH = "tier_high"
        private const val KEY_TIER_CRITICAL = "tier_critical"
        private const val FEATURE_PREFIX = "feature."
    }
}
