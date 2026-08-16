# PHASE 10C — DEVICE RESULTS

**Date:** 2026-08-15 · **Device:** Samsung `SM-G781B` / `RZCW40LVBJD`, Android 13, arm64-v8a
**Model:** Gemma 4 E2B · **Runtime:** litertlm-android 0.16.0, `Backend.CPU(4)`
**Verdict: PASS**

---

## 1. Full regression after a clean build (guide §36, §37)

| Suite | Result |
|---|---|
| `clean :app:assembleDebug` + `assembleDebugAndroidTest` | **BUILD SUCCESSFUL** |
| `:app:testDebugUnitTest` | **213 tests, 0 failures** |
| `connectedDebugAndroidTest` — S20 FE, all classes except the 13-min eval and the backend benchmark | **90 tests, 0 failures, 3 skipped** |

The 3 skips are the frozen Phase 9 baseline skips (Device-Owner-only enforcement,
Full-Power ACT, and the DND-denial test that by design only runs when the access is
absent). **Identical to the pre-Phase-10 baseline.**

### Coverage inside that 90

| Area | Classes |
|---|---|
| Phase 9 automation | `Phase9AutomationInstrumentedTest` — Meeting/Focus/Driving, snapshot, restore, rollback, process recovery, expiry, one-active-routine, audit chain |
| Phase 8 scanner | `DeviceScanInstrumentedTest`, `SecurityAuditEngineInstrumentedTest`, `PackageVisibilityInstrumentedTest` |
| Phase 8.1 evidence + User ACT | `Phase81EvidenceInstrumentedTest`, `Phase81FullPowerActInstrumentedTest` |
| ThreatPack / Rulepack | `ThreatIntelInstrumentedTest`, `FoundationInstrumentedTest` |
| Network Guard | `NetworkGuardInstrumentedTest` |
| Device Owner | `DeviceOwnerEnforcementInstrumentedTest` |
| GoodCaller / VillainCaller | covered by the threat-intel and policy suites |
| Audit chain | `AuditLogInstrumentedTest`, `AuditTrailPersistenceInstrumentedTest` |
| Phase 10A runtime | `Phase10ARuntimeInstrumentedTest` (8) |
| Phase 10C integration | `Phase10CIntegrationInstrumentedTest` (9) |

**AI integration changed no deterministic behaviour.** Every Phase 1–9 suite passes
unchanged, and `AutomationIntent`, `AutomationSafetyPolicy`, `AutomationPlanner`,
`AutomationEngine` and all executors are byte-identical to their Phase 9 state.

## 2. AI-originated automation, executed through the real UI

| Stage | Ground truth (adb) |
|---|---|
| Before | `bright=91 mode=AUTO zen=0 ringer=NORMAL` |
| After interpretation (plan shown) | **unchanged** — `91 / AUTO / 0 / NORMAL` |
| After user pressed START | **`77 / MANUAL / 1 / VIBRATE`** |
| After STOP & RESTORE | **`91 / AUTO / 0 / NORMAL`** — the exact pre-state |

## 3. Performance on this handset

| Metric | Value |
|---|---|
| SHA-256 integrity gate (2.41 GiB) | 3.19 s |
| Model load, cold | 6.63 s |
| Model load, warm | 5.20 s |
| Median inference latency | 11.5 s |
| PSS while loaded | ~2.0–2.1 GiB |
| Peak PSS during inference | 2.80 GiB (device has 7.44 GiB) |
| Released on unload | 1.68 GiB in 289 ms |
| 20-request stress | net PSS **−59 MiB**, latency drift **+0.5 %** |

## 4. Device left safe

Final state, adb-verified: **`screen_brightness_mode=1 (AUTO)`, `screen_off_timeout=600000`,
`zen_mode=0`, `mode_ringer=2 (NORMAL)`** — the Phase 9 baseline exactly. No routine active,
no persisted restoration duty.

Radios restored to their **original** configuration: `wifi_on=1`, `mobile_data=0`
(mobile data was off before testing began and is off now).

Development artifacts remain in the app's own external files directory (the provisioned
model, the evaluation set, three result JSONs). All were pulled into this documentation
set, and Android removes the directory on uninstall.
