# THRAKSHA DEMO — PHASE 8 IMPLEMENTATION PROGRESS

## REAL-WORLD DEVICE SCANNER + REAL THREAT INTELLIGENCE

Implementation run against `temp/THRAKSHA_DEMO_IMPLEMENTATION_PHASE_8.md` (authoritative),
on the Phase 1–7 known-good baseline.

**Devices:** Samsung `SM-G781B` (S20 FE), Android 13, `DEVICE_ADMIN` (Advice Mode);
AOSP `thraksha_do` AVD, Android 13, `DEVICE_OWNER` (Full Power).

---

## BASELINE (before any Phase 8 change)

| Check | Result |
|---|---|
| `:app:testDebugUnitTest` | 92 tests, 0 failures |
| `connectedDebugAndroidTest` — SM-G781B | 43 tests, 0 failures, 1 skipped |
| `connectedDebugAndroidTest` — thraksha_do AVD | 43 tests, 0 failures, 4 skipped |

Matches the Phase 7 exit state exactly.

---

## WHAT PHASE 8 ADDS

A generalized **SCAN THIS DEVICE** capability: Thraksha enumerates every visible
installed application (not just the two controlled decoys), collects real
Android-observable metadata, fingerprints signing certificates and base APKs,
runs deterministic generic risk rules, applies contextual baselines only where a
deterministic baseline exists, matches against a signed ThreatPack now carrying a
genuinely sourced real-world corpus (abuse.ch), and classifies each app with the honest
vocabulary NO KNOWN FINDINGS / REVIEW / HIGH-RISK PROFILE / KNOWN THREAT MATCH /
PARTIAL. There is exactly one scanner code path; VillainCaller is detected by it for
the same reason any real threat would be.

### Package visibility (first blocker — see temp/PHASE8_PACKAGE_VISIBILITY.md)

`QUERY_ALL_PACKAGES` is declared in a **debug-only manifest overlay**
(`app/src/debug/AndroidManifest.xml`). The main manifest keeps the targeted
`<queries>` only; the merged **release** manifest verifiably carries no broad
visibility. Runtime-proved on the S20 FE: 441 packages discovered app-side (26 user /
415 system), equal to the adb shell ground truth; `visibilityScope()` reports
FULL/REDUCED honestly and the UI warns on REDUCED. No Play-eligibility claim is made.

### Files created

| File | Purpose |
|---|---|
| `app/src/debug/AndroidManifest.xml` | Debug-build-only `QUERY_ALL_PACKAGES` (documented, isolated) |
| `security/scan/DeviceScanModels.kt` | `ScanClassification` (honest result vocabulary), `IntelligenceState` (ACTIVE/STALE/UNAVAILABLE), `ScanProgress` (IDLE/DISCOVERING/ANALYZING/FINISHED), `ScanOutcome` (COMPLETE/PARTIAL/ERROR), `AppScanRecord`, `DeviceScanResult`, `DeviceScanTimings` |
| `security/scan/GenericRiskRules.kt` | 9 deterministic, evidence-based generic rules (accessibility / device-admin / notification-listener / VPN declarations, broad visibility, overlay, sensitive-permission breadth ≥5 areas, unprotected exported surface ≥4, HIGH capability-combination). Pure; max severity HIGH; never a malware verdict; applied to user apps only (documented §19 rationale) |
| `security/scan/ScanClassifier.kt` | Pure classification ladder — only STRONG-tier intelligence can claim KNOWN THREAT MATCH; package-name matches cap at REVIEW; missing evidence → PARTIAL, never clean |
| `security/scan/DeviceScanEngine.kt` | `scanDevice()` orchestration: enumerate → observe → fingerprint once per app → generic rules → contextual baselines (typed apps only) → indexed ThreatPack matching → classify → policy for threat-tier apps only → events → audit. StateFlow progress, cooperative cancel, per-app failure isolation, Dispatchers.IO |
| `tools/build_threatpack.py` | Developer-side intelligence pipeline: parse → normalize → validate → dedup → expiry → provenance → merge demo indicators → generate → sign → verify → stats. No feed code in the app |
| `app/src/test/.../ScanClassifierTest.kt` | 14 tests: the ladder, weak-match honesty, PARTIAL rules, name-independence |
| `app/src/test/.../GenericRiskRulesTest.kt` | 17 tests: positive/negative/boundary/missing-evidence per rule; no-CRITICAL cap; identical-evidence ⇒ identical-findings (§26) |
| `app/src/test/.../ThreatIntelPhase8Test.kt` | 16 tests: expiry (indicator, pack, network), tier defaults/overrides/carriage, REAL_* provenance enforcement, malformed expiry/tier rejection, pre-Phase-8 schema back-compat |
| `app/src/androidTest/.../DeviceScanInstrumentedTest.kt` | 7 on-device tests: full-visibility discovery, arbitrary-package fingerprinting, honest classifications for unknown apps, Guardian-scans-itself (no special-casing), controlled samples through the same pipeline, repeat-scan determinism, timing measurement. Runs under OBSERVE so tests never mutate device state |
| `temp/PHASE8_PACKAGE_VISIBILITY.md` | Visibility mechanism, decision, Play implications, runtime proof |
| `temp/THREAT_INTELLIGENCE_PROVENANCE.md` | Sources, acquisition, licensing caveats, transformations, counts, expiry rules |
| `temp/PHASE8_THREAT_CORPUS_REPORT.md` | Corpus quality report (inputs/accepted/rejected/duplicates/size/verification/limitations) |
| `temp/threat_corpus/` | Archived curated inputs (`threatfox_android.json`, `mb_apk.csv`, `cscb.csv`) for reproducibility |

