package com.thraksha.guardian.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Gates 2 and 3 of the Phase 10 pipeline (guide §17): strict parse, then contract check.
 *
 * Pure Kotlin — no Android, no LiteRT, no automation types — so every rule is unit-tested
 * off-device and the class reads as a contract.
 *
 * Deliberate strictness (PREP prompt contract §2):
 *  * prose mixed with JSON is a **rejection**, not a parsing challenge;
 *  * unknown keys are a rejection — no silent ignoring of fields we did not design;
 *  * enums are closed sets; anything outside them is a rejection;
 *  * numbers must be JSON integers — `"45"`, `45.0` and `"forty-five"` all fail;
 *  * `targetApp` must belong to the enum injected for THIS request, so a package the
 *    device does not have cannot survive.
 *
 * There is no repair pass, no "find the first brace", and no regex extraction anywhere.
 */
object IntentJsonValidator {

    private val json = Json { ignoreUnknownKeys = false; isLenient = false }

    private val ALLOWED_KEYS = setOf(
        "result", "routine", "durationMinutes", "targetApp", "reasonCode",
    )

    /**
     * The outcome vocabulary (guide §16). [Rejected] is **app-side only** — the model
     * cannot emit it; it is what Thraksha concludes when model output fails a gate.
     */
    sealed interface Outcome {
        data class Intent(
            val routine: String,
            val durationMinutes: Int?,
            val targetApp: String?,
        ) : Outcome

        data class Clarify(val reasonCode: String) : Outcome
        data class Unsupported(val reasonCode: String) : Outcome
        data class Rejected(val stage: String, val reason: String) : Outcome
    }

    /**
     * @param raw untrusted model output.
     * @param allowedApps the exact enum injected into the schema for this request.
     * @param requestedAnApp true when the user's text asked for an app to be opened. Used
     *   only to turn an unresolved app request into a **question**; it can never create or
     *   modify an intent.
     */
    fun validate(
        raw: String?,
        allowedApps: List<String>,
        requestedAnApp: Boolean = false,
    ): Outcome {
        if (raw.isNullOrBlank()) {
            return Outcome.Rejected("PARSE", "empty model output")
        }

        val root = runCatching { json.parseToJsonElement(raw.trim()) }.getOrNull()
            ?: return Outcome.Rejected("PARSE", "output is not valid JSON")
        if (root !is JsonObject) {
            return Outcome.Rejected("PARSE", "output is not a JSON object")
        }

        val unknown = root.keys - ALLOWED_KEYS
        if (unknown.isNotEmpty()) {
            return Outcome.Rejected("SCHEMA", "unknown field(s): ${unknown.joinToString()}")
        }

        val result = root.stringOrNull("result")
            ?: return Outcome.Rejected("SCHEMA", "missing or non-string 'result'")

        val reasonCode = root.stringOrNull("reasonCode")

        return when (result) {
            IntentSchema.RESULT_INTENT -> validateIntent(root, allowedApps, requestedAnApp)

            // A decline or a question is the SAFE direction. If the model gets the
            // decision right but omits or mislabels the reasonCode, the decision is
            // honoured and a generic reason substituted — the reasonCode only selects
            // which fixed sentence Thraksha shows, and discarding a correct refusal
            // because its label was wrong would be worse for the user, not safer.
            // (Measured: eval iteration 1 threw away 12 correct declines this way.)
            IntentSchema.RESULT_CLARIFY ->
                Outcome.Clarify(
                    reasonCode.takeIf { it in IntentSchema.CLARIFY_REASONS } ?: "AMBIGUOUS_ROUTINE",
                )

            IntentSchema.RESULT_UNSUPPORTED ->
                Outcome.Unsupported(
                    reasonCode.takeIf { it in IntentSchema.UNSUPPORTED_REASONS } ?: "OUT_OF_SCOPE",
                )

            else -> Outcome.Rejected("SCHEMA", "unknown result '$result'")
        }
    }

    private fun validateIntent(
        root: JsonObject,
        allowedApps: List<String>,
        requestedAnApp: Boolean,
    ): Outcome {
        val routine = root.stringOrNull("routine")
            ?: return Outcome.Rejected("SCHEMA", "INTENT without a routine")
        if (routine !in IntentSchema.ALLOWED_ROUTINES) {
            // CUSTOM lands here: it is not an allowed routine for model output (contract
            // §3), so a model that emits it is rejected rather than accommodated.
            return Outcome.Rejected("CONTRACT", "routine '$routine' is not model-selectable")
        }

        val durationElement = root["durationMinutes"]
        val duration: Int? = when {
            durationElement == null -> null
            durationElement is JsonPrimitive && durationElement.contentOrNull == null -> null
            durationElement is JsonPrimitive && durationElement.isString ->
                return Outcome.Rejected("SCHEMA", "durationMinutes must be a number, not a string")
            durationElement is JsonPrimitive -> durationElement.intOrNull
                ?: return Outcome.Rejected("SCHEMA", "durationMinutes is not an integer")
            else -> return Outcome.Rejected("SCHEMA", "durationMinutes has the wrong type")
        }
        if (duration != null && duration !in IntentSchema.DURATION_MIN..IntentSchema.DURATION_MAX) {
            // The schema deliberately leaves this field unbounded so the model states the
            // real number instead of having it coerced into range by the sampler. THIS is
            // where the bound is enforced — and the honest answer to "meeting mode for
            // 9 hours" is "that is outside the safe range", not "could not interpret".
            // AutomationSafetyPolicy enforces the same bound again downstream.
            return Outcome.Unsupported("UNSAFE_VALUE")
        }

        val targetApp = root.stringOrNull("targetApp")
        if (targetApp != null && targetApp !in allowedApps) {
            return Outcome.Rejected(
                "CONTRACT", "targetApp '$targetApp' was not offered for this request",
            )
        }

        // Resolution rule from the frozen contract §6: "target app named but ambiguous /
        // not installed → CLARIFY / MISSING_TARGET_APP — the app asks; it does not pick
        // one." The user asked for an app and none was resolved, so Thraksha asks rather
        // than opening whatever the planner's default candidate happens to be.
        if (requestedAnApp && targetApp == null) {
            return Outcome.Clarify("MISSING_TARGET_APP")
        }

        // NOTE on `reasonCode` for INTENT: the contract says it must be null, and the
        // model frequently emits a junk value anyway. It is deliberately NOT read here.
        // reasonCode carries zero authority — it only selects which fixed UI string is
        // shown for CLARIFY/UNSUPPORTED — so ignoring it on the INTENT branch discards a
        // meaningless field rather than relaxing a safety rule. Every field that CAN
        // affect the device (routine, duration, targetApp) is validated above and again
        // by AutomationSafetyPolicy.
        return Outcome.Intent(routine, duration, targetApp)
    }

    private fun JsonObject.stringOrNull(key: String): String? {
        val element = this[key] ?: return null
        val primitive = element as? JsonPrimitive ?: return null
        if (primitive.contentOrNull == null) return null // JSON null
        if (!primitive.isString) return null
        return primitive.jsonPrimitive.content.takeIf { it.isNotBlank() }
    }
}
