package com.thraksha.guardian

import com.thraksha.guardian.ai.AppRequestDetector
import com.thraksha.guardian.ai.IntentJsonValidator
import com.thraksha.guardian.ai.IntentSchema
import com.thraksha.guardian.automation.AutomationSafetyPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 10B validation proofs (guide §17). Pure Kotlin — these run off-device and prove
 * the gate independently of whatever the model happens to emit today.
 *
 * The point: even a fully compromised or jailbroken model can only hand Thraksha a
 * string. Everything below is what that string has to survive.
 */
class Phase10IntentValidationTest {

    private val apps = listOf("com.samsung.android.calendar", "com.google.android.apps.maps")

    private fun validate(raw: String?, requestedAnApp: Boolean = false) =
        IntentJsonValidator.validate(raw, apps, requestedAnApp)

    // ---- happy paths ----

    @Test
    fun wellFormedIntent_isAccepted() {
        assertEquals(
            IntentJsonValidator.Outcome.Intent("MEETING", 45, null),
            validate("""{"result":"INTENT","routine":"MEETING","durationMinutes":45,"targetApp":null}"""),
        )
    }

    @Test
    fun nullDuration_meansUnspecified_notZero() {
        assertEquals(
            IntentJsonValidator.Outcome.Intent("FOCUS", null, null),
            validate("""{"result":"INTENT","routine":"FOCUS","durationMinutes":null}"""),
        )
    }

    @Test
    fun omittedOptionalKeys_areTreatedAsUnspecified() {
        assertEquals(
            IntentJsonValidator.Outcome.Intent("DRIVING", null, null),
            validate("""{"result":"INTENT","routine":"DRIVING"}"""),
        )
    }

    @Test
    fun anOfferedApp_isCarried() {
        assertEquals(
            IntentJsonValidator.Outcome.Intent("DRIVING", 30, "com.google.android.apps.maps"),
            validate(
                """{"result":"INTENT","routine":"DRIVING","durationMinutes":30,"targetApp":"com.google.android.apps.maps"}""",
            ),
        )
    }

    // ---- refusals ----

    @Test
    fun clarifyAndUnsupported_carryTheirReasonCodes() {
        assertEquals(
            IntentJsonValidator.Outcome.Clarify("MISSING_TARGET_APP"),
            validate("""{"result":"CLARIFY","reasonCode":"MISSING_TARGET_APP"}"""),
        )
        assertEquals(
            IntentJsonValidator.Outcome.Unsupported("SECURITY_REQUEST"),
            validate("""{"result":"UNSUPPORTED","reasonCode":"SECURITY_REQUEST"}"""),
        )
    }

    @Test
    fun refusalsAreHonoured_evenWhenTheReasonCodeIsMissingOrWrong() {
        // A decline or a question is the safe direction. Getting the DECISION right but
        // the label wrong must not throw the decision away — the label only picks which
        // fixed sentence is shown. What matters is that nothing becomes actionable.
        listOf(
            """{"result":"CLARIFY","reasonCode":"WHATEVER"}""",
            """{"result":"CLARIFY"}""",
            """{"result":"CLARIFY","reasonCode":"SECURITY_REQUEST"}""",
        ).forEach { raw ->
            val outcome = validate(raw)
            assertTrue("$raw must stay a clarification", outcome is IntentJsonValidator.Outcome.Clarify)
            assertTrue(
                (outcome as IntentJsonValidator.Outcome.Clarify).reasonCode in IntentSchema.CLARIFY_REASONS,
            )
        }
        listOf(
            """{"result":"UNSUPPORTED","reasonCode":"NONSENSE"}""",
            """{"result":"UNSUPPORTED"}""",
        ).forEach { raw ->
            val outcome = validate(raw)
            assertTrue("$raw must stay a refusal", outcome is IntentJsonValidator.Outcome.Unsupported)
            assertTrue(
                (outcome as IntentJsonValidator.Outcome.Unsupported).reasonCode
                    in IntentSchema.UNSUPPORTED_REASONS,
            )
        }
    }

