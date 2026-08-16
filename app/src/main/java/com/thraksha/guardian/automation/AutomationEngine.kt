package com.thraksha.guardian.automation

import android.content.Context
import android.util.Log
import com.thraksha.guardian.automation.executors.AppLaunchExecutor
import com.thraksha.guardian.automation.executors.AutomationExecutor
import com.thraksha.guardian.automation.executors.BrightnessExecutor
import com.thraksha.guardian.automation.executors.DndExecutor
import com.thraksha.guardian.automation.executors.RingerExecutor
import com.thraksha.guardian.automation.executors.ScreenTimeoutExecutor
import com.thraksha.guardian.data.config.ConfigStore
import com.thraksha.guardian.security.events.SecurityEvent
import com.thraksha.guardian.security.events.SecurityEventBus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/**
 * The deterministic Phase 9 automation engine (guide §1, §49):
 *
 *   intent → planner → safety policy → preflight → SNAPSHOT (persisted before any
 *   mutation) → executors → verify actual Android state → ACTIVE → stop/expiry →
 *   restore snapshot → verify restoration → audit.
 *
 * Honesty invariants:
 *  * the actual previous state is captured and persisted (encrypted config store)
 *    BEFORE the first mutation — process death never loses restoration duty (§9, §22);
 *  * ACTED/RESTORED only after the resulting Android state was re-read (§7);
 *  * launch actions report OPENED, never ACTED (§7 + capability matrix);
 *  * one active routine at a time (§23);
 *  * a REQUIRED action failure rolls back the already-applied reversible actions from
 *    the snapshot, each rollback individually verified and reported (§14);
 *  * restoration is per-capability honest — a failed restore is RESTORE_FAILED and the
 *    engine says exactly what remains changed (§31);
 *  * every lifecycle stage lands in the ONE tamper-evident audit chain as AUTOMATION
 *    rows (§32/§33). Security monitoring is never touched by routines (§34).
 */
object AutomationEngine {

    private const val TAG = "AutomationEngine"
    private const val KEY_ACTIVE_RUN = "automation.active_run"

    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Mutex()

    private val _state = MutableStateFlow(AutomationEngineState())
    val state: StateFlow<AutomationEngineState> = _state.asStateFlow()

    private val executors: Map<AutomationCapability, AutomationExecutor> = listOf(
        DndExecutor(), RingerExecutor(), BrightnessExecutor(),
        ScreenTimeoutExecutor(), AppLaunchExecutor(),
    ).associateBy { it.capability }

    fun probe(context: Context): CapabilityProbe =
        AndroidCapabilityProbe(context, executors)

    /** The §11 preview: the exact plan [start] would execute, without executing it. */
    fun preview(context: Context, intent: AutomationIntent): AutomationPlan =
        AutomationPlanner.plan(intent, probe(context))

