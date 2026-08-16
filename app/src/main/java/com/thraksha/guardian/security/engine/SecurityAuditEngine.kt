package com.thraksha.guardian.security.engine

import android.content.Context
import android.util.Log
import com.thraksha.guardian.security.SecurityCapability
import com.thraksha.guardian.security.baseline.AppBaselines
import com.thraksha.guardian.security.events.SecurityEvent
import com.thraksha.guardian.security.events.SecurityEventBus
import com.thraksha.guardian.security.inventory.AppInventory
import com.thraksha.guardian.security.inventory.AppType
import com.thraksha.guardian.security.inventory.DemoAppRegistry
import com.thraksha.guardian.security.inventory.ObservedApp
import com.thraksha.guardian.security.enforcement.DeviceOwnerEnforcer
import com.thraksha.guardian.security.policy.AdvisoryEnforcer
import com.thraksha.guardian.security.policy.DecisionType
import com.thraksha.guardian.security.policy.Enforcer
import com.thraksha.guardian.security.policy.EnforcementResult
import com.thraksha.guardian.security.policy.EnforcementVerdict
import com.thraksha.guardian.security.policy.SecurityDecision
import com.thraksha.guardian.security.policy.SecurityResponseCoordinator
import com.thraksha.guardian.security.rulepack.Rulepack
import com.thraksha.guardian.security.rulepack.RulepackLoader
import com.thraksha.guardian.security.threatintel.AppFingerprinter
import com.thraksha.guardian.security.threatintel.IndicatorTypes
import com.thraksha.guardian.security.threatintel.ThreatIntelligenceScanner
import com.thraksha.guardian.security.threatintel.ThreatPackLoader
import com.thraksha.guardian.security.threatintel.ThreatPackState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Per-app outcome of an audit. */
data class AppAuditResult(
    val packageName: String,
    val appName: String,
    val appType: AppType,
    val findings: List<Finding>,

    /** The policy verdict for this app. Null only when the policy stage could not run. */
    val decision: SecurityDecision? = null,

    /**
     * What was done about the decision. Null for OBSERVE decisions (nothing is executed,
     * by design) and when the policy stage could not run.
     */
    val enforcement: EnforcementResult? = null,
) {
    val isClean: Boolean get() = findings.isEmpty()
}

/**
 * The state of the threat-intelligence pillar within one audit. When [available] is
 * false the audit still completes on the profile pillar, but the result carries the
 * failure visibly — an invalid threat pack must never be indistinguishable from "no
 * threats found".
 */
data class ThreatIntelStatus(
    val available: Boolean,
    val packVersion: Int?,
    val indicatorsEvaluated: Int,
    val detail: String,
)

/** Per-stage scan durations in milliseconds (guide §6.18). */
data class ScanTimings(
    val inventoryMs: Long,
    val ruleEngineMs: Long,
    val certFingerprintMs: Long,
    val apkHashMs: Long,
    val totalMs: Long,
)

/** The outcome of one complete audit run. */
sealed interface AuditResult {

    /** No audit has run yet in this process. */
    data object NotRun : AuditResult

    /** An audit completed. [results] is empty when no registered demo app is installed. */
    data class Completed(
        val results: List<AppAuditResult>,
        val rulepackVersion: Int,
        val rulesEvaluated: Int,
        val completedAt: Long,
        val threatIntel: ThreatIntelStatus = ThreatIntelStatus(
            available = false,
            packVersion = null,
            indicatorsEvaluated = 0,
            detail = "not evaluated",
        ),
        val timings: ScanTimings? = null,
    ) : AuditResult {
        val allFindings: List<Finding> get() = results.flatMap { it.findings }
        val appsScanned: Int get() = results.size
        val threatsFound: Int get() = allFindings.size
        val cleanApps: List<AppAuditResult> get() = results.filter { it.isClean }
        val flaggedApps: List<AppAuditResult> get() = results.filterNot { it.isClean }
    }

    /**
     * The audit could not run. Most importantly this covers rulepack signature failure —
     * the engine refuses to evaluate unverified rules rather than falling back to them.
     */
    data class Failed(val message: String, val cause: String?) : AuditResult
}

/**
 * The single entry point for security auditing.
 *
 * Both the automatic launch audit and the dashboard's `Run Security Check` button call
 * [runAudit]; there is deliberately no second code path, so what the button demonstrates
 * is exactly what runs at startup.
 *
 * Pipeline: **verified rulepack → AppInventory → AppType/baseline → RuleEngine →
 * SecurityEventBus → AuditLog**. The engine emits events and returns a result; it performs
 * no enforcement and holds no `DevicePolicyManager`. Detection and enforcement stay separate.
 */
object SecurityAuditEngine {

    private const val TAG = "SecurityAuditEngine"

    private val auditLock = Mutex()

    private val _state = MutableStateFlow<AuditResult>(AuditResult.NotRun)

    /** Latest audit outcome, shared by the launch audit and the manual trigger. */
    val state: StateFlow<AuditResult> = _state.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    /**
     * Cached verified rulepack. Verification happens once per process; a failure is not
     * cached, so a genuine transient failure can be retried by pressing the button.
     */
    @Volatile
    private var verifiedRulepack: Rulepack? = null

    /** Cached verified threat pack. Unavailable states are not cached, so a transient
     *  read failure can recover on the next scan; a bad signature keeps failing closed. */
    @Volatile
    private var verifiedThreatPack: ThreatPackState.Verified? = null

    private val advisoryEnforcer: Enforcer = AdvisoryEnforcer()

    /**
     * Routes a decision to its enforcement path. ACT goes to the Device Owner enforcer
     * only when genuine Device Owner authority is verified *at execution time*; anything
     * else — including an ACT decision on a device that lost authority in between —
     * falls back to the advisory path, which records the situation honestly instead of
     * failing or pretending.
     */
    private fun responderFor(context: Context, decision: SecurityDecision): Enforcer =
        if (decision.decisionType == DecisionType.ACT && SecurityCapability.canEnforce(context)) {
            DeviceOwnerEnforcer(context)
        } else {
            advisoryEnforcer
        }

    /**
     * Runs a complete audit and publishes the result to [state].
     *
     * Serialised by [auditLock]: a launch audit and an impatient button press cannot
     * interleave and produce duplicate findings in the audit chain.
     */
    suspend fun runAudit(context: Context): AuditResult = auditLock.withLock {
        _isScanning.value = true
        try {
            val appContext = context.applicationContext
            val detection = withContext(Dispatchers.IO) { audit(appContext) }
            val result = when (detection) {
                is AuditResult.Completed -> decideAndRespond(appContext, detection)
                else -> detection
            }
            _state.value = result
            publishScanSummary(result)
            result
        } finally {
            _isScanning.value = false
        }
    }

