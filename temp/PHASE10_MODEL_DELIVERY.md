# PHASE 10 PREP — MODEL DELIVERY STRATEGY

**Date:** 2026-08-15
**Guide:** `temp/THRAKSHA_DEMO_IMPLEMENTATION_PHASE_10_PREP.md` §8, §9 (authoritative)
**Artifact:** `Gemma3-1B-IT_multi-prefill-seq_q4_ekv4096.litertlm` — 557.34 MiB,
SHA-256 `1325ae366d31950f137c9c357b9fa89448b176d76998180c08ceaca78bba98be`

---

## 1. Options evaluated

### Option A — developer-side provisioning (`adb push`)

The developer pushes the model to an app-readable location before the demo.

| Criterion | Assessment |
|---|---|
| APK size impact | **none** — APK grows only by the ~21 MB LiteRT-LM native lib |
| Install time | unchanged |
| Storage duplication | **none** — exactly one copy on the device |
| Update complexity | replace one file + update the pinned SHA-256; no app rebuild |
| Sandbox accessibility | **proven** — pushed to `/sdcard/Android/data/com.thraksha.guardian/files/models/`, owned `u0_a331`, opened by the app via `run-as` (`LITERTLM` header read back) with **zero storage permissions** |
| Reproducibility | one documented command; hash-verified on device |
| Demo reliability | **high** — provisioned and verified ahead of time; nothing happens during the demo |
| Legal posture | **best** — the weights are never redistributed inside a build artifact (see `model/MODEL_ATTRIBUTION.md` §4) |
| Measured cost | **12.7 s** transfer at ≈44 MB/s; on-device SHA-256 re-verify **0.81 s** |

### Option B — packaged asset (`app/src/main/assets`)

| Criterion | Assessment |
|---|---|
| APK size impact | **+557 MiB** — the APK would exceed 600 MB |
| Install time | minutes; also risks `INSTALL_FAILED_INSUFFICIENT_STORAGE` on tighter devices |
| Storage duplication | **~2×** — assets are stored compressed-or-not inside the APK **and** must typically be copied out to a real path, because `EngineConfig` takes a **file path**, not an `AssetFileDescriptor`. Assets inside an APK have no plain filesystem path. |
| Update complexity | any model change forces a full app rebuild + reinstall |
| Sandbox accessibility | requires an extract-on-first-run step anyway → collapses into Option C with a worse footprint |
| Reproducibility | good, but the build becomes slow and unwieldy |
| Demo reliability | **poor** — a 600 MB install right before a demo is exactly the wrong risk |
| Legal posture | **worst** — bundling the weights into a distributable artifact is redistribution, which is unresolved pending Gemma Terms review |
| Verdict | **REJECTED** |

### Option C — first-run local import

App copies the model from a user-visible location (Downloads, SAF picker) into its own
storage on first launch.

| Criterion | Assessment |
|---|---|
| APK size impact | none |
| Install time | unchanged |
| Storage duplication | **2×** during/after import (source + internal copy) — ~1.1 GB unless the source is deleted |
| Update complexity | moderate; needs import UI + progress + error handling |
| Sandbox accessibility | good after import |
| Reproducibility | depends on a human doing a file-picker dance correctly |
| Demo reliability | **medium** — introduces a multi-minute copy and a UI flow that can fail on stage |
| Legal posture | fine |
| Verdict | **not selected as primary**; retained as a documented fallback if the external-dir path ever proves problematic (see §4) |

## 2. Decision

**Option A — developer-side provisioning into the app-specific external files
directory — is selected.**

It is the only option with zero APK impact, zero duplication, no redistribution, a
proven sandbox read, and a measured 12.7 s provisioning cost. It also matches the
guide's stated bias (§8: "developer-provisioned local model file, not a giant APK").

## 3. The one authoritative model location

```
/sdcard/Android/data/com.thraksha.guardian/files/models/Gemma3-1B-IT_multi-prefill-seq_q4_ekv4096.litertlm
```

which the app resolves — and **only** ever resolves — as:

```kotlin
File(context.getExternalFilesDir("models"), MODEL_FILE_NAME)
```

Rules frozen here (guide §9):

1. **One path, one resolver.** No path string is written anywhere else in UI or runtime
   code. Everything goes through `LocalModelProvider`.
2. **No fallback search.** If that exact file is absent, the state is `MISSING`. The app
   does **not** scan Downloads, does **not** try a second directory, and **never
   silently loads a different model**.
3. `getExternalFilesDir` needs **no runtime permission** on any supported API level, and
   Android deletes the directory on uninstall — clean lifecycle, no orphaned 557 MiB.

### Provisioning command (the reproducible one-liner)

