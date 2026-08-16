# PHASE 13 — PACKAGE VISIBILITY

Guide reference: §5. Build: frozen RC2 (`93c15f22…`), Advice Mode, no Device Owner.

## Device A — Samsung Galaxy S20 FE (SM-G781B, Android 13 / API 33)

Ground truth captured at Phase 13 start via `adb shell pm list packages`:

| Set | Ground truth | Thraksha (RC2 release) | Delta |
| --- | --- | --- | --- |
| Total packages | **441** | **441** | 0 |
| System (`-s`) | **415** | **415** | 0 |
| Third-party (`-3`) | **26** | **26** | 0 |
| Genuine owner-installed (excl. Guardian + 2 decoys) | **23** | **23** | 0 |
| `VisibilityScope` | — | `FULL` | — |

**Exact parity.** No package the OS reports is invisible to the scanner.

Depth as well as breadth — from the app's own SCAN DETAILS panel:

* **441 signing identities checked**
* **441 APK fingerprints computed**

Fingerprinting requires reading each APK, so the count is not an inventory listing of
packages that could not actually be inspected.

### Relevant third-party apps confirmed visible and scanned

The 23 genuine owner-installed applications:

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
com.samsung.android.app.notes.addons    com.samsung.android.oneconnect
com.samsung.android.app.tips            com.samsung.android.spay
com.samsung.android.app.watchmanager
```

Named in scan output with honest classifications: Google TV, Samsung Notes, Tips (REVIEW);
VillainCaller (KNOWN THREAT, controlled decoy). See `PHASE13_REAL_SCAN_COMPARISON.md`.

### Reduced-visibility warning

Correctly **absent** — `visibilityScope()` returns `FULL`, so neither the FindingsScreen
banner nor the "Partial" module summary triggers.

### API-level / OEM notes

API 33 with `targetSdk 36`, so Android 11+ package-visibility filtering is fully in force.
Visibility is achieved solely through the declared `QUERY_ALL_PACKAGES` permission — no
OEM-specific allowance is involved, so this result should carry to other OEMs at the same
or higher API level. **That expectation is untested** without Device B.

## Device B — Samsung Galaxy S24 Ultra

⛔ **NOT MEASURED — device not connected.** See `DEVICE_MATRIX.md`.

Guide §5 requires visibility comparison "on every device" and §5 further requires
investigation "if visibility differs materially by OEM/API level". With one device there is
no comparison and no way to detect an API-level or OEM divergence.

Specifically untested and material:

* **API 34/35 behaviour.** The S24 Ultra ships Android 14+. Google has tightened package
  visibility and restricted-permission handling across those releases; RC2's parity result
  on API 33 does not by itself establish the same on API 34+.
* **One UI 6/7 differences** in background-service and permission handling.
* Whether `QUERY_ALL_PACKAGES` yields the same full-registry view on newer Samsung firmware.

This is a genuine gap in Phase 13's core claim, not a formality.

## Summary

| Device | Ground truth | Discovered | Third-party visible | Verdict |
| --- | --- | --- | --- | --- |
| A — S20 FE (API 33) | 441 / 415 / 26 | 441 / 415 / 26 | 23 / 23 | ✅ exact parity |
| B — S24 Ultra | — | — | — | ⛔ not connected |
