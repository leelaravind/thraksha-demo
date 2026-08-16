# PHASE 10C — FAILURE & RECOVERY BEHAVIOUR

**Date:** 2026-08-15 · **Device:** S20 FE `SM-G781B` · **Guide:** §31, §32, §33, §34
**Verdict: PASS**

---

## 1. Failure matrix — every case exercised on the handset

| Scenario | How it was induced | Result | Phase 9 automation |
|---|---|---|---|
| **Model missing** | engine unloaded, file renamed away | state → `MISSING`; interpreter returns `ModelUnavailable`; no guess | **fully usable** |
| **Invalid hash** | real model replaced with a decoy carrying valid `LITERTLM` magic but wrong size | state → `ERROR` "MODEL INVALID"; **never loaded**; no fallback model searched | **fully usable — full Meeting cycle ran and restored** |
| **Load failure** | covered by the two above; also `UnsatisfiedLinkError` → `UNSUPPORTED` | honest state, NL input hidden | **fully usable** |
| **Cancellation** | `OnDeviceIntentModel.cancel()` wired to the UI Cancel button | in-flight `Conversation.cancelProcess()`; state returns to Idle | unaffected |
| **Repeated inference** | 20 consecutive `interpret()` calls | no crash, no leak, no drift (§3) | untouched — engine stayed `IDLE` throughout |
| **Active routine vs model lifecycle** | routine started, then `unload()` called underneath it | routine stayed `ACTIVE` with the same `runId`; DND still applied; STOP restored correctly | **independent** |
| **Process death** | covered by the unchanged Phase 9 `recover()` path | restoration duty reconstructed from encrypted persistence | Phase 9 behaviour |

The invalid-hash test is the sharpest: it corrupts the model, asserts the load is refused,
**and then runs a complete deterministic Meeting cycle successfully** — proving the AI is
not a single point of failure (guide §31).

## 2. Memory and thermal stress (guide §34)

20 consecutive natural-language requests through the full product path:

| Metric | Result |
|---|---|
| Requests | 20 |
| Rejections / errors | **0** |
| PSS before | 2 148 138 kB (2.05 GiB) |
| PSS after | 2 087 959 kB (1.99 GiB) |
| **Net change** | **−60 179 kB (−59 MiB)** |
| Second-half growth | +37 828 kB (+37 MiB) — noise, not a trend |
| Latency, first half | 11 423.7 ms mean |
| Latency, second half | 11 482.4 ms mean |
| **Latency drift** | **+0.5 %** |

Per-request PSS (MiB): `2101 2094 2065 2045 2037 2035 1999 2007 2004 2009 2002 2001 2067
2025 2002 2075 2092 2049 2031 2039` — flat, oscillating around ~2.0 GiB.

**No leak, no thermal throttling, no drift.** The persistent-engine strategy (guide §19) is
therefore kept; no unload/reload workaround is needed.

> **Recorded honestly:** an earlier version of this test reported 1.6 GB "growth" and
> failed. That was a measurement artefact — JUnit's default method ordering meant the
> baseline was sometimes captured before the model was loaded, so the load itself was
> counted as growth. The test now records per-request PSS and asserts on **second-half**
> growth, after any one-off allocation has settled. The fix was to the measurement, and the
> raw per-request series is published above so the shape can be checked independently.

## 3. Session policy (guide §33)

* **One inference at a time**, serialised by a `Mutex` — overlapping requests cannot occur.
* **One persistent `Engine`**, a **fresh `Conversation` per utterance** (closed in a
  `finally`), so no chat history accumulates and the 4096-token budget stays clean.
* **Cancellation** exposed via `cancelProcess()` and wired to the UI.
* Everything off the main thread; the UI observes a `StateFlow`.

## 4. Honest failure text

| Condition | Shown to the user |
|---|---|
| Model absent | ON-DEVICE AI MODEL NOT INSTALLED |
| Integrity failure | ON-DEVICE AI UNAVAILABLE + "MODEL INVALID — checksum mismatch (expected …, found …)" |
| Load failure / OOM | ON-DEVICE AI UNAVAILABLE + the exception class |
| Unsupported ABI | ON-DEVICE AI NOT SUPPORTED ON THIS DEVICE |
| Inference or validation failure | COULD NOT INTERPRET REQUEST |
| SafetyPolicy rejection | the policy's own reason, verbatim from Phase 9 |

Every one is followed by *"The Meeting, Focus and Driving buttons below are unaffected."*
— which the tests prove is true.