    @Test
    fun aRefusalIsNeverUpgradedToAnIntent_evenWithAttachedParameters() {
        // Observed on device: the model emits UNSUPPORTED *with* a routine and duration.
        // `result` decides; stray parameters must not make a refusal actionable.
        listOf(
            """{"result":"UNSUPPORTED","routine":"DRIVING","durationMinutes":10,"reasonCode":"DESTRUCTIVE_REQUEST"}""",
            """{"result":"CLARIFY","routine":"MEETING","durationMinutes":45,"targetApp":"com.samsung.android.calendar"}""",
        ).forEach { raw ->
            assertFalse(
                "$raw must never become an intent",
                validate(raw) is IntentJsonValidator.Outcome.Intent,
            )
        }
    }

    @Test
    fun junkReasonCodeOnIntent_isIgnored_becauseItCarriesNoAuthority() {
        // The model frequently attaches a leftover reasonCode to an INTENT. It selects a
        // UI string for refusals and nothing else, so it is not read on this branch —
        // while every field that CAN affect the device is still validated.
        assertEquals(
            IntentJsonValidator.Outcome.Intent("DRIVING", null, null),
            validate("""{"result":"INTENT","routine":"DRIVING","reasonCode":"UNSAFE_VALUE"}"""),
        )
    }

    // ---- parse-level rejections (guide §17) ----

    @Test
    fun proseAroundJson_isRejected_notScraped() {
        listOf(
            "Sure! Here you go: {\"result\":\"INTENT\"}",
            "```json\n{\"routine\":\"MEETING\"}\n```",
            "{\"result\":\"INTENT\"} Hope that helps!",
        ).forEach { raw ->
            assertTrue(
                "must reject prose-wrapped JSON: $raw",
                validate(raw) is IntentJsonValidator.Outcome.Rejected,
            )
        }
    }

    @Test
    fun malformedOrEmptyOutput_isRejected() {
        listOf(null, "", "   ", "not json", "{", "[]", "\"MEETING\"", "42").forEach { raw ->
            assertTrue("must reject: $raw", validate(raw) is IntentJsonValidator.Outcome.Rejected)
        }
    }

    // ---- contract-level rejections ----

    @Test
    fun unknownFields_areRejected_neverIgnored() {
        val outcome = validate("""{"result":"INTENT","routine":"MEETING","shellCommand":"rm -rf /"}""")
        assertTrue(outcome is IntentJsonValidator.Outcome.Rejected)
        assertTrue((outcome as IntentJsonValidator.Outcome.Rejected).reason.contains("shellCommand"))
    }

    @Test
    fun customRoutine_isNotModelSelectable() {
        val outcome = validate("""{"result":"INTENT","routine":"CUSTOM","durationMinutes":15}""")
        assertTrue(outcome is IntentJsonValidator.Outcome.Rejected)
        assertEquals("CONTRACT", (outcome as IntentJsonValidator.Outcome.Rejected).stage)
    }

    @Test
    fun unknownRoutine_isRejected() {
        listOf("GYM", "EXECUTE", "intent", "", "OK").forEach { label ->
            assertTrue(
                "label '$label' must be rejected",
                validate("""{"result":"INTENT","routine":"$label"}""") is IntentJsonValidator.Outcome.Rejected,
            )
        }
    }

    @Test
    fun intentWithoutRoutine_isRejected() {
        assertTrue(
            validate("""{"result":"INTENT","durationMinutes":30}""") is IntentJsonValidator.Outcome.Rejected,
        )
    }

    @Test
    fun outOfRangeDuration_becomesUnsafeValue_neverAnActionableIntent() {
        listOf(0, -30, 481, 540, 99_999, 100_000).forEach { minutes ->
            val outcome = validate("""{"result":"INTENT","routine":"FOCUS","durationMinutes":$minutes}""")
            assertTrue(
                "duration $minutes must never become an actionable intent",
                outcome !is IntentJsonValidator.Outcome.Intent,
            )
            assertEquals(IntentJsonValidator.Outcome.Unsupported("UNSAFE_VALUE"), outcome)
        }
    }

    @Test
    fun nonIntegerDuration_isRejected() {
        listOf("\"45\"", "45.5", "\"forty-five\"", "true", "[45]").forEach { value ->
            assertTrue(
                "duration $value must be rejected",
                validate("""{"result":"INTENT","routine":"MEETING","durationMinutes":$value}""")
                    is IntentJsonValidator.Outcome.Rejected,
            )
        }
    }

