# THRAKSHA DEMO — PHASE 8.1 IMPLEMENTATION PROGRESS

## EVIDENCE PROGRESSION + RUNTIME OBSERVATION + USER ACT

Implementation run against `temp/THRAKSHA_DEMO_IMPLEMENTATION_PHASE_8_1.md`
(authoritative), on the Phase 1–8 known-good baseline.

**Devices:** Samsung `SM-G781B` (S20 FE), Android 13, `DEVICE_ADMIN` (Advice Mode);
AOSP `thraksha_do` AVD, Android 13, `DEVICE_OWNER` (Full Power).
**Date:** 2026-08-14.

---

## BASELINE (before any Phase 8.1 change)

| Check | Result |
|---|---|
| `:app:testDebugUnitTest` | 139 tests, 0 failures (Phase 8 exit state) |
| Device | S20 FE attached, 441 packages (26 user / 415 system), Guardian + GoodCaller + VillainCaller installed |
| Note | Thraksha's phone privilege read back DEVICE_ADMIN at run time (dashboard-verified); the emulator remains Device Owner |

## RESEARCH FIRST (guide §6/§7) — temp/PHASE8_1_RUNTIME_OBSERVABILITY.md

Written before any implementation. Key conclusions (each verified on-device or by
instrumented test):

* Per-app **usage** of Contacts / Location / Camera / Microphone / Media /
  Accessibility / Overlay / Notification-listener is **NOT OBSERVABLE** at any
  privilege Thraksha can legitimately hold (requires `GET_APP_OPS_STATS`,
  signature|privileged|development; Device Owner does not help). The UI says so
  verbatim instead of inventing evidence.
* Genuinely observable Stage 3 sources — exactly two, both attributed and handled by
  Thraksha itself (reliability VERIFIED): **Network Guard TUN interception** (VPN-scoped
  to VillainCaller, Phase 7) and **notification posting** seen by Thraksha's
  user-enabled notification listener (metadata only, content never recorded).
* Stage 2 (GRANTED/ENABLED) is fully queryable: runtime-permission grant flags,
  overlay app-op mode (+ grant-flag fallback for MODE_DEFAULT), notification-listener /
  accessibility enablement, active device admins. Unanswerable queries surface as
  UNKNOWN, never DENIED.
* Central calibration fact, recorded from adb ground truth: **Galaxy Wearable on this
  phone has every runtime permission `granted=false`** and zero recorded op usage —
  Phase 8's HIGH-RISK flag rested entirely on Stage 1.

## WHAT PHASE 8.1 ADDS

The model DECLARED → GRANTED → OBSERVED → CONTEXT → DECISION → USER ACT, end to end.

### Files created