    private fun audit(context: Context): AuditResult {
        val totalStart = System.nanoTime()

        // 1. Verified rulepack, or nothing. RulepackLoader throws on signature failure.
        val rulepack = try {
            verifiedRulepack ?: RulepackLoader(context).load().also { verifiedRulepack = it }
        } catch (error: Throwable) {
            Log.e(TAG, "Rulepack verification failed — refusing to evaluate", error)
            return AuditResult.Failed(
                "Rulepack signature verification failed. Detection is disabled rather " +
                    "than run against unverified rules.",
                "${error::class.java.simpleName}: ${error.message}",
            )
        }

        // 1b. Verified threat pack, or a visibly unavailable pillar. Failure here does
        // not abort the audit — profile detection still runs — but it is carried on the
        // result so an invalid pack can never masquerade as "no threats found".
        val threatState = verifiedThreatPack
            ?: ThreatPackLoader(context).load().also {
                if (it is ThreatPackState.Verified) verifiedThreatPack = it
            }
        val scanner = (threatState as? ThreatPackState.Verified)
            ?.let { ThreatIntelligenceScanner(it.pack) }

        // 2. Inventory only the packages Thraksha can actually see.
        val inventoryStart = System.nanoTime()
        val inventory = AppInventory(context.packageManager)
        val observed: List<ObservedApp> = inventory.inventory(DemoAppRegistry.demoPackages)
        val inventoryMs = (System.nanoTime() - inventoryStart) / 1_000_000

        // 3–4. Per app: profile pillar (baseline + RuleEngine), then threat-intelligence
        // pillar (fingerprint + indicator match), merged into one finding list. An app
        // with no baseline is skipped rather than guessed at. Fingerprints are computed
        // once per app per scan — never re-hashed within a scan.
        val engine = RuleEngine(rulepack)
        val fingerprinter = AppFingerprinter(context.packageManager)
        var ruleEngineMs = 0L
        var certMs = 0L
        var apkHashMs = 0L

        val results = observed.mapNotNull { app ->
            val baseline = AppBaselines.forType(app.appType)
            if (baseline == null) {
                Log.i(TAG, "No baseline for ${app.packageName} (${app.appType}) — skipping")
                return@mapNotNull null
            }

            val ruleStart = System.nanoTime()
            val profileFindings = engine.evaluate(app, baseline)
            ruleEngineMs += (System.nanoTime() - ruleStart) / 1_000_000

            val threatFindings = if (scanner != null) {
                val fingerprint = fingerprinter.fingerprint(app.packageName)
                if (fingerprint != null) {
                    certMs += fingerprint.certDurationMs
                    apkHashMs += fingerprint.apkHashDurationMs
                    scanner.scan(app, fingerprint)
                } else {
                    Log.w(TAG, "No fingerprint for ${app.packageName}; threat-intel skipped")
                    emptyList()
                }
            } else {
                emptyList()
            }

            AppAuditResult(
                packageName = app.packageName,
                appName = app.displayName,
                appType = app.appType,
                findings = profileFindings + threatFindings,
            )
        }

        val timings = ScanTimings(
            inventoryMs = inventoryMs,
            ruleEngineMs = ruleEngineMs,
            certFingerprintMs = certMs,
            apkHashMs = apkHashMs,
            totalMs = (System.nanoTime() - totalStart) / 1_000_000,
        )
        Log.i(TAG, "Scan timings: $timings")

        return AuditResult.Completed(
            results = results,
            rulepackVersion = rulepack.version,
            rulesEvaluated = rulepack.rules.count { it.enabled },
            completedAt = System.currentTimeMillis(),
            threatIntel = when (threatState) {
                is ThreatPackState.Verified -> ThreatIntelStatus(
                    available = true,
                    packVersion = threatState.pack.packVersion,
                    indicatorsEvaluated = threatState.pack.indicators.count {
                        it.enabled && it.type in IndicatorTypes.SUPPORTED
                    },
                    detail = "threat pack v${threatState.pack.packVersion} verified",
                )
                is ThreatPackState.Unavailable -> ThreatIntelStatus(
                    available = false,
                    packVersion = null,
                    indicatorsEvaluated = 0,
                    detail = threatState.reason,
                )
            },
            timings = timings,
        )
    }

    /**
     * 5–6. The policy + response stage (Phase 4).
     *
     * For each audited app, in audit-chain order: emit the ThreatDetected events, ask the
     * pure [PolicyEngine] for a decision under the *current* privilege level and execution
     * mode, emit the DecisionMade event, and route the decision to an enforcer. The
     * Application collector turns every event into hash-chained rows in the encrypted
     * audit trail — this engine never writes to the database itself.
     *
     * Routing: ADVISE runs the [AdvisoryEnforcer]; ACT would run a privileged enforcer,
     * and in this build (none exists) falls back to the advisory path, which records each
     * Device-Owner action as NOT_AUTHORISED rather than pretending it happened; OBSERVE
     * executes nothing, by definition.
     */
    private suspend fun decideAndRespond(
        context: Context,
        detection: AuditResult.Completed,
    ): AuditResult.Completed {
        val responded = detection.results.map { app ->
            app.findings.forEach { finding ->
                SecurityEventBus.emit(
                    SecurityEvent.ThreatDetected(
                        signalId = finding.ruleId,
                        severity = finding.severity,
                        details = finding.summary(),
                        confidence = finding.confidence,
                        packageName = finding.packageName,
                    ),
                )
            }

            // Shared decision path (also used by the live Network Guard): reads the
            // current policy context, invokes the pure PolicyEngine, and emits the
            // DECISION event for findings-bearing decisions. A clean app's OBSERVE
            // decision earns no DECISION row — the SCAN row is its record.
            val decision = SecurityResponseCoordinator.decide(
                context = context,
                packageName = app.packageName,
                appName = app.appName,
                findings = app.findings,
            )

            val enforcement = when (decision.decisionType) {
                DecisionType.OBSERVE -> null
                DecisionType.ADVISE, DecisionType.ACT ->
                    executeSafely(responderFor(context, decision), decision)
            }

            app.copy(decision = decision, enforcement = enforcement)
        }

        return detection.copy(results = responded)
    }

