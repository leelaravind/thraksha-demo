package com.thraksha.guardian

import com.thraksha.guardian.security.PrivilegeLevel
import com.thraksha.guardian.security.act.SupportedUserActions
import com.thraksha.guardian.security.act.UserActOption
import com.thraksha.guardian.security.act.UserActOutcomeMapper
import com.thraksha.guardian.security.act.UserActStatus
import com.thraksha.guardian.security.engine.Finding
import com.thraksha.guardian.security.engine.FindingSource
import com.thraksha.guardian.security.events.Severity
import com.thraksha.guardian.security.evidence.AppContextAssessment
import com.thraksha.guardian.security.evidence.AppDisplayStatus
import com.thraksha.guardian.security.evidence.CapabilityCatalog
import com.thraksha.guardian.security.evidence.CapabilityEvidence
import com.thraksha.guardian.security.evidence.CapabilityObservation
import com.thraksha.guardian.security.evidence.ContextAssessment
import com.thraksha.guardian.security.evidence.ContextEvaluator
import com.thraksha.guardian.security.evidence.EvidenceAssembler
import com.thraksha.guardian.security.evidence.EvidenceStage
import com.thraksha.guardian.security.evidence.ObservationReliability
import com.thraksha.guardian.security.evidence.StatusMapper
import com.thraksha.guardian.security.inventory.AppType
import com.thraksha.guardian.security.inventory.Capability
import com.thraksha.guardian.security.inventory.ExportedComponents
import com.thraksha.guardian.security.inventory.GrantState
import com.thraksha.guardian.security.inventory.ObservedApp
import com.thraksha.guardian.security.inventory.SpecialAccessSnapshot
import com.thraksha.guardian.security.observe.RuntimeObservationStore
import com.thraksha.guardian.security.policy.ActionOutcome
import com.thraksha.guardian.security.policy.ActionStatus
import com.thraksha.guardian.security.policy.EnforcementVerdict
import com.thraksha.guardian.security.policy.SecurityAction
import com.thraksha.guardian.security.scan.AppScanRecord
import com.thraksha.guardian.security.scan.ScanClassification
import com.thraksha.guardian.security.threatintel.IndicatorTier
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 8.1 evidence progression, context, classification-to-status mapping and user
 * ACT unit proofs (guide §30). The load-bearing invariants:
 *
 *  * evidence stages never imply one another;
 *  * OBSERVED derives only from VERIFIED observations — never from static metadata,
 *    never from weaker reliabilities;
 *  * unsupported observation stays honestly unavailable;
 *  * declared-only capability exposure never escalates past WATCHING/REVIEW framing;
 *  * ACT exposes only supported actions and a failure never maps to ACTED.
 */
class Phase81EvidenceTest {

    @After
    fun tearDown() {
        RuntimeObservationStore.resetForTest()
    }

    // ---- fixtures ----

    private fun app(
        packageName: String = "com.example.subject",
        requested: Set<String> = emptySet(),
        granted: Set<String> = emptySet(),
        capabilities: Set<Capability> = emptySet(),
        specialAccess: SpecialAccessSnapshot = SpecialAccessSnapshot.UNKNOWN,
        appType: AppType = AppType.UNKNOWN,
        isSystemApp: Boolean = false,
    ) = ObservedApp(
        packageName = packageName,
        displayName = "Subject",
        appType = appType,
        requestedPermissions = requested,
        grantedPermissions = granted,
        capabilities = capabilities,
        versionName = "1.0",
        firstInstallTime = 0L,
        lastUpdateTime = 0L,
        isDebuggable = false,
        isSystemApp = isSystemApp,
        exportedComponents = ExportedComponents.NONE,
        specialAccess = specialAccess,
    )

