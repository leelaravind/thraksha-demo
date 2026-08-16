package com.thraksha.guardian.automation

/**
 * Deterministic routine templates + planner (guide §11, §16–§19).
 *
 * Pure Kotlin: capability availability is supplied through [CapabilityProbe], so plans
 * are unit-testable off-device and identical inputs always produce identical plans.
 * Every action is classified SUPPORTED / USER_ACTION_REQUIRED / UNSUPPORTED before
 * anything runs, and unsupported actions stay visible in the plan — never silently
 * dropped (§11).
 */
object AutomationTargets {
    // NotificationManager interruption filters (stable Android constants).
    const val DND_ALL = 1
    const val DND_PRIORITY = 2

    // AudioManager ringer modes.
    const val RINGER_SILENT = 0
    const val RINGER_VIBRATE = 1
    const val RINGER_NORMAL = 2

    /** ~30% of 255 — calm meeting brightness. */
    const val BRIGHTNESS_MEETING = 77

    /** ~85% of 255 — daylight-visible driving brightness. */
    const val BRIGHTNESS_DRIVING = 217

    /** 10 minutes — focus/driving screen timeout. */
    const val TIMEOUT_LONG_MS = 600_000

    /** Default launch candidates, first launchable wins; none installed → action omitted. */
    val MEETING_APP_CANDIDATES = listOf(
        "com.samsung.android.calendar",
        "com.google.android.calendar",
    )
    val DRIVING_APP_CANDIDATES = listOf(
        "com.google.android.apps.maps",
        "com.waze",
    )
}

/** How the planner learns what the device currently allows (Android impl or test fake). */
interface CapabilityProbe {
    fun support(capability: AutomationCapability): Pair<ActionSupport, String>
    fun launchSupport(packageName: String?): Pair<ActionSupport, String>

    /** First candidate that is installed and launchable, or null. */
    fun firstLaunchable(candidates: List<String>): String?
}

object AutomationPlanner {

    /**
     * Validates the intent (safety policy first, §27) and produces the deterministic
     * plan. A rejected intent yields a non-executable plan carrying the reason.
     */
    fun plan(intent: AutomationIntent, probe: CapabilityProbe): AutomationPlan {
        val label = routineLabel(intent.routineType)

        val validation = AutomationSafetyPolicy.validateIntent(intent)
        if (validation is AutomationSafetyPolicy.Validation.Rejected) {
            return AutomationPlan(
                routineType = intent.routineType,
                routineLabel = label,
                durationMinutes = intent.durationMinutes,
                actions = emptyList(),
                executable = false,
                blockedReason = "Safety policy rejected the request: ${validation.reason}",
            )
        }

        val actions = templateActions(intent, probe).map { spec ->
            val (support, detail) = when (spec.target.capability) {
                AutomationCapability.LAUNCH_APP ->
                    probe.launchSupport(spec.target.stringValue)

                AutomationCapability.OPEN_SETTINGS ->
                    ActionSupport.USER_ACTION_REQUIRED to
                        "hand-off to Android settings; completion is the user's"

                else -> probe.support(spec.target.capability)
            }
            PlannedAction(
                target = spec.target,
                label = spec.label,
                required = spec.required,
                reversible = spec.reversible,
                support = support,
                supportDetail = detail,
            )
        }

        val blockedBy = actions.filter {
            it.required && it.support != ActionSupport.SUPPORTED
        }
        return AutomationPlan(
            routineType = intent.routineType,
            routineLabel = label,
            durationMinutes = intent.durationMinutes,
            actions = actions,
            executable = actions.isNotEmpty() && blockedBy.isEmpty(),
            blockedReason = when {
                actions.isEmpty() -> "The routine contains no actions."
                blockedBy.isNotEmpty() ->
                    "Required action(s) not currently executable: " +
                        blockedBy.joinToString { "${it.label} (${it.supportDetail})" }
                else -> null
            },
        )
    }

    fun routineLabel(type: RoutineType): String = when (type) {
        RoutineType.MEETING -> "Meeting Mode"
        RoutineType.FOCUS -> "Focus Mode"
        RoutineType.DRIVING -> "Driving Mode"
        RoutineType.CUSTOM -> "Custom Routine"
    }

    private data class ActionSpec(
        val target: ActionTarget,
        val label: String,
        val required: Boolean,
        val reversible: Boolean,
    )

