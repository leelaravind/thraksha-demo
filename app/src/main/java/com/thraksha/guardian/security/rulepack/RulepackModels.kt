package com.thraksha.guardian.security.rulepack

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** The bundled "knowledge": a versioned set of detection rules. */
@Serializable
data class Rulepack(
    val version: Int,
    val rules: List<Rule>,
)

/**
 * One detection signal's declaration. [params] is a free-form placeholder that Phase 3
 * detectors will read (thresholds, allow-lists, etc.).
 */
@Serializable
data class Rule(
    val id: String,
    val name: String,
    val category: String,
    val severity: String,
    val enabled: Boolean = true,
    val params: JsonObject = JsonObject(emptyMap()),
)
