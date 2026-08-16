

# THRAKSHA — PHASE 12 IMPLEMENTATION GUIDE

## 12A RELEASE HARDENING → 12B REAL-DEVICE SOAK → 12C PRIVATE-ALPHA READINESS

## 1. PURPOSE

Phase 12 converts the verified Phase 11B investor-demo build into a **private-alpha release candidate suitable for cautious installation on an everyday Android phone**.

This is NOT a feature-development phase.

Do not add:

* new malware engines;
* new automation routines;
* new AI features;
* new security heuristics;
* new cloud services;
* new UI concepts.

The objective is to establish that the existing product:

* can be built as a controlled release candidate;
* does not contain development secrets/tooling;
* requests only justified permissions;
* handles failures safely;
* installs/upgrades/uninstalls cleanly;
* survives realistic Android lifecycle conditions;
* does not leave device state modified;
* can scan real installed applications safely;
* can run local AI and automation without requiring development infrastructure;
* is reasonable for a private real-world pilot.

---

# 2. BASELINE

Treat Phase 11B as frozen.

Do not rewrite:

* scanner;
* evidence engine;
* ThreatPack;
* Rulepack;
* Network Guard;
* User ACT;
* audit-chain architecture;
* Device Owner enforcement;
* AutomationIntent;
* AutomationSafetyPolicy;
* AutomationPlanner;
* AutomationEngine;
* executors;
* snapshot/restore;
* Gemma/LiteRT-LM boundaries;
* Stitch-driven UI architecture.

Phase 11B final verification is the known-good functional/UI baseline.

---

# 3. EXECUTION MODEL

Claude must create and maintain a task list:

### TASK 12A

Release & Safety Hardening

### TASK 12B

Clean-device / Real-world Soak Verification

### TASK 12C

Private Alpha Readiness Decision

Work **sequentially**.

Do NOT enter 12B until 12A passes.

Do NOT enter 12C until 12B passes.

If a gate fails:

**STOP, document the blocker, preserve evidence, do not simulate PASS.**

---

# TASK 12A — RELEASE & SAFETY HARDENING

## 4. 12A OBJECTIVE

Establish whether the current application can become a safe private-alpha release candidate without development-time dependencies or hidden risk.

Create:

`temp/phase12/12a_release_hardening/`

---

# 5. RELEASE BUILD AUDIT

Inspect the current Gradle/build configuration.

Determine:

* debug vs release differences;
* signing configuration;
* release build type;
* debuggable state;
* minification/resource shrinking;
* ProGuard/R8 behavior;
* native libraries;
* ABI packaging;
* versionCode/versionName;
* package/application ID;
* build-time constants;
* test hooks;
* developer flags.

Create:

`PHASE12_RELEASE_BUILD_AUDIT.md`

Do not enable minification blindly if it breaks LiteRT-LM or reflective Android components.

---

# 6. RELEASE APK

Produce a controlled **release candidate APK**.

Do not use debug signing as the final private-alpha identity unless explicitly documented as temporary.

If a secure release signing identity already exists, use it appropriately.

If not, STOP before inventing or embedding signing credentials.

Document:

* APK filename;
* SHA-256;
* byte size;
* signer certificate fingerprint;
* build timestamp;
* version;
* ABI contents;
* debuggable status.

Create:

`PHASE12_RELEASE_ARTIFACT.md`

---

# 7. MANIFEST HARDENING

Audit merged release manifest.

Review every:

* permission;
* service;
* receiver;
* provider;
* activity;
* intent filter;
* `exported`;
* foreground-service type;
* VPN service;
* DeviceAdmin receiver;
* notification listener;
* special access;
* package query.

For each permission/capability classify:

* REQUIRED;
* OPTIONAL;
* DEV/TEST ONLY;
* REMOVE.

Remove unnecessary production permissions only when evidence supports removal.

Do not break verified functionality simply to reduce permission count.

Create:

`PHASE12_MANIFEST_PERMISSION_AUDIT.md`

---

# 8. EXPORTED COMPONENT AUDIT

For every exported component document:

* why it must be exported;
* accepted intents;
* permissions protecting it;
* externally supplied inputs;
* validation behavior;
* whether export can be disabled.

Unexpected exported components are blockers.

---

# 9. DEV/TEST TOOLING REMOVAL

Verify the release APK does NOT contain:

* Stitch API key;
* Stitch MCP endpoint credentials;
* MCP proxy;
* Claude config;
* temp docs;
* evaluation datasets unless intentionally shipped;
* screenshots;
* ADB-only utilities;
* test hooks;
* fake-failure hooks;
* developer diagnostics not intended for private alpha;
* signing private keys;
* raw threat-pack signing private key;
* internal research documents.

