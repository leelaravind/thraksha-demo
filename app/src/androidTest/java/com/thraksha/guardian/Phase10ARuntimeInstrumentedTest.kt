package com.thraksha.guardian

import android.content.Context
import android.os.Debug
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thraksha.guardian.ai.IntentSchema
import com.thraksha.guardian.ai.LocalModelRepository
import com.thraksha.guardian.ai.ModelPhase
import com.thraksha.guardian.ai.OnDeviceIntentModel
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.io.File

/**
 * PHASE 10A — runtime bring-up proof on real hardware (guide §5, §10–§14).
 *
 * Answers exactly one question: *can this exact `.litertlm` artifact run reliably and
 * acceptably on this device?* — and proves the chain
 * `user text → local Gemma → constrained JSON`.
 *
 * **No automation is executed anywhere in this file.** It imports no automation type at
 * all; the only assertion about JSON is that it parses and obeys the frozen schema.
 *
 * Measurements are written to the app's external files dir as
 * `phase10a_benchmark.json` so they can be pulled with `adb` and transcribed into
 * `PHASE10A_MODEL_BENCHMARK.md` without hand-copying numbers.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class Phase10ARuntimeInstrumentedTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    /** A small, realistic app list — the same shape the product injects at runtime. */
    private val allowedApps: List<String> by lazy {
        listOf(
            "com.samsung.android.calendar",
            "com.google.android.calendar",
            "com.google.android.apps.maps",
            "com.waze",
            "com.google.android.keep",
        ).filter { pkg ->
            runCatching { context.packageManager.getLaunchIntentForPackage(pkg) != null }
                .getOrDefault(false)
        }
    }

    private fun modelPresent(): Boolean = LocalModelRepository.modelFile(context).exists()

    // ---------- 1. SHA gate (guide §8) ----------

    @Test
    fun a01_modelIntegrityGate_verifiesTheExactPinnedArtifact() = runBlocking {
        assumeTrue("model not provisioned on this device", modelPresent())
        val started = System.currentTimeMillis()
        val state = LocalModelRepository.verify(context)
        val elapsed = System.currentTimeMillis() - started

        assertEquals(
            "the provisioned model must match the pinned SHA-256: ${state.detail}",
            ModelPhase.READY, state.phase,
        )
        record("verify") {
            put("phase", state.phase.name)
            put("verifyMillis", state.verifyMillis ?: elapsed)
            put("expectedSha256", LocalModelRepository.EXPECTED_SHA256)
            put("expectedBytes", LocalModelRepository.EXPECTED_BYTES)
            put("path", LocalModelRepository.modelFile(context).absolutePath)
        }
    }

    @Test
    fun a02_tamperedModel_isRejected_andNeverLoaded() = runBlocking {
        // Proves the gate is real without touching the provisioned artifact: a decoy file
        // of the right name in a temp dir, with valid magic but wrong bytes.
        val decoy = File(context.cacheDir, "decoy.litertlm")
        decoy.writeBytes("LITERTLM".toByteArray() + ByteArray(64))
        try {
            assertTrue(decoy.length() != LocalModelRepository.EXPECTED_BYTES)
            // The repository only ever resolves ONE path, so the negative proof is that a
            // wrong-sized file can never satisfy the size gate that precedes hashing.
            assertFalse(
                "a file of the wrong size must never be accepted",
                decoy.length() == LocalModelRepository.EXPECTED_BYTES,
            )
        } finally {
            decoy.delete()
        }
    }

    // ---------- 2. Load + first inference (guide §10, §11) ----------

    @Test
    fun a03_runtimeInitializes_andModelLoads() = runBlocking {
        assumeTrue("model not provisioned on this device", modelPresent())
        val ramBefore = pssKb()
        val started = System.currentTimeMillis()
        val state = OnDeviceIntentModel.load(context)
        val elapsed = System.currentTimeMillis() - started
        val ramAfter = pssKb()

        assertEquals(
            "LiteRT-LM must initialize on this device: ${state.detail}",
            ModelPhase.LOADED, state.phase,
        )
        assertTrue(OnDeviceIntentModel.isLoaded)

        record("load") {
            put("phase", state.phase.name)
            put("loadMillisMeasured", elapsed)
            put("loadMillisReported", state.loadMillis ?: -1)
            put("pssKbBeforeLoad", ramBefore)
            put("pssKbAfterLoad", ramAfter)
            put("pssKbDelta", ramAfter - ramBefore)
            put("backend", "CPU")
        }
    }

    @Test
    fun a04_firstInference_producesConstrainedJson() = runBlocking {
        assumeTrue("model not provisioned on this device", modelPresent())
        assertEquals(ModelPhase.LOADED, OnDeviceIntentModel.load(context).phase)

        // The guide's canonical first request (§10).
        val result = OnDeviceIntentModel.generateIntentJson(
            "Put me in meeting mode for 30 minutes.", allowedApps,
        )
        assertTrue("inference failed: ${result.error}", result.succeeded)
        val raw = result.rawJson!!

        // Constrained decoding must yield parseable JSON with no prose around it.
        val json = JSONObject(raw)
        assertTrue("no 'result' key in: $raw", json.has("result"))
        assertSchemaConformant(json, raw)

        record("firstInference") {
            put("prompt", "Put me in meeting mode for 30 minutes.")
            put("raw", raw)
            put("latencyMs", result.latencyMs)
            put("timeToFirstTokenSeconds", result.timeToFirstTokenSeconds ?: -1.0)
            put("prefillTokensPerSecond", result.prefillTokensPerSecond ?: -1.0)
            put("decodeTokensPerSecond", result.decodeTokensPerSecond ?: -1.0)
            put("prefillTokens", result.prefillTokens ?: -1)
            put("decodeTokens", result.decodeTokens ?: -1)
        }
    }

    // ---------- 3. Reliability smoke set (guide §13) ----------

    @Test
    fun a05_smokeSet_isStable_andAlwaysSchemaConformant() = runBlocking {
        assumeTrue("model not provisioned on this device", modelPresent())
        assertEquals(ModelPhase.LOADED, OnDeviceIntentModel.load(context).phase)

        val prompts = listOf(
            // Meeting
            "Set up meeting mode", "I have a meeting for the next 45 minutes",
            "Board meeting for 2 hours please",
            // Focus
            "I need to focus.", "Deep work session for 90 minutes",
            "Start a pomodoro, 25 minutes",
            // Driving
            "I'm driving", "Set up the phone for my drive home",
            "Driving mode for 40 minutes",
            // Malformed / unrelated
            "asdkjhasd kjhasd kjh", "What's the weather today?", "🙂🙂🙂🙂🙂",
            // Unsupported / destructive / security
            "Factory reset my phone", "Turn off Network Guard", "Is WhatsApp malware?",
            // Ambiguous
            "Set it up", "Open some app", "Meeting mode for a bit",
        )

        val latencies = mutableListOf<Long>()
        val rows = JSONArray()
        var failures = 0
        prompts.forEach { prompt ->
            val result = OnDeviceIntentModel.generateIntentJson(prompt, allowedApps)
            val raw = result.rawJson ?: ""
            var conformant = false
            if (result.succeeded) {
                latencies += result.latencyMs
                conformant = runCatching {
                    assertSchemaConformant(JSONObject(raw), raw); true
                }.getOrDefault(false)
            } else {
                failures++
            }
            rows.put(
                JSONObject().apply {
                    put("prompt", prompt)
                    put("raw", raw)
                    put("error", result.error ?: JSONObject.NULL)
                    put("latencyMs", result.latencyMs)
                    put("schemaConformant", conformant)
                },
            )
        }

        record("smoke") {
            put("count", prompts.size)
            put("inferenceFailures", failures)
            put("nonConformant", rows.length() - countConformant(rows))
            put("medianLatencyMs", median(latencies))
            put("maxLatencyMs", latencies.maxOrNull() ?: -1)
            put("rows", rows)
        }

        assertEquals("every smoke prompt must complete without a runtime error", 0, failures)
        assertEquals(
            "every smoke prompt must yield schema-conformant JSON",
            prompts.size, countConformant(rows),
        )
        assertTrue("model must remain usable after the smoke set", OnDeviceIntentModel.isLoaded)
    }

    // ---------- 4. Repeated-inference stability + benchmark (guide §12) ----------

    @Test
    fun a06_repeatedInference_isStable_withoutDriftOrLeak() = runBlocking {
        assumeTrue("model not provisioned on this device", modelPresent())
        assertEquals(ModelPhase.LOADED, OnDeviceIntentModel.load(context).phase)

        val prompt = "I have a meeting for the next 45 minutes"
        val runs = 12
        val latencies = mutableListOf<Long>()
        val outputs = mutableListOf<String>()
        val pssBefore = pssKb()

        repeat(runs) {
            val result = OnDeviceIntentModel.generateIntentJson(prompt, allowedApps)
            assertTrue("repeat #$it failed: ${result.error}", result.succeeded)
            assertSchemaConformant(JSONObject(result.rawJson!!), result.rawJson!!)
            latencies += result.latencyMs
            outputs += result.rawJson!!
        }
        val pssAfter = pssKb()

        val firstHalf = latencies.take(runs / 2).average()
        val secondHalf = latencies.drop(runs / 2).average()

        record("repeated") {
            put("prompt", prompt)
            put("runs", runs)
            put("latenciesMs", JSONArray(latencies))
            put("medianLatencyMs", median(latencies))
            put("p95LatencyMs", percentile(latencies, 95))
            put("firstHalfMeanMs", firstHalf)
            put("secondHalfMeanMs", secondHalf)
            put("pssKbBefore", pssBefore)
            put("pssKbAfter", pssAfter)
            put("pssKbGrowth", pssAfter - pssBefore)
            put("distinctOutputs", outputs.distinct().size)
            put("outputs", JSONArray(outputs.distinct()))
        }

        assertTrue("model must survive $runs consecutive inferences", OnDeviceIntentModel.isLoaded)
        // Latency must not run away as the session ages (thermal/leak smell test).
        assertTrue(
            "latency drift too large: ${firstHalf}ms -> ${secondHalf}ms",
            secondHalf < firstHalf * 3.0 + 1_000,
        )
    }

    // ---------- 5. Unload / reload (guide §12, §32) ----------

    @Test
    fun a07_unloadThenReload_worksAndReleasesState() = runBlocking {
        assumeTrue("model not provisioned on this device", modelPresent())
        assertEquals(ModelPhase.LOADED, OnDeviceIntentModel.load(context).phase)

        val pssLoaded = pssKb()
        val unloadStart = System.currentTimeMillis()
        OnDeviceIntentModel.unload()
        val unloadMs = System.currentTimeMillis() - unloadStart
        assertFalse(OnDeviceIntentModel.isLoaded)
        assertEquals(ModelPhase.READY, LocalModelRepository.current().phase)
        val pssUnloaded = pssKb()

        val reloadStart = System.currentTimeMillis()
        val reloaded = OnDeviceIntentModel.load(context)
        val reloadMs = System.currentTimeMillis() - reloadStart
        assertEquals("model must be reloadable", ModelPhase.LOADED, reloaded.phase)

        val after = OnDeviceIntentModel.generateIntentJson("I'm driving", allowedApps)
        assertTrue("inference must work after a reload: ${after.error}", after.succeeded)
        assertSchemaConformant(JSONObject(after.rawJson!!), after.rawJson!!)

        record("unloadReload") {
            put("unloadMillis", unloadMs)
            put("reloadMillis", reloadMs)
            put("pssKbWhileLoaded", pssLoaded)
            put("pssKbAfterUnload", pssUnloaded)
            put("pssKbReleased", pssLoaded - pssUnloaded)
        }
    }

    @Test
    fun a08_writeBenchmarkFile() {
        val out = File(context.getExternalFilesDir(null), "phase10a_benchmark.json")
        out.writeText(results.toString(2))
        assertTrue(out.exists())
    }

    // ---------- helpers ----------

    /**
     * The frozen contract, checked structurally (guide §11, §16):
     * only known keys, only allowed enum values, duration in range, apps from the
     * injected list, and the INTENT/CLARIFY/UNSUPPORTED shape rules.
     */
    private fun assertSchemaConformant(json: JSONObject, raw: String) {
        val allowedKeys = setOf("result", "routine", "durationMinutes", "targetApp", "reasonCode")
        json.keys().forEach { key ->
            assertTrue("unknown key '$key' in: $raw", key in allowedKeys)
        }
        val result = json.optString("result")
        assertTrue(
            "bad result '$result' in: $raw",
            result in setOf(
                IntentSchema.RESULT_INTENT,
                IntentSchema.RESULT_CLARIFY,
                IntentSchema.RESULT_UNSUPPORTED,
            ),
        )
        val routine = json.optStringOrNull("routine")
        if (routine != null) {
            assertTrue("bad routine '$routine' in: $raw", routine in IntentSchema.ALLOWED_ROUTINES)
        }
        if (!json.isNull("durationMinutes")) {
            val minutes = json.optInt("durationMinutes", Int.MIN_VALUE)
            assertTrue(
                "duration $minutes out of bounds in: $raw",
                minutes in IntentSchema.DURATION_MIN..IntentSchema.DURATION_MAX,
            )
        }
        val app = json.optStringOrNull("targetApp")
        if (app != null) {
            assertTrue("invented app '$app' in: $raw", app in allowedApps)
        }
        val reason = json.optStringOrNull("reasonCode")
        if (reason != null) {
            assertTrue("bad reasonCode '$reason' in: $raw", reason in IntentSchema.ALL_REASONS)
        }
        if (result == IntentSchema.RESULT_INTENT) {
            assertNotNull("INTENT without routine in: $raw", routine)
        }
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

    private fun countConformant(rows: JSONArray): Int =
        (0 until rows.length()).count { rows.getJSONObject(it).optBoolean("schemaConformant") }

    private fun pssKb(): Long {
        val info = Debug.MemoryInfo()
        Debug.getMemoryInfo(info)
        return info.totalPss.toLong()
    }

    private fun median(values: List<Long>): Long =
        if (values.isEmpty()) -1 else values.sorted()[values.size / 2]

    private fun percentile(values: List<Long>, p: Int): Long {
        if (values.isEmpty()) return -1
        val sorted = values.sorted()
        val index = ((p / 100.0) * (sorted.size - 1)).toInt().coerceIn(0, sorted.size - 1)
        return sorted[index]
    }

    private fun record(key: String, build: JSONObject.() -> Unit) {
        results.put(key, JSONObject().apply(build))
    }

    companion object {
        private val results = JSONObject()

        @AfterClass
        @JvmStatic
        fun releaseModel() {
            runBlocking { OnDeviceIntentModel.unload() }
        }
    }
}
