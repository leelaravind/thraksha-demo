package com.thraksha.guardian.data.config

/**
 * How aggressively the guardian may act on a detection.
 * The gating logic that consumes this is Phase 4 — Phase 2 only stores/reads the value.
 */
enum class ExecutionMode {
    /** Detect + record only, never act. Default. */
    OBSERVE,

    /** Detect + prompt the user before acting. */
    GUIDED,

    /** Detect + auto-remediate up to the configured tier. */
    AUTO_DEFEND,

    /** Maximum containment. */
    LOCKDOWN,
}