    private fun observation(
        capabilityId: String,
        packageName: String = "com.example.subject",
        reliability: ObservationReliability = ObservationReliability.VERIFIED,
        at: Long = 1000L,
    ) = CapabilityObservation(
        capabilityId = capabilityId,
        packageName = packageName,
        observedAt = at,
        source = "test source",
        reliability = reliability,
        detail = "test detail",
    )

    private fun finding(
        severity: Severity = Severity.MEDIUM,
        source: FindingSource = FindingSource.GENERIC_RULE,
        tier: IndicatorTier? = null,
        offending: List<String> = emptyList(),
    ) = Finding(
        ruleId = "test-rule",
        ruleName = "Test rule",
        packageName = "com.example.subject",
        appName = "Subject",
        appType = AppType.UNKNOWN,
        severity = severity,
        confidence = 60,
        reason = "test",
        evidence = listOf("test evidence"),
        offendingPermissions = offending,
        source = source,
        intelligenceTier = tier,
    )

    private fun record(
        classification: ScanClassification,
        findings: List<Finding> = emptyList(),
        evidence: List<CapabilityEvidence> = emptyList(),
        displayStatus: AppDisplayStatus? = null,
        packageName: String = "com.example.subject",
    ) = AppScanRecord(
        packageName = packageName,
        displayName = "Subject",
        appType = AppType.UNKNOWN,
        isSystemApp = false,
        isEnabled = true,
        versionName = "1.0",
        installerPackage = null,
        certSha256 = setOf("aa"),
        baseApkSha256 = "bb",
        findings = findings,
        classification = classification,
        evidenceComplete = true,
        displayStatus = displayStatus,
    )

    private fun contacts(app: ObservedApp) =
        EvidenceAssembler.assemble(app, emptyList())
            .first { it.capabilityId == CapabilityCatalog.CONTACTS }

    private val readContacts = "android.permission.READ_CONTACTS"

    // ---- evidence model: stages never imply one another ----

    @Test
    fun declaredOnly_reachesDeclaredStageOnly() {
        val row = contacts(app(requested = setOf(readContacts)))
        assertTrue(row.declared)
        assertEquals(GrantState.DENIED, row.grantState)
        assertFalse("GRANTED must never imply OBSERVED", row.observed)
        assertEquals(EvidenceStage.DECLARED, row.highestStage())
        assertNotNull("an unobserved row must explain why", row.observationLimitation)
    }

    @Test
    fun declaredAndGranted_reachesGrantedStage_notObserved() {
        val row = contacts(
            app(requested = setOf(readContacts), granted = setOf(readContacts)),
        )
        assertTrue(row.declared)
        assertEquals(GrantState.GRANTED, row.grantState)
        assertFalse("granted must never imply observed", row.observed)
        assertEquals(EvidenceStage.GRANTED, row.highestStage())
    }

    @Test
    fun verifiedObservation_reachesObservedStage() {
        val row = EvidenceAssembler.assemble(
            app(requested = setOf(readContacts), granted = setOf(readContacts)),
            listOf(observation(CapabilityCatalog.CONTACTS)),
        ).first { it.capabilityId == CapabilityCatalog.CONTACTS }
        assertTrue(row.observed)
        assertEquals(EvidenceStage.OBSERVED, row.highestStage())
        assertEquals(1, row.observationCount)
        assertNull(row.observationLimitation)
    }

    @Test
    fun observationWithoutDeclaration_isStillReported() {
        // Stage 3 does not require stages 1–2: an observation is never hidden because
        // the capability was not declared.
        val rows = EvidenceAssembler.assemble(
            app(),
            listOf(observation(CapabilityCatalog.NOTIFICATION_POSTING)),
        )
        val row = rows.first { it.capabilityId == CapabilityCatalog.NOTIFICATION_POSTING }
        assertFalse(row.declared)
        assertTrue(row.observed)
    }

