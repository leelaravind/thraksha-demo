# PHASE 10 PREP — LITERT-LM RUNTIME SELECTION

**Date:** 2026-08-15
**Guide:** `temp/THRAKSHA_DEMO_IMPLEMENTATION_PHASE_10_PREP.md` §6 (authoritative)
**Method:** official Google Maven metadata + the published AAR itself + official docs.
No JAR/AAR was taken from an unofficial source. Every API signature below was read out
of the **official artifact's own `classes.jar`** with `javap`, not from memory.

---

## 1. Selected dependency

```kotlin
// gradle/libs.versions.toml
litertlm = "0.16.0"
litertlm-android = { group = "com.google.ai.edge.litertlm", name = "litertlm-android", version.ref = "litertlm" }

// app/build.gradle.kts
implementation(libs.litertlm.android)      // com.google.ai.edge.litertlm:litertlm-android:0.16.0
```

| Field | Value |
|---|---|
| Group | `com.google.ai.edge.litertlm` |
| Artifact | `litertlm-android` |
| **Pinned version** | **`0.16.0`** |
| Packaging | `aar` |
| Repository | **Google Maven** (`google()`) — already declared in `settings.gradle.kts` |
| Verified URL | `https://dl.google.com/dl/android/maven2/com/google/ai/edge/litertlm/litertlm-android/0.16.0/` |
| Runtime license | Apache-2.0 (declared in the POM) |
| Upstream project | <https://github.com/google-ai-edge/LiteRT-LM> |

**Pin the exact version — do not use `latest.release`.** The official quick-start shows
`latest.release`; that is unacceptable for a frozen investor-demo baseline because the
runtime could change under the demo without a code change.

### Version evidence (Google Maven `maven-metadata.xml`, fetched 2026-08-15)

`<latest>0.16.0</latest>`, `<release>0.16.0</release>`, `lastUpdated 20260811174405`.
Published versions: `0.0.0-alpha06, 0.8.0, 0.9.0-alpha01…06, 0.9.0-beta, 0.9.0, 0.10.0,
0.10.2, 0.11.0-rc1, 0.11.0, 0.12.0, 0.13.0, 0.13.1, 0.14.0, 0.15.0, 0.16.0`.

`0.16.0` is the newest release (2026-08-11). **Documented fallback:** if `0.16.0`
misbehaves during the first on-device bring-up, step down to **`0.15.0`** and record the
reason — do not change the *model* to chase a runtime problem (guide §27).

Note the library is pre-1.0. Treat the API as still moving: pin it, and re-read this
document before any version bump.

### Transitive dependencies (from the official POM)

| Dependency | Version | Interaction with this project |
|---|---|---|
| `com.google.code.gson:gson` | 2.13.2 | new to the project; used internally by the runtime for tool/JSON plumbing. Thraksha keeps using **kotlinx-serialization** for its own parsing — do not introduce a second JSON style in Thraksha code. |
| `org.jetbrains.kotlin:kotlin-reflect` | 2.2.21 | project Kotlin is **2.2.20** → a 2.2.21 reflect artifact arrives transitively. Same minor, so expected to be fine; **verify at first build** and, if Gradle warns, align explicitly. |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | 1.9.0 | project currently pins **1.8.1** → Gradle will resolve up to **1.9.0**. This is a real, if small, change to an existing Phase 9 dependency and must be re-verified by re-running the Phase 9 suites when the dependency is added. |

## 2. Platform requirements

