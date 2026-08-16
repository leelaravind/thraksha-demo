package com.thraksha.guardian.security.evidence

import com.thraksha.guardian.security.inventory.GrantState

/**
 * The Phase 8.1 evidence progression (guide §3): every capability claim an app-facing
 * finding rests on must say which stage it belongs to. The stages never imply one
 * another — DECLARED does not mean GRANTED, and neither means OBSERVED.
 */
enum class EvidenceStage { DECLARED, GRANTED, OBSERVED }

/**
 * How reliable an observation source is (guide §8). Only [VERIFIED] — evidence Thraksha
 * itself handled (an intercepted TUN packet, a notification delivered to its enabled
 * listener) — may render as "OBSERVED ✓". [INDIRECT] heuristics are never called
 * observed; [UNAVAILABLE] means Android exposes nothing at Thraksha's privilege.
 */
enum class ObservationReliability {
    /** Thraksha handled the evidence itself. The only grade that renders OBSERVED ✓. */
    VERIFIED,

    /** Attributed, system-recorded history (e.g. UsageStats). Not shipped in 8.1. */
    RECENT_SYSTEM_SIGNAL,

    /** Unattributed or inferred signal. Never rendered as observed. */
    INDIRECT,

    /** No legitimate source exists at Thraksha's privilege. */
    UNAVAILABLE,
}

/**
 * One genuine runtime observation: something Thraksha can prove happened, with the
 * source that proves it. Metadata only — never content (no payloads, no notification
 * text).
 */
data class CapabilityObservation(
    /** Which capability this observation evidences (a [CapabilityCatalog] id). */
    val capabilityId: String,
    val packageName: String,
    val observedAt: Long,
    /** The concrete mechanism, e.g. "Network Guard TUN interception". */
    val source: String,
    val reliability: ObservationReliability,
    /** Human-readable metadata, e.g. "outbound UDP 32 B → 203.0.113.113:443". */
    val detail: String,
)

/**
 * The complete evidence progression for one capability of one app (guide §3).
 *
 * Construction runs through [of], which enforces the honesty invariants structurally:
 * [observed] can only be true when a VERIFIED observation exists — it can never be
 * derived from [declared] or [grantState].
 */
data class CapabilityEvidence(
    val capabilityId: String,
    val capabilityLabel: String,

    /** Stage 1 — the app declares this capability (manifest metadata). */
    val declared: Boolean,
    /** The concrete declarations Stage 1 rests on (permission strings, component kinds). */
    val declaredEvidence: List<String>,

    /** Stage 2 — what Android currently allows. [GrantState.NOT_APPLICABLE] when undefined. */
    val grantState: GrantState,

    /** Stage 3 — true only when a VERIFIED observation exists. Never inferred. */
    val observed: Boolean,
    /** The most recent observation, when one exists. */
    val latestObservation: CapabilityObservation?,
    val observationCount: Int,

    /**
     * Why Stage 3 is empty when it is: either "no verified observation available" (a
     * source exists but saw nothing) or the NOT OBSERVABLE limitation for capabilities
     * Android exposes no usage signal for (research doc §2). Null when observed.
     */
    val observationLimitation: String?,

    /** One-sentence explanation of what this row does and does not claim. */
    val explanation: String,
) {
    /** The strongest stage this evidence actually reaches. */
    fun highestStage(): EvidenceStage = when {
        observed -> EvidenceStage.OBSERVED
        grantState == GrantState.GRANTED || grantState == GrantState.ENABLED ->
            EvidenceStage.GRANTED
        else -> EvidenceStage.DECLARED
    }

    companion object {
        /**
         * The only constructor used by the pipeline. Enforces:
         *  * observed ⇔ a VERIFIED observation is present (guide §6: never inferred,
         *    never synthesized, weaker reliabilities never claim the stage);
         *  * an unobserved row always carries a limitation string.
         */
        fun of(
            capabilityId: String,
            capabilityLabel: String,
            declared: Boolean,
            declaredEvidence: List<String>,
            grantState: GrantState,
            observations: List<CapabilityObservation>,
            observationLimitation: String,
            explanation: String,
        ): CapabilityEvidence {
            val verified = observations
                .filter {
                    it.capabilityId == capabilityId &&
                        it.reliability == ObservationReliability.VERIFIED
                }
                .sortedByDescending { it.observedAt }
            val observed = verified.isNotEmpty()
            return CapabilityEvidence(
                capabilityId = capabilityId,
                capabilityLabel = capabilityLabel,
                declared = declared,
                declaredEvidence = declaredEvidence,
                grantState = grantState,
                observed = observed,
                latestObservation = verified.firstOrNull(),
                observationCount = verified.size,
                observationLimitation = if (observed) null else observationLimitation,
                explanation = explanation,
            )
        }
    }
}

/** Deterministic context verdict for one app's assembled evidence (guide §9). */
enum class ContextAssessment { EXPECTED, UNUSUAL, SUSPICIOUS, UNKNOWN }

/** The context verdict plus the deterministic reasoning that produced it. */
data class AppContextAssessment(
    val assessment: ContextAssessment,
    val rationale: String,
)