### Files modified

| File | Change |
|---|---|
| `security/inventory/ObservedApp.kt` | +`versionCode`, `isEnabled`, `installerPackage`, `ExportedComponents` (exported/unprotected counts) — all defaulted, Phases 1–7 call sites unchanged |
| `security/inventory/AppInventory.kt` | +`inventoryDevice()` (two-step enumeration, per-package failure isolation), `visibilityScope()`, exported-component inventory, installer/source, updated-system-app detection. `observe()`/`inventory()` behaviour for the decoys unchanged |
| `security/threatintel/ThreatPackModels.kt` | +optional `sourceReference`, `family`, `firstSeen`, `lastSeen`, `expiresAt`, `tier`; `IndicatorTier` (STRONG/CORROBORATING with by-type defaults); pack `isStaleAt`; still schemaVersion 1 (additive) |
| `security/threatintel/ThreatPackParser.kt` | Fail-closed Phase 8 rules: `REAL_*` without `sourceReference` rejected; malformed `expiresAt`/`tier` rejected (indicator and pack level) |
| `security/threatintel/ThreatIntelligenceScanner.kt` | Expiry-aware (fixed scan-time clock), **indexed** by (type, value) — per-app cost O(identifiers), required for the 4k-indicator corpus × 441 apps; findings carry the indicator tier; expired/active counts exposed |
| `security/network/NetworkThreatEvaluator.kt` | Skips expired DESTINATION_IP indicators (§16 — network IOCs must not fire past expiry) |
| `security/engine/Finding.kt` | +`FindingSource.GENERIC_RULE`, +`intelligenceTier` |
| `security/events/SecurityEvent.kt` | +`DeviceScanStarted` (audit row for scan start) |
| `ThrakshaApplication.kt` | Collector maps `DeviceScanStarted` → SCAN row |
| `ui/screens/DashboardScreen.kt` | New REAL-DEVICE SCANNER card: SCAN THIS DEVICE, live progress ("Analyzing 324 of 441 applications" + current package + bar + cancel), runtime-derived summary (apps user/system, identities checked, APK fingerprints, known-threat/high-risk/review/finding counts, duration, pack version + active/expired indicators, STALE/UNAVAILABLE states, REDUCED-visibility warning), per-app cards with classification badge, abbreviated cert/APK SHA-256, expandable evidence, policy/response for threat-tier apps; collapsible NO-KNOWN-FINDINGS and system lists. GENERIC_RULE label added to the Phase 4–6 audit card renderer |
| `app/src/test/.../ThreatPackTest.kt` | Classification assertion evolved: demo classifications ∪ `REAL_*`-with-provenance (original no-fabricated-family intent preserved); +real-corpus presence/provenance/expiry/tier test |
| `assets/threatpack.json/.sig/.key` | Regenerated: **packVersion 3** with the real corpus, re-signed with the existing key |

### Threat intelligence corpus (see the provenance + corpus reports)

* **Sources (metadata only, zero binaries):** abuse.ch ThreatFox full export (Android
  `apk.*` families), MalwareBazaar full CSV (`file_type=apk`), MalwareBazaar CSCB.
* **Shipped:** 3,000 real APK SHA-256 (STRONG), 408 vetted signing-cert SHA-256
  (STRONG; predominantly Windows Authenticode — caveat documented per record), 670
  fresh C2/distribution IPv4s (CORROBORATING, 90-day expiry, evaluated by the live
  Network Guard pillar), 8 controlled demo indicators. **4,086 total, 2.18 MB,
  pack expiry 2027-02-10 → THREAT INTELLIGENCE STALE state after that.**
* **Not shipped (honesty):** domains/URLs/md5/sha1 (no evaluator in this build —
  archived instead), package names (no legitimate source exists; none fabricated).
* Every `REAL_*` indicator carries source, per-record sourceReference URL, family as
  labelled by the source, timestamps, expiry, tier — and the parser refuses the pack
  if any real indicator lacks provenance.
