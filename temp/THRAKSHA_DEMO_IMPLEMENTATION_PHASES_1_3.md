# THRAKSHA DEMO — IMPLEMENTATION GUIDE — PHASES 1–3

## PURPOSE

This document defines the first implementation run for converting the existing Thraksha Guardian Android project into a focused, technically honest security demo.

This run covers:

- **Phase 1 — Foundation Stabilization**
- **Phase 2 — Controlled Decoy Applications**
- **Phase 3 — Detection Brain + Launch Security Audit**

Do not implement enforcement, Device Owner actions, panic/safe mode, or full VPN/network monitoring during this run.

Those belong to later phases.

The objective of this run is:

**Existing foundation → stable sandbox → real static security detection → working audit scan**

---

# 0. LOCKED ARCHITECTURE DECISIONS

Treat these as fixed unless implementation proves one technically impossible.

## 0.1 Demo modes

The visible demo modes are based on Android privilege:

- `DEVICE_OWNER` → **FULL POWER**
- `NORMAL` / non-Device-Owner → **ADVICE MODE**

Do not collapse this into `ExecutionMode`.

`ExecutionMode` is a separate policy concept and should not drive the FULL POWER / ADVICE badge.

For Phases 1–3, no enforcement is implemented yet.

---

## 0.2 Technical honesty

Do NOT claim Thraksha can directly observe:

- another app reading photos/media;
- another app querying the installed-app list;
- another app's encrypted HTTPS payload.

Android does not expose supported APIs for these claims.

For this demo, detect **real observable indicators**, including:

- inappropriate requested/granted permissions;
- inappropriate capabilities for the declared app type;
- broad package-query capability;
- app identity/package metadata;
- later, suspicious outbound network behaviour.

Do not disguise simulated telemetry as OS-level observation.

---

## 0.3 App-type reasoning

The central detection principle is:

`APP TYPE + CAPABILITY/PERMISSION + CONTEXT → VERDICT`

Not:

`PERMISSION X = MALWARE`

Example:

A banking/security app may legitimately require broader package visibility.

A caller-ID app should not normally require broad photo/media access.

The same capability may therefore produce different verdicts for different application categories.

---

## 0.4 Existing foundation must be reused

Do not rebuild these unless a concrete defect requires a minimal fix:

- SQLCipher encrypted Room database
- Android Keystore passphrase management
- hash-chained audit log
- RulepackVerifier
- RulepackLoader
- SecurityEvent / SecurityEventBus
- SecurityCapability
- Device Admin receiver registration
- CompanionForegroundService shell
- existing Compose theme/branding
- audit feed
- existing foundation tests

---

# PHASE 1 — FOUNDATION STABILIZATION

## Goal

Create a known-good foundation suitable for the demo without introducing new security features yet.

At the end of Phase 1:

- the project builds;
- existing tests are run where possible;
- Thraksha can see the two future decoy packages;
- privilege state is reliable;
- audit writes are safe for multiple future producers;
- startup failures are handled cleanly;
- the unfinished VPN cannot accidentally damage the demo.

---

## 1.1 Establish baseline

Before changing code:

1. Run the existing unit-test suite.
2. Build the debug APK.
3. Record failures before modifying anything.
4. Do not rewrite working foundation components simply to improve style.

If instrumented tests cannot be run because no device is available, record that clearly.

Create a Phase 1 checkpoint report containing:

- build result;
- test result;
- existing failures;
- files changed;
- remaining runtime-only checks.

---

## 1.2 Package visibility

The audit found that Thraksha currently has no package visibility configuration.

The two demo apps will use fixed package names:

- `com.thraksha.demo.goodcaller`
- `com.thraksha.demo.villaincaller`

Prefer targeted Android manifest visibility using `<queries>` for these packages instead of requesting broad `QUERY_ALL_PACKAGES`.

The demo should only require visibility into the controlled decoy environment.

Verify from Thraksha that both packages become discoverable once installed.

---

## 1.3 AuditLog concurrency

`AuditLog.append()` currently performs a read-last → calculate-next-ID → insert flow.

This will become unsafe when multiple security components emit concurrently.

Protect append operations with an appropriate coroutine synchronization primitive such as `Mutex`.

Preserve:

- current hash format;
- genesis behaviour;
- append-only API;
- existing verification behaviour.

Run existing audit tests after this change.

Add a concurrency-focused test if practical.

---

## 1.4 Startup resilience

Review `ThrakshaApplication` initialization.

The audit identified that database initialization during bus→audit wiring can currently propagate a Keystore/database failure and crash process startup.

Make initialization failures:

- visible;
- logged safely;
- non-silent;
- recoverable where reasonable.

Do not weaken cryptographic fail-closed behaviour.

