package com.thraksha.guardian

import com.thraksha.guardian.security.enforcement.EnforcementTargets
import com.thraksha.guardian.security.inventory.DemoAppRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The enforcement allowlist is an intentional safety boundary (guide §5.4): only the
 * controlled VillainCaller sample may ever be a privileged-enforcement target.
 */
class EnforcementTargetsTest {

    @Test
    fun villainCaller_isTheOnlyEnforceableTarget() {
        assertTrue(EnforcementTargets.isEnforceable(DemoAppRegistry.VILLAIN_CALLER))
    }

    @Test
    fun goodCaller_isNeverEnforceable() {
        assertFalse(EnforcementTargets.isEnforceable(DemoAppRegistry.GOOD_CALLER))
    }

    @Test
    fun guardianItself_isNeverEnforceable() {
        assertFalse(EnforcementTargets.isEnforceable("com.thraksha.guardian"))
    }

    @Test
    fun systemAndArbitraryPackages_areNeverEnforceable() {
        listOf(
            "com.android.systemui",
            "com.android.settings",
            "com.sec.android.app.launcher",
            "com.samsung.android.dialer",
            "com.android.phone",
            "com.example.random",
            "",
        ).forEach { pkg ->
            assertFalse("$pkg must be refused", EnforcementTargets.isEnforceable(pkg))
        }
    }
}
