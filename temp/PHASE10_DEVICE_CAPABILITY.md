# PHASE 10 PREP — S20 FE DEVICE CAPABILITY

**Date:** 2026-08-15
**Guide:** `temp/THRAKSHA_DEMO_IMPLEMENTATION_PHASE_10_PREP.md` §7, §18 (authoritative)
**Device:** Samsung Galaxy S20 FE, serial `RZCW40LVBJD`
**Method:** live `adb` probes of the attached handset. Values are transcribed, not
estimated.

---

## 1. Platform

| Property | Value | Source |
|---|---|---|
| Model | `SM-G781B` | `ro.product.model` |
| Manufacturer | `samsung` | `ro.product.manufacturer` |
| Android version | **13** | `ro.build.version.release` |
| API level | **33** | `ro.build.version.sdk` |
| SoC | **Qualcomm SM8250** (Snapdragon 865 5G), platform `kona` | `ro.soc.model`, `ro.board.platform` |
| CPU cores | **8** | `/proc/cpuinfo` |
| Primary ABI | **`arm64-v8a`** | `ro.product.cpu.abi` |
| 64-bit ABI list | `arm64-v8a` | `ro.product.cpu.abilist64` |
| Full ABI list | `arm64-v8a, armeabi-v7a, armeabi` | `ro.product.cpu.abilist` |

**Runtime match:** LiteRT-LM `0.16.0` ships `jni/arm64-v8a/liblitertlm_jni.so`.
The device's primary ABI is `arm64-v8a` → **native library available** ✅
The AAR's `minSdkVersion 24` ≤ device API 33 ✅ (and ≤ project `minSdk 26`).

## 2. Memory

| Property | Value |
|---|---|
| `MemTotal` | 7 806 008 kB ≈ **7.44 GiB** |
| `MemAvailable` at probe | 4 062 496 kB ≈ **3.87 GiB** |
| `MemFree` at probe | 325 540 kB ≈ 0.31 GiB |
| `dalvik.vm.heapsize` | `512m` |
| `dalvik.vm.heapgrowthlimit` | `256m` |

**Assessment.** The model file is 557.34 MiB and the weights load into **native**
memory (allocated by `liblitertlm_jni.so`), not the Dalvik heap — so the 256 MB
heap-growth limit is not the binding constraint and `largeHeap` is not the remedy.
With ≈3.87 GiB available, a ~700 MiB resident footprint is **plausible with headroom**,
but it is **not measured yet** and is not being claimed. Low-memory kill behaviour under
a foreground-service + VPN + model process must be observed, not assumed.

## 3. Storage

| Property | Value |
|---|---|
| `/data/user/0` filesystem | `/dev/block/sda36` |
| Size | **107 GB** |
| Available (after the model was pushed) | **93 GB** |
| Use | 13 % |
| Model requirement | 557.34 MiB (one copy) |

Headroom is ~170× the model size. Storage is a non-issue on this handset, which is why
the delivery decision can favour reliability over byte-thrift
(`temp/PHASE10_MODEL_DELIVERY.md`).

## 4. GPU / graphics capability

| Property | Value | Source |
|---|---|---|
| GPU | **Qualcomm Adreno (TM) 650** | SurfaceFlinger `GLES:` line |
| OpenGL ES | **3.2** (driver `V@0502.11.5`, dated 2025-05-11) | SurfaceFlinger |
| `ro.opengles.version` | `196610` = **0x30002 → GLES 3.2** | getprop |
| Vulkan version feature | `4198400` = `VK_MAKE_VERSION(1,1,0)` → **Vulkan 1.1** | `pm list features` |
| Vulkan level | `android.hardware.vulkan.level=1` | `pm list features` |
| Vulkan compute | `android.hardware.vulkan.compute` **present** | `pm list features` |
| AEP | `android.hardware.opengles.aep` **present** | `pm list features` |
| Vulkan HAL | `ro.hardware.vulkan = adreno` | getprop |