Do not silently create a replacement database if the key cannot decrypt an existing one.

The application should communicate an initialization/security-storage failure rather than pretending protection is active.

---

## 1.5 Reactive privilege state

The dashboard currently takes a one-time privilege snapshot.

Create a clean source of truth that can refresh when the app resumes.

The dashboard must correctly reflect:

- Device Owner
- Device Admin
- Normal

without requiring a force-stop or full Activity recreation.

For the eventual demo:

- Device Owner maps to `FULL POWER`
- everything else maps to `ADVICE MODE`

Do not implement enforcement yet.

---

## 1.6 VPN safety

The current VPN implementation establishes a full-route TUN but has no forwarding implementation.

It must NOT be usable as a normal "VPN monitor" during Phases 1–3.

Choose the least invasive safe approach:

- disable/hide the VPN debug control from the demo path; or
- explicitly mark the service unavailable for this phase.

Do NOT implement full packet forwarding or network detection in this phase.

Do not leave a stage-visible control that can unintentionally black-hole device traffic.

---

## Phase 1 acceptance criteria

Phase 1 is complete only when:

- project builds successfully;
- unit tests pass or every remaining failure is documented;
- existing crypto/audit/rulepack tests remain intact;
- targeted package visibility exists for both decoys;
- privilege state refreshes reliably;
- audit append is concurrency-safe;
- initialization errors are handled honestly;
- unfinished VPN cannot accidentally be activated from the normal demo path.

STOP and resolve Phase 1 failures before proceeding.

---

# PHASE 2 — CONTROLLED DECOY APPLICATIONS

## Goal

Create two small Android apps in the same repository that provide a reliable controlled security sandbox.

Recommended Gradle modules:

`:goodcaller`

`:villaincaller`

Both must build as separately installable APKs.

Do not place decoy behaviour inside the Guardian application.

---

# 2.1 GoodCaller

Package:

`com.thraksha.demo.goodcaller`

Purpose:

Provide a caller-ID-shaped application whose security profile stays within the expected caller-ID baseline.

Keep it intentionally minimal.

Expected characteristics:

- simple branded screen;
- visible app name `GoodCaller`;
- requests only permissions/capabilities appropriate to its role;
- no unnecessary media permission;
- no broad package visibility;
- no suspicious network action;
- no hidden/background behaviour.

Its purpose is to prove:

**Thraksha does not flag everything.**

GoodCaller must produce zero threat findings under the Phase 3 caller-ID baseline.

---

# 2.2 VillainCaller

Package:

`com.thraksha.demo.villaincaller`

Purpose:

Provide a controlled caller-ID-shaped application with an intentionally inappropriate security profile.

It must remain harmless.

Its static profile should intentionally contain indicators that Thraksha can genuinely inspect.

Examples appropriate for the demo:

- caller-ID identity/category;
- inappropriate photo/media permission for its role;
- broad package visibility capability where Android permits declaration;
- other clearly unnecessary capability selected specifically for deterministic rule evaluation.

Do NOT implement malware.

Do NOT collect private user data.

Do NOT exfiltrate real photos, contacts, credentials, messages, or device information.

If a later network phase requires outbound behaviour, use only synthetic demo data.

---

## 2.3 Decoy UI

Keep both apps extremely simple.

Each should clearly display:

- app name;
- declared demo role: `Caller ID`;
- whether it represents the good or controlled suspicious sample.

VillainCaller may include a future-facing button such as:

`Run Demo Behaviour`

but Phase 2 does not need network enforcement.

Do not build complex interfaces.

---

## 2.4 Shared demo metadata

Create a deterministic mechanism in the Guardian project for mapping:

`packageName → AppType`

For example:

`GoodCaller → CALLER_ID`

`VillainCaller → CALLER_ID`

Do not infer these demo categories using machine learning.

The demo baseline is intentionally hard-coded and deterministic.

Design the mapping so more app types can be added later without rewriting the engine.

---

## Phase 2 acceptance criteria

Phase 2 is complete only when:

- both modules build;
- both produce separate APKs;
- both can coexist on one device;
- package IDs are stable;
- Thraksha can discover both packages;
- GoodCaller exposes only the intended caller-ID profile;
- VillainCaller exposes the intentionally suspicious profile;
- no actual malicious or privacy-invasive behaviour exists.

STOP and resolve Phase 2 failures before proceeding.

---

# PHASE 3 — DETECTION BRAIN + SECURITY AUDIT

## Goal

Turn the existing signed-rulepack/event-bus/audit infrastructure into a real security detection pipeline.

At the end of this phase, Thraksha must perform a real audit of the decoy applications and reliably produce:

`GoodCaller → CLEAN / WATCHING`

`VillainCaller → THREAT DETECTED`

No Device Owner action is required yet.

