# PHASE 10A — RUNTIME BRING-UP

**Date:** 2026-08-15
**Guide:** `temp/THRAKSHA_DEMO_IMPLEMENTATION_PHASE_10_ABC.md` §5–§14 (authoritative)
**Device:** Samsung `SM-G781B` (S20 FE), Android 13 / API 33, arm64-v8a
**Question answered:** *can this exact `.litertlm` artifact run reliably and acceptably on
this phone?*

**Result: PASS** — with one documented performance concern (§7) carried into 10B.

---

## 1. Dependency (guide §6)

```kotlin
// app/build.gradle.kts
implementation("com.google.ai.edge.litertlm:litertlm-android:0.16.0")

// defaultConfig
ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
```

Version **pinned**, never `latest.release` — the demo baseline must not move underneath
us. Source: Google Maven, Apache-2.0. No JAR/AAR was hand-downloaded into the project.

`abiFilters` is explicit because LiteRT-LM ships `liblitertlm_jni.so` for **arm64-v8a and
x86_64 only**. Without the filter a 32-bit APK would install happily and then fail at
model load. Both demo targets are covered (S20 FE arm64-v8a, `thraksha_do` AVD x86_64).

APK impact, measured: **29.1 MB → 74.3 MB**, entirely the two native libraries
(21.5 MB arm64 + 25.7 MB x86_64). A single-ABI split would ship ~50 MB.

### Post-dependency regression (guide §6 — mandatory before proceeding)

| Check | Result |
|---|---|
| `clean :app:assembleDebug` | **BUILD SUCCESSFUL** |
| `:app:testDebugUnitTest` | **181 tests, 0 failures** — identical to the Phase 9 baseline |
| `connectedDebugAndroidTest` — S20 FE (Phase 1–9 suites) | **73 tests, 0 failures, 3 skipped** — identical to the Phase 9 baseline |

The transitive coroutines bump (1.8.1 → 1.9.0) and the new gson/kotlin-reflect artifacts
caused **no regression**. The dependency is safe to keep.

## 2. Architecture added in 10A

Three files, all under `com.thraksha.guardian.ai`, none of which imports a single
automation, executor, security or Android-control type:

| File | Responsibility |
|---|---|
| `ModelState.kt` | the seven-phase lifecycle + honest UI labels |
| `LocalModelRepository.kt` | the one authoritative path, the SHA gate, `StateFlow<ModelState>` |
| `IntentSchema.kt` | the frozen JSON schema + system instruction (pure strings) |
| `OnDeviceIntentModel.kt` | the only component that touches LiteRT-LM |

**The boundary is structural, not documentary.** `OnDeviceIntentModel`'s entire output is
a `String`. It has no reference to `AutomationEngine`, no executor, no `Settings`, no
`DevicePolicyManager`, no `Intent`. It physically cannot start a routine.

Also structurally absent by design:

* **tool calling** — `ConversationConfig.tools` stays empty and `automaticToolCalling` is
  never enabled, so model output can never invoke a Kotlin function;
* **network** — no HTTP client, no cloud endpoint, no fallback.

## 3. The SHA gate (guide §8) — VERIFIED

Order is deliberately cheapest-first so a wrong file fails without hashing 557 MiB:

1. `exists()` → else `MISSING`
2. `canRead()` → else `ERROR`
3. `length() == 584 417 280` → else `ERROR` ("unexpected size")
4. first 8 bytes == `LITERTLM` → else `ERROR` ("not a LiteRT-LM container")
5. SHA-256 == pinned digest → else `ERROR` ("checksum mismatch")

Only then is `Engine` constructed. Measured on device: **839 ms** for the full gate.

`a01` asserts the provisioned artifact reaches `READY` against the pinned digest
`1325ae36…8bba98be`. `a02` asserts a wrong-sized file can never satisfy the size gate that
precedes hashing. There is **no fallback path to another model** anywhere in the class.

## 4. Constrained JSON (guide §11) — WORKS

```kotlin
ConversationConfig(systemInstruction = …, samplerConfig = …,
                   maxOutputToken = 96, enableResponseFormat = true)
conversation.sendMessage(userText, responseFormat = ResponseFormat.json(schema))
```

The schema is generated from the frozen Phase 10 intent contract, with the `targetApp`
enum injected from genuinely installed launchable packages.

