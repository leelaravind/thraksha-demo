package com.thraksha.guardian.ai

/**
 * Phase 10A model lifecycle (guide §7, PREP offline doc §1).
 *
 * The UI observes this and nothing else — it must never infer readiness from "the file
 * exists". [ModelPhase.LOADED] is the ONLY phase in which Thraksha may claim on-device AI
 * is ready, and reaching it requires BOTH a passing SHA-256 gate AND a successful
 * LiteRT-LM initialization.
 *
 * Every phase leaves the Phase 9 deterministic automation fully usable. AI is an
 * additive input method, never a dependency (guide §31).
 */
enum class ModelPhase {
    /** The pinned model file is not at the authoritative path. */
    MISSING,

    /** Size / container magic / SHA-256 being checked. */
    VERIFYING,

    /** Integrity passed. Not yet loaded into memory. */
    READY,

    /** `Engine.initialize()` running off the main thread. */
    LOADING,

    /** Runtime initialized and reusable. The only "AI ready" phase. */
    LOADED,

    /** Integrity failed, init failed, OOM, or the native library refused to load. */
    ERROR,

    /** This device/ABI cannot run the runtime at all. */
    UNSUPPORTED,
}

/**
 * @param detail human-readable, app-authored cause. Never model output.
 * @param verifyMillis duration of the last integrity gate.
 * @param loadMillis duration of the last successful [ModelPhase.LOADED] transition.
 */
data class ModelState(
    val phase: ModelPhase = ModelPhase.MISSING,
    val detail: String = "",
    val verifyMillis: Long? = null,
    val loadMillis: Long? = null,
) {
    /** The single predicate the UI may use before offering natural-language input. */
    val isUsable: Boolean get() = phase == ModelPhase.LOADED

    /** Honest short label for the dashboard (guide §25). */
    val label: String
        get() = when (phase) {
            ModelPhase.MISSING -> "ON-DEVICE AI MODEL NOT INSTALLED"
            ModelPhase.VERIFYING -> "CHECKING ON-DEVICE AI MODEL…"
            ModelPhase.READY -> "ON-DEVICE AI AVAILABLE"
            ModelPhase.LOADING -> "LOADING ON-DEVICE AI…"
            ModelPhase.LOADED -> "ON-DEVICE AI READY"
            ModelPhase.ERROR -> "ON-DEVICE AI UNAVAILABLE"
            ModelPhase.UNSUPPORTED -> "ON-DEVICE AI NOT SUPPORTED ON THIS DEVICE"
        }
}
