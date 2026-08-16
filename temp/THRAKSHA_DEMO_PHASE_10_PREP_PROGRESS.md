# THRAKSHA DEMO — PHASE 10 PREPARATION PROGRESS

## GEMMA 3 1B + LITERT-LM — ASSET INTEGRITY, RUNTIME, DELIVERY, INTENT CONTRACT, EVALUATION

Run against `temp/THRAKSHA_DEMO_IMPLEMENTATION_PHASE_10_PREP.md` (authoritative), on the
frozen Phase 9 baseline.

**Date:** 2026-08-15
**Devices:** Samsung `SM-G781B` (S20 FE), Android 13 / API 33, `arm64-v8a`;
AOSP `thraksha_do` AVD, Android 13, DEVICE_OWNER, `x86_64`.
**Scope executed:** preparation only. **No inference was connected to automation. No
application code, test, Gradle configuration, manifest or Android resource was
modified.**

---

## 1. What this phase established

> The exact Gemma artifact is identified and hash-verified, its runtime dependency is
> pinned to an exact official version whose ABI and API were read out of the published
> AAR itself, the delivery path is proven byte-intact on the target handset, and the
> model has a frozen contract under which it may propose one of three routines and
> nothing else — with no authority to control Android.

The one thing deliberately **not** claimed: that `Engine.initialize()` succeeds on the
phone. That needs the dependency in the app build, which would unfreeze the Phase 9
baseline, so it is scheduled as implementation step 1 (§7).

## 2. Baseline preserved

Phase 9 exit state, unchanged by this run: **181 unit / 73 instrumented on the S20 FE
(3 skipped) / 73 instrumented on the DO AVD (7 skipped)**, all 0 failures. Re-verified —
see §8.

## 3. Deliverables

| # | Guide § | Artifact | Status |
|---|---|---|---|
| 1 | §3 | `temp/PHASE10_MODEL_INTEGRITY.md` | ✅ created |
| 2 | §4 | `.gitignore` model-weights block | ✅ added + verified |
| 3 | §5 | `model/MODEL_ATTRIBUTION.md` | ✅ created |
| 4 | §6 | `temp/PHASE10_LITERT_RUNTIME.md` | ✅ created |
| 5 | §7, §18 | `temp/PHASE10_DEVICE_CAPABILITY.md` | ✅ created |
| 6 | §8, §9 | `temp/PHASE10_MODEL_DELIVERY.md` | ✅ created |
| 7 | §11 | `temp/PHASE10_AUTOMATION_INTENT_CONTRACT.md` | ✅ created |
| 8 | §12, §13, §14 | `temp/PHASE10_MODEL_PROMPT.md` | ✅ created |
| 9 | §15 | `temp/PHASE10_INTENT_EVAL.json` | ✅ created — **100 cases**, JSON-validated |
| 10 | §10, §16, §17, §19, §20 | `temp/PHASE10_OFFLINE_VERIFICATION.md` | ✅ created |
| 11 | §25 | this progress report | ✅ created |

## 4. Findings

### 4.1 Model integrity (§3)

| Property | Value |
|---|---|
| File | `model/Gemma3-1B-IT_multi-prefill-seq_q4_ekv4096.litertlm` |
| Size | **584 417 280 bytes** (557.34 MiB) |
| **SHA-256** | **`1325ae366d31950f137c9c357b9fa89448b176d76998180c08ceaca78bba98be`** |
| Modified | 2026-08-15T00:23:52Z · readable ✅ |

The binary was **read only, never modified**. Container inspection proves what it is
rather than trusting the filename: magic `LITERTLM`, version **1.0.0**, metadata
`author = The ODML Authors`, `model_type = TF_LITE_PREFILL_DECODE`, and an embedded
tokenizer carrying the Gemma chat control tokens `<start_of_turn>` / `<end_of_turn>`.

Filename-encoded parameters: `q4` (4-bit), **`ekv4096` → ≈4096-token context budget**,
which became a hard input to the prompt contract.

### 4.2 Git safety (§4)

`/model/*.litertlm`, `*.task`, `*.tflite`, `*.bin` added to `.gitignore`. Verified:
binary **ignored** (`.gitignore:44`), **untracked**, **unstaged**; `model/README.md` and
`model/MODEL_ATTRIBUTION.md` remain **trackable**. A 557 MiB blob can no longer enter the
repo through `git add .`.

### 4.3 Provenance / licensing (§5)

Recorded from `model/README.md` and a live check of the source page: **Hugging Face
`litert-community/Gemma3-1B-IT`**, license label **`gemma`**, repository **gated** behind
acceptance of the Gemma license. Runtime library is separately **Apache-2.0**.