---

# 3.1 AppInventory

Create a dedicated application inventory component.

Responsibilities:

- query only packages visible to Thraksha;
- retrieve package/application metadata;
- retrieve requested permissions;
- determine granted permission state where supported;
- retrieve relevant package capabilities;
- expose signing certificate metadata if useful;
- map known demo packages to their demo `AppType`.

Use structured domain models rather than passing raw `PackageInfo` throughout the security engine.

Example conceptual model:

`ObservedApp`

with fields such as:

- packageName
- displayName
- appType
- requestedPermissions
- grantedPermissions
- capabilities
- signing information
- installed/version metadata

Keep Android-specific package querying isolated from rule evaluation.

---

# 3.2 Demo app baselines

Create a deterministic baseline system.

Minimum app type:

`CALLER_ID`

Expected/acceptable capabilities should include the minimum reasonable caller-ID profile.

Suspicious indicators may include:

- photo/media permission inappropriate for caller-ID;
- broad package-list visibility/capability;
- other explicitly configured demo-only mismatch.

Important:

A permission being requested does not automatically mean malware.

The rule verdict must consider the app type.

Design the baseline layer so future types can be added, e.g.:

- MESSAGING
- BANKING
- GAME

but only implement what is necessary for this demo.

---

# 3.3 Production Rulepack loading

`RulepackLoader` currently works but has no production caller.

Put verified rulepack loading onto the real security path.

Requirements:

1. load bundled rulepack;
2. verify signature before use;
3. refuse unverified rule data;
4. expose clear failure state if verification fails;
5. do not silently continue with unsigned rules.

Prefer using the existing models/verifier rather than replacing them.

---

# 3.4 Demo-aligned rule definitions

The existing generic 12-rule pack does not directly model the demo.

Create demo-aligned rules while preserving the signed-rulepack architecture.

Potential rule concepts:

- `caller-media-permission-mismatch`
- `caller-package-visibility-mismatch`
- `caller-high-risk-profile`

Populate meaningful rule parameters instead of leaving everything as `{}`.

If modifying `rulepack.json`, re-sign it correctly.

Do not commit private signing material into application assets or tracked source.

If the repository lacks a repeatable signing utility, create a small developer-side signing script/tool outside runtime application code and document exactly how to use it.

The APK must contain only:

- rulepack;
- signature;
- public verification key.

Never package the private key.

---

# 3.5 RuleEngine

Create a clear `RuleEngine`.

Input:

- verified rulepack;
- `ObservedApp`;
- app baseline/context.

Output:

structured findings.

A finding should contain enough information for later enforcement and UI work, such as:

- ruleId
- packageName
- appName
- appType
- severity
- confidence
- human-readable reason
- observed evidence

Do not make the engine depend directly on Compose/UI.

Do not perform DevicePolicyManager actions from the RuleEngine.

Detection and enforcement must remain separate.

---

# 3.6 Security events

Use the existing `SecurityEventBus`.

Real RuleEngine findings should emit `SecurityEvent.ThreatDetected`.

Extend the event model only where necessary to carry structured information.

Do not create a second parallel event system.

The audit collector must continue to record security events.

---

# 3.7 Security Audit Engine

Create one reusable audit entry point, conceptually:

`SecurityAuditEngine.runAudit()`

Responsibilities:

1. ensure the rulepack is verified;
2. inventory target apps;
3. resolve app type/baseline;
4. evaluate rules;
5. emit findings;
6. produce a structured audit result.

The same engine must support both:

- automatic launch audit;
- manual `Run Security Check` invocation later.

Do not duplicate logic between startup and button-triggered scans.

---

# 3.8 Launch audit

Run an initial security audit after the application/security foundation is ready.

Avoid racing the event-bus subscriber during process startup.

The audit should occur at a lifecycle point where:

- encrypted storage is initialized;
- event/audit collector is active;
- package inventory can run safely.

Do not repeatedly rescan on every Compose recomposition.

Define a clear scan lifecycle.

---

# 3.9 Manual audit trigger

Provide a developer/demo-visible button:

`Run Security Check`

For this phase, its job is detection only.

It invokes exactly the same `SecurityAuditEngine` as the launch scan.

It must NOT use the old fake `Emit Test Threat Event` mechanism.

The fake test-event button should either:

- move into an explicitly labelled developer-only section; or
- be removed from the normal demo path.

Do not represent a synthetic event as a real finding.

---

# 3.10 Audit result behaviour

Expected result with both decoys installed:

## GoodCaller

Result:

`CLEAN / WATCHING`

No `ThreatDetected` event.

No false-positive audit row.

It may have a scan/clean informational result if the existing audit model supports that cleanly.

## VillainCaller

Result:

`THREAT DETECTED`

