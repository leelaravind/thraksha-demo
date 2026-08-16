# PHASE 9 — AUTOMATION CAPABILITY MATRIX

**Date:** 2026-08-14 (finalized after on-device proof — see §4)
**Devices:** Samsung SM-G781B (S20 FE), Android 13 / API 33, Thraksha privilege DEVICE_ADMIN;
AOSP `thraksha_do` AVD, Android 13 / API 33, DEVICE_OWNER.
**Method:** Android API contract review + runtime proof. Every capability marked
SUPPORTED below is exercised by `Phase9AutomationInstrumentedTest` **as Thraksha** (read →
execute → verify → restore → verify-restored against real Android state) on both
devices. adb was used only to dev-grant the two user-grantable special accesses and to
read ground truth — never as a runtime automation mechanism.

Recorded phone baseline before any Phase 9 testing (to be restored after):
`screen_brightness=86`, `screen_brightness_mode=1 (AUTO)`, `screen_off_timeout=600000`,
`zen_mode=0 (DND off)`, `mode_ringer=2 (NORMAL)`.

---

## 1. The matrix

Legend: **Direct** = Thraksha calls the API itself once prerequisites are held.
**UA** = user-assisted (USER ACTION REQUIRED + deep-link). R/V/Rev = current-state
readable / resulting-state verifiable / reversible from a captured snapshot.

