package com.thraksha.guardian

import com.thraksha.guardian.automation.ActionSupport
import com.thraksha.guardian.automation.ActionTarget
import com.thraksha.guardian.automation.AutomationCapability
import com.thraksha.guardian.automation.AutomationIntent
import com.thraksha.guardian.automation.AutomationPlanner
import com.thraksha.guardian.automation.AutomationSafetyPolicy
import com.thraksha.guardian.automation.AutomationTargets
import com.thraksha.guardian.automation.CapabilityProbe
import com.thraksha.guardian.automation.RoutineType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 9 planner + safety-policy unit proofs (guide §27, §35): plans are
 * deterministic, unsupported/user-assisted actions stay visible, required-vs-optional
 * is explicit, and the safety policy rejects everything outside its allowlist — the
 * boundary a Phase 10 AI will sit behind.
 */
class Phase9AutomationTest {

    /** Deterministic fake probe: everything supported unless configured otherwise. */
    private class FakeProbe(
        private val userAssisted: Set<AutomationCapability> = emptySet(),
        private val unsupported: Set<AutomationCapability> = emptySet(),
        private val launchablePackages: Set<String> = setOf(
            "com.samsung.android.calendar", "com.google.android.apps.maps",
        ),
    ) : CapabilityProbe {
        override fun support(capability: AutomationCapability) = when (capability) {
            in unsupported -> ActionSupport.UNSUPPORTED to "fake: unsupported"
            in userAssisted -> ActionSupport.USER_ACTION_REQUIRED to "fake: grant needed"
            else -> ActionSupport.SUPPORTED to "fake: supported"
        }

        override fun launchSupport(packageName: String?) = when {
            packageName == null -> ActionSupport.UNSUPPORTED to "no target"
            packageName in launchablePackages -> ActionSupport.SUPPORTED to "launchable"
            else -> ActionSupport.UNSUPPORTED to "not installed"
        }

        override fun firstLaunchable(candidates: List<String>): String? =
            candidates.firstOrNull { it in launchablePackages }
    }

    private val meeting = AutomationIntent(RoutineType.MEETING, durationMinutes = 30)

    // ---- safety policy (guide §27) ----

    @Test
    fun validIntents_pass() {
        assertTrue(
            AutomationSafetyPolicy.validateIntent(meeting)
                is AutomationSafetyPolicy.Validation.Valid,
        )
        assertTrue(
            AutomationSafetyPolicy.validateIntent(
                AutomationIntent(RoutineType.DRIVING, durationMinutes = null),
            ) is AutomationSafetyPolicy.Validation.Valid,
        )
    }

    @Test
    fun durationOutOfBounds_isRejected() {
        listOf(0, -5, 481, 10_000).forEach { minutes ->
            assertTrue(
                "duration $minutes must be rejected",
                AutomationSafetyPolicy.validateIntent(
                    AutomationIntent(RoutineType.MEETING, durationMinutes = minutes),
                ) is AutomationSafetyPolicy.Validation.Rejected,
            )
        }
    }

    @Test
    fun invalidOrForbiddenLaunchTargets_areRejected() {
        listOf(
            "not a package", "intent://evil", "http://x.y", "..", "com.thraksha.guardian",
        ).forEach { target ->
            assertTrue(
                "target '$target' must be rejected",
                AutomationSafetyPolicy.validateIntent(
                    AutomationIntent(RoutineType.MEETING, 30, targetApp = target),
                ) is AutomationSafetyPolicy.Validation.Rejected,
            )
        }
    }

