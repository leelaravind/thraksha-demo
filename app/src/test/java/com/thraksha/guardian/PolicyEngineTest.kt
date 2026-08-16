package com.thraksha.guardian

import com.thraksha.guardian.data.config.ExecutionMode
import com.thraksha.guardian.security.PrivilegeLevel
import com.thraksha.guardian.security.engine.Finding
import com.thraksha.guardian.security.events.Severity
import com.thraksha.guardian.security.inventory.AppType
import com.thraksha.guardian.security.inventory.DemoAppRegistry
import com.thraksha.guardian.security.policy.DecisionType
import com.thraksha.guardian.security.policy.PolicyEngine
import com.thraksha.guardian.security.policy.PolicyThresholds
import com.thraksha.guardian.security.policy.SecurityAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Off-device tests for the pure policy layer (guide §4.10). These run with no Android
 * runtime at all — which is itself the proof that [PolicyEngine] contains no framework
 * calls: any `android.*` method invoked here would throw from the mockable jar.
 */
class PolicyEngineTest {

    private companion object {
        const val MEDIA = "android.permission.READ_MEDIA_IMAGES"
        const val LOCATION = "android.permission.ACCESS_FINE_LOCATION"
        const val QUERY_ALL = "android.permission.QUERY_ALL_PACKAGES"
    }

    private fun finding(
        ruleId: String,
        severity: Severity,
        confidence: Int,
        offending: List<String> = emptyList(),
    ) = Finding(
        ruleId = ruleId,
        ruleName = ruleId,
        packageName = DemoAppRegistry.VILLAIN_CALLER,
        appName = "VillainCaller",
        appType = AppType.CALLER_ID,
        severity = severity,
        confidence = confidence,
        reason = "test reason for $ruleId",
        evidence = listOf("test evidence"),
        offendingPermissions = offending,
    )

    private val mediaFinding =
        finding("caller-media-permission-mismatch", Severity.HIGH, 92, listOf(MEDIA))
    private val visibilityFinding =
        finding("caller-package-visibility-mismatch", Severity.HIGH, 88, listOf(QUERY_ALL))
    private val locationFinding =
        finding("caller-location-permission-mismatch", Severity.MEDIUM, 74, listOf(LOCATION))
    private val compoundFinding =
        finding("caller-high-risk-profile", Severity.CRITICAL, 95)

    private val villainFindings =
        listOf(mediaFinding, visibilityFinding, locationFinding, compoundFinding)

    private fun decide(
        findings: List<Finding>,
        privilege: PrivilegeLevel,
        mode: ExecutionMode = ExecutionMode.OBSERVE,
        thresholds: PolicyThresholds = PolicyThresholds(),
    ) = PolicyEngine.decide(
        packageName = DemoAppRegistry.VILLAIN_CALLER,
        appName = "VillainCaller",
        findings = findings,
        privilegeLevel = privilege,
        executionMode = mode,
        thresholds = thresholds,
    )

    // ---- Decision type semantics ----

    @Test
    fun noFindings_produceObserve() {
        val decision = decide(emptyList(), PrivilegeLevel.NORMAL)
        assertEquals(DecisionType.OBSERVE, decision.decisionType)
        assertTrue(decision.recommendedActions.isEmpty())
    }

    @Test
    fun normalPrivilege_withHighFinding_advises() {
        val decision = decide(listOf(mediaFinding), PrivilegeLevel.NORMAL)
        assertEquals(DecisionType.ADVISE, decision.decisionType)
    }

    @Test
    fun deviceAdmin_withCriticalFinding_advises_neverActs() {
        // Device Admin is NOT Device Owner; even a CRITICAL finding under AUTO_DEFEND
        // must not become ACT.
        val decision = decide(
            villainFindings,
            PrivilegeLevel.DEVICE_ADMIN,
            ExecutionMode.AUTO_DEFEND,
        )
        assertEquals(DecisionType.ADVISE, decision.decisionType)
    }

    @Test
    fun deviceOwner_observeMode_observes() {
        val decision = decide(villainFindings, PrivilegeLevel.DEVICE_OWNER, ExecutionMode.OBSERVE)
        assertEquals(DecisionType.OBSERVE, decision.decisionType)
    }

    @Test
    fun deviceOwner_guidedMode_advisesPendingApproval() {
        val decision = decide(villainFindings, PrivilegeLevel.DEVICE_OWNER, ExecutionMode.GUIDED)
        assertEquals(DecisionType.ADVISE, decision.decisionType)
        assertTrue(
            "the explanation must say approval is required",
            decision.explanation.contains("approval"),
        )
    }

