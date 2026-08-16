# SCREEN → REAL STATE MAPPING — Phase 11B

Rule: **Stitch is the visual specification; existing Thraksha code is the functional
specification. Where they disagree, runtime truth wins.**

Every row below is `STITCH DESIGN → REAL THRAKSHA STATE → REAL DATA SOURCE → REAL ACTION`.
Anything Stitch shows that has no real counterpart is listed in §B and is **not built**.

---

## A. Mapped surfaces

### A1. App shell

| Stitch element | Real state | Data source | Real action |
| --- | --- | --- | --- |
| Bottom nav: Protect / Automate / Audit | current route | `ThrakshaNavHost` `rememberNavController` | navigate, single-top, state saved per destination |
| Top bar brand lockup | static identity | `BrandingComponents.TRLogo` + `Wordmark` (existing) | — |
| Top bar trailing icon | — | — | open Settings |

### A2. Protect — dashboard

| Stitch element | Real state | Data source | Real action |
| --- | --- | --- | --- |
| Hero ring + state label ("Protected"/"Enforced") | foundation readiness + privilege | `FoundationStatus.state` (`Ready`/`Failed`/starting), `SecurityCapability.currentLevel` via `rememberPrivilegeLevel()`, `DemoMode.from(level)` | — |
| Hero headline ("Your device is safe" ⛔) | **replaced** — see §C1 | derived from `DeviceScanEngine.lastResult` + `FoundationStatus` | — |
| "Last scan: 14 mins ago" | last scan wall-clock | `DeviceScanEngine.lastResult.completedAt` (absent → "No scan yet") | — |
| "Scan Device" CTA | scanner busy flag | `DeviceScanEngine.progress` is `Discovering`/`Analyzing` | `DeviceScanEngine.scanDevice(context)` |
| "1 item needs your attention" card | review/threat counts | `DeviceScanResult.reviewApps`, `.knownThreatApps`, `.highRiskApps`, minus `UserActCoordinator.dismissedPackages` | navigate to findings list |
| Module card — Threat Intelligence | pack state | `DeviceScanResult.intelligence` (`ACTIVE`/`STALE`/`UNAVAILABLE`, `packVersion`, `activeIndicators`, `expiredIndicators`, `detail`) | open Threat Intelligence detail |
| Module card — Network Guard | live guard state | `NetworkGuard.state`: `Disabled`/`ConsentRequired`/`Starting`/`Active(scopedPackage, packetsObserved/Forwarded/Blocked, lastOutcome)`/`Error(reason)` | open Network Guard detail; enable/disable via real `VpnService.prepare` consent |
| Module card — App Scanner | scan outcome + counts | `DeviceScanResult.outcome`, `.analyzedCount`, `.totalFindings`, `.visibilityScope` | open findings list |
| Module card — Automation | engine phase | `AutomationEngine.state.phase` (`IDLE`…`RESTORE_FAILED`), `activeRun.routineLabel` | navigate to Automate |

### A3. Protect — scan progress

| Stitch element | Real state | Data source | Real action |
| --- | --- | --- | --- |
| "Scanning device…" + spinner | `ScanProgress.Discovering` / `Analyzing` | `DeviceScanEngine.progress` | — |
| Sub-line | discovering vs "Analyzing n of N" + current package | `ScanProgress.Analyzing(analyzed, total, currentPackage)` | — |
| Determinate progress | `analyzed / total` — **real fraction, only in ANALYZING** | same | — |
| "Cancel Scan" | cancellable | — | `DeviceScanEngine.requestCancel()` |

Discovering has no denominator, so it renders an **indeterminate** indicator. No
percentage is ever synthesised (§C2).

### A4. Protect — scan results