**Honest gap recorded rather than papered over:** the file advertised on that repository
today is named `gemma3-1b-it-int4.litertlm`, which is **not** our filename. The local
name follows the `litert-community` LiteRT-LM convention and is most likely an earlier or
sibling revision, but **the exact upstream revision was not independently established**
and is marked **REQUIRES CONFIRMATION**. No license terms are invented, restated as
binding, or waived; redistribution obligations are marked **REQUIRES LEGAL REVIEW** before
any distribution beyond developer-provisioned demo hardware.

### 4.4 LiteRT-LM runtime (§6)

**`com.google.ai.edge.litertlm:litertlm-android:0.16.0`** — Google Maven, Apache-2.0.
Pinned exactly; the official quick-start's `latest.release` is explicitly rejected as
unsafe for a frozen demo baseline. Fallback `0.15.0` documented.

Evidence came from the artifacts themselves, not from memory:

| Fact | Source |
|---|---|
| `0.16.0` is the current release (2026-08-11), 20 published versions | Google Maven `maven-metadata.xml` |
| Apache-2.0; deps gson 2.13.2, kotlin-reflect 2.2.21, coroutines 1.9.0 | official POM |
| **minSdk 24** | the AAR's own `AndroidManifest.xml` |
| **ABIs: `arm64-v8a` + `x86_64` only** (no 32-bit) | AAR `jni/` listing |
| Full Kotlin API surface | `javap` over the AAR's `classes.jar` |

Key API facts that shaped the design:

* `EngineConfig(modelPath=…)` loads a **`.litertlm` file path** — exactly our artifact.
* **`ResponseFormat.json(schema)` / `Type.JSON_OBJECT` = constrained decoding.** This is
  the decisive find: strict JSON is *enforced at sampling time*, so the guide's "do not
  regex-scrape prose" requirement is satisfiable.
* `BenchmarkInfo` exposes init time, TTFT and prefill/decode tokens/sec — the §18 metrics
  come from the runtime itself.
* `Engine`/`Conversation`/`Session` are `AutoCloseable`; `initialize()` is blocking (~10 s)
  and must run off-main.
* **Tool-calling exists and is deliberately refused.** `ToolManager`/`ToolProvider`/
  `automaticToolCalling` would let model output invoke Kotlin functions — precisely the
  boundary Phase 9 §28 forbids. Frozen: `tools` stays empty, `automaticToolCalling` false.

Compatibility verdict: **compatible on every statically checkable axis** — container
format, file-path loading, minSdk 24 ≤ 26, arm64-v8a present, constrained JSON available.

### 4.5 Device capability (§7)

S20 FE: Android 13 / API 33, **Snapdragon 865 (SM8250)**, 8 cores, **arm64-v8a**,
**7.44 GiB RAM** (3.87 GiB available), **93 GB free**, **Adreno 650**, OpenGL ES 3.2,
**Vulkan 1.1 with compute**. Every static requirement is met with wide margin.

Noted for implementation: the weights live in **native** memory, so the 256 MB Dalvik
`heapgrowthlimit` is not the binding constraint and `largeHeap` is not the remedy — a
common and expensive misdiagnosis avoided.

### 4.6 Delivery (§8/§9) — proven on device

**Option A (developer provisioning) selected.** Option B (packaged asset) rejected: it
would add 557 MiB to the APK, *still* need extraction because `EngineConfig` takes a file
path and APK assets have none, and would constitute redistribution of gated weights.
Option C retained as a documented fallback.

Proven end-to-end on the S20 FE:

| Step | Result |
|---|---|
| `adb push` → `/sdcard/Android/data/com.thraksha.guardian/files/models/` | **12.7 s**, ≈44 MB/s |
| On-device SHA-256 | **identical to host** ✅ |
| Integrity-check cost on device | **0.81 s** for 557 MiB → cheap enough to run every launch |
| Read from inside the app sandbox | `run-as … head -c 8` → `LITERTLM` ✅ with **zero storage permissions** |
| Free space after | 93 GB |

One authoritative path, one resolver (`LocalModelProvider`), **no fallback search, no
second model, ever**. Verification order is size → magic → SHA-256, so a wrong file fails
fast without hashing.

### 4.7 Intent contract (§11) — frozen

Phase 9's `AutomationIntent` is **preserved unchanged**. The model may populate exactly
three things:

| Field | Allowed |
|---|---|
| `routineType` | `MEETING`, `FOCUS`, `DRIVING` |
| `durationMinutes` | 1–480, or null (= until stopped) |
| `targetApp` | only a package from a **runtime-generated enum of genuinely installed, launchable apps** |
| `customActions` | **forced empty** — `CUSTOM` is excluded from the schema entirely |

