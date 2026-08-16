package com.thraksha.guardian.security.scan

import android.content.Context
import android.util.Log
import com.thraksha.guardian.security.SecurityCapability
import com.thraksha.guardian.security.baseline.AppBaselines
import com.thraksha.guardian.security.enforcement.DeviceOwnerEnforcer
import com.thraksha.guardian.security.enforcement.EnforcementTargets
import com.thraksha.guardian.security.engine.Finding
import com.thraksha.guardian.security.engine.RuleEngine
import com.thraksha.guardian.security.events.SecurityEvent
import com.thraksha.guardian.security.events.SecurityEventBus
import com.thraksha.guardian.security.evidence.ContextEvaluator
import com.thraksha.guardian.security.evidence.EvidenceAssembler
import com.thraksha.guardian.security.evidence.StatusMapper
import com.thraksha.guardian.security.inventory.AppInventory
import com.thraksha.guardian.security.inventory.ObservedApp
import com.thraksha.guardian.security.observe.RuntimeObservationProvider
import com.thraksha.guardian.security.observe.RuntimeObservationStore
import com.thraksha.guardian.security.policy.AdvisoryEnforcer
import com.thraksha.guardian.security.policy.DecisionType
import com.thraksha.guardian.security.policy.EnforcementResult
import com.thraksha.guardian.security.policy.Enforcer
import com.thraksha.guardian.security.policy.SecurityDecision
import com.thraksha.guardian.security.policy.SecurityResponseCoordinator
import com.thraksha.guardian.security.rulepack.Rulepack
import com.thraksha.guardian.security.rulepack.RulepackLoader
import com.thraksha.guardian.security.threatintel.AppFingerprinter
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
import kotlinx.coroutines.yield

/**
 * SCAN THIS DEVICE — the Phase 8 generalized real-device scanner (guide §18).
 *
 * One orchestration entry point, [scanDevice], that runs the SAME pipeline for every
 * visible installed application, whatever it is called and whenever it was installed:
 *
 *   enumerate → observe metadata → fingerprint (signer certs + base APK SHA-256) →
 *   generic static rules (user apps) → contextual baseline rules (only where a
 *   deterministic baseline exists) → verified signed ThreatPack matching →
 *   classify (honest vocabulary) → policy for threat-tier apps → events → audit.
 *
 * Design guarantees, enforced structurally:
 *
 *  * **No hardcoded verdicts** — nothing in this engine (or anything it calls to reach a
 *    classification) branches on a package name. The controlled VillainCaller sample is
 *    detected the same way any real threat would be: because signed intelligence and
 *    signed contextual rules match its observable evidence.
 *  * **Failure isolation** — a package that cannot be observed or fingerprinted degrades
 *    to a per-app PARTIAL (or a failed-package entry), never aborts the scan.
 *  * **Conservative response for real apps** (guide §27) — automatic privileged action
 *    is doubly restricted: this engine routes ACT decisions to the Device Owner enforcer
 *    only for packages in the [EnforcementTargets] demo allowlist, and the enforcer
 *    itself refuses out-of-allowlist targets before any authority check. Every real-world
 *    finding path is detect → explain → advise → audit.
 *  * **Missing evidence is never clean** — an unavailable or stale ThreatPack, or a
 *    fingerprint failure, yields PARTIAL, visibly.
 *  * **UI responsiveness** — all PackageManager and hashing work runs on Dispatchers.IO
 *    with cooperative yields; progress is a StateFlow the dashboard renders live.
 */
object DeviceScanEngine {

    private const val TAG = "DeviceScanEngine"

    private val scanLock = Mutex()

    private val _progress = MutableStateFlow<ScanProgress>(ScanProgress.Idle)
    val progress: StateFlow<ScanProgress> = _progress.asStateFlow()

    private val _lastResult = MutableStateFlow<DeviceScanResult?>(null)
    val lastResult: StateFlow<DeviceScanResult?> = _lastResult.asStateFlow()

    @Volatile
    private var cancelRequested = false

    /** Requests cooperative cancellation of the in-flight scan (result becomes PARTIAL). */
    fun requestCancel() {
        cancelRequested = true
    }