    @Test
    fun weakerReliabilities_neverClaimObserved() {
        listOf(
            ObservationReliability.RECENT_SYSTEM_SIGNAL,
            ObservationReliability.INDIRECT,
            ObservationReliability.UNAVAILABLE,
        ).forEach { reliability ->
            val row = EvidenceAssembler.assemble(
                app(requested = setOf(readContacts)),
                listOf(observation(CapabilityCatalog.CONTACTS, reliability = reliability)),
            ).first { it.capabilityId == CapabilityCatalog.CONTACTS }
            assertFalse(
                "$reliability must not render OBSERVED (guide §8)",
                row.observed,
            )
        }
    }

    @Test
    fun otherPackagesObservations_neverLeak() {
        val row = EvidenceAssembler.assemble(
            app(requested = setOf(readContacts)),
            listOf(
                observation(CapabilityCatalog.CONTACTS, packageName = "com.other.app"),
            ),
        ).first { it.capabilityId == CapabilityCatalog.CONTACTS }
        assertFalse(row.observed)
    }

    @Test
    fun notObservableCapability_carriesTheLimitationVerbatim() {
        val row = contacts(app(requested = setOf(readContacts)))
        assertEquals(CapabilityCatalog.NOT_OBSERVABLE, row.observationLimitation)
    }

    @Test
    fun specialAccess_unknownStaysUnknown_enabledIsEnabled() {
        val unknownRow = EvidenceAssembler.assemble(
            app(capabilities = setOf(Capability.NOTIFICATION_LISTENER)),
            emptyList(),
        ).first { it.capabilityId == CapabilityCatalog.NOTIFICATION_LISTENER }
        assertEquals(
            "an unqueried special access must be UNKNOWN, not DISABLED",
            GrantState.UNKNOWN, unknownRow.grantState,
        )

        val enabledRow = EvidenceAssembler.assemble(
            app(
                capabilities = setOf(Capability.NOTIFICATION_LISTENER),
                specialAccess = SpecialAccessSnapshot(
                    notificationListener = GrantState.ENABLED,
                ),
            ),
            emptyList(),
        ).first { it.capabilityId == CapabilityCatalog.NOTIFICATION_LISTENER }
        assertEquals(GrantState.ENABLED, enabledRow.grantState)
        assertFalse("enabled special access is still not observed use", enabledRow.observed)
    }

    // ---- observation store ----

    @Test
    fun observationStore_boundsAndOrders() {
        repeat(60) { i ->
            RuntimeObservationStore.record(
                observation(CapabilityCatalog.NETWORK, at = i.toLong()),
            )
        }
        val stored = RuntimeObservationStore.forPackage("com.example.subject")
        assertEquals("per-package bound", 50, stored.size)
        assertEquals("newest first", 59L, stored.first().observedAt)
        assertTrue(RuntimeObservationStore.hasVerified("com.example.subject"))
        assertFalse(RuntimeObservationStore.hasVerified("com.other.app"))
    }

    // ---- context assessment ----

    @Test
    fun strongIntelMatch_isSuspicious() {
        val context = ContextEvaluator.evaluate(
            AppType.UNKNOWN, false, emptyList(),
            listOf(
                finding(
                    severity = Severity.CRITICAL,
                    source = FindingSource.THREAT_INTELLIGENCE,
                    tier = IndicatorTier.STRONG,
                ),
            ),
        )
        assertEquals(ContextAssessment.SUSPICIOUS, context.assessment)
    }

    @Test
    fun observedNetworkIndicatorMatch_isSuspicious() {
        val context = ContextEvaluator.evaluate(
            AppType.UNKNOWN, false, emptyList(),
            listOf(finding(source = FindingSource.NETWORK_THREAT_INTELLIGENCE)),
        )
        assertEquals(ContextAssessment.SUSPICIOUS, context.assessment)
    }

    @Test
    fun typedAppMatchingBaseline_isExpected() {
        val context = ContextEvaluator.evaluate(
            AppType.CALLER_ID, false, emptyList(), emptyList(),
        )
        assertEquals(ContextAssessment.EXPECTED, context.assessment)
    }