Two decisions worth calling out:

* **`CUSTOM` excluded.** It is the only routine whose actions come from the intent rather
  than a planner template; letting a 1B model author capability+value pairs is a much
  wider surface for no demo benefit. The user still has the deterministic custom button.
* **`targetApp` as an injected enum.** Constrained decoding then makes hallucinating an
  uninstalled package **structurally impossible**, not merely discouraged.

Everything a routine actually does — capabilities, values, required-vs-optional,
ordering, snapshot, verification, restoration — stays hardcoded in Phase 9's planner and
is not expressible in the model's schema at all.

### 4.8 Structured output & prompt (§12/§13/§14)

Frozen five-key schema `{result, routine, durationMinutes, targetApp, reasonCode}` with
`additionalProperties:false`, enforced by `ResponseFormat.json(...)` at decode time and
re-validated in Kotlin with `ignoreUnknownKeys = false`.

**The model emits no natural language at all.** `CLARIFY`/`UNSUPPORTED` carry a closed
`reasonCode` enum, and every user-visible sentence is authored by Thraksha from the
*validated* intent and the *deterministic* plan (guide §23). That removes hallucinated
descriptions of Android behaviour, prompt-injection echo into the UI, and the model
speaking with the app's authority — in one decision.

Five mandatory gates, none skippable: constrained decoding → strict parse → contract
check → **unmodified `AutomationSafetyPolicy`** → planner/capability probe.

### 4.9 Evaluation set (§15)

`temp/PHASE10_INTENT_EVAL.json` — **100 cases**, JSON-validated and
consistency-checked programmatically (unique ids; every INTENT has a routine and no
reasonCode; every non-INTENT has a valid reasonCode; every duration within 1–480; every
`targetApp` present in the declared installed-app enum).

| Category | n | | Category | n |
|---|---|---|---|---|
| duration | 13 | | destructive | 7 |
| meeting | 10 | | security_request | 7 |
| focus | 10 | | prompt_injection | 7 |
| driving | 10 | | unrelated | 6 |
| target_app | 8 | | custom | 5 |
| ambiguous | 7 | | brightness | 5 |
| malformed | 5 | | | |

Expected outcomes: 38 `INTENT`, 16 `CLARIFY`, 46 `UNSUPPORTED`.

Scoring is deliberately **asymmetric**: a `CLARIFY` where `INTENT` was expected is a soft
failure (over-cautious); an `INTENT` where `UNSUPPORTED` was expected is a hard failure
(over-confident). Marked `EVALUATION_ONLY` in the file header — not training data, not
few-shot material.

### 4.10 States, fallback, offline (§10/§16/§17/§19/§20)

Seven states frozen — `MISSING, VERIFYING, READY, LOADING, LOADED, ERROR, UNSUPPORTED` —
with legal transitions, and two invariants:

1. **"ON-DEVICE AI READY" requires `LOADED`** — i.e. both a passing SHA-256 *and* a
   successful runtime init. A file merely existing never earns that label.
2. **Every single state leaves the Phase 9 automation card fully usable.** AI is an
   additive input method, never a dependency.

Session design: one persistent `Engine`, a fresh `Conversation` per utterance, one
inference at a time behind a `Mutex`, cancellation wired, everything off the main thread.

Offline: no network code on the model path, **no cloud fallback**, no telemetry. A
16-step verification procedure is defined whose hard pass/fail is **zero bytes attributed
to the app's uid** with Wi-Fi and data off — plus a step that renames the model away to
prove the deterministic path is genuinely independent.

## 5. Security boundary (§21/§22/§24) — restated and unchanged

```
user text → local Gemma → JSON → schema validation → AutomationIntent
   → AutomationSafetyPolicy → AutomationPlanner → PLAN PREVIEW
   → user presses START → Phase 9 AutomationEngine → executors → verify → restore → audit
```

* The model **cannot** invoke executors, touch `DevicePolicyManager` or `Settings`,
  construct Intents/URIs, alter `PolicyEngine`, disable Network Guard, modify security
  findings, write audit rows, or bypass the preview — **none of these have any
  representation in its schema**, and tool-calling is switched off.
* **No auto-execution.** Inference completing is not authorisation; START is a human act
  with no flag, threshold or debug switch to skip it.
* **No model-based security verdicts** (guide §24). Security stays deterministic and
  signed-evidence driven; `SECURITY_REQUEST` is an explicit rejection reason in the eval
  set.