    suspend fun start(context: Context, intent: AutomationIntent) {
        val appContext = context.applicationContext
        lock.withLock {
            if (_state.value.activeRun != null) {
                // §23: one routine at a time; the UI offers STOP & RESTORE first.
                _state.value = _state.value.copy(
                    message = "Another routine is active — stop and restore it first.",
                )
                return
            }
            audit("REQUESTED", AutomationPlanner.routineLabel(intent.routineType),
                "duration=${intent.durationMinutes?.let { "$it min" } ?: "until stopped"}")

            _state.value = AutomationEngineState(phase = RunPhase.PREFLIGHT)
            val plan = AutomationPlanner.plan(intent, probe(appContext))
            audit(
                "PLANNED", plan.routineLabel,
                "${plan.actions.size} action(s); executable=${plan.executable}" +
                    (plan.blockedReason?.let { "; $it" } ?: ""),
            )
            if (!plan.executable) {
                _state.value = AutomationEngineState(
                    phase = RunPhase.FAILED, plan = plan,
                    message = plan.blockedReason ?: "Plan is not executable.",
                )
                return
            }

            // §9: read + persist the ACTUAL previous state of everything we intend to
            // change, before mutating anything.
            val runId = "run-${System.currentTimeMillis()}"
            val snapshots = mutableListOf<SnapshotEntry>()
            for (action in plan.actions) {
                if (!action.reversible || action.support != ActionSupport.SUPPORTED) continue
                val entry = runCatching {
                    executors.getValue(action.target.capability).snapshot(appContext)
                }.getOrElse {
                    SnapshotEntry(
                        action.target.capability, readable = false,
                        detail = "snapshot read threw: ${it.message}",
                    )
                }
                if (!entry.readable && action.required) {
                    // Restoration cannot be guaranteed for a required mutation → refuse
                    // to run rather than overwrite unknown user state (§12, §48).
                    _state.value = AutomationEngineState(
                        phase = RunPhase.FAILED, plan = plan,
                        message = "Cannot run: current ${action.target.capability} state " +
                            "is unreadable, so restoration could not be guaranteed.",
                    )
                    audit("REFUSED", plan.routineLabel, "unreadable state for required " +
                        "action ${action.target.capability}; not executed")
                    return
                }
                snapshots += entry
            }

            var persisted = PersistedRun(
                runId = runId,
                routineType = intent.routineType.name,
                routineLabel = plan.routineLabel,
                startedAt = System.currentTimeMillis(),
                expiresAt = intent.durationMinutes
                    ?.let { System.currentTimeMillis() + it * 60_000L },
                snapshot = snapshots,
                appliedCapabilities = emptyList(),
            )
            persist(appContext, persisted)
            audit("STARTED", plan.routineLabel, "snapshot captured for " +
                snapshots.joinToString { it.capability.name } + "; run $runId")

            _state.value = AutomationEngineState(
                phase = RunPhase.EXECUTING, plan = plan, activeRun = persisted,
            )

            // §13: deterministic order, read→execute→verify→record per action.
            val results = mutableListOf<AutomationActionResult>()
            for (action in plan.actions) {
                when (action.support) {
                    ActionSupport.USER_ACTION_REQUIRED -> {
                        results += AutomationActionResult(
                            action, AutomationActionStatus.USER_ACTION_REQUIRED,
                            action.supportDetail,
                        )
                        continue
                    }
                    ActionSupport.UNSUPPORTED -> {
                        results += AutomationActionResult(
                            action, AutomationActionStatus.UNSUPPORTED, action.supportDetail,
                        )
                        continue
                    }
                    ActionSupport.SUPPORTED -> Unit
                }

                val executor = executors.getValue(action.target.capability)
                val applied = if (testFailCapability == action.target.capability) {
                    // Instrumented-test hook: forces THIS action to fail so the §14
                    // rollback path can be proven against real device state. Never set
                    // in production code paths.
                    false
                } else {
                    runCatching { executor.apply(appContext, action.target) }
                        .onFailure { Log.w(TAG, "apply threw for ${action.label}", it) }
                        .getOrDefault(false)
                }
                val verified = applied && runCatching {
                    executor.verify(appContext, action.target)
                }.getOrDefault(false)

                if (verified) {
                    val status = if (action.target.capability == AutomationCapability.LAUNCH_APP) {
                        AutomationActionStatus.OPENED
                    } else {
                        AutomationActionStatus.ACTED
                    }
                    results += AutomationActionResult(
                        action, status,
                        if (status == AutomationActionStatus.ACTED) {
                            "verified: Android state now matches the target"
                        } else {
                            "launch fired; foreground arrival is not claimed"
                        },
                    )
                    if (action.reversible) {
                        persisted = persisted.copy(
                            appliedCapabilities = persisted.appliedCapabilities +
                                action.target.capability.name,
                        )
                        persist(appContext, persisted)
                    }
                    audit(
                        if (status == AutomationActionStatus.ACTED) "ACTION_VERIFIED" else "ACTION_OPENED",
                        plan.routineLabel, action.label,
                    )
                } else {
                    val detail = if (applied) {
                        "executed but Android state did not confirm the change"
                    } else {
                        "Android refused the change"
                    }
                    results += AutomationActionResult(
                        action, AutomationActionStatus.FAILED, detail,
                    )
                    audit("ACTION_FAILED", plan.routineLabel, "${action.label} — $detail")

                    if (action.required) {
                        // §14: roll the already-applied reversible actions back.
                        val rollback = restoreCapabilities(appContext, persisted)
                        clearPersist(appContext)
                        val rolledBack = rollback.map {
                            it.copy(
                                status = if (it.status == AutomationActionStatus.RESTORED) {
                                    AutomationActionStatus.ROLLED_BACK
                                } else {
                                    it.status
                                },
                            )
                        }
                        _state.value = AutomationEngineState(
                            phase = RunPhase.FAILED, plan = plan,
                            actionResults = results,
                            restoreResults = rolledBack,
                            message = "Required action failed (${action.label}); " +
                                "applied changes were rolled back from the snapshot.",
                        )
                        audit(
                            "ROLLED_BACK", plan.routineLabel,
                            rolledBack.joinToString { "${it.capability}: ${it.status}" }
                                .ifEmpty { "nothing had been applied yet" },
                        )
                        return
                    }
                }
            }

            AutomationScheduler.schedule(appContext, persisted)
            AutomationNotifier.showActive(appContext, persisted)

            val anyFailed = results.any { it.status == AutomationActionStatus.FAILED }
            _state.value = AutomationEngineState(
                phase = if (anyFailed) RunPhase.PARTIAL else RunPhase.ACTIVE,
                plan = plan, actionResults = results, activeRun = persisted,
            )
            val acted = results.count { it.status == AutomationActionStatus.ACTED }
            audit(
                "ACTIVE", plan.routineLabel,
                "$acted verified action(s)" +
                    (if (anyFailed) " (optional failures recorded)" else "") +
                    (persisted.expiresAt?.let {
                        "; auto-restore at ${java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).format(java.util.Date(it))}"
                    } ?: "; active until stopped"),
            )
        }
    }

