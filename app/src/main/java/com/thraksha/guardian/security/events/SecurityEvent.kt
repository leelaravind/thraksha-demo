package com.thraksha.guardian.security.events

/** Severity of a security signal. */
enum class Severity { LOW, MEDIUM, HIGH, CRITICAL }

/**
 * Events carried on the internal pub/sub bus so detection, remediation and UI stay
 * decoupled. Extensible: new subtypes can be added without touching existing producers
 * or consumers.
 */
sealed interface SecurityEvent {

    /** A detector fired. [confidence] is 0..100; [severity] is the rule's declared severity. */
    data class ThreatDetected(
        val signalId: String,
        val severity: Severity,
        val details: String,
        val confidence: Int,
    ) : SecurityEvent

    /** A remediation/automation action ran. */
    data class ActionTaken(
        val action: String,
        val target: String,
        val result: String,
    ) : SecurityEvent

    /** The app's privilege level changed. */
    data class ModeChanged(
        val level: String,
    ) : SecurityEvent
}