| Stitch element | Real state | Data source | Real action |
| --- | --- | --- | --- |
| "Scan Complete" / partial / error banner | `ScanOutcome.COMPLETE` / `PARTIAL` / `ERROR` + `outcomeDetail` | `DeviceScanResult` | — |
| "254 apps checked. 1 item needs attention." | real counts | `analyzedCount` (`userAppCount`/`systemAppCount`), review+threat counts | — |
| Finding rows | per-app records | `DeviceScanResult.apps: List<AppScanRecord>` filtered by `classification`, `displayStatus`, dismissed set | open Finding Details |
| "UNVERIFIED MODULES" (partial) | honest partial reasons | `outcomeDetail`, `visibilityScope == REDUCED`, `intelligence.status`, `evidenceComplete == false` | — |
| Secondary stat tiles | intelligence + network | `intelligence`, `NetworkGuard.state` | — |

### A5. Protect — finding details & evidence

| Stitch element | Real state | Data source | Real action |
| --- | --- | --- | --- |
| App header (name, package) | scanned app identity | `AppScanRecord.displayName`, `.packageName`, `.isSystemApp`, `.isEnabled` | — |
| Consumer summary line | derived from status | `AppScanRecord.displayStatus` + `StatusMapper.recommendation(status)` | — |
| Status badge | honest display status | `AppDisplayStatus` (`KNOWN_THREAT_MATCH`/`ADVISED`/`REVIEW`/`WATCHING`/`PARTIAL`) | — |
| Context line | deterministic assessment | `AppScanRecord.contextAssessment` (`EXPECTED`/`UNUSUAL`/`SUSPICIOUS`/`UNKNOWN` + rationale) | — |
| Evidence rail DECLARED → GRANTED → OBSERVED | per-capability evidence | `CapabilityEvidence`: `capabilityLabel`, `declared`, `grantState` (`GRANTED`/`ENABLED`/`DENIED`/`DISABLED`/`UNKNOWN`/`NOT_APPLICABLE`), `observed`, `latestObservation`, `observationCount`, `observationLimitation` | expand |
| "Technical Evidence" disclosure | raw evidence | `findings` (`source`, `severity`, `ruleId`, `reason`, `evidence[]`), `certSha256`, `baseApkSha256`, `decision`, `enforcement` | expand |
| Action bar ("Review App Permissions") | supported responses only | `UserActCoordinator.supportedOptions(context, record)` | `UserActCoordinator.perform(...)`; disruptive options require explicit confirm; cancel → `recordCancelled` |
| Result line | verified ACT result | `UserActResult.status` (`ACTED`/`PARTIALLY_ACTED`/`USER_ACTION_REQUIRED`/`KEPT_WATCHING`/`DISMISSED`/`CANCELLED`/`FAILED`/`UNSUPPORTED`) + `detail` | — |

`ACTED` is rendered **only** when `UserActResult.status == ACTED`.

### A6. Protect — Threat Intelligence & Network Guard details

| Stitch element | Real state | Data source | Real action |
| --- | --- | --- | --- |
| Threat Intelligence status | pack verification | `IntelligenceState.status`, `packVersion`, `activeIndicators`, `expiredIndicators`, `detail` | — |
| Rulepack line | signed profile rules | `AuditResult.Completed.rulepackVersion`, `.rulesEvaluated` | `SecurityAuditEngine.runAudit(context)` |
| Network Guard status / counters | live VPN state | `NetworkGuard.State.Active(scopedPackage, packetsObserved, packetsForwarded, packetsBlocked, lastOutcome)` | `ThrakshaVpnService.prepareIntent` → consent → `start` / `stop` |
| Last outbound attempt | verified outcome | `lastOutcome.observation.protocolName/endpoint()`, `.indicatorId`, `.outcome` (`BLOCKED`/advised), `.detail` | — |

### A7. Automate — Ask Thraksha

