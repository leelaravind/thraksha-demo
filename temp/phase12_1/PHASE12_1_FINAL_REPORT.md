# PHASE 12.1 — FINAL REPORT

Date: 2026-08-16
Guide: `temp/THRAKSHA_DEMO_IMPLEMENTATION_PHASE_12_1_RC2.txt`
Devices: Samsung Galaxy S20 FE `RZCW40LVBJD` (SM-G781B, Android 13) · DO AVD `emulator-5554`

---

## Verdict

**PASS — RC2 REAL-WORLD PRIVATE ALPHA READY**

All three tasks completed and every §31 acceptance criterion verified against the **actual
release APK** on the real phone, not a debug proxy.

---

## 1. Package visibility — before / after

| Metric | Ground truth | RC1 (release) | **RC2 (release)** |
| --- | --- | --- | --- |
| Total packages | 441 | 322 | **441** ✅ |
| System packages | 415 | 319 | **415** ✅ |
| Third-party packages | 26 | 3 | **26** ✅ |
| Genuine owner-installed apps | 23 | **0** | **23** ✅ |
| `VisibilityScope` | — | `REDUCED` | `FULL` |

Exact parity with ground truth. The 119-package gap is closed completely.

**Cause, proven four independent ways** (not assumed): `QUERY_ALL_PACKAGES` was declared
only in `app/src/debug/AndroidManifest.xml`, a build-type overlay that never merges into
`release`. Ruled out as causes: product flavors, `src/release/`, `sourceSets`,
`manifestPlaceholders`, R8, and any `BuildConfig.DEBUG` branch in the scanner — there is
none; `AppInventory` was honestly reporting `REDUCED`. RC1's 322 = 319 force-visible system
packages + Thraksha + 2 declared decoys, matching exactly with no residue.

**Fix:** permission moved to the main manifest with in-place justification; debug overlay
deleted; targeted `<queries>` retained as fallback; two stale KDoc blocks corrected. **No
behavioural code changed** — the scanner needed none.

Play policy documented as a **separate, unmet, future** requirement. No declaration
submitted, no review passed, no approval implied.

## 2. Relevant third-party apps visible and genuinely scanned

All **23** genuine owner-installed apps. Depth verified, not just breadth: **441 signing
identities checked, 441 APK fingerprints computed** — every package actually inspected.

Real apps analysed with honest classifications: Google TV (REVIEW), Samsung Notes (REVIEW),
Tips (REVIEW), plus Facebook, LinkedIn, Outlook, Office, Samsung Pay/Internet/Health,
Google Photos/Docs and the rest. Every finding is phrased as what the app is *currently
allowed* to do — DECLARED/GRANTED, never fabricated behaviour. `High-exposure profiles: 0`.

## 3. Signing identity / fingerprint

| Field | Value |
| --- | --- |
| Signer | `CN=Thraksha Guardian, OU=Private Alpha, O=Thraksha, L=Tirumula, ST=Andhra Pradesh, C=IN` |
| Certificate SHA-256 | `0d96977d15ce5b17c4167a03f70fc72401f580a8d4f2258e059fa113516ac928` |
| Certificate SHA-1 | `c7bafcb654883e0b67758471d1ee129e6e53b2d7` |
| Key | RSA **4096** |
| Schemes | v1 false, **v2 true, v3 true** |
| On-device | `apkSigningVersion=3` |

**NOT `CN=Android Debug`.** ✅ No debug-signing fallback exists: `signingConfig` is `null`
without credentials and a hard gate fails the build — verified by running
`:app:assembleRelease` with no credentials and confirming `BUILD FAILED` with no artifact
produced.

Keystore lives outside the repo; `keystore.properties` untracked and git-ignored; the build
**fails at configuration time** if `storeFile` ever resolves inside `rootDir`.

## 4. RC2 APK SHA-256

```
93c15f227bc724ef02eb5cb79cabcac308f2dd53aa531a19d0665da8ed05c7de
```

`thraksha-guardian-1.0-privatealpha-rc2.apk` · 67,476,953 bytes · 955 entries ·
`com.thraksha.guardian` · versionCode **2** / versionName **`1.0-privatealpha-rc2`** ·
ABIs `arm64-v8a`, `x86_64` · **debuggable=false** · model **not** bundled · built from a
clean tree.