    /** STOP & RESTORE (§21) — also the expiry and recovery path. */
    suspend fun stop(context: Context, reason: String) {
        val appContext = context.applicationContext
        lock.withLock {
            val run = _state.value.activeRun ?: loadPersisted(appContext)
            if (run == null) {
                _state.value = _state.value.copy(message = "No active routine to stop.")
                return
            }
            _state.value = _state.value.copy(phase = RunPhase.RESTORING, activeRun = run)
            audit("RESTORE_REQUESTED", run.routineLabel, reason)

            val results = restoreCapabilities(appContext, run)
            clearPersist(appContext)
            AutomationScheduler.cancel(appContext)

            val allOk = results.all { it.status == AutomationActionStatus.RESTORED }
            AutomationNotifier.showRestored(appContext, run, allOk)

            _state.value = AutomationEngineState(
                phase = if (allOk) RunPhase.RESTORED else RunPhase.RESTORE_FAILED,
                plan = _state.value.plan,
                actionResults = _state.value.actionResults,
                restoreResults = results,
                message = if (allOk) {
                    "${run.routineLabel} ended — previous settings restored."
                } else {
                    "PARTIAL RESTORE — some settings could not be verified as restored."
                },
            )
            audit(
                if (allOk) "RESTORED" else "RESTORE_FAILED", run.routineLabel,
                results.joinToString { "${it.capability}: ${it.status}" }
                    .ifEmpty { "no reversible changes had been applied" },
            )
        }
    }

    /**
     * §22 lifecycle recovery: called once at app start. Reconstructs ACTIVE from the
     * persisted run, or — if the duration expired while the process was dead —
     * restores immediately. Restoration duty is never silently dropped.
     */
    suspend fun recover(context: Context) {
        val appContext = context.applicationContext
        val run = lock.withLock {
            if (_state.value.activeRun != null) return
            loadPersisted(appContext) ?: return
        }
        if (run.expiresAt != null && run.expiresAt <= System.currentTimeMillis()) {
            audit(
                "RECOVERED", run.routineLabel,
                "process restarted after the routine expired — restoring now",
            )
            stop(appContext, "duration expired while the app was not running")
        } else {
            lock.withLock {
                _state.value = AutomationEngineState(
                    phase = RunPhase.ACTIVE, activeRun = run,
                    message = "${run.routineLabel} reconstructed after app restart.",
                )
            }
            AutomationScheduler.schedule(appContext, run)
            AutomationNotifier.showActive(appContext, run)
            audit(
                "RECOVERED", run.routineLabel,
                "active routine reconstructed after process restart; restoration duty intact",
            )
        }
    }

    /** Clears a finished (RESTORED/FAILED) result panel back to IDLE. UI convenience. */
    fun acknowledgeResult() {
        val current = _state.value
        if (current.activeRun == null && current.phase != RunPhase.EXECUTING &&
            current.phase != RunPhase.RESTORING
        ) {
            _state.value = AutomationEngineState()
        }
    }

    // ---- internals ----

    /** Restores applied capabilities in reverse order, each verified individually. */
    private suspend fun restoreCapabilities(
        context: Context,
        run: PersistedRun,
    ): List<AutomationRestoreResult> =
        run.appliedCapabilities.reversed().mapNotNull { name ->
            val capability = runCatching { AutomationCapability.valueOf(name) }.getOrNull()
                ?: return@mapNotNull null
            val entry = run.snapshot.firstOrNull { it.capability == capability }
                ?: return@mapNotNull AutomationRestoreResult(
                    capability, AutomationActionStatus.RESTORE_FAILED,
                    "no snapshot entry — cannot restore",
                )
            val executor = executors.getValue(capability)
            val restored = runCatching { executor.restore(context, entry) }
                .onFailure { Log.w(TAG, "restore threw for $capability", it) }
                .getOrDefault(false)
            val verified = restored && runCatching {
                executor.verifyRestored(context, entry)
            }.getOrDefault(false)
            val result = if (verified) {
                AutomationRestoreResult(
                    capability, AutomationActionStatus.RESTORED,
                    "verified back to ${entry.detail.removePrefix("previous ")}",
                )
            } else {
                AutomationRestoreResult(
                    capability, AutomationActionStatus.RESTORE_FAILED,
                    "restore " + (if (restored) "did not verify" else "was refused") +
                        " — ${entry.detail}",
                )
            }
            audit(
                if (verified) "STATE_RESTORED" else "STATE_RESTORE_FAILED",
                run.routineLabel, "$capability — ${result.detail}",
            )
            result
        }

    private suspend fun persist(context: Context, run: PersistedRun) {
        runCatching {
            ConfigStore(context).putRawValue(KEY_ACTIVE_RUN, json.encodeToString(PersistedRun.serializer(), run))
        }.onFailure { Log.e(TAG, "Failed to persist automation run", it) }
    }

    private suspend fun loadPersisted(context: Context): PersistedRun? =
        runCatching {
            ConfigStore(context).getRawValue(KEY_ACTIVE_RUN)
                ?.let { json.decodeFromString(PersistedRun.serializer(), it) }
        }.onFailure { Log.e(TAG, "Failed to load persisted automation run", it) }
            .getOrNull()

    private suspend fun clearPersist(context: Context) {
        runCatching { ConfigStore(context).putRawValue(KEY_ACTIVE_RUN, "") }
            .onFailure { Log.e(TAG, "Failed to clear persisted automation run", it) }
    }

    private suspend fun audit(stage: String, routine: String, detail: String) {
        SecurityEventBus.emit(SecurityEvent.AutomationEvent(stage, routine, detail))
    }

    /** Test-only: force one capability's apply to fail (rollback proof). */
    @Volatile
    internal var testFailCapability: AutomationCapability? = null

    /** Test-only reset (does not touch persisted state). */
    internal fun resetForTest() {
        _state.value = AutomationEngineState()
        testFailCapability = null
    }
}
