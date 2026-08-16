package com.thraksha.guardian

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thraksha.guardian.automation.ActionSupport
import com.thraksha.guardian.automation.ActionTarget
import com.thraksha.guardian.automation.AutomationActionStatus
import com.thraksha.guardian.automation.AutomationCapability
import com.thraksha.guardian.automation.AutomationEngine
import com.thraksha.guardian.automation.AutomationIntent
import com.thraksha.guardian.automation.AutomationTargets
import com.thraksha.guardian.automation.PersistedRun
import com.thraksha.guardian.automation.RoutineType
import com.thraksha.guardian.automation.RunPhase
import com.thraksha.guardian.automation.executors.BrightnessExecutor
import com.thraksha.guardian.automation.executors.DndExecutor
import com.thraksha.guardian.automation.executors.RingerExecutor
import com.thraksha.guardian.automation.executors.ScreenTimeoutExecutor
import com.thraksha.guardian.data.config.ConfigStore
import com.thraksha.guardian.data.db.DatabaseProvider
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phase 9 on-device proofs (guide §36–§38): real Android state transitions —
 * snapshot-before-mutation, verified execution, verified restoration, rollback on
 * required failure, one-active-routine, process-recovery reconstruction and audit
 * persistence. Every test restores the state it found (§38: leave the phone as it was).
 *
 * Requires the two user-grantable special accesses (dev-granted via adb for test runs;
 * the product path is the in-app onboarding). Tests skip honestly where access is absent.
 */