    /**
     * The deterministic action templates (§16–§19). Execution order is the list order;
     * restoration walks the applied actions in reverse. Ringer changes precede the DND
     * filter change so the audible profile is settled before interruptions are gated.
     */
    private fun templateActions(
        intent: AutomationIntent,
        probe: CapabilityProbe,
    ): List<ActionSpec> = when (intent.routineType) {
        RoutineType.MEETING -> buildList {
            // Optional courtesy action: DND is the routine's real promise. On AOSP,
            // an active DND filter coerces the presented ringer mode to SILENT (Samsung
            // keeps VIBRATE) — a documented cross-capability interplay, so the ringer
            // must not be a required action whose later coerced state could be
            // misread as failure (capability matrix §3).
            add(
                ActionSpec(
                    ActionTarget(
                        AutomationCapability.RINGER_MODE,
                        AutomationTargets.RINGER_VIBRATE,
                    ),
                    "Set ringer to vibrate", required = false, reversible = true,
                ),
            )
            add(
                ActionSpec(
                    ActionTarget(
                        AutomationCapability.SCREEN_BRIGHTNESS,
                        AutomationTargets.BRIGHTNESS_MEETING,
                    ),
                    "Dim screen brightness", required = false, reversible = true,
                ),
            )
            add(
                ActionSpec(
                    ActionTarget(
                        AutomationCapability.DO_NOT_DISTURB,
                        AutomationTargets.DND_PRIORITY,
                    ),
                    "Enable Do Not Disturb (priority only)",
                    required = true, reversible = true,
                ),
            )
            launchSpec(
                intent.targetApp
                    ?: probe.firstLaunchable(AutomationTargets.MEETING_APP_CANDIDATES),
                "Open calendar/meeting app",
            )?.let { add(it) }
        }

        RoutineType.FOCUS -> buildList {
            // Optional for the same zen-coercion reason as Meeting Mode.
            add(
                ActionSpec(
                    ActionTarget(
                        AutomationCapability.RINGER_MODE,
                        AutomationTargets.RINGER_VIBRATE,
                    ),
                    "Set ringer to vibrate", required = false, reversible = true,
                ),
            )
            add(
                ActionSpec(
                    ActionTarget(
                        AutomationCapability.SCREEN_TIMEOUT,
                        AutomationTargets.TIMEOUT_LONG_MS,
                    ),
                    "Extend screen timeout to 10 min", required = false, reversible = true,
                ),
            )
            add(
                ActionSpec(
                    ActionTarget(
                        AutomationCapability.DO_NOT_DISTURB,
                        AutomationTargets.DND_PRIORITY,
                    ),
                    "Enable Do Not Disturb (priority only)",
                    required = true, reversible = true,
                ),
            )
            // App-level restriction has no legitimate automatic path at this privilege
            // (capability matrix §1) — first-class user-assisted plan item, not hidden.
            add(
                ActionSpec(
                    ActionTarget(
                        AutomationCapability.OPEN_SETTINGS,
                        stringValue = "android.settings.SETTINGS",
                    ),
                    "Restrict distracting apps (manual)",
                    required = false, reversible = false,
                ),
            )
        }

        RoutineType.DRIVING -> buildList {
            // Safety-first (§18): calls stay audible, screen bright, user-started only.
            add(
                ActionSpec(
                    ActionTarget(
                        AutomationCapability.RINGER_MODE,
                        AutomationTargets.RINGER_NORMAL,
                    ),
                    "Ringer audible (hear calls without touching the phone)",
                    required = true, reversible = true,
                ),
            )
            add(
                ActionSpec(
                    ActionTarget(
                        AutomationCapability.SCREEN_BRIGHTNESS,
                        AutomationTargets.BRIGHTNESS_DRIVING,
                    ),
                    "Raise brightness for daylight visibility",
                    required = true, reversible = true,
                ),
            )
            add(
                ActionSpec(
                    ActionTarget(
                        AutomationCapability.SCREEN_TIMEOUT,
                        AutomationTargets.TIMEOUT_LONG_MS,
                    ),
                    "Extend screen timeout to 10 min", required = false, reversible = true,
                ),
            )
            launchSpec(
                intent.targetApp
                    ?: probe.firstLaunchable(AutomationTargets.DRIVING_APP_CANDIDATES),
                "Open navigation app",
            )?.let { add(it) }
        }

        RoutineType.CUSTOM -> intent.customActions.map { target ->
            ActionSpec(
                target = target,
                label = customLabel(target),
                // Custom actions are optional by design: the builder proves the engine
                // is composable; partial results are honest results (§19).
                required = false,
                reversible = target.capability != AutomationCapability.LAUNCH_APP &&
                    target.capability != AutomationCapability.OPEN_SETTINGS,
            )
        }
    }

    private fun launchSpec(packageName: String?, label: String): ActionSpec? =
        packageName?.let {
            ActionSpec(
                ActionTarget(AutomationCapability.LAUNCH_APP, stringValue = it),
                "$label (${it.substringAfterLast('.')})",
                required = false,
                reversible = false,
            )
        }

    private fun customLabel(target: ActionTarget): String = when (target.capability) {
        AutomationCapability.DO_NOT_DISTURB ->
            if (target.intValue == AutomationTargets.DND_ALL) {
                "Disable Do Not Disturb"
            } else {
                "Enable Do Not Disturb"
            }
        AutomationCapability.RINGER_MODE ->
            "Set ringer to " + when (target.intValue) {
                AutomationTargets.RINGER_SILENT -> "silent"
                AutomationTargets.RINGER_VIBRATE -> "vibrate"
                else -> "normal"
            }
        AutomationCapability.SCREEN_BRIGHTNESS ->
            "Set brightness to ${target.intValue}/255"
        AutomationCapability.SCREEN_TIMEOUT ->
            "Set screen timeout to ${(target.intValue ?: 0) / 1000}s"
        AutomationCapability.LAUNCH_APP ->
            "Open ${target.stringValue?.substringAfterLast('.') ?: "app"}"
        AutomationCapability.OPEN_SETTINGS -> "Open Android settings"
    }
}