    suspend fun scanDevice(context: Context): DeviceScanResult = scanLock.withLock {
        cancelRequested = false
        val appContext = context.applicationContext
        val result = withContext(Dispatchers.IO) {
            runCatching { runScan(appContext) }.getOrElse { error ->
                Log.e(TAG, "Device scan failed", error)
                errorResult(error)
            }
        }
        _lastResult.value = result
        _progress.value = ScanProgress.Finished(result.outcome)
        publishSummary(result)
        // Phase 8.1 (guide §20/§21): quiet, thresholded notifications — never one per
        // declared capability, at most one review summary per changed review set, and a
        // high-priority alert only for verified known-threat matches.
        runCatching {
            com.thraksha.guardian.security.notify.SecurityNotifier
                .notifyScanResult(appContext, result)
        }.onFailure { Log.w(TAG, "Security notification failed", it) }
        result
    }

    private suspend fun runScan(context: Context): DeviceScanResult {
        val startedAt = System.currentTimeMillis()
        val totalStart = System.nanoTime()
        _progress.value = ScanProgress.Discovering

        SecurityEventBus.emit(
            SecurityEvent.DeviceScanStarted(
                summary = "Device scan started (generalized scanner; " +
                    "results derive from installed-app evidence)",
            ),
        )

        // Signed artefacts. The rulepack drives only the contextual (baseline) pillar;
        // its failure disables that pillar visibly without killing the generic scan.
        val rulepack: Rulepack? = runCatching { RulepackLoader(context).load() }
            .onFailure { Log.e(TAG, "Rulepack unavailable for contextual pillar", it) }
            .getOrNull()

        val threatState = ThreatPackLoader(context).load()
        val nowMillis = startedAt
        val (scanner, intelligence) = when (threatState) {
            is ThreatPackState.Verified -> {
                val pack = threatState.pack
                if (pack.isStaleAt(nowMillis)) {
                    // A stale pack must never quietly keep matching or quietly stop.
                    null to IntelligenceState(
                        status = IntelligenceState.Status.STALE,
                        packVersion = pack.packVersion,
                        activeIndicators = 0,
                        expiredIndicators = pack.indicators.size,
                        detail = "THREAT INTELLIGENCE STALE — pack v${pack.packVersion} " +
                            "expired at ${pack.expiresAt}; matching disabled",
                    )
                } else {
                    val s = ThreatIntelligenceScanner(pack, nowMillis)
                    s to IntelligenceState(
                        status = IntelligenceState.Status.ACTIVE,
                        packVersion = pack.packVersion,
                        activeIndicators = s.activeIndicators,
                        expiredIndicators = s.expiredIndicators,
                        detail = "threat pack v${pack.packVersion} verified — " +
                            "${s.activeIndicators} active indicator(s), " +
                            "${s.expiredIndicators} expired (retained for audit)",
                    )
                }
            }

            is ThreatPackState.Unavailable -> null to IntelligenceState(
                status = IntelligenceState.Status.UNAVAILABLE,
                packVersion = null,
                activeIndicators = 0,
                expiredIndicators = 0,
                detail = threatState.reason,
            )
        }

        // 1. Enumerate everything the OS makes visible.
        val inventoryStart = System.nanoTime()
        val inventory = AppInventory(context.packageManager)
        val discovered = inventory.inventoryDevice()
        val inventoryMs = (System.nanoTime() - inventoryStart) / 1_000_000

        val ruleEngine = rulepack?.let { RuleEngine(it) }
        val fingerprinter = AppFingerprinter(context.packageManager)
        // Phase 8.1: one provider per scan — special-access settings/DPM state is read
        // once and shared across all apps; per-app cost is the AppOps overlay query.
        val observationProvider = RuntimeObservationProvider(context)

        var certMs = 0L
        var apkHashMs = 0L
        var genericMs = 0L
        var contextualMs = 0L
        var intelMs = 0L
        var evidenceMs = 0L
        var certificatesChecked = 0
        var apkHashesComputed = 0
        var cancelled = false

        // 2. Analyze each app through the one shared pipeline. Fingerprints are computed
        // exactly once per app per scan (guide §18 — no repeat hashing).
        val records = mutableListOf<AppScanRecord>()
        val apps = discovered.apps
        for ((index, app) in apps.withIndex()) {
            if (cancelRequested) {
                cancelled = true
                break
            }
            _progress.value = ScanProgress.Analyzing(
                analyzed = index,
                total = apps.size,
                currentPackage = app.packageName,
            )
            yield()

            val timed = runCatching {
                // Phase 8.1: attach the live special-access snapshot before any rule
                // sees the app, so grant-state evidence and rules share one source.
                val evidenceStart = System.nanoTime()
                val enriched = app.copy(
                    specialAccess = observationProvider.specialAccessFor(app),
                )
                evidenceMs += (System.nanoTime() - evidenceStart) / 1_000_000
                analyzeApp(enriched, fingerprinter, ruleEngine, scanner, intelligence)
            }.getOrElse { error ->
                // One inexplicable app must not kill the scan: honest per-app PARTIAL.
                Log.w(TAG, "Analysis failed for ${app.packageName}", error)
                TimedRecord(
                    record = AppScanRecord(
                        packageName = app.packageName,
                        displayName = app.displayName,
                        appType = app.appType,
                        isSystemApp = app.isSystemApp,
                        isEnabled = app.isEnabled,
                        versionName = app.versionName,
                        installerPackage = app.installerPackage,
                        certSha256 = emptySet(),
                        baseApkSha256 = null,
                        findings = emptyList(),
                        classification = ScanClassification.PARTIAL,
                        evidenceComplete = false,
                    ),
                )
            }

            if (timed.record.certSha256.isNotEmpty()) certificatesChecked++
            if (timed.record.baseApkSha256 != null) apkHashesComputed++
            certMs += timed.certDurationMs
            apkHashMs += timed.apkDurationMs
            genericMs += timed.genericMs
            contextualMs += timed.contextualMs
            intelMs += timed.intelMs
            evidenceMs += timed.evidenceMs
            records += timed.record
        }

        // 3. Policy + response for threat-tier apps only (guide §27): REVIEW never
        // reaches the policy layer, and ACT is routed to privileged enforcement only
        // for the controlled demo allowlist.
        val decidedRecords = decideForThreatTier(context, records)

        val timings = DeviceScanTimings(
            inventoryMs = inventoryMs,
            certFingerprintMs = certMs,
            apkHashMs = apkHashMs,
            genericRulesMs = genericMs,
            contextualRulesMs = contextualMs,
            threatIntelMs = intelMs,
            totalMs = (System.nanoTime() - totalStart) / 1_000_000,
            evidenceMs = evidenceMs,
        )

        val partialReasons = buildList {
            if (cancelled) add("scan cancelled by user before completion")
            if (discovered.failedPackages.isNotEmpty()) {
                add("${discovered.failedPackages.size} package(s) could not be observed")
            }
            if (intelligence.status != IntelligenceState.Status.ACTIVE) add(intelligence.detail)
        }

        val result = DeviceScanResult(
            startedAt = startedAt,
            completedAt = System.currentTimeMillis(),
            visibilityScope = discovered.visibilityScope,
            outcome = if (partialReasons.isEmpty()) ScanOutcome.COMPLETE else ScanOutcome.PARTIAL,
            outcomeDetail = partialReasons.joinToString("; ").ifEmpty { null },
            totalDiscovered = apps.size,
            userAppCount = discovered.userApps.size,
            systemAppCount = discovered.systemApps.size,
            analyzedCount = decidedRecords.size,
            failedPackages = discovered.failedPackages,
            certificatesChecked = certificatesChecked,
            apkHashesComputed = apkHashesComputed,
            intelligence = intelligence,
            apps = decidedRecords.sortedWith(
                compareBy({ it.classification != ScanClassification.KNOWN_THREAT_MATCH },
                    { it.classification != ScanClassification.HIGH_RISK_PROFILE },
                    { it.classification != ScanClassification.REVIEW },
                    { it.packageName }),
            ),
            timings = timings,
        )
        Log.i(TAG, "Device scan finished: ${result.analyzedCount} apps, timings=$timings")
        return result
    }

