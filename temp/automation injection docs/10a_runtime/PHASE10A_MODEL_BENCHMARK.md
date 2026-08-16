# PHASE 10A — MODEL BENCHMARK (S20 FE)

**Date:** 2026-08-15 · **Device:** `SM-G781B`, Android 13, Snapdragon 865, 7.44 GiB RAM
**Model:** `Gemma3-1B-IT_multi-prefill-seq_q4_ekv4096.litertlm` (557.34 MiB, q4, ekv4096)
**Runtime:** `com.google.ai.edge.litertlm:litertlm-android:0.16.0`, `Backend.CPU(4)`
**Raw data:** `phase10a_benchmark.json`, `phase10a_backends.json` (in this folder)
**Source:** `Phase10ARuntimeInstrumentedTest`, `Phase10ABackendBenchmarkTest` — measured on
the handset, not estimated.

---

## 1. Guide §12 metrics

| Metric | Measured |
|---|---|
| Model verification time (size + magic + SHA-256, 557 MiB) | **839 ms** |
| Model load time — cold | **4 188 ms** wall (runtime reported 3 683 ms) |
| Model load time — warm (page cache) | **1 474–1 518 ms** (reported 746–755 ms) |
| RAM before load (total PSS) | **181 328 kB** ≈ 177 MiB |
| RAM after load (total PSS) | **1 295 797 kB** ≈ 1.24 GiB |
| **Load delta** | **+1 114 469 kB ≈ +1.06 GiB** |
| Inference latency — first request | 7 990 ms |
| Inference latency — median (smoke set, n=18) | **9 792 ms** |
| Inference latency — max (smoke set) | 10 333 ms |
| Inference latency — median (repeat set, n=12) | **10 086 ms** |
| Inference latency — p95 (repeat set) | **10 127 ms** |
| First-token latency / tokens-per-sec | **not exposed** — `getBenchmarkInfo()` returned no data for this configuration (fields came back unset) |
| Model unload time | **204 ms** |
| Memory released on unload | **1 350 879 kB ≈ 1.29 GiB** (1 924 162 kB → 573 283 kB) |
| Reload after unload | **1 518 ms** |
| Thermal state | battery 35.2 °C at start; no throttling observed, no latency drift across 30+ inferences |

> `BenchmarkInfo` exists in the API and is wired up in `OnDeviceIntentModel`, but returned
> unpopulated values on this build/backend. TTFT and tokens/sec are therefore reported as
> **not available** rather than estimated from wall-clock. Total latency is measured
> directly and is the figure that matters for the demo.

## 2. Repeated-inference stability (n = 12, identical prompt)

```
10114, 10063, 10047, 10120, 10127, 10614, 10086, 10112, 10080, 10085, 10050, 10062  (ms)
```

| Aspect | Result |
|---|---|
| First-half mean | 10 180.8 ms |
| Second-half mean | **10 079.2 ms** — no upward drift |
| Spread | 567 ms across 12 runs (one 10 614 ms outlier) |
| PSS growth over 12 inferences | **+3 047 kB ≈ +3 MB** — no meaningful leak |
| Distinct outputs | **1** — fully deterministic at `temperature = 0.0, topK = 1, seed = 1234` |
| Engine usable afterwards | yes |

## 3. Backend comparison

| Backend | Load (warm) | Mean inference | PSS loaded | Verdict |
|---|---|---|---|---|
| **CPU, 4 threads** | 1 501 ms | **10 049 ms** | 628 137 kB | ✅ **selected default** |
| CPU, 8 threads | 1 474 ms | 10 534 ms | 627 912 kB | slower — big.LITTLE contention |
| GPU | 10 341 ms | — | 588 731 kB | ❌ **initializes but cannot infer** |

GPU failure, verbatim:

```
LiteRtLmJniException: Failed to call nativeSendMessage:
UNKNOWN: UNKNOWN: Can not find OpenCL library on this device
```

Two lessons recorded rather than smoothed over:

1. **A successful `initialize()` is not proof a backend works.** The GPU engine loaded in
   10.3 s and then failed on every single inference. Only real inference proves a backend.
2. **More threads is not faster.** 8 threads was consistently ~5 % slower than 4 on this
   big.LITTLE SoC.

## 4. Memory profile

| Point | Total PSS |
|---|---|
| Before load | 177 MiB |
| After load | 1.24 GiB |
| After 12 inferences | 1.83 GiB (working buffers + KV cache) |
| After `unload()` | **560 MiB** |

Against 7.44 GiB total / ~3.87 GiB available, a ~1.3–1.8 GiB working set fits with
headroom. `unload()` returns 1.29 GiB promptly, so the low-memory strategy
("close the engine, keep the app") is viable and measured, not hoped for.

## 5. Honest assessment for the demo

**Works reliably.** 33/33 generations were schema-conformant, zero crashes, zero OOM,
deterministic output, clean unload/reload, negligible memory growth over repeated use.

**Latency is the weak point at ~10 s.** The dominant cost is prefilling the ~500-token
system instruction on every request. It is tolerable for this specific product shape —
the user reads a plan and presses START, so nothing is blocked on the model — but it is
targeted for reduction in 10B by shortening the prompt, which also addresses the accuracy
problems seen in 10A.

No performance promise is made beyond these measured numbers.