* **Licensing:** private non-commercial demo use of a curated abuse.ch subset;
  commercial redistribution requires written Spamhaus/abuse.ch confirmation
  (documented gating task).

### Trust model (deterministic, guide §17)

STRONG (exact APK digest / vetted signer digest) → eligible for KNOWN THREAT MATCH.
CORROBORATING (package name, network destination) → REVIEW at most, never a threat
verdict alone. HIGH+ static/contextual findings → HIGH-RISK PROFILE. Any findings →
REVIEW. No findings + complete evidence + ACTIVE intelligence → NO KNOWN FINDINGS.
Anything less → PARTIAL. Enforced by `ScanClassifier` and unit-proved.

### Policy conservatism for real apps (guide §27)

Threat-tier apps (KNOWN THREAT / HIGH-RISK) go through the shared
`SecurityResponseCoordinator` → PolicyEngine; REVIEW never reaches the policy layer.
ACT decisions route to `DeviceOwnerEnforcer` **only** for packages in the
`EnforcementTargets` demo allowlist (VillainCaller), and the enforcer independently
refuses out-of-allowlist targets — two structural layers guaranteeing no real app can
be automatically contained. Real-world findings: detect → explain → advise → audit.

---

## BUILD & TEST RESULTS

| Check | Result |
|---|---|
| `:app:compileDebugKotlin` / `assembleDebug` | **BUILD SUCCESSFUL** |
| `:app:testDebugUnitTest` | **139 tests, 0 failures** (92 baseline + 47 new) |
| `connectedDebugAndroidTest` — SM-G781B | **50 tests, 0 failures, 1 skipped** (43 baseline + 7 new) |
| `connectedDebugAndroidTest` — thraksha_do AVD | **50 tests, 0 failures, 4 skipped** |
| Release-manifest check | no `QUERY_ALL_PACKAGES` uses-permission in release |

`DeviceScanInstrumentedTest` also ran green standalone on the S20 FE before the full
suites (7/7).

## DEVICE RESULTS — S20 FE (the Phase 8 acceptance run)

Dashboard **SCAN THIS DEVICE** (screenshots below):

* **441 applications discovered and analyzed** (26 user / 415 system) — identical to
  the `adb shell pm list packages` ground truth; all discovered at runtime, none known
  at build time.
* **441 signing identities checked, APK fingerprints computed** for every readable
  base APK; real per-app cert + APK SHA-256 shown in the UI (e.g. Galaxy Wearable
  `34df0e7a9f…a65f900a42` / `0832d31842…30e71c3646`).
* **Threat intelligence: pack v3, 3,412 active indicators** (SUPPORTED static types;
  670 network indicators live in the Network Guard pillar), 0 expired at scan time.
* **1 known-threat match — VillainCaller** (the controlled positive, installed on the
  phone), matched by STRONG cert + base-APK digests and corroborated by the
  package-name record, through the same pipeline as the other 440 apps.
  POLICY: ADVISE (not Device Owner) → RESPONSE: ADVISED. Nothing auto-contained.
* **4 high-risk profiles, 15 apps for review, 56 findings** — all real user apps
  (e.g. Galaxy Wearable: QUERY_ALL_PACKAGES + overlay + 6 sensitive areas → HIGH
  combination rule; Samsung Wallet, Samsung Internet, Samsung Members, Drive:
  evidence-cited REVIEW findings). System packages are inventoried, fingerprinted and
  intelligence-matched but not profiled by user-app generic rules (documented).
* **6 user apps honestly at NO KNOWN FINDINGS** (including GoodCaller). A zero-threat
  scan of a clean phone remains a valid outcome — the only KNOWN THREAT on this phone
  is the controlled sample, and removing it yields an honest 0.
* **Guardian scanned itself**: its own debug capability profile (accessibility,
  notification listener, VPN, device admin, broad visibility) produced generic REVIEW
  findings — instrumented-test-asserted proof of no special-casing.
* Repeat scan: deterministic (asserted on device).

### Performance (S20 FE, 441 apps)

| Stage | Duration |
|---|---|
| Inventory (PackageManager metadata, 441 pkgs) | 1.3 s |
| Certificate fingerprints (441) | 0.5 s |
| Base-APK SHA-256 hashing (441 real APKs) | 17.0 s |
| Generic + contextual rules | < 0.1 s |
| Threat-intel matching (441 × 3,412 indicators, indexed) | < 0.1 s |
| **Total (UI run)** | **21.2 s** |

Instrumented runs measured 13.7–15.0 s for the same 442-package scan (no UI). The UI
stayed responsive throughout (live progress + working Cancel; all work on
Dispatchers.IO). 2.18 MB signed JSON parse is included in these totals — no SQLite
migration needed at this scale (guide §21).

## CONTROLLED POSITIVE (same scanner)

