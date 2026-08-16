# PHASE 12B — CLEAN-DEVICE / REAL-WORLD SOAK

**Status: PASS.** The blocker below was resolved when the handset was physically
reconnected; 12B was then completed in full.

Device: Samsung Galaxy S20 FE (`SM-G781B`, Android 13, serial `RZCW40LVBJD`)
Artifact under test: `thraksha-guardian-1.0-privatealpha-rc1.apk`,
SHA-256 `80e7aaea2619257159b05d2f13c57f81dad10e44b502b748bfb1788e9bb337c6`
— the exact 12A release candidate, unmodified.

---

## BLOCKER (RESOLVED) — the phone stopped responding to ADB after the reboot test

The reboot test (§25) was initiated with `adb reboot`. The device rebooted and then never
re-enumerated: it sat `offline` for ~3 minutes, then disappeared from `adb devices`
entirely. `adb reconnect`, `adb kill-server`/`start-server` and ~9 minutes of polling did
not recover it. The emulator on the same host remained connected throughout, so this is
specific to the phone's USB/ADB session, not the host.

**Most likely cause:** after a reboot this Samsung unit requires the lock screen to be
unlocked and, typically, the "Allow USB debugging?" prompt to be re-accepted before the
ADB transport is restored. Both need **physical access to the handset**, which this session
does not have.

### Recovery attempted (all unsuccessful)

| Attempt | Result |
| --- | --- |
| `adb wait-for-device` + 40 s settle | device reported `offline` |
| Polling `get-state` / `getprop sys.boot_completed`, 12 × 15 s (3 min) | `offline` throughout, `boot_completed` never readable |
| `adb reconnect offline` | device dropped off the list entirely |
| `adb kill-server` + `adb start-server` | daemon restarted; phone still absent |
| Polling `adb devices`, 10 × 20 s (3 min 20 s) | not listed |
| Final poll, 12 × 25 s (5 min), watching for `device` / `unauthorized` | **not listed** for the whole window |

Total ≈ 20 minutes of recovery attempts across four distinct strategies. The emulator
(`emulator-5554`) remained `device` on the same host for the entire period, which isolates
the fault to the phone's USB/ADB session rather than the host or the ADB installation.

If the handset were merely awaiting authorisation it would normally appear as
`unauthorized`; its complete absence from `adb devices` points to the USB transport not
being re-established at all — consistent with the device sitting at a locked screen after
boot, or the cable/port needing to be reseated.

**This was an environment/access limitation, not an application defect.** No crash, ANR or
misbehaviour of Thraksha was observed at any point.

**Resolution:** the handset was physically reattended and re-enumerated as `device`. All
remaining 12B work (§25–§30) was then executed and is recorded in the sections that follow
the "What WAS verified" list.

### Last verified device state, immediately before the reboot

This matters, because it is the state the phone is in right now:

| Property | Value |
| --- | --- |
| Zen / DND | `0` (off) — restored |
| Screen brightness | `13` — restored to the pre-routine value |
| Screen timeout | `600000` — unchanged throughout |
| Active routine | **none** (STOP & RESTORE completed and verified) |
| Suspended packages | none — VillainCaller `suspended=false` |
| Device admin | **deactivated** (see §2) |
| Thraksha | installed (release candidate), model provisioned |
| Network Guard | off (never enabled in 12B) |

The phone was therefore left **clean and restored** before the reboot, and Thraksha does not
register a `BOOT_COMPLETED` receiver, so nothing of ours runs until the app is launched by
hand.

### To resume 12B

1. Unlock the handset; accept the USB-debugging prompt if shown; reseat the cable if needed.
2. Confirm `adb devices` lists `RZCW40LVBJD` as `device`.
3. Re-run from §25 (reboot verification) onward — everything before it is already evidenced
   below and need not be repeated.

---

## What WAS verified before the blocker

### 1. Baseline device state (§23.1)

Recorded in `BASELINE_DEVICE_STATE.txt`: zen 0, brightness 13 (adaptive mode ON),
timeout 600000, no device owner, VillainCaller not suspended, battery 82 %.

### 2. Uninstall — a genuine finding (§23.2, §27)

The first `adb uninstall` **failed**:

```
Failure [DELETE_FAILED_DEVICE_POLICY_MANAGER]
```

Guardian was still registered as an **active Device Admin**. `dpm remove-active-admin`
also refused:

```
SecurityException: Attempt to remove non-test admin
  ComponentInfo{com.thraksha.guardian/...ThrakshaDeviceAdminReceiver}
```