    @Test
    fun unknownTypeWithGrantedPower_isUnknown_notSuspicious() {
        // The Galaxy-Wearable shape (guide §9): powerful capabilities, no baseline, no
        // observed behavior → UNKNOWN, never auto-suspicion.
        val evidence = EvidenceAssembler.assemble(
            app(
                requested = setOf(readContacts, "android.permission.SYSTEM_ALERT_WINDOW"),
                granted = setOf(readContacts),
            ),
            emptyList(),
        )
        val context = ContextEvaluator.evaluate(
            AppType.UNKNOWN, false, evidence,
            listOf(finding(severity = Severity.MEDIUM)),
        )
        assertEquals(ContextAssessment.UNKNOWN, context.assessment)
    }

    @Test
    fun verifiedSensitiveObservation_withoutBaseline_isUnusual() {
        val evidence = EvidenceAssembler.assemble(
            app(
                requested = setOf("android.permission.RECORD_AUDIO"),
                granted = setOf("android.permission.RECORD_AUDIO"),
            ),
            listOf(observation(CapabilityCatalog.MICROPHONE)),
        )
        val context = ContextEvaluator.evaluate(
            AppType.UNKNOWN, false, evidence, emptyList(),
        )
        assertEquals(ContextAssessment.UNUSUAL, context.assessment)
    }

    @Test
    fun systemAppWithoutFindings_isExpected() {
        val context = ContextEvaluator.evaluate(
            AppType.UNKNOWN, true, emptyList(), emptyList(),
        )
        assertEquals(ContextAssessment.EXPECTED, context.assessment)
    }

    // ---- status mapping (guide §23) ----

    private val unknownContext = AppContextAssessment(ContextAssessment.UNKNOWN, "test")

    @Test
    fun knownThreatMatch_mapsToKnownThreatMatch() {
        assertEquals(
            AppDisplayStatus.KNOWN_THREAT_MATCH,
            StatusMapper.map(
                ScanClassification.KNOWN_THREAT_MATCH, emptyList(), emptyList(),
                unknownContext,
            ),
        )
    }

    @Test
    fun staticHighRiskProfile_displaysAsReview_notAdvised() {
        // Declared-only high capability exposure must not visually resemble proven
        // malicious behavior (guide §11).
        assertEquals(
            AppDisplayStatus.REVIEW,
            StatusMapper.map(
                ScanClassification.HIGH_RISK_PROFILE,
                listOf(finding(severity = Severity.HIGH)),
                emptyList(),
                unknownContext,
            ),
        )
    }

    @Test
    fun suspiciousHighRiskProfile_displaysAsAdvised() {
        assertEquals(
            AppDisplayStatus.ADVISED,
            StatusMapper.map(
                ScanClassification.HIGH_RISK_PROFILE,
                listOf(finding(severity = Severity.CRITICAL, source = FindingSource.PROFILE_RULE)),
                emptyList(),
                AppContextAssessment(ContextAssessment.SUSPICIOUS, "test"),
            ),
        )
    }

    @Test
    fun declaredOnlyReview_displaysAsWatching() {
        val evidence = EvidenceAssembler.assemble(
            app(requested = setOf(readContacts)),
            emptyList(),
        )
        assertEquals(
            "Stage-1-only findings → WATCHING (guide §23)",
            AppDisplayStatus.WATCHING,
            StatusMapper.map(
                ScanClassification.REVIEW, listOf(finding()), evidence, unknownContext,
            ),
        )
    }

    @Test
    fun grantedReview_staysReview() {
        val evidence = EvidenceAssembler.assemble(
            app(requested = setOf(readContacts), granted = setOf(readContacts)),
            emptyList(),
        )
        assertEquals(
            AppDisplayStatus.REVIEW,
            StatusMapper.map(
                ScanClassification.REVIEW, listOf(finding()), evidence, unknownContext,
            ),
        )
    }