| Capability | API | Prerequisite | Normal | Dev-Admin | Dev-Owner | Direct/UA | R | V | Rev | Demo-suitable |
|---|---|---|---|---|---|---|---|---|---|---|
| **Do Not Disturb (interruption filter)** | `NotificationManager.setInterruptionFilter` / `getCurrentInterruptionFilter` | Notification Policy Access (`ACCESS_NOTIFICATION_POLICY` special access; user grants via `ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS`) | ✅ once access granted | same | ✅ already granted on the current DO AVD — **origin not established** (Device Owner-derived vs. persisted development grant; see §2) | Direct after one-time UA grant (no in-app grant was needed on the current DO AVD) | ✅ | ✅ re-read filter (asynchronous propagation — see §3) | ✅ snapshot filter | **YES — hero action** |
| **Ringer mode** | `AudioManager.setRingerMode` / `getRingerMode` | none for NORMAL↔VIBRATE; transitions involving SILENT require the same Notification Policy Access (Android routes silent through DND) | ✅ | ✅ | ✅ | Direct | ✅ | ✅ re-read | ✅ snapshot mode | **YES** |
| **Screen brightness (value + auto/manual mode)** | `Settings.System.SCREEN_BRIGHTNESS` + `SCREEN_BRIGHTNESS_MODE` via `Settings.System.putInt` | `WRITE_SETTINGS` special access (`ACTION_MANAGE_WRITE_SETTINGS`) | ✅ once granted | same | same | Direct after one-time UA grant | ✅ | ✅ re-read both keys | ✅ snapshot **both** value and mode (the phone runs AUTO — restoring only the value would corrupt the user's mode) | **YES** |
| **Screen timeout** | `Settings.System.SCREEN_OFF_TIMEOUT` | `WRITE_SETTINGS` | ✅ | ✅ | ✅ | Direct | ✅ | ✅ | ✅ | **YES** |
| **Launch an application** | `PackageManager.getLaunchIntentForPackage` + `startActivity` | target visible + launchable | ✅ | ✅ | ✅ | Direct | n/a | 🟡 partial — resolve + start verified; **foreground arrival is not claimed** (would need usage-stats access). Status vocabulary uses OPENED, never ACTED-with-verified-state | n/a (nothing to restore — launching is not a reversible mutation) | YES (optional action, honest limits) |
| **Open Android settings screens (incl. app-specific)** | `ACTION_SETTINGS` family / `ACTION_APPLICATION_DETAILS_SETTINGS` | none | ✅ | ✅ | ✅ | UA by definition (hand-off) | n/a | resolve-only | n/a | YES (user-assisted plan items) |
| **Media/ring/alarm volume** | `AudioManager.setStreamVolume` | ring-stream changes that mute interact with DND access; media/alarm free | ✅ | ✅ | ✅ | Direct | ✅ | ✅ | ✅ | Deferred — ringer *mode* + DND already cover the demo story; volume adds surface without new proof value (§43). Documented, not shipped |
| **Screen ON/keep-awake** | `FLAG_KEEP_SCREEN_ON` (own activity only) / `PowerManager` wakelocks | — | own UI only | — | — | — | — | — | — | NO — cannot legitimately control global screen state; not shipped |
| **Orientation** | per-activity only; global rotation lock = `Settings.System.ACCELEROMETER_ROTATION` (WRITE_SETTINGS) | WRITE_SETTINGS | ✅ | ✅ | ✅ | Direct | ✅ | ✅ | ✅ | NO for 9 — no routine needs it; documented as available |
| **Notification-content controls** | listener APIs | listener access | — | — | — | — | — | — | — | NO — interception is a security surface, not automation (§5 prohibits repurposing) |
| **App suspension (restrict distracting apps)** | `DevicePolicyManager.setPackagesSuspended` | Device Owner **and** the Phase 4–8 enforcement allowlist | ❌ | ❌ | ✅ but allowlist-limited to the controlled demo sample | — | ✅ | ✅ | ✅ | **NO for automation** — kept in the security domain (guide §26); Focus Mode surfaces app restriction as USER ACTION REQUIRED instead. No arbitrary real-app suspension from a routine, ever |
| **Device Owner user restrictions (e.g. disallow config changes)** | `DPM.addUserRestriction` | Device Owner | ❌ | ❌ | ✅ | — | ✅ | ✅ | ✅ | NO — policy control, not useful automation (§39: don't add privileged actions because DO makes them possible) |
| **Network Guard enable/disable from a routine** | existing Phase 7 service | VPN consent | ✅ | ✅ | ✅ | Direct | ✅ | ✅ | ✅ | NO — §34: routines must not toggle security monitoring. Explicitly excluded |
| **Driving detection (activity recognition)** | `ACTIVITY_RECOGNITION` | runtime permission | ✅ | — | — | — | — | — | — | NO for 9 — Driving Mode is user-started (§18); no detection is claimed |

## 2. Special-access onboarding (guide §24/§25)

Two one-time user-assisted grants gate the direct capabilities:

* **Notification Policy Access** — checked via
  `NotificationManager.isNotificationPolicyAccessGranted`; deep link
  `ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS`. Grant is per-app, revocable in the same
  screen (shown in the onboarding text). **On the current Device Owner AVD, Notification
  Policy Access is already granted. Whether this derives intrinsically from Device Owner
  status or from a persisted development grant was not independently established** —
  separating the two would require destructive reprovisioning (uninstalling the Device
  Owner app), which was not performed. **Runtime behavior does not depend on this
  distinction: the implementation checks `isNotificationPolicyAccessGranted` and handles
  `false` as USER ACTION REQUIRED** with the deep link.
* **Modify system settings (WRITE_SETTINGS)** — checked via `Settings.System.canWrite`;
  deep link `ACTION_MANAGE_WRITE_SETTINGS` with the package URI.

Until granted, DND/brightness/timeout plan items surface as **USER ACTION REQUIRED**
with the deep link; nothing is silently skipped. The manifest declares
`ACCESS_NOTIFICATION_POLICY` (normal) and `WRITE_SETTINGS` (special, satisfied only by
the user toggle). Dev/test runs grant both via adb (`appops set … WRITE_SETTINGS allow`,
`cmd notification allow_dnd …`) — development-only, documented, never a runtime path.

## 3. Runtime findings that shaped the implementation (discovered, not assumed)

* **Asynchronous filter propagation (Samsung):** `setInterruptionFilter` reflects in
  `getCurrentInterruptionFilter` a moment later. Verification uses a bounded settle-poll
  (≤2 s, 50 ms steps) — still a real-state check; a state that never materialises stays
  unverified. Without this, the very first S20 FE run honestly failed verification.
* **Zen→ringer coercion (AOSP):** while an interruption filter is active, AOSP presents
  the ringer as SILENT even after a verified `setRingerMode(VIBRATE)`; Samsung keeps
  VIBRATE. Consequence: the ringer action in Meeting/Focus is OPTIONAL (DND is the
  routine's real promise), ACTED refers to its execution-time verification, and
  restoration is deterministic because DND is restored before the ringer (reverse
  order), so the ringer restore verifies with zen off. Documented in the planner.
* **DND policy access is already granted on the current DO AVD** (see the matrix row and
  §2) — origin not independently established, and the implementation does not depend on
  it. Either way, Device Owner adds no *further* automation-relevant privilege, and per
  guide §39 no DO-only automation was added: policy control is not useful automation.
* **Vibrator-less hardware caveat:** on devices without a vibrator, VIBRATE may
  present as SILENT — a second reason the ringer action is optional.

## 4. Verification & reversibility rules derived from this audit

* DND, ringer, brightness (value+mode), timeout: **read → snapshot → write → re-read →
  compare** both on apply and on restore. ACTED / RESTORED only on confirmed state.
* App launch: resolve + `startActivity` without exception ⇒ **OPENED** (explicitly not
  a verified-state ACTED); no snapshot, no restore obligation.
* Settings navigation: hand-off ⇒ **USER ACTION REQUIRED** always.
* Brightness writes clamp to the device-valid 1..255 range; timeout bounded
  15 s..30 min; duration bounded 1 min..8 h (AutomationSafetyPolicy).
* Restore always uses the snapshot's actual previous values — the engine has no
  default-values table at all, so "restore to hardcoded defaults" is unrepresentable.

## 5. Runtime proof

Each SUPPORTED row is proven by `Phase9AutomationInstrumentedTest` (13 tests; S20 FE:
12 passed / 1 skipped, thraksha_do AVD: 12 passed / 1 skipped — the skip is the denial
test, which by design only runs when the required access is absent): the executor
round-trip tests read the real
current state, apply a change **as Thraksha**, verify via re-read, restore the snapshot
value, and verify restoration, leaving state exactly as found. The denial path — the
engine refusing to run, and executing nothing, when a **required** capability is
unavailable — is recorded in `temp/PHASE9_ROUTINE_VALIDATION.md` §6, whose FINAL evidence
is the `WRITE_SETTINGS` denial described in the re-verification note below. See that
document for the UI-driven end-to-end proofs with adb ground-truth cross-checks on both
devices.

**Re-verification note (2026-08-15, independent final verify).** On SM-G781B the
development command `cmd notification disallow_dnd com.thraksha.guardian` (with and
without an explicit user id) could no longer make Notification Policy Access read
`false`: `isNotificationPolicyAccessGranted` stayed true, proved by the denial test
continuing to skip while every other DND test ran. Thraksha's notification listener is
not in `enabled_notification_listeners`, so the hold comes from some other Samsung-side
route that was not identified. **No DND denial was reproduced, and none is claimed.**

The required-capability preflight denial architecture was instead independently
re-proved using **`WRITE_SETTINGS` denial** (`appops set com.thraksha.guardian
WRITE_SETTINGS ignore`), which exercises exactly the same guide §12 preflight path:

* the required capability became unavailable (brightness writes refused);
* preflight blocked the routine — Driving Mode's plan was reported non-executable with
  the exact reason;
* **USER ACTION REQUIRED** was produced for the affected actions, with the grant text;
* **START was not offered** (only Cancel), so execution did not proceed;
* **zero device mutations occurred** (no settings write, no persisted run);
* the grant was restored to `allow` and re-verified immediately afterwards.
