# PHASE 13 — NORMAL-USE PILOT LOG

Guide reference: §14. Suggested minimum: **3–7 days** on the owner's real phone.

## Status: ⛔ NOT STARTED — elapsed calendar time cannot be manufactured

Phase 13 §14 asks for several days of ordinary daily use. That is wall-clock human time on
the owner's own phone: real meetings, real drives, real app installs, real overnight idle,
real battery cycles. **No amount of automation compresses it**, and the Phase 13 directive is
explicit — *"Do not fabricate soak duration. If human time is required, record the checkpoint
and stop rather than claiming elapsed usage that did not occur."*

Accordingly this log contains **zero fabricated pilot days**.

| Field | Value |
| --- | --- |
| Pilot start | **not started** |
| Elapsed pilot duration | **0 days** |
| Required minimum | 3–7 days |
| Sessions logged | 0 |

## Checkpoint — what the pilot is waiting on

1. **Device A must stay connected/usable.** The S20 FE dropped off USB twice this session
   (once after the Phase 12.1 reboot, once mid-Phase-13). Not a product fault, but it needs a
   manual unlock / USB-debugging re-authorise before further instrumented work.
2. **The owner must actually carry the phone** with RC2 installed and use it normally.

## What is already established, so the pilot starts from a known-good state

RC2 has been validated well beyond a smoke test on Device A (Phase 12.1 + Phase 13):

* real release scan of 441 packages, non-destructive, deterministic across reboot;
* Meeting Mode preview → START → verified changes → STOP & RESTORE → exact restoration;
* offline AI with radios disabled, no auto-execution;
* reboot safe, encrypted audit persists, no crashes or ANRs.

So the pilot is a **duration and daily-variety** gap, not a functionality gap.

## Issue log

Issues found so far — recorded here because they are real, even though the pilot proper has
not begun. Format per guide §14.

### ISSUE-13-001 — S20 FE drops off USB under sustained adb load

| Field | Value |
| --- | --- |
| Device | A — S20 FE (`RZCW40LVBJD`) |
| Timestamp | 2026-08-16 ~05:50 and ~06:45 BST |
| Symptom | `adb devices` stops listing the phone; `uiautomator dump` returns empty; commands fail `device not found` |
| Severity | **Medium — test-environment only** |
| Reproduction | Sustained scripted adb interaction (repeated `uiautomator dump` + `input tap` over many minutes); also observed after a device reboot |
| Recovery | `adb kill-server` + reconnect; may need manual screen unlock / re-authorise USB debugging |
| Product or environment? | **Environment.** No Thraksha component is involved; the app is not running any adb-facing code. It nonetheless **blocked** parts of Phase 13. |

### ISSUE-13-002 — UI automation harness drove the phone out of the app

| Field | Value |
| --- | --- |
| Device | A — S20 FE |
| Timestamp | 2026-08-16 ~05:50 BST |
| Symptom | A scripted prompt suite using fixed screen coordinates lost sync with the UI and navigated into Samsung Account's "Create your Samsung account" consent screen |
| Severity | **Low — tooling defect, no device change** |
| Reproduction | Blind coordinate taps with no foreground guard, once the app state diverged from what the script assumed |
| Recovery | Backed out with BACK/HOME. **Verified afterwards: no Samsung account was created** (`dumpsys account` lists authenticator services only, zero `Account {}` entries), system settings still at baseline (`zen=0 bmode=1 timeout=600000 airplane=0`), package count still 441 |
| Product or environment? | **Test tooling.** My harness, not RC2. Rewritten with a foreground guard, but the run did not complete before the device disconnected. |

### Observation — input robustness (positive)

Not an issue, but worth logging: a corrupted prompt string
(`"tI want to focus for 90 minutes "`, mangled by the faulty harness) was still interpreted
correctly as **Focus Mode — 90 minutes**. Malformed input degraded gracefully rather than
producing a wrong or unsafe plan.

## To start the pilot

1. Reconnect / unlock Device A.
2. Confirm RC2 (`versionCode 2`) is installed and Advice Mode is on.
3. Log a start timestamp here and use the phone normally.
4. Record each scan, AI request, routine, reboot and permission change with date and
   outcome, plus any issue in the format above.