    @Test
    fun universalInternetGrant_doesNotEscalateWatching() {
        val evidence = EvidenceAssembler.assemble(
            app(
                requested = setOf("android.permission.INTERNET", readContacts),
                granted = setOf("android.permission.INTERNET"),
            ),
            emptyList(),
        )
        assertEquals(
            "an install-time INTERNET grant alone must not turn WATCHING into REVIEW",
            AppDisplayStatus.WATCHING,
            StatusMapper.map(
                ScanClassification.REVIEW, listOf(finding()), evidence, unknownContext,
            ),
        )
    }

    @Test
    fun noKnownFindings_displaysAsWatching_andPartialStaysPartial() {
        assertEquals(
            AppDisplayStatus.WATCHING,
            StatusMapper.map(
                ScanClassification.NO_KNOWN_FINDINGS, emptyList(), emptyList(), unknownContext,
            ),
        )
        assertEquals(
            AppDisplayStatus.PARTIAL,
            StatusMapper.map(
                ScanClassification.PARTIAL, emptyList(), emptyList(), unknownContext,
            ),
        )
    }

    // ---- exact ThreatPack match independence (guide §24) ----

    @Test
    fun strongThreatMatch_remainsStrong_withoutAnyStage3Evidence() {
        val strongFinding = finding(
            severity = Severity.CRITICAL,
            source = FindingSource.THREAT_INTELLIGENCE,
            tier = IndicatorTier.STRONG,
        )
        // No observations anywhere, no granted permissions — intelligence still rules.
        val status = StatusMapper.map(
            ScanClassification.KNOWN_THREAT_MATCH,
            listOf(strongFinding),
            emptyList(),
            AppContextAssessment(ContextAssessment.SUSPICIOUS, "intel"),
        )
        assertEquals(AppDisplayStatus.KNOWN_THREAT_MATCH, status)
    }

    // ---- user ACT: supported options only (guide §14) ----

    private fun actContext(
        privilege: PrivilegeLevel = PrivilegeLevel.NORMAL,
        guardActive: Boolean = false,
        scoped: Set<String> = emptySet(),
        blocked: Set<String> = emptySet(),
    ) = SupportedUserActions.ActContext(privilege, guardActive, scoped, blocked)

    @Test
    fun normalPrivilege_realApp_getsManualPathOnly() {
        val options = SupportedUserActions.forRecord(
            record(
                ScanClassification.REVIEW,
                findings = listOf(finding(offending = listOf("android.permission.CAMERA"))),
            ),
            actContext(),
        )
        assertTrue(options.any { it is UserActOption.OpenAppSettings })
        assertTrue(options.any { it is UserActOption.KeepWatching })
        assertTrue(options.any { it is UserActOption.Dismiss })
        assertTrue(
            "no privileged option may be offered without Device Owner + allowlist",
            options.none { it is UserActOption.RestrictPermissionDirect } &&
                options.none { it is UserActOption.SuspendApp } &&
                options.none { it is UserActOption.BlockNetwork },
        )
    }

    @Test
    fun deviceOwner_nonAllowlistedRealApp_stillGetsNoDirectEnforcement() {
        val options = SupportedUserActions.forRecord(
            record(
                ScanClassification.HIGH_RISK_PROFILE,
                findings = listOf(finding(offending = listOf("android.permission.CAMERA"))),
                packageName = "com.some.realapp",
            ),
            actContext(privilege = PrivilegeLevel.DEVICE_OWNER),
        )
        assertTrue(
            "arbitrary real apps must never be offered direct containment (guide §17)",
            options.none { it is UserActOption.RestrictPermissionDirect } &&
                options.none { it is UserActOption.SuspendApp },
        )
    }

