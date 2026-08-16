package com.thraksha.guardian.security.network

import android.content.Context
import android.util.Log
import com.thraksha.guardian.security.engine.Finding
import com.thraksha.guardian.security.events.SecurityEvent
import com.thraksha.guardian.security.events.SecurityEventBus
import com.thraksha.guardian.security.evidence.CapabilityCatalog
import com.thraksha.guardian.security.evidence.CapabilityObservation
import com.thraksha.guardian.security.evidence.ObservationReliability
import com.thraksha.guardian.security.observe.RuntimeObservationStore
import com.thraksha.guardian.security.policy.ActionOutcome
import com.thraksha.guardian.security.policy.ActionStatus
import com.thraksha.guardian.security.policy.DecisionType
import com.thraksha.guardian.security.policy.SecurityAction
import com.thraksha.guardian.security.policy.SecurityDecision
import com.thraksha.guardian.security.policy.SecurityResponseCoordinator
import com.thraksha.guardian.security.threatintel.ThreatPackLoader
import com.thraksha.guardian.security.threatintel.ThreatPackState

/**
 * Orchestration for one live packet (guide §17): parse → observe → evaluate against the
 * verified signed ThreatPack → shared decision path → instruct the service to FORWARD
 * or DROP → publish the verified outcome. The VpnService stays thin (TUN lifecycle,
 * read loop, protected forwarding, drop execution); this engine owns no sockets.
 *
 * Fail-closed posture (guide §24/§25): an Unavailable ThreatPack means observations are
 * still recorded but **no threat verdict is claimed** — the state carries
 * "threat intelligence unavailable", never "clean". Malformed packets are dropped
 * safely; nothing here throws into the packet loop.
 */
class NetworkGuardEngine(context: Context) {

    private val appContext = context.applicationContext

    /** Loaded once per engine; Verified is cached, Unavailable is re-attempted per start. */
    private val threatState: ThreatPackState = ThreatPackLoader(appContext).load()

    val threatIntelAvailable: Boolean get() = threatState is ThreatPackState.Verified

    val threatIntelDetail: String
        get() = when (threatState) {
            is ThreatPackState.Verified ->
                "threat pack v${threatState.pack.packVersion} verified"
            is ThreatPackState.Unavailable -> threatState.reason
        }

    private val evaluator: NetworkThreatEvaluator? =
        (threatState as? ThreatPackState.Verified)?.let { NetworkThreatEvaluator(it.pack) }

    /** What the service must do with the packet, decided before any forwarding. */
    sealed interface Verdict {
        /** Not an evaluable packet (unsupported/malformed). Drop silently and count. */
        data class Ignore(val reason: String) : Verdict

        /** Forward via the protected socket (observe/advise paths). */
        data class Forward(
            val observation: NetworkObservation,
            val payload: NetworkPacketParser.PayloadWindow,
            val finding: Finding?,
            val decision: SecurityDecision?,
        ) : Verdict

        /** Policy chose ACT/BlockNetwork: do NOT create the forwarding socket. */
        data class Block(
            val observation: NetworkObservation,
            val finding: Finding,
            val decision: SecurityDecision,
        ) : Verdict

        /**
         * The user ordered this package blocked via ACT (Phase 8.1). Same structural
         * guarantee as [Block]: this path never constructs a forwarding socket.
         */
        data class UserBlock(val observation: NetworkObservation) : Verdict
    }

    /**
     * Full evaluation of one TUN packet. Emits the observation (NETWORK row), the
     * THREAT event on an indicator match, and the DECISION event via the shared
     * coordinator. The caller then executes the verdict and reports through
     * [publishForwardOutcome] / [publishBlockOutcome].
     */
    suspend fun evaluate(buffer: ByteArray, length: Int): Verdict {
        val parsed = when (
            val result = NetworkPacketParser.parse(
                buffer = buffer,
                length = length,
                packageName = VpnScope.ALLOWED_PACKAGES.single(),
                timestamp = System.currentTimeMillis(),
            )
        ) {
            is NetworkPacketParser.Result.Malformed -> {
                Log.w(TAG, "Malformed tunnel packet dropped: ${result.reason}")
                return Verdict.Ignore("malformed: ${result.reason}")
            }
            is NetworkPacketParser.Result.Unsupported ->
                return Verdict.Ignore("unsupported: ${result.reason}")
            is NetworkPacketParser.Result.Parsed -> result
        }
        val observation = parsed.observation

        SecurityEventBus.emit(
            SecurityEvent.NetworkAttemptObserved(
                packageName = observation.packageName,
                destinationIp = observation.destinationIp,
                destinationPort = observation.destinationPort,
                protocolName = observation.protocolName,
                packetLength = observation.packetLength,
            ),
        )

        // Phase 8.1 (guide §25): a packet actually read from the scoped TUN is genuine
        // Stage 3 evidence — recorded as VERIFIED because Thraksha itself handled it.
        // Metadata only; the payload never leaves the packet path.
        RuntimeObservationStore.record(
            CapabilityObservation(
                capabilityId = CapabilityCatalog.NETWORK,
                packageName = observation.packageName,
                observedAt = observation.timestamp,
                source = "Network Guard TUN interception",
                reliability = ObservationReliability.VERIFIED,
                detail = "outbound ${observation.protocolName} " +
                    "${observation.packetLength} B → ${observation.endpoint()}",
            ),
        )

        // Phase 8.1 user ACT: an explicit user block outranks per-indicator policy.
        // Checked after the observation is emitted/recorded — blocking never hides
        // the evidence that the attempt happened.
        if (NetworkGuard.isUserBlocked(observation.packageName)) {
            return Verdict.UserBlock(observation)
        }

        val finding = evaluator?.evaluate(observation)
        if (finding == null) {
            // No verdict is claimed when intelligence is unavailable (fail closed) —
            // and a non-matching destination is simply forwarded without ceremony.
            return Verdict.Forward(observation, parsed.payload, finding = null, decision = null)
        }

        SecurityEventBus.emit(
            SecurityEvent.ThreatDetected(
                signalId = finding.ruleId,
                severity = finding.severity,
                details = finding.summary(),
                confidence = finding.confidence,
                packageName = finding.packageName,
            ),
        )

        val decision = SecurityResponseCoordinator.decide(
            context = appContext,
            packageName = finding.packageName,
            appName = finding.appName,
            findings = listOf(finding),
        )

        val blocks = decision.decisionType == DecisionType.ACT &&
            decision.recommendedActions.any { it.action is SecurityAction.BlockNetwork }

        return if (blocks) {
            Verdict.Block(observation, finding, decision)
        } else {
            Verdict.Forward(observation, parsed.payload, finding, decision)
        }
    }

