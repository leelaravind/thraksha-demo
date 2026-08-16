# THRAKSHA DEMO — PHASES 4–6 IMPLEMENTATION PROGRESS

Implementation run against `temp/THRAKSHA_DEMO_IMPLEMENTATION_PHASES_4_6.md`,
starting from the Phase 1–3 baseline described by
`temp/THRAKSHA_DEMO_PHASES_1_3_PROGRESS.md`.

**Advice-mode device (this run):** Samsung `SM-G781B` (S20 FE), Android 13 / API 33,
serial `RZCW40LVBJD`, privilege `DEVICE_ADMIN` (not Device Owner).

---

## BASELINE (before any Phase 4 change)

| Check | Result |
|---|---|
| `./gradlew :app:testDebugUnitTest` | **BUILD SUCCESSFUL** — 17 tests, 0 failures |
| `./gradlew :app:connectedDebugAndroidTest` (SM-G781B) | **20 tests, 0 failures** |
| Device attached | `RZCW40LVBJD  device  model:SM_G781B` |

No pre-existing failures. Phases 1–3 behaviour confirmed intact before the first edit.

---

## PHASE 4 — POLICY ENGINE + ADVICE MODE ✅ COMPLETE

### Files created

| File | Purpose |
|---|---|
| `security/policy/SecurityAction.kt` | Sealed action model (`DenyPermission`, `SuspendPackage`, `BlockNetwork`, `OpenAppSettings`, `NoAction`) each declaring `RequiredAuthority` (NONE / DEVICE_OWNER / FUTURE_SUBSYSTEM), an `ActionRiskTier`, and `isExecutable`. `RecommendedAction` pairs an action with its finding-derived rationale. |
| `security/policy/SecurityDecision.kt` | `DecisionType { OBSERVE, ADVISE, ACT }`, the full `SecurityDecision` (findings, highest severity, max rule confidence, privilege, execution mode, recommendations, explanation, deterministic `decisionId`), and `PolicyThresholds` (pure mirror of the ConfigStore 60/80/95/99 tiers). |
| `security/policy/PolicyEngine.kt` | Pure, deterministic `(findings, PrivilegeLevel, ExecutionMode, thresholds) → SecurityDecision`. Zero Android imports/calls. |
| `security/policy/Enforcer.kt` | `Enforcer` interface + structured results: `ActionStatus { ADVISED, SUCCEEDED, FAILED, UNSUPPORTED, NOT_AUTHORISED, ALREADY_IN_STATE }`, `ActionOutcome`, `EnforcementVerdict { ADVISED, ACTED, PARTIALLY_ACTED, NOT_ACTED }`, `EnforcementResult`. |
| `security/policy/AdvisoryEnforcer.kt` | The non-privileged path: converts a decision into a **successful ADVISED** result, emits `AdviceIssued` onto the bus. Holds no `Context`/Android import — structural proof of zero privileged action. Also the safe fallback for ACT with no privileged enforcer: DO actions recorded `NOT_AUTHORISED`, never pretended done. |
| `app/src/test/.../PolicyEngineTest.kt` | 15 off-device tests — every §4.10 policy case. |
| `app/src/test/.../AdvisoryEnforcerTest.kt` | 4 off-device tests — ADVISED success, ACT fallback never claims success, bus emission, determinism. |
| `app/src/androidTest/.../PolicyPipelineInstrumentedTest.kt` | 5 on-device tests — ADVISE end-to-end, GoodCaller untouched, zero privileged action (OS ground truth), DECISION/ADVISED rows in the encrypted chain, decision determinism. |

### Files modified

| File | Change |
|---|---|
| `security/engine/Finding.kt` | Added `offendingPermissions: List<String> = emptyList()` so policy can map findings to responses without parsing evidence strings. |
| `security/engine/RuleEngine.kt` | Populates `offendingPermissions` for permission rules. Detection logic unchanged. |
| `security/events/SecurityEvent.kt` | Added `DecisionMade` and `AdviceIssued` events (same bus, no second bus). |
| `ThrakshaApplication.kt` | Collector maps `DecisionMade → "DECISION"` rows (tier = max rule confidence) and `AdviceIssued → "ADVISED"` rows. |
| `security/engine/SecurityAuditEngine.kt` | New stage 5–6 `decideAndRespond()`: per app, emit THREAT events → `PolicyEngine.decide()` under live privilege/mode/thresholds → emit `DecisionMade` (findings only) → route ADVISE/ACT to the enforcer; OBSERVE executes nothing. `AppAuditResult` gains `decision` + `enforcement` (defaulted, non-breaking). SCAN summary still last. |
| `ui/screens/DashboardScreen.kt` | ADVISED/ACTED badge under the threat status (from `EnforcementVerdict`, never assumed); POLICY DECISION section with explanation, recommendations, "FULL POWER would be able to…" (only for actions Phase 5 actually implements); "rule confidence N" wording (not "%"); DECISION/ADVISED feed styling; enforcement row now "Advisory only (not Device Owner)". |

### Architecture decisions

1. **PolicyEngine is pure and takes its Android-observed inputs as parameters.**
   `SecurityAuditEngine` reads `SecurityCapability.currentLevel()`, `ConfigStore`
   execution mode and tier thresholds, and hands them in. Every policy path is
   unit-tested off-device; an `android.*` call would throw from the mockable jar.
2. **ExecutionMode gates only Device Owner.** Without authority the only honest response
   to findings is ADVISE, whatever the policy dial says (§4.4). For DO:
   OBSERVE → OBSERVE, GUIDED → ADVISE (explicit approval), AUTO_DEFEND → ACT for
   eligible HIGH/CRITICAL findings, LOCKDOWN treated as at-least-AUTO_DEFEND (no broad
   lockdown implemented, per §4.4).
3. **Tier thresholds gate action invasiveness, not detection.** `ActionRiskTier` maps
   each action to the seeded 60/80/95/99 confidence thresholds: auto-executing a
   MEDIUM-risk `DenyPermission` needs rule confidence ≥ 80, a HIGH-risk
   `SuspendPackage` ≥ 95. Confidence values are named/authored **rule weights**, never
   probabilities (§4.5).
4. **Recommendations correspond to findings, never generic punishment (§4.6).**
   Runtime-deniable offending permission → `DenyPermission(exactly that permission)`.
   `QUERY_ALL_PACKAGES` is install-time, cannot be revoked at runtime → advisory
   review-in-settings only (recommending its denial would recommend the impossible).
   CRITICAL compound finding → `SuspendPackage`. `BlockNetwork` exists in the model but
   is `FUTURE_SUBSYSTEM`/not executable and is never recommended.
5. **ADVISED is a success state.** `AdvisoryEnforcer` returns verdict `ADVISED` with
   per-action `ADVISED` outcomes; an ACT decision reaching it (no privileged enforcer in
   this build) records DO actions as `NOT_AUTHORISED` — visible, honest, never converted
   to fake success. `ACTED` in the UI can only ever come from a verified
   `EnforcementVerdict.ACTED`, which nothing in Phase 4 can produce.
6. **Audit-chain order is `SCAN`-terminated `THREAT* → DECISION → ADVISED`** per app,
   through the existing bus/collector — no second bus, no DB schema change. Clean apps
   get an OBSERVE decision in memory but no DECISION row (the SCAN row is their record).
7. **`Finding` was extended, not overloaded**: `offendingPermissions` is structured
   detection output; the decision remains a separate `SecurityDecision` type.

### Build & test results

| Check | Result |
|---|---|
| `./gradlew :app:testDebugUnitTest` | **BUILD SUCCESSFUL** — **36 tests, 0 failures** (17 baseline + 19 new) |
| `./gradlew :app:connectedDebugAndroidTest` (SM-G781B) | **25 tests, 0 failures** (20 baseline + 5 new) |

All pre-existing Phase 1–3 tests pass unmodified — no existing test file was edited.

### Runtime checks performed (SM-G781B, Android 13, DEVICE_ADMIN)

Cold start after force-stop; logcat `ThrakshaApplication`:

| Check | Result |
|---|---|
| Launch audit fires | ✅ once per process |
| GoodCaller | ✅ 0 findings → decision `OBSERVE` → **CLEAN / WATCHING**, no advisory |
| VillainCaller | ✅ 4 findings → decision `ADVISE` → **THREAT DETECTED · ADVISED** |
| Decision explanation | ✅ "Thraksha is not Device Owner (privilege DEVICE_ADMIN), so it cannot act on another app; it advises instead." |
| Recommendations | ✅ deny ACCESS_FINE_LOCATION, deny READ_MEDIA_IMAGES, suspend package (from compound rule), review in settings |
| "FULL POWER would be able to…" | ✅ lists only deny-permission + suspend (Phase 5 capabilities), no network claim |
| Privileged action count | ✅ **zero** — OS ground truth asserted by instrumented test (VillainCaller still enabled + launchable); no DPM call exists in the app (`grep` below) |
| DECISION row in encrypted chain | ✅ `ADVISE VillainCaller (com.thraksha.demo.villaincaller): 4 finding(s), max severity CRITICAL …` tier 95 |
| ADVISED row in encrypted chain | ✅ recommendations + "no automatic OS action performed" |
| Chain verifies after new row types | ✅ asserted on-device |
| `AndroidRuntime:E` | ✅ none |

Safety grep (repo-wide, `app/src/main`):
`setPackagesSuspended|setPermissionGrantState|setApplicationHidden|setUserRestriction|lockNow|wipeData|resetPassword|reboot(` → **0 hits**. Phase 4 introduces no DPM call anywhere.

Screenshots: `temp/screenshots/phase4_dashboard_top.png`, `phase4_advice_card.png`
(THREAT DETECTED · ADVISED badges), `phase4_policy_decision.png` (decision, recommendations,
FULL POWER line, ADVISED result), `phase4_decision_section.png`, `phase4_audit_feed.png`.

### Limitations / divergences

* `LOCKDOWN` execution mode is mapped to AUTO_DEFEND's act-eligibility (documented in
  code); its broader Safe-Mode semantics remain future work — the guide reserves them.
* Clean apps produce no DECISION audit row (deliberate — the SCAN row records the clean
  audit; a DECISION row per clean app per scan would be noise). Divergence from a strict
  reading of §4.9's example, none from its requirements.
* `ConfigStore` execution mode is still not user-changeable in the UI; the device's
  seeded default (OBSERVE) is displayed. Irrelevant for Advice Mode (mode gates only DO).
* The advisory is shown in the audit card + audit trail; no system notification is
  posted (guide §4.8 asked for minimal UI extension).

### Exact next step (completed — see Phase 5 below)

Phase 5: document Device Owner provisioning in `temp/DEVICE_OWNER_DEMO_SETUP.md`,
verify the dedicated test phone's eligibility (accounts must be empty for
`dpm set-device-owner`), then implement `DeviceOwnerEnforcer` behind the existing
`Enforcer` interface with the explicit VillainCaller-only target allowlist.

---

## PHASE 5 — FULL POWER DEVICE OWNER ENFORCEMENT ✅ COMPLETE
### (verified on a real-Android emulator; Samsung phone is Knox-blocked — see Divergences)

### Files created

| File | Purpose |
|---|---|
| `temp/DEVICE_OWNER_DEMO_SETUP.md` | Provisioning/removal procedure, eligibility checks, the observed Samsung Knox blocker, and the emulator provisioning path actually used. Written **before** any provisioning attempt, per §5.1. |
| `security/enforcement/EnforcementTargets.kt` | Explicit allowlist: `com.thraksha.demo.villaincaller` only. Checked by the enforcer **before** the authority check, so Guardian / GoodCaller / launcher / system / arbitrary packages are refused even on a fully provisioned build. |
| `security/enforcement/DeviceOwnerEnforcer.kt` | The ONLY class in the app calling DPM mutation APIs. Per action: allowlist → verify genuine Device Owner → record before-state → execute → **query actual resulting OS state** → report only what the OS confirms → emit `ActionTaken` (persisted). Implements runtime-permission denial (`setPermissionGrantState` DENIED) and package suspension (`setPackagesSuspended`), plus the verified `restore()` path (unsuspend + permission grant-state back to DEFAULT). |
| `app/src/test/.../EnforcementTargetsTest.kt` | 4 unit tests on the allowlist boundary. |
| `app/src/androidTest/.../DeviceOwnerEnforcementInstrumentedTest.kt` | 6 on-device safety tests: GoodCaller/Guardian/arbitrary-package refusal (any privilege state), restore refusal for non-targets, NOT_AUTHORISED without Device Owner, and the full DO round-trip (contain → OS-verified → idempotent re-contain → restore → OS-verified → repeat-restore safe). |

### Files modified