**APK content audit: clean.** 0 hits across entry-name and deep-binary scans for private
keys, the key alias, `keystore.properties`, passwords, Stitch/MCP/Claude artefacts,
`temp/`, screenshots, evaluation files, test hooks and model weights. Only X.509 **public**
keys present.

## 5. Real scan result

441 apps checked · 56 findings · **1 known-threat match** (VillainCaller only) · 0
high-exposure profiles · 13.6 s. GoodCaller correctly not flagged. Signed rulepack v2 and
threatpack verified through the production loader.

**Non-destructive:** 0 packages suspended, 0 disabled, count still 441, every real app
`installed=true suspended=false`. No automatic containment of arbitrary apps — enforced in
code, proven on the DO AVD by `arbitraryPackage_isRefused`,
`goodCaller_isRefusedAsTarget_regardlessOfAuthority`, `guardian_cannotTargetItself` and
`restore_refusesNonAllowlistedTargets` passing *with* Device Owner authority.

**Advice Mode is the default** on a fresh install; `dpm list-owners` → "no owners".

## 6. Automation restore result

Meeting Mode, on the release build:

1. **Fresh install correctly refused to run** — "CANNOT RUN YET", required access missing.
   No auto-execution, no silent degradation.
2. After granting the two special accesses: preview showed 4 changes all READY and
   **changed nothing** (`zen=0 brightness=101 bmode=1` before and after opening it).
3. **Explicit START** → real Android changes: `zen_mode 0→1`, `brightness 101→77`,
   `brightness_mode 1→0`, calendar launched. App reported 3 × VERIFIED + 1 × OPENED with
   the honest caveat *"launch fired; foreground arrival is not claimed"*.
4. **STOP & RESTORE** → app-verified restoration: DND *"verified back to interruption
   filter: ALL (DND off)"*, brightness *"verified back to brightness: 101/255, mode: AUTO"*,
   ringer *"verified back to ringer mode: NORMAL"* — exactly the pre-START values.

## 7. Offline AI result

Airplane mode enabled, `ping 8.8.8.8` → **"Network is unreachable"**. Asked *"I have a
meeting for 45 minutes"*:

* correct intent extracted — **"THRAKSHA UNDERSTOOD — Meeting Mode — 45 minutes"**;
* plan preview with 4 READY actions and an explicit START button;
* **no auto-execution** — settings unchanged during preview and after cancelling
  (`zen=0 bmode=1` throughout).

Model: SHA-256 `181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c`, matching
`LocalModelRepository.EXPECTED_SHA256` exactly; app reports **"Integrity verified"**.
Connectivity restored afterwards.

## 8. Reboot result

* **No unexpected auto-start** — 0 services, no process after boot.
* Model still present (2,588,147,712 bytes).
* App launches cleanly, **no crash, no ANR**.
* **Encrypted audit persists** — pre-reboot entries (04:04, 04:07) still present beneath
  new post-boot entries.
* Scanner unchanged: 441 / 26 / 415, 56 findings, 1 known-threat.
* **No device settings modified**: `zen=0 bmode=1 timeout=600000 airplane=0 wifi=1 bt=1`,
  identical to baseline.

## 9. Tests

| Suite | Result |
| --- | --- |
| Clean build (`clean` → `assembleRelease`) | **BUILD SUCCESSFUL** — produced RC2 |
| Unit (`testDebugUnitTest`) | **213 tests, 0 failures, 0 errors, 0 skipped** |
| S20 FE instrumented (pre-RC2, full) | **90 tests, 0 failures, 0 errors**, 3 assume-skips |
| Device Owner AVD (post-RC2, full) | **OK (90 tests), 0 failures** |
| Crashes / ANRs (whole session) | **0** |

Phase 10B evaluation and the Phase 10A benchmark excluded per §28. No S20 FE skip is left
unproven — the two Device-Owner-gated cases passed on the AVD.

The S20 FE instrumented suite was deliberately **not** re-run after RC2: it would have
uninstalled RC2 and destroyed the model and audit chain. Phone coverage came instead from
direct verification on the shipped release APK, which is stronger evidence than a debug
proxy.

