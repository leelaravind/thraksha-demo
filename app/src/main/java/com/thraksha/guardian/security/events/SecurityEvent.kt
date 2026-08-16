package com.thraksha.guardian.security.events

/** Severity of a security signal. */
enum class Severity { LOW, MEDIUM, HIGH, CRITICAL }

/**
 * Events carried on the internal pub/sub bus so detection, remediation and UI stay
 * decoupled. Extensible: new subtypes can be added without touching existing producers
 * or consumers.
 */
sealed interface SecurityEvent {

    /**
     * A detector fired. [confidence] is 0..100; [severity] is the rule's declared severity.
     *
     * [packageName] identifies the app the finding is about. It defaults to null so
     * non-app-scoped detectors (and the existing tests) are unaffected, and it exists so
     * the dashboard and the audit trail can attribute a finding without parsing
     * [details].
     */
    data class ThreatDetected(
        val signalId: String,
        val severity: Severity,
        val details: String,
        val confidence: Int,
        val packageName: String? = null,
    ) : SecurityEvent

    /**
     * A security audit finished. Recorded whether or not anything was found — an audit
     * that produced no findings is itself evidence, and the absence of a THREAT row must
     * not be the only proof that a scan ran.
     */
    data class ScanCompleted(
        val appsScanned: Int,
        val findings: Int,
        val summary: String,
    ) : SecurityEvent

    /**
     * A generalized device scan began (Phase 8). Recorded so the audit chain shows the
     * scan's start as well as its outcome — an interrupted scan leaves evidence.
     */
    data class DeviceScanStarted(
        val summary: String,
    ) : SecurityEvent

    /** A remediation/automation action ran. */
    data class ActionTaken(
        val action: String,
        val target: String,
        val result: String,
    ) : SecurityEvent

    /**
     * The policy layer reached a decision about one app's findings. Emitted so the
     * decision itself — not just the findings and any later action — is traceable in the
     * audit chain (`SCAN → THREAT → DECISION → ADVISED/ACTION`).
     *
     * [maxRuleConfidence] is the strongest authored rule weight among the findings, not a
     * probability.
     */
    data class DecisionMade(
        val packageName: String,
        val appName: String,
        val decisionType: String,
        val highestSeverity: Severity,
        val findingCount: Int,
        val maxRuleConfidence: Int,
        val explanation: String,
    ) : SecurityEvent

    /**
     * The advisory enforcer issued recommendations for one app. This is a *successful*
     * outcome of Advice Mode, distinct from [ActionTaken]: no automatic OS action
     * occurred, and the audit row type ("ADVISED") says so.
     */
    data class AdviceIssued(
        val packageName: String,
        val appName: String,
        val recommendations: List<String>,
        val explanation: String,
    ) : SecurityEvent

    /** The app's privilege level changed. */
    data class ModeChanged(
        val level: String,
    ) : SecurityEvent

    /**
     * A deterministic automation lifecycle event (Phase 9, guide §32/§33). Automation
     * shares the ONE tamper-evident audit history with security, distinguishable by the
     * AUTOMATION row type — never a separate untrusted log. [stage] examples:
     * REQUESTED, PLANNED, STARTED, ACTION_VERIFIED, ACTION_FAILED, ACTIVE,
     * RESTORE_REQUESTED, RESTORED, RESTORE_FAILED, RECOVERED.
     */
    data class AutomationEvent(
        val stage: String,
        val routine: String,
        val detail: String,
    ) : SecurityEvent

    /**
     * The user exercised (or declined) an ACT option for one app (Phase 8.1, guide §14).
     * [status] is the verified outcome vocabulary — ACTED is only ever carried after the
     * underlying enforcer verified real Android state; a user cancel or an
     * settings-hand-off is recorded as exactly what it was.
     */
    data class UserActionPerformed(
        val packageName: String,
        val appName: String,
        val actionLabel: String,
        val status: String,
        val detail: String,
    ) : SecurityEvent

    /**
     * The Network Guard observed a live outbound attempt from the scoped app (Phase 7).
     * Metadata only — destination, protocol, size. Payload contents are never carried
     * on events or persisted.
     */
    data class NetworkAttemptObserved(
        val packageName: String,
        val destinationIp: String,
        val destinationPort: Int,
        val protocolName: String,
        val packetLength: Int,
    ) : SecurityEvent
}