**Assessment.** The device clears the graphics bar for a GPU-accelerated LLM backend
(Adreno 650 with GLES 3.2 + Vulkan 1.1 compute; Adreno 650 also exposes OpenCL, which is
what LiteRT's Qualcomm GPU path typically uses). **Which backend is actually faster and
more stable for this model on this phone is an open measurement**, see §6. Default to
`Backend.CPU()` for the demo unless GPU measurably wins — 8 cores at Snapdragon 865 class
is a credible CPU target for a 1B q4 model, and CPU avoids driver variability on stage.

`Backend.NPU` is **out of scope** — it needs vendor NPU/QNN native libraries this project
does not ship.

## 5. Model delivery on this device — PROVEN

| Step | Result |
|---|---|
| Destination | `/sdcard/Android/data/com.thraksha.guardian/files/models/` (app-specific external dir) |
| Push | 584 417 280 bytes in **12.7 s** (≈ 44 MB/s over USB) |
| File owner on device | `u0_a331 sdcard_rw` — the Guardian app's own uid |
| Permissions needed by the app | **none** (app-specific dir; no `READ_EXTERNAL_STORAGE`, no `MANAGE_EXTERNAL_STORAGE`) |
| On-device SHA-256 | `1325ae366d31950f137c9c357b9fa89448b176d76998180c08ceaca78bba98be` — **identical to host** ✅ |
| SHA-256 duration on device | **0.81 s** for 557.34 MiB |
| Read as the app | `run-as com.thraksha.guardian head -c 8 …` → `LITERTLM` ✅ |
| Free space after | 93 GB |

This proves three things that matter for Phase 10: the artifact arrives **byte-intact**,
the **app process can open it with zero storage permissions**, and the **integrity gate
is cheap enough (< 1 s) to run on every launch** before declaring the model READY.

## 6. Runtime feasibility — status and what is NOT claimed

| Question | Status |
|---|---|
| Correct ABI present in the official AAR | **YES** (arm64-v8a) |
| minSdk satisfied | **YES** (24 ≤ 33) |
| Enough RAM in principle | **YES** (~3.87 GiB available vs ~0.7 GiB expected) |
| Enough storage | **YES** (93 GB free) |
| GPU capable in principle | **YES** (Adreno 650, GLES 3.2, Vulkan 1.1 compute) |
| Model file readable by the app | **YES — proven on device** |
| **LiteRT-LM `Engine.initialize()` succeeds on this phone** | **NOT YET PROVEN — NOT CLAIMED** |
| **Inference latency acceptable** | **NOT MEASURED — NOT CLAIMED** |

Per guide §7 ("No model execution claim until runtime initialization succeeds on the
phone"), Thraksha must not display "On-device AI ready" until the bring-up below passes.
The app build was intentionally left frozen in this prep run, so initialization is the
one deferred item; it is fully specified in `temp/PHASE10_LITERT_RUNTIME.md` §8.

## 7. Performance baseline plan (guide §18)

Metrics to capture on the S20 FE during Phase 10 bring-up, per backend
(`Backend.CPU(threadCount = 4)`, `Backend.CPU(threadCount = 8)`, `Backend.GPU()`):

| # | Metric | How |
|---|---|---|
| 1 | Integrity-check time | wall clock around the SHA-256 read (baseline already measured: **0.81 s** via shell) |
| 2 | Model load time | `BenchmarkInfo.initTimeInSecond` + wall clock around `Engine.initialize()` |
| 3 | RAM before load | `Debug.MemoryInfo` / `dumpsys meminfo com.thraksha.guardian` PSS+RSS |
| 4 | RAM after load | same, immediately after `isInitialized()` |
| 5 | RAM after N inferences | same, after 10 sequential intents (leak check) |
| 6 | First-token latency | `BenchmarkInfo.timeToFirstTokenInSecond` |
| 7 | Total intent latency | wall clock, user text → validated `AutomationIntent` (includes JSON parse + schema validation) |
| 8 | Prefill tokens/sec | `BenchmarkInfo.lastPrefillTokensPerSecond` |
| 9 | Decode tokens/sec | `BenchmarkInfo.lastDecodeTokensPerSecond` |
| 10 | Prompt token budget used | `Conversation.getTokenCount()` vs the 4096 `ekv` ceiling |
| 11 | CPU/GPU utilisation | `dumpsys cpuinfo` / `simpleperf` sampling during a run |
| 12 | Thermal behaviour | `dumpsys thermalservice` + battery temperature before/after 20 consecutive requests (baseline at probe: **35.2 °C**, battery 83 %) |
| 13 | Unload/reload | `close()` → RAM returns to baseline → re-`initialize()` succeeds |
| 14 | Cold vs warm load | first load after reboot vs subsequent (page cache effect) |

Demo-acceptance targets to be **set after** the first measurements, not before
(guide §18: "Do not promise acceptable performance before measurement"). The one hard
requirement already known: total intent latency must stay short enough that the
**plan preview appears responsive**, because the user still has to read the plan and
press START — the model is never on the critical path of executing anything.

## 8. Secondary device — `thraksha_do` AVD

The Device Owner AVD is `x86_64`, and the AAR ships `jni/x86_64/liblitertlm_jni.so`, so
the runtime is loadable there too. Emulator inference performance is **not**
representative of the handset and must never be quoted as a demo figure; the AVD's role
stays what it is in Phase 9 — Device Owner behaviour verification.

## 9. Verdict

**No device blocker.** The S20 FE meets every statically checkable requirement (ABI, API
level, RAM, storage, GPU class), and the model has been proven to arrive intact and be
readable by the app sandbox with no permissions. The remaining unknown — actual runtime
initialization and inference speed — is scheduled as the first implementation step and is
explicitly not claimed here.

**Device state note:** these probes were read-only apart from writing the model file into
the app's own external files directory. No system setting was changed; the Phase 9
"leave the phone as found" state is intact.