because that command only works on test-only admins, and this one is `testOnlyAdmin=false`.

**Consequence for a private alpha, and it is a real one:** if the owner ever activates
Device Admin, the app **cannot be uninstalled** — by ADB or by the normal Play/Settings
uninstall — until Device Admin is deactivated first, manually, at:

> Settings → Security and privacy → Other security settings → **Device admin apps** →
> Thraksha Guardian → **Deactivate**

That path was walked and it works (screenshots `12b_device_admin_settings.png`,
`12b_admin_list.png`, `12b_admin_deactivate.png`, `12b_admin_deactivate_confirm.png`).
It **must** appear in the emergency-recovery checklist.

A second observation from that dialog: Android lists the policies the app *declares*, and
`res/xml/device_admin.xml` declares ten — including **"Delete all data" (wipe-data)**,
**"Disable cameras"** and **"Set password rules"** — none of which the app uses. The
consent dialog therefore looks far more alarming than the app's actual behaviour warrants.
Logged as a 12C risk.

After deactivation, uninstall succeeded and removal was verified:

| Check | Result |
| --- | --- |
| Package present | **0** |
| `/sdcard/Android/data/com.thraksha.guardian` | **REMOVED** |
| Provisioned model (2.41 GiB) | **REMOVED** with the app-specific external dir |
| Device settings (zen / timeout / adaptive brightness) | unchanged |
| VillainCaller | untouched, `suspended=false` |

### 3. Fresh install of the exact release candidate (§23.4–23.5)

`adb install` of the 12A artifact → `Success`. Installed package reports
`versionName=1.0`, `flags=[ HAS_CODE ALLOW_CLEAR_USER_DATA ]` — **no `DEBUGGABLE` flag**,
confirming the release build is not debuggable as installed. First launch succeeded, zero
crashes.

### 4. Onboarding through the real UI (§23.6) — with a copy defect found

The Samsung battery-optimisation dialog appears on launch (`12b_first_launch.png`). It was
dismissed with **"Maybe Later"**, which is the correct least-privilege choice for a private
alpha; the app continued normally.

**Defect found (not fixed — see note):** the dialog's copy overclaims. It says Thraksha's
*"eyes and ears"* stay *"active 24/7"* and that the exemption *"ensures the AI stays awake
even when your phone is in your pocket."* Neither is true: the AI runs only on demand, and
as of the 12A fix it is **released under memory pressure**. This directly contradicts the
honesty standard the rest of the UI was rebuilt to meet in Phase 11B. The dialog is also
still styled in the pre-Phase-11B green/gold palette, so it was evidently missed during the
redesign.

*Not fixed during 12B by design:* §22 requires testing **the exact 12A artifact**. Changing
this string would produce a different APK and invalidate every result above. It is recorded
for the next build and carried into the 12C register.

### 5. Clean-state verification (§24)

On the fresh install, with no stale development data:

* Protect hero: **"Not scanned yet"**, ring state READY
* Threat Intelligence: **"NOT CHECKED — Run a scan to verify the threat pack."**
* Network Guard: **OFF**
* Settings → Protection: **"Advice mode"** — the correct everyday-phone default
* Permissions & access: Do Not Disturb **NOT GRANTED**, Modify system settings **NOT
  GRANTED**, Notifications **NOT GRANTED** — least privilege by default, and stated
  honestly (`12b_scan_result.png` capture of that screen)

No inherited grants, no stale scan, no leftover audit content.

### 6. Model provisioning and SHA verification through the app (§23.8–23.9)

The developer-provisioning step (the only sanctioned use of ADB for functionality):

```
adb push model/gemma-4-E2B-it.litertlm \
  /sdcard/Android/data/com.thraksha.guardian/files/models/gemma-4-E2B-it.litertlm
```

2,588,147,712 bytes at ~28.7 MB/s (86 s). Source SHA-256 checked before push:
`181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c` — **matches
`LocalModelRepository.EXPECTED_SHA256` exactly**.

*Practical note for the checklist:* under Git Bash the first push silently mis-targeted a
mangled Windows path and left the models directory empty. `MSYS_NO_PATHCONV=1` and an
explicit destination filename fixed it. Worth documenting, since a half-provisioned model
would surface as "MODEL INVALID".

Verified **through the app**, not by inspecting the file: after restart, Settings shows
**On-device AI — AVAILABLE** (`_nav_check.png`). Reaching that state requires the full gate
to pass: exists → readable → exact byte length → LiteRT-LM container magic → SHA-256.

