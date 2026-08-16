# THRAKSHA — PHASE 13 PROGRESS

Guide: `temp/THRAKSHA_DEMO_IMPLEMENTATION_PHASE_13.md`
Date: 2026-08-16
Baseline: **frozen RC2** — `93c15f227bc724ef02eb5cb79cabcac308f2dd53aa531a19d0665da8ed05c7de`

## VERDICT: **FAIL — PRIVATE BETA NOT READY**

Not because RC2 misbehaved — **no functional defect was found in RC2**, and everything that
could be exercised on Device A passed. Phase 13's PASS criteria are definitionally
multi-device and multi-day, and neither condition exists:

1. **Device B (S24 Ultra) is not connected.** §20 requires "RC2 installs normally on
   **multiple real devices**". One device is not multiple, and no comparison document
   (§5, §6, §10) can be produced from a single column.
2. **The §14 pilot needs 3–7 days** of real use. Zero days have elapsed. Fabricating soak
   duration is explicitly forbidden.

**RC2 remains frozen and unmodified.** No RC3 is proposed, because no defect requires one.

## RC2 integrity — re-verified at Phase 13 start

| Check | Result |
| --- | --- |
| APK SHA-256 | ✅ `93c15f22…` — unchanged since Phase 12.1 |
| Signer | ✅ `CN=Thraksha Guardian`, cert SHA-256 `0d96977d…` |
| Installed on Device A | ✅ versionCode 2, `apkSigningVersion=3`, Advice Mode, **no Device Owner** |
| Model SHA | ✅ `181938105e…` matches `EXPECTED_SHA256`; app reports "Integrity verified" |
| Source identity after the §15 test build | ✅ reverted to versionCode 2 and re-verified |

## Guide section status

| § | Item | Status |
| --- | --- | --- |
| 3 | Device matrix | ⚠ Device A complete; **Device B unavailable** |
| 4 | Installation (SHA, signer, Advice Mode, model) | ✅ Device A |
| 5 | Package visibility | ✅ Device A **441/441 exact parity**; ⛔ cross-device |
| 6 | Real-device scan | ✅ Device A (2 scans, deterministic); ⛔ comparison |
| 7 | False-positive review | ⚠ 13 candidates identified, **3 analysed**, 10 not characterised |
| 8 | AI performance | ⚠ cold/warm/memory/thermal measured; ⛔ median/p95, backends |
| 9 | Intent quality (25–40 prompts) | ⛔ **did not complete** — harness + device disconnects |
| 10 | Automation OEM matrix | ⚠ Meeting **full pass**; Focus partial; Driving untested |
| 11 | Reboot / process recovery | ✅ (Phase 12.1, same RC2 binary); ⛔ process death mid-routine |
| 12 | Network Guard | ⛔ **not tested** |
| 13 | Battery / memory / thermal | ⚠ memory + thermal measured; ⛔ **battery not measured** |
| 14 | Normal-use pilot | ⛔ **0 of 3–7 days** |
| 15 | Signing continuity | ⚠ same-key vc3 APK built + signer proven identical; ⛔ install not verified |
| 16 | Install/uninstall on secondary device | ⛔ no secondary device |
| 17 | UX review | ⚠ informal observations only |

## What passed, on the real release binary

* **Package visibility: exact parity** — 441 discovered / 441 ground truth, 26 user apps,
  415 system, all 23 genuine owner-installed apps visible. 441 signing identities checked
  and 441 APK fingerprints computed, so packages were genuinely inspected.
* **Scanner non-destructive and deterministic** — 0 suspended, 0 disabled, identical counts
  before and after a reboot. 1 known-threat match, and it is the controlled decoy; **zero**
  legitimate apps called a threat. `High-exposure profiles: 0`.
* **Meeting Mode end-to-end** — preview (side-effect free) → explicit START → real Android
  changes → 3 VERIFIED + 1 honestly-caveated OPENED → STOP & RESTORE → **exact restoration**.
  On a fresh install with no special access it correctly refused: **CANNOT RUN YET**.
* **On-device AI** — SHA verified, cold 19 s, warm 14 s, thermal 0, and correct intent
  extraction even from a corrupted prompt.
* **Stability** — 0 crashes, 0 ANRs, 0 dropbox entries.
* **Signing continuity precondition** — a versionCode-3 APK built from the same keystore has
  a byte-identical certificate to RC2.

## What blocked, and why

### Hard blocker 1 — Device B not connected
`adb devices` listed only Device A and the x86_64 DO AVD. The emulator cannot substitute:
wrong ABI, no real SoC/thermal/battery, Device-Owner provisioned (§4 forbids DO on the
validation path), and none of the OEM differences Phase 13 exists to find.

### Hard blocker 2 — pilot duration
0 of 3–7 days. Cannot be compressed or simulated.

### Environmental — Device A USB instability (ISSUE-13-001)
The S20 FE dropped off USB twice under sustained adb load and did not re-enumerate,
truncating the §9 intent suite, the remaining §7 false-positive analysis, §12 Network Guard,
and the §15 install verification. **Not a product fault** — no Thraksha code is involved —
but it materially limited evidence.

