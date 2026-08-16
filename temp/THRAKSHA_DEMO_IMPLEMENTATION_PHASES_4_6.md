# THRAKSHA DEMO — IMPLEMENTATION GUIDE — PHASES 4–6

## PURPOSE

This document defines the second major implementation run for the existing Thraksha Guardian Android demo.

Phases 1–3 are complete and must be treated as the **known-good baseline**.

This run covers:

- **Phase 4 — Policy Engine + Advice Mode**
- **Phase 5 — Full Power Device Owner Enforcement**
- **Phase 6 — Antivirus / Signed Threat Intelligence**

The objective is to transform the current pipeline:

`SEE → UNDERSTAND → JUDGE → RECORD`

into:

`SEE → UNDERSTAND → JUDGE → DECIDE → ACT/ADVISE → VERIFY → RECORD`

and then add a second, independent detection pillar:

`SIGNED ANTIVIRUS / THREAT INTELLIGENCE`

Do not rebuild the working detection foundation.

---

# 0. CURRENT BASELINE — DO NOT REGRESS

The existing implementation already provides:

- encrypted SQLCipher Room database;
- Android Keystore-protected DB passphrase;
- hash-chained tamper-evident audit log;
- concurrency-safe `AuditLog.append()`;
- signed Rulepack v2;
- `RulepackVerifier`;
- production `RulepackLoader`;
- `AppInventory`;
- `ObservedApp`;
- `AppType`;
- deterministic app baselines;
- `RuleEngine`;
- `SecurityAuditEngine`;
- launch audit;
- manual `Run Security Check`;
- `SecurityEventBus`;
- reactive privilege detection;
- `DemoMode`;
- `FoundationStatus`;
- GoodCaller;
- VillainCaller;
- targeted package visibility;
- working ADVICE MODE badge;
- VPN intentionally disabled;
- 17 unit tests passing;
- 20 instrumented tests passing.

Expected current behaviour:

`GoodCaller → CLEAN / WATCHING`

`VillainCaller → THREAT DETECTED`

Current VillainCaller detection is based on **observable declared application capabilities**, not unsupported claims about runtime activity.

Preserve that technical honesty throughout this run.

---

# 1. LOCKED PRODUCT PRINCIPLES

## 1.1 Authority mode

Demo authority derives from Android privilege:

`DEVICE_OWNER → FULL POWER`

`DEVICE_ADMIN / NORMAL → ADVICE MODE`

Do not change this mapping.

`ExecutionMode` remains a separate policy preference.

---

## 1.2 Detection and enforcement remain separate

The detector answers:

**What did we find?**

The policy layer answers:

**What should Thraksha do about it?**

The enforcer answers:

**What did Android actually allow us to do?**

Never combine all three responsibilities into `RuleEngine`.

Desired architecture:

`Finding`
→ `PolicyEngine`
→ `SecurityDecision`
→ `Enforcer`
→ `ActionResult`
→ `SecurityEventBus`
→ `AuditLog`
→ UI

---

## 1.3 Never claim an action succeeded before verifying it

Calling an Android API is not equivalent to successful containment.

Every enforcement action must return a structured result such as:

- REQUESTED
- SUCCEEDED
- FAILED
- UNSUPPORTED
- NOT_AUTHORISED
- ALREADY_IN_STATE

The dashboard may display **ACTED** only after the action result confirms success.

---

## 1.4 Advice Mode is a real product mode

Advice Mode must not look like a broken Full Power mode.

If Thraksha lacks authority, the result should be:

`THREAT DETECTED → ADVISED`

not:

`THREAT DETECTED → FAILED`

Advice must explain:

- what was found;
- why it matters;
- what Thraksha recommends;
- what Full Power would have done;
- what the user can do manually when appropriate.

---

## 1.5 No destructive Device Owner actions

Explicitly prohibited in this demo:

- wipeData;
- factory reset;
- deleting user files;
- resetPassword;
- arbitrary user restrictions unrelated to containment;
- reboot;
- uninstalling unrelated apps;
- hiding/suspending GoodCaller;
- applying policy to packages outside the controlled demo scope.

The demo should contain, not destroy.

---