At least one real rule should fire based on its observable static security profile.

The threat must identify:

- VillainCaller;
- the specific rule;
- why the capability is abnormal for a caller-ID app;
- severity/confidence;
- evidence used.

Do not claim an action occurred.

Enforcement comes in the next implementation phase.

---

# 3.11 Dashboard scope for this run

Do NOT perform the full investor dashboard redesign yet.

Only make the minimum UI changes necessary to validate Phase 3:

- clear FULL POWER / ADVICE MODE badge;
- `Run Security Check` button;
- visible audit result;
- identifiable GoodCaller/VillainCaller finding;
- no misleading `ACTED` state yet.

Full:

- WATCHING
- THREAT DETECTED
- ACTED
- ADVISED

presentation belongs to a later UI phase after PolicyEngine/enforcement exists.

---

# 3.12 Tests

Add focused tests for:

### App baseline

- GoodCaller profile is allowed.
- VillainCaller media/profile mismatch is flagged.
- same capability can be acceptable for a different app type when configured.

### RuleEngine

- disabled rule does not fire;
- enabled matching rule fires;
- finding contains correct package/rule/evidence;
- unverified rulepack cannot be used.

### Audit engine

- GoodCaller produces zero threat findings;
- VillainCaller produces deterministic threat findings;
- repeated scan behaves predictably;
- missing decoy app does not crash the audit.

Preserve all existing foundation tests.

---

# PHASE 3 ACCEPTANCE CRITERIA

Phase 3 is complete only when all of the following are true:

- Guardian builds.
- GoodCaller builds.
- VillainCaller builds.
- Existing foundation tests still pass.
- Production code actually calls `RulepackLoader`.
- Rulepack signature is verified before evaluation.
- Thraksha can inventory both decoy apps.
- Both decoys are classified as `CALLER_ID`.
- GoodCaller triggers zero security threats.
- VillainCaller reliably triggers the intended profile-mismatch rule(s).
- Findings flow through the real `SecurityEventBus`.
- Findings appear in the encrypted audit trail.
- Launch audit executes reliably.
- Manual `Run Security Check` uses the exact same audit engine.
- No fake threat-event path is presented as real detection.
- No enforcement is claimed or performed.
- VPN/network work has not been expanded into Phase 3.
- No unsupported claim is made about observing another app's actual media reads or package-query calls.

---

# IMPLEMENTATION DISCIPLINE

Work sequentially:

`PHASE 1 → VERIFY → PHASE 2 → VERIFY → PHASE 3 → VERIFY`

Do not start the next phase while the previous phase has unresolved build failures.

Prefer the smallest changes compatible with the existing architecture.

Do not perform unrelated cleanup.

Do not rewrite working security foundation code for stylistic reasons.

Do not delete deferred components such as AccessibilityService or NotificationListener unless explicitly instructed.

Do not add cloud/backend dependencies.

Do not add analytics.

Do not add voice.

Do not add adaptive/ML detection.

Do not implement autonomous assistant behaviour.

Do not implement Device Owner enforcement yet.

Do not implement panic/safe mode yet.

Do not implement a full VPN stack yet.

---

# REQUIRED CHECKPOINT REPORTS

After each phase, append a section to:

`temp/THRAKSHA_DEMO_PHASES_1_3_PROGRESS.md`

For every phase record:

- phase completed;
- files created;
- files modified;
- architecture decisions made;
- build result;
- tests executed;
- tests passed/failed;
- runtime checks performed;
- remaining uncertainties;
- known limitations;
- exact next step.

At the end of Phase 3, include:

## FINAL IMPLEMENTATION SUMMARY

Document the real end-to-end runtime flow using actual classes:

`Launch`
→ `SecurityAuditEngine`
→ `AppInventory`
→ `AppType/Baseline`
→ `Verified Rulepack`
→ `RuleEngine`
→ `SecurityEventBus`
→ `AuditLog`
→ `Dashboard`

Also list anything that diverged from this implementation guide and explain why.

---

# STOP CONDITIONS

Stop and report instead of improvising if:

- existing encrypted-store architecture would need destructive replacement;
- existing audit-chain format would need to be abandoned;
- private signing material would need to enter APK/source control;
- Android APIs do not support a requested capability;
- package visibility cannot be achieved through the targeted decoy configuration;
- the repository cannot build after a phase and the cause cannot be isolated;
- implementation would require pretending simulated behaviour is real OS observation;
- an architectural decision would materially affect later enforcement/network phases.

The priority is a **technically credible and repeatable demo**, not maximum feature count.

At the end of this run, Thraksha should have its first genuine security brain:

**See the controlled apps → understand their role → evaluate their observable security profile → detect VillainCaller → leave GoodCaller alone → record exactly why.**