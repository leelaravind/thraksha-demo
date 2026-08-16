# PHASE 13 — AUTOMATION DEVICE MATRIX

Guide reference: §10. Build: frozen RC2, Advice Mode, no Device Owner.

## Device A — S20 FE (Android 13 / API 33, One UI 5)

### Meeting Mode — ✅ FULL PASS (verified on the RC2 release APK)

| Step | Result |
| --- | --- |
| Preview shown | ✅ 4 changes listed |
| Preview is side-effect free | ✅ `zen=0 brightness=101 bmode=1` identical before and after opening |
| Explicit START required | ✅ nothing runs without it |
| DND applied | ✅ `zen_mode 0 → 1` |
| Ringer applied | ✅ set to vibrate (VERIFIED) |
| Brightness applied | ✅ `101 → 77`, `brightness_mode 1 → 0` |
| App launch | ✅ Samsung Calendar opened, reported **OPENED** with the honest caveat *"launch fired; foreground arrival is not claimed"* |
| ACTIVE state | ✅ "Meeting Mode is running — ends in about 29 min" |
| Verification | ✅ 3 × VERIFIED + 1 × OPENED |
| STOP & RESTORE | ✅ |
| **Exact restoration** | ✅ app-verified: DND *"back to interruption filter: ALL"*, brightness *"back to 101/255, mode: AUTO"*, ringer *"back to NORMAL"* |

Least-privilege behaviour also confirmed: on a **fresh install with no special access**, the
plan correctly reported **CANNOT RUN YET** and refused to execute. Nothing degraded silently.

### Focus Mode — ⚠ PARTIAL

Plan generation verified: an AI request produced **Focus Mode — 90 minutes** with 4 changes
(ringer → vibrate, extend screen timeout to 10 min, DND priority-only, + 1), each correctly
marked **NEEDS YOU** because the special accesses had been revoked after Phase 12.1 testing.

⛔ **START → ACTIVE → STOP & RESTORE not executed.** Focus is the only routine that touches
**screen timeout**, so its restore path is the one Meeting does not cover.

### Driving Mode — ⛔ NOT TESTED

Not exercised at all. Driving is open-ended ("runs until you stop it") rather than
duration-bounded, so its expiry/restore path is structurally different from Meeting's and is
**not** covered by the Meeting result.

### OEM-specific behaviour observed (Device A / One UI 5)

| Behaviour | Detail |
| --- | --- |
| Battery optimisation | One UI pauses background services unless exempted; RC2's corrected dialog now states this accurately rather than promising 24/7 operation. |
| `ringer_mode` | `settings get system ringer_mode` returns `null` — Samsung keeps ringer state in AudioManager. Snapshot/restore correctly reads AudioManager, and the app reports "verified back to ringer mode: NORMAL". A settings-provider-only implementation would silently fail here. |
| Auto-brightness drift | With `screen_brightness_mode=1` the raw `screen_brightness` value drifts continuously. A raw re-read after restore showed 98 vs a pre-START 101 — **not** a restore failure; the app restored and verified 101/255 + AUTO, and the OS then re-adjusted. Any restore check on this OEM must compare the *mode* plus the app's verified snapshot, never a bare value re-read. |
| DND access | Requires the user-granted notification-policy special access; without it silent-mode transitions are correctly refused rather than half-applied. |

## Device B — S24 Ultra (Android 14+ / One UI 6+)

⛔ **NOT TESTED — device not connected.**

This is where Phase 13 §10 has its real content, and none of it exists. Specifically unknown:

* whether DND / ringer / brightness / screen-timeout writes behave the same under **One UI 6+**;
* Android 14+ tightening around `WRITE_SETTINGS` and notification-policy access;
* whether the AudioManager ringer quirk is identical or worse on newer One UI;
* foreground-service and exact-alarm restrictions on newer API levels, which govern routine expiry;
* whether app-launch actions are throttled by newer background-launch restrictions.

**A routine that restores correctly on API 33 is not evidence that it restores on API 34+.**
Restoration is the single most safety-critical behaviour in the product — it is what
guarantees the app leaves the user's phone as it found it — so this gap is material.

## Summary

| Routine | Device A (S20 FE) | Device B (S24 Ultra) |
| --- | --- | --- |
| Meeting: preview → START → verify → STOP & RESTORE | ✅ **full pass, exact restoration** | ⛔ |
| Focus | ⚠ plan only, not executed | ⛔ |
| Driving | ⛔ not tested | ⛔ |
| Refuses to run without required access | ✅ | ⛔ |
| Preview has no side effects | ✅ | ⛔ |