# PHASE 4 — POLICY ENGINE + ADVICE MODE

## Goal

Introduce a deterministic decision layer between detection and enforcement.

At the end of Phase 4:

`VillainCaller → detected → PolicyEngine → ADVISED`

on the existing non-Device-Owner phone.

No automatic Device Owner enforcement is required during Phase 4.

---

# 4.1 SecurityDecision model

Create a structured decision model.

Conceptually:

`SecurityDecision`

should contain enough information for later Full Power execution and UI rendering.

Suggested fields:

- decisionId;
- packageName;
- appName;
- originating findings;
- highest severity;
- effective confidence/rule weight;
- privilegeLevel;
- executionMode;
- decisionType;
- recommendedActions;
- human-readable explanation;
- timestamp/reason metadata where appropriate.

Recommended `decisionType` values:

- OBSERVE
- ADVISE
- ACT

Do not overload `Finding` to represent a policy decision.

---

# 4.2 SecurityAction model

Define actions as structured domain objects.

Potential actions for this demo:

- `DENY_PERMISSION(permission)`
- `SUSPEND_PACKAGE`
- `BLOCK_NETWORK`
- `OPEN_APP_SETTINGS`
- `NO_ACTION`

`BLOCK_NETWORK` is future-facing during Phase 4/5 unless a real network enforcer exists.

Do not represent unsupported actions as executable.

Each action must declare whether it requires:

- Device Owner;
- ordinary user permission;
- no special authority;
- future subsystem support.

---

# 4.3 PolicyEngine

Create a pure/testable `PolicyEngine`.

Inputs:

- findings;
- `PrivilegeLevel`;
- `ExecutionMode`;
- configured severity/confidence thresholds.

Output:

- `SecurityDecision`.

The engine must contain **no Android framework calls**.

It should be deterministic.

The same input must always produce the same decision.

---

# 4.4 Policy semantics

Use the existing concepts rather than introducing arbitrary new policy levels.

Suggested initial semantics:

### No findings

→ `OBSERVE`

### Findings exist + no Device Owner

→ `ADVISE`

### Findings exist + Device Owner

Whether the result becomes `ACT` depends on `ExecutionMode` and severity threshold.

Recommended demo mapping:

`OBSERVE`
→ never enforce automatically.

`GUIDED`
→ advise; user explicitly approves an enforcement action.

`AUTO_DEFEND`
→ eligible HIGH/CRITICAL findings may automatically act.

`LOCKDOWN`
→ reserved for later Safe Mode work; do not implement broad lockdown here.

Do not silently reinterpret the existing enum.

---

# 4.5 Confidence handling

Current confidence values in the signed rulepack are authored rule weights.

Do NOT present them internally as machine-learning probability.

Policy may use them as thresholds, but names/comments should reflect that they are rule confidence/weight values.

Example:

`ruleConfidence = 92`

not:

`malwareProbability = 92%`

---

# 4.6 Recommended action generation

Policy should translate findings into sensible proposed responses.

For VillainCaller, examples may include:

Media capability mismatch:

→ recommend denying media permission.

Location mismatch:

→ recommend denying location permission.

High-risk compound profile:

→ recommend package suspension/quarantine.

Package visibility mismatch:

→ advise that broad app-list capability is inappropriate for Caller-ID.

Only recommend actions that actually correspond to the finding.

Do not recommend arbitrary punishment simply because severity is high.

---

# 4.7 AdvisoryEnforcer

Create an `Enforcer` abstraction now so Phase 5 does not redesign Phase 4.

Conceptual interface:

`execute(decision): EnforcementResult`

Implement:

`AdvisoryEnforcer`

Responsibilities:

- perform no privileged action;
- convert the decision into a clear advisory result;
- optionally expose an app-settings deep-link;
- emit the correct action/advice security event;
- make it explicit that no automatic OS action occurred.

It should produce a successful **ADVISED** result rather than a failed ACT result.

---

# 4.8 Advisory UI

Extend the security audit card minimally.

VillainCaller flow should visibly become:

`THREAT DETECTED`

then:

`ADVISED`

Display:

- offending app;
- strongest finding;
- why Thraksha considers it abnormal;
- recommended response;
- authority explanation;
- "FULL POWER would be able to..." language only for capabilities Phase 5 actually implements.

Do not claim network blocking yet.

---

# 4.9 Security events

Extend the existing event model where necessary.

Possible new event:

`DecisionMade`

and/or enrich `ActionTaken`.

Avoid creating a second event bus.

Every policy outcome should remain traceable through `SecurityEventBus`.

Advice should be auditable.

Example audit sequence:

`SCAN`
→ `THREAT`
→ `DECISION`
→ `ADVISED`

Use existing audit infrastructure.

Do not redesign the DB schema unless concrete requirements demand it.

---

# 4.10 Tests

Add tests covering:

- no findings → OBSERVE;
- normal privilege + HIGH finding → ADVISE;
- Device Admin + CRITICAL finding → ADVISE;
- Device Owner + OBSERVE execution mode → OBSERVE;
- Device Owner + GUIDED → ADVISE pending approval;
- Device Owner + AUTO_DEFEND + eligible HIGH/CRITICAL → ACT;
- irrelevant/disabled finding does not produce destructive recommendation;
- media mismatch maps to permission-related recommendation;
- compound critical finding may recommend suspension;
- AdvisoryEnforcer performs no DPM calls;
- repeated decisions are deterministic.

---

# PHASE 4 ACCEPTANCE CRITERIA

Phase 4 is complete only when:

- all previous tests remain passing;
- PolicyEngine is pure and Android-independent;
- detection remains unchanged;
- GoodCaller remains CLEAN/WATCHING;
- VillainCaller receives deterministic decisions;
- current DEVICE_ADMIN/NORMAL device produces ADVISED;
- no privileged action occurs in Advice Mode;
- advisory result reaches the event bus;
- advisory result reaches the encrypted audit trail;
- UI clearly distinguishes THREAT DETECTED from ADVISED;
- Full Power behaviour is represented only as a future/available action, never falsely reported as completed.

STOP before Phase 5 if the policy boundary is unclear or tests are unstable.

---

# PHASE 5 — FULL POWER DEVICE OWNER ENFORCEMENT

## Goal

Add genuine, narrowly scoped Android Device Owner containment.

A dedicated factory-reset test phone is available for this phase.

At the end:

`VillainCaller`
→ detected
→ PolicyEngine decides ACT
→ Device Owner enforcement executes
→ result verified
→ ACTED displayed
→ action persisted in audit chain.

GoodCaller must never be affected.

---

# 5.1 Device Owner provisioning

Do not assume Device Owner is active.

Before implementation/runtime verification:

1. Confirm the dedicated test device is factory reset or otherwise eligible.
2. Install Thraksha.
3. Provision the existing DeviceAdminReceiver as Device Owner using the correct development provisioning method.
4. Verify using Android APIs and ADB.
5. Confirm dashboard changes from ADVICE MODE to FULL POWER.

Document exact provisioning and removal/reset steps in:

`temp/DEVICE_OWNER_DEMO_SETUP.md`

Never automate a factory reset.

Never trigger a wipe.

---

# 5.2 DeviceOwnerEnforcer

Implement an enforcer dedicated to supported DevicePolicyManager operations.

Keep all DPM calls isolated here or behind a narrow Android enforcement adapter.

Do not place DPM calls inside:

- RuleEngine;
- SecurityAuditEngine;
- Compose;
- PolicyEngine.

---

# 5.3 Permission containment

Implement defensive permission denial where applicable using supported Device Owner APIs.

The enforcement system should inspect which offending requested permissions are runtime permissions and only attempt appropriate revocation.

For the controlled VillainCaller profile, candidates include:

- `READ_MEDIA_IMAGES`;
- `ACCESS_FINE_LOCATION`.

Before action:

- record current state.

Perform action.

After action:

- query/verify actual resulting state.

Return structured result.

Do not claim a permission was revoked merely because the API returned without throwing.

---

# 5.4 Package suspension / quarantine

Implement package suspension for VillainCaller using DevicePolicyManager-supported package suspension.

Scope enforcement through an explicit demo allowlist/target registry.

Allowed target for suspension:

