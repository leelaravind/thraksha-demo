package com.thraksha.guardian.automation

/**
 * Deterministic automation safety rules (guide §27) — the gate every
 * [AutomationIntent] passes BEFORE planning, and the boundary Phase 10's model will sit
 * behind (guide §28): a future AI submits intents; if a field is out of policy the
 * intent is rejected here, structurally before any executor exists in the call path.
 *
 * Pure Kotlin: every rule is unit-tested off-device.
 */
object AutomationSafetyPolicy {

    sealed interface Validation {
        data object Valid : Validation
        data class Rejected(val reason: String) : Validation
    }

    /** Capabilities automation may ever touch. Everything else is rejected outright. */
    val ALLOWED_CAPABILITIES: Set<AutomationCapability> = setOf(
        AutomationCapability.DO_NOT_DISTURB,
        AutomationCapability.RINGER_MODE,
        AutomationCapability.SCREEN_BRIGHTNESS,
        AutomationCapability.SCREEN_TIMEOUT,
        AutomationCapability.LAUNCH_APP,
        AutomationCapability.OPEN_SETTINGS,
    )

    /** Valid interruption filters: ALL(1), PRIORITY(2), NONE(3), ALARMS(4). */
    private val VALID_DND_FILTERS = setOf(1, 2, 3, 4)

    /** Valid ringer modes: SILENT(0), VIBRATE(1), NORMAL(2). */
    private val VALID_RINGER_MODES = setOf(0, 1, 2)

    const val BRIGHTNESS_MIN = 1
    const val BRIGHTNESS_MAX = 255
    const val TIMEOUT_MIN_MS = 15_000
    const val TIMEOUT_MAX_MS = 1_800_000
    const val DURATION_MIN_MINUTES = 1
    const val DURATION_MAX_MINUTES = 480

    /** The only Android settings actions a plan may hand the user to. */
    val ALLOWED_SETTINGS_ACTIONS: Set<String> = setOf(
        "android.settings.SETTINGS",
        "android.settings.SOUND_SETTINGS",
        "android.settings.DISPLAY_SETTINGS",
        "android.settings.NOTIFICATION_POLICY_ACCESS_SETTINGS",
        "android.settings.action.MANAGE_WRITE_SETTINGS",
        "android.settings.APPLICATION_DETAILS_SETTINGS",
    )

    /** Packages a routine must never be asked to launch. */
    private val FORBIDDEN_LAUNCH_TARGETS = setOf(
        "com.thraksha.guardian", // launching ourselves is never a routine action
    )

    fun validateIntent(intent: AutomationIntent): Validation {
        intent.durationMinutes?.let { minutes ->
            if (minutes < DURATION_MIN_MINUTES || minutes > DURATION_MAX_MINUTES) {
                return Validation.Rejected(
                    "Duration $minutes min is outside the allowed " +
                        "$DURATION_MIN_MINUTES–$DURATION_MAX_MINUTES min bound.",
                )
            }
        }
        intent.targetApp?.let { pkg ->
            validPackageNameOrNull(pkg)
                ?: return Validation.Rejected("Target app '$pkg' is not a valid package name.")
            if (pkg in FORBIDDEN_LAUNCH_TARGETS) {
                return Validation.Rejected("Target app '$pkg' is not a permitted launch target.")
            }
        }
        if (intent.routineType == RoutineType.CUSTOM) {
            if (intent.customActions.isEmpty()) {
                return Validation.Rejected("A custom routine needs at least one action.")
            }
            intent.customActions.forEach { target ->
                val result = validateTarget(target)
                if (result is Validation.Rejected) return result
            }
        } else if (intent.customActions.isNotEmpty()) {
            return Validation.Rejected(
                "Custom actions are only accepted for CUSTOM routines.",
            )
        }
        return Validation.Valid
    }

    /** Field-level validation of one action target (guide §27: no unvalidated values). */
    fun validateTarget(target: ActionTarget): Validation {
        if (target.capability !in ALLOWED_CAPABILITIES) {
            return Validation.Rejected("Capability ${target.capability} is not allowlisted.")
        }
        return when (target.capability) {
            AutomationCapability.DO_NOT_DISTURB ->
                requireInt(target, VALID_DND_FILTERS::contains, "interruption filter")

            AutomationCapability.RINGER_MODE ->
                requireInt(target, VALID_RINGER_MODES::contains, "ringer mode")

            AutomationCapability.SCREEN_BRIGHTNESS ->
                requireInt(
                    target, { it in BRIGHTNESS_MIN..BRIGHTNESS_MAX },
                    "brightness ($BRIGHTNESS_MIN–$BRIGHTNESS_MAX)",
                )

            AutomationCapability.SCREEN_TIMEOUT ->
                requireInt(
                    target, { it in TIMEOUT_MIN_MS..TIMEOUT_MAX_MS },
                    "screen timeout (${TIMEOUT_MIN_MS}–${TIMEOUT_MAX_MS} ms)",
                )

            AutomationCapability.LAUNCH_APP -> {
                val pkg = target.stringValue
                    ?: return Validation.Rejected("Launch action needs a package name.")
                validPackageNameOrNull(pkg)
                    ?: return Validation.Rejected("'$pkg' is not a valid package name.")
                if (pkg in FORBIDDEN_LAUNCH_TARGETS) {
                    Validation.Rejected("'$pkg' is not a permitted launch target.")
                } else {
                    Validation.Valid
                }
            }

            AutomationCapability.OPEN_SETTINGS -> {
                val action = target.stringValue
                    ?: return Validation.Rejected("Settings action missing.")
                if (action in ALLOWED_SETTINGS_ACTIONS) {
                    Validation.Valid
                } else {
                    // No arbitrary intent URIs — a future AI cannot smuggle one (§27).
                    Validation.Rejected("Settings action '$action' is not allowlisted.")
                }
            }
        }
    }

    private fun requireInt(
        target: ActionTarget,
        valid: (Int) -> Boolean,
        what: String,
    ): Validation {
        val value = target.intValue
            ?: return Validation.Rejected("Missing value for $what.")
        return if (valid(value)) {
            Validation.Valid
        } else {
            Validation.Rejected("Value $value is not a valid $what.")
        }
    }

    /** Conservative package-name shape check (no URIs, no shell metacharacters). */
    private fun validPackageNameOrNull(pkg: String): String? =
        pkg.takeIf { it.matches(Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$")) }
}