VillainCaller on the S20 FE: KNOWN THREAT MATCH via `demo-villain-signer`
(SIGNING_CERT_SHA256, tier STRONG) + `demo-villain-base-apk` (BASE_APK_SHA256, tier
STRONG, `50027f45…` — recomputed from the exact APK reinstalled on both devices) +
`demo-villain-package` (PACKAGE_NAME, tier CORROBORATING), plus the Phase 4–6
contextual CRITICAL profile findings — 9 findings total, POLICY ADVISE → ADVISED in
Advice Mode. The emulator (Device Owner) regression suite exercises the Full Power
path over the same engine (50/50 green, including Device Owner enforcement and
Network Guard tests).

## AUDIT

Scan start and completion land as SCAN rows; VillainCaller's findings as THREAT rows
with DECISION + ADVISED rows (screenshots). The audit chain verifies on device
(`AuditTrailPersistenceInstrumentedTest`, `AuditLogInstrumentedTest` in the green
suite). Review findings are summarized in the completion SCAN row rather than
flooding the chain (§28); no APK contents or certificate blobs are persisted.

## SCREENSHOTS (`temp/screenshots/`)

`phase8_scan_button.png` (SCAN THIS DEVICE), `phase8_scan_progress.png` +
`phase8_scan_progress2.png` (live "Analyzing 83/324 of 441" with real package names),
`phase8_scan_summary.png` + `phase8_summary_counts.png` (runtime-derived summary),
`phase8_first_results.png` (real Samsung/Google apps with real fingerprints),
`phase8_villain_card.png` (KNOWN THREAT MATCH), `phase8_villain_evidence1/2.png`
(contextual + intelligence evidence, tiers, pack v3), `phase8_villain_intel_policy.png`
(POLICY ADVISE → ADVISED), `phase8_review_evidence.png` (Galaxy Wearable HIGH-RISK
evidence), `phase8_clean_list.png` (NO KNOWN FINDINGS user apps),
`phase8_audit_feed.png` (THREAT/SCAN rows).

## SAFETY VERIFICATION

* **No hardcoded verdicts** (§26): repo grep — zero references to the decoy packages
  in `security/scan/`, `security/threatintel/`, `security/engine/`;
  `DemoAppRegistry` remains only in the permitted places (type registry, enforcement
  allowlist, VPN scope, demo audit engine, network evaluator display-name lookup).
  Unit tests additionally prove identical evidence ⇒ identical findings/classification
  regardless of package name, and clean-profile-with-villain-name ⇒ clean.
* **APK inspection:** exactly 6 assets (rulepack + threatpack triples); zero
  `PRIVATE KEY` markers anywhere in the APK; `git ls-files keys/` = 0; keys/ and
  `*.pem` gitignored. No malware binaries anywhere (metadata CSV/JSON only — verified
  by acquisition method; sample endpoints never touched).
* **Privacy:** scanner is fully local; no upload path exists in the app; audit rows
  carry summaries/fingerprints, never APK contents or full inventory dumps.
* **Real-app protection:** double allowlist gate (engine routing + enforcer refusal);
  REVIEW/HIGH-RISK on real apps is advisory only — verified live on the S20 FE (ADVISED).

## LIMITATIONS / DIVERGENCES

* Full Power remains verified on the AOSP emulator (Samsung Knox blocks Device Owner —
  documented since Phase 4–6). Advice Mode is verified on the real phone.
* CSCB certificates are predominantly Windows Authenticode (documented per record and
  in the provenance report); Android-signer positives are exercised via the controlled
  demo signer only.
* Domains/URLs are acquired+archived but not shipped (no evaluator would read them);
  package-name real indicators are absent (no legitimate source; none fabricated).
* Generic rules profile user apps only; system packages get inventory + fingerprints +
  intelligence matching (documented §19 trade-off, UI splits USER/SYSTEM).
* `ThreatPackTest`'s Phase 6 "demo classifications only" assertion was evolved (not
  weakened): classifications must now be demo-set ∪ `REAL_*`-with-provenance, and a
  new test enforces corpus presence/provenance/expiry/tier.
* The scan currently re-hashes APKs on each run (no cache); at 21 s for 441 apps this
  is acceptable for the demo and honest to measure (§21 — no premature optimization).
* Licensing: shipping abuse.ch data commercially requires written Spamhaus
  confirmation — gating task before any commercial build (provenance doc §6).

## GIT WORKING TREE

Nothing committed (per instructions). New/modified files as listed above; both signing
private keys remain untracked and outside the APK. Villaincaller reinstalled on both
devices from the exact APK whose digest the pack carries.

## NEXT STEP

Phase 9 candidates: scan-result persistence/history in the encrypted store, Bloom+SQLite
pack format for larger corpora, pack update/rollback flow (version monotonicity + kill
switch per the research), Spamhaus written-permission task before any commercial build.