| Requirement | Value | Thraksha status |
|---|---|---|
| `minSdkVersion` of the AAR | **24** (read verbatim from the AAR's `AndroidManifest.xml`) | project `minSdk = 26` → **compatible** ✅ |
| Bundled ABIs | **`arm64-v8a`** (21.5 MB `liblitertlm_jni.so`) and **`x86_64`** (25.7 MB) — *only these two* | S20 FE is `arm64-v8a` ✅ · `thraksha_do` AVD is `x86_64` ✅ |
| Not supported | `armeabi-v7a`, `x86` — **no 32-bit support** | acceptable; both target devices are 64-bit |
| AAR download size | 20 192 711 bytes (≈19.3 MiB) | see APK-size note in §7 |
| `compileSdk`/`targetSdk` constraint | none imposed by the AAR | project is 36 ✅ |

> **Consequence for the build:** because only two ABIs ship, the debug APK will grow by
> the native library for the installed ABI (~21 MB on the phone). If both ABIs end up in
> one universal APK the growth is ~47 MB. Use ABI splits or an `abiFilters` of
> `arm64-v8a` + `x86_64` for demo builds.

## 3. Model/runtime pairing — is THIS artifact supported?

**Yes, on structural evidence:**

| Evidence | Finding |
|---|---|
| Container magic | file begins `LITERTLM` — the LiteRT-LM bundle format this runtime consumes |
| Container version | `1.0.0` |
| `model_type` metadata | `TF_LITE_PREFILL_DECODE` — the prefill/decode TFLite graph pair LiteRT-LM executes |
| `author` metadata | `The ODML Authors` — the same authorship as the runtime |
| Embedded tokenizer | Gemma control tokens `<start_of_turn>`, `<end_of_turn>` → Gemma IT chat template, which `Conversation` renders |
| API contract | `EngineConfig(modelPath = "…​.litertlm")` — the runtime loads a `.litertlm` **file path**, exactly our artifact type |

**Not yet established:** that this specific bundle initialises on this specific phone.
That claim is deliberately withheld — see §8 and guide §7
("No model execution claim until runtime initialization succeeds on the phone").

## 4. API surface — read from `litertlm-android-0.16.0.aar/classes.jar`

### 4.1 Initialization

```kotlin
public final class EngineConfig(
    modelPath: String,
    backend: Backend?,          // main/text backend
    visionBackend: Backend?,
    audioBackend: Backend?,
    maxNumTokens: Integer?,
    maxNumImages: Integer?,
    cacheDir: String?,
)

public final class Engine(engineConfig: EngineConfig) : AutoCloseable {
    fun initialize()                 // BLOCKING — see threading, §6
    fun isInitialized(): Boolean
    fun createConversation(config: ConversationConfig = …): Conversation
    fun createSession(config: SessionConfig = …): Session
    override fun close()
}
```

Backends (`Backend` is a sealed-style abstract class):

| Backend | Constructor | Notes |
|---|---|---|
| `Backend.CPU(threadCount: Int?, numOfThreads: Int?)` | default | `numOfThreads` is deprecated-annotated; use `threadCount` |
| `Backend.GPU()` | no args | Adreno 650 on the S20 FE is a candidate — **measure before choosing** |
| `Backend.NPU(nativeLibraryDir: String)` | requires vendor native libs | **not used** — needs vendor QNN/NPU libraries we do not ship |

`Capabilities(modelPath).hasSpeculativeDecodingSupport()` (also `AutoCloseable`) can be
queried against a model file without constructing an `Engine`.

### 4.2 Conversation (recommended path — applies the Gemma chat template)

```kotlin
public final class ConversationConfig(
    systemInstruction: Contents?,          // ← Thraksha's frozen system prompt goes here
    initialMessages: List<Message>,
    tools: List<ToolProvider>,             // ← MUST stay EMPTY (see §5)
    samplerConfig: SamplerConfig?,
    …,
    maxOutputToken: Integer?,              // ← cap the JSON reply
    thinkingConfig: ThinkingConfig?,
    enableResponseFormat: Boolean,         // ← must be true for constrained JSON
)

public final class Conversation : AutoCloseable {
    fun sendMessage(text: String, …, responseFormat: ResponseFormat?): Message   // blocking
    fun sendMessageAsync(text: String, callback: MessageCallback, …)             // callback
    fun sendMessageAsync(text: String, …): Flow<Message>                         // coroutine Flow
    fun cancelProcess()
    fun getBenchmarkInfo(): BenchmarkInfo
    fun getTokenCount(): Int
    fun renderMessageIntoString(m: Message, …): String   // useful for prompt-budget checks
    fun renderPrefaceIntoString(): String
    override fun close()
}

public enum class Role { SYSTEM, USER, MODEL, TOOL }
public final class Message(role: Role, contents: Contents, toolCalls: List<ToolCall>, channels: Map<String,String>)
```

### 4.3 Session (lower-level, no chat template)

```kotlin
public final class Session : AutoCloseable {
    fun runPrefill(inputs: List<InputData>)
    fun runDecode(): String
    fun generateContent(inputs: List<InputData>): String
    fun generateContentStream(inputs: List<InputData>, cb: ResponseCallback)
    fun cancelProcess()
}
```

**Thraksha uses `Conversation`, not `Session`** — the model is a Gemma *IT* checkpoint
whose behaviour depends on the `<start_of_turn>` template that `Conversation` applies.
Hand-rolling the template with `Session` would be a silent correctness risk.

### 4.4 Structured output — the decisive capability

```kotlin
public final class ResponseFormat(type: Type, schemaOrPattern: String) {
    enum class Type { REGEX, JSON_OBJECT }
    companion object {
        fun json(schema: String): ResponseFormat
        fun json(schema: Map<String, Any?>): ResponseFormat
        fun regex(pattern: String): ResponseFormat
    }
}
```

This is **constrained decoding**, not prompt-based hoping: the runtime restricts token
sampling to strings matching the supplied JSON schema (or regex). It is the reason the
guide's "do not rely on regex extraction from arbitrary paragraphs" (§12) is
satisfiable here.

Requirements to make it effective:

1. `ConversationConfig.enableResponseFormat = true`;
2. pass `ResponseFormat.json(<the frozen schema>)` on every `sendMessage*` call;
3. **still validate the returned string against the schema in Kotlin afterwards.**
   Constrained decoding is a strong prior, not a security control. Thraksha's own
   validation stays the authority (`temp/PHASE10_AUTOMATION_INTENT_CONTRACT.md` §7).

### 4.5 Sampling

```kotlin
public final class SamplerConfig(topK: Int, topP: Double, temperature: Double, seed: Int)
```

For intent extraction, configure for determinism, not creativity: **temperature at/near
0, `topK = 1`, fixed `seed`**. Exact values to be fixed during implementation and
recorded with eval results.

### 4.6 Measurement hooks (feed the §18 performance baseline)

```kotlin
public final class BenchmarkInfo {
    val initTimeInSecond: Double
    val timeToFirstTokenInSecond: Double
    val lastPrefillTokenCount: Int
    val lastDecodeTokenCount: Int
    val lastPrefillTokensPerSecond: Double
    val lastDecodeTokensPerSecond: Double
}
```

The runtime therefore reports init time, TTFT and prefill/decode throughput directly —
no hand-rolled timing needed for those metrics.

## 5. Deliberately unused API surface (safety boundary)

`ToolManager`, `ToolProvider`, `ToolSet`, `Tool`, `ToolCall`, `OpenApiTool`,
`ReflectionTool`, `automaticToolCalling` exist in this runtime — **and Thraksha will not
use any of them.**

Reason: tool-calling would let model output invoke Kotlin functions directly. That is
precisely the boundary Phase 9 §28 and Phase 10 §21 forbid. The model must produce
**data** (an `AutomationIntent`), never a **call**.

Frozen rule: `ConversationConfig.tools` stays **empty** and `automaticToolCalling` stays
**false**. Any future change requires re-opening the intent contract.

`Backend.NPU`, `LoraConfig`, vision/audio backends and `maxNumImages` are likewise out of
scope for Phase 10.

## 6. Threading and lifecycle requirements

| Concern | Requirement |
|---|---|
| `Engine.initialize()` | **Blocking, documented as up to ~10 s.** Must run off the main thread — `withContext(Dispatchers.IO)` (or a dedicated single-thread dispatcher). Never in a Compose composition or `LaunchedEffect` body without a dispatcher switch. |
| `sendMessage(...)` | Blocking. Off-main-thread only. |
| `sendMessageAsync(...): Flow<Message>` | Preferred for Thraksha — coroutine-native, cancellable, and lets the UI show a THINKING state honestly. |
| Cancellation | `Conversation.cancelProcess()` / `Session.cancelProcess()`. Wire this to the user leaving the screen and to the inference timeout. |
| Lifecycle | `Engine`, `Conversation`, `Session` and `Capabilities` are all `AutoCloseable`. Every one must be closed; a leaked `Engine` holds the full model in memory. |
| Concurrency | **One inference at a time.** Serialise with a `Mutex`, exactly as `AutomationEngine` already serialises routines. Concurrent sessions are not proven safe here and guide §19 forbids assuming they are. |
| Process death | Native memory dies with the process. On restart the model must be re-verified and re-loaded through the same state machine (MISSING → VERIFYING → READY → LOADING → LOADED). No state is persisted. |
| Ownership | One process-wide holder (object/singleton or a bound service) exposing a `StateFlow<ModelState>`; **never** a per-composable `Engine`. |

## 7. Memory implications

| Item | Figure |
|---|---|
| Model file on disk | 557.34 MiB |
| Expected resident footprint once loaded | **same order as the file** — 4-bit weights are mapped/loaded largely as-is, plus a 4096-token KV cache and activation buffers. Budget **≥ 700 MiB** for the process and **measure**. |
| S20 FE RAM | 7.44 GiB total, ≈3.87 GiB available at probe time |
| Dalvik heap limits | `dalvik.vm.heapsize = 512m`, `heapgrowthlimit = 256m` |

**Critical:** the 256 MB Java heap-growth limit is **not** the binding constraint — the
weights live in **native** memory allocated by `liblitertlm_jni.so`, outside the Dalvik
heap. `android:largeHeap` is therefore not the fix, and requesting it would be cargo
cult. The real risks are total-process RSS and low-memory kills, which is why guide §19
prefers one persistent engine and why the app must survive `ERROR` cleanly.

## 8. Compatibility verdict

| Question | Answer | Basis |
|---|---|---|
| Is there an official Android dependency for this runtime? | **Yes** — `com.google.ai.edge.litertlm:litertlm-android` on Google Maven | maven-metadata + POM fetched |
| Exact version selected? | **0.16.0** (fallback 0.15.0) | latest release, 2026-08-11 |
| Does it accept a `.litertlm` file? | **Yes** — `EngineConfig.modelPath` | AAR `classes.jar` |
| Is our artifact that format? | **Yes** — `LITERTLM` v1.0.0, `TF_LITE_PREFILL_DECODE` | binary header |
| minSdk satisfied? | **Yes** — AAR 24 ≤ project 26 | AAR manifest |
| ABI satisfied on the S20 FE? | **Yes** — `arm64-v8a` present | AAR `jni/` listing |
| ABI satisfied on the DO AVD? | **Yes** — `x86_64` present | AAR `jni/` listing |
| Can strict JSON be enforced? | **Yes** — `ResponseFormat.json(schema)` constrained decoding | AAR `classes.jar` |
| **Does it initialise on the S20 FE with this model?** | **NOT YET PROVEN** | requires adding the dependency to the app build — deliberately not done in this prep run, see below |

### Why the on-device init proof was not performed in this run

Proving initialization requires adding the AAR to `app/build.gradle.kts` and installing a
rebuilt APK (~21 MB larger, plus a transitive coroutines bump 1.8.1 → 1.9.0). This run's
standing instruction is **PREPARE ONLY** with the Phase 9 baseline frozen, so the app
build was left untouched. The proof is specified as the **first step of Phase 10
implementation**, and until it passes, no "on-device AI ready" claim may appear anywhere
in the UI or documents.

### Ready-to-run bring-up procedure (Phase 10 implementation, step 1)

1. Add `com.google.ai.edge.litertlm:litertlm-android:0.16.0` to the version catalog and
   `app/build.gradle.kts`; set `ndk { abiFilters += listOf("arm64-v8a", "x86_64") }`.
2. Re-run the **full Phase 9 unit + instrumented suites on both devices** and confirm the
   frozen counts still hold (181 / 73-3 / 73-7). Any drift is a dependency regression and
   must be resolved before going further.
3. Add one isolated instrumented test — no automation imports at all — that:
   * resolves the model path via `LocalModelProvider`,
   * verifies the pinned SHA-256,
   * builds `EngineConfig(modelPath, Backend.CPU())` and calls `engine.initialize()` on
     an IO dispatcher with a generous timeout,
   * asserts `engine.isInitialized()`,
   * records `BenchmarkInfo.initTimeInSecond` and process RSS before/after,
   * calls `engine.close()`.
4. Repeat with `Backend.GPU()` and record which backend is faster/more stable.
5. Record all measurements in `temp/PHASE10_DEVICE_CAPABILITY.md` §6.
6. Only then wire prompt → constrained JSON → schema validation → `AutomationIntent`.

## 9. Blockers

**None at this step.** The runtime exists officially, is version-pinned, ships the
required ABI, accepts this exact artifact format, supports constrained JSON output, and
its Kotlin API is fully enumerated above. The single open item is the on-device
initialization measurement, which is scheduled, specified and explicitly not claimed.

**Sources:**
- [LiteRT-LM Android quick-start](https://developers.google.com/edge/litert-lm/android)
- [LiteRT-LM Kotlin getting started](https://github.com/google-ai-edge/LiteRT-LM/blob/main/docs/api/kotlin/getting_started.md)
- [LiteRT-LM project](https://github.com/google-ai-edge/LiteRT-LM)
- Google Maven: `https://dl.google.com/dl/android/maven2/com/google/ai/edge/litertlm/litertlm-android/maven-metadata.xml` and the `0.16.0` POM/AAR
