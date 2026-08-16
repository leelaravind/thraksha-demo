package com.thraksha.guardian.security.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** What happened to the most recent evaluated packet — for the dashboard. */
data class NetworkOutcomeSummary(
    val observation: NetworkObservation,
    val indicatorId: String?,
    val decisionType: String?,
    /** e.g. "FORWARDED", "FORWARD_FAILED", "BLOCKED", "OBSERVED" */
    val outcome: String,
    val detail: String,
)

/**
 * Process-wide, observable Network Guard lifecycle (guide §6). The single source of
 * truth for the UI — never a local Compose boolean. Only [ThrakshaVpnService] (and the
 * consent flow) may transition it; the dashboard just collects.
 */
object NetworkGuard {

    sealed interface State {
        /** Not running. The default, and the state after a clean stop. */
        data object Disabled : State

        /** `VpnService.prepare()` returned a consent intent the user has not accepted. */
        data object ConsentRequired : State

        /** Consent held; the service is bringing the TUN up. */
        data object Starting : State

        /** Tunnel established, scoped to [scopedPackage] only. */
        data class Active(
            val scopedPackage: String,
            val tunnelAddress: String,
            val packetsObserved: Long = 0,
            val packetsForwarded: Long = 0,
            val packetsBlocked: Long = 0,
            val lastOutcome: NetworkOutcomeSummary? = null,
        ) : State

        /** The guard could not run (scoping failure, tunnel failure). Fail closed. */
        data class Error(val reason: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Disabled)
    val state: StateFlow<State> = _state.asStateFlow()

    /**
     * Packages the user has explicitly ordered blocked via ACT (Phase 8.1, guide §14).
     * Consulted by the engine before threat evaluation: a user-blocked package's
     * intercepted packets take the drop path — the same path whose "not forwarded"
     * property is structural (no forwarding socket is ever created on it). Only ever
     * effective for packages already scoped into the tunnel; it widens nothing.
     */
    private val _userBlockedPackages = MutableStateFlow<Set<String>>(emptySet())
    val userBlockedPackages: StateFlow<Set<String>> = _userBlockedPackages.asStateFlow()

    fun setUserBlocked(packageName: String, blocked: Boolean) {
        _userBlockedPackages.update { if (blocked) it + packageName else it - packageName }
    }

    fun isUserBlocked(packageName: String): Boolean =
        packageName in _userBlockedPackages.value

    fun transition(state: State) {
        _state.value = state
    }

    /** Updates Active counters/last-outcome; no-op when not active. */
    fun recordOutcome(
        summary: NetworkOutcomeSummary,
        forwarded: Boolean,
        blocked: Boolean,
    ) {
        _state.update { current ->
            if (current !is State.Active) return@update current
            current.copy(
                packetsObserved = current.packetsObserved + 1,
                packetsForwarded = current.packetsForwarded + if (forwarded) 1 else 0,
                packetsBlocked = current.packetsBlocked + if (blocked) 1 else 0,
                lastOutcome = summary,
            )
        }
    }

    /** Counts a packet that was read but produced no evaluable observation. */
    fun recordIgnored() {
        _state.update { current ->
            if (current !is State.Active) return@update current
            current.copy(packetsObserved = current.packetsObserved + 1)
        }
    }
}