### 7. Real scan on the clean install (§28)

| Metric | Value |
| --- | --- |
| Outcome | Scan complete |
| Packages analysed | **322** (319 system + Guardian + VillainCaller + GoodCaller) |
| Visibility | **REDUCED**, stated in the UI |
| Known-threat matches | **1** (VillainCaller) |
| Items needing attention | **1** |
| Duration | well under the 35 s sampling window |
| Crashes / ANRs | **0** |
| Memory after scan | 118,938 KB PSS |

An earlier release-build scan (before Device Admin was deactivated) reported **2** items —
the second being Thraksha Guardian itself, flagged for holding device admin. After
deactivation that finding **correctly disappeared**. The scanner is responding to real
evidence, not to a static list.

Scanner non-destructiveness confirmed: no app was modified, suspended or quarantined by
scanning.

### 8. Permission revoke/regrant handling (§29)

The two special accesses and the notification permission were granted (setup step,
equivalent to the user's manual grant in Android Settings). The app re-read them live and
Permissions & access flipped all three to **GRANTED** (`12b_permissions_granted.png`) — the
`ON_RESUME` re-read works on the release build.

### 9. Full automation cycle on the release candidate (§24, §17)

Meeting Mode, 30 minutes, all four actions **READY** after the grants:

| Stage | zen_mode | screen_brightness | screen_off_timeout |
| --- | --- | --- | --- |
| Before START | 0 | 13 | 600000 |
| During ACTIVE | **1** (DND priority-only) | **77** | 600000 |
| After STOP & RESTORE | **0** | **13** | 600000 |

**Exact restoration verified against real OS state**, on the release build, from a clean
install. Preview → explicit START → verified execution → STOP & RESTORE → verified
restoration, unchanged from the frozen baseline. Zero crashes.

---

## 10. §25 Reboot test — PASS

The device was rebooted mid-soak. After it was reattended and reconnected:

| Check | Result |
| --- | --- |
| Settings silently changed by Thraksha? | **No.** zen `0`, timeout `600000`, adaptive-brightness mode `1` — all identical to pre-reboot. (The raw `screen_brightness` value moves on its own because adaptive brightness is on.) |
| Did Thraksha auto-start? | **No** — 0 processes, 0 services. It declares no `BOOT_COMPLETED` receiver, confirmed empirically. |
| App still installed / model still present | yes / yes (2,588,147,712 bytes) |
| Device owner / Guardian admin | none / **not an admin** (only Samsung's `kgclient` is an enabled admin) |
| Suspended packages | none |
| Crashes since boot | **0** |
| Launch after reboot | clean, 106,296 KB PSS, 0 crashes |
| **Audit chain intact** | **yes** — 23:59 pre-reboot entries still present alongside new 01:12 entries |
| Encrypted store after reboot | opened successfully — SQLCipher + Keystore-wrapped passphrase survived |
| Model re-verified after reboot | **On-device AI: AVAILABLE** — the full SHA gate re-ran and passed |
| Scanner after reboot | ran cleanly, 0 crashes |
| Network Guard status | honest — OFF, and `VpnService` genuinely not running |

## 11. §26 Upgrade test — PASS

Installed the **same release candidate** over the existing install (`install -r`):

| Check | Result |
| --- | --- |
| Install | `Success` |
| `firstInstallTime` | **preserved** (2026-08-15 23:44:44) |
| `lastUpdateTime` | advanced (2026-08-16 01:16:58) — a genuine in-place upgrade |
| Encrypted DB | survived; no schema corruption |
| **Audit chain** | **survived both the reboot and the upgrade** — entries from 00:02 and 00:04 still present |
| Provisioned model | survived at the same path |
| Runtime permission (`POST_NOTIFICATIONS`) | survived as granted |
| Crashes | **0** |

## 12. §29 Normal-use soak — PASS

| Exercise | Result |
| --- | --- |
| Repeated background/foreground, 5 cycles | process survived every cycle; **0 crashes, 0 ANRs** |
| Memory across those cycles | 2,895,030 → 2,900,046 KB — stable, **+5 MB drift, no leak** |
| Model unload/reload under real pressure | 2,899,804 KB → **971,635 KB** on `RUNNING_CRITICAL`; log confirms `onTrimMemory(15): releasing the on-device model`; process alive |
| Repeated scans | clean-install scan, post-reboot scan, post-upgrade launch audit — all completed, 0 crashes |
| Repeated inference | three separate requests across the session, all produced valid plans |
| Permission revoke/regrant | NOT GRANTED → GRANTED re-read live by the app on resume |

### Offline AI — proven, not assumed

With **airplane mode enabled** (verified `airplane_mode_on = 1`, aeroplane icon visible in
the status bar), Ask Thraksha was given *"I have a meeting for 45 minutes"* and returned:

> **Thraksha understood: Meeting Mode — 45 minutes** — with the full four-action
> deterministic plan, all READY.

Inference ran entirely on-device with **no network of any kind**. Combined with the fact
that the APK contains no HTTP stack at all (12A egress audit), the local-AI boundary is
demonstrated rather than asserted. Network was restored afterwards (`airplane_mode_on = 0`).

## 13. Routines exercised on the release candidate

| Routine | Result |
| --- | --- |
| **Meeting** (30 min) | preview → explicit START → zen `0→1`, brightness `13→77` → STOP & RESTORE → **zen `→0`, brightness `→13` exactly**. Verified restoration. |
| **Driving** (until stopped) | preview → START → 3 actions all **VERIFIED** (audible ringer, raised brightness, extended timeout) → STOP & RESTORE → **"Your previous settings are back"**. |
| **Focus** | plan previewed (4 actions, one honestly marked *"Restrict distracting apps — NEEDS YOU"*); not started, since Meeting and Driving already exercised the full START/restore path. |

**Driving correctly does *not* enable Do Not Disturb** — `zen_mode` stayed `0` throughout —
matching the safety-shaping the unit suite asserts (`drivingPlan_isSafetyShaped`).

A note on brightness restoration: after Driving, `screen_brightness` read `105` rather than
the pre-routine `88`. This is **not** a restore failure — adaptive brightness
(`screen_brightness_mode=1`) is continuously re-driving that value, and the engine reported
**RESTORED**. Where the measurement window was short and lighting stable (the Meeting run),
the restore was numerically exact (`13 → 77 → 13`).

## 14. §30 Final device state — clean

Recorded in `FINAL_DEVICE_STATE.txt`:

| Property | Value | vs baseline |
| --- | --- | --- |
| zen_mode | `0` | same |
| brightness_mode | `1` (adaptive) | same |
| screen_off_timeout | `600000` | same |
| airplane_mode | `0` | restored |
| Active routine | **none** | — |
| Device owner | none | same |
| Guardian device admin | **not an admin** | *reduced* from baseline (deactivated during 12B; correct for an Advice-Mode alpha) |
| Suspended packages | none | same |
| `VpnService` running | **0** | Network Guard never enabled |
| Services running | 1 (`CompanionForegroundService`) | expected |
| App / model | installed / present | private alpha left installed |
| Crashes / ANRs | **0 / 0** | — |

Granted and left in place (user-revocable, documented): DND policy access, Modify system
settings, `POST_NOTIFICATIONS`. These are the accesses a private-alpha user grants
deliberately; they can be revoked at any time from Android Settings.

## 15. Coverage limitations, stated honestly

* **Extended multi-hour soak not performed.** §29 suggests "several hours plus an extended
  installed period". This session ran a dense ~1.5 h verification cycle covering install,
  scan, AI, two routines, reboot, upgrade and memory-pressure — not a long passive soak.
  Recommended as an owner-side step before relying on the build.
* **Network Guard was not enabled during 12B** (its behaviour is covered by the Phase 11B
  device verification and the `NetworkGuardInstrumentedTest` suite, 7 tests, passing).
* **Focus was previewed but not started.**
* **Screen rotation not exercised** (the app is portrait-oriented in practice).

## 16. §32 gate

| Criterion | Verdict |
| --- | --- |
| Clean install works | **PASS** |
| Normal UI onboarding works | **PASS** |
| Scanning works | **PASS** |
| No destructive side effect | **PASS** — nothing modified, suspended or quarantined by scanning |
| Automation restores correctly | **PASS** — Meeting numerically exact; Driving reported RESTORED |
| AI remains bounded/offline | **PASS** — proven in airplane mode |
| Reboot/recovery safe | **PASS** — no auto-start, no settings changed, audit + model intact |
| No serious crash/ANR | **PASS** — 0 crashes, 0 ANR files across the entire session |
| Uninstall/reinstall understood | **PASS** — including the Device Admin blocker and its manual recovery path |
| No dev tooling needed at runtime (except documented model provisioning) | **PASS** |
| Final device state clean | **PASS** |

# VERDICT: 12B — PASS

Proceeding to 12C.