| File | Purpose |
|---|---|
| `security/inventory/SpecialAccess.kt` | `GrantState` (GRANTED/DENIED vs ENABLED/DISABLED vs UNKNOWN/NOT_APPLICABLE — special access never forced into the runtime-permission model, §5) + `SpecialAccessSnapshot` (default all-UNKNOWN) |
| `security/evidence/EvidenceModels.kt` | `EvidenceStage`, `ObservationReliability` (only VERIFIED renders OBSERVED ✓, §8), `CapabilityObservation`, `CapabilityEvidence` (factory structurally forbids observed without a VERIFIED observation), `ContextAssessment` |
| `security/evidence/CapabilityCatalog.kt` | The reported capabilities with their exact permission/component sources and the honest NOT-OBSERVABLE limitation strings |
| `security/evidence/EvidenceAssembler.kt` | Pure assembly: Stage 1 from declarations, Stage 2 from grant flags/special-access state, Stage 3 only from the observation store; observation-only capabilities are never hidden |
| `security/evidence/ContextEvaluator.kt` | Deterministic, package-name-blind context: strong intel / observed-indicator-match / critical baseline mismatch → SUSPICIOUS; verified sensitive observation without baseline → UNUSUAL; typed-baseline match or clean system provenance → EXPECTED; otherwise UNKNOWN (no invented intent) |
| `security/evidence/AppDisplayStatus.kt` | WATCHING / REVIEW / ADVISED / KNOWN_THREAT_MATCH / PARTIAL + `StatusMapper` (§23 ladder: Stage-1-only → WATCHING; universal INTERNET grant alone never escalates; static HIGH exposure renders REVIEW · HIGH CAPABILITY EXPOSURE, never fake behavior) |
| `security/observe/RuntimeObservationStore.kt` | Bounded process-wide store; the ONLY Stage 3 feed; writers are the two genuine sources |
| `security/observe/RuntimeObservationProvider.kt` | The single Android-facing observation component (§7): overlay op query (API-level-guarded), listener/accessibility/admin enablement (one read per scan), per-app snapshot enrichment |
| `security/act/UserActModels.kt` | `UserActOption` (options constructed only when technically supported, §14), `SupportedUserActions` (pure builder: direct enforcement only under Device Owner + allowlist; BlockNetwork only while the guard is active and the package tunnel-scoped; manual path always; no Dismiss for known threats), `UserActStatus` (§19 vocabulary), `UserActOutcomeMapper` (pure verdict→status honesty mapping) |
| `security/act/UserActCoordinator.kt` | ACT → re-validate live supported set → `SecurityResponseCoordinator`/PolicyEngine (an action policy does not map to the findings is refused — user initiation never bypasses policy, §15) → executor (DeviceOwnerEnforcer / NetworkGuard user-block / settings intent) → verify real state → `UserActionPerformed` event → audit. Settings hand-off = USER_ACTION_REQUIRED, never ACTED (§16); cancel = CANCELLED with no action |
| `security/notify/SecurityNotifier.kt` | §20/§21 notifications: one review summary per *changed* review set, high-priority alert only for verified known-threat matches, calm capability-exposure wording, deep link with focus package; nothing for declared-only findings |
| `ui/state/UiFocus.kt` | Notification deep-link → dashboard focuses/expands that app's evidence card |
| `app/src/test/.../Phase81EvidenceTest.kt` | 29 unit proofs (see TESTS) |
| `app/src/androidTest/.../Phase81EvidenceInstrumentedTest.kt` | 11 on-device proofs (see TESTS) |
| `app/src/androidTest/.../Phase81FullPowerActInstrumentedTest.kt` | Device-Owner-only user-ACT positive: verified suspension + reversibility |
| `temp/PHASE8_1_RUNTIME_OBSERVABILITY.md`, `temp/PHASE8_1_WEARABLE_VALIDATION.md` | Required research/validation documents |

### Files modified (all additive; Phase 1–8 call sites source-compatible)