@RunWith(AndroidJUnit4::class)
class Phase9AutomationInstrumentedTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val json = Json { ignoreUnknownKeys = true }

    private val nm get() = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val audio get() = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private fun hasDndAccess() = nm.isNotificationPolicyAccessGranted
    private fun hasWriteSettings() = Settings.System.canWrite(context)

    private suspend fun persistedRun(): PersistedRun? =
        ConfigStore(context).getRawValue("automation.active_run")
            ?.let { json.decodeFromString(PersistedRun.serializer(), it) }

    @Before
    fun reset() {
        AutomationEngine.resetForTest()
    }

    @After
    fun cleanup() = runBlocking {
        // Never leave a routine (or its persisted duty) behind.
        if (persistedRun() != null) {
            AutomationEngine.stop(context, "test cleanup")
        }
        AutomationEngine.resetForTest()
    }

    // ---- executor round-trips (§37): real state, real verify, real restore ----

    @Test
    fun dndExecutor_roundTripsRealState() {
        assumeTrue("DND access not granted", hasDndAccess())
        val executor = DndExecutor()
        val snapshot = executor.snapshot(context)
        assumeTrue("filter unreadable", snapshot.readable)

        val target = ActionTarget(
            AutomationCapability.DO_NOT_DISTURB,
            if (snapshot.previousValue == AutomationTargets.DND_PRIORITY) {
                AutomationTargets.DND_ALL
            } else {
                AutomationTargets.DND_PRIORITY
            },
        )
        assertTrue(executor.apply(context, target))
        assertTrue("applied state must verify", executor.verify(context, target))
        assertTrue(executor.restore(context, snapshot))
        assertTrue("restored state must verify", executor.verifyRestored(context, snapshot))
    }

    @Test
    fun ringerExecutor_roundTripsRealState() {
        assumeTrue("DND access not granted", hasDndAccess())
        val executor = RingerExecutor()
        val snapshot = executor.snapshot(context)
        assumeTrue(snapshot.readable)

        val target = ActionTarget(
            AutomationCapability.RINGER_MODE,
            if (snapshot.previousValue == AudioManager.RINGER_MODE_VIBRATE) {
                AudioManager.RINGER_MODE_NORMAL
            } else {
                AudioManager.RINGER_MODE_VIBRATE
            },
        )
        assertTrue(executor.apply(context, target))
        assertTrue(executor.verify(context, target))
        assertTrue(executor.restore(context, snapshot))
        assertTrue(executor.verifyRestored(context, snapshot))
    }

    @Test
    fun brightnessExecutor_roundTripsValueAndMode() {
        assumeTrue("WRITE_SETTINGS not granted", hasWriteSettings())
        val executor = BrightnessExecutor()
        val snapshot = executor.snapshot(context)
        assumeTrue(snapshot.readable)

        val target = ActionTarget(
            AutomationCapability.SCREEN_BRIGHTNESS,
            if (snapshot.previousValue == 77) 120 else 77,
        )
        assertTrue(executor.apply(context, target))
        assertTrue(executor.verify(context, target))
        // Restore must bring back BOTH the value and the auto/manual mode (§9).
        assertTrue(executor.restore(context, snapshot))
        assertTrue(executor.verifyRestored(context, snapshot))
        assertEquals(
            snapshot.previousValue2,
            Settings.System.getInt(
                context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
            ),
        )
    }

    @Test
    fun screenTimeoutExecutor_roundTripsRealState() {
        assumeTrue("WRITE_SETTINGS not granted", hasWriteSettings())
        val executor = ScreenTimeoutExecutor()
        val snapshot = executor.snapshot(context)
        assumeTrue(snapshot.readable)

        val target = ActionTarget(
            AutomationCapability.SCREEN_TIMEOUT,
            if (snapshot.previousValue == 120_000) 60_000 else 120_000,
        )
        assertTrue(executor.apply(context, target))
        assertTrue(executor.verify(context, target))
        assertTrue(executor.restore(context, snapshot))
        assertTrue(executor.verifyRestored(context, snapshot))
    }

    // ---- the hero end-to-end (§38, §42) ----

    @Test
    fun meetingMode_endToEnd_changesVerifies_thenRestoresExactPreviousState() = runBlocking {
        assumeTrue(hasDndAccess() && hasWriteSettings())

        // Record the REAL pre-state independently of the engine.
        val preFilter = nm.currentInterruptionFilter
        val preRinger = audio.ringerMode
        val preBrightness = Settings.System.getInt(
            context.contentResolver, Settings.System.SCREEN_BRIGHTNESS,
        )
        val preBrightnessMode = Settings.System.getInt(
            context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
        )

        // No launch target: the test proves the reversible mutations.
        AutomationEngine.start(
            context, AutomationIntent(RoutineType.MEETING, durationMinutes = 30),
        )
        val active = AutomationEngine.state.value
        assertEquals(RunPhase.ACTIVE, active.phase)

        // The engine's claims must match REAL Android state. Ringer: verified VIBRATE
        // at execution time; an active zen filter may present it as SILENT afterwards
        // (AOSP coercion; Samsung keeps VIBRATE) — both are the OS's own truth.
        assertEquals(AutomationTargets.DND_PRIORITY, nm.currentInterruptionFilter)
        assertTrue(
            audio.ringerMode == AudioManager.RINGER_MODE_VIBRATE ||
                audio.ringerMode == AudioManager.RINGER_MODE_SILENT,
        )
        assertEquals(
            AutomationTargets.BRIGHTNESS_MEETING,
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS),
        )
        val actedCount = active.actionResults.count {
            it.status == AutomationActionStatus.ACTED
        }
        assertTrue("expected ≥3 verified actions, got $actedCount", actedCount >= 3)

        // Restoration duty is persisted (survives process death, §22).
        val run = persistedRun()
        assertNotNull(run)
        assertTrue(run!!.appliedCapabilities.isNotEmpty())
        assertTrue(run.snapshot.all { it.readable })

        // STOP & RESTORE (§21) — back to the EXACT previous values.
        AutomationEngine.stop(context, "test stop")
        assertEquals(RunPhase.RESTORED, AutomationEngine.state.value.phase)
        assertEquals(preFilter, nm.currentInterruptionFilter)
        assertEquals(preRinger, audio.ringerMode)
        assertEquals(
            preBrightness,
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS),
        )
        assertEquals(
            preBrightnessMode,
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE),
        )
        assertNull("persisted duty must be cleared after restore", persistedRun())
    }

    @Test
    fun oneActiveRoutine_secondStartIsRefused() = runBlocking {
        assumeTrue(hasDndAccess() && hasWriteSettings())
        AutomationEngine.start(context, AutomationIntent(RoutineType.MEETING, 30))
        assumeTrue(AutomationEngine.state.value.phase == RunPhase.ACTIVE)

        AutomationEngine.start(context, AutomationIntent(RoutineType.FOCUS, 60))
        val state = AutomationEngine.state.value
        assertEquals("first routine must stay active", RunPhase.ACTIVE, state.phase)
        assertEquals("Meeting Mode", state.activeRun?.routineLabel)
        assertTrue(state.message?.contains("Another routine is active") == true)

        AutomationEngine.stop(context, "test cleanup")
        assertEquals(RunPhase.RESTORED, AutomationEngine.state.value.phase)
    }

    @Test
    fun requiredFailure_rollsBackAppliedActions_toRealPreviousState() = runBlocking {
        assumeTrue(hasDndAccess() && hasWriteSettings())
        val preRinger = audio.ringerMode
        val preBrightness = Settings.System.getInt(
            context.contentResolver, Settings.System.SCREEN_BRIGHTNESS,
        )
        val preBrightnessMode = Settings.System.getInt(
            context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
        )

        // Meeting executes ringer → brightness → DND; failing DND (required, last)
        // forces rollback of the two genuinely applied changes.
        AutomationEngine.testFailCapability = AutomationCapability.DO_NOT_DISTURB
        AutomationEngine.start(context, AutomationIntent(RoutineType.MEETING, 30))
        AutomationEngine.testFailCapability = null

        val state = AutomationEngine.state.value
        assertEquals(RunPhase.FAILED, state.phase)
        assertTrue(
            state.restoreResults.any {
                it.capability == AutomationCapability.RINGER_MODE &&
                    it.status == AutomationActionStatus.ROLLED_BACK
            },
        )
        // The DEVICE is back where it was — the real §14 guarantee.
        assertEquals(preRinger, audio.ringerMode)
        assertEquals(
            preBrightness,
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS),
        )
        assertEquals(
            preBrightnessMode,
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE),
        )
        assertNull("rollback must clear the persisted duty", persistedRun())
    }

    @Test
    fun processRecovery_reconstructsActiveRoutine_fromPersistedState() = runBlocking {
        assumeTrue(hasDndAccess() && hasWriteSettings())
        AutomationEngine.start(context, AutomationIntent(RoutineType.MEETING, 30))
        assumeTrue(AutomationEngine.state.value.phase == RunPhase.ACTIVE)

        // Simulate process death: in-memory engine state gone, persistence intact.
        AutomationEngine.resetForTest()
        assertEquals(RunPhase.IDLE, AutomationEngine.state.value.phase)
        assertNotNull(persistedRun())

        AutomationEngine.recover(context)
        val recovered = AutomationEngine.state.value
        assertEquals(
            "recovery must reconstruct ACTIVE, not forget restoration duty",
            RunPhase.ACTIVE, recovered.phase,
        )
        assertEquals("Meeting Mode", recovered.activeRun?.routineLabel)

        AutomationEngine.stop(context, "test cleanup")
        assertEquals(RunPhase.RESTORED, AutomationEngine.state.value.phase)
    }

    @Test
    fun expiryWhileProcessDead_isRestoredOnRecovery() = runBlocking {
        assumeTrue(hasDndAccess())
        // Genuinely change DND, then hand-craft the persisted run as already expired.
        val executor = DndExecutor()
        val snapshot = executor.snapshot(context)
        assumeTrue(snapshot.readable)
        val target = ActionTarget(
            AutomationCapability.DO_NOT_DISTURB,
            if (snapshot.previousValue == AutomationTargets.DND_PRIORITY) {
                AutomationTargets.DND_ALL
            } else {
                AutomationTargets.DND_PRIORITY
            },
        )
        assertTrue(executor.apply(context, target))
        assumeTrue(executor.verify(context, target))

        val run = PersistedRun(
            runId = "run-test-expired",
            routineType = RoutineType.MEETING.name,
            routineLabel = "Meeting Mode",
            startedAt = System.currentTimeMillis() - 3_600_000,
            expiresAt = System.currentTimeMillis() - 60_000,
            snapshot = listOf(snapshot),
            appliedCapabilities = listOf(AutomationCapability.DO_NOT_DISTURB.name),
        )
        ConfigStore(context).putRawValue(
            "automation.active_run",
            json.encodeToString(PersistedRun.serializer(), run),
        )
        AutomationEngine.resetForTest()

        AutomationEngine.recover(context)
        assertEquals(RunPhase.RESTORED, AutomationEngine.state.value.phase)
        assertEquals(
            "expired routine must be restored to the snapshot state",
            snapshot.previousValue, nm.currentInterruptionFilter,
        )
        assertNull(persistedRun())
    }

    @Test
    fun blockedPlan_executesNothing_whenRequiredAccessMissing() = runBlocking {
        // Runs meaningfully when access is NOT granted (the denial path, §38).
        assumeTrue("access granted — denial path not testable here", !hasDndAccess())
        val preRinger = audio.ringerMode

        AutomationEngine.start(context, AutomationIntent(RoutineType.MEETING, 30))
        val state = AutomationEngine.state.value
        assertEquals(RunPhase.FAILED, state.phase)
        assertNotNull(state.message)
        assertEquals("nothing may have been executed", preRinger, audio.ringerMode)
        assertNull(persistedRun())
    }

    // ---- audit integration (§32/§33) ----

    @Test
    fun automationLifecycle_landsInTheTamperEvidentAuditChain() = runBlocking {
        assumeTrue(hasDndAccess() && hasWriteSettings())
        val dao = DatabaseProvider.get(context).auditDao()
        val before = dao.count()

        AutomationEngine.start(context, AutomationIntent(RoutineType.MEETING, 30))
        assumeTrue(AutomationEngine.state.value.phase == RunPhase.ACTIVE)
        AutomationEngine.stop(context, "audit test stop")

        val expectedStages = listOf(
            "REQUESTED", "PLANNED", "STARTED", "ACTION_VERIFIED", "ACTIVE",
            "RESTORE_REQUESTED", "STATE_RESTORED", "RESTORED",
        )
        var stages = emptyList<String>()
        var waited = 0
        while (waited < 12_000) {
            stages = dao.allOrdered()
                .filter {
                    it.id > before && it.type == "AUTOMATION" &&
                        it.details.contains("Meeting Mode")
                }
                .map { it.details.substringAfter('[').substringBefore(']') }
            if (stages.containsAll(expectedStages)) break
            Thread.sleep(250)
            waited += 250
        }
        expectedStages.forEach { stage ->
            assertTrue("missing audit stage $stage in $stages", stage in stages)
        }
        // And the chain still verifies (tamper-evidence intact).
        assertTrue(
            com.thraksha.guardian.data.audit.AuditLog(dao).verifyChain(),
        )
    }

    @Test
    fun engineNeverReportsActed_withoutMatchingRealState() = runBlocking {
        assumeTrue(hasDndAccess() && hasWriteSettings())
        AutomationEngine.start(context, AutomationIntent(RoutineType.MEETING, 30))
        val state = AutomationEngine.state.value
        assumeTrue(state.phase == RunPhase.ACTIVE)
        state.actionResults
            .filter { it.status == AutomationActionStatus.ACTED }
            .forEach { result ->
                val matches = when (result.action.target.capability) {
                    AutomationCapability.DO_NOT_DISTURB ->
                        nm.currentInterruptionFilter == result.action.target.intValue
                    AutomationCapability.RINGER_MODE ->
                        // Verified at execution time; an active zen filter may present
                        // SILENT afterwards (documented AOSP coercion).
                        audio.ringerMode == result.action.target.intValue ||
                            (
                                nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL &&
                                    audio.ringerMode == AudioManager.RINGER_MODE_SILENT
                                )
                    AutomationCapability.SCREEN_BRIGHTNESS ->
                        Settings.System.getInt(
                            context.contentResolver, Settings.System.SCREEN_BRIGHTNESS,
                        ) == result.action.target.intValue
                    AutomationCapability.SCREEN_TIMEOUT ->
                        Settings.System.getInt(
                            context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT,
                        ) == result.action.target.intValue
                    else -> true
                }
                assertTrue(
                    "ACTED claimed for ${result.action.label} but Android disagrees",
                    matches,
                )
            }
        AutomationEngine.stop(context, "test cleanup")
        assertEquals(RunPhase.RESTORED, AutomationEngine.state.value.phase)
    }
}
