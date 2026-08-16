# PHASE 10A — DEVICE RESULTS (S20 FE)

**Date:** 2026-08-15 · **Device:** `SM-G781B` / `RZCW40LVBJD`, Android 13, arm64-v8a

---

## 1. Test execution

| Suite | Result |
|---|---|
| `Phase10ARuntimeInstrumentedTest` | **8 tests, 0 failures, 0 skipped** |
| `Phase10ABackendBenchmarkTest` | **1 test, 0 failures** |
| Phase 1–9 regression (all other instrumented classes) | **73 tests, 0 failures, 3 skipped** |
| `:app:testDebugUnitTest` | **181 tests, 0 failures** |

The 3 instrumented skips are the frozen Phase 9 baseline skips (Device-Owner-only test,
Full-Power ACT test, and the DND-denial test which by design only runs when the access is
absent). **Identical to the pre-Phase-10 baseline** — the LiteRT-LM dependency introduced
no regression.

## 2. Provisioning state

| Item | Value |
|---|---|
| Model path on device | `/storage/emulated/0/Android/data/com.thraksha.guardian/files/models/Gemma3-1B-IT_multi-prefill-seq_q4_ekv4096.litertlm` |
| Verified digest | `1325ae366d31950f137c9c357b9fa89448b176d76998180c08ceaca78bba98be` ✅ matches pinned |
| Gate duration | 839 ms |
| Storage free after | 93 GB |

## 3. Proven chain (guide §10) — `text → local Gemma → constrained JSON`

Canonical first request, executed on the handset:

```
prompt : "Put me in meeting mode for 30 minutes."
output : {"result":"INTENT","routine":"MEETING","durationMinutes":30,
          "targetApp":"com.samsung.android.calendar","reasonCode":"UNSAFE_VALUE"}
latency: 7 990 ms
```

The routine and duration are correct. Two fields are junk (`targetApp` was not requested;
`reasonCode` is meaningless for an INTENT) — a **model-quality** problem addressed in 10B,
not a runtime problem. Structurally the output is legal JSON with only contract keys and
only enum values, which is what 10A had to prove.

**Nothing was executed.** This test class imports no automation type at all.

## 4. Smoke set (18 prompts) — guide §13

Coverage: 3 Meeting · 3 Focus · 3 Driving · 3 malformed/unrelated · 3 unsupported/
destructive/security · 3 ambiguous.

| Metric | Result |
|---|---|
| Inference failures | **0** |
| Non-conformant outputs | **0** |
| Median latency | 9 792 ms |
| Max latency | 10 333 ms |
| Engine usable afterwards | ✅ |

## 5. Lifecycle

| Scenario | Result |
|---|---|
| Load → infer → unload | ✅ 1.29 GiB released in 204 ms |
| Unload → reload → infer | ✅ reload 1 518 ms, inference correct afterwards |
| 12 consecutive inferences | ✅ no drift, +3 MB PSS |
| Model state machine | MISSING → VERIFYING → READY → LOADING → LOADED → READY (on unload) — all transitions exercised |

## 6. Device left safe

The S20 FE's **settings were never touched by 10A** — this phase performs no automation
and holds no special access. Phase 9's "leave the phone as found" state is intact:
brightness mode AUTO, timeout 600000, zen 0, ringer NORMAL.

Artifacts written to the app's own external files directory during testing
(`phase10a_benchmark.json`, `phase10a_backends.json`) were pulled into this folder and are
removed by Android on uninstall.

## 7. Verdict

**10A PASS.** Runtime initializes, the exact pinned model loads, the SHA gate works,
constrained JSON generation works reliably, repeated inference is stable, memory behaves,
and every Phase 1–9 regression stays green.

Proceeding automatically to **Phase 10B**.