`tools/stitch_mcp_proxy.py` must remain development-only.

---

# 10. SECRET SCAN

Scan:

* source;
* Gradle;
* resources;
* assets;
* manifest;
* generated BuildConfig;
* native strings where practical;
* built APK.

Look for:

* API keys;
* OAuth secrets;
* private keys;
* passwords;
* bearer tokens;
* MCP credentials;
* developer paths;
* personal identifiers.

Do not report only source-tree findings; inspect the actual release APK too.

Create:

`PHASE12_SECRET_AUDIT.md`

---

# 11. NETWORK EGRESS AUDIT

Inventory every network-capable production code path.

Classify:

* required security/network functionality;
* local-only;
* user initiated;
* unexpected.

Explicitly verify the local-AI path has no cloud fallback.

Confirm no Stitch/Claude/Google model-development endpoints are part of runtime operation.

Document:

`PHASE12_NETWORK_EGRESS_AUDIT.md`

---

# 12. DATA / STORAGE / PRIVACY AUDIT

Document what Thraksha stores:

* scan results;
* evidence;
* audit events;
* automation snapshots;
* active routine state;
* AI-related metadata;
* model file;
* settings.

For each:

* storage location;
* encrypted/plain;
* retention;
* deletion behavior;
* uninstall behavior;
* sensitive-data implications.

Verify raw natural-language prompts are not persisted unless explicitly intended.

Create:

`PHASE12_DATA_PRIVACY_AUDIT.md`

---

# 13. MODEL HARDENING

Verify production AI behavior:

* exact approved model identity;
* SHA gate;
* invalid model refused;
* missing model handled;
* load failure handled;
* model cannot call tools/executors;
* structured output only;
* SafetyPolicy final;
* no cloud fallback.

Document model provenance/license issue separately.

Do not claim redistribution clearance if unresolved.

---

# 14. DEPENDENCY / SBOM AUDIT

Create an inventory of production dependencies.

Include:

* Maven dependencies;
* native libraries;
* LiteRT-LM;
* SQLCipher;
* AndroidX/Compose;
* security libraries;
* any bundled third-party code.

Generate an SBOM or equivalent dependency inventory where practical.

Record:

* package;
* version;
* license;
* source;
* known purpose;
* whether shipped.

Create:

`PHASE12_DEPENDENCIES_SBOM.md`

Do not assume transitive dependency licensing is covered by the parent project.

---

# 15. OPEN-SOURCE / LICENSE REVIEW

Review:

* Gemma/Gemma 4 E2B provenance;
* LiteRT-LM;
* ThreatPack source licenses;
* Rulepack assets;
* third-party code;
* notices.

Separate:

**technical readiness**

from:

**commercial redistribution/legal readiness**.

Private alpha may proceed with unresolved redistribution review only if the model is developer-provisioned and not redistributed in the APK, and this limitation is documented.

---

# 16. FAILURE-MODE TESTING

Test on real device:

* model missing;
* corrupt model;
* model load error;
* scanner partial failure;
* ThreatPack invalid;
* Rulepack invalid;
* audit verification failure;
* denied DND access;
* denied Modify System Settings;
* notification permission denied;
* Network Guard unavailable;
* process killed during active routine;
* process killed during restore;
* reboot while routine active;
* low-storage condition where practical;
* app update during inactive state;
* app update with persisted state where safe.

Never leave the phone altered after testing.

---

# 17. AUTOMATION SAFETY

Re-prove:

`START`

→ snapshot

→ execute

→ verify

→ active

→ restore

→ verify restoration.

Test:

* Meeting;
* Focus;
* Driving;
* cancel before START;
* second routine while active;
* failed required action;
* rollback;
* manual STOP & RESTORE;
* expiry;
* process recovery.

Confirm no model inference can bypass explicit START.

---

# 18. SCANNER SAFETY

Run real scan.

Verify:

* no app modification merely from scanning;
* no automatic quarantine of arbitrary real apps;
* partial visibility represented honestly;
* static capability ≠ observed behavior;
* ThreatPack matches remain separate from heuristic findings;
* real-app findings remain advice/review unless policy explicitly supports stronger action.

---

# 19. BATTERY / THERMAL / PERFORMANCE

Measure:

* idle Thraksha;
* full scan;
* Network Guard;
* model loaded;
* repeated inference;
* active automation;
* background state.

Record:

* battery impact where practical;
* thermal status;
* memory;
* crashes;
* ANRs;
* excessive wakeups/services.

Private alpha does not require perfect optimization, but pathological behavior is a blocker.