* **`AutomationSafetyPolicy` is not weakened, relaxed or special-cased** for AI input.
  Model-originated intents traverse the identical policy object as button-originated ones.

## 6. Acceptance criteria (§26)

| Criterion | Status |
|---|---|
| Model binary exists | ✅ |
| SHA-256 recorded | ✅ `1325ae36…8bba98be` |
| Binary gitignored / untracked / unstaged | ✅ verified three ways |
| Attribution / license record exists | ✅ with gaps honestly marked |
| Exact LiteRT-LM dependency + version selected | ✅ `0.16.0`, pinned |
| Model/runtime compatibility documented | ✅ statically established; on-device init explicitly not claimed |
| S20 FE capability documented | ✅ |
| Delivery strategy selected | ✅ Option A, proven on device |
| Authoritative model path + integrity strategy | ✅ single path, single resolver, no fallback |
| `AutomationIntent` contract frozen | ✅ Phase 9 class unchanged |
| Structured-output format frozen | ✅ 5-key schema + constrained decoding |
| Prompt contract exists | ✅ |
| Evaluation dataset exists | ✅ 100 cases |
| Fallback behaviour defined | ✅ exhaustive table |
| Offline procedure exists | ✅ 16 steps |
| Performance metrics defined | ✅ 14 metrics |
| Phase 9 safety boundary preserved | ✅ |
| No free-form output reaches executors | ✅ five gates |
| No auto-execution from inference | ✅ |
| No security verdict uses the model | ✅ |

**All met**, with the single item explicitly deferred and named in §7.

## 7. The one open item

**On-device runtime initialization is NOT yet proven and is NOT claimed.**

Proving it requires adding the AAR to `app/build.gradle.kts` (+~21 MB native lib, plus a
transitive coroutines bump 1.8.1 → 1.9.0), which would change the very APK the frozen
Phase 9 suites were verified against. This run's instruction was PREPARE ONLY, so the app
build was left untouched.

Ready-to-run procedure — `temp/PHASE10_LITERT_RUNTIME.md` §8:

1. add the pinned dependency + `abiFilters` (`arm64-v8a`, `x86_64`);
2. **re-run the full Phase 9 unit + instrumented suites on both devices** and confirm
   181 / 73−3 / 73−7 still hold — any drift is a dependency regression to resolve first;
3. one isolated instrumented test (no automation imports): resolve path → verify SHA-256
   → `EngineConfig(modelPath, Backend.CPU())` → `initialize()` off-main → assert
   `isInitialized()` → record `initTimeInSecond` and RSS before/after → `close()`;
4. repeat with `Backend.GPU()`; record which is faster/more stable;
5. record results in `temp/PHASE10_DEVICE_CAPABILITY.md` §6/§7;
6. **only then** wire prompt → constrained JSON → validation → `AutomationIntent`.

Until step 3 passes, no "on-device AI ready" claim may appear anywhere.

## 8. Regression state

| Check | Result |
|---|---|
| Application code / tests / Gradle / manifests / resources modified | **NONE** |
| `git diff --stat` on tracked Phase 9 files | unchanged from the frozen baseline apart from `.gitignore` |
| `:app:testDebugUnitTest` | **181 tests, 0 failures** — identical to the Phase 9 baseline |
| Phase 9 behaviour | unchanged — no automation, security, policy or UI file touched |
| Committed | **nothing** |

**Device note:** the S20 FE's *settings* are untouched (Phase 9's "leave the phone as
found" state holds). The one change on the handset is the deliberate, documented
provisioning of the model file into the app's own external files directory — removable
with a single `adb shell rm`, and removed automatically on uninstall.

## 9. Stop conditions (§27)

None triggered. Specifically: the SHA-256 **was** established; LiteRT-LM **does** support
this artifact format; the runtime does **not** require a different model format; delivery
does **not** require an unacceptable APK design; the intent contract **does** map cleanly
onto Phase 9; structured output **can** be validated reliably (constrained decoding plus
four further gates); **no** weakening of `AutomationSafetyPolicy` is needed; the preview
gate is **not** bypassed; cloud inference is **not** necessary. The model was not swapped
and the runtime was not silently changed.

Two items carry honest caveats rather than blockers: the exact upstream model revision
(**REQUIRES CONFIRMATION**) and redistribution obligations (**REQUIRES LEGAL REVIEW**) —
neither blocks a developer-provisioned demo.

## 10. Final prep statement

> The exact Gemma model is identified and verified, its runtime and delivery path are
> fixed, the phone is proven capable on every statically checkable axis with the one
> remaining unknown named and scheduled, and the model has a strict contract: it may
> interpret language into a validated `AutomationIntent` but it has **no authority to
> control Android**.
