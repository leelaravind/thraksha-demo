# PHASE 10 PREP — MODEL STATES, FALLBACK & OFFLINE VERIFICATION

**Date:** 2026-08-15
**Guide:** `temp/THRAKSHA_DEMO_IMPLEMENTATION_PHASE_10_PREP.md` §10, §16, §17, §19, §20
**Governing rule:** *Phase 9's deterministic automation must remain fully usable in every
model state.* AI is an **additive input method**, never a dependency.

---

## 1. Model lifecycle states (frozen)

Exposed as `StateFlow<ModelState>` from the single process-wide holder. Compose observes
the flow — never a UI-local boolean (the same discipline `AutomationEngine` already
follows in Phase 9).

| State | Meaning | UI label | NL input | Phase 9 routine buttons |
|---|---|---|---|---|
| `MISSING` | the pinned model file is not at the authoritative path | **ON-DEVICE AI MODEL NOT INSTALLED** | hidden | **fully usable** |
| `VERIFYING` | size → magic → SHA-256 being checked | *Checking on-device AI model…* | disabled | **fully usable** |
| `READY` | integrity passed; not yet loaded into memory | *On-device AI available — tap to load* | enabled (triggers load) | **fully usable** |
| `LOADING` | `Engine.initialize()` running off the main thread | *Loading on-device AI…* | disabled, cancellable | **fully usable** |
| `LOADED` | `engine.isInitialized() == true` | **ON-DEVICE AI READY** | enabled | **fully usable** |
| `ERROR` | integrity failed, init failed, OOM, or native load failed | **MODEL INVALID** / **MODEL UNAVAILABLE** (+ short cause) | hidden | **fully usable** |
| `UNSUPPORTED` | this device/ABI cannot run the runtime at all | **ON-DEVICE AI NOT SUPPORTED ON THIS DEVICE** | hidden | **fully usable** |

Legal transitions:

```
MISSING ──(file appears)──▶ VERIFYING
VERIFYING ──ok──▶ READY ──user/auto──▶ LOADING ──ok──▶ LOADED
VERIFYING ──mismatch──▶ ERROR            LOADING ──fail/OOM──▶ ERROR
any ──(file removed)──▶ MISSING          startup ──(bad ABI)──▶ UNSUPPORTED
LOADED ──close()/low-memory──▶ READY
```

**Two invariants:**

1. `LOADED` is the **only** state in which "ON-DEVICE AI READY" may appear. Reaching it
   requires *both* a passing SHA-256 check *and* a successful runtime initialization
   (guide §10). A file merely existing never earns that label.
2. **No state disables, hides or degrades the Phase 9 automation card.** The rightmost
   column above is `fully usable` on every single row, by design.

## 2. Session design (guide §19)

| Question | Decision | Rationale |
|---|---|---|
| Persistent engine or load-on-demand? | **One persistent `Engine`, loaded on first use and kept** | init costs seconds; reloading before every utterance would make the demo feel broken. Guide §19: prefer reliability over aggressive memory optimisation. |
| Ownership | one process-wide holder exposing `StateFlow<ModelState>`; never a per-composable `Engine` | prevents duplicate 557 MiB loads |
| Conversation lifetime | a **fresh `Conversation` per utterance**, closed immediately after | intent extraction is stateless; no chat history is wanted, and it keeps the 4096-token budget clean and prevents cross-request contamination |
| Concurrency | **one inference at a time**, serialised by a `Mutex` | concurrent sessions are not proven safe; mirrors `AutomationEngine`'s existing lock |
| Cancellation | `Conversation.cancelProcess()` on screen exit and on timeout | avoids a wedged native call holding the mutex |
| Process death | nothing persisted; on restart the state machine re-runs `MISSING → VERIFYING → READY` | native memory dies with the process; **no automation state is involved** — Phase 9's own recovery is entirely independent of the model |
| Low memory | `close()` the engine → `READY`; reload on next use | never let an OOM kill take the app down mid-demo |

## 3. Threading (guide §20)

* `Engine.initialize()` — **off main**, `Dispatchers.IO`, documented as up to ~10 s.
* Inference — **off main**, via `sendMessageAsync(...): Flow<Message>`.
* SHA-256 verification — **off main** (measured 0.81 s on the S20 FE).
* The UI shows LOADING / THINKING / ERROR honestly; no spinner ever claims progress that
  is not happening.
* Nothing in the model path touches the Compose/UI thread.

## 4. Fallback behaviour (guide §16) — exhaustive