---

# 20. 12A REGRESSION

Run:

* clean release/debug builds as relevant;
* all unit tests;
* S20 FE instrumented tests;
* Device Owner AVD tests;
* scanner;
* evidence;
* ACT;
* ThreatPack;
* Rulepack;
* Network Guard;
* audit chain;
* Meeting/Focus/Driving;
* rollback/recovery;
* AI;
* hostile prompts;
* offline inference;
* deterministic fallback;
* redesigned UI paths.

---

# 21. 12A PASS GATE

12A passes only if:

* release artifact is reproducible;
* no production secret exists;
* no unexpected exported component;
* permissions justified;
* dev/test tooling absent from runtime;
* network egress understood;
* local AI boundary intact;
* storage/privacy behavior understood;
* failure modes safe;
* automation restores reliably;
* scanner is non-destructive;
* full regressions green;
* legal/provenance limitations documented.

If PASS:

continue automatically to 12B.

---

# TASK 12B — CLEAN-DEVICE / REAL-WORLD SOAK VERIFICATION

## 22. 12B OBJECTIVE

Test the actual release candidate like a normal installed application rather than a development build.

Do not rely on ADB for normal runtime behavior.

ADB may observe/verify state during testing but may not provide runtime functionality.

Create:

`temp/phase12/12b_soak/`

---

# 23. CLEAN INSTALL

On the S20 FE:

1. record baseline device state;
2. remove previous Thraksha installation where safe;
3. verify expected app-local data removal;
4. install the release candidate fresh;
5. launch normally;
6. perform onboarding through real UI;
7. grant only required demo/private-alpha accesses;
8. provision model using the approved private-alpha delivery mechanism;
9. verify model SHA through the app.

Document every development-only step separately.

---

# 24. FRESH-INSTALL TEST

Verify from a clean install:

* launch;
* branding;
* themes;
* permissions;
* scanner;
* findings;
* Network Guard;
* Ask Thraksha;
* plan preview;
* Meeting;
* Focus;
* Driving;
* restoration;
* audit;
* Settings;
* About/Privacy;
* error states.

No dependency on stale development data.

---

# 25. REBOOT TEST

Reboot device.

Verify:

* app remains stable;
* active/persisted state handled;
* audit intact;
* model found/verified;
* scanner works;
* Network Guard status honest;
* no unexpected background behavior;
* no settings silently changed.

---

# 26. UPGRADE TEST

Install the same/newer release candidate over an existing private-alpha installation.

Verify:

* encrypted data survives when intended;
* audit chain survives;
* active-state recovery behaves safely;
* no schema corruption;
* model location remains valid;
* permissions remain coherent.

---

# 27. UNINSTALL TEST

Uninstall.

Verify:

* app sandbox removed;
* no unexpected persistent routine state;
* Android settings changed by Thraksha are restored before uninstall where possible;
* Device Owner is NOT used on ordinary-phone private-alpha path;
* VPN/service state does not remain misleadingly active;
* model delivery behavior documented.

---

# 28. REAL APP SCAN

Run a real scan of the S20 FE.

Record:

* apps discovered;
* apps scanned;
* partial/unobservable surfaces;
* findings;
* known-threat matches;
* scan duration;
* false-positive examples;
* UI clarity.

Do not tune rules during 12B unless a severe correctness defect requires returning to 12A.

---

# 29. NORMAL-USE SOAK

Run the release candidate through repeated normal usage.

Target:

at least several hours of active verification plus an extended installed period where possible.

Exercise:

* repeated app opening/closing;
* scanning;
* background/foreground;
* AI requests;
* routine start/stop;
* Network Guard;
* reboot;
* screen rotation where applicable;
* permission revocation/regrant;
* model unload/reload.

Track:

* crashes;
* ANRs;
* lost snapshots;
* restore failures;
* battery issues;
* unexpected notifications;
* misleading security states.

---

# 30. PHONE FINAL STATE

At the end of soak:

verify the S20 FE is returned to intended normal state:

* no active routine;
* DND as expected;
* ringer as expected;
* brightness/adaptive mode expected;
* timeout expected;
* no app suspension;
* Network Guard state known;
* model storage known;
* permissions documented.

---

# 31. 12B REPORT

Create:

`PHASE12_REAL_DEVICE_SOAK.md`

Record:

* release APK;
* install method;
* provisioning;
* onboarding;
* scans;
* automation;
* AI;
* reboots;
* upgrades;
* uninstall/reinstall;
* performance;
* failures;
* recovery;
* final device state;
* screenshots.

---

# 32. 12B PASS GATE