    /** An [AppScanRecord] plus the per-stage durations its analysis cost. */
    private data class TimedRecord(
        val record: AppScanRecord,
        val certDurationMs: Long = 0,
        val apkDurationMs: Long = 0,
        val genericMs: Long = 0,
        val contextualMs: Long = 0,
        val intelMs: Long = 0,
        val evidenceMs: Long = 0,
    )

    private fun analyzeApp(
        app: ObservedApp,
        fingerprinter: AppFingerprinter,
        ruleEngine: RuleEngine?,
        scanner: ThreatIntelligenceScanner?,
        intelligence: IntelligenceState,
    ): TimedRecord {
        // Fingerprint once. Failure downgrades evidence, never crashes the app's record.
        val fingerprint = fingerprinter.fingerprint(app.packageName)

        val findings = mutableListOf<Finding>()

        // Generic static rules: user-installable software only (see GenericRiskRules KDoc).
        val genericStart = System.nanoTime()
        if (!app.isSystemApp) {
            findings += GenericRiskRules.evaluate(app)
        }
        val genericMs = (System.nanoTime() - genericStart) / 1_000_000

        // Contextual baseline rules: ONLY where a deterministic baseline exists for the
        // app's registered type. Unknown apps are never forced into a baseline (§8).
        val contextualStart = System.nanoTime()
        val baseline = AppBaselines.forType(app.appType)
        if (baseline != null && ruleEngine != null) {
            findings += ruleEngine.evaluate(app, baseline)
        }
        val contextualMs = (System.nanoTime() - contextualStart) / 1_000_000

        // Signed threat-intelligence matching, all apps, when the pack is usable.
        val intelStart = System.nanoTime()
        if (scanner != null && fingerprint != null) {
            findings += scanner.scan(app, fingerprint)
        }
        val intelMs = (System.nanoTime() - intelStart) / 1_000_000

        val evidenceComplete = fingerprint != null &&
            fingerprint.certSha256.isNotEmpty() &&
            fingerprint.baseApkSha256 != null

        val classification = ScanClassifier.classify(
            findings = findings,
            evidenceComplete = evidenceComplete,
            intelligenceStatus = intelligence.status,
        )

        // Phase 8.1: assemble the DECLARED/GRANTED/OBSERVED progression from the same
        // snapshot the rules just evaluated plus the genuine runtime-observation store.
        // Stage 3 comes only from the store — nothing here infers it from stages 1–2.
        val evidenceStart = System.nanoTime()
        val capabilityEvidence = EvidenceAssembler.assemble(
            app = app,
            observations = RuntimeObservationStore.forPackage(app.packageName),
        )
        val contextAssessment = ContextEvaluator.evaluate(
            appType = app.appType,
            isSystemApp = app.isSystemApp,
            evidence = capabilityEvidence,
            findings = findings,
        )
        val displayStatus = StatusMapper.map(
            classification = classification,
            findings = findings,
            evidence = capabilityEvidence,
            context = contextAssessment,
        )
        val evidenceAssemblyMs = (System.nanoTime() - evidenceStart) / 1_000_000

        return TimedRecord(
            record = AppScanRecord(
                packageName = app.packageName,
                displayName = app.displayName,
                appType = app.appType,
                isSystemApp = app.isSystemApp,
                isEnabled = app.isEnabled,
                versionName = app.versionName,
                installerPackage = app.installerPackage,
                certSha256 = fingerprint?.certSha256.orEmpty(),
                baseApkSha256 = fingerprint?.baseApkSha256,
                findings = findings.sortedBy { it.ruleId },
                classification = classification,
                evidenceComplete = evidenceComplete,
                evidence = capabilityEvidence,
                contextAssessment = contextAssessment,
                displayStatus = displayStatus,
            ),
            certDurationMs = fingerprint?.certDurationMs ?: 0,
            apkDurationMs = fingerprint?.apkHashDurationMs ?: 0,
            genericMs = genericMs,
            contextualMs = contextualMs,
            intelMs = intelMs,
            evidenceMs = evidenceAssemblyMs,
        )
    }