| Stitch element | Real state | Data source | Real action |
| --- | --- | --- | --- |
| Model status chip | model lifecycle | `LocalModelRepository.state` → `ModelPhase` `MISSING`/`VERIFYING`/`READY`/`LOADING`/`LOADED`/`ERROR`/`UNSUPPORTED`, `ModelState.label`, `.detail` | `LocalModelRepository.verify(context)` |
| Input field | enabled only when usable | `ModelState.isUsable` (`LOADED`) — otherwise input hidden with honest reason | — |
| "Understanding…" | in-flight inference (~11.5 s) | UI state driven by `OnDeviceIntentModel.load` + `AiIntentInterpreter.interpret` | `OnDeviceIntentModel.cancel()` |
| "Thraksha understood: …" | **validated** intent | `AiIntentInterpreter.Result.PlanReady.understood` (app-authored from validated `AutomationIntent`, never model prose) | — |
| Clarify bubble | CLARIFY | `Result.Clarify.question` | dismiss / re-ask |
| Unsupported state | UNSUPPORTED | `Result.Unsupported.message` | dismiss |
| Rejected state | REJECTED | `Result.Rejected` → app-authored "could not interpret" | dismiss |
| Model-unavailable state | ModelUnavailable | `Result.ModelUnavailable.message` | fall back to routine buttons |
| Quick routine chips | fixed real routines | `RoutineType.MEETING`, `FOCUS`, `DRIVING` (+ `CUSTOM` builder) | `AutomationEngine.preview(context, intent)` |

### A8. Automate — plan preview → START

| Stitch element | Real state | Data source | Real action |
| --- | --- | --- | --- |
| Plan title / duration | planner output | `AutomationPlan.routineLabel`, `.durationMinutes` (null → "until stopped") | — |
| Action list ("3 Actions") | **deterministic planner actions only** | `AutomationPlan.actions[].label`, `.support` (`SUPPORTED`/`USER_ACTION_REQUIRED`/`UNSUPPORTED`), `.required` | — |
| Blocked reason | non-executable plan | `AutomationPlan.executable`, `.blockedReason` | — |
| **START** | explicit human confirmation | — | `AutomationEngine.start(context, intent)` — the only start path, preserved |

### A9. Automate — active routine & restoration

| Stitch element | Real state | Data source | Real action |
| --- | --- | --- | --- |
| "Meeting Mode Active" | engine phase | `AutomationEngine.state.phase == ACTIVE` (or `PARTIAL`), `activeRun.routineLabel` | — |
| "RUNNING – 45M REMAINING" | real expiry | `activeRun.expiresAt` (absent → "until stopped") | — |
| Applied action cards + checkmarks | verified per-action results | `actionResults[].status`: `ACTED` (verified) / `OPENED` / `USER_ACTION_REQUIRED` / `FAILED` / `UNSUPPORTED` | — |
| STOP & RESTORE | — | — | `AutomationEngine.stop(context, "stopped by the user")` |
| "Restoring…" | RESTORING | `RunPhase.RESTORING` | — |
| "Restoration Complete" | RESTORED | `RunPhase.RESTORED` + `restoreResults[].status` (`RESTORED`/`ROLLED_BACK`) | `AutomationEngine.acknowledgeResult()` |
| Partial / failed restore | RESTORE_FAILED / FAILED | same, with per-capability `detail` | acknowledge |

`OPENED` is never rendered as "verified" (guide §20).

### A10. Audit

| Stitch element | Real state | Data source | Real action |
| --- | --- | --- | --- |
| Timeline rows | hash-chained audit entries | `auditDao().observeRecent(n)` → `AuditEntity(id, timestamp, type, details, tier, prevHash, hash)` | open event detail |
| Category chips (Security / Automation / AI / User action) | entry `type` | `SCAN`, `THREAT`, `DECISION`, `ADVISED`, `NETWORK`, `ACTION`, `AUTOMATION`, `USER`, … mapped to four consumer categories | filter |
| Date separators / time | `timestamp` | same | — |
| Event detail | one entry | `AuditEntity` fields | — |
| "Technical details" disclosure | chain integrity | `id`, `tier`, `prevHash`, `hash` | — |
| "Load older events" | window size | increase `observeRecent` limit | — |