```bash
adb -s <serial> shell mkdir -p /sdcard/Android/data/com.thraksha.guardian/files/models
adb -s <serial> push Gemma3-1B-IT_multi-prefill-seq_q4_ekv4096.litertlm \
    /sdcard/Android/data/com.thraksha.guardian/files/models/
adb -s <serial> shell sha256sum \
    /sdcard/Android/data/com.thraksha.guardian/files/models/Gemma3-1B-IT_multi-prefill-seq_q4_ekv4096.litertlm
# expect: 1325ae366d31950f137c9c357b9fa89448b176d76998180c08ceaca78bba98be
```

> Windows/Git-Bash note: MSYS rewrites `/sdcard/...` into a Windows path. Run these from
> PowerShell, or prefix with `MSYS_NO_PATHCONV=1`. This bit us once during prep.

**Order matters:** the app must be installed first so the package's external data
directory exists.

## 4. Known risk and its mitigation

The app-specific external directory is served through Android's emulated/FUSE storage
layer. Native code (`liblitertlm_jni.so`) will `open()`/`mmap()` the 557 MiB file from
there. **Whether mmap-backed weight loading over emulated storage is as fast and as
stable as internal storage is unmeasured.**

Mitigation, to be decided by measurement during bring-up:

* **If load time and stability are good** (expected): keep Option A exactly as
  specified — single copy, no import step.
* **If mmap over emulated storage is slow or unreliable:** switch to the
  **A+C hybrid** — developer still pushes to the external dir; on first launch the app
  copies once into internal `filesDir/models/`, re-verifies the SHA-256 there, and
  thereafter loads from internal storage. Cost: a one-time copy and ~557 MiB extra until
  the external copy is deleted. The authoritative-path rule in §3 still holds; only the
  resolved directory changes, still behind `LocalModelProvider`.

This decision is explicitly deferred to evidence rather than guessed now.

## 5. `LocalModelProvider` — the path/integrity abstraction (guide §9)

Prep phase implements **only** path resolution + integrity + state. **No inference, no
automation coupling.**

```kotlin
// Sketch — the contract, not final code.
object LocalModelProvider {
    const val MODEL_FILE_NAME = "Gemma3-1B-IT_multi-prefill-seq_q4_ekv4096.litertlm"
    const val EXPECTED_SHA256 = "1325ae366d31950f137c9c357b9fa89448b176d76998180c08ceaca78bba98be"
    const val EXPECTED_BYTES  = 584_417_280L

    fun modelFile(context: Context): File            // the ONE path (§3)
    suspend fun verify(context: Context): ModelState // MISSING / ERROR / READY
    val state: StateFlow<ModelState>
}
```

Verification sequence — cheap checks first, so a wrong file fails fast:

1. **exists?** → no ⇒ `MISSING`
2. **length == 584 417 280?** → no ⇒ `ERROR` ("MODEL INVALID — unexpected size")
3. **first 8 bytes == `LITERTLM`?** → no ⇒ `ERROR` ("MODEL INVALID — not a LiteRT-LM container")
4. **SHA-256 == pinned digest?** → no ⇒ `ERROR` ("MODEL INVALID — checksum mismatch")
5. all pass ⇒ `READY`

Measured cost of step 4 on the S20 FE: **0.81 s** for 557.34 MiB — cheap enough to run
on every app start. Steps 1–3 are effectively free and catch the common failures (not
provisioned, truncated push, wrong file) without hashing.

**Non-negotiable:** a failed check never degrades into "load it anyway". Phase 9's
deterministic routines stay fully usable in every failure state
(`temp/PHASE10_OFFLINE_VERIFICATION.md` §4).

## 6. Integrity strategy summary

| Layer | Control |
|---|---|
| Source of truth | the pinned SHA-256 in `temp/PHASE10_MODEL_INTEGRITY.md` §1, duplicated as a constant in `LocalModelProvider` |
| Where checked | on device, over the real bytes, at app start and before every load |
| Sidecar files | **none** — no `.sha256` file next to the model that an attacker/mistake could edit in step with it |
| On mismatch | `ERROR` → "MODEL INVALID" → model never loaded, no fallback model, automation unaffected |
| On model replacement | regenerate the integrity doc and update the constant — a reviewed, deliberate change (guide §27) |
| Repo safety | `/model/*.litertlm` gitignored; verified untracked and unstaged |

## 7. Verdict

**No delivery blocker.** The chosen path is proven end-to-end on the target handset:
pushed in 12.7 s, byte-identical on arrival, readable by the app with no permissions,
re-verifiable in 0.81 s, 93 GB of headroom left, zero APK impact, and no redistribution
of the weights.
