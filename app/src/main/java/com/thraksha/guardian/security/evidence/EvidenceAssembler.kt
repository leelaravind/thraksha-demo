package com.thraksha.guardian.security.evidence

import com.thraksha.guardian.security.inventory.GrantState
import com.thraksha.guardian.security.inventory.ObservedApp

/**
 * Pure assembly of one app's evidence progression (guide §3, §12).
 *
 * Inputs are what Android genuinely reported: the [ObservedApp] snapshot (declared +
 * granted permissions, declared components, live special-access states) and the genuine
 * runtime observations collected by `RuntimeObservationStore`. Output is one
 * [CapabilityEvidence] row per capability the app declares — plus rows for capabilities
 * with verified observations even when undeclared (an observation is never hidden just
 * because Stage 1 is absent).
 *
 * Honesty invariants (unit-proved):
 *  * Stage 2 derives only from grant flags / live special-access state — never from
 *    Stage 1;
 *  * Stage 3 derives only from VERIFIED observations — never from Stages 1–2;
 *  * capabilities with no usage API carry the NOT OBSERVABLE limitation verbatim;
 *  * an empty row set means "declares none of the catalogued capabilities", not "clean".
 */
object EvidenceAssembler {

    fun assemble(
        app: ObservedApp,
        observations: List<CapabilityObservation>,
    ): List<CapabilityEvidence> {
        val appObservations = observations.filter { it.packageName == app.packageName }
        val rows = mutableListOf<CapabilityEvidence>()

        CapabilityCatalog.PERMISSION_CAPABILITIES.forEach { capability ->
            val declaredPermissions = capability.permissions.filter { app.requests(it) }
            val grantedPermissions = capability.permissions.filter { app.isGranted(it) }
            val hasObservations = appObservations.any { it.capabilityId == capability.id }
            if (declaredPermissions.isEmpty() && !hasObservations) return@forEach

            val declared = declaredPermissions.isNotEmpty()
            val grantState = when {
                !declared -> GrantState.NOT_APPLICABLE
                capability.id == CapabilityCatalog.NETWORK ||
                    capability.id == CapabilityCatalog.PACKAGE_VISIBILITY ->
                    // Install-time permissions: granted iff requested-and-granted flagwise;
                    // Android grants these at install, so DENIED here is a real OS state.
                    if (grantedPermissions.isNotEmpty()) GrantState.GRANTED else GrantState.DENIED
                grantedPermissions.isNotEmpty() -> GrantState.GRANTED
                else -> GrantState.DENIED
            }

            rows += CapabilityEvidence.of(
                capabilityId = capability.id,
                capabilityLabel = capability.label,
                declared = declared,
                declaredEvidence = declaredPermissions.sorted().map { "declares $it" },
                grantState = grantState,
                observations = appObservations,
                observationLimitation = capability.observabilityNote,
                explanation = explanationFor(capability.label, declared, grantState),
            )
        }

        CapabilityCatalog.SPECIAL_CAPABILITIES.forEach { capability ->
            val declared =
                (capability.declaredByCapability?.let { it in app.capabilities } == true) ||
                    (capability.declaredByPermission?.let { app.requests(it) } == true)
            val hasObservations = appObservations.any { it.capabilityId == capability.id }
            if (!declared && !hasObservations) return@forEach

            val grantState = when (capability.kind) {
                CapabilityCatalog.SpecialAccessKind.OVERLAY_ACCESS -> app.specialAccess.overlay
                CapabilityCatalog.SpecialAccessKind.NOTIFICATION_LISTENER_ACCESS ->
                    app.specialAccess.notificationListener
                CapabilityCatalog.SpecialAccessKind.ACCESSIBILITY_ACCESS ->
                    app.specialAccess.accessibility
                CapabilityCatalog.SpecialAccessKind.DEVICE_ADMIN_ACTIVE ->
                    app.specialAccess.deviceAdmin
            }

            rows += CapabilityEvidence.of(
                capabilityId = capability.id,
                capabilityLabel = capability.label,
                declared = declared,
                declaredEvidence = if (declared) listOf(capability.declaredEvidenceText) else emptyList(),
                grantState = grantState,
                observations = appObservations,
                observationLimitation = CapabilityCatalog.NOT_OBSERVABLE,
                explanation = explanationFor(capability.label, declared, grantState),
            )
        }

        // Observation-only capabilities (e.g. notification-posting): genuine Stage 3
        // evidence with no Stage 1/2 counterpart. Never dropped.
        val covered = rows.map { it.capabilityId }.toSet()
        appObservations
            .map { it.capabilityId }
            .distinct()
            .filter { it !in covered }
            .forEach { capabilityId ->
                rows += CapabilityEvidence.of(
                    capabilityId = capabilityId,
                    capabilityLabel = labelForObservationOnly(capabilityId),
                    declared = false,
                    declaredEvidence = emptyList(),
                    grantState = GrantState.NOT_APPLICABLE,
                    observations = appObservations,
                    observationLimitation = CapabilityCatalog.NO_OBSERVATION_YET,
                    explanation = "Runtime behavior observed by Thraksha; this capability " +
                        "has no corresponding manifest declaration stage.",
                )
            }

        return rows.sortedWith(
            compareByDescending<CapabilityEvidence> { it.observed }
                .thenByDescending {
                    it.grantState == GrantState.GRANTED || it.grantState == GrantState.ENABLED
                }
                .thenBy { it.capabilityLabel },
        )
    }

    private fun explanationFor(
        label: String,
        declared: Boolean,
        grantState: GrantState,
    ): String = buildString {
        if (declared) {
            append("$label capability is declared by the app")
        } else {
            append("$label capability is not declared")
        }
        when (grantState) {
            GrantState.GRANTED -> append(" and Android currently grants it")
            GrantState.ENABLED -> append(" and the user currently has it enabled")
            GrantState.DENIED -> append("; Android does not currently grant it")
            GrantState.DISABLED -> append("; the user has not enabled it")
            GrantState.UNKNOWN -> append("; its current grant state could not be determined")
            GrantState.NOT_APPLICABLE -> Unit
        }
        append(". Declaration and grant are capability exposure, not evidence of use.")
    }

    private fun labelForObservationOnly(capabilityId: String): String = when (capabilityId) {
        CapabilityCatalog.NOTIFICATION_POSTING -> "Notification activity"
        else -> capabilityId.replace('-', ' ').replaceFirstChar { it.uppercase() }
    }
}