**Measured: 33 of 33 generations across all 10A tests were parseable JSON with only
contract keys and only enum-legal values. Zero prose. Zero code fences. Zero invalid
JSON.** No regex scraping exists anywhere in the codebase, and no free-form fallback
parser was written.

Sampling is deterministic — `SamplerConfig(topK = 1, topP = 1.0, temperature = 0.0,
seed = 1234)`. Confirmed: 12 repetitions of one prompt produced **1 distinct output**.

## 5. Backend selection — decided on evidence

| Backend | Load | Inference | Outcome |
|---|---|---|---|
| `Backend.CPU(threadCount = 4)` | 1 501 ms warm | mean **10 049 ms** | ✅ **selected** |
| `Backend.CPU(threadCount = 8)` | 1 474 ms warm | mean 10 534 ms | slower — big.LITTLE contention, not headroom |
| `Backend.GPU()` | 10 341 ms | **all 3 requests failed** | ❌ unusable |

GPU failed with:

```
LiteRtLmJniException: Failed to call nativeSendMessage:
UNKNOWN: UNKNOWN: Can not find OpenCL library on this device
```

Notable: the GPU engine **initializes successfully and only fails at inference** — so
"the engine loaded" is not evidence that a backend works. This is exactly why the guide
requires real inference before any claim. CPU/4 is the pinned default; GPU is documented
as unavailable on this handset rather than left as a hopeful option.

## 6. Reliability (guide §13) — 8/8 tests green

| Test | Result |
|---|---|
| `a01` SHA gate verifies the pinned artifact | ✅ READY in 839 ms |
| `a02` wrong-sized file rejected before hashing | ✅ |
| `a03` runtime initializes, model loads | ✅ LOADED |
| `a04` first inference → constrained JSON | ✅ |
| `a05` 18-prompt smoke set (meeting/focus/driving/malformed/unsupported/ambiguous) | ✅ **0 inference failures, 0 non-conformant** |
| `a06` 12 consecutive inferences | ✅ stable, no drift, no leak |
| `a07` unload → reload → infer | ✅ |
| `a08` benchmark file written | ✅ |

No crash, no OOM, no runtime corruption, and the engine remained reusable throughout.

## 7. The one concern carried into 10B — latency and output quality

**Latency ≈ 10 s per request.** Acceptable for a plan-preview flow (the user must read the
plan and press START anyway), but slower than a demo deserves. The dominant cost is
prefilling the ~500-token system instruction on every request, since a fresh
`Conversation` is created per utterance.

**Output quality is poor at this stage — and this is a 10B problem, not a 10A one.**
Observed with the initial long prompt:

| Prompt | Output | Issue |
|---|---|---|
| "Put me in meeting mode for 30 minutes." | `INTENT/MEETING/30` + `targetApp` invented + `reasonCode:"UNSAFE_VALUE"` | fills irrelevant fields |
| "I have a meeting for the next 45 minutes" | `INTENT/**DRIVING**/45` | wrong routine |
| "I need to focus for 30 minutes" | `CLARIFY` with no `routine` key | wrong outcome |
| "I'm driving" | `UNSUPPORTED` **with** `routine:"DRIVING"` | self-contradictory |

The model emits every declared property rather than omitting optional ones, and a 1B model
struggles with a long multi-rule instruction. Both point at the same fix — a shorter,
sharper prompt — which should improve accuracy **and** cut prefill latency. That work is
10B's tuning loop (guide §22), recorded in `PHASE10B_TUNING_LOG.md`.

**None of this affects the 10A gate**, which is about runtime viability: the structure was
always schema-legal, so every one of these bad outputs is caught downstream by validation
rather than reaching the device.

## 8. 10A PASS GATE (guide §14)

| Criterion | Status |
|---|---|
| Runtime initializes | ✅ |
| Model loads | ✅ 4 188 ms cold / ~1 500 ms warm |
| SHA verification passes | ✅ 839 ms |
| Inference works | ✅ |
| Constrained JSON works | ✅ 33/33 conformant |
| No OOM / crash | ✅ |
| Repeated inference stable | ✅ 12 runs, +3 MB PSS, no latency drift |
| Performance acceptable for a live demo | ⚠️ **~10 s** — acceptable for a preview flow, targeted for improvement in 10B |
| Phase 9 regressions green | ✅ 181 unit / 73 instrumented (3 skipped) |

**VERDICT: PASS → continue automatically to Phase 10B.**
