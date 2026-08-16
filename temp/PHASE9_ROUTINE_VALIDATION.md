# PHASE 9 — ROUTINE VALIDATION (on-device, UI-driven, ground-truth-checked)

**Date:** 2026-08-15
**Devices:** Samsung SM-G781B (S20 FE, Android 13, Advice-Mode privilege) and
`thraksha_do` AVD (Android 13, Device Owner).
**Method:** every claim below was cross-checked against **adb-read OS state**
(`settings get system/global …`), not the app's UI. adb was used only to read ground
truth and to dev-manage the special-access grants — never as a runtime automation path.

## 1. Meeting Mode — the hero end-to-end (guide §38/§42), S20 FE

| Step | OS ground truth (adb) |
|---|---|
| **Original state** | brightness 85/255, mode AUTO(1), timeout 600000 ms, zen 0 (DND off), ringer 2 (NORMAL) |
| **Preview** | 4 planned actions shown before anything ran: ringer→vibrate (supported), dim brightness (supported), enable DND priority (supported · required), open calendar (supported) — `phase9_meeting_preview.png` |
| **START** | — |
| **Active (verified by adb)** | **brightness 77, mode MANUAL(0), zen 1 (DND on), ringer 1 (VIBRATE)** — three genuine device changes + Samsung Calendar opened (`phase9_calendar_opened.png`) |
| **Active UI** | `✓ verified` on ringer/brightness/DND, `✓ opened` on calendar, "Ends in ~29 min (auto-restore scheduled)", STOP & RESTORE — `phase9_meeting_active.png` |
| **Ongoing notification** | "Meeting Mode active — Ends at …. Tap to view or stop." (`phase9_notification_active.png`) |
| **STOP & RESTORE** | — |
| **Restored (verified by adb)** | **zen 0, ringer 2, brightness mode AUTO(1), timeout 600000** — the exact snapshot states. UI: "ROUTINE RESTORED · DO_NOT_DISTURB → ALL (DND off) ✓ · SCREEN_BRIGHTNESS → 85/255, AUTO ✓ · RINGER_MODE → NORMAL ✓" — `phase9_meeting_restored.png` |

Note on the brightness *value* under AUTO: the snapshot's exact pair (85, AUTO) is
re-applied and verified; with adaptive mode restored, the sensor immediately resumes
driving the value (it read 83–96 across the session with no routine active). The user's
real previous state is the AUTO mode — which is restored bit-for-bit.

## 2. Focus Mode, S20 FE

Preview shows the first-class user-assisted item: `Restrict distracting apps (manual)
— user action required` (`phase9_focus_preview_userassist.png`) — no fake app
restriction is claimed at this privilege. Cycle ground truth: zen 0→1→0, ringer 2→1→2;
timeout target equals the device's current 600000 → applied/verified idempotently.

## 3. Driving Mode (user-started, safety-shaped), S20 FE

No DND action exists in the plan (calls stay audible); no driving detection is claimed.
Ground truth: brightness 83/AUTO → **217/MANUAL** → restored AUTO; ringer stayed NORMAL;
zen untouched. `phase9_driving_preview.png`, `phase9_driving_active.png`.

## 4. Custom routine (composability, §19), S20 FE

DND + brightness 128 + timeout 120 s, 15 min: ground truth 96/600000/zen0 →
**128/120000/zen1** → restored 600000/zen0/AUTO. `phase9_custom_preview.png`,
`phase9_custom_restored.png`.

## 5. Device Owner AVD (guide §39)

Same Meeting cycle: **102/zen0/ringer2 → 77(MANUAL)/zen1/ringer1 → restored exactly
102/zen0/ringer2** (`phase9_emu_*.png`). **On the current Device Owner AVD, Notification
Policy Access is already granted. Whether this derives intrinsically from Device Owner
status or from a persisted development grant was not independently established** —
separating the two would need destructive reprovisioning, which was not performed.
**Runtime behavior does not depend on this distinction: the implementation checks
`isNotificationPolicyAccessGranted` and handles `false` as USER ACTION REQUIRED.**
No DO-only automation actions were added (§39: policy control ≠ useful automation).

## 6. Failure, rollback, recovery, denial (instrumented, both devices green)

* **Required failure → rollback (§14):** with the DND apply forced to fail
  (test hook), Meeting rolled back the genuinely applied ringer + brightness to their
  adb-verified previous values, reported ROLLED_BACK per capability, cleared the
  persisted duty.
* **Process death (§22):** engine state wiped in-memory → `recover()` reconstructed
  ACTIVE from the encrypted persisted run → STOP restored verified.
* **Expiry while dead (§20/§22):** a genuinely applied DND change with an
  already-expired persisted run was restored at recovery; duty cleared.
* **Denial — required-capability preflight block (§38).** *Historical (2026-08-14):* an
  earlier run recorded this against revoked DND access on the S20 FE (Meeting blocked at
  preflight — FAILED with reason, zero executions, no persisted duty). **FINAL evidence
  (2026-08-15, independent re-verification):** on this Samsung the development command
  `cmd notification disallow_dnd com.thraksha.guardian` could no longer make Notification
  Policy Access read `false` (it stayed granted, proved by the denial test continuing to
  skip while every other DND test ran), so **a DND denial was not reproduced and is not
  claimed**. The denial architecture was instead re-proved with **`WRITE_SETTINGS`
  denial** (`appops set com.thraksha.guardian WRITE_SETTINGS ignore`), the same guide §12
  preflight path:
  * the required capability became unavailable (brightness writes refused);
  * preflight blocked the routine — Driving Mode reported non-executable with the exact
    reason: `Required action(s) not currently executable: Raise brightness for daylight
    visibility (Allow Thraksha to modify system settings …)`;
  * **USER ACTION REQUIRED** was produced for the affected actions and stayed visible;
  * **START was not offered** (Cancel only) — execution did not proceed;
  * **zero device mutations occurred** — no settings write, no persisted run;
  * the access was restored to `allow` and re-verified immediately afterwards.

  On the DO AVD the DND-denial variant is likewise not exercisable, because Notification
  Policy Access reads granted there (origin not established — see §5).
* **One active routine (§23):** second start refused with "Another routine is active";
  first routine untouched.
* **Audit (§32):** one Meeting cycle produced AUTOMATION rows REQUESTED → PLANNED →
  STARTED → ACTION_VERIFIED×3 → ACTION_OPENED → ACTIVE → RESTORE_REQUESTED →
  STATE_RESTORED×3 → RESTORED in the hash-chained log, and `verifyChain()` passed on
  device afterwards. Feed screenshot: `phase9_audit_automation.png`.

## 7. Honesty incidents worth recording

The first S20 FE run **failed honestly**: Samsung applies `setInterruptionFilter`
asynchronously and immediate re-read verification refused to claim ACTED. The fix is a
bounded settle-poll (real state only). That failed run also left DND enabled on the
phone (the aborted test never reached restore); it was detected against the recorded
baseline and reverted, and the final state check confirms the phone's original
settings: mode AUTO, timeout 600000, zen 0, ringer NORMAL.