    /**
     * Publishes the outcome of a Forward verdict after the service attempted (or chose
     * not to attempt) the protected send. "Forwarded" claims only that the protected
     * outbound send completed locally — never remote delivery (guide §10).
     */
    suspend fun publishForwardOutcome(verdict: Verdict.Forward, sendSucceeded: Boolean) {
        val outcomeName = if (sendSucceeded) "FORWARDED" else "FORWARD_FAILED"
        val detail = if (sendSucceeded) {
            "Protected outbound send completed; remote delivery is not claimed."
        } else {
            "Protected outbound send failed; the packet was NOT forwarded."
        }

        if (verdict.decision != null && verdict.finding != null) {
            // Advisory outcome for the matched attempt.
            SecurityEventBus.emit(
                SecurityEvent.AdviceIssued(
                    packageName = verdict.observation.packageName,
                    appName = verdict.finding.appName,
                    recommendations = verdict.decision.recommendedActions.map { it.action.label },
                    explanation = "${verdict.decision.explanation} Outcome: $outcomeName — $detail",
                ),
            )
        }
        SecurityEventBus.emit(
            SecurityEvent.ActionTaken(
                action = "Forward outbound attempt (${verdict.observation.protocolName})",
                target = "${verdict.observation.packageName} → ${verdict.observation.endpoint()}",
                result = "$outcomeName: $detail",
            ),
        )
        NetworkGuard.recordOutcome(
            NetworkOutcomeSummary(
                observation = verdict.observation,
                indicatorId = verdict.finding?.ruleId,
                decisionType = verdict.decision?.decisionType?.name,
                outcome = outcomeName,
                detail = detail,
            ),
            forwarded = sendSucceeded,
            blocked = false,
        )
    }

    /**
     * Publishes a verified user-ordered block (Phase 8.1 ACT). Like [publishBlockOutcome],
     * "not forwarded" is a property of the executed code path — the UserBlock branch
     * never creates a forwarding socket.
     */
    suspend fun publishUserBlockOutcome(verdict: Verdict.UserBlock) {
        val detail = "User ACT block: packet to ${verdict.observation.endpoint()} " +
            "intercepted and dropped before forwarding — no forwarding socket was " +
            "created on this path."
        SecurityEventBus.emit(
            SecurityEvent.ActionTaken(
                action = SecurityAction.BlockNetwork.label + " (user ACT)",
                target = "${verdict.observation.packageName} → ${verdict.observation.endpoint()}",
                result = "${ActionStatus.SUCCEEDED}: $detail",
            ),
        )
        NetworkGuard.recordOutcome(
            NetworkOutcomeSummary(
                observation = verdict.observation,
                indicatorId = null,
                decisionType = "USER_ACT",
                outcome = "BLOCKED",
                detail = detail,
            ),
            forwarded = false,
            blocked = true,
        )
    }

    /**
     * Publishes a verified BLOCK_NETWORK outcome. Verification derives from the actual
     * processing branch: the Block verdict path never constructs a forwarding socket,
     * so "not forwarded" is a property of the code path that executed, not an
     * assumption (guide §11).
     */
    suspend fun publishBlockOutcome(verdict: Verdict.Block) {
        val outcome = ActionOutcome(
            action = SecurityAction.BlockNetwork,
            status = ActionStatus.SUCCEEDED,
            detail = "Packet to ${verdict.observation.endpoint()} intercepted and " +
                "intentionally dropped before forwarding — no forwarding socket was " +
                "created on this path.",
        )
        SecurityEventBus.emit(
            SecurityEvent.ActionTaken(
                action = outcome.action.label,
                target = "${verdict.observation.packageName} → ${verdict.observation.endpoint()}",
                result = "${outcome.status}: ${outcome.detail}",
            ),
        )
        NetworkGuard.recordOutcome(
            NetworkOutcomeSummary(
                observation = verdict.observation,
                indicatorId = verdict.finding.ruleId,
                decisionType = verdict.decision.decisionType.name,
                outcome = "BLOCKED",
                detail = outcome.detail,
            ),
            forwarded = false,
            blocked = true,
        )
    }

    private companion object {
        const val TAG = "NetworkGuardEngine"
    }
}
