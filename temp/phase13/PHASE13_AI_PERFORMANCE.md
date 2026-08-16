# PHASE 13 — ON-DEVICE AI PERFORMANCE

Guide reference: §8. Build: frozen RC2, release. Model and runtime unchanged.

## Model under test (unchanged — guide §8 "use the exact approved model first")

| Field | Value |
| --- | --- |
| File | `gemma-4-E2B-it.litertlm` |
| Size | 2,588,147,712 B (2.41 GiB) |
| **SHA-256** | `181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c` |
| SHA verified on device | ✅ `sha256sum` on-device matches `LocalModelRepository.EXPECTED_SHA256` exactly |
| App-reported state | **"Local AI available — Integrity verified"** |
| Runtime | LiteRT-LM `0.16.0` |
| Location | `/sdcard/Android/data/com.thraksha.guardian/files/models/` (app external files dir) |

No model replacement was considered or performed.

## Device A — S20 FE (Snapdragon 865 / SM8250, Adreno 650, 7.44 GiB RAM)

### Measured

| Metric | Value | Method |
| --- | --- | --- |
| **SHA verification** | ✅ pass | on-device `sha256sum` + in-app "Integrity verified" |
| **Cold inference** (force-stop → launch → first result, includes model load) | **19 s** | wall-clock, UI-observed |
| **Warm inference** (model resident, same prompt) | **14 s** | wall-clock, UI-observed |
| Implied cold-load overhead | ≈ **5 s** | cold − warm |
| **Idle memory** (app running, model NOT loaded) | TOTAL PSS **108,543 kB ≈ 106 MB**, RSS 221,660 kB | `dumpsys meminfo` |
| **Model-loaded memory** | TOTAL PSS **3,058,602 kB ≈ 2.92 GB**, RSS 3,147,448 kB ≈ 3.00 GB, Native Heap 1,261,500 kB ≈ 1.20 GB | `dumpsys meminfo` |
| Memory delta from loading the model | ≈ **+2.81 GB PSS** | — |
| **Thermal status after repeated inference** | **0 (NONE)** — no throttling | `dumpsys thermalservice` |
| Additional latency samples | 12 s, 15 s (further UI-observed inferences) | wall-clock |
| Robustness | garbled input `"tI want to focus for 90 minutes "` still produced the correct **Focus Mode — 90 minutes** plan | observed |

Timing precision is ±2–3 s: RC2 is a release build and emits no inference timing to logcat,
so latency was measured by polling the UI's `WORKING` state. Values are honest wall-clock
user-perceived latency, not instrumented model time.

### Memory observation worth flagging

**PSS ≈ 2.92 GB on a 7.44 GiB device is ~39% of total RAM** for one app. It is dominated by
the mmap'd 2.41 GiB model, which is expected for this architecture, and the Phase 12
memory-pressure fix keeps it stable. But on a 6 GB or 4 GB device this would be at or beyond
the practical ceiling, and Android would be far more likely to kill the process. **Device A
is the roomiest realistic target tested; smaller-RAM behaviour is unmeasured.**

### NOT measured — honest gaps

| Guide §8 item | Status |
| --- | --- |
| **Median latency** | ⛔ insufficient samples (4 usable) |
| **p95 latency** | ⛔ needs ≥20 samples; the automated suite did not complete |
| **Repeated-inference stability** | ⛔ only partially observed |
| **Unload / reload cycle** | ⛔ not exercised |
| **Sustained thermal behaviour** | ⚠ status 0 after a handful of inferences only; no sustained load test |
| **CPU vs GPU/NPU backend comparison** | ⛔ **not measured** |

### On accelerated backends — measured, not assumed

Guide §8 says "do not assume GPU/NPU acceleration". Device A advertises:

```
android.hardware.vulkan.compute
android.hardware.vulkan.level=1
android.hardware.vulkan.version=4198400
```

and **no NNAPI or OpenCL feature flag**. GPU is Adreno 650. RC2 ships
`liblitertlm_jni.so` for arm64-v8a and x86_64 and the app exposes no backend selector, so
what RC2 actually runs on this device is the **default LiteRT-LM backend** — not a
demonstrated GPU/NPU path. **No claim of GPU or NPU acceleration is made**, and none was
observed.

## Device B — S24 Ultra

⛔ **NOT MEASURED — device not connected.**

This is the single largest evidence gap in Phase 13. Guide §8 names the S24 Ultra
specifically as the device on which "supported LiteRT-LM backends" are to be tested, and §3
calls it the "primary newer-performance target". Everything below is therefore unknown:

* latency on Snapdragon 8 Gen 3 (the whole point of measuring newer hardware);
* whether any GPU/NPU backend is genuinely usable on newer silicon;
* memory headroom on a 12 GB device;
* thermal behaviour under sustained inference on a modern SoC;
* cold-load time from faster storage.

The S20 FE numbers above are a **floor**, not a range.

## Summary

| Item | Device A | Device B |
| --- | --- | --- |
| Model SHA verified | ✅ | ⛔ |
| Cold load | 19 s | ⛔ |
| Warm inference | 14 s | ⛔ |
| Memory (model loaded) | 2.92 GB PSS | ⛔ |
| Thermal | 0 (no throttle, light load) | ⛔ |
| Median / p95 latency | ⛔ insufficient samples | ⛔ |
| Accelerated backend | none demonstrated | ⛔ |
