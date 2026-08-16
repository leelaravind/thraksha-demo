# PHASE 8.1 — RUNTIME OBSERVABILITY RESEARCH

**Date:** 2026-08-14
**Device:** Samsung SM-G781B (S20 FE), Android 13 (API 33), One UI 5.x
**Privilege levels considered:** NORMAL install · NORMAL + user-granted special access ·
DEVICE_ADMIN · DEVICE_OWNER (AOSP emulator)
**Question:** for each target capability, can Thraksha *prove* another app actually used it —
as opposed to declared it (Stage 1) or currently holds it (Stage 2)?

This document is the required pre-implementation research (guide §6/§7). Nothing below is
assumed; every "verified on device" line was probed on the attached phone (adb ground truth)
and/or is asserted by a Phase 8.1 instrumented test running *as Thraksha* (in-app proof).
Where Android does not expose usage, Stage 3 is **NOT OBSERVABLE** and the app must say so.

---

## 1. The API landscape (what Android offers at all)

| API | What it gives | Who can call it |
|---|---|---|
| `PackageManager.getPackageInfo(GET_PERMISSIONS)` — `requestedPermissions` | **DECLARED** permissions | any app with visibility of the target (Thraksha debug holds `QUERY_ALL_PACKAGES`) |
| same — `requestedPermissionsFlags & REQUESTED_PERMISSION_GRANTED` | **GRANTED** runtime-permission state | same |
| `AppOpsManager.unsafeCheckOpNoThrow(op, uid, pkg)` (API 29+) | current **mode** (allow/ignore/deny/default) of one app-op for another package — *state, not history* | normal apps may query mode; verified by `Phase81EvidenceInstrumentedTest.overlayOpQuery_isAnswerable_forAnotherPackage` |
| `AppOpsManager.getPackagesForOps` / `getHistoricalOps` | per-app op **usage history with timestamps** (what the OS Privacy Dashboard shows) | requires `android.permission.GET_APP_OPS_STATS` — `signature\|privileged\|development`. **NOT grantable to Thraksha.** Not obtainable via DEVICE_ADMIN or DEVICE_OWNER either — Device Owner gets policy APIs, not ops-stats APIs. |
| `PermissionManager` / PermissionUsage (Privacy Dashboard, API 31+) | recent permission usage per app | privileged (`GET_APP_OPS_STATS` / role-gated). Not available. |
| `UsageStatsManager` (special access `PACKAGE_USAGE_STATS`, user-grantable via Settings) | per-app **foreground usage** history | Thraksha could request it from the user. Gives "app was in foreground at T" — NOT permission usage. |
| `AudioManager.getActiveRecordingConfigurations()` (API 24+) | "someone is recording audio right now" | any app — but configurations for **other apps are anonymized: no package attribution** |
| `CameraManager.registerAvailabilityCallback` | camera device became unavailable (in use) | any app — device-wide, **no package attribution** |
| `SensorPrivacyManager` (API 31+) | global mic/camera privacy-toggle state | state of the *toggle*, not who used the sensor |
| `NotificationManagerCompat.getEnabledListenerPackages(ctx)` | which packages have **notification-listener access enabled** | any app |
| `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES` | which accessibility services are **enabled** | any app (readable secure setting) |
| `DevicePolicyManager.getActiveAdmins()` | which device-admin receivers are **active** | any app |
| `NotificationListenerService.onNotificationPosted` (Thraksha's own, user-enabled) | **attributed, timestamped proof that an app posted a notification** | Thraksha, only while the user has enabled its listener |
| Phase 7 Network Guard (per-app-scoped `VpnService`) | **attributed, timestamped proof of an outbound packet** (dest IP/port/protocol/size) from the scoped app | Thraksha, only for the package(s) in `VpnScope` (VillainCaller) with VPN consent |
| `ConnectivityManager.getConnectionOwnerUid` (API 29+) | owner of a connection 5-tuple | **restricted to VPN owners for connections through their VPN** — same scope as the Network Guard |

**Key negative result (unchanged from the Phase 0 audit, now formally required):** the
Privacy-Dashboard-grade "app X used location at 10:42" data exists in the OS but is
exposed only to callers holding `GET_APP_OPS_STATS`, which is `signature|privileged|development`
— a sideloaded demo app cannot hold it, and neither DEVICE_ADMIN nor DEVICE_OWNER changes
that. Any UI that showed per-app contacts/location/camera/mic/media *usage* would be
fabricated. Phase 8.1 therefore shows **NO VERIFIED OBSERVATION** for those capabilities.

---

## 2. Per-capability verdicts

Stages: **S1 = DECLARED**, **S2 = GRANTED/ENABLED**, **S3 = OBSERVED (genuine usage proof)**.

| Capability | S1 declared | S2 granted/enabled | S3 actual usage | S3 verdict |
|---|---|---|---|---|
| **Contacts** | ✅ manifest (`READ_CONTACTS` …) | ✅ grant flags | ❌ needs `GET_APP_OPS_STATS` | **NOT OBSERVABLE** |
| **Location** | ✅ manifest | ✅ grant flags | ❌ same | **NOT OBSERVABLE** |
| **Camera** | ✅ manifest | ✅ grant flags | ❌ device-wide in-use signal exists but is **unattributed** → would be inference | **NOT OBSERVABLE** (per app) |
| **Microphone** | ✅ manifest | ✅ grant flags | ❌ `getActiveRecordingConfigurations` is anonymized for other apps | **NOT OBSERVABLE** (per app) |
| **Media/storage** | ✅ manifest | ✅ grant flags | ❌ no API at any demo privilege | **NOT OBSERVABLE** |
| **Accessibility** | ✅ service with `BIND_ACCESSIBILITY_SERVICE` | ✅ `ENABLED_ACCESSIBILITY_SERVICES` | ❌ no API reports another service's event consumption | **NOT OBSERVABLE** |
| **Overlay** | ✅ `SYSTEM_ALERT_WINDOW` in manifest | 🟡 `unsafeCheckOpNoThrow(OPSTR_SYSTEM_ALERT_WINDOW)`: MODE_ALLOWED→ENABLED, MODE_IGNORED/ERRORED→DISABLED, MODE_DEFAULT→fall back to grant flag, else **UNKNOWN** | ❌ no supported API attributes a drawn overlay window to a package | **NOT OBSERVABLE** |
| **Notification listener** | ✅ service with `BIND_NOTIFICATION_LISTENER_SERVICE` | ✅ `getEnabledListenerPackages` | ❌ what an enabled listener *read* is not exposed | **NOT OBSERVABLE** |
| **Device admin** | ✅ receiver with `BIND_DEVICE_ADMIN` | ✅ `getActiveAdmins` | ❌ policy exercise not exposed | **NOT OBSERVABLE** |
| **Network** | ✅ `INTERNET` | (normal-level; granted at install) | ✅ **Phase 7 TUN interception** — attributed, timestamped, verified by the packet actually read; scope limited to `VpnScope` (VillainCaller) | **OBSERVED (VERIFIED)** within VPN scope; **NOT OBSERVABLE** outside it |
| **Notification posting** (new, honest extra) | n/a | n/a | ✅ Thraksha's own `NotificationListenerService`, when user-enabled, receives attributed `onNotificationPosted` | **OBSERVED (VERIFIED)** while listener enabled |

### Reliability grading used by the implementation

* `VERIFIED` — Thraksha itself handled the evidence (TUN packet; notification posted to its
  enabled listener). Only this grade may render **OBSERVED ✓**.
* `RECENT_SYSTEM_SIGNAL` — reserved for system-recorded, attributed history (e.g.
  UsageStats foreground events *if* the user grants usage access). **Not shipped in 8.1**
  (candidate only; foreground usage is not one of the target capabilities).
* `INDIRECT` — unattributed device-wide signals (mic-in-use, camera-unavailable). Never
  rendered as observed; not shipped in 8.1.
* `UNAVAILABLE` — Android exposes nothing at Thraksha's privilege. Rendered as
  **No verified observation available** / **NOT OBSERVABLE**.

---

## 3. Ground-truth probes recorded on the attached phone (2026-08-14)

* `dumpsys package com.samsung.android.app.watchmanager` → **every runtime permission
  `granted=false`** (28 runtime permissions, all denied; watch not set up). Declared count
  ~125 permission strings. ⇒ Phase 8's Galaxy Wearable HIGH-RISK finding rested **entirely on
  Stage 1 (declared)** evidence; Stage 2 is empty. This is the central calibration fact.
* `appops get com.samsung.android.app.watchmanager SYSTEM_ALERT_WINDOW` → `default`
  (no explicit overlay allow) — so "Overlay DECLARED ✓ / ENABLED ✗(default+not granted)".
* `settings get secure enabled_notification_listeners` → gearhead, launcher, smartmirroring —
  ground truth the in-app `getEnabledListenerPackages` result can be validated against.
* `settings get secure enabled_accessibility_services` → empty.
* `dpm list-owners` → none; Thraksha is currently **NORMAL** privilege on the phone (its
  device-admin was deactivated at some point after Phase 8). Advice Mode paths are exercised
  from NORMAL; DEVICE_OWNER paths run on the AOSP emulator as in Phases 4–8.
* Shell `appops get <pkg>` shows `time=…` history **because adb shell holds
  `GET_APP_OPS_STATS`** — that is the privileged view Thraksha honestly does not have, and
  it doubles as an external ground truth for the validation doc.

---

## 4. What Phase 8.1 implements as a result

1. **Stage 2 collection** (`RuntimeObservationProvider` + inventory extensions):
   runtime-permission grant flags (already collected), overlay op-mode query with honest
   `UNKNOWN`, notification-listener enablement, accessibility enablement, active device
   admins. Special access is modeled separately from runtime permissions (guide §5).
2. **Stage 3 sources — exactly two, both genuine:**
   * Network: Phase 7 `NetworkAttemptObserved` events retained in a bounded in-memory
     observation store, surfaced per package (VillainCaller scope only).
   * Notification posting: Thraksha's `ThrakshaNotificationListener` records
     package + timestamp (metadata only, no content) into the same observation store,
     only while the user has enabled the listener.
3. **Everything else:** Stage 3 renders "No verified observation available", with the
   limitation string carried in the evidence model (`limitations` field), never invented.
4. **No privileged fabrication:** no `GET_APP_OPS_STATS`, no hidden APIs, no shell.

## 5. STOP conditions honoured (guide §41)

* Contacts/Location/Camera/Microphone/Media/Accessibility/Overlay/Notification-listener
  **usage**: STOPPED — Android does not expose reliable per-app usage at any privilege
  Thraksha can legitimately hold on this device. Documented above; UI shows NOT OBSERVABLE.
* No inference from Stage 1+2 to Stage 3 anywhere in the evidence pipeline (unit-tested).
