package com.thraksha.guardian.automation

import kotlinx.serialization.Serializable

/**
 * Phase 9 automation domain model (guide §6). Deliberately free of Android framework
 * types: routines, plans, snapshots and results are plain data, so the planner and the
 * safety policy are unit-testable off-device, and a future Phase 10 AI can only ever
 * hand over a validated [AutomationIntent] — never touch an executor (guide §28).
 *
 * Automation is a separate domain from security (guide §26): it reuses the *principles*
 * (authority checks, verification, structured results, audit) but never the
 * PolicyEngine's threat semantics — "user wants DND" is not "threat requires
 * containment".
 */
enum class RoutineType { MEETING, FOCUS, DRIVING, CUSTOM }

/** The narrow, allowlisted device capabilities automation may touch (guide §27). */
enum class AutomationCapability {
    /** NotificationManager interruption filter. Requires Notification Policy Access. */
    DO_NOT_DISTURB,

    /** AudioManager ringer mode. Silent transitions require Notification Policy Access. */
    RINGER_MODE,

    /** Settings.System brightness value + auto/manual mode. Requires WRITE_SETTINGS. */
    SCREEN_BRIGHTNESS,

    /** Settings.System screen-off timeout. Requires WRITE_SETTINGS. */
    SCREEN_TIMEOUT,

    /** Launch an installed app. Fire-verified only — status OPENED, never ACTED. */
    LAUNCH_APP,

    /** Hand the user to a legitimate Android settings screen. Always user-assisted. */
    OPEN_SETTINGS,
}

/**
 * The desired end state of one action, in primitives so it serializes and so the
 * safety policy can validate every field (guide §27: no unvalidated values).
 * [intValue] carries the DND filter / ringer mode / brightness / timeout-ms;
 * [stringValue] carries a launch package or an allowlisted settings action.
 */
@Serializable
data class ActionTarget(
    val capability: AutomationCapability,
    val intValue: Int? = null,
    val stringValue: String? = null,
)

/** How an action can run right now (guide §11): shown in the preview, never hidden. */
enum class ActionSupport { SUPPORTED, USER_ACTION_REQUIRED, UNSUPPORTED }

/** One step of an [AutomationPlan]. */
data class PlannedAction(
    val target: ActionTarget,
    val label: String,
    /** Required failure stops/rolls back the routine; optional failure → partial (§13). */
    val required: Boolean,
    /** True when the action mutates restorable state (and therefore demands a snapshot). */
    val reversible: Boolean,
    val support: ActionSupport,
    val supportDetail: String,
)

/** The explicit, deterministic plan the UI previews before anything runs (guide §11). */
data class AutomationPlan(
    val routineType: RoutineType,
    val routineLabel: String,
    val durationMinutes: Int?,
    val actions: List<PlannedAction>,
    /** False when a required action is not currently executable (CANNOT RUN, §12). */
    val executable: Boolean,
    val blockedReason: String?,
)

/**
 * The only input surface for requesting automation — what Phase 10's model will be
 * allowed to produce. Validated by [AutomationSafetyPolicy] before planning.
 */
data class AutomationIntent(
    val routineType: RoutineType,
    /** null = until stopped manually. Bounded by the safety policy when present. */
    val durationMinutes: Int? = null,
    /** Optional app the routine should open (validated: installed + launchable). */
    val targetApp: String? = null,
    /** CUSTOM routines only: the chosen action targets (allowlisted capabilities). */
    val customActions: List<ActionTarget> = emptyList(),
)

/** Per-action outcome vocabulary (guide §7). ACTED strictly requires verified state. */
enum class AutomationActionStatus {
    PLANNED,
    EXECUTING,

    /** Executed AND the resulting Android state was re-read and confirmed. */
    ACTED,

    /**
     * Launch-type action: resolved and started without error. Deliberately distinct
     * from ACTED because foreground arrival is not verifiable at Thraksha's privilege.
     */
    OPENED,

    USER_ACTION_REQUIRED,
    FAILED,
    UNSUPPORTED,
    SKIPPED,
    CANCELLED,

    /** Applied earlier, then reverted because a later REQUIRED action failed (§14). */
    ROLLED_BACK,

    /** Snapshot state re-applied AND re-read as restored. */
    RESTORED,
    RESTORE_FAILED,
}

data class AutomationActionResult(
    val action: PlannedAction,
    val status: AutomationActionStatus,
    val detail: String,
)

/**
 * The actual previous state of one capability, captured BEFORE mutation (guide §9).
 * Restoration uses exactly these values; the engine has no defaults table, so
 * "restore to hardcoded defaults" is unrepresentable.
 */
@Serializable
data class SnapshotEntry(
    val capability: AutomationCapability,
    /** Primary previous value (DND filter / ringer mode / brightness / timeout ms). */
    val previousValue: Int? = null,
    /** Secondary previous value (brightness: the auto/manual mode). */
    val previousValue2: Int? = null,
    val readable: Boolean,
    val detail: String,
)

/**
 * Everything needed to honour restoration after process death (guide §22), persisted
 * in the encrypted config store the moment the snapshot exists — before any mutation.
 */
@Serializable
data class PersistedRun(
    val runId: String,
    val routineType: String,
    val routineLabel: String,
    val startedAt: Long,
    /** Epoch millis when the routine should auto-restore; null = until stopped. */
    val expiresAt: Long?,
    val snapshot: List<SnapshotEntry>,
    /** Capabilities actually changed (in execution order) — restore walks this reversed. */
    val appliedCapabilities: List<String>,
)

/** Result of one capability's restoration. */
data class AutomationRestoreResult(
    val capability: AutomationCapability,
    val status: AutomationActionStatus,
    val detail: String,
)

/** Engine lifecycle (guide §15) — the single source of truth Compose observes. */
enum class RunPhase {
    IDLE, PREFLIGHT, EXECUTING, ACTIVE, PARTIAL, FAILED,
    RESTORING, RESTORED, RESTORE_FAILED,
}

data class AutomationEngineState(
    val phase: RunPhase = RunPhase.IDLE,
    val plan: AutomationPlan? = null,
    val actionResults: List<AutomationActionResult> = emptyList(),
    val restoreResults: List<AutomationRestoreResult> = emptyList(),
    val activeRun: PersistedRun? = null,
    val message: String? = null,
)