    @Test
    fun deviceOwner_autoDefend_eligibleFindings_act() {
        val decision = decide(
            villainFindings,
            PrivilegeLevel.DEVICE_OWNER,
            ExecutionMode.AUTO_DEFEND,
        )
        assertEquals(DecisionType.ACT, decision.decisionType)
    }

    @Test
    fun deviceOwner_autoDefend_lowConfidence_fallsBackToAdvise() {
        // A HIGH finding whose rule confidence is below every action threshold must not
        // auto-act.
        val weak = finding("weak-rule", Severity.HIGH, 50, listOf(MEDIA))
        val decision = decide(listOf(weak), PrivilegeLevel.DEVICE_OWNER, ExecutionMode.AUTO_DEFEND)
        assertEquals(DecisionType.ADVISE, decision.decisionType)
    }

    @Test
    fun deviceOwner_autoDefend_mediumSeverityOnly_doesNotAct() {
        val decision = decide(
            listOf(locationFinding),
            PrivilegeLevel.DEVICE_OWNER,
            ExecutionMode.AUTO_DEFEND,
        )
        assertEquals(DecisionType.ADVISE, decision.decisionType)
    }

    // ---- Recommendation mapping (§4.6) ----

    @Test
    fun mediaMismatch_mapsToDenyingExactlyThatPermission() {
        val decision = decide(listOf(mediaFinding), PrivilegeLevel.NORMAL)
        val deny = decision.recommendedActions
            .map { it.action }
            .filterIsInstance<SecurityAction.DenyPermission>()
        assertEquals(listOf(MEDIA), deny.map { it.permission })
    }

    @Test
    fun compoundCriticalFinding_recommendsSuspension() {
        val decision = decide(villainFindings, PrivilegeLevel.NORMAL)
        assertTrue(
            decision.recommendedActions.any { it.action is SecurityAction.SuspendPackage },
        )
        assertEquals(
            "suspension must be justified by the compound rule",
            "caller-high-risk-profile",
            decision.recommendedActions
                .first { it.action is SecurityAction.SuspendPackage }.sourceRuleId,
        )
    }

    @Test
    fun nonCriticalFindings_neverRecommendSuspension() {
        // Media + visibility + location without the compound rule: no destructive
        // recommendation simply because severity is high.
        val decision = decide(
            listOf(mediaFinding, visibilityFinding, locationFinding),
            PrivilegeLevel.NORMAL,
        )
        assertFalse(decision.recommendedActions.any { it.action is SecurityAction.SuspendPackage })
    }

    @Test
    fun installTimeCapability_isNeverRecommendedForRuntimeDenial() {
        // QUERY_ALL_PACKAGES cannot be revoked at runtime; recommending its denial would
        // be recommending the impossible. The response is advisory (review in settings).
        val decision = decide(listOf(visibilityFinding), PrivilegeLevel.NORMAL)
        val deny = decision.recommendedActions
            .map { it.action }
            .filterIsInstance<SecurityAction.DenyPermission>()
        assertTrue("no DenyPermission for $QUERY_ALL: $deny", deny.none { it.permission == QUERY_ALL })
        assertTrue(
            decision.recommendedActions.any { it.action is SecurityAction.OpenAppSettings },
        )
    }

    @Test
    fun blockNetwork_isNeverRecommended_noSubsystemExists() {
        val decision = decide(villainFindings, PrivilegeLevel.DEVICE_OWNER, ExecutionMode.AUTO_DEFEND)
        assertTrue(decision.recommendedActions.none { it.action is SecurityAction.BlockNetwork })
    }

    // ---- Determinism & metadata ----

    @Test
    fun repeatedDecisions_areIdentical() {
        val first = decide(villainFindings, PrivilegeLevel.DEVICE_ADMIN, ExecutionMode.GUIDED)
        val second = decide(villainFindings, PrivilegeLevel.DEVICE_ADMIN, ExecutionMode.GUIDED)
        assertEquals(first, second)
        assertEquals(first.decisionId, second.decisionId)
    }

    @Test
    fun decisionCarriesSeverityConfidenceAndContext() {
        val decision = decide(villainFindings, PrivilegeLevel.NORMAL, ExecutionMode.GUIDED)
        assertEquals(Severity.CRITICAL, decision.highestSeverity)
        assertEquals(95, decision.maxRuleConfidence)
        assertEquals(PrivilegeLevel.NORMAL, decision.privilegeLevel)
        assertEquals(ExecutionMode.GUIDED, decision.executionMode)
        assertEquals(villainFindings, decision.findings)
        assertTrue(decision.explanation.isNotBlank())
    }
}