| File | Change |
|---|---|
| `security/engine/SecurityAuditEngine.kt` | ACT decisions route to `DeviceOwnerEnforcer` only when `SecurityCapability.canEnforce()` verifies at execution time; otherwise advisory fallback. Added `approveAndEnforce()` (GUIDED explicit-approval path, refuses without DO) and `restoreDemoState()` (verified restore; resets dashboard to pre-scan state instead of silently re-auditing — under AUTO_DEFEND a hidden re-audit would instantly re-contain, making restore look broken). |
| `ThrakshaApplication.kt` | `ModeChanged` events now persist as `MODE` audit rows (mode transitions leave a trace). |
| `ui/screens/DashboardScreen.kt` | Execution-mode selector (OBSERVE/GUIDED/AUTO_DEFEND; writes ConfigStore + emits ModeChanged); "Approve containment (GUIDED)" button (shown only for DO + GUIDED + ADVISE); verified-actions list under ACTED (successful, OS-verified operations only — failed ones are never shown as done); "Restore demo state" button after ACTED/PARTIALLY_ACTED. |
| `app/src/androidTest/.../AuditTrailPersistenceInstrumentedTest.kt` | De-flaked (pre-existing race exposed by the new DECISION/ADVISED rows): waits for DB quiescence before baselining and for the audit's final SCAN row before asserting. Assertions unchanged. |

### Device Owner provisioning — what actually happened

1. **Samsung SM-G781B (the dedicated phone): provisioning is permanently impossible.**
   All documented preconditions held (0 accounts, single user 0, no owner, online), yet
   `dpm set-device-owner` fails with `E DevicePolicyManager: Failed in device integrity
   check`. Knox attestation output: **`TrustBoot: Abnormal, Warranty: Abnormal`**
   (`keymaster_swd: abnormal WB : 0x1`) — this unit's Knox warranty fuse is tripped
   (past unofficial firmware). That is unerasable hardware state; **factory reset would
   not help**, so no reset was performed and none would have been useful. Retried
   online/offline to isolate the cause; identical failure. Side effect discovered and
   documented: each failed attempt deactivates the active Device Admin (restored via
   `dpm set-active-admin`).
2. **Emulator (AVD `thraksha_do`, AOSP android-33 x86_64): provisioned cleanly.**
   Fresh AVD (0 accounts, single user), all three APKs installed,
   `dpm set-device-owner` → `Success`. Dashboard reads `DEVICE_OWNER` → **FULL POWER**.
   Real Android, real DevicePolicyManager — labelled as emulator verification wherever
   reported. Discovered en route: `am force-stop` does not kill a Device Owner app.

### Build & test results

| Check | Result |
|---|---|
| `./gradlew :app:testDebugUnitTest` | **40 tests, 0 failures** (36 + 4 allowlist) |
| `connectedDebugAndroidTest` — SM-G781B (DEVICE_ADMIN) | **31 tests, 0 failures, 1 skipped** (the DO round-trip, correctly) |
| `connectedDebugAndroidTest` — thraksha_do AVD (DEVICE_OWNER) | **31 tests, 0 failures, 4 skipped** (the requires-non-DO tests, correctly) |

The same suite passing in both privilege states is the §5.11 regression proof: detector,
rulepack, audit engine and PolicyEngine are shared; only the enforcement boundary differs.

### Runtime verification (emulator, Device Owner)

OS ground truth via `dumpsys package com.thraksha.demo.villaincaller` at every step:

| Step | UI | OS state |
|---|---|---|
| Boot, OBSERVE (default) | FULL POWER · decision OBSERVE, no action | `suspended=false` |
| Mode → AUTO_DEFEND → Run Security Check | THREAT DETECTED → **ACTED**, 3 ✓ verified actions | `suspended=true`; `ACCESS_FINE_LOCATION`/`READ_MEDIA_IMAGES` `granted=false` with **`POLICY_FIXED`** |
| Restore demo state | back to pre-scan slate; RESTORE rows in feed | `suspended=false`; `POLICY_FIXED` cleared (DEFAULT) |
| Mode → GUIDED → Run Security Check | THREAT DETECTED → **ADVISED** + "Approve containment (GUIDED)" | `suspended=false` (no auto action) |
| Tap Approve | **ACTED**, verified actions listed | `suspended=true` + `POLICY_FIXED` |
| Restore demo state | pre-scan slate | `suspended=false`, permissions DEFAULT |
| GoodCaller throughout | CLEAN / WATCHING | never suspended, never modified |