## 10. Wording

Two overclaims found in `WhitelistingDialog.kt`, not the one reported: the Samsung *"eyes
and ears active 24/7"* line **and** *"This ensures the AI stays awake…"*. Both replaced
with accurate wording plus an explicit non-guarantee, and **verified live on RC2**
(`screenshots/12_1_wording_dialog.png`).

§19 sweep over 26 claim patterns found **no other user-facing overclaim** — every other hit
is a code comment, factual state reporting, or an existing disclaimer. The Protect
surface's documented no-absolute-claims invariant holds. Nothing unrelated was rewritten.

## 11. Unresolved limitations

1. **Play policy is unaddressed.** `QUERY_ALL_PACKAGES` is restricted; device-security is a
   permitted use case but no declaration has been submitted and no review passed. This
   build is sideloaded, owner-installed only. Not a defect — out of scope by design.
2. **Key continuity is now a live risk.** Every future private-alpha update must use this
   same key. Losing it means no future build can update an install; the app must be
   uninstalled, destroying the encrypted DB and audit chain. Back it up off-laptop.
3. **The two automation special accesses were revoked** after testing (I granted them via
   adb, the owner never did). Meeting/Focus/Driving will correctly show "CANNOT RUN YET"
   until granted in-app — one tap from the plan preview.
4. **RC1's data was destroyed**, as §23 required. Additionally, the pre-RC2 instrumented
   run uninstalled RC1 earlier than planned and took the 2.4 GiB model with it — recovered
   and hash-verified. Documented as an amendment in `evidence/PRE_RC2_DEVICE_STATE.txt`.
5. **Model is developer-provisioned**, not bundled. A fresh install on another phone needs
   the 2.4 GiB push.
6. **Phase 10B/10A-benchmark not re-run** per §28.
7. **RC2 is not byte-for-byte reproducible** (build timestamp, signature salt). Inputs are
   pinned.

## 12. Final device state

### S20 FE — clean

| Item | State |
| --- | --- |
| RC2 | installed, `1.0-privatealpha-rc2` (2), signer `CN=Thraksha Guardian` |
| Decoys | GoodCaller + VillainCaller installed, untouched, `suspended=false` |
| Packages | 441 — unchanged, **0 suspended, 0 disabled** |
| Device Owner | **none** — Advice Mode |
| Settings | `zen=0 bmode=1 timeout=600000 airplane=0 wifi=1 bt=1` — baseline |
| Active routines | none (0 alarms) |
| Model | present, SHA verified |
| Test APKs | none |
| Special accesses | revoked (restored to owner-chosen state) |
| Crashes/ANRs | 0 |

### DO AVD — clean

Device Owner intact · VillainCaller `suspended=false` · GoodCaller untouched · test APK
uninstalled.

## 13. Git status

**Nothing committed. Nothing staged.** `git diff --cached` empty.

| File | Change |
| --- | --- |
| `app/src/main/AndroidManifest.xml` | M — permission + justification |
| `app/src/debug/AndroidManifest.xml` | D — redundant overlay |
| `app/src/main/java/…/inventory/AppInventory.kt` | M — KDoc only |
| `app/build.gradle.kts` | M — signing config, gate, versioning |
| `app/src/main/java/…/ui/components/WhitelistingDialog.kt` | M — wording |
| `app/src/androidTest/java/…/DeviceScanInstrumentedTest.kt` | M — test rename |
| `keystore.properties.template` | A — no values |
| `temp/THRAKSHA_DEMO_PHASE_12_1_PROGRESS.md`, `temp/phase12_1/**` | A — docs/evidence |

`keystore.properties` is present but **untracked and git-ignored**. No signing material in
any tracked file, source file, manifest, document, log or the APK.

## 14. Is RC2 safe for everyday-phone private-alpha installation?

**Yes**, for the owner's own device, with the limitations in §11 understood.

It is properly signed with a key under the owner's control, not debuggable, contains no
secrets or development artefacts, defaults to Advice Mode with no Device Owner, scans
without modifying anything, never auto-executes, restores exactly what it changes, works
fully offline, and survives a reboot with its audit intact.

---

**PASS — RC2 REAL-WORLD PRIVATE ALPHA READY**