| File | Change |
|---|---|
| `security/inventory/ObservedApp.kt` | +`specialAccess: SpecialAccessSnapshot = UNKNOWN` |
| `security/scan/DeviceScanModels.kt` | `AppScanRecord` +`evidence`, `contextAssessment`, `displayStatus` (defaulted); `DeviceScanTimings` +`evidenceMs` (§36) |
| `security/scan/DeviceScanEngine.kt` | Per-scan `RuntimeObservationProvider`; special-access enrichment before rules; evidence/context/status assembly per app; §20 notifications after the scan summary |
| `security/scan/GenericRiskRules.kt` | §11 calibration: combination rule reaches HIGH only when ≥4 sensitive areas are **currently granted**; declared-only breadth = MEDIUM "broad declared capability exposure"; exposure language throughout; evidence lines now carry stage tags (`DECLARED only` / `DECLARED + GRANTED`) and live special-access state where determined. Still pure, package-name-blind |
| `security/policy/PolicyEngine.kt` | `RUNTIME_DENIABLE_PERMISSIONS` made public for the ACT option builder (no behavior change) |
| `security/network/NetworkGuardState.kt` | +user-block set (ACT §14): consulted by the engine; widens nothing (only tunnel-scoped packages can ever be affected) |
| `security/network/NetworkGuardEngine.kt` | Records every intercepted packet into the observation store (Stage 3, §25) after the NETWORK event; +`Verdict.UserBlock` (same structural no-forwarding-socket guarantee) + verified publish |
| `services/ThrakshaVpnService.kt` | Handles `UserBlock` verdict (drop path) |
| `services/ThrakshaNotificationListener.kt` | Posted notifications recorded as attributed Stage 3 observations — **metadata only** (package + timestamp; no title/text) |
| `security/events/SecurityEvent.kt` | +`UserActionPerformed` |
| `ThrakshaApplication.kt` | Collector maps `UserActionPerformed` → `USER` audit row |
| `MainActivity.kt` | Consumes the notification focus extra (onCreate + onNewIntent) |
| `ui/screens/DashboardScreen.kt` | Scan cards rebuilt per §13: honest status badge (WATCHING/REVIEW[· HIGH CAPABILITY EXPOSURE]/ADVISED/KNOWN THREAT MATCH/PARTIAL), EVIDENCE PROGRESSION section (per-capability DECLARED / GRANTED-or-ENABLED / OBSERVED ✓-with-timestamp-source or verbatim limitation), context line, independent threat-intelligence line (§24), recommendation, ACT sheet (supported options only, confirmation for disruptive actions, §18/§19 result rendering), session-dismiss filter, deep-link auto-expand |
| `app/src/test/.../GenericRiskRulesTest.kt` | Combination-rule test **evolved** per §11 (like Phase 8's ThreatPackTest evolution): split into declared-only→MEDIUM and granted→HIGH; all other 16 tests unchanged |
| `app/src/androidTest/.../NetworkGuardInstrumentedTest.kt` | Exhaustive-when: explicit `UserBlock` branch (errors if ever hit in that test) |

## BUILD & TEST RESULTS (final, after `clean`)

| Check | Result |
|---|---|
| `clean :app:assembleDebug` | **BUILD SUCCESSFUL** |
| `:app:testDebugUnitTest` (fresh) | **169 tests, 0 failures** (139 baseline + 29 new + 1 evolved-split) |
| `connectedDebugAndroidTest` — SM-G781B | **61 tests, 0 failures, 2 skipped** (Phase 8 baseline + 12 new; skips: baseline DO-only + the Full-Power ACT test) |
| `connectedDebugAndroidTest` — thraksha_do AVD | **61 tests, 0 failures, 6 skipped** (skips: baseline DO-behavioral set + the Wearable-dependent probes absent on AOSP) |
| Release-manifest check | unchanged from Phase 8 (no `QUERY_ALL_PACKAGES` in release) |

### New unit proofs (`Phase81EvidenceTest`, 29 tests)

Evidence: declared-only / declared+granted / +verified-observation stage progression;
stages never imply one another; INDIRECT/RECENT_SYSTEM_SIGNAL/UNAVAILABLE never claim
OBSERVED; observations never leak across packages; NOT-OBSERVABLE limitation carried
verbatim; UNKNOWN special access stays UNKNOWN; observation-only capabilities reported;
store bounds/ordering. Context: strong-intel→SUSPICIOUS, observed-indicator→SUSPICIOUS,
baseline-match→EXPECTED, granted-power-no-baseline→UNKNOWN (the Wearable shape),
verified-sensitive-observation-no-baseline→UNUSUAL, clean-system→EXPECTED.
Classification: declared-only never behavioral; KNOWN THREAT stays strong with zero
Stage 3 (§24); static HIGH profile displays REVIEW not ADVISED; universal INTERNET
grant never escalates WATCHING; PARTIAL stays PARTIAL. ACT: manual-path-only at NORMAL;
no direct enforcement for non-allowlisted apps even as Device Owner; allowlisted target
gets Restrict/Suspend; BlockNetwork only when guard active+scoped; no Dismiss on known
threats; **failed enforcement never maps to ACTED**, NOT_AUTHORISED maps to
USER_ACTION_REQUIRED (§16), ADVISED maps to USER_ACTION_REQUIRED.

### New on-device proofs (Phase81 instrumented, 11 + 1 DO-only)

Special-access enablement determinable (not fabricated); overlay op query answerable
for another package; requested⊇granted with real declared-but-ungranted rows that are
never observed; NOT OBSERVABLE stays on real sensitive rows; a genuine store
observation reaches Stage 3 for the right package only; a full device scan invents no
Stage 3 with an empty store and carries status+context on every analyzed record;
VillainCaller remains KNOWN THREAT MATCH (independent of Stage 3); Wearable never
ADVISED/KNOWN-THREAT; ACT refuses unsupported actions (and the OS verifiably does not
suspend); Keep-watching lands a USER row in the encrypted chain; settings intent
resolves; dismiss is session-only and detection-unaffected. Full Power (emulator):
user-ACT suspension → **ACTED only after `isPackageSuspended` re-query**, then verified
restore.

## DEVICE RESULTS — S20 FE (Advice Mode acceptance run, UI-driven)

* **Scan:** 441 apps (26 user / 415 system), 441 identities, 441 APK hashes, pack v3
  with 3,412 active indicators, **20.1 s** (Phase 8: 21.2 s — the Phase 8.1 evidence
  stage adds no measurable overhead; grant-state reads are batched once per scan).
* **Calibration outcome (guide §40):** **1 KNOWN THREAT MATCH (VillainCaller), 0
  high-risk profiles** (Phase 8: 4), 19 classification-REVIEW findings-bearing apps of
  which the display ladder shows **13 REVIEW / 6 WATCHING**, 6 user apps WATCHING with
  no known findings, 56 findings. Galaxy Wearable now: **REVIEW** (context UNKNOWN,
  stated), evidence card §34-exact: Location `DECLARED ✓ / GRANTED — not currently
  granted / OBSERVED — NOT OBSERVABLE…`, Overlay `DECLARED ✓ / ENABLED ✓`,
  Package visibility `DECLARED ✓ / GRANTED ✓`. See temp/PHASE8_1_WEARABLE_VALIDATION.md
  (states B/C honestly impractical — no watch paired; documented, not simulated).
* **Observed network evidence (§25):** Network Guard ACTIVE (real consent) →
  VillainCaller "SEND DEMO OUTBOUND PACKET" → guard intercepted UDP → 203.0.113.113:443,
  signed indicator matched, ADVISED (Advice Mode). After rescan the villain card shows
  **`Network — OBSERVED ✓ 23:13:30 — outbound UDP 55 B → 203.0.113.113:443 (1×, Network
  Guard TUN interception)`** — genuine Stage 3 from the actually intercepted packet.
* **User ACT, Advice Mode:** Wearable ACT sheet = Open Android app settings / Keep
  watching / Dismiss (nothing privileged offered). Settings action genuinely opened the
  OS App-info page and recorded **USER ACTION REQUIRED** — never ACTED.
* **User ACT, Network Guard:** VillainCaller ACT sheet = Block network / Open settings /
  Keep watching (no Dismiss for a known threat; no Suspend without Device Owner).
  Block → confirmation → **ACTED (verified: block armed)** → the next real packet was
  intercepted and dropped on the no-forwarding-socket path: guard card shows
  **BLOCKED — "User ACT block: packet to 203.0.113.113:443 intercepted and dropped
  before forwarding — no forwarding socket was created"**. Unblocked and guard disabled
  afterwards.
* **Notifications (§20/§21):** exactly two: default-priority "Thraksha Security Review —
  13 apps have broad capabilities enabled. Tap to review the evidence." and
  high-priority "Known threat match — VillainCaller matches signed threat
  intelligence…". No per-declaration spam; wording is exposure-based. Deep link
  verified: launching with the focus extra auto-expands the VillainCaller evidence card.
* **Audit:** SCAN/THREAT/DECISION/ADVISED/NETWORK/ACTION/**USER** rows all present in
  the live feed; chain verified on device by the green audit instrumented tests.

## DEVICE RESULTS — thraksha_do emulator (Full Power)

* Scan: 159 apps, VillainCaller KNOWN THREAT MATCH. Under **AUTO_DEFEND** the launch
  audit auto-contained VillainCaller (Phase 8 behavior intact); restored via the
  dashboard Restore button (verified `suspended=false`).
* Under **GUIDED**: scan leaves villain un-suspended; ACT sheet offers
  `Restrict ACCESS_FINE_LOCATION / Restrict READ_MEDIA_IMAGES / Suspend app
  (quarantine) / Open Android app settings / Keep watching` (no Dismiss). Suspend →
  confirmation → **"ACTED — SUCCEEDED: Verified OS state: com.thraksha.demo.villaincaller
  suspended=true (quarantined by package suspension)"**; OS dumpsys confirms. Restored
  afterwards (verified false). The same flow is asserted by
  `Phase81FullPowerActInstrumentedTest` (green).

## SCREENSHOTS (`temp/screenshots/`, all captured live this run)

`phase8_1_scan_button/progress/summary.png`, `phase8_1_villain_card.png`,
`phase8_1_villain_evidence.png` (stages), `phase8_1_villain_observed_evidence.png`
(OBSERVED ✓ network), `phase8_1_wearable_card.png`, `phase8_1_wearable_evidence.png`,
`phase8_1_wearable_evidence2.png` (declared-not-granted rows),
`phase8_1_watching_cards.png`, `phase8_1_act_options.png` (Advice sheet),
`phase8_1_act_settings_opened.png`, `phase8_1_act_result_advice.png`
(USER ACTION REQUIRED), `phase8_1_villain_act_options.png` (Block network offered),
`phase8_1_act_confirm.png`, `phase8_1_act_block_acted.png` (verified ACTED),
`phase8_1_act_user_blocked.png` (real packet BLOCKED), `phase8_1_netguard_observed.png`,
`phase8_1_villain_send.png`, `phase8_1_guard_active.png`, `phase8_1_notification.png`,
`phase8_1_notification2.png` (known-threat alert), `phase8_1_notification_deeplink.png`
(auto-expanded card), `phase8_1_audit_feed.png`, `phase8_1_fullpower_act_options.png`,
`phase8_1_fullpower_confirm.png`, `phase8_1_fullpower_acted.png`.

## SAFETY VERIFICATION

* Destructive-API grep over `app/src/main`: only the DeviceOwnerEnforcer KDoc line
  *listing what is absent*; zero calls.
* No package-name special-casing in `scan/`, `threatintel/`, `engine/`, `evidence/`,
  `observe/`, `act/`, `notify/` (grep = 0 outside `DemoAppRegistry`'s permitted homes);
  §26 name-independence unit tests still green.
* Real-app protection unchanged and extended to ACT: the double allowlist gate
  (engine routing + enforcer refusal) still stands, `SupportedUserActions` never offers
  direct enforcement for non-allowlisted packages even under Device Owner
  (unit + instrumented proven), and automatic quarantine of arbitrary apps remains
  impossible. Advice/manual action is the real-app path (§17).
* `git ls-files` shows no `keys/`/`*.pem`; assets carry no `PRIVATE KEY` marker;
  rulepack + threatpack verify in the green suites (verifier tests + on-device loads).
* Privacy: notification observations record package+timestamp only — no notification
  content; network observations carry addressing metadata only — no payloads.

## LIMITATIONS / DIVERGENCES (documented per §41 rather than papered over)

* **Stage 3 for contacts/location/camera/mic/media/accessibility/overlay/listener
  usage: STOPPED, not simulated.** Android reserves that data for
  `GET_APP_OPS_STATS` holders; the UI shows NOT OBSERVABLE (research doc §2, §5).
* **Wearable states B/C not run** — no watch is paired with this phone (Bluetooth: no
  bonded devices; every runtime permission ungranted). Faking a paired state would
  manufacture evidence. Equivalent guarantees proven generally
  (temp/PHASE8_1_WEARABLE_VALIDATION.md §4).
* `GenericRiskRulesTest`'s combination-rule expectation was **evolved** (not weakened)
  per the §11 calibration mandate: declared-only → MEDIUM exposure, granted → HIGH;
  everything else in the Phase 8 suite is untouched and green.
* The notification deep-link expands the target card; it does not yet auto-scroll the
  list to it (Compose Column, demo scale). Verified working via the focus extra.
* `RuntimeObservationStore` is in-memory (bounded): Stage 3 badges reset on process
  death, while every observation remains durable in the encrypted audit chain
  (NETWORK rows). Deliberate §27 choice — no destructive DB migration for the demo.
* Phone privilege was DEVICE_ADMIN during this run (its active-admin state is
  user-toggleable in Settings); Advice-mode semantics are identical from NORMAL, and
  the policy layer reads the live level at decision time either way.

## GIT WORKING TREE

Nothing committed (per instructions). New/modified files as listed above; both signing
private keys remain untracked and outside the APK; `temp/` remains untracked.

## ACCEPTANCE CRITERIA (§40) — ALL MET

UI distinguishes DECLARED/GRANTED/OBSERVED ✓ · Stage 3 never inferred (structural +
tested) ✓ · unsupported observation labelled honestly ✓ · Phase 8 flags recalibrated
(4 high-risk → 0 on the same phone; Wearable → REVIEW) ✓ · static capabilities no
longer look like proven behavior ✓ · rules evidence-based, no allowlists ✓ · WATCHING
exists ✓ · user ACT exists, supported-actions-only ✓ · Advice Mode gives useful
manual/settings actions ✓ · Full Power verified enforcement for the controlled target
✓ · notifications informative, not alarmist ✓ · network events populate genuine
Stage 3 ✓ · known threat intelligence independent and strong ✓ · Phase 1–8 behavior
green on both devices ✓ · audit integrity valid on device ✓.
