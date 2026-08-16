# PHASE 13 — REAL-DEVICE SCAN COMPARISON

Guide reference: §6. Build: frozen RC2 (`93c15f22…`), release, Advice Mode.

> **This document cannot fulfil its title.** A comparison requires ≥2 devices; only Device A
> is available. Device A results are complete and are recorded here so the comparison can be
> completed by adding Device B's column, not by re-running Device A.

## Device A — Samsung Galaxy S20 FE (SM-G781B, Android 13 / API 33)

Two full scans on the release build: one on first launch, one after a full reboot.

| Metric | Scan 1 (first launch) | Scan 2 (post-reboot) |
| --- | --- | --- |
| Packages discovered | **441** | **441** |
| — user apps | 26 | 26 |
| — system packages | 415 | 415 |
| Signing identities checked | **441** | **441** |
| APK fingerprints computed | **441** | **441** |
| Scan duration | **13.6 s** | **16.4 s** |
| Findings recorded | **56** | **56** |
| Known-threat matches | **1** | **1** |
| High-exposure profiles | **0** | **0** |
| Items surfaced as "needs attention" | 14 | 14 |

Deterministic across a reboot on every count; only wall-clock duration varies (13.6 s →
16.4 s, cold caches after boot).

### Throughput

441 packages fingerprinted in 13.6 s ≈ **32 packages/second**, including APK hashing. No
progress stall, no ANR, no partial-scan state.

### Known-threat matching

**1 match: VillainCaller** (`com.thraksha.demo.villaincaller`) — the controlled decoy,
matched on a ThreatPack indicator. **Zero** of the 23 genuine owner-installed apps matched a
known-threat indicator, despite all of them passing through the identical pipeline.
GoodCaller — same app category, same caller-ID permission baseline — correctly **not**
flagged. The discrimination that the demo exists to prove still holds at full device scale.

### Evidence stages

Findings are DECLARED/GRANTED statements. Every real-app finding is phrased as what the app
is *currently allowed* to do, e.g.:

* Google TV — "currently allowed to use network and package visibility" → REVIEW
* Samsung Notes — "currently allowed to use draw over other apps and media / storage and 1
  more" → REVIEW
* Tips — capability exposure → REVIEW

`High-exposure profiles: 0` — no real app was escalated on static capability alone, which is
the Phase 8.1 recalibration behaving as designed. No finding claims OBSERVED behaviour that
was not observed.

### Unavailable observations

Runtime observation (Stage 3 / OBSERVED) requires the VPN checkpoint or notification
listener to have actually seen traffic or events from a package. On a fresh install with
Network Guard off, **no OBSERVED evidence exists for any real app** — and the UI correctly
does not claim any. This is honest under-claiming, not a gap in the scan.

### Non-destructive — verified after each scan

| Check | Result |
| --- | --- |
| Packages suspended | **0** |
| Packages disabled / uninstalled | **0** (count still 441) |
| `com.facebook.katana` | `installed=true suspended=false` |
| `com.linkedin.android` | `installed=true suspended=false` |
| `com.microsoft.office.outlook` | `installed=true suspended=false` |
| `com.google.android.videos` | `installed=true suspended=false` |
| `com.samsung.android.app.notes` | `installed=true suspended=false` |

No automatic containment of any arbitrary app. Advice Mode, no Device Owner.

### False-positive candidates

14 items surfaced; 1 is the intended decoy, leaving **13 REVIEW items on legitimate apps**.
Analysed individually in `PHASE13_FALSE_POSITIVE_LOG.md`. **No rule was modified** to change
any device's results (guide §6).

## Device B — Samsung Galaxy S24 Ultra

⛔ **NOT MEASURED — device not connected.**

Untested and material to Phase 13's purpose:

* scan duration on newer silicon (Snapdragon 8 Gen 3 vs 865) — the throughput claim above is
  single-SoC;
* whether package counts and fingerprinting hold on Android 14+/One UI 6+;
* whether a different installed-app population produces different or worse false positives —
  **13 REVIEW items from one app population is not a calibration**;
* OEM differences in `getInstallSourceInfo`, component metadata and label resolution.

## Summary

| Metric | Device A (S20 FE) | Device B (S24 Ultra) |
| --- | --- | --- |
| Discovered / ground truth | 441 / 441 ✅ | ⛔ |
| Fingerprinted | 441 ✅ | ⛔ |
| Duration | 13.6 s / 16.4 s | ⛔ |
| Findings | 56 | ⛔ |
| Known-threat | 1 (decoy only) ✅ | ⛔ |
| High-exposure | 0 | ⛔ |
| Destructive actions | 0 ✅ | ⛔ |