    @Test
    fun deviceOwner_allowlistedTarget_getsDirectOptions() {
        val options = SupportedUserActions.forRecord(
            record(
                ScanClassification.KNOWN_THREAT_MATCH,
                findings = listOf(
                    finding(offending = listOf("android.permission.ACCESS_FINE_LOCATION")),
                ),
                packageName = "com.thraksha.demo.villaincaller",
            ),
            actContext(privilege = PrivilegeLevel.DEVICE_OWNER),
        )
        assertTrue(options.any { it is UserActOption.SuspendApp })
        assertTrue(
            options.any {
                it is UserActOption.RestrictPermissionDirect &&
                    it.permission == "android.permission.ACCESS_FINE_LOCATION"
            },
        )
    }

    @Test
    fun blockNetwork_onlyWhenGuardActiveAndScoped() {
        val villain = record(
            ScanClassification.KNOWN_THREAT_MATCH,
            findings = listOf(finding()),
            packageName = "com.thraksha.demo.villaincaller",
        )
        val inactive = SupportedUserActions.forRecord(villain, actContext(guardActive = false))
        assertTrue(inactive.none { it is UserActOption.BlockNetwork })

        val activeUnscoped = SupportedUserActions.forRecord(
            villain, actContext(guardActive = true, scoped = setOf("com.other")),
        )
        assertTrue(activeUnscoped.none { it is UserActOption.BlockNetwork })

        val activeScoped = SupportedUserActions.forRecord(
            villain,
            actContext(guardActive = true, scoped = setOf(villain.packageName)),
        )
        assertTrue(activeScoped.any { it is UserActOption.BlockNetwork })

        val alreadyBlocked = SupportedUserActions.forRecord(
            villain,
            actContext(
                guardActive = true,
                scoped = setOf(villain.packageName),
                blocked = setOf(villain.packageName),
            ),
        )
        assertTrue(alreadyBlocked.any { it is UserActOption.UnblockNetwork })
        assertTrue(alreadyBlocked.none { it is UserActOption.BlockNetwork })
    }

    @Test
    fun knownThreatCard_hasNoDismiss() {
        val options = SupportedUserActions.forRecord(
            record(
                ScanClassification.KNOWN_THREAT_MATCH,
                findings = listOf(finding()),
                displayStatus = AppDisplayStatus.KNOWN_THREAT_MATCH,
            ),
            actContext(),
        )
        assertTrue(options.none { it is UserActOption.Dismiss })
    }

    // ---- user ACT: failure never reports ACTED (guide §19) ----

    @Test
    fun enforcementOutcome_mapsHonestly() {
        val failedOutcome = ActionOutcome(
            SecurityAction.SuspendPackage, ActionStatus.FAILED, "state not confirmed",
        )
        val notAuthorised = ActionOutcome(
            SecurityAction.SuspendPackage, ActionStatus.NOT_AUTHORISED, "not device owner",
        )

        assertEquals(
            UserActStatus.ACTED,
            UserActOutcomeMapper.fromEnforcement(EnforcementVerdict.ACTED, emptyList()),
        )
        assertEquals(
            UserActStatus.PARTIALLY_ACTED,
            UserActOutcomeMapper.fromEnforcement(
                EnforcementVerdict.PARTIALLY_ACTED, emptyList(),
            ),
        )
        assertEquals(
            "a failed enforcement must never surface as ACTED",
            UserActStatus.FAILED,
            UserActOutcomeMapper.fromEnforcement(
                EnforcementVerdict.NOT_ACTED, listOf(failedOutcome),
            ),
        )
        assertEquals(
            "missing authority is a user hand-off, not a fake failure (guide §16)",
            UserActStatus.USER_ACTION_REQUIRED,
            UserActOutcomeMapper.fromEnforcement(
                EnforcementVerdict.NOT_ACTED, listOf(notAuthorised),
            ),
        )
        assertEquals(
            UserActStatus.USER_ACTION_REQUIRED,
            UserActOutcomeMapper.fromEnforcement(EnforcementVerdict.ADVISED, emptyList()),
        )
    }
}
