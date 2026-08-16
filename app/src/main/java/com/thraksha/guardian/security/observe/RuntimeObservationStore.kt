package com.thraksha.guardian.security.observe

import com.thraksha.guardian.security.evidence.CapabilityObservation
import com.thraksha.guardian.security.evidence.ObservationReliability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Process-wide store of genuine runtime observations (Phase 8.1, guide §7).
 *
 * The ONLY writers are the two genuine observation sources documented in
 * temp/PHASE8_1_RUNTIME_OBSERVABILITY.md:
 *
 *  * the Network Guard engine, for packets actually read from the scoped TUN;
 *  * Thraksha's notification listener, for notifications actually delivered to it.
 *
 * Nothing derived from static metadata may ever be recorded here — recording an
 * observation with a reliability weaker than the source justifies would poison Stage 3,
 * so writers pass their genuine mechanism and reliability explicitly.
 *
 * Bounded (per package and overall) because this is demo-scale runtime state, not a
 * persistence layer; the audit chain remains the durable record.
 */
object RuntimeObservationStore {

    private const val MAX_PER_PACKAGE = 50
    private const val MAX_PACKAGES = 200

    private val _observations =
        MutableStateFlow<Map<String, List<CapabilityObservation>>>(emptyMap())

    /** Live per-package observations, newest first — the dashboard collects this. */
    val observations: StateFlow<Map<String, List<CapabilityObservation>>> =
        _observations.asStateFlow()

    fun record(observation: CapabilityObservation) {
        _observations.update { current ->
            val existing = current[observation.packageName].orEmpty()
            val updated = (listOf(observation) + existing).take(MAX_PER_PACKAGE)
            val next = current + (observation.packageName to updated)
            if (next.size > MAX_PACKAGES) {
                // Evict the package with the oldest most-recent observation.
                val evict = next.minByOrNull { (_, obs) ->
                    obs.firstOrNull()?.observedAt ?: 0L
                }?.key
                if (evict != null && evict != observation.packageName) next - evict else next
            } else {
                next
            }
        }
    }

    /** All observations for one package, newest first. */
    fun forPackage(packageName: String): List<CapabilityObservation> =
        _observations.value[packageName].orEmpty()

    /** Flat snapshot across packages, for the evidence assembler. */
    fun snapshot(): List<CapabilityObservation> =
        _observations.value.values.flatten()

    /** True when at least one VERIFIED observation exists for the package. */
    fun hasVerified(packageName: String): Boolean =
        forPackage(packageName).any { it.reliability == ObservationReliability.VERIFIED }

    /** Test-only reset. */
    internal fun resetForTest() {
        _observations.value = emptyMap()
    }
}