| Failure | User sees | Automation impact |
|---|---|---|
| Model file absent | `ON-DEVICE AI MODEL NOT INSTALLED` | **none** — Meeting/Focus/Driving/Custom buttons work exactly as in Phase 9 |
| SHA-256 / size / magic mismatch | `MODEL INVALID` | **none.** The file is **not loaded**. No fallback model is searched for. |
| `Engine.initialize()` fails or OOMs | `MODEL UNAVAILABLE` | **none** |
| Native library missing for the ABI | `ON-DEVICE AI NOT SUPPORTED ON THIS DEVICE` | **none** |
| Inference timeout | `COULD NOT INTERPRET REQUEST` | **nothing executes**; `cancelProcess()` is called |
| Output not schema-valid | `COULD NOT INTERPRET REQUEST` | **nothing executes**; no partial parse, no repair, no regex scraping |
| Output valid but violates the contract | `COULD NOT INTERPRET REQUEST` | **nothing executes**; logged for eval |
| `AutomationSafetyPolicy` rejects the intent | the policy's own reason, verbatim from Phase 9 | **nothing executes**. Policy is **never** bypassed because the model "seemed confident". |
| Planner marks a required action unavailable | Phase 9's existing `CANNOT RUN` + reason | **nothing executes** — identical to the button path |

Universal rule: **there is no partial execution anywhere.** A routine either passes every
gate and is started by the user, or nothing happens to the device.

## 5. Offline requirement (guide §17)

The design contains **no network code on the model path**:

* inference is local — LiteRT-LM native, no HTTP client, no gRPC, no cloud endpoint;
* **no cloud fallback of any kind** is permitted if local inference fails — the failure
  states in §4 are the only outcomes;
* **no telemetry**, no crash-reporting upload, no model-usage analytics;
* the model is developer-provisioned from local storage
  (`temp/PHASE10_MODEL_DELIVERY.md`), so not even provisioning needs the network at
  runtime;
* Thraksha remains an offline-only product per the project's standing architecture — the
  model must not become the first thing that phones home.

### Offline verification procedure (to execute during Phase 10 implementation)

**Preconditions:** model provisioned and SHA-256-verified; app installed; state `READY`.

| # | Step | Expected |
|---|---|---|
| 1 | Record baseline device settings via adb (brightness+mode, timeout, zen, ringer) | recorded |
| 2 | `adb shell svc wifi disable` | Wi-Fi OFF |
| 3 | `adb shell svc data disable` | mobile data OFF |
| 4 | Confirm no connectivity: `adb shell ping -c 2 8.8.8.8` | 100 % packet loss |
| 5 | Screenshot the status bar showing no Wi-Fi and no data | evidence captured |
| 6 | Start a `tcpdump`/`dumpsys netstats` snapshot for the app's uid | baseline byte counters recorded |
| 7 | Cold-start the app | state reaches `READY` with no network |
| 8 | Load the model | `LOADING → LOADED`; record `BenchmarkInfo.initTimeInSecond` |
| 9 | Enter "I have a meeting for the next 45 minutes" | valid JSON → `AutomationIntent(MEETING, 45)` |
| 10 | Confirm the **plan preview** appears and nothing has executed yet | zero device changes; adb ground truth unchanged from step 1 |
| 11 | Press **START** | Phase 9 engine runs, verifies, audits — as in Phase 9 |
| 12 | Press **STOP & RESTORE** | exact previous state restored, adb-verified against step 1 |
| 13 | Run ≥ 10 further utterances from `PHASE10_INTENT_EVAL.json` incl. UNSUPPORTED and CLARIFY cases | correct structured outcomes; nothing executes for non-INTENT cases |
| 14 | Re-read the app-uid network counters from step 6 | **zero bytes transmitted by the app** — the hard pass/fail of this procedure |
| 15 | Also verify with the model **absent** (rename the file) that every Phase 9 routine still runs offline | `MISSING` shown; automation fully usable |
| 16 | Restore Wi-Fi/data and the device's original settings | phone left as found |

**Pass criteria:** every intent is produced locally with radios off; no bytes attributed
to the app's uid; every routine start/stop behaves exactly as Phase 9 does; and step 15
proves the deterministic path is genuinely independent of the model.

Per guide §17 this document defines the procedure; the **executed** offline proof belongs
to the full Phase 10 implementation and is not claimed here.

## 6. What must never be added

* cloud/remote inference or an API key of any kind;
* a "download the model" in-app fetch over the network;
* telemetry, analytics or remote logging on the model path;
* a silent fallback to a different local model;
* an auto-execute path that skips the plan preview and the user's START;
* any use of the model for security or malware verdicts (guide §24).