`com.thraksha.demo.villaincaller`

Never suspend:

- Guardian;
- launcher;
- system UI;
- settings;
- phone/dialer;
- GoodCaller;
- arbitrary packages.

The enforcer should refuse packages outside permitted demo targets during this phase.

This is an intentional safety boundary.

---

# 5.5 Quarantine semantics

For this demo:

**QUARANTINE = package suspended**

Do not claim:

- force-stop;
- uninstall;
- delete;
- malware removal.

Use technically accurate language:

`VillainCaller suspended`

or:

`VillainCaller quarantined by package suspension`

---

# 5.6 Restoration path

Every containment action must have an explicit reversible restore path.

Implement:

- resume/unsuspend VillainCaller;
- restore permissions only if deliberate and appropriate;
- clear/resolve quarantine state;
- record restoration in audit history.

Provide a developer/demo reset control clearly separated from security actions.

The demo must be repeatable without reinstalling the entire device between every run.

---

# 5.7 Enforcement verification

Introduce a verification layer.

After permission action:

→ inspect grant state.

After suspension:

→ inspect package suspended state through supported package APIs where available.

Possible structured result:

`ActionResult`

with:

- action;
- target;
- requestedAt;
- status;
- verifiedState;
- failureReason.

UI uses `verifiedState`, not intent.

---

# 5.8 Enforcer orchestration

Introduce an enforcement coordinator if necessary.

Conceptual path:

`SecurityDecision.ACT`
→ `EnforcementCoordinator`
→ action list
→ DeviceOwnerEnforcer
→ verification
→ aggregate result.

If multiple actions are requested and one fails:

do not convert the whole result into false success.

Represent partial success accurately.

Example:

Media permission: DENIED ✅  
Location permission: DENIED ✅  
Package suspension: SUCCEEDED ✅

→ `ACTED`

or:

Media permission: FAILED  
Package suspension: SUCCEEDED

→ `PARTIALLY ACTED`

Use a structured model even if the UI later simplifies the wording.

---

# 5.9 User-triggered versus automatic action

Respect `ExecutionMode`.

During testing:

- OBSERVE → never act;
- GUIDED → require explicit confirmation;
- AUTO_DEFEND → eligible findings may act automatically.

Do not let merely becoming Device Owner automatically mean every finding is enforced.

Authority and policy are separate axes.

---

# 5.10 Device Owner UI

When Device Owner is verified:

badge:

`FULL POWER`

VillainCaller sequence should become:

`WATCHING`
→ `THREAT DETECTED`
→ `ACTING`
→ `ACTED`

Display actual successful operations.

Example:

`Media permission denied`

`Location permission denied`

`VillainCaller suspended`

Do not show operations that failed.

---

# 5.11 Advice Mode regression

Repeat the same test without Device Owner authority.

Expected:

same detection

same findings

but:

`THREAT DETECTED`
→ `ADVISED`

No DPM action.

This regression test is critical.

FULL POWER and ADVICE must share:

- detector;
- rulepack;
- audit engine;
- PolicyEngine.

They should differ only at the authority/enforcement boundary.

---

# 5.12 Audit requirements

Record:

- policy decision;
- requested actions;
- actual results;
- verification state;
- failures;
- restorations.

The encrypted audit log should make it possible to answer:

**What was detected?**

**What did Thraksha decide?**

**What did Android allow?**

**What actually happened?**

---

# 5.13 Safety tests

Add explicit tests proving:

- GoodCaller cannot be targeted by the demo quarantine command;
- Guardian cannot suspend itself;
- arbitrary package names are rejected;
- Device Admin without Device Owner cannot execute Owner-only containment;
- policy ACT without authority falls back safely to ADVISED or NOT_AUTHORISED;
- failed DPM calls are never represented as ACTED;
- restoration works;
- repeated suspension is idempotent;
- repeated restore is safe;
- no wipe/reset APIs are present in the enforcement path.

Perform repository-wide grep after implementation for prohibited destructive API usage and document the result.

---

# PHASE 5 ACCEPTANCE CRITERIA

Phase 5 is complete only when:

- dedicated test phone is provisioned as Device Owner;
- Thraksha detects Device Owner and shows FULL POWER;
- GoodCaller remains unaffected;
- VillainCaller detection remains unchanged;
- eligible runtime permissions can be denied and verified where supported;
- VillainCaller can be suspended and verified;
- restoration works;
- ACTED appears only after verified success;
- Advice Mode still performs zero privileged actions;
- every action/result is recorded in encrypted audit;
- no destructive Device Owner functionality is implemented;
- all tests pass;
- full demo can be repeated reliably.

STOP if Device Owner API behaviour differs from expectations. Document actual Android behaviour rather than masking it.

---

# PHASE 6 — ANTIVIRUS / SIGNED THREAT INTELLIGENCE

## Goal

Add a traditional signature/intelligence layer alongside Thraksha's existing contextual profile detection.

The intended architecture is:

`SecurityAuditEngine`

→ `Profile/Rule Detection`

plus

→ `Threat Intelligence Scanner`

→ unified findings

→ `PolicyEngine`

→ Act / Advise.

Do NOT replace the contextual RuleEngine.

Antivirus becomes a **second detection pillar**.

---

# 6.1 Phase 6 scope

This is an OFFLINE demo antivirus implementation.

Do NOT implement:

- cloud reputation API;
- live malware feed;
- malware downloading;
- automatic sample collection;
- executable malware;
- dynamic code execution;
- exploit analysis;
- arbitrary filesystem crawling;
- machine-learning malware classification.

The objective is to prove the architecture safely using controlled benign test samples.

---

# 6.2 ThreatPack

Create a separate signed threat-intelligence artefact.

Recommended files:

`threatpack.json`

`threatpack.sig`

`threatpack_public.key`

Do not mix threat intelligence records into the behavioural rulepack unless there is a compelling architectural reason.

Both may reuse the same signature-verification framework, but keep policy rules and threat-indicator data conceptually separate.

---

# 6.3 ThreatPack schema

Design a versioned schema.

Conceptually:

`ThreatPack`

fields:

- schemaVersion;
- packVersion;
- generatedAt;
- expiresAt optional;
- indicators.

Each indicator should have:

- id;
- type;
- value;
- classification;
- severity;
- description;
- source;
- enabled;
- optional metadata.

Supported Phase 6 indicator types:

- `APK_SHA256`
- `SIGNING_CERT_SHA256`
- `PACKAGE_NAME`

Optional future types may be represented in schema but not evaluated:

- DOMAIN
- IP
- URL
- FILE_HASH
- MALWARE_FAMILY

Do not pretend unsupported indicators are active.

---

# 6.4 Controlled threat records

Use only safe controlled demo records.

For example, the demo threat pack may contain indicators matching VillainCaller's:

- package name;
- built APK SHA-256;
- signing certificate SHA-256.

Classify these clearly as:

`DEMO_TEST_THREAT`

or equivalent.

Do NOT label the benign VillainCaller APK as a real known malware family.

The purpose is to demonstrate the detection mechanism, not manufacture fake threat intelligence.

---

# 6.5 Signing

Reuse the project's existing cryptographic trust model where practical.

Requirements:

- RSA/SHA-256 signed threat pack;
- exact bytes verified before parsing/activation;
- verification fail-closed;
- private key never packaged in APK;
- private key never committed;
- signing utility stored under `tools/`;
- public verification key may ship in APK;
- signature checked by tests.

Consider extracting common signing-verification utilities only if doing so is small and does not destabilize the working rulepack implementation.

Avoid unnecessary cryptographic refactoring.

---

# 6.6 ThreatPackLoader

Create a production loader analogous to the working `RulepackLoader`.

Responsibilities:

1. read threat pack bytes;
2. read signature;
3. read public key;
4. verify;
5. parse only after successful verification;
6. validate schema/version;
7. return verified immutable threat data;
8. fail closed on corruption.

Expose clear error states.

The application must not silently treat an invalid threat database as clean.

---

# 6.7 APK fingerprinting

Create a dedicated scanner component.

Potential name:

`AntivirusScanner`

or:

`ThreatIntelligenceScanner`

Input:

`ObservedApp`

plus package/application file metadata available through Android.

