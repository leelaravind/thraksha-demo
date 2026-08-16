package com.thraksha.guardian

import android.app.Application
import android.util.Log
import com.thraksha.guardian.ai.OnDeviceIntentModel
import com.thraksha.guardian.data.audit.AuditLog
import com.thraksha.guardian.data.config.ConfigStore
import com.thraksha.guardian.data.db.DatabaseProvider
import com.thraksha.guardian.security.FoundationStatus
import com.thraksha.guardian.security.engine.SecurityAuditEngine
import com.thraksha.guardian.security.events.SecurityEvent
import com.thraksha.guardian.security.events.SecurityEventBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Application entry point.
 *
 * Loads the SQLCipher native library once per process (required before the encrypted
 * Room database is opened), seeds config defaults on first run, and subscribes the
 * audit log to the security event bus so ThreatDetected / ActionTaken events are
 * recorded in the tamper-evident chain.
 *
 * Every step is failure-visible via [FoundationStatus]: if the encrypted store cannot
 * be opened the process does not crash and does not fall back to an unencrypted or
 * freshly-created database — it publishes a Failed state that the dashboard renders,
 * so the app never claims protection it does not have.
 */
class ThrakshaApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // SQLCipher (net.zetetic:sqlcipher-android) requires the native lib before DB open.
        val nativeLoaded = runCatching { System.loadLibrary("sqlcipher") }
            .onFailure {
                FoundationStatus.reportFailure(
                    "SQLCipher native library could not be loaded — encrypted storage is unavailable.",
                    it,
                )
            }
            .isSuccess
        if (!nativeLoaded) return

        initialiseSecureFoundation()
    }

    /**
     * Opens the encrypted store, seeds config, and starts the bus → audit collector.
     * Runs off the main thread because the Keystore unwrap and first DB open are both
     * genuine work.
     */
    private fun initialiseSecureFoundation() {
        appScope.launch {
            val auditDao = runCatching { DatabaseProvider.get(this@ThrakshaApplication).auditDao() }
                .onFailure {
                    FoundationStatus.reportFailure(
                        "Encrypted store could not be opened. The audit trail is NOT recording.",
                        it,
                    )
                }
                .getOrNull() ?: return@launch

            runCatching { ConfigStore(this@ThrakshaApplication).ensureDefaults() }
                .onFailure { Log.w(TAG, "Config defaults could not be seeded", it) }

            startAuditCollector(AuditLog(auditDao))
        }
    }

    /**
     * The bus has `replay = 0`, so anything emitted before this collector is actually
     * subscribed would be dropped. [FoundationStatus.markReady] is therefore deferred
     * until `subscriptionCount` confirms the subscription is live; the launch audit
     * waits on that signal before emitting its first finding.
     */
    private fun startAuditCollector(auditLog: AuditLog) {
        appScope.launch {
            SecurityEventBus.events.collect { event ->
                runCatching {
                    when (event) {
                        is SecurityEvent.ThreatDetected -> auditLog.append(
                            type = "THREAT",
                            details = "${event.signalId} [${event.severity}] ${event.details}",
                            tier = event.confidence,
                        )
                        is SecurityEvent.ActionTaken -> auditLog.append(
                            type = "ACTION",
                            details = "${event.action} -> ${event.target}: ${event.result}",
                            tier = 0,
                        )
                        is SecurityEvent.ScanCompleted -> auditLog.append(
                            type = "SCAN",
                            details = event.summary,
                            tier = 0,
                        )
                        is SecurityEvent.DeviceScanStarted -> auditLog.append(
                            type = "SCAN",
                            details = event.summary,
                            tier = 0,
                        )
                        is SecurityEvent.DecisionMade -> auditLog.append(
                            type = "DECISION",
                            details = "${event.decisionType} ${event.appName} " +
                                "(${event.packageName}): ${event.findingCount} finding(s), " +
                                "max severity ${event.highestSeverity} — ${event.explanation}",
                            tier = event.maxRuleConfidence,
                        )
                        is SecurityEvent.AdviceIssued -> auditLog.append(
                            type = "ADVISED",
                            details = "${event.appName} (${event.packageName}): " +
                                event.recommendations.joinToString("; ") +
                                " — no automatic OS action performed",
                            tier = 0,
                        )
                        is SecurityEvent.ModeChanged -> auditLog.append(
                            type = "MODE",
                            details = "Execution mode changed to ${event.level}",
                            tier = 0,
                        )
                        is SecurityEvent.AutomationEvent -> auditLog.append(
                            type = "AUTOMATION",
                            details = "[${event.stage}] ${event.routine}: ${event.detail}",
                            tier = 0,
                        )
                        is SecurityEvent.UserActionPerformed -> auditLog.append(
                            type = "USER",
                            details = "${event.actionLabel} on ${event.appName} " +
                                "(${event.packageName}) — ${event.status}: ${event.detail}",
                            tier = 0,
                        )
                        is SecurityEvent.NetworkAttemptObserved -> auditLog.append(
                            type = "NETWORK",
                            details = "${event.packageName}: outbound ${event.protocolName} " +
                                "attempt to ${event.destinationIp}:${event.destinationPort} " +
                                "(${event.packetLength} B) observed by Network Guard",
                            tier = 0,
                        )
                    }
                }.onFailure { Log.e(TAG, "Failed to append audit entry for $event", it) }
            }
        }

        appScope.launch {
            SecurityEventBus.subscriptionCount.first { it > 0 }
            FoundationStatus.markReady()
            // Phase 9 (guide §22): if a routine was active when the process died, its
            // restoration duty is reconstructed (or discharged, if it expired) before
            // anything else can start a new one. Sequenced after the collector is live
            // so the recovery audit rows are never lost to the replay=0 window.
            runCatching {
                com.thraksha.guardian.automation.AutomationEngine.recover(this@ThrakshaApplication)
            }.onFailure { Log.e(TAG, "Automation recovery failed", it) }
            runLaunchAudit()
        }
    }

    /**
     * The launch security audit (guide §3.8).
     *
     * Deliberately sequenced after `subscriptionCount > 0`, which is the point at which
     * every precondition genuinely holds: the encrypted store is open, the bus → audit
     * collector is subscribed (so no finding is lost to the `replay = 0` window), and the
     * package inventory can run.
     *
     * It runs exactly once per process, here — never from a Compose effect, so it cannot
     * be re-triggered by recomposition. Re-scanning is an explicit user action via
     * `Run Security Check`, and both paths call the same [SecurityAuditEngine.runAudit].
     */
    private suspend fun runLaunchAudit() {
        val result = runCatching { SecurityAuditEngine.runAudit(this) }
            .onFailure { Log.e(TAG, "Launch security audit failed", it) }
            .getOrNull() ?: return
        Log.i(TAG, "Launch security audit complete: $result")
    }

    /**
     * Releases the on-device model when the system is genuinely short of memory
     * (Phase 12A §19).
     *
     * The loaded Gemma engine resides at roughly 2.9 GB — about 37 % of this phone's
     * physical RAM — and the process runs at foreground-service priority, so without this
     * hook Android would evict the user's *other* apps rather than reclaim from Thraksha,
     * and the model would stay resident indefinitely after a single request. Keeping it
     * loaded is still the right default (a reload costs ~25 s); giving it back the moment
     * the system needs the memory is what makes that default acceptable on an everyday
     * phone.
     *
     * [OnDeviceIntentModel.unload] takes the same mutex as inference, so an in-flight
     * request completes first, and it leaves the state machine at READY with integrity
     * still verified — the next request simply reloads. `UI_HIDDEN` and `RUNNING_MODERATE`
     * are deliberately excluded: they signal "app not visible" / "mild pressure", not
     * memory scarcity, and unloading there would cost a reload for no real benefit.
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        val underPressure = when (level) {
            TRIM_MEMORY_RUNNING_LOW,
            TRIM_MEMORY_RUNNING_CRITICAL,
            TRIM_MEMORY_BACKGROUND,
            TRIM_MEMORY_MODERATE,
            TRIM_MEMORY_COMPLETE,
            -> true

            else -> false
        }
        if (underPressure && OnDeviceIntentModel.isLoaded) {
            Log.i(TAG, "onTrimMemory($level): releasing the on-device model")
            appScope.launch {
                runCatching { OnDeviceIntentModel.unload() }
                    .onFailure { Log.w(TAG, "Model unload on memory pressure failed", it) }
            }
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        if (OnDeviceIntentModel.isLoaded) {
            Log.i(TAG, "onLowMemory: releasing the on-device model")
            appScope.launch {
                runCatching { OnDeviceIntentModel.unload() }
                    .onFailure { Log.w(TAG, "Model unload on low memory failed", it) }
            }
        }
    }

    private companion object {
        const val TAG = "ThrakshaApplication"
    }
}
