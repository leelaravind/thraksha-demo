package com.thraksha.guardian

import com.thraksha.guardian.data.config.ExecutionMode
import com.thraksha.guardian.security.PrivilegeLevel
import com.thraksha.guardian.security.engine.Finding
import com.thraksha.guardian.security.events.SecurityEvent
import com.thraksha.guardian.security.events.SecurityEventBus
import com.thraksha.guardian.security.events.Severity
import com.thraksha.guardian.security.inventory.AppType
import com.thraksha.guardian.security.inventory.DemoAppRegistry
import com.thraksha.guardian.security.policy.ActionStatus
import com.thraksha.guardian.security.policy.AdvisoryEnforcer
import com.thraksha.guardian.security.policy.DecisionType
import com.thraksha.guardian.security.policy.EnforcementVerdict
import com.thraksha.guardian.security.policy.PolicyEngine
import com.thraksha.guardian.security.policy.RequiredAuthority
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The advisory path must be a *successful* ADVISED result with zero privileged actions.
 *
 * Running off-device is itself the structural proof that no `DevicePolicyManager` (or any
 * Android framework API) is called: the class has no `Context`, and any `android.*` call
 * on this path would throw from the mockable jar.
 */
class AdvisoryEnforcerTest {

    private val findings = listOf(
        Finding(
            ruleId = "caller-media-permission-mismatch",
            ruleName = "media mismatch",
            packageName = DemoAppRegistry.VILLAIN_CALLER,
            appName = "VillainCaller",
            appType = AppType.CALLER_ID,
            severity = Severity.HIGH,
            confidence = 92,
            reason = "test",
            evidence = listOf("evidence"),
            offendingPermissions = listOf("android.permission.READ_MEDIA_IMAGES"),
        ),
        Finding(
            ruleId = "caller-high-risk-profile",
            ruleName = "compound",
            packageName = DemoAppRegistry.VILLAIN_CALLER,
            appName = "VillainCaller",
            appType = AppType.CALLER_ID,
            severity = Severity.CRITICAL,
            confidence = 95,
            reason = "test",
            evidence = listOf("evidence"),
        ),
    )

    private fun decision(privilege: PrivilegeLevel, mode: ExecutionMode) = PolicyEngine.decide(
        packageName = DemoAppRegistry.VILLAIN_CALLER,
        appName = "VillainCaller",
        findings = findings,
        privilegeLevel = privilege,
        executionMode = mode,
    )

    @Test
    fun adviseDecision_producesSuccessfulAdvisedResult() = runBlocking {
        val advise = decision(PrivilegeLevel.NORMAL, ExecutionMode.OBSERVE)
        assertEquals(DecisionType.ADVISE, advise.decisionType)

        val result = AdvisoryEnforcer().execute(advise)

        assertEquals(EnforcementVerdict.ADVISED, result.verdict)
        assertEquals(advise.decisionId, result.decisionId)
        assertTrue(result.outcomes.isNotEmpty())
        // An ADVISE decision is a success state: nothing is FAILED, nothing SUCCEEDED
        // (nothing was executed), everything is advisory.
        assertTrue(
            result.outcomes.all {
                it.status == ActionStatus.ADVISED || it.status == ActionStatus.UNSUPPORTED
            },
        )
        assertTrue(result.outcomes.none { it.status == ActionStatus.SUCCEEDED })
    }

    @Test
    fun actDecisionWithoutPrivilegedEnforcer_fallsBackSafely_neverClaimsSuccess() = runBlocking {
        val act = decision(PrivilegeLevel.DEVICE_OWNER, ExecutionMode.AUTO_DEFEND)
        assertEquals(DecisionType.ACT, act.decisionType)

        val result = AdvisoryEnforcer().execute(act)

        assertEquals(EnforcementVerdict.ADVISED, result.verdict)
        assertTrue(result.outcomes.none { it.status == ActionStatus.SUCCEEDED })
        assertTrue(
            "Device-Owner actions must be recorded NOT_AUTHORISED, not pretended done",
            result.outcomes
                .filter { it.action.requiredAuthority == RequiredAuthority.DEVICE_OWNER }
                .all { it.status == ActionStatus.NOT_AUTHORISED },
        )
    }

    @Test
    fun adviceIsEmittedOntoTheSecurityEventBus() = runBlocking {
        val advise = decision(PrivilegeLevel.DEVICE_ADMIN, ExecutionMode.OBSERVE)

        val collected = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(5_000) {
                SecurityEventBus.events.first { it is SecurityEvent.AdviceIssued }
            }
        }
        AdvisoryEnforcer().execute(advise)
        val event = collected.await() as SecurityEvent.AdviceIssued

        assertEquals(DemoAppRegistry.VILLAIN_CALLER, event.packageName)
        assertTrue(event.recommendations.isNotEmpty())
        assertTrue(event.explanation.isNotBlank())
    }

    @Test
    fun advisoryResultsAreDeterministic() = runBlocking {
        val advise = decision(PrivilegeLevel.NORMAL, ExecutionMode.OBSERVE)
        val first = AdvisoryEnforcer().execute(advise)
        val second = AdvisoryEnforcer().execute(advise)
        assertEquals(first, second)
    }
}
