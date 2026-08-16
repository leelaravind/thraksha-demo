# THRAKSHA DEMO — PHASE 9 IMPLEMENTATION PROGRESS

## DETERMINISTIC AUTOMATION ENGINE + ROUTINES + VERIFIED RESTORATION

Implementation run against `temp/THRAKSHA_DEMO_IMPLEMENTATION_PHASE_9.md`
(authoritative), on the Phase 1–8.1 known-good baseline. **No AI/LLM anywhere.**

**Devices:** Samsung `SM-G781B` (S20 FE), Android 13, Advice-Mode privilege;
AOSP `thraksha_do` AVD, Android 13, DEVICE_OWNER.
**Dates:** 2026-08-14 → 2026-08-15 (session resumed once mid-implementation; state was
recovered from the working tree and continued, nothing re-written).

---

## BASELINE

Phase 8.1 exit state re-verified before Phase 9 changes: 169 unit / 61+61 instrumented
green (see temp/THRAKSHA_DEMO_PHASE_8_1_PROGRESS.md). Phone settings baseline recorded
first (brightness 86 AUTO, timeout 600 s, DND off, ringer NORMAL) — and the phone was
returned to exactly this state at the end (§ "leave the phone as found").

## CAPABILITY AUDIT FIRST (guide §3) — temp/PHASE9_AUTOMATION_CAPABILITY_MATRIX.md

Written before implementation; finalized from runtime proof. Shipped capabilities —
each proven on both devices (support, authority, readability, verifiability,
reversibility): **DND interruption filter**, **ringer mode**, **brightness value+mode**,
**screen timeout**, **app launch** (OPENED, never ACTED — foreground arrival is
unverifiable and not claimed), **settings hand-off** (always USER ACTION REQUIRED).
Documented as NOT shipped: volume streams (no added proof value), screen on/off +
orientation (no legitimate global control / no routine need), notification controls
(security surface), app suspension (stays in the security domain; Focus surfaces
restriction as USER ACTION REQUIRED), DO user restrictions (policy ≠ automation),
Network-Guard toggling from routines (§34 forbids), driving detection (Driving is
user-started).

Runtime discoveries (matrix §3): Samsung applies `setInterruptionFilter`
asynchronously (verification uses a bounded real-state settle-poll); AOSP coerces the
presented ringer to SILENT while zen is active (→ ringer is an optional courtesy
action, and restore order — DND before ringer — keeps restoration deterministic);
on the current Device Owner AVD **notification policy access is already granted, origin
not independently established** (Device Owner-derived vs. persisted development grant —
matrix §2; runtime behaviour does not depend on it, the code checks
`isNotificationPolicyAccessGranted` and handles `false` as USER ACTION REQUIRED).

## ARCHITECTURE (guide §49) — new `automation/` package, fully separate from security

