package com.thraksha.guardian

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thraksha.guardian.data.config.ConfigStore
import com.thraksha.guardian.data.config.ExecutionMode
import com.thraksha.guardian.security.PrivilegeLevel
import com.thraksha.guardian.security.SecurityCapability
import com.thraksha.guardian.security.act.UserActCoordinator
import com.thraksha.guardian.security.act.UserActOption
import com.thraksha.guardian.security.act.UserActStatus
import com.thraksha.guardian.security.enforcement.DeviceOwnerEnforcer
import com.thraksha.guardian.security.inventory.DemoAppRegistry
import com.thraksha.guardian.security.observe.RuntimeObservationStore
import com.thraksha.guardian.security.scan.DeviceScanEngine
import com.thraksha.guardian.security.scan.ScanClassification
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phase 8.1 Full Power user ACT (guide §17/§19), runnable only on the Device
 * Owner-provisioned emulator (skips elsewhere): the user-selected suspension of the
 * controlled target executes through PolicyEngine + DeviceOwnerEnforcer, reports ACTED
 * only after the OS state was re-queried, and is fully reversible.
 */
@RunWith(AndroidJUnit4::class)
class Phase81FullPowerActInstrumentedTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private var previousMode: ExecutionMode? = null

    @Before
    fun setUp() {
        assumeTrue(
            "requires Device Owner (thraksha_do emulator)",
            SecurityCapability.currentLevel(context) == PrivilegeLevel.DEVICE_OWNER,
        )
        runBlocking {
            DeviceScanEngine.resetForTest()
            RuntimeObservationStore.resetForTest()
            UserActCoordinator.resetForTest()
            previousMode = runCatching { ConfigStore(context).getExecutionMode() }.getOrNull()
            // GUIDED: policy advises, the user's ACT is the explicit approval.
            ConfigStore(context).setExecutionMode(ExecutionMode.GUIDED)
        }
    }

    @After
    fun tearDown() {
        runBlocking {
            // Always restore the controlled target and the previous mode.
            if (SecurityCapability.currentLevel(context) == PrivilegeLevel.DEVICE_OWNER) {
                DeviceOwnerEnforcer(context).restore(
                    DemoAppRegistry.VILLAIN_CALLER, "VillainCaller",
                )
            }
            previousMode?.let { ConfigStore(context).setExecutionMode(it) }
        }
    }

    @Test
    fun userActSuspend_isVerifiedActed_andReversible() = runBlocking {
        val result = runBlocking { DeviceScanEngine.scanDevice(context) }
        val villain = result.apps.firstOrNull {
            it.packageName == DemoAppRegistry.VILLAIN_CALLER
        }
        assumeTrue("VillainCaller not installed", villain != null)
        assertEquals(ScanClassification.KNOWN_THREAT_MATCH, villain!!.classification)

        // The ACT sheet must offer direct enforcement here — Device Owner + allowlisted.
        val options = UserActCoordinator.supportedOptions(context, villain)
        assertTrue(options.any { it is UserActOption.SuspendApp })
        assertTrue(options.any { it is UserActOption.RestrictPermissionDirect })

        // Execute the user's chosen suspension. ACTED requires verified OS state.
        val act = UserActCoordinator.perform(context, villain, UserActOption.SuspendApp)
        assertEquals(
            "Full Power user ACT must produce a verified ACTED, got " +
                "${act.status}: ${act.detail}",
            UserActStatus.ACTED, act.status,
        )
        assertTrue(
            "OS must report the package suspended",
            context.packageManager.isPackageSuspended(DemoAppRegistry.VILLAIN_CALLER),
        )

        // Reversibility (guide §18): restore and verify.
        val restore = DeviceOwnerEnforcer(context).restore(
            DemoAppRegistry.VILLAIN_CALLER, "VillainCaller",
        )
        assertFalse(
            "restore must verifiably lift the suspension",
            context.packageManager.isPackageSuspended(DemoAppRegistry.VILLAIN_CALLER),
        )
        assertEquals(
            com.thraksha.guardian.security.policy.EnforcementVerdict.ACTED,
            restore.verdict,
        )
    }
}