Generate supported identifiers such as:

- package name;
- signing certificate SHA-256;
- APK SHA-256 where reliably accessible.

Keep hashing off the main/UI thread.

Use streaming file hashing rather than reading an entire APK into memory.

---

# 6.8 Signing certificate identity

Use Android's package signing information rather than relying on deprecated legacy signature APIs where newer supported APIs exist.

Account for signing history correctly where practical.

Represent:

- current signer;
- certificate SHA-256;
- historical signer if relevant.

For this demo, the decoys are debug-signed, so certificate matching is a controlled test signal only.

Do not call the debug certificate inherently malicious.

---

# 6.9 APK hash limitations

Document what exactly is being hashed.

An installed APK may involve:

- base APK;
- split APKs.

For Phase 6, if only `sourceDir` / base APK SHA-256 is evaluated, name the indicator accordingly:

`BASE_APK_SHA256`

rather than implying a cryptographic digest of every split and resource on the installed application.

If implementing split hashing is simple and reliable, model it explicitly.

Do not hide this distinction.

---

# 6.10 Antivirus finding model

Do not create an incompatible parallel finding system.

Either extend `Finding` with a source/type field or introduce a compatible common interface.

A finding should distinguish:

`PROFILE_RULE`

from:

`THREAT_INTELLIGENCE`

Example antivirus finding:

Source: THREAT_INTELLIGENCE  
Indicator: SIGNING_CERT_SHA256  
Classification: DEMO_TEST_THREAT  
Package: VillainCaller  
Severity: HIGH  
Evidence: signing certificate matched signed threat pack record.

---

# 6.11 Unified audit flow

Update `SecurityAuditEngine.runAudit()` so one scan can orchestrate:

1. load/verify rulepack;
2. load/verify threat pack;
3. inventory target apps;
4. run profile RuleEngine;
5. run ThreatIntelligenceScanner;
6. merge findings;
7. emit events;
8. invoke PolicyEngine;
9. Act or Advise according to privilege/policy;
10. persist results.

Do not duplicate app inventory.

Do not hash the same APK repeatedly within one scan.

Cache per-scan fingerprints.

---

# 6.12 Antivirus decision semantics

A signature/intelligence match is different from a behavioural profile mismatch.

Policy must preserve that distinction.

Potential initial policy:

Package-only test indicator:

→ HIGH or configured severity.

Certificate + APK hash both match the same threat record:

→ stronger confirmed indicator.

Do NOT invent probabilistic confidence math.

If combining indicators, use deterministic signed rules/weights.

---

# 6.13 Known-clean controls

GoodCaller must exist in the scan but not in the threat pack.

Expected:

GoodCaller:

Profile detection → CLEAN  
Threat Intelligence → NO MATCH  
Overall → CLEAN / WATCHING

VillainCaller:

Profile detection → findings  
Threat Intelligence → controlled indicator match  
Overall → THREAT DETECTED

This proves the antivirus scanner does not simply flag every visible APK.

---

# 6.14 Corruption tests

Threat pack tests must include:

- valid signature → loads;
- one-byte threatpack modification → rejected;
- modified signature → rejected;
- wrong public key → rejected;
- malformed JSON after successful signature scenario handled appropriately;
- unsupported schema version → rejected;
- duplicate indicator IDs → rejected;
- malformed hash length → rejected;
- invalid hex/base64 → rejected;
- disabled indicator → no match;
- unknown indicator type → skipped or rejected according to documented schema policy.

Prefer fail-closed behaviour.

---

# 6.15 Antivirus UI

Do not redesign the full investor dashboard yet.

Add a clear scan-source distinction.

For example:

`PROFILE CHECK`

`THREAT INTELLIGENCE`

For VillainCaller:

`Profile mismatch detected`

and:

`Signed threat-intelligence match`

Do not display:

`Virus found`

unless the threat pack classification genuinely uses that language.

For controlled samples use:

`DEMO TEST THREAT MATCH`

or another technically honest label.

---

# 6.16 Antivirus audit trail

Audit entries must include:

- threat pack version;
- indicator ID;
- indicator type;
- package;
- evidence digest/fingerprint;
- classification;
- severity;
- resulting policy decision;
- enforcement/advice outcome.