`AutomationIntent` (the ONLY input surface; what Phase 10's model will produce) →
`AutomationSafetyPolicy` (pure, deterministic: capability allowlist, brightness
1–255 clamp, timeout 15 s–30 min, duration 1–480 min, package-name shape check +
forbidden targets, settings-action allowlist — no arbitrary intent URIs, §27/§28) →
`AutomationPlanner` (pure, deterministic templates; every action classified
SUPPORTED / USER_ACTION_REQUIRED / UNSUPPORTED via `CapabilityProbe`; required vs
optional explicit) → `AutomationEngine` (StateFlow lifecycle IDLE→PREFLIGHT→EXECUTING→
ACTIVE/PARTIAL/FAILED→RESTORING→RESTORED/RESTORE_FAILED) → per-capability executors
(`DndExecutor`, `RingerExecutor`, `BrightnessExecutor`, `ScreenTimeoutExecutor`,
`AppLaunchExecutor`; contract: support/snapshot/apply/verify/restore/verifyRestored —
each verify is a re-read of real Android state).

Snapshot-before-mutation (§9/§10): the ACTUAL previous states (brightness captures
value AND auto/manual mode) are read and **persisted to the encrypted config store
before the first mutation** — no schema migration, no defaults table anywhere in the
engine, so "restore to hardcoded defaults" is unrepresentable. Restore walks applied
capabilities in reverse, each verified individually.

Duration/expiry (§20): AlarmManager (`setExactAndAllowWhileIdle` when
`canScheduleExactAlarms`, else the inexact while-idle variant — documented fallback) →
`AutomationAlarmReceiver` → STOP & RESTORE. Process-death recovery (§22):
`AutomationEngine.recover()` runs at app start (after the audit collector is live) —
reconstructs ACTIVE, or restores immediately if the routine expired while dead.

One active routine (§23); rollback on required failure (§14) restores the genuinely
applied actions from the snapshot with per-capability results; user-assisted actions
are first-class plan items with settings deep-links (§24) and a two-row special-access
onboarding (§25: why/what/how-to-revoke + exact settings screen).

Audit (§32/§33): `SecurityEvent.AutomationEvent` → `AUTOMATION` rows in the ONE
hash-chained log (REQUESTED, PLANNED, STARTED, ACTION_VERIFIED/OPENED/FAILED, ACTIVE,
RESTORE_REQUESTED, STATE_RESTORED, RESTORED/RESTORE_FAILED, ROLLED_BACK, RECOVERED).
Notifications (§41): one ongoing "X active — tap to view or stop", one completion
"X ended — previous settings restored"; calm wording, no spam.

## ROUTINES (only audited capabilities; §16–§19)

* **Meeting Mode (hero, 30 min):** ringer→vibrate (optional), brightness→30%
  (optional), **DND priority (required)**, open calendar app (optional; omitted rather
  than faked when no candidate is installed).
* **Focus Mode (60 min):** ringer→vibrate, timeout→10 min, **DND priority (required)**,
  `Restrict distracting apps (manual)` as an honest USER ACTION REQUIRED hand-off.
* **Driving Mode (user-started, until stopped):** ringer NORMAL (required — calls stay
  audible), brightness 85% (required), timeout 10 min, open navigation app. **No DND**,
  no fake driving detection, nothing encouraging interaction.
* **Custom routine (§19):** intent-supplied action targets, safety-validated
  field-by-field; proves the engine is composable, not routine-hardcoded.

## FILES

**Created:** `automation/AutomationModels.kt`, `AutomationSafetyPolicy.kt`,
`AutomationPlanner.kt` (+`AutomationTargets`, `CapabilityProbe`),
`AndroidCapabilityProbe.kt`, `AutomationEngine.kt`, `AutomationScheduler.kt`
(+alarm receiver), `AutomationNotifier.kt`, `automation/executors/*` (contract + 5
executors), `test/Phase9AutomationTest.kt`,
`androidTest/Phase9AutomationInstrumentedTest.kt`,
`temp/PHASE9_AUTOMATION_CAPABILITY_MATRIX.md`, `temp/PHASE9_ROUTINE_VALIDATION.md`.

**Modified (all additive):** manifest (+`ACCESS_NOTIFICATION_POLICY`, `WRITE_SETTINGS`,
`SCHEDULE_EXACT_ALARM`, alarm receiver), `ConfigStore` (+raw get/put for encrypted run
persistence), `SecurityEvent` (+`AutomationEvent`), `ThrakshaApplication` (+AUTOMATION
audit mapping; +`recover()` at start), `DashboardScreen` (+`AutomationCard`:
onboarding, routine chooser, §29 preview dialog, §30 active panel, §31 restore panel;
+AUTOMATION/USER feed styling).

## TEST RESULTS (final, after `clean`)

| Check | Result |
|---|---|
| `clean :app:assembleDebug` | **BUILD SUCCESSFUL** |
| `:app:testDebugUnitTest` | **181 tests, 0 failures** (169 baseline + 12 Phase 9: safety-policy rejections, plan determinism/shape, user-assisted visibility, required-vs-optional, launch-candidate handling, invalid duration/target, rejected-intent plans) |
| `connectedDebugAndroidTest` — SM-G781B | **73 tests, 0 failures, 3 skipped** (Phase 8.1 baseline + 13 Phase 9; and the phone read back its original settings after the full suite) |
| `connectedDebugAndroidTest` — thraksha_do AVD | **73 tests, 0 failures, 7 skipped** (skips: baseline Advice-mode-only + Wearable-dependent probes + the DND-denial test, which by design only runs when the access is absent — it reads granted on this AVD) |

Phase 9 instrumented coverage (§36–§38): executor round-trips against real state (all
four reversible capabilities), Meeting end-to-end with independent pre/post state
capture, one-active-routine, required-failure rollback to real previous state (via the
documented test-only failure hook), process-death reconstruction, expiry-while-dead
restoration, the denial/blocked-plan test (FAILED at preflight, zero executions — it
runs only when a required access is absent, and its FINAL on-device evidence is the
`WRITE_SETTINGS` denial re-proof of 2026-08-15; see validation §6),
never-ACTED-without-real-state sweep, audit-stage completeness + on-device
`verifyChain()`.

## DEVICE RESULTS

See `temp/PHASE9_ROUTINE_VALIDATION.md` for the full ground-truth tables. Highlights:

* **Hero (S20 FE):** preview → START → adb-verified brightness 77/MANUAL + zen 1 +
  ringer VIBRATE + calendar opened → "✓ verified"×3 + "✓ opened" + ongoing
  notification → STOP & RESTORE → adb-verified zen 0, ringer NORMAL, brightness AUTO,
  timeout 600 s — the exact snapshot. Activation felt immediate (sub-second per
  action; the only intentional latency is the ≤2 s DND settle-poll on Samsung).
* **Focus / Driving / Custom (S20 FE):** all cycled with adb ground truth; Focus shows
  the honest USER ACTION REQUIRED restriction item; Driving changes brightness/timeout
  only, ringer stays audible, zen untouched.
* **Device Owner AVD:** Meeting cycle 102/zen0/NORMAL → 77/zen1/vibrate → restored
  exactly; DND policy access already granted there, origin not independently established
  (matrix §2); no DO-only automation added.
* **Phone left in original state** (adb-verified final check: mode AUTO, timeout
  600000, zen 0, ringer NORMAL; special accesses remain granted for the demo,
  revocable in Settings — documented).

## PERFORMANCE (guide §40)

Preflight+plan: <50 ms (pure). Snapshot: <100 ms (settings/manager reads). Actions:
each settings write verifies immediately; DND verify ≤2 s on Samsung (settle-poll),
~instant on AOSP. Meeting activation end-to-end ≈2–3 s incl. calendar launch; restore
≈1–2 s. Well inside "feels immediate".

## SAFETY VERIFICATION

* Greps over `automation/`: zero shell/exec/su, zero reflection/hidden APIs, zero
  accessibility references, zero coordinate tapping. Destructive-API grep over
  `app/src/main`: unchanged (only the enforcer KDoc line documenting absence).
* No tracked keys; no PRIVATE KEY markers in assets; rulepack + threatpack verify in
  the green suites.
* §34 respected: routines touch no security component (scanner, ThreatPack, audit,
  Network Guard, privilege are all untouched by the automation package — enforced by
  imports: `automation/` imports only `ConfigStore`, `SecurityEvent(Bus)` and its own
  types). Thraksha's own alerts under routine-DND: threat notifications are
  high-priority but DND-filtered like any app's; documented (no self-exemption
  claimed).
* Phase 10 boundary (§28): the model will produce `AutomationIntent` only; executors
  are reachable exclusively through engine→planner→safety-policy validation.

## LIMITATIONS / DIVERGENCES (documented per §48, not simulated around)

* App-launch verification is OPENED (resolve + start), never ACTED — foreground
  arrival is not verifiable at Thraksha's privilege.
* Ringer under active zen: AOSP presents SILENT after a verified VIBRATE set — ringer
  is therefore optional in Meeting/Focus; ACTED refers to execution-time verification;
  restore is deterministic (DND restored first). Matrix §3.
* Brightness under restored AUTO mode: the mode+value pair is restored and verified;
  the sensor then legitimately drives the value. The user's real state (AUTO) is what
  is restored.
* Exact alarms depend on the OS "Alarms & reminders" state; the inexact while-idle
  fallback plus start-time recovery is the documented §20 mechanism.
* Special accesses were dev-granted via adb for automated test runs (ADB =
  development/testing only); the product path is the in-app §25 onboarding with
  settings deep-links. The required-capability denial path was genuinely exercised — its
  FINAL evidence is the `WRITE_SETTINGS` denial re-proof (validation §6). On this Samsung
  the dev command can no longer make Notification Policy Access read `false`, so **no DND
  denial is claimed** as final evidence.
* The first S20 FE run failed honestly on Samsung's async DND propagation and briefly
  left DND enabled (aborted test never reached restore); detected against the recorded
  baseline, reverted, and fixed with the bounded settle-poll (validation doc §7).

## GIT WORKING TREE

Nothing committed (per instructions). New/modified files as listed above; keys remain
untracked; `temp/` remains untracked.

## ACCEPTANCE CRITERIA (§47) — ALL MET

Capabilities documented from actual behavior ✓ · multi-action routine end-to-end on
the S20 FE ✓ (Meeting = hero) · Focus ✓ · Driving safe/user-started ✓ · deterministic
plans ✓ · unsupported/user-assisted shown honestly ✓ · snapshot before mutation ✓ ·
verified ACTED only ✓ · STOP & RESTORE to actual previous values ✓ · restoration
survives lifecycle ✓ · partial failure + rollback honest ✓ · conflicting routines
prevented ✓ · deep-linked user-assisted actions ✓ · no ADB/root/private API at
runtime ✓ · nothing destructive ✓ · automation in the existing audit chain ✓ ·
security monitoring untouched ✓ · Phase 1–8.1 suites green ✓ · nothing committed ✓.
