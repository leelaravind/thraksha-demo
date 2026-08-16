package com.thraksha.guardian.ai

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ResponseFormat
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The ONLY component in Thraksha that touches the language model (guide §9).
 *
 * Structural boundary — this file deliberately imports **no** automation, executor,
 * security, policy or Android-control type. Its entire output is a `String` of JSON.
 * It cannot start a routine, and nothing here can reach an Android setting. The model's
 * text becomes device behaviour only after Phase 10B validation, Phase 9
 * `AutomationSafetyPolicy`, the deterministic planner, a rendered preview and an explicit
 * user START (guide §4, §26).
 *
 * Also structurally absent by design:
 *  * **no tool calling** — `ConversationConfig.tools` stays empty and
 *    `automaticToolCalling` is never enabled, so model output can never invoke a Kotlin
 *    function (guide §4 / PREP runtime doc §5);
 *  * **no network** — LiteRT-LM runs locally; there is no HTTP client here and no cloud
 *    fallback anywhere in the path (guide §30).
 */
object OnDeviceIntentModel {

    private const val TAG = "OnDeviceIntentModel"

    /** §33: one inference at a time. Overlapping sessions are not proven safe. */
    private val lock = Mutex()

    @Volatile
    private var engine: Engine? = null

    /** Held only for the duration of one request so [cancel] can interrupt it. */
    @Volatile
    private var inFlight: Conversation? = null

    /** Deterministic decoding — this is classification, not creative writing. */
    private val sampler = SamplerConfig(topK = 1, topP = 1.0, temperature = 0.0, seed = 1234)

    val isLoaded: Boolean get() = engine?.isInitialized() == true

    /**
     * SHA gate → runtime init (guide §8). Verification ALWAYS runs first; a mismatch
     * means the engine is never constructed.
     */
    /**
     * Chosen on measurement, not assumption (10A backend benchmark):
     *  * `Backend.GPU()` **initializes but cannot infer** on the S20 FE —
     *    `Can not find OpenCL library on this device`. It is therefore not a safe default.
     *  * 4 threads beat 8 (10.05 s vs 10.53 s mean) — big.LITTLE contention, not headroom.
     */
    private val defaultBackend: Backend get() = Backend.CPU(threadCount = 4)

    suspend fun load(context: Context, backend: Backend = defaultBackend): ModelState {
        val appContext = context.applicationContext
        return lock.withLock {
            engine?.let { existing ->
                if (existing.isInitialized()) {
                    // Re-publish LOADED rather than returning whatever the repository last
                    // published. `verify()` can be called independently (e.g. the UI
                    // re-checking integrity) and leaves the state at READY — which would
                    // otherwise be reported here while the engine is in fact live, so the
                    // UI would say "available" for a model that is already loaded.
                    val previous = LocalModelRepository.current()
                    return@withLock LocalModelRepository.publish(
                        ModelState(
                            ModelPhase.LOADED, "Already loaded",
                            previous.verifyMillis, previous.loadMillis,
                        ),
                    )
                }
            }

            val verified = LocalModelRepository.verify(appContext)
            if (verified.phase != ModelPhase.READY) return@withLock verified

            LocalModelRepository.publish(
                ModelState(ModelPhase.LOADING, "Initializing LiteRT-LM", verified.verifyMillis),
            )

            val path = LocalModelRepository.modelFile(appContext).absolutePath
            val started = System.currentTimeMillis()
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val created = Engine(
                        EngineConfig(
                            modelPath = path,
                            backend = backend,
                            cacheDir = appContext.cacheDir.absolutePath,
                        ),
                    )
                    created.initialize()
                    created
                }
            }
            val elapsed = System.currentTimeMillis() - started

            result.fold(
                onSuccess = { created ->
                    if (created.isInitialized()) {
                        engine = created
                        LocalModelRepository.publish(
                            ModelState(
                                ModelPhase.LOADED, "Loaded in ${elapsed} ms",
                                verified.verifyMillis, elapsed,
                            ),
                        )
                    } else {
                        runCatching { created.close() }
                        LocalModelRepository.publish(
                            ModelState(
                                ModelPhase.ERROR,
                                "MODEL UNAVAILABLE — runtime reported not initialized",
                                verified.verifyMillis,
                            ),
                        )
                    }
                },
                onFailure = { error ->
                    Log.e(TAG, "Engine initialization failed", error)
                    val unsupported = error is UnsatisfiedLinkError ||
                        error.cause is UnsatisfiedLinkError
                    LocalModelRepository.publish(
                        ModelState(
                            if (unsupported) ModelPhase.UNSUPPORTED else ModelPhase.ERROR,
                            if (unsupported) {
                                "ON-DEVICE AI NOT SUPPORTED ON THIS DEVICE — native library unavailable"
                            } else {
                                "MODEL UNAVAILABLE — ${error.javaClass.simpleName}: ${error.message}"
                            },
                            verified.verifyMillis,
                        ),
                    )
                },
            )
        }
    }

    /**
     * One utterance → one constrained-JSON string. The raw string is returned verbatim;
     * validation is the caller's job and is never done here.
     *
     * A fresh [Conversation] per request: intent extraction is stateless, chat history is
     * unwanted, and it keeps the 4096-token budget clean.
     */
    suspend fun generateIntentJson(
        userText: String,
        allowedApps: List<String>,
    ): InferenceResult = lock.withLock {
        val active = engine
        if (active == null || !active.isInitialized()) {
            return@withLock InferenceResult(error = "MODEL_NOT_LOADED")
        }
        val started = System.currentTimeMillis()
        withContext(Dispatchers.IO) {
            var conversation: Conversation? = null
            try {
                conversation = active.createConversation(
                    ConversationConfig(
                        systemInstruction = Contents.of(
                            IntentSchema.systemInstruction(allowedApps),
                        ),
                        samplerConfig = sampler,
                        maxOutputToken = IntentSchema.MAX_OUTPUT_TOKENS,
                        enableResponseFormat = true,
                    ),
                )
                inFlight = conversation
                val reply = conversation.sendMessage(
                    userText,
                    responseFormat = ResponseFormat.json(IntentSchema.schemaJson(allowedApps)),
                )
                val benchmark = benchmarkOf(conversation)
                InferenceResult(
                    rawJson = textOf(reply.contents),
                    latencyMs = System.currentTimeMillis() - started,
                    timeToFirstTokenSeconds = benchmark?.timeToFirstTokenInSecond,
                    prefillTokensPerSecond = benchmark?.lastPrefillTokensPerSecond,
                    decodeTokensPerSecond = benchmark?.lastDecodeTokensPerSecond,
                    prefillTokens = benchmark?.lastPrefillTokenCount,
                    decodeTokens = benchmark?.lastDecodeTokenCount,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                Log.w(TAG, "Inference failed", error)
                InferenceResult(
                    error = "${error.javaClass.simpleName}: ${error.message}",
                    latencyMs = System.currentTimeMillis() - started,
                )
            } finally {
                inFlight = null
                runCatching { conversation?.close() }
            }
        }
    }

    /** §33: cancellation where the runtime supports it. Safe to call at any time. */
    fun cancel() {
        runCatching { inFlight?.cancelProcess() }
            .onFailure { Log.w(TAG, "Cancel failed", it) }
    }

    /** Releases native memory. The state machine returns to READY (integrity still holds). */
    suspend fun unload() = lock.withLock {
        runCatching { engine?.close() }.onFailure { Log.w(TAG, "Engine close failed", it) }
        engine = null
        if (LocalModelRepository.current().phase == ModelPhase.LOADED) {
            LocalModelRepository.publish(
                ModelState(
                    ModelPhase.READY, "Unloaded — integrity still verified",
                    LocalModelRepository.current().verifyMillis,
                ),
            )
        }
    }

    /**
     * `Conversation.getBenchmarkInfo()` is `@ExperimentalApi` in litertlm 0.16.0. It is
     * measurement-only (init time, TTFT, tokens/sec) and never affects behaviour, so a
     * failure here degrades to null metrics rather than a failed inference.
     */
    @OptIn(com.google.ai.edge.litertlm.ExperimentalApi::class)
    private fun benchmarkOf(conversation: Conversation) =
        runCatching { conversation.getBenchmarkInfo() }.getOrNull()

    /**
     * `Contents.contents` is marked `@ExperimentalApi` in litertlm 0.16.0. The opt-in is
     * scoped to this one accessor rather than applied module-wide, so a future breaking
     * change surfaces here and nowhere else. The alternative — parsing `toString()` — would
     * be strictly worse.
     */
    @OptIn(com.google.ai.edge.litertlm.ExperimentalApi::class)
    private fun textOf(contents: Contents): String =
        contents.contents
            .filterIsInstance<Content.Text>()
            .joinToString("") { it.text }
            .trim()

    /**
     * One inference outcome. [rawJson] is untrusted model output — it has passed no gate
     * at this point and must not be shown to the user or acted upon.
     */
    data class InferenceResult(
        val rawJson: String? = null,
        val error: String? = null,
        val latencyMs: Long = 0,
        val timeToFirstTokenSeconds: Double? = null,
        val prefillTokensPerSecond: Double? = null,
        val decodeTokensPerSecond: Double? = null,
        val prefillTokens: Int? = null,
        val decodeTokens: Int? = null,
    ) {
        val succeeded: Boolean get() = error == null && !rawJson.isNullOrBlank()
    }
}