    /**
     * Threat-tier apps (KNOWN_THREAT_MATCH / HIGH_RISK_PROFILE) go through the shared
     * policy path: THREAT events, one DECISION, and a routed response. Everything else
     * is recorded in the scan result and the completion summary only — a REVIEW is a
     * review, not a threat, and must not fill the audit chain with THREAT rows (§28).
     */
    private suspend fun decideForThreatTier(
        context: Context,
        records: List<AppScanRecord>,
    ): List<AppScanRecord> = records.map { record ->
        val threatTier = record.classification == ScanClassification.KNOWN_THREAT_MATCH ||
            record.classification == ScanClassification.HIGH_RISK_PROFILE
        if (!threatTier) return@map record

        record.findings.forEach { finding ->
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

        val decision = SecurityResponseCoordinator.decide(
            context = context,
            packageName = record.packageName,
            appName = record.displayName,
            findings = record.findings,
        )

        val enforcement = when (decision.decisionType) {
            DecisionType.OBSERVE -> null
            DecisionType.ADVISE, DecisionType.ACT ->
                executeSafely(responderFor(context, decision), decision)
        }

        record.copy(decision = decision, enforcement = enforcement)
    }

    /**
     * ACT reaches the Device Owner enforcer only with live Device Owner authority AND a
     * target inside the controlled demo allowlist. An ACT decision against any real app
     * is executed as advice — the Phase 8 safety rule that an immature rule can never
     * disable someone's real application (§27).
     */
    private fun responderFor(context: Context, decision: SecurityDecision): Enforcer =
        if (decision.decisionType == DecisionType.ACT &&
            SecurityCapability.canEnforce(context) &&
            EnforcementTargets.isEnforceable(decision.packageName)
        ) {
            DeviceOwnerEnforcer(context)
        } else {
            AdvisoryEnforcer()
        }

    private suspend fun executeSafely(
        enforcer: Enforcer,
        decision: SecurityDecision,
    ): EnforcementResult? =
        runCatching { enforcer.execute(decision) }
            .onFailure { Log.e(TAG, "Enforcer failed for ${decision.packageName}", it) }
            .getOrNull()

    /** One SCAN summary row per device scan — honest counts, straight from the result. */
    private suspend fun publishSummary(result: DeviceScanResult) {
        val reviewList = result.reviewApps.take(5).joinToString { it.packageName }
        SecurityEventBus.emit(
            SecurityEvent.ScanCompleted(
                appsScanned = result.analyzedCount,
                findings = result.totalFindings,
                summary = buildString {
                    append("Device scan ${result.outcome}: ")
                    append("${result.analyzedCount} app(s) analyzed ")
                    append("(${result.userAppCount} user / ${result.systemAppCount} system), ")
                    append("${result.certificatesChecked} certificate(s) checked, ")
                    append("${result.apkHashesComputed} APK fingerprint(s), ")
                    append("${result.knownThreatApps.size} known-threat match(es), ")
                    append("${result.highRiskApps.size} high-risk profile(s), ")
                    append("${result.reviewApps.size} app(s) for review")
                    if (reviewList.isNotEmpty()) append(" [$reviewList]")
                    append(". Intelligence: ${result.intelligence.detail}.")
                    append(" Visibility: ${result.visibilityScope}.")
                    result.outcomeDetail?.let { append(" Note: $it") }
                },
            ),
        )
    }

    private fun errorResult(error: Throwable): DeviceScanResult {
        val now = System.currentTimeMillis()
        return DeviceScanResult(
            startedAt = now,
            completedAt = now,
            visibilityScope = com.thraksha.guardian.security.inventory.VisibilityScope.REDUCED,
            outcome = ScanOutcome.ERROR,
            outcomeDetail = "Scan failed: ${error::class.java.simpleName}: ${error.message}",
            totalDiscovered = 0,
            userAppCount = 0,
            systemAppCount = 0,
            analyzedCount = 0,
            failedPackages = emptyList(),
            certificatesChecked = 0,
            apkHashesComputed = 0,
            intelligence = IntelligenceState(
                status = IntelligenceState.Status.UNAVAILABLE,
                packVersion = null,
                activeIndicators = 0,
                expiredIndicators = 0,
                detail = "scan failed before intelligence could be evaluated",
            ),
            apps = emptyList(),
            timings = DeviceScanTimings(0, 0, 0, 0, 0, 0, 0),
        )
    }

    /** Test-only reset. */
    internal fun resetForTest() {
        _progress.value = ScanProgress.Idle
        _lastResult.value = null
        cancelRequested = false
    }
}
