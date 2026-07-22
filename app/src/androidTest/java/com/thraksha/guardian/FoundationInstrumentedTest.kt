package com.thraksha.guardian

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thraksha.guardian.data.config.ConfigStore
import com.thraksha.guardian.data.config.ExecutionMode
import com.thraksha.guardian.security.PrivilegeLevel
import com.thraksha.guardian.security.SecurityCapability
import com.thraksha.guardian.security.rulepack.RulepackLoader
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** On-device checks for config defaults, privilege detection, and the signed rulepack load path. */
@RunWith(AndroidJUnit4::class)
class FoundationInstrumentedTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun configDefaults_seedAndReadBack() = runBlocking {
        val config = ConfigStore(context)
        config.ensureDefaults()
        assertEquals(ExecutionMode.OBSERVE, config.getExecutionMode())
        val tiers = config.getTierThresholds()
        assertEquals(60, tiers.low)
        assertEquals(80, tiers.medium)
        assertEquals(95, tiers.high)
        assertEquals(99, tiers.critical)
    }

    @Test
    fun securityCapability_returnsValidLevel() {
        val level = SecurityCapability.currentLevel(context)
        assertTrue("level is a valid enum", PrivilegeLevel.values().contains(level))
        assertEquals(
            "canEnforce iff DEVICE_OWNER",
            level == PrivilegeLevel.DEVICE_OWNER,
            SecurityCapability.canEnforce(context),
        )
    }

    @Test
    fun rulepackLoader_loadsSignedPackWithTwelveRules() {
        val rulepack = RulepackLoader(context).load()
        assertEquals(1, rulepack.version)
        assertEquals(12, rulepack.rules.size)
    }
}