12B passes only if:

* clean install works;
* normal UI onboarding works;
* scanning works;
* no destructive side effect;
* automation restores correctly;
* AI remains bounded/offline;
* reboot/recovery safe;
* no serious crash/ANR;
* uninstall/reinstall behavior understood;
* no development tool is required for runtime behavior except documented model provisioning;
* final device state clean.

If PASS:

continue to 12C.

---

# TASK 12C — PRIVATE ALPHA / EVERYDAY-PHONE READINESS

## 33. 12C OBJECTIVE

Make a formal engineering decision:

> Is this build reasonable for the owner to install on an everyday Android phone as a private experimental alpha?

This is not commercial certification.

Create:

`temp/phase12/12c_readiness/`

---

# 34. RISK REGISTER

Create:

`PHASE12_PRIVATE_ALPHA_RISK_REGISTER.md`

For every remaining limitation classify:

* LOW;
* MEDIUM;
* HIGH;
* BLOCKER.

Include:

* false positives;
* CLARIFY behavior;
* ~11.5 s AI latency;
* developer-provisioned model;
* model licensing;
* Android API visibility limitations;
* Device Owner limitations;
* scanner coverage;
* Network Guard scope;
* battery/performance;
* permissions;
* unsupported devices/API levels.

---

# 35. EVERYDAY-PHONE OPERATING MODE

Private alpha on personal phone must default to:

**Advice Mode / least privilege.**

Do NOT provision Device Owner on an everyday personal device as part of this gate.

Automatic containment of arbitrary real apps remains disabled.

User-controlled actions must remain explicit.

---

# 36. PRIVATE-ALPHA INSTALL CHECKLIST

Create a concise checklist:

Before install:

* backup important device data;
* verify APK SHA;
* verify signer;
* confirm compatible Android version;
* ensure sufficient storage for model;
* understand required special accesses.

After install:

* grant permissions deliberately;
* run initial scan;
* inspect findings;
* test one Meeting routine;
* restore;
* verify audit.

Emergency recovery:

* Stop & Restore;
* disable Network Guard;
* revoke special access;
* uninstall.

---

# 37. RELEASE NOTES

Create:

`PHASE12_PRIVATE_ALPHA_RELEASE_NOTES.md`

Include:

* what works;
* what is experimental;
* known limitations;
* unsupported scenarios;
* privacy/local AI statement;
* model provisioning requirement;
* safe operating mode;
* recovery instructions.

Avoid marketing exaggeration.

---

# 38. GO / NO-GO REPORT

Create:

`PHASE12_PRIVATE_ALPHA_READINESS.md`

Final verdict must be exactly one:

### PASS — PRIVATE ALPHA READY

or

### FAIL — NOT READY

If PASS, state:

> Thraksha is reasonable for controlled installation on the owner's everyday Android device as a private experimental alpha in Advice Mode, subject to the documented limitations.

Do NOT call it production-ready.

---

# 39. MASTER PHASE 12 REPORT

Create:

`temp/THRAKSHA_DEMO_PHASE_12_PROGRESS.md`

Summarize:

* 12A result;
* release artifact;
* manifest/permissions;
* secrets;
* dependency/SBOM;
* network/privacy;
* resilience;
* 12B clean install;
* soak;
* scanning;
* automation;
* AI;
* regressions;
* 12C risk register;
* final readiness verdict;
* git status.

---

# 40. DOCUMENT STRUCTURE

Use:

```text
temp/
├── THRAKSHA_DEMO_IMPLEMENTATION_PHASE_12_ABC.md
├── THRAKSHA_DEMO_PHASE_12_PROGRESS.md
└── phase12/
    ├── 12a_release_hardening/
    ├── 12b_soak/
    ├── 12c_readiness/
    └── screenshots/
```

Keep Phase 12 evidence structured.

---

# 41. NO AUTOMATIC COMMIT

Nothing is committed automatically.

At completion report:

1. 12A PASS/FAIL
2. 12B PASS/FAIL
3. 12C PASS/FAIL
4. release APK identity/SHA/signer
5. unit/instrumented results
6. real-scan result
7. soak findings
8. unresolved risks
9. device final state
10. exact recommendation for everyday-phone installation
11. git status

---

# 42. FINAL PRINCIPLE

The question Phase 12 must answer is not:

> “Does the demo look good?”

That was Phase 11.

The question is:

> **“Can this exact build be installed on a normal personal Android phone, operate without development machinery, scan real applications without harming them, perform bounded automation safely, recover correctly, and leave the device in a known state?”**

Only a demonstrated **YES** earns:

**PRIVATE ALPHA READY.**