    private suspend fun executeSafely(
        enforcer: Enforcer,
        decision: SecurityDecision,
    ): EnforcementResult? =
        runCatching { enforcer.execute(decision) }
            .onFailure { Log.e(TAG, "Enforcer failed for ${decision.packageName}", it) }
            .getOrNull()

    /**
     * The explicit-approval path for GUIDED mode (guide §5.9): the user pressed
     * "approve" on an advisory shown under Device Owner authority. Executes the already
     * published decision through the Device Owner enforcer and updates [state] with the
     * verified outcome. Refuses (returns null) without genuine Device Owner authority —
     * approval in Advice Mode is meaningless and must do nothing.
     */
    suspend fun approveAndEnforce(context: Context, packageName: String): EnforcementResult? =
        auditLock.withLock {
            val appContext = context.applicationContext
            if (!SecurityCapability.canEnforce(appContext)) return@withLock null
            val current = _state.value as? AuditResult.Completed ?: return@withLock null
            val app = current.results.firstOrNull { it.packageName == packageName }
                ?: return@withLock null
            val decision = app.decision?.takeIf { it.findings.isNotEmpty() }
                ?: return@withLock null

            val result = executeSafely(DeviceOwnerEnforcer(appContext), decision)
            if (result != null) {
                _state.value = current.copy(
                    results = current.results.map {
                        if (it.packageName == packageName) it.copy(enforcement = result) else it
                    },
                )
            }
            result
        }

    /**
     * Reverses demo containment on [packageName] (guide §5.6). Allowlist- and
     * authority-gated inside the enforcer; safe to call repeatedly.
     *
     * On verified restoration the dashboard state returns to [AuditResult.NotRun] — a
     * clean pre-scan slate for the next demo run. Deliberately NOT an automatic
     * re-audit: under AUTO_DEFEND a re-audit would instantly re-contain the still-present
     * threat, so re-scanning after a restore must be the operator's explicit choice.
     * The restore itself is fully recorded in the audit chain either way.
     */
    suspend fun restoreDemoState(context: Context, packageName: String): EnforcementResult? {
        val appContext = context.applicationContext
        val appName = (_state.value as? AuditResult.Completed)
            ?.results?.firstOrNull { it.packageName == packageName }?.appName
            ?: packageName
        val result = runCatching { DeviceOwnerEnforcer(appContext).restore(packageName, appName) }
            .onFailure { Log.e(TAG, "Restore failed for $packageName", it) }
            .getOrNull()
        if (result?.verdict == EnforcementVerdict.ACTED) {
            _state.value = AuditResult.NotRun
        }
        return result
    }

    /** 7. One SCAN row per audit, found-something or not — the audit itself is evidence. */
    private suspend fun publishScanSummary(result: AuditResult) {
        when (result) {
            is AuditResult.Completed -> SecurityEventBus.emit(
                SecurityEvent.ScanCompleted(
                    appsScanned = result.appsScanned,
                    findings = result.threatsFound,
                    summary = buildString {
                        append("Security check: ${result.appsScanned} app(s) audited, ")
                        append("${result.threatsFound} finding(s), ")
                        append("rulepack v${result.rulepackVersion} verified, ")
                        append("${result.rulesEvaluated} rule(s) active, ")
                        append(
                            if (result.threatIntel.available) {
                                "threat pack v${result.threatIntel.packVersion} verified " +
                                    "(${result.threatIntel.indicatorsEvaluated} indicator(s))"
                            } else {
                                "THREAT INTELLIGENCE UNAVAILABLE: ${result.threatIntel.detail}"
                            },
                        )
                    },
                ),
            )

            is AuditResult.Failed -> SecurityEventBus.emit(
                SecurityEvent.ScanCompleted(
                    appsScanned = 0,
                    findings = 0,
                    summary = "Security check could not run: ${result.message}",
                ),
            )

            AuditResult.NotRun -> Unit
        }
    }

    /** Test-only reset so each test starts from a clean engine. */
    internal fun resetForTest() {
        verifiedRulepack = null
        verifiedThreatPack = null
        _state.value = AuditResult.NotRun
        _isScanning.value = false
    }
}