    @Test
    fun customActions_validatedFieldByField() {
        // Empty custom routine rejected.
        assertTrue(
            AutomationSafetyPolicy.validateIntent(
                AutomationIntent(RoutineType.CUSTOM, 15),
            ) is AutomationSafetyPolicy.Validation.Rejected,
        )
        // Out-of-range brightness rejected.
        assertTrue(
            AutomationSafetyPolicy.validateIntent(
                AutomationIntent(
                    RoutineType.CUSTOM, 15,
                    customActions = listOf(
                        ActionTarget(AutomationCapability.SCREEN_BRIGHTNESS, 9999),
                    ),
                ),
            ) is AutomationSafetyPolicy.Validation.Rejected,
        )
        // Invalid DND filter rejected.
        assertTrue(
            AutomationSafetyPolicy.validateIntent(
                AutomationIntent(
                    RoutineType.CUSTOM, 15,
                    customActions = listOf(
                        ActionTarget(AutomationCapability.DO_NOT_DISTURB, 42),
                    ),
                ),
            ) is AutomationSafetyPolicy.Validation.Rejected,
        )
        // Timeout outside bounds rejected.
        assertTrue(
            AutomationSafetyPolicy.validateIntent(
                AutomationIntent(
                    RoutineType.CUSTOM, 15,
                    customActions = listOf(
                        ActionTarget(AutomationCapability.SCREEN_TIMEOUT, 5_000),
                    ),
                ),
            ) is AutomationSafetyPolicy.Validation.Rejected,
        )
        // Non-allowlisted settings action rejected (no arbitrary intents from AI, §28).
        assertTrue(
            AutomationSafetyPolicy.validateIntent(
                AutomationIntent(
                    RoutineType.CUSTOM, 15,
                    customActions = listOf(
                        ActionTarget(
                            AutomationCapability.OPEN_SETTINGS,
                            stringValue = "android.settings.FACTORY_RESET",
                        ),
                    ),
                ),
            ) is AutomationSafetyPolicy.Validation.Rejected,
        )
        // Custom actions on a non-custom routine rejected.
        assertTrue(
            AutomationSafetyPolicy.validateIntent(
                AutomationIntent(
                    RoutineType.MEETING, 30,
                    customActions = listOf(
                        ActionTarget(AutomationCapability.SCREEN_BRIGHTNESS, 100),
                    ),
                ),
            ) is AutomationSafetyPolicy.Validation.Rejected,
        )
        // A fully valid custom routine passes.
        assertTrue(
            AutomationSafetyPolicy.validateIntent(
                AutomationIntent(
                    RoutineType.CUSTOM, 15,
                    customActions = listOf(
                        ActionTarget(
                            AutomationCapability.DO_NOT_DISTURB,
                            AutomationTargets.DND_PRIORITY,
                        ),
                        ActionTarget(AutomationCapability.SCREEN_BRIGHTNESS, 128),
                    ),
                ),
            ) is AutomationSafetyPolicy.Validation.Valid,
        )
    }

    // ---- planner determinism + plan shape (guide §11, §35) ----

    @Test
    fun meetingPlan_isDeterministic_andCorrectlyShaped() {
        val probe = FakeProbe()
        val plan1 = AutomationPlanner.plan(meeting, probe)
        val plan2 = AutomationPlanner.plan(meeting, probe)
        assertEquals("identical inputs must produce identical plans", plan1, plan2)

        assertTrue(plan1.executable)
        assertNull(plan1.blockedReason)
        val capabilities = plan1.actions.map { it.target.capability }
        assertEquals(
            listOf(
                AutomationCapability.RINGER_MODE,
                AutomationCapability.SCREEN_BRIGHTNESS,
                AutomationCapability.DO_NOT_DISTURB,
                AutomationCapability.LAUNCH_APP,
            ),
            capabilities,
        )
        // Required vs optional is explicit (§13). Ringer is deliberately optional —
        // zen coerces the presented ringer mode on AOSP (planner comment).
        assertFalse(plan1.actions.first { it.target.capability == AutomationCapability.RINGER_MODE }.required)
        assertTrue(plan1.actions.first { it.target.capability == AutomationCapability.DO_NOT_DISTURB }.required)
        assertFalse(plan1.actions.first { it.target.capability == AutomationCapability.SCREEN_BRIGHTNESS }.required)
        assertFalse(plan1.actions.first { it.target.capability == AutomationCapability.LAUNCH_APP }.required)
        // Launch is never treated as reversible.
        assertFalse(plan1.actions.first { it.target.capability == AutomationCapability.LAUNCH_APP }.reversible)
    }

