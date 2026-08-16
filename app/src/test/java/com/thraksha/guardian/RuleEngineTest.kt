package com.thraksha.guardian

import com.thraksha.guardian.security.baseline.AppBaseline
import com.thraksha.guardian.security.baseline.AppBaselines
import com.thraksha.guardian.security.engine.RuleEngine
import com.thraksha.guardian.security.events.Severity
import com.thraksha.guardian.security.inventory.AppType
import com.thraksha.guardian.security.inventory.DemoAppRegistry
import com.thraksha.guardian.security.inventory.ObservedApp
import com.thraksha.guardian.security.rulepack.Rulepack
import com.thraksha.guardian.security.rulepack.RulepackVerifier
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Off-device tests for the detection brain, run against the **real signed rulepack asset**
 * rather than a hand-written fixture — so a rulepack edited without re-signing, or edited
 * in a way that breaks the demo, fails here rather than on stage.
 *
 * These prove the app-type-aware principle: the same permission on the same observation
 * produces a finding for one app type and none for another.
 */
class RuleEngineTest {

    private companion object {
        const val MEDIA = "android.permission.READ_MEDIA_IMAGES"
        const val QUERY_ALL = "android.permission.QUERY_ALL_PACKAGES"
        const val LOCATION = "android.permission.ACCESS_FINE_LOCATION"
        const val PHONE_STATE = "android.permission.READ_PHONE_STATE"
        const val CONTACTS = "android.permission.READ_CONTACTS"
        const val CALL_LOG = "android.permission.READ_CALL_LOG"
        const val INTERNET = "android.permission.INTERNET"
    }

    private fun asset(name: String): File =
        listOf(File("src/main/assets/$name"), File("app/src/main/assets/$name"))
            .firstOrNull { it.exists() }
            ?: error("asset not found: $name")

    /** Loads the bundled pack the same fail-closed way RulepackLoader does. */
    private fun signedRulepack(): Rulepack {
        val bytes = asset("rulepack.json").readBytes()
        require(
            RulepackVerifier.verify(
                bytes,
                asset("rulepack.sig").readText(),
                asset("rulepack_public.key").readText(),
            ),
        ) { "bundled rulepack signature does not verify — re-run tools/sign_rulepack.sh" }
        return Json { ignoreUnknownKeys = true }
            .decodeFromString(Rulepack.serializer(), bytes.decodeToString())
    }

    private val callerIdBaseline: AppBaseline =
        requireNotNull(AppBaselines.forType(AppType.CALLER_ID))

    private fun app(
        packageName: String,
        name: String,
        permissions: Set<String>,
        appType: AppType = AppType.CALLER_ID,
        granted: Set<String> = emptySet(),
    ) = ObservedApp(
        packageName = packageName,
        displayName = name,
        appType = appType,
        requestedPermissions = permissions,
        grantedPermissions = granted,
        capabilities = emptySet(),
        versionName = "1.0",
        firstInstallTime = 0L,
        lastUpdateTime = 0L,
        isDebuggable = true,
        isSystemApp = false,
    )

    private val baselinePermissions = setOf(PHONE_STATE, CONTACTS, CALL_LOG, INTERNET)

    private fun goodCaller() = app(
        DemoAppRegistry.GOOD_CALLER,
        "GoodCaller",
        baselinePermissions,
    )

    private fun villainCaller() = app(
        DemoAppRegistry.VILLAIN_CALLER,
        "VillainCaller",
        baselinePermissions + setOf(MEDIA, QUERY_ALL, LOCATION),
    )

    // ---- App baseline ----

    @Test
    fun goodCallerProfile_isAllowed_producingZeroFindings() {
        val findings = RuleEngine(signedRulepack()).evaluate(goodCaller(), callerIdBaseline)
        assertEquals("GoodCaller must produce no findings: $findings", 0, findings.size)
    }

    @Test
    fun villainCallerMediaMismatch_isFlagged() {
        val findings = RuleEngine(signedRulepack()).evaluate(villainCaller(), callerIdBaseline)
        val media = findings.firstOrNull { it.ruleId == "caller-media-permission-mismatch" }
        assertNotNull("photo/media mismatch must fire", media)
        assertEquals(Severity.HIGH, media!!.severity)
        assertTrue(
            "evidence must cite the actual permission read from the manifest",
            media.evidence.any { it.contains(MEDIA) },
        )
    }

    @Test
    fun sameCapabilityIsAcceptable_whenTheBaselineForThatTypeIncludesIt() {
        // Identical app, identical permission — only the baseline differs. A type whose
        // role legitimately needs photo access produces no finding from the same rule.
        val galleryLikeBaseline = AppBaseline(
            appType = AppType.CALLER_ID,
            roleDescription = "hypothetical role that legitimately needs media access",
            expectedPermissions = callerIdBaseline.expectedPermissions + MEDIA,
        )
        val subject = app(
            "com.example.mediarole",
            "MediaRole",
            baselinePermissions + MEDIA,
        )

        val withCallerBaseline = RuleEngine(signedRulepack()).evaluate(subject, callerIdBaseline)
        val withMediaBaseline = RuleEngine(signedRulepack()).evaluate(subject, galleryLikeBaseline)

        assertTrue(
            "photo access must be flagged under the caller-ID baseline",
            withCallerBaseline.any { it.ruleId == "caller-media-permission-mismatch" },
        )
        assertFalse(
            "the same observation must NOT be flagged when the baseline expects it",
            withMediaBaseline.any { it.ruleId == "caller-media-permission-mismatch" },
        )
    }

    // ---- RuleEngine ----

    @Test
    fun disabledRule_doesNotFire() {
        val pack = signedRulepack()
        val disabledIds = pack.rules.filterNot { it.enabled }.map { it.id }.toSet()
        assertTrue("the pack should retain disabled catalogue rules", disabledIds.isNotEmpty())

        val findings = RuleEngine(pack).evaluate(villainCaller(), callerIdBaseline)
        assertTrue(
            "no disabled rule may appear in findings",
            findings.none { it.ruleId in disabledIds },
        )
    }

    @Test
    fun enabledMatchingRules_fireWithCorrectPackageRuleAndEvidence() {
        val findings = RuleEngine(signedRulepack()).evaluate(villainCaller(), callerIdBaseline)

        assertTrue("VillainCaller must produce findings", findings.isNotEmpty())
        assertTrue(
            "every finding must attribute the right package",
            findings.all { it.packageName == DemoAppRegistry.VILLAIN_CALLER },
        )
        assertTrue(
            "every finding must name the app and its type",
            findings.all { it.appName == "VillainCaller" && it.appType == AppType.CALLER_ID },
        )
        assertTrue(
            "every finding must carry evidence and a reason",
            findings.all { it.evidence.isNotEmpty() && it.reason.isNotBlank() },
        )
        assertTrue(
            "confidence must be a sane percentage",
            findings.all { it.confidence in 1..100 },
        )

        val ids = findings.map { it.ruleId }.toSet()
        assertTrue(ids.contains("caller-media-permission-mismatch"))
        assertTrue(ids.contains("caller-package-visibility-mismatch"))
        assertTrue(ids.contains("caller-location-permission-mismatch"))
    }

    @Test
    fun compoundProfileRule_firesOnlyWhenEnoughIndependentMismatchesExist() {
        val engine = RuleEngine(signedRulepack())

        val oneMismatch = app(
            DemoAppRegistry.VILLAIN_CALLER,
            "VillainCaller",
            baselinePermissions + MEDIA,
        )
        assertFalse(
            "a single mismatch must not trip the compound rule",
            engine.evaluate(oneMismatch, callerIdBaseline)
                .any { it.ruleId == "caller-high-risk-profile" },
        )

        val compound = engine.evaluate(villainCaller(), callerIdBaseline)
            .firstOrNull { it.ruleId == "caller-high-risk-profile" }
        assertNotNull("three mismatches must trip the compound rule", compound)
        assertEquals(Severity.CRITICAL, compound!!.severity)
    }

    @Test
    fun ruleScopedToAnAppType_doesNotFireForAnotherType() {
        // Same permissions as VillainCaller, but classified as a GAME. Every demo rule is
        // scoped to CALLER_ID, so nothing may fire.
        val game = app(
            "com.example.game",
            "SomeGame",
            baselinePermissions + setOf(MEDIA, QUERY_ALL, LOCATION),
            appType = AppType.GAME,
        )
        val findings = RuleEngine(signedRulepack()).evaluate(game, callerIdBaseline)
        assertEquals("CALLER_ID-scoped rules must not fire for a GAME: $findings", 0, findings.size)
    }

    @Test
    fun grantStateIsRecordedAsEvidence_butDetectionUsesTheRequestedSet() {
        // Neither decoy ever requests its dangerous permissions at runtime, so they are
        // ungranted. A requested-but-ungranted capability is still a real profile mismatch.
        val ungranted = villainCaller()
        assertTrue(
            "detection must not depend on the permission being granted",
            RuleEngine(signedRulepack()).evaluate(ungranted, callerIdBaseline).isNotEmpty(),
        )

        val granted = app(
            DemoAppRegistry.VILLAIN_CALLER,
            "VillainCaller",
            baselinePermissions + MEDIA,
            granted = setOf(MEDIA),
        )
        val finding = RuleEngine(signedRulepack()).evaluate(granted, callerIdBaseline)
            .first { it.ruleId == "caller-media-permission-mismatch" }
        assertTrue(
            "grant state belongs in the evidence",
            finding.evidence.any { it.contains("granted") },
        )
    }

    // ---- Rulepack integrity on the production path ----

    @Test
    fun unverifiedRulepack_cannotBeUsed() {
        val bytes = asset("rulepack.json").readBytes()
        val tampered = bytes.copyOf().also { it[it.size / 2] = (it[it.size / 2].toInt() xor 1).toByte() }

        assertFalse(
            "a tampered pack must fail verification, so it never reaches the engine",
            RulepackVerifier.verify(
                tampered,
                asset("rulepack.sig").readText(),
                asset("rulepack_public.key").readText(),
            ),
        )
    }
}
