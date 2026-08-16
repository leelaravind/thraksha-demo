package com.thraksha.guardian.ai

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * The ONE authoritative source of the on-device model file (guide §7, §8).
 *
 * Deliberate non-features:
 *  * **no fallback search** — if the pinned file is absent the state is MISSING; the
 *    repository never scans Downloads or a second directory;
 *  * **no alternative model** — a hash mismatch is ERROR, never "load it anyway";
 *  * **no sidecar digest** — the expected SHA-256 is compiled in, so it cannot be edited
 *    in step with a swapped model file;
 *  * **no network** — the model is developer-provisioned; nothing here can fetch.
 *
 * Delivery decision (PREP): `getExternalFilesDir("models")` — the app's own external
 * files directory. Readable by this app with **zero storage permissions**, writable by
 * `adb push`, and removed by Android on uninstall.
 */
object LocalModelRepository {

    private const val TAG = "LocalModelRepository"

    /**
     * The pinned artifact. Changing this triple is a **reviewed event**, never a silent
     * substitution (guide §27) — see
     * `temp/automation injection docs/00_index/PHASE10_MODEL_SWAP_RUNBOOK.md`.
     *
     * Swapped 2026-08-15 on an explicit owner decision, after Gemma 3 1B IT q4 failed the
     * 10B accuracy gate at 67 % against a ≥ 90 % target across eleven tuning iterations.
     * Three artifacts were then measured end-to-end on the S20 FE — see
     * `10a_runtime/PHASE10A_MODEL_COMPARISON.md`:
     *
     *   Gemma 3 1B q4   67 % / 68.4 % routine · 10.3 s · 1.9 GiB peak
     *   Gemma 4 E2B     79 % / 84.2 % routine · 10.1 s · 2.9 GiB peak  ← selected
     *   Gemma 4 E4B     80 % / 81.6 % routine · 22.2 s · 4.6 GiB peak
     *
     * E2B is the best of the three on every axis that matters: it is the most accurate per
     * unit latency, the only one that improves routine classification materially, and it
     * costs nothing over the 1B in response time. E4B buys one point of overall accuracy
     * for 2.2x the latency and is *worse* at routine classification.
     */
    const val MODEL_FILE_NAME = "gemma-4-E2B-it.litertlm"
    const val EXPECTED_SHA256 = "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c"
    const val EXPECTED_BYTES = 2_588_147_712L

    /** LiteRT-LM container magic — cheap structural check before the expensive hash. */
    private val CONTAINER_MAGIC = "LITERTLM".toByteArray(Charsets.US_ASCII)

    private val _state = MutableStateFlow(ModelState())
    val state: StateFlow<ModelState> = _state.asStateFlow()

    /** The single path. No other component may construct a model path. */
    fun modelFile(context: Context): File =
        File(context.applicationContext.getExternalFilesDir("models"), MODEL_FILE_NAME)

    /**
     * The SHA gate (guide §8): resolve → size → magic → SHA-256. Cheap checks first so a
     * missing or truncated file fails fast without hashing 557 MiB.
     *
     * Returns the resulting state and publishes it. Never throws.
     */
    suspend fun verify(context: Context): ModelState = withContext(Dispatchers.IO) {
        val file = modelFile(context)
        publish(ModelState(ModelPhase.VERIFYING, "Checking ${file.name}"))
        val started = System.currentTimeMillis()

        if (!file.exists()) {
            return@withContext publish(
                ModelState(
                    ModelPhase.MISSING,
                    "No model at ${file.absolutePath}",
                    verifyMillis = System.currentTimeMillis() - started,
                ),
            )
        }
        if (!file.canRead()) {
            return@withContext publish(
                ModelState(
                    ModelPhase.ERROR, "MODEL INVALID — file is not readable",
                    verifyMillis = System.currentTimeMillis() - started,
                ),
            )
        }
        if (file.length() != EXPECTED_BYTES) {
            return@withContext publish(
                ModelState(
                    ModelPhase.ERROR,
                    "MODEL INVALID — expected $EXPECTED_BYTES bytes, found ${file.length()}",
                    verifyMillis = System.currentTimeMillis() - started,
                ),
            )
        }
        if (!hasContainerMagic(file)) {
            return@withContext publish(
                ModelState(
                    ModelPhase.ERROR, "MODEL INVALID — not a LiteRT-LM container",
                    verifyMillis = System.currentTimeMillis() - started,
                ),
            )
        }

        val digest = runCatching { sha256(file) }
            .onFailure { Log.e(TAG, "Hashing failed", it) }
            .getOrNull()
            ?: return@withContext publish(
                ModelState(
                    ModelPhase.ERROR, "MODEL INVALID — checksum could not be computed",
                    verifyMillis = System.currentTimeMillis() - started,
                ),
            )

        val elapsed = System.currentTimeMillis() - started
        return@withContext if (digest.equals(EXPECTED_SHA256, ignoreCase = true)) {
            publish(ModelState(ModelPhase.READY, "Integrity verified", verifyMillis = elapsed))
        } else {
            // Never load an unexpected model, and never fall back to another one.
            publish(
                ModelState(
                    ModelPhase.ERROR,
                    "MODEL INVALID — checksum mismatch (expected ${EXPECTED_SHA256.take(12)}…, " +
                        "found ${digest.take(12)}…)",
                    verifyMillis = elapsed,
                ),
            )
        }
    }

    /** Used by the loader to move the state machine without re-running the gate. */
    internal fun publish(state: ModelState): ModelState {
        _state.value = state
        return state
    }

    internal fun current(): ModelState = _state.value

    private fun hasContainerMagic(file: File): Boolean = runCatching {
        file.inputStream().use { stream ->
            val head = ByteArray(CONTAINER_MAGIC.size)
            stream.read(head) == CONTAINER_MAGIC.size && head.contentEquals(CONTAINER_MAGIC)
        }
    }.getOrDefault(false)

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buffer = ByteArray(1 shl 20)
            while (true) {
                val read = stream.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