### A11. Settings & support

| Stitch element | Real state | Data source | Real action |
| --- | --- | --- | --- |
| Appearance | theme preference | app-level `ThemePreference` (System/Light/Dark), persisted via `ConfigStore` | set |
| Protection → execution mode | operator policy dial | `ConfigStore.getExecutionMode()` / `setExecutionMode` + `SecurityEvent.ModeChanged` | set (audited) |
| Protection → privilege/enforcement | Android privilege | `SecurityCapability.currentLevel`, `DemoMode` | — |
| Permissions & Access | real special accesses | DND policy access + Modify system settings via `AutomationEngine.probe(context).support(capability)`; notification permission; notification listener `ThrakshaNotificationListener.isRunning()`; device admin/owner `SecurityCapability.currentLevel` | Android settings deep links (`ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS`, `ACTION_MANAGE_WRITE_SETTINGS`, `ACTION_NOTIFICATION_LISTENER_SETTINGS`, `ACTION_SECURITY_SETTINGS`) |
| On-device AI | model lifecycle | `LocalModelRepository.state` (`label`, `detail`, `verifyMillis`, `loadMillis`) | verify |
| Privacy & Data | implementation facts | SQLCipher store, Keystore passphrase, hash-chained audit, offline inference | — |
| About | build identity | `BuildConfig.VERSION_NAME` / `VERSION_CODE`, `applicationId` | — |

---

## B. Stitch concepts REJECTED (no real counterpart)

Nothing below was implemented. Each is listed with why.

| # | Stitch concept | Screen(s) | Reason |
| --- | --- | --- | --- |
| B1 | **Accessibility Service as a required permission** for security automation | 26, 27 | Automation uses DND policy access + Modify system settings. Guide §25 explicitly forbids inventing this. Both Permission Guidance screens dropped entirely. |
| B2 | "Pause background sync" automation action | 16, 17, 21 | No background-sync executor exists. Executors are DND, ringer, brightness, screen timeout, app launch. |
| B3 | "Set status to Busy across integrated platforms / enterprise presence" | 17, 18 | No presence integration exists. |
| B4 | "Blocks social media" / app blocking in Focus Mode | 13, 14 | Focus routine does not block apps. |
| B5 | "Secures mic/cam" in Meeting Mode | 14 | No camera/microphone control executor. |
| B6 | "Auto-replies" / "forces navigation pin" in Driving Mode | 13, 14 | No messaging or navigation control. |
| B7 | Suggested routines: Wind Down, Workout, Morning Brief | 13 | Routines are exactly Meeting, Focus, Driving (+ Custom). Guide §7. |
| B8 | Routine on/off toggles and "Edit" plan | 13 | Routines start only through preview → explicit START. A toggle would bypass that boundary. |
| B9 | "Meeting mode triggered via Calendar integration" | 18 | No calendar trigger; routines are user-started. |
| B10 | "Silenced 3 low-priority push notifications" counter | 18 | Not measured. |
| B11 | Automated backup sequence / cloud sync / "100%" progress | 20 | No backup automation. |
| B12 | "Unusual login pattern blocked", IP blacklisting for 24 h | 20 | No login monitoring; Network Guard is a scoped per-app outbound monitor, and blocking is per packet. |
| B13 | "Network Guard Alert — connection blocked" with bare IP severity chips as the default story | 21 | Only rendered when a real `lastOutcome` exists with its real verdict. |
| B14 | File-count metrics ("142,854 files scanned", "Files: 142k"), CPU-usage average, engine version `v4.2.1-stable`, "Policy Group: Standard Baseline" | 22 | The scanner analyses installed packages, not files; none of these are measured. |
| B15 | Synthetic scan console log + "Export Log" | 22 | The audit chain is the log; no console text exists and export is not implemented. |
| B16 | "Zero-Knowledge Architecture", "secure enclave", "NPU Active", "real-time threat detection", model name `TG-Guard-v4.2-quantized` | 28 | Inference is LiteRT-LM on the pinned Gemma model; no enclave, no NPU claim, and the model is not a threat detector. |
| B17 | "Latency < 5 ms", "Cloud Dependency: Zero" tiles, `/opt/thraksha/data`, "logged locally in plaintext" | 29 | Inference is ~11.5 s, storage is SQLCipher-encrypted app-private, and the path is not a real one. |
| B18 | "Model Updates downloaded via one-way sync" | 29 | Offline build; no update channel. |
| B19 | "Granular Control — specify directories, processes, network interfaces" | 29 | No such controls. |
| B20 | Acknowledgements: Suricata, OSSEC, Zeek, YARA, CoreOS, OpenSSL | 30 | None are used. |
| B21 | Privacy Policy / Terms of Service links | 30 | No hosted documents. |
| B22 | "Background Monitoring — granted / Revoke" permission | 25 | Not an Android permission this app holds or can revoke. |
| B23 | "System Automation — required to execute defensive responses automatically" | 25 | Automation never triggers from security findings. |
| B24 | Sign Out / user profile / "Administrator" account | 23 | No accounts; the app is fully local. |
| B25 | "Log In" / "Learn More" on Welcome | 31, 32 | No auth, no destination. |
| B26 | Stitch-generated TR monogram artwork and "abstract shield/eye" logo image | 30, 31, 32, 20, 13, 33 | Existing in-repo `BrandingComponents.kt` is authoritative (guide §8). |
| B27 | Mic / voice input button | 14 | No speech input. |
| B28 | Scripted rotating scan-status strings ("Scanning file system anomalies", …) | 7 | Fabricated progress narration. |
| B29 | "System Root Directories / External Network Interfaces" as partial-scan reasons | 10 | Real partial reasons are reduced package visibility, threat-pack unavailability and per-app fingerprint failures. |
| B30 | "Threats Found: 0" framed as a safety guarantee, "System Integrity: Secure", "No suspicious connections" | 9, 22 | Absolute claims — see §C1. |

