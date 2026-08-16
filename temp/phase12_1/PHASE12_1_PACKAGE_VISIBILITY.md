# PHASE 12.1 — PACKAGE VISIBILITY AUDIT (TASK A)

Guide reference: `THRAKSHA_DEMO_IMPLEMENTATION_PHASE_12_1_RC2` §§4–9.

## 1. The defect

RC1 (`thraksha-guardian-1.0-privatealpha-rc1.apk`, release build type) discovered **322**
packages on the S20 FE while the debug build discovered **441**, and could not see a single
genuine user-installed third-party application.

RC1's own recorded breakdown (`temp/phase12/12b_soak/PHASE12_REAL_DEVICE_SOAK.md` line 199):

> Packages analysed | **322** (319 system + Guardian + VillainCaller + GoodCaller)

That is the whole story in one line: of the 26 third-party packages installed on the phone,
RC1 saw exactly **3** — itself and the two demo decoys it had explicitly declared. The 23
real apps the owner actually installed were invisible.

## 2. Cause — proven, not assumed

The guide (§5) requires the cause be verified rather than presumed to be the permission.
It was verified at four independent levels, and all four agree.

### Level 1 — source layout

`QUERY_ALL_PACKAGES` was declared **only** in `app/src/debug/AndroidManifest.xml`:

```xml
<!-- app/src/debug/AndroidManifest.xml (pre-12.1) -->
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.QUERY_ALL_PACKAGES" />
</manifest>
```

The Android Gradle Plugin merges a `src/<buildType>/AndroidManifest.xml` overlay into that
build type **only**. `src/debug/` therefore never reaches `release`. `app/src/main/AndroidManifest.xml`
declared no such permission — it declared the opposite intent in a comment:

> Thraksha deliberately does NOT request QUERY_ALL_PACKAGES.

### Level 2 — no other variant-dependent cause exists

Ruled out by inspection:

| Candidate cause | Finding |
| --- | --- |
| Product flavors | None. No `productFlavors` / `flavorDimensions` in `app/build.gradle.kts`. |
| `src/release/` overlay | Does not exist. `app/src/` held only `main`, `debug`, `test`, `androidTest`. |
| `sourceSets` overrides | None declared in any Gradle file. |
| `manifestPlaceholders` | None declared. |
| R8/resource shrinking stripping something | `isMinifyEnabled = false` for release. |
| Scanner code gated on build type | **None.** `AppInventory.inventoryDevice()` calls `packageManager.getInstalledPackages(0)` unconditionally; there is no `BuildConfig.DEBUG` branch anywhere in `app/src/main`. `visibilityScope()` reads the permission **live** off the installed package rather than inferring it from the build type. |

The scanner was never the problem. It faithfully enumerated everything the OS handed it and
honestly labelled the result `VisibilityScope.REDUCED`. It was the OS handing over less.

### Level 3 — the installed RC1 APK on the device

`adb shell dumpsys package com.thraksha.guardian` against the S20 FE with RC1 installed:

```
    requested permissions:
      android.permission.INTERNET
      android.permission.ACCESS_NETWORK_STATE
      android.permission.VIBRATE
      android.permission.FOREGROUND_SERVICE
      android.permission.FOREGROUND_SERVICE_DATA_SYNC
      android.permission.FOREGROUND_SERVICE_SPECIAL_USE
      android.permission.POST_NOTIFICATIONS
      android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
      android.permission.ACCESS_NOTIFICATION_POLICY
      android.permission.WRITE_SETTINGS
      android.permission.SCHEDULE_EXACT_ALARM
      com.thraksha.guardian.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION

    forceQueryable=false
    queriesPackages=[com.thraksha.demo.goodcaller, com.thraksha.demo.villaincaller]
```

No `QUERY_ALL_PACKAGES`. Visibility limited to two declared packages.

### Level 4 — the arithmetic matches exactly

Under Android 11+ filtering an app sees: itself + its `<queries>` + platform
force-queryable packages. RC1's 322 = 319 platform-visible system packages + Thraksha +
GoodCaller + VillainCaller. That is precisely the predicted set, with no residual
discrepancy left for a second cause to explain.

**Conclusion: single cause, fully explanatory — the permission was scoped to the wrong
source set.**

## 3. Device ground truth (S20 FE, `RZCW40LVBJD`, SM-G781B)

Captured 2026-08-16 via `adb shell pm list packages`:

| Set | Count | Evidence file |
| --- | --- | --- |
| All installed packages | **441** | `evidence/groundtruth_all_packages.txt` |
| System (`-s`) | **415** | `evidence/groundtruth_system_packages.txt` |
| Third-party (`-3`) | **26** | `evidence/groundtruth_thirdparty_packages.txt` |

The 26 third-party packages, in full:

```
com.facebook.katana                     com.samsung.android.voc
com.google.android.apps.docs            com.samsung.ecomm.global.in
com.google.android.apps.photos          com.samsung.sree
com.google.android.apps.youtube.music   com.sec.android.app.kidshome
com.google.android.videos               com.sec.android.app.popupcalculator
com.linkedin.android                    com.sec.android.app.sbrowser
com.microsoft.office.officehubrow       com.sec.android.app.shealth
com.microsoft.office.outlook            com.sec.android.app.voicenote
com.samsung.android.app.notes           com.sec.android.easyMover
com.samsung.android.app.notes.addons    com.thraksha.demo.goodcaller
com.samsung.android.app.tips            com.thraksha.demo.villaincaller
com.samsung.android.app.watchmanager    com.thraksha.guardian
com.samsung.android.oneconnect
com.samsung.android.spay
```

23 of these are genuine owner-installed applications — Facebook, LinkedIn, Outlook, Office,
Samsung Pay, Samsung Internet, Samsung Health, Samsung Notes, Google Photos/Docs, and so
on. **RC1 could see none of them.** These are exactly the apps a device-security scanner
exists to evaluate, so the defect removed essentially all of the product's real-world value.

## 4. The fix

`QUERY_ALL_PACKAGES` moved from the debug-only overlay into `app/src/main/AndroidManifest.xml`,
with the justification recorded in-place. `app/src/debug/AndroidManifest.xml` deleted — it
contained nothing else, and keeping a redundant overlay is exactly the shape of the original
bug.

The targeted `<queries>` block is **retained deliberately**: redundant while the permission
is held, but it keeps the two decoys visible if broad visibility is ever withdrawn for a
future Play-track build, and it documents the controlled demo sandbox.

Doc-comment corrections in `AppInventory.kt` (two KDoc blocks that described the permission
as debug-only). No behavioural code changed — the scanner needed no change and got none.

### Merge verified

`java -jar gradle/wrapper/gradle-wrapper.jar :app:processReleaseMainManifest` →
`app/build/intermediates/merged_manifest/release/processReleaseMainManifest/AndroidManifest.xml`
(archived as `evidence/merged_manifest_release_rc2.xml`) now contains:

```xml
<uses-permission android:name="android.permission.QUERY_ALL_PACKAGES" />
<queries>
    <package android:name="com.thraksha.demo.goodcaller" />
    <package android:name="com.thraksha.demo.villaincaller" />
</queries>
```

Debug and release now merge to the same visibility posture, which is the point: the
release build a person actually installs must not be a weaker scanner than the one used
to develop it.

## 5. Why the scanner requires broad visibility

Guide §6 requires this stated explicitly.

* **Scanning installed applications is Thraksha's core product function.** The Protect
  surface, the ThreatPack known-threat match, the generic capability rules, the evidence
  DECLARED/GRANTED/OBSERVED progression and the User ACT flow all operate on the set of
  apps installed on the device. With reduced visibility that set excludes every app the
  owner chose to install, which is the entire population of interest.
* **There is no narrower alternative.** Targeted `<queries>` requires knowing package names
  in advance. A security scanner's job is precisely to evaluate applications it does *not*
  know about ahead of time; an allowlist-shaped permission cannot express "everything the
  user installed". Intent-signature and provider-authority `<queries>` forms are similarly
  keyed to things known in advance.
* **The grant is read-only in this app.** `AppInventory` is the only `PackageManager`
  consumer in the security path. It calls `getInstalledPackages`, `getPackageInfo`,
  `getApplicationLabel`, `getInstallSourceInfo`. It installs nothing, suspends nothing,
  disables nothing, and uninstalls nothing. Containment happens only through an explicit
  User ACT decision under Device Owner, which is a separate mechanism and remains off by
  default (Advice Mode).
* **Thraksha holds itself to the same rule.** `GenericRiskRules` flags
  `QUERY_ALL_PACKAGES` as broad-visibility exposure in any app that requests it, and
  `DeviceScanInstrumentedTest.guardianItself_isScannedByTheSameRules_notSpecialCased`
  asserts Guardian appears in its own scan with generic findings from its own capability
  profile. Now that the permission is in the main manifest, that self-reporting is true in
  release too — previously the release build quietly understated its own capability set.

## 6. Play policy — a separate, future, unmet requirement

Stated plainly so it cannot be mistaken for an approval claim:

* `QUERY_ALL_PACKAGES` is a **restricted permission** under Google Play policy. Apps using
  it must submit a permission declaration and pass review.
* Device-security / anti-malware / anti-virus is one of Play's **permitted** use cases, and
  Thraksha's use falls squarely inside its intent. That makes an eventual approval
  *plausible*; it does not make it *granted*.
* **No Play declaration has been submitted and no review has been passed.** This build is
  sideloaded and owner-installed. Nothing in Phase 12.1 constitutes, implies, or advances
  Play Store approval.
* Play-track distribution is a separate future workstream. If a Play build is ever cut and
  the declaration is refused, the fallback is the reduced-visibility posture RC1 shipped
  with — which is why the honest `VisibilityScope.REDUCED` reporting path and the targeted
  `<queries>` block are both retained rather than deleted.
* The permission is declared openly in the main manifest with its rationale beside it. It
  is not hidden, obfuscated, or split across overlays.

## 7. After — on-device release verification ✅ COMPLETE

Measured on the RC2 release APK (signer `CN=Thraksha Guardian`, `debuggable=false`)
installed on the S20 FE, 2026-08-16.

| Metric | Ground truth | RC1 (release) | **RC2 (release)** |
| --- | --- | --- | --- |
| Total packages discovered | 441 | 322 | **441** ✅ |
| System packages | 415 | 319 | **415** ✅ |
| Third-party packages | 26 | 3 | **26** ✅ |
| Genuine owner-installed apps visible | 23 | **0** | **23** ✅ |
| `VisibilityScope` reported | — | `REDUCED` | **`FULL`** (warning correctly absent) |

**Exact parity with ground truth**, not merely improvement. The 119-package gap is closed
completely, and all 23 real owner-installed apps are now discoverable and scannable.

Depth, not just breadth: the release build reports **441 signing identities checked** and
**441 APK fingerprints computed**, so every discovered package was genuinely inspected
rather than counted.

### Proven at every level

| Level | Evidence | Status |
| --- | --- | --- |
| Source | Permission declared in `app/src/main/AndroidManifest.xml` | **done** |
| Manifest merge | archived as `evidence/merged_manifest_release_rc2.xml` | **done** |
| Shipped binary | `aapt2 dump badging` on the RC2 APK: `uses-permission: name='android.permission.QUERY_ALL_PACKAGES'` | **done** |
| Installed runtime | `dumpsys package com.thraksha.guardian` reports the permission held | **done** |
| Runtime, debug build | `build_holdsFullVisibility_andDiscoversTheRegistry` passes on both devices | **done** |
| **Runtime, release APK on S20 FE** | **441/26/415 measured in-app; see `PHASE12_1_REAL_SCAN.md`** | **done** ✅ |
| Persistence | identical counts after a full reboot | **done** |

### Reduced-visibility warning (§24)

Correctly absent. No code change was needed — `FindingsScreen.kt:131` and
`ProtectPresentation.kt:256` are both gated on the live `visibilityScope()`, which now
returns `FULL`. The honest-reporting path RC1 exercised simply stopped triggering.

### Safety unchanged by the broader view

Broad visibility exposed 119 more packages to the scan pipeline without widening what
Thraksha will act on: 0 packages suspended, 0 high-exposure profiles, 1 known-threat match
(the controlled decoy only), and on the DO AVD `arbitraryPackage_isRefused` plus the three
other containment-refusal tests all pass under Device Owner authority.

### Reduced-visibility warning (§24)

No code change was needed. Both surfaces are driven off the live `visibilityScope()`:

* `FindingsScreen.kt:131` — the "This build cannot see every installed package" banner is
  gated on `scan.visibilityScope == VisibilityScope.REDUCED`.
* `ProtectPresentation.kt:256` — the "Partial" module summary likewise.

`AppInventory.visibilityScope()` reads the permission off the installed package at runtime,
so once RC2 holds `QUERY_ALL_PACKAGES` both correctly stop appearing. Verifying that on the
release APK is part of the blocked work above.
