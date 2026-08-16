# PHASE 13 — SIGNING CONTINUITY

Guide reference: §15.

## Purpose

Prove that the permanent Thraksha private-alpha signing identity created in Phase 12.1 can
actually deliver an **in-place update** to an installed RC2 — i.e. that the owner is not
locked into uninstall-and-lose-data for every future build.

## Test artifact

Guide §15 explicitly permits a minimal versionCode-incremented test APK. Built one; the
RC2 source identity was restored immediately afterwards.

| Field | RC2 (installed) | Update-test APK |
| --- | --- | --- |
| File | `thraksha-guardian-1.0-privatealpha-rc2.apk` | `thraksha-updatetest-vc3.apk` |
| applicationId | `com.thraksha.guardian` | `com.thraksha.guardian` |
| **versionCode** | **2** | **3** |
| versionName | `1.0-privatealpha-rc2` | `1.0-privatealpha-rc2-updatetest` |
| Signer DN | `CN=Thraksha Guardian, OU=Private Alpha, O=Thraksha, L=Tirumula, ST=Andhra Pradesh, C=IN` | **identical** |
| Signer cert SHA-256 | `0d96977d15ce5b17c4167a03f70fc72401f580a8d4f2258e059fa113516ac928` | **identical** |

**The signing key was not rotated.** Both APKs are signed by the same keystore
(`thraksha-private-alpha`), and `apksigner` reports byte-identical certificate digests.

### RC2 remained frozen

`app/build.gradle.kts` was reverted to `versionCode = 2` /
`versionName = "1.0-privatealpha-rc2"` immediately after the test build, and the RC2
artifact was re-hashed to prove it was never touched:

```
93c15f227bc724ef02eb5cb79cabcac308f2dd53aa531a19d0665da8ed05c7de   (unchanged)
```

The update-test APK is stored separately under `temp/phase13/artifact/` and is **not** RC3
and **not** a replacement for RC2.

## Result

| Step | Status |
| --- | --- |
| Same-key test APK builds | ✅ **PASS** |
| Signer certificate identical to RC2 | ✅ **PASS** — same DN, same SHA-256 |
| versionCode increments correctly (2 → 3) | ✅ **PASS** |
| Key not rotated | ✅ **PASS** |
| RC2 artifact unmodified | ✅ **PASS** |
| **Android accepts the in-place update on Device A** | ⛔ **NOT VERIFIED** |
| Update preserves encrypted DB and audit chain | ⛔ **NOT VERIFIED** |
| Update continuity on Device B | ⛔ **NOT VERIFIED — no device** |

### Why the install step is unverified

Device A (S20 FE) dropped off USB mid-session and did not re-enumerate. `adb devices`
lists only the emulator. This is an **environmental** fault, not a product fault — the same
device has disconnected after reboot earlier in this project and needs a manual unlock /
USB-debugging re-authorise.

The install command is prepared and takes seconds once the device returns:

```
adb -s RZCW40LVBJD install -r temp/phase13/artifact/thraksha-updatetest-vc3.apk
# expect: Success   (NOT INSTALL_FAILED_UPDATE_INCOMPATIBLE)
# then: dumpsys package com.thraksha.guardian | grep versionCode   -> 3
# then confirm the audit chain and scan history survived the update
```

### What is and is not established

**Established:** the cryptographic precondition for update continuity. A signer mismatch is
the only thing Android checks that this project previously got wrong (RC1 was debug-signed),
and that specific failure mode is now provably eliminated — the same key produces a
higher-versionCode APK with an identical certificate.

**Not established:** that the OS actually performs the update on a real device and that app
data survives it. That is the part that matters to the owner, and it is honestly recorded as
untested rather than inferred.