### My tooling fault — ISSUE-13-002
My first prompt harness used fixed screen coordinates with no foreground guard. When app
state diverged, it tapped blind and navigated into Samsung Account's consent screen. I
stopped it and verified the device was unchanged: **no account created** (zero `Account {}`
entries), settings still `zen=0 bmode=1 timeout=600000 airplane=0`, package count still 441.
Rewritten with a foreground guard, but the device disconnected before it completed. The
early "results" it produced were stale reads and have been discarded, not reported.

## To reach PASS

1. Connect the **S24 Ultra**; run §§4–13 on it and complete the comparison documents.
2. Stabilise Device A's USB link (or use wireless adb); finish the §9 suite, the remaining 10
   false-positive items, §12 Network Guard, Focus/Driving, and the §15 install check.
3. Run the **3–7 day pilot** on the owner's real phone, including unplugged battery
   measurement.

## Git status

**Nothing committed, nothing staged.** `app/build.gradle.kts` was temporarily bumped to
versionCode 3 for the §15 test build and **restored**; RC2's artifact SHA re-verified
unchanged. New files are documentation and evidence under `temp/phase13/` only.

---

## RESUME CHECKLIST (device-blocked work)

Every item below was attempted or scoped and is blocked **only** on physical device access.
Nothing here needs re-derivation — run it in this order when Device A is reconnected
(unlock the phone; accept the USB-debugging prompt if shown) and, ideally, when Device B is
attached.

### Immediately runnable on Device A once reconnected

| # | Item | Guide § | Command / action | Expected |
| --- | --- | --- | --- | --- |
| 1 | **Update-continuity install** | §15 | `adb -s RZCW40LVBJD install -r temp/phase13/artifact/thraksha-updatetest-vc3.apk` | `Success` (not `INSTALL_FAILED_UPDATE_INCOMPATIBLE`); then `dumpsys package com.thraksha.guardian \| grep versionCode` → **3**; then confirm audit chain + scan history survived |
| 2 | **Reinstate RC2 afterwards** | §2 | `adb install -r temp/phase12_1/artifact/thraksha-guardian-1.0-privatealpha-rc2.apk` will be **refused** (lower versionCode) — uninstall then reinstall RC2, or keep vc3 and note it | RC2 is the frozen artifact; do not leave vc3 as the pilot build |
| 3 | **Intent suite (25–40 prompts)** | §9 | `bash temp/phase13/evidence/run_intent_suite_v2.sh` (foreground-guarded; prompts in `prompts_v2.tsv`) | fills `intent_results.tsv`; discard the current file, its rows are stale reads |
| 4 | **Network Guard** | §12 | activate in-app, confirm scoped routing to VillainCaller only, observe a real event, verify block, disable and confirm connectivity restored | never widen VPN scope for testing |
| 5 | **Focus + Driving routines** | §10 | grant DND + WRITE_SETTINGS, run each: preview → START → verify → STOP & RESTORE | Focus is the only routine touching **screen timeout**; Driving is open-ended, so its expiry path differs from Meeting's |
| 6 | **Remaining 10 false-positive items** | §7 | open each REVIEW finding, record rule / DECLARED / GRANTED / OBSERVED / context / severity | no allowlists, no rule edits |
| 7 | **AI median + p95** | §8 | ≥20 inference samples via the suite | current sample count is 4 |
| 8 | **Process death mid-routine** | §11 | start Meeting, `adb shell am force-stop`, relaunch | must recover or restore, never strand settings |
| 9 | **Permission revoke / regrant** | §11 | revoke DND + WRITE_SETTINGS mid-routine | must degrade honestly, not half-apply |

### Requires the phone unplugged and in normal use

| # | Item | Guide § | Note |
| --- | --- | --- | --- |
| 10 | **Battery trend** | §13 | Meaningless on USB power. Needs unplugged wall-clock use. |
| 11 | **3–7 day pilot** | §14 | Start timestamp goes in `PHASE13_PILOT_LOG.md`. Do not backfill. |

### Requires Device B (S24 Ultra)

| # | Item | Guide § |
| --- | --- | --- |
| 12 | Full device record, install, model provisioning | §3, §4 |
| 13 | Visibility + scan comparison columns | §5, §6 |
| 14 | LiteRT-LM backend testing on newer silicon | §8 |
| 15 | Automation on Android 14+ / One UI 6+ — **the safety-critical unknown** | §10 |
| 16 | Install/uninstall safety on a secondary device | §16 |

### State to be aware of when resuming

* Device A's automation **special accesses are revoked** (DND + `WRITE_SETTINGS`), so
  routines will correctly show **NEEDS YOU / CANNOT RUN YET** until granted. That is the
  intended least-privilege default, not a fault.
* `temp/phase13/evidence/intent_results.tsv` currently holds **stale/garbage rows** from the
  two aborted harness runs. Delete it before re-running; it is not cited as evidence
  anywhere.
* `temp/phase13/artifact/thraksha-updatetest-vc3.apk` is a **§15 test artifact only** — not
  RC3, not a replacement for RC2.
