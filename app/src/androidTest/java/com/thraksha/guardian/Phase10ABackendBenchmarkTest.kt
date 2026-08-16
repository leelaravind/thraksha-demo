package com.thraksha.guardian

import android.content.Context
import android.os.Debug
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.ai.edge.litertlm.Backend
import com.thraksha.guardian.ai.LocalModelRepository
import com.thraksha.guardian.ai.ModelPhase
import com.thraksha.guardian.ai.OnDeviceIntentModel
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * PHASE 10A — backend comparison (guide §12: "supported backends/delegates").
 *
 * Chooses CPU vs GPU on evidence rather than assumption. Writes
 * `phase10a_backends.json` for transcription into the benchmark document.
 *
 * No automation, no execution — inference timing only.
 */
@RunWith(AndroidJUnit4::class)
class Phase10ABackendBenchmarkTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val allowedApps = listOf(
        "com.samsung.android.calendar",
        "com.google.android.apps.maps",
    )

    private val prompts = listOf(
        "Set up meeting mode",
        "I need to focus for 30 minutes",
        "I'm driving",
    )

    @Test
    fun compareBackends() = runBlocking {
        assumeTrue(
            "model not provisioned",
            LocalModelRepository.modelFile(context).exists(),
        )

        val report = JSONObject()
        listOf("CPU4" to Backend.CPU(threadCount = 4), "CPU8" to Backend.CPU(threadCount = 8), "GPU" to Backend.GPU())
            .forEach { (name, backend) ->
                OnDeviceIntentModel.unload()
                val entry = JSONObject().put("backend", name)
                val loadStart = System.currentTimeMillis()
                val state = runCatching { OnDeviceIntentModel.load(context, backend) }
                    .getOrElse { error ->
                        entry.put("loadError", "${error.javaClass.simpleName}: ${error.message}")
                        report.put(name, entry)
                        return@forEach
                    }
                entry.put("loadMillis", System.currentTimeMillis() - loadStart)
                entry.put("phase", state.phase.name)
                entry.put("detail", state.detail)

                if (state.phase != ModelPhase.LOADED) {
                    report.put(name, entry)
                    return@forEach
                }
                entry.put("pssKbLoaded", pssKb())

                val latencies = JSONArray()
                val outputs = JSONArray()
                var failures = 0
                prompts.forEach { prompt ->
                    val result = OnDeviceIntentModel.generateIntentJson(prompt, allowedApps)
                    if (result.succeeded) {
                        latencies.put(result.latencyMs)
                        outputs.put(result.rawJson)
                    } else {
                        failures++
                        outputs.put("ERROR: ${result.error}")
                    }
                }
                entry.put("latenciesMs", latencies)
                entry.put("failures", failures)
                entry.put("outputs", outputs)
                val values = (0 until latencies.length()).map { latencies.getLong(it) }
                entry.put("meanLatencyMs", if (values.isEmpty()) -1 else values.average())
                report.put(name, entry)
            }

        OnDeviceIntentModel.unload()
        File(context.getExternalFilesDir(null), "phase10a_backends.json")
            .writeText(report.toString(2))
    }

    private fun pssKb(): Long {
        val info = Debug.MemoryInfo()
        Debug.getMemoryInfo(info)
        return info.totalPss.toLong()
    }
}