## C. Language corrections applied

**C1 — absolute security claims removed.** Replacements used at runtime, chosen from the
actual state:

| Stitch copy | Replacement | Condition |
| --- | --- | --- |
| "Your device is safe" / "Your device is protected" | "No urgent threats found" | scan completed, no threat/review items |
| — | "1 item needs attention" (pluralised) | review/threat items present |
| — | "Scan finished with limits" | `ScanOutcome.PARTIAL` |
| — | "Scan could not finish" | `ScanOutcome.ERROR` |
| — | "Not scanned yet" | no result |
| "Protected" (ring label) | "MONITORING" / "ATTENTION" / "LIMITED" / "UNAVAILABLE" | from foundation + scan state |
| "Connection is secure" | "Monitoring VillainCaller" / "Off" / "Consent needed" / real error | `NetworkGuard.state` |
| "Real-time protection is active" | "Pack v… · N indicators" / "Pack unavailable" | `IntelligenceState` |
| "All apps scanned" | "N apps analysed" | `analyzedCount` |
| "100% secure", "Virus free" | never used | — |

**C2 — no fabricated progress.** Determinate progress is shown only in
`ScanProgress.Analyzing`, where `analyzed/total` is real. Discovering and AI inference use
indeterminate indicators with the honest labels "Finding installed apps…" and
"Understanding…". No token streaming (the runtime does not expose it).

**C3 — technical vocabulary moved behind disclosure.** Permission constants
(`ACCESS_BACKGROUND_LOCATION`), SHA-256 digests, rule IDs, pack versions, chain hashes,
`ScanClassification`/`RunPhase` enum names and engine class names appear only inside
"Technical details" sections, never on primary surfaces.
