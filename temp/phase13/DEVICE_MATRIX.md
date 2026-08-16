# PHASE 13 — DEVICE MATRIX

Guide reference: §3. Captured 2026-08-16.

## Baseline artifact (frozen — guide §2)

| Field | Value |
| --- | --- |
| APK | `thraksha-guardian-1.0-privatealpha-rc2.apk` |
| **SHA-256** | `93c15f227bc724ef02eb5cb79cabcac308f2dd53aa531a19d0665da8ed05c7de` |
| Re-verified at Phase 13 start | ✅ **matches** — artifact unmodified since Phase 12.1 |
| versionCode / versionName | `2` / `1.0-privatealpha-rc2` |
| Signer | `CN=Thraksha Guardian, OU=Private Alpha, O=Thraksha, L=Tirumula, ST=Andhra Pradesh, C=IN` |
| Signer cert SHA-256 | `0d96977d15ce5b17c4167a03f70fc72401f580a8d4f2258e059fa113516ac928` |
| Schemes | v2 + v3 |
| Model | `gemma-4-E2B-it.litertlm`, SHA-256 `181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c` (2,588,147,712 B) |
| Runtime | LiteRT-LM `com.google.ai.edge.litertlm:litertlm-android:0.16.0` |

RC2 is **not** rebuilt during Phase 13.

## Device A — Samsung Galaxy S20 FE (reference baseline)

| Attribute | Value |
| --- | --- |
| Serial | `RZCW40LVBJD` |
| Manufacturer / model | Samsung / **SM-G781B** (`r8qxxx`) |
| Android / API | **13** / **33** |
| Build | `TP1A.220624.014.G781BXXSIHYJ4` |
| **Security patch** | **2025-10-01** |
| SoC | **Qualcomm SM8250** (Snapdragon 865, `kona`), QTI |
| CPU | 8 cores, arm64 |
| ABI | **`arm64-v8a`** (abilist: arm64-v8a, armeabi-v7a, armeabi) |
| RAM | **7,806,024 kB ≈ 7.44 GiB** (MemAvailable at capture ≈ 3.77 GiB) |
| Storage `/data` | 107 G total, 16 G used, **91 G free** (15%) |
| GPU | **Adreno (TM) 650**, OpenGL ES 3.2 |
| Accelerator features | `android.hardware.vulkan.compute`, Vulkan level 1 / version 4198400. **No NNAPI/OpenCL feature flag advertised.** |
| Thermal status at start | 0 (NONE) |
| Thraksha state | RC2 installed, versionCode 2, `apkSigningVersion=3`, **Advice Mode**, **no Device Owner** |

### OEM behaviour notes (Device A)

* **One UI battery optimisation** actively pauses background services unless the app is
  exempted — this is what the corrected Samsung dialog now explains honestly.
* `settings get system ringer_mode` returns `null`; ringer state is AudioManager-owned, so
  automation snapshots read it via `AudioManager`, not the settings provider.
* **Auto-brightness drifts** the `screen_brightness` value continuously while
  `screen_brightness_mode=1`. Restoration must be judged on the *mode* plus the app's own
  verified snapshot value, not on a raw re-read.
* adb may require a manual unlock / USB-debugging re-authorise after reboot before the
  device re-enumerates.

## Device B — Samsung Galaxy S24 Ultra (REQUIRED by guide §3)

| Attribute | Value |
| --- | --- |
| Status | ⛔ **NOT CONNECTED — not available to this session** |

`adb devices` at Phase 13 start listed only:

```
RZCW40LVBJD    device   product:r8qxxx  model:SM_G781B      (Device A)
emulator-5554  device   product:sdk_phone64_x86_64          (DO AVD — not a real device)
```

No second physical Android device is attached. This is a **hard blocker** for the parts of
Phase 13 that are definitionally multi-device:

* §3 Device B record;
* §5 cross-device visibility comparison;
* §6 `PHASE13_REAL_SCAN_COMPARISON.md` (a comparison needs ≥2 devices);
* §8 S24 Ultra LiteRT-LM backend testing (the only newer-silicon target);
* §10 OEM/API-level automation differences;
* §16 install/uninstall safety "on at least one secondary device";
* §20 "RC2 installs normally on **multiple real devices**".

Nothing about this can be substituted with the emulator: the AVD is x86_64, has no real
SoC/thermal/battery behaviour, is Device-Owner-provisioned (guide §4 forbids DO on the
validation path), and carries none of the OEM differences Phase 13 exists to surface.

**Not worked around, not simulated.** Device-A results are recorded in full so that
attaching an S24 Ultra later resumes Phase 13 rather than restarting it.

## Device C

Not applicable — optional per guide §3, and no third device is available.

## Summary

| Device | Role | Available | Phase 13 coverage |
| --- | --- | --- | --- |
| A — S20 FE (SM-G781B) | reference baseline | ✅ yes | full single-device validation |
| B — S24 Ultra | primary newer-performance target | ⛔ **no** | **none — blocked** |
| C — any third phone | optional | ⛔ no | n/a |
| DO AVD `emulator-5554` | Device-Owner regression only | ✅ yes | **not** counted as a Phase 13 target device |