    @Test
    fun missingSpecialAccess_makesRequiredActionsUserAssisted_andBlocksExecution() {
        val plan = AutomationPlanner.plan(
            meeting,
            FakeProbe(
                userAssisted = setOf(
                    AutomationCapability.DO_NOT_DISTURB,
                    AutomationCapability.RINGER_MODE,
                ),
            ),
        )
        // Visible, not hidden (§11) — and the plan honestly refuses to run (§12).
        assertEquals(
            ActionSupport.USER_ACTION_REQUIRED,
            plan.actions.first { it.target.capability == AutomationCapability.DO_NOT_DISTURB }.support,
        )
        assertFalse(plan.executable)
        assertNotNull(plan.blockedReason)
    }

    @Test
    fun unsupportedOptionalAction_staysVisible_withoutBlockingThePlan() {
        val plan = AutomationPlanner.plan(
            meeting,
            FakeProbe(unsupported = setOf(AutomationCapability.SCREEN_BRIGHTNESS)),
        )
        assertEquals(
            ActionSupport.UNSUPPORTED,
            plan.actions.first { it.target.capability == AutomationCapability.SCREEN_BRIGHTNESS }.support,
        )
        assertTrue("optional unsupported action must not block the plan", plan.executable)
    }

    @Test
    fun launchTarget_omittedWhenNoCandidateInstalled_overriddenByIntent() {
        val noApps = AutomationPlanner.plan(meeting, FakeProbe(launchablePackages = emptySet()))
        assertTrue(
            "no launchable candidate → no launch action, not a fake one",
            noApps.actions.none { it.target.capability == AutomationCapability.LAUNCH_APP },
        )

        val overridden = AutomationPlanner.plan(
            meeting.copy(targetApp = "com.google.android.apps.maps"), FakeProbe(),
        )
        assertEquals(
            "com.google.android.apps.maps",
            overridden.actions
                .first { it.target.capability == AutomationCapability.LAUNCH_APP }
                .target.stringValue,
        )
    }

    @Test
    fun focusPlan_carriesTheUserAssistedRestrictionItem() {
        val plan = AutomationPlanner.plan(
            AutomationIntent(RoutineType.FOCUS, durationMinutes = 60), FakeProbe(),
        )
        val restriction = plan.actions.first {
            it.target.capability == AutomationCapability.OPEN_SETTINGS
        }
        assertEquals(ActionSupport.USER_ACTION_REQUIRED, restriction.support)
        assertFalse(restriction.required)
        assertTrue("hand-off item must still leave the plan executable", plan.executable)
    }

    @Test
    fun drivingPlan_isSafetyShaped() {
        val plan = AutomationPlanner.plan(AutomationIntent(RoutineType.DRIVING), FakeProbe())
        // Calls stay audible; no DND action exists at all (§18).
        assertTrue(plan.actions.none { it.target.capability == AutomationCapability.DO_NOT_DISTURB })
        assertEquals(
            AutomationTargets.RINGER_NORMAL,
            plan.actions.first { it.target.capability == AutomationCapability.RINGER_MODE }
                .target.intValue,
        )
        assertEquals(
            AutomationTargets.BRIGHTNESS_DRIVING,
            plan.actions.first { it.target.capability == AutomationCapability.SCREEN_BRIGHTNESS }
                .target.intValue,
        )
    }

    @Test
    fun customPlan_isComposedFromTheIntent_allOptional() {
        val intent = AutomationIntent(
            RoutineType.CUSTOM, 15,
            customActions = listOf(
                ActionTarget(AutomationCapability.DO_NOT_DISTURB, AutomationTargets.DND_PRIORITY),
                ActionTarget(AutomationCapability.SCREEN_TIMEOUT, 120_000),
            ),
        )
        val plan = AutomationPlanner.plan(intent, FakeProbe())
        assertEquals(2, plan.actions.size)
        assertTrue(plan.actions.all { !it.required })
        assertTrue(plan.actions.all { it.reversible })
        assertTrue(plan.executable)
    }

    @Test
    fun rejectedIntent_yieldsNonExecutablePlanWithTheSafetyReason() {
        val plan = AutomationPlanner.plan(
            AutomationIntent(RoutineType.MEETING, durationMinutes = 9_999), FakeProbe(),
        )
        assertFalse(plan.executable)
        assertTrue(plan.actions.isEmpty())
        assertTrue(
            plan.blockedReason?.contains("Safety policy") == true,
        )
    }
}