    @Test
    fun targetAppOutsideTheInjectedEnum_isRejected() {
        listOf(
            "com.attacker.payload", "com.thraksha.guardian", "com.spotify.music",
            "not a package", "intent://evil", "../../etc/passwd",
        ).forEach { pkg ->
            assertTrue(
                "app '$pkg' was never offered and must be rejected",
                validate("""{"result":"INTENT","routine":"MEETING","targetApp":"$pkg"}""")
                    is IntentJsonValidator.Outcome.Rejected,
            )
        }
    }

    // ---- the app-resolution rule (frozen contract §6) ----

    @Test
    fun anUnresolvedAppRequest_asksInsteadOfOpeningSomethingElse() {
        // The user asked to open something and no offered app was resolved: Thraksha asks
        // rather than launching whatever the planner's default candidate happens to be.
        val outcome = validate("""{"result":"INTENT","routine":"FOCUS","targetApp":null}""", requestedAnApp = true)
        assertEquals(IntentJsonValidator.Outcome.Clarify("MISSING_TARGET_APP"), outcome)
    }

    @Test
    fun aResolvedAppRequest_proceeds() {
        val outcome = validate(
            """{"result":"INTENT","routine":"MEETING","targetApp":"com.samsung.android.calendar"}""",
            requestedAnApp = true,
        )
        assertEquals(
            IntentJsonValidator.Outcome.Intent("MEETING", null, "com.samsung.android.calendar"),
            outcome,
        )
    }

    @Test
    fun appRequestDetector_spotsOpeningVerbs_andIgnoresPlainRoutineRequests() {
        listOf(
            "open some app", "launch my calendar", "driving mode, open maps",
            "pull up the navigation", "switch to my notes",
        ).forEach { assertTrue("should detect: $it", AppRequestDetector.requestsAnApp(it)) }

        listOf(
            "meeting mode for 45 minutes", "I need to focus", "I'm driving",
            "quiet please, I'm presenting",
        ).forEach { assertFalse("should not detect: $it", AppRequestDetector.requestsAnApp(it)) }
    }

    // ---- the schema/policy contract stays in sync ----

    @Test
    fun schemaBounds_matchTheUnmodifiedSafetyPolicy() {
        assertEquals(
            "the AI duration bound must never drift from AutomationSafetyPolicy",
            AutomationSafetyPolicy.DURATION_MIN_MINUTES, IntentSchema.DURATION_MIN,
        )
        assertEquals(AutomationSafetyPolicy.DURATION_MAX_MINUTES, IntentSchema.DURATION_MAX)
    }

    @Test
    fun modelSelectableRoutines_excludeCustom() {
        assertEquals(listOf("MEETING", "FOCUS", "DRIVING"), IntentSchema.ALLOWED_ROUTINES)
        assertTrue("CUSTOM" !in IntentSchema.ALLOWED_ROUTINES)
    }

    @Test
    fun generatedSchema_onlyOffersInstalledApps_andLeavesDurationUnbounded() {
        val schema = IntentSchema.schemaJson(apps)
        assertTrue(schema.contains("\"additionalProperties\": false"))
        assertTrue(schema.contains("com.samsung.android.calendar"))
        assertTrue(
            "an app that is not installed must never enter the schema",
            !schema.contains("com.spotify.music"),
        )
        // Bounds in the sampler coerce unsafe values into range instead of refusing them
        // (measured, eval iteration 1). The bound lives in validation.
        assertTrue(
            "duration must not be bounded in the schema",
            !schema.contains("\"minimum\"") && !schema.contains("\"maximum\""),
        )
        assertEquals(
            IntentJsonValidator.Outcome.Unsupported("UNSAFE_VALUE"),
            IntentJsonValidator.validate("""{"result":"INTENT","routine":"MEETING","durationMinutes":540}""", apps),
        )
    }

    @Test
    fun systemInstruction_neverPromisesDeviceControl() {
        val instruction = IntentSchema.systemInstruction(apps).lowercase()
        listOf("you can change", "you may execute", "you will run", "adb", "shell")
            .forEach { forbidden ->
                assertTrue(
                    "system instruction must not contain '$forbidden'",
                    !instruction.contains(forbidden),
                )
            }
    }
}