Advice-mode phone (SM-G781B) re-verified after every change: same detection, ADVISED,
zero privileged action (`VillainCaller` enabled + launchable, asserted by instrumented
test from OS state).

Audit chain now answers all four §5.12 questions in sequence:
`THREAT* → DECISION (ACT/ADVISE + why) → ACTION ("SUCCEEDED: Verified OS state: …") /
ADVISED → SCAN`, plus `MODE` rows for the operator's mode changes and `RESTORE`-prefixed
ACTION rows for restorations. Chain verification asserted on-device after all new row
types.

### Safety verification

* Destructive-API grep across all three modules:
  `wipeData|resetPassword|reboot(|setApplicationHidden|addUserRestriction|setUninstallBlocked|lockNow|FORCE_STOP`
  → **0 code hits** (one KDoc comment naming what is absent).
* All DPM mutation calls live in exactly one file: `security/enforcement/DeviceOwnerEnforcer.kt`.
* Allowlist refusal proven on a provisioned Device Owner build (GoodCaller/Guardian/
  arbitrary targets → NOT_ACTED, OS state untouched).
* ACTED appears only after `SUCCEEDED`/`ALREADY_IN_STATE` outcomes whose detail carries
  the re-queried OS state; `PARTIALLY_ACTED`/`NOT_ACTED` exist and are never rounded up.

### Screenshots

`temp/screenshots/`: `phase5_advice_regression.png`, `phase5_mode_selector.png` (phone);
`phase5_fullpower_badge.png`, `phase5_mode_row.png`, `phase5_acted_card.png`,
`phase5_acted_detail.png`, `phase5_verified_actions.png`, `phase5_restore_button.png`,
`phase5_guided_advise.png`, `phase5_guided_acted.png`, `phase5_find_restore.png`,
`phase5_feed_check2.png` (emulator).

### Limitations / divergences

* **FULL POWER verified on an AOSP emulator, not the Samsung phone.** The phone's Knox
  warranty fuse permanently blocks Device Owner there (§5 stop condition hit,
  documented, not masked). A demo phone with an intact Knox bit (or any non-Samsung
  device) will behave like the emulator; the provisioning doc covers both.
* `device_admin.xml` still declares the historical `<wipe-data/>` policy inherited from
  the baseline. No code path invokes it (grep-verified); left untouched per the
  do-not-rewrite rule, but flagged for a conscious decision before any public demo.
* The demo restore returns the dashboard to the pre-scan state rather than re-auditing
  (deliberate, documented in code — an auto re-audit under AUTO_DEFEND instantly
  re-contains the still-present threat).
* `pm list packages` after suspension still lists the package (suspension ≠ removal) —
  wording everywhere is "suspended/quarantined", never "removed".

### Exact next step (completed — see Phase 6 below)

Phase 6: signed ThreatPack (`threatpack.json/.sig/threatpack_public.key`),
`tools/sign_threatpack.sh`, fail-closed `ThreatPackVerifier`/`ThreatPackLoader`,
`ThreatIntelligenceScanner` (PACKAGE_NAME / SIGNING_CERT_SHA256 / BASE_APK_SHA256),
merge into `SecurityAuditEngine`, corruption tests, performance measurement.

---

## PHASE 6 — ANTIVIRUS / SIGNED THREAT INTELLIGENCE ✅ COMPLETE

### Files created

| File | Purpose |
|---|---|
| `security/threatintel/ThreatPackModels.kt` | `ThreatPack`/`ThreatIndicator` schema (schemaVersion, packVersion, generatedAt, optional expiresAt, indicators) + `IndicatorTypes`: SUPPORTED = PACKAGE_NAME / SIGNING_CERT_SHA256 / BASE_APK_SHA256; FUTURE = DOMAIN/IP/URL/FILE_HASH/MALWARE_FAMILY (representable, never evaluated). `weight` is an authored signed rule-weight, not a probability. |
| `security/threatintel/ThreatPackParser.kt` | Pure fail-closed schema validation: exact schemaVersion, unique ids, 64-lowercase-hex digests, non-blank package values, weight 0..100; an unknown indicator type rejects the whole pack. |
| `security/threatintel/ThreatPackLoader.kt` | Assets → signature verification (same RSA-2048/SHA-256 primitive as the rulepack, the threat pack's **own key pair**) → schema validation → `ThreatPackState.Verified` or **`Unavailable(reason)`** — an invalid pack surfaces visibly instead of scanning nothing and looking clean. Shaped for a future verify-then-activate update flow; this build is strictly offline (no networking added). |
| `security/threatintel/AppFingerprinter.kt` | The only Android-touching threat-intel class: signing-cert SHA-256 via `GET_SIGNING_CERTIFICATES`/`SigningInfo` (rotation history included), base-APK SHA-256 streamed from `sourceDir` (64 KB buffer, never whole-file in memory), per-stage durations captured. Unreadable APK degrades to a null hash, never a crash. |
| `security/threatintel/ThreatIntelligenceScanner.kt` | Pure matcher: literal equality per supported type; disabled and FUTURE indicators never match; produces `Finding`s with `source = THREAT_INTELLIGENCE`, evidence carrying indicator id/type, pack version, classification. |
| `tools/make_threatpack.sh` | Computes the sample's cert digest (apksigner) + base-APK digest from the built `villaincaller-debug.apk`, generates `threatpack.json` (3 live DEMO_TEST_THREAT indicators + disabled control + non-matching-hash control + FUTURE-type control), signs with `keys/threatpack_private.pem`, self-verifies. |
| `villaincaller/villain-demo.keystore` | VillainCaller's own committed demo signing key (with a `.gitignore` exception + rationale). Both decoys sharing the debug cert would have made the cert indicator flag GoodCaller too; distinct signers make certificate identity a real controlled signal. NOT part of Guardian's trust model. |
| `app/src/test/.../ThreatPackTest.kt` | 20 off-device tests: real-asset signature verify, flipped byte / modified sig / wrong key rejection, every parser fail-closed rule, all matching paths incl. negative controls, weight carriage, and bundled-pack honesty (only DEMO_TEST_THREAT / DEMO_NEGATIVE_CONTROL classifications; PACKAGE_NAME indicators point only at the sample). |
| `app/src/androidTest/.../ThreatIntelInstrumentedTest.kt` | 5 on-device tests: verified load on the production path, tampered bytes → visible Unavailable, stable real fingerprints + decoy signers differ, unified two-pillar audit (VillainCaller matches package+cert+APK indicators, GoodCaller clean under BOTH pillars, controls never match), timing capture. |

### Files modified

| File | Change |
|---|---|
| `security/engine/Finding.kt` | `FindingSource { PROFILE_RULE, THREAT_INTELLIGENCE }` + defaulted `source` field. One finding model, two pillars — no parallel finding system. |
| `security/engine/SecurityAuditEngine.kt` | One audit now runs: verified rulepack → verified threat pack (or visible Unavailable) → single inventory → per app: RuleEngine + fingerprint-once + scanner → merged findings → PolicyEngine → Advise/Act → audit. Added `ThreatIntelStatus` + `ScanTimings` to `AuditResult.Completed`; SCAN summary row now records both pillars' verification state. |
| `ui/screens/DashboardScreen.kt` | "Profile check" + "Threat intelligence" status rows (Unavailable rendered loud and red, never as clean); per-finding source label — `PROFILE CHECK` vs `THREAT INTELLIGENCE · DEMO TEST THREAT MATCH`; honesty footnote extended. |
| `villaincaller/build.gradle.kts` | Signs with the committed demo keystore (debug + release). |
| `.gitattributes` | `-text` protection for the three new signed assets + the keystore. |
| `.gitignore` | Documented exception for `villaincaller/villain-demo.keystore`. |
| `app/src/androidTest/.../FoundationInstrumentedTest.kt` | `configDefaults_seedAndReadBack` assumed the seeded OBSERVE mode could never change; since Phase 5 made the mode a real operator setting, it now proves set/read-back round-trips and restores the configured value. Tier assertions unchanged. |

### Architecture decisions

1. **Two pillars, one finding model, one audit.** The scanner emits ordinary `Finding`s
   tagged `THREAT_INTELLIGENCE`, merged with profile findings before the PolicyEngine —
   which therefore consumes unified findings with zero changes. Indicator weights are
   authored signed values; no probabilistic combination was invented.
2. **Threat-pack failure is a state, not an exception.** Rulepack failure still aborts
   (no rules → nothing trustworthy to evaluate), but threat-pack failure yields
   `Unavailable(reason)` carried through result + UI + SCAN row — profile detection
   continues, and the missing pillar is impossible to mistake for "no matches".
3. **VillainCaller got its own signing identity.** With both decoys debug-signed, a
   SIGNING_CERT_SHA256 indicator would match GoodCaller — the exact "flag everything
   visible" failure §6.13 exists to prevent. The committed demo keystore is the sample's
   identity, not trust material; Guardian's signing keys remain gitignored.
4. **`BASE_APK_SHA256` names exactly what is hashed** (base APK, streamed; splits are
   not hashed) — in the indicator name, the finding evidence, and the UI. Verified:
   the clean rebuild reproduces a byte-identical APK, so the signed digest survives
   rebuilds; `tools/make_threatpack.sh` re-derives it in one command if that changes.
5. **Negative controls ship in the signed pack**: a disabled GoodCaller record (proving
   disabled ≠ evaluated), an enabled never-matching digest (proving value matching),
   and a FUTURE-typed record (proving representable ≠ evaluated). All classified
   `DEMO_NEGATIVE_CONTROL`; the sample's records are `DEMO_TEST_THREAT` — no record
   claims a real malware family.

### Build & test results

| Check | Result |
|---|---|
| `./gradlew clean :app:assembleDebug :goodcaller:assembleDebug :villaincaller:assembleDebug` | **BUILD SUCCESSFUL** (104 tasks, all executed) |
| `./gradlew :app:testDebugUnitTest` | **60 tests, 0 failures** |
| `connectedDebugAndroidTest` — SM-G781B (DEVICE_ADMIN) | **36 tests, 0 failures, 1 skipped** (DO round-trip) |
| `connectedDebugAndroidTest` — thraksha_do AVD (DEVICE_OWNER) | **36 tests, 0 failures, 4 skipped** (non-DO-only tests) |

Unit: ThreatPackTest 20, PolicyEngineTest 15, RuleEngineTest 9, AdvisoryEnforcerTest 4,
EnforcementTargetsTest 4, AuditLogTest 2, RulepackVerifierTest 2, SecurityEventBusTest 2,
AuditLogConcurrencyTest 1, ExampleUnitTest 1.

### Scan performance (Samsung S20 FE, §6.18)

`Scan timings: inventoryMs=8, ruleEngineMs=0, certFingerprintMs=5, apkHashMs=11, totalMs=52`
— cold-start launch audit, 2 apps, both pillars, hashing a ~3.1 MB APK on Dispatchers.IO.
UI remained responsive; no optimisation needed at demo scale.

### Runtime verification

**S20 FE (Advice Mode):** launch + manual audit → 7 findings (4 profile + 3 threat-intel);
status rows "Profile check: rulepack v2 verified · 5 active rules" and "Threat
intelligence: pack v1 verified · 4 indicators"; VillainCaller card shows both
`PROFILE CHECK` and `THREAT INTELLIGENCE · DEMO TEST THREAT MATCH` findings incl. the
byte-for-byte base-APK digest; decision ADVISE over all 7 findings → **ADVISED**, zero
privileged action. GoodCaller **CLEAN / WATCHING** under both pillars.

**Emulator (FULL POWER):** GUIDED launch audit → 7 findings → ADVISED; switched to
AUTO_DEFEND → Run Security Check → ACT → OS-verified `suspended=true` +
`POLICY_FIXED` on both permissions → **ACTED** with per-action verified state → Restore
→ OS-verified `suspended=false`, permissions back to DEFAULT. Audit feed shows the full
`THREAT×7 → DECISION(ACT) → ACTION(SUCCEEDED: Verified OS state …) → SCAN` chain.

Screenshots: `phase6_pillars_status.png`, `phase6_goodcaller_clean.png`,
`phase6_intel_match.png`, `phase6_intel_findings.png` (phone);
`phase6_emu_acted_card.png`, `phase6_emu_restore_area.png` (feed ACTION rows),
`phase6_emu_find_restore.png`, `phase6_emu_restore_btn.png` (emulator).

### Limitations / divergences

* The decoys' certificates are demo-generated self-signed keys; the pack's cert match is
  a controlled test signal, as §6.8 anticipates. The debug certificate itself is never
  called malicious — only the villain's dedicated demo cert is listed.
* `expiresAt` is schema-representable but not enforced (no clock/expiry policy in the
  offline demo). Documented in the model KDoc.
* Rebuilding **villaincaller with changed sources** invalidates the BASE_APK_SHA256
  indicator until `tools/make_threatpack.sh` is re-run (then rebuild `:app`). The
  cert/package indicators are rebuild-proof; the clean-rebuild APK was verified
  byte-identical, so routine rebuilds don't break the pack.

---

# FINAL VERIFICATION (end of Phase 6)

| Requirement | Result |
|---|---|
| Clean-build all three modules | ✅ `clean :app :goodcaller :villaincaller assembleDebug` — SUCCESS |
| Full unit suite | ✅ 60/60 |
| Full instrumented suite | ✅ 36/36 phone (1 skip: DO-only) + 36/36 emulator (4 skips: non-DO-only) |
| Launch audit | ✅ verified on both targets (logcat + UI) |
| Manual audit | ✅ same engine, verified on both targets |
| GoodCaller | ✅ profile CLEAN + threat-intel NO MATCH → CLEAN / WATCHING everywhere |
| VillainCaller, Advice Mode | ✅ 7 findings (both pillars) → THREAT DETECTED → ADVISED, zero privileged action (OS-asserted) |
| VillainCaller, Full Power | ✅ THREAT DETECTED → ACT → verified containment (suspend + 2 permission denials, `POLICY_FIXED`) → ACTED |
| Containment verification | ✅ every ACTED claim backed by a re-queried OS state, asserted in tests and shown in UI/audit rows |
| Restoration | ✅ verified unsuspend + permission DEFAULT reset, repeatable (exercised 3×) |
| Audit-chain integrity | ✅ `AuditLog.verify` asserted on-device after all new row types (THREAT/DECISION/ADVISED/ACTION/MODE/SCAN) |
| Rulepack signature | ✅ verified in unit + on-device production path |
| ThreatPack signature | ✅ verified in unit + on-device; corruption/wrong-key/tamper fail closed |
| APK asset inspection | ✅ exactly 6 assets (rulepack.json/.sig/public.key + threatpack.json/.sig/public.key); zero `PRIVATE KEY` matches; no .pem/.jks/keystore packaged |
| Private keys out of source control | ✅ `git ls-files keys/` = 0; both private keys gitignored |
| Destructive-API grep | ✅ 0 code hits (`wipeData\|resetPassword\|reboot(\|setApplicationHidden\|addUserRestriction\|setUninstallBlocked\|lockNow\|FORCE_STOP`); all DPM mutations in `DeviceOwnerEnforcer.kt` only |
| Scan performance measured | ✅ 52 ms total on S20 FE (breakdown above) |
| Git working tree | ✅ inventoried below; **nothing committed** — left for human review |

### Changed-file inventory (vs HEAD `bad1633` + the pre-existing Phase 1–3 untracked set)

**Modified this run (Phases 4–6):** `.gitattributes`, `.gitignore`,
`app/src/main/java/com/thraksha/guardian/ThrakshaApplication.kt`,
`security/events/SecurityEvent.kt`, `security/engine/{Finding,RuleEngine,SecurityAuditEngine}.kt`,
`ui/screens/DashboardScreen.kt`, `villaincaller/build.gradle.kts`,
`app/src/androidTest/.../{AuditTrailPersistenceInstrumentedTest,FoundationInstrumentedTest}.kt`.

**Created this run:** `security/policy/` (5 files), `security/enforcement/` (2 files),
`security/threatintel/` (5 files), `app/src/main/assets/threatpack.{json,sig}` +
`threatpack_public.key`, `villaincaller/villain-demo.keystore`,
`tools/make_threatpack.sh`, `temp/DEVICE_OWNER_DEMO_SETUP.md`, this report,
`temp/screenshots/phase4_*|phase5_*|phase6_*`, unit tests
`{PolicyEngine,AdvisoryEnforcer,EnforcementTargets,ThreatPack}Test.kt`, instrumented
tests `{PolicyPipeline,DeviceOwnerEnforcement,ThreatIntel}InstrumentedTest.kt`,
local-only `keys/threatpack_private.pem` (gitignored).

**Test environment note:** the `thraksha_do` AVD (AOSP android-33 x86_64) remains
provisioned with Guardian as Device Owner for repeat demos; it is disposable
(`dpm remove-active-admin …` or delete the AVD). The Samsung phone remains in
DEVICE_ADMIN / Advice Mode — its tripped Knox warranty fuse permanently blocks Device
Owner there (see `temp/DEVICE_OWNER_DEMO_SETUP.md` §1a).