Do not log private key material.

Avoid logging full unnecessary certificate blobs.

Fingerprint values are sufficient.

---

# 6.17 Threat pack update architecture — PREPARE, DO NOT IMPLEMENT NETWORKING

Design the loader/storage APIs so future updates can support:

`download candidate`
→ `verify signature`
→ `validate schema/version`
→ `write temporary file`
→ `atomically activate`
→ `retain previous known-good pack`
→ `audit update`.

But Phase 6 remains OFFLINE.

Do not add:

- Retrofit;
- OkHttp;
- backend URL;
- cloud fetch;
- scheduled download.

The bundled threat pack is enough for this phase.

---

# 6.18 Performance

Measure scan time on the S20 FE.

Record separately:

- inventory duration;
- RuleEngine duration;
- certificate fingerprint duration;
- APK hash duration;
- total audit duration.

Hashing a multi-megabyte APK should occur on IO/default worker context.

UI must remain responsive.

Do not prematurely optimise without measurement.

---

# 6.19 Tests

Add unit/instrumented tests covering:

### Threat pack

- signature verification;
- corruption rejection;
- schema validation;
- indicator parsing.

### Fingerprinting

- deterministic SHA-256;
- known fixture produces known digest;
- certificate digest stable;
- missing/unreadable APK handled safely.

### Matching

- GoodCaller → no threat-intel findings;
- VillainCaller → expected controlled indicator findings;
- wrong hash → no match;
- disabled indicator → no match;
- certificate-only match identified correctly;
- package-name-only match identified correctly;
- duplicate findings appropriately deduplicated.

### Unified pipeline

- profile + antivirus findings coexist;
- one source failing does not falsely report CLEAN;
- invalid threatpack produces a visible scanner-unavailable/failure state;
- policy receives unified findings;
- Advice Mode advises;
- Full Power acts only according to Phase 5 policy;
- audit chain remains valid.

---

# PHASE 6 ACCEPTANCE CRITERIA

Phase 6 is complete only when:

- all previous tests remain passing;
- signed threat pack exists;
- private key is absent from APK;
- private key is absent from tracked files;
- threat pack is verified before use;
- corrupted pack fails closed;
- production audit path invokes threat intelligence scanner;
- GoodCaller produces no antivirus match;
- VillainCaller produces deterministic controlled test matches;
- APK/package/certificate indicators are clearly distinguished;
- findings merge with existing profile findings;
- PolicyEngine consumes unified findings;
- Advice Mode remains non-enforcing;
- Full Power enforcement uses the same Phase 5 mechanisms;
- antivirus findings are persisted into the encrypted audit chain;
- scan performance is measured on S20 FE;
- no malware is downloaded, generated, executed, or collected;
- no backend/network threat-feed system is added.

---

# CROSS-PHASE ARCHITECTURE TARGET

By the end of Phase 6, the real runtime architecture should resemble:

`App launch / Run Security Check`

→ `SecurityAuditEngine`

→ `AppInventory`

→ per app:

### Detection pillar A

`AppBaseline`
→ `Verified Rulepack`
→ `RuleEngine`
→ profile findings

### Detection pillar B

`Verified ThreatPack`
→ `ThreatIntelligenceScanner`
→ package/hash/certificate findings

→ merge findings

→ `PolicyEngine`

using:

- findings;
- severity;
- rule weight;
- `ExecutionMode`;
- `PrivilegeLevel`.

→ Decision:

`OBSERVE`

or

`ADVISE`

or

`ACT`

### ADVISE

→ `AdvisoryEnforcer`

→ recommendation

→ event

→ audit

### ACT

→ `DeviceOwnerEnforcer`

→ permission denial

→ package suspension

→ verify actual state

→ event

→ audit

→ Dashboard:

`WATCHING`

or

`THREAT DETECTED → ADVISED`

or

`THREAT DETECTED → ACTED`

---

# REQUIRED CHECKPOINT REPORT

Continue using:

`temp/THRAKSHA_DEMO_PHASES_4_6_PROGRESS.md`

After every phase record:

- phase;
- files created;
- files modified;
- architecture changes;
- decisions made;
- build result;
- unit-test result;
- instrumented-test result;
- device/runtime tests;
- screenshots produced;
- Device Owner status;
- safety checks;
- limitations;
- divergences from this guide;
- exact next step.

Do not wait until Phase 6 to document Phase 4 or Phase 5.

---

# REQUIRED DEVICE TESTS

## Advice device / state

Test:

`DEVICE_ADMIN or NORMAL`

Expected:

VillainCaller
→ detection
→ ADVISE
→ zero Device Owner action.

## Full Power device

Test dedicated factory-reset phone provisioned as Device Owner.

Expected:

VillainCaller
→ detection
→ ACT
→ supported permissions denied where applicable
→ package suspended
→ state verified
→ ACTED.

GoodCaller:

→ never acted upon.

---

# REQUIRED SCREENSHOTS

Capture at minimum:

### Phase 4

- Advice Mode finding;
- advisory result;
- audit record.

### Phase 5

- FULL POWER badge;
- VillainCaller before detection;
- Threat Detected;
- Acted;
- suspended/quarantined state;
- restoration state;
- audit actions.

### Phase 6

- threat-intelligence source shown;
- controlled signature/hash match;
- GoodCaller clean result;
- unified result containing profile + threat-intelligence findings.

Store under:

`temp/screenshots/`

---

# REQUIRED FINAL VERIFICATION

At the end of Phase 6 run:

- clean build all three modules;
- run all unit tests;
- run all applicable instrumented tests;
- verify GoodCaller;
- verify VillainCaller;
- verify Advice Mode;
- verify Full Power;
- verify restoration;
- verify audit chain;
- verify rulepack signature;
- verify threatpack signature;
- inspect APK assets;
- verify no private keys are packaged;
- inspect git working tree;
- document every changed file.

Do not commit automatically.

Leave the working tree available for human review.

---

# STOP CONDITIONS

STOP and report instead of improvising if:

- Device Owner provisioning cannot be achieved safely;
- DPM actions behave differently from documented expectations;
- GoodCaller becomes affected by containment;
- enforcement would need wipe/reset/destructive operations;
- detection must be weakened to make enforcement work;
- an action cannot be verified but the UI would need to claim success;
- threat scanning would require downloading real malware;
- threat pack private key would need to enter the APK;
- cryptographic verification would need to be bypassed;
- existing audit-chain integrity would need to be abandoned;
- adding antivirus requires scanning inaccessible private app files;
- implementation would require unsupported Android APIs;
- a security capability would have to be simulated without explicit labelling;
- existing Phases 1–3 tests regress and the cause cannot be isolated.

Never hide an Android limitation with staged behaviour.

---

# FINAL PRODUCT EXPECTATION AFTER PHASE 6

The demo should now demonstrate three distinct concepts.

## 1. Contextual security

"Is this capability appropriate for this kind of app?"

GoodCaller:
→ expected caller-ID profile
→ clean.

VillainCaller:
→ inappropriate media/location/package visibility profile
→ detected.

## 2. Antivirus / known-threat intelligence

"Does this application match something in our cryptographically trusted threat intelligence?"

GoodCaller:
→ no match.

VillainCaller:
→ controlled signed demo-threat match.

## 3. Authority-aware response

"What can Thraksha actually do about it?"

Advice Mode:

→ detect  
→ explain  
→ recommend  
→ record.

Full Power:

→ detect  
→ decide  
→ deny supported permissions  
→ suspend/quarantine  
→ verify  
→ record.

The story is:

**Thraksha does not rely on one security technique.**

It combines:

**known-threat intelligence**

+

**contextual app-profile analysis**

+

**authority-aware response**

while keeping every claim technically grounded in what Android actually exposes.

At the end of Phase 6 the expected core demonstration is:

`GoodCaller`
→ profile clean
→ threat-intel clean
→ WATCHING.

`VillainCaller`
→ profile mismatch
→ signed threat-intel match
→ THREAT DETECTED.

Normal authority:
→ ADVISED.

Device Owner:
→ ACTED.

Everything:
→ encrypted, tamper-evident audit history.

That is the completion target for this implementation run.