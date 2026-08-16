# DEVICE OWNER DEMO SETUP — Thraksha Guardian

Safe provisioning and removal of Device Owner for the demo, verified on the dedicated
test phone (Samsung SM-G781B / S20 FE, Android 13, serial `RZCW40LVBJD`).

**Nothing in this document — and nothing in the app — wipes, factory-resets or reboots
the device. All steps are reversible.**

---

## 1. Eligibility preconditions

`adb shell dpm set-device-owner` refuses to run unless the device is unprovisioned.
Check all of these first:

| Check | Command | Required result |
|---|---|---|
| No accounts on the device | `adb shell dumpsys account \| grep -c "Account {"` | `0` |
| Single user | `adb shell pm list users` | only `UserInfo{0:Owner:...}` |
| No existing Device Owner | `adb shell dumpsys device_policy \| grep -i "device owner"` | no owner block |
| Guardian installed | `adb shell pm list packages com.thraksha.guardian` | present |

If a Google/Samsung account exists, remove it in **Settings → Accounts** (this does not
wipe the device). Only if the OS refuses account removal is a factory reset the fallback,
and that is a **manual human decision** — never automated.

State observed on `RZCW40LVBJD` before provisioning (2026-08-13): 0 accounts, single
user 0, no device owner, Guardian admin active with `testOnlyAdmin=true`.

## 1a. OBSERVED BLOCKER on this Samsung unit — Knox integrity check

`dpm set-device-owner` **fails on this SM-G781B** even with every documented
precondition satisfied (0 accounts, single user, no owner, online):

```
java.lang.RuntimeException: Can't set package … as device owner.
logcat: E DevicePolicyManager: Failed in device integrity check
logcat: AttestedCertParser: [Integrity Status] TrustBoot: Abnormal, Warranty: Abnormal
```

Samsung's DevicePolicyManagerService runs a **Knox attestation** before permitting
Device Owner provisioning. This unit's **Knox warranty bit is tripped** (`abnormal
WB : 0x1`, `abnormal TB : 0x1` from `keymaster_swd`) — the device ran unofficial
firmware at some point in its life. A tripped Knox fuse is **permanent hardware
state**: a factory reset does NOT clear it, so no amount of resetting makes this
particular unit Device-Owner-capable. First attempted 16:13 offline (in case the
attestation only needed connectivity), retried 16:14 online — identical failure with
the attestation reaching completion, isolating the warranty bit as the cause.

**Side effect to know about:** each failed `set-device-owner` attempt *removes* the
active Device Admin. Restore it with:

```
adb shell dpm set-active-admin com.thraksha.guardian/.security.ThrakshaDeviceAdminReceiver
```

**Consequence for the demo:** FULL POWER cannot be shown on this phone. Options:
(a) a non-Samsung test phone, or a Samsung with an intact Knox warranty bit; or
(b) an Android emulator (AVD) — real Android, real DevicePolicyManager, provisions
cleanly. This run verifies Device Owner enforcement on option (b), clearly labelled
as emulator verification everywhere it is reported.

## 1b. Emulator (AVD) provisioning — verified in this run

```
sdkmanager "system-images;android-33;default;x86_64"
avdmanager create avd -n thraksha_do -k "system-images;android-33;default;x86_64"
emulator -avd thraksha_do   (fresh AVD: zero accounts, single user)
adb -s emulator-5554 install -r -t app-debug.apk        (plus both decoy APKs)
adb -s emulator-5554 shell dpm set-device-owner com.thraksha.guardian/.security.ThrakshaDeviceAdminReceiver
```

Removal is identical (`dpm remove-active-admin`, test-only build) or simply wipe/delete
the AVD — it is disposable by design.

## 2. Why removal is safe on this device

The debug build installs with `testOnly=true`, so Android marks the admin
`testOnlyAdmin=true`. For a test-only admin, **`adb shell dpm remove-active-admin`
works even while it is Device Owner** — so Device Owner status can be dropped at any
time without a factory reset. This is the property that makes the demo repeatable.

A release (non-testOnly) build would NOT be removable this way; it would need
`clearDeviceOwnerApp()` from inside the app or a factory reset. Keep the demo on the
debug build.

## 3. Provisioning

```
adb shell dpm set-device-owner com.thraksha.guardian/.security.ThrakshaDeviceAdminReceiver
```

Expected output:

```
Success: Device owner set to package ComponentInfo{com.thraksha.guardian/com.thraksha.guardian.security.ThrakshaDeviceAdminReceiver}
Active admin set to component {com.thraksha.guardian/com.thraksha.guardian.security.ThrakshaDeviceAdminReceiver}
```

## 4. Verification (both sides)

| Check | Command / place | Expected |
|---|---|---|
| ADB view | `adb shell dumpsys device_policy \| grep -A3 "Device Owner"` | Guardian named as owner of user 0 |
| API view | Guardian dashboard (re-open the app) | Privilege `DEVICE_OWNER`, badge **FULL POWER** |
| App query | `DevicePolicyManager.isDeviceOwnerApp("com.thraksha.guardian")` | `true` (this is what `SecurityCapability` reads) |

## 5. Removal / return to Advice Mode

```
adb shell dpm remove-active-admin com.thraksha.guardian/.security.ThrakshaDeviceAdminReceiver
```

This drops Device Owner AND active-admin status (allowed because the admin is
test-only). Re-open Guardian: the badge returns to **ADVICE MODE**. To re-enrol plain
Device Admin afterwards: Settings → Security → Device admin apps → Thraksha.

Before removal, restore any demo containment so nothing stays suspended:
use the dashboard's **Restore demo state** control (or
`adb shell pm unsuspend com.thraksha.demo.villaincaller` as a fallback — note `pm
unsuspend` from shell may not clear a suspension created by a different suspending
package; the in-app restore is the reliable path).

## 6. Standing safety rules

* The app never calls `wipeData`, `resetPassword`, `lockNow`, `reboot`,
  `setApplicationHidden` or any user-restriction API — verified by repo grep at each
  phase checkpoint.
* Enforcement is allowlisted to `com.thraksha.demo.villaincaller` only; Guardian,
  GoodCaller, launcher, system packages and arbitrary packages are refused in code.
* Every enforcement action is verified against actual OS state before being reported,
  and every action has a restore path (`unsuspend`, permission grant-state reset to
  DEFAULT).
* Never run `dpm set-device-owner` on a personal device: on a non-test-only build the
  only way back is a factory reset.
