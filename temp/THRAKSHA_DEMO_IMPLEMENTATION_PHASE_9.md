
This phase should make automation **real before AI enters the picture**. The principle is the same one that has worked well for security: Thraksha must distinguish between *requesting* an action and *proving* the device actually changed.

# THRAKSHA DEMO — PHASE 9 IMPLEMENTATION GUIDE

## DETERMINISTIC AUTOMATION ENGINE + ROUTINES + VERIFIED RESTORATION

## 1. PURPOSE

Phase 9 builds the automation half of the Thraksha demo.

The objective is to prove that Thraksha can safely orchestrate multiple Android device actions as a single routine while:

* understanding current device state;
* determining what it is actually authorized to control;
* creating an explicit execution plan;
* executing supported actions;
* verifying resulting Android state;
* preserving previous state;
* restoring that state later;
* handling partial failures honestly;
* recording actions in the existing tamper-evident audit architecture.

No AI/LLM is required in Phase 9.

The automation engine must be deterministic.

Target architecture:

`Routine Request`

→ `Automation Planner`

→ `Capability/Authority Check`

→ `Preflight`

→ `State Snapshot`

→ `Execution Plan`

→ `Action Executors`

→ `Verify`

→ `Routine Result`

→ `Audit`

and later:

`Restore`

→ `Restore Snapshot`

→ `Verify`

→ `Audit`.

---

# 2. KNOWN-GOOD BASELINE

Phases 1–8.1 are complete.

Preserve:

* encrypted SQLCipher storage;
* Android Keystore;
* hash-chained AuditLog;
* SecurityEventBus;
* PolicyEngine;
* privilege detection;
* Advice Mode;
* Full Power;
* Device Owner enforcement;
* generalized real-device scanner;
* ThreatPack;
* Rulepack;
* DECLARED / GRANTED / OBSERVED evidence;
* User ACT;
* Network Guard;
* GoodCaller;
* VillainCaller.

Phase 8.1 established the important pattern:

`request → validate → authority → execute → re-query OS → result`

Reuse that philosophy for automation. 

Do not unnecessarily rewrite security components.

---

# 3. FIRST TASK — AUTOMATION CAPABILITY AUDIT

Do NOT begin by implementing Meeting/Focus/Driving Mode.

First determine what Android genuinely permits Thraksha to control.

Create:

`temp/PHASE9_AUTOMATION_CAPABILITY_MATRIX.md`

Investigate each candidate action on:

1. Samsung S20 FE / current Thraksha privilege;
2. Device Owner AVD;
3. relevant Android API level.

For every capability record:

* capability/action;
* Android API;
* required permission;
* special access/role if applicable;
* Normal support;
* Device Admin support;
* Device Owner support;
* direct or user-assisted;
* current-state readability;
* resulting-state verifiability;
* reversibility;
* limitations;
* whether suitable for investor demo.

Do not assume Device Owner grants an API capability unless Android documentation/runtime proves it.

---

# 4. CANDIDATE AUTOMATION CAPABILITIES

Investigate at minimum:

* Do Not Disturb / interruption policy;
* ringer mode;
* notification-related controls;
* media/ring/alarm volume where appropriate;
* screen brightness;
* screen timeout where legitimately writable;
* launching an application;
* opening Android settings;
* opening app-specific settings;
* keeping/turning screen state where supported;
* orientation if legitimately controllable;
* Device Owner restrictions relevant to safe automation;
* network guard enable/disable feasibility;
* app suspension only as controlled/demo capability;
* other low-risk reversible Android settings already available to Thraksha.

Investigate first.

Implement only capabilities that are technically honest and safe.

---

# 5. PROHIBITED AUTOMATION

Do NOT use:

* root;
* shell/ADB as production runtime behavior;
* hidden/private Android APIs;
* accessibility click automation merely to fake control;
* UI coordinate tapping;
* notification interception as arbitrary automation control;
* destructive DevicePolicyManager APIs;
* factory reset;
* wipe;
* credential changes;
* silent app installation/uninstallation;
* arbitrary app killing;
* malware-like persistence;
* bypasses of Android permission/consent systems.

ADB may be used for development/testing/verification only.

If Android requires the user to change something manually, represent it as:

`USER ACTION REQUIRED`.

---

# 6. AUTOMATION DOMAIN MODEL

Create a clean automation domain package.

Suggested concepts:

`AutomationRoutine`

`AutomationAction`

`AutomationPlan`

`AutomationSnapshot`

`AutomationActionResult`

`AutomationRunResult`

`AutomationCapability`

`AutomationCapabilityState`

`AutomationExecutionStatus`

`AutomationRestoreResult`

Avoid coupling routine definitions directly to Android framework APIs.

---

# 7. ACTION STATUS MODEL

Use explicit statuses.

Suggested:

* PLANNED
* SUPPORTED
* USER_ACTION_REQUIRED
* EXECUTING
* ACTED
* FAILED
* UNSUPPORTED
* SKIPPED
* CANCELLED
* RESTORED
* RESTORE_FAILED

Never report ACTED merely because an Android API was invoked.

ACTED requires post-action verification where verification is technically available.

---

# 8. ACTION EXECUTOR CONTRACT

Create a consistent executor interface.

Conceptually:

`canExecute()`

`readCurrentState()`

`execute(targetState)`

`verify(targetState)`

`restore(snapshotState)`

`verifyRestored(snapshotState)`

Each executor owns one narrow Android capability.

Examples:

`DndExecutor`

`RingerExecutor`

`BrightnessExecutor`

`ScreenTimeoutExecutor`

`LaunchAppExecutor`

`SettingsNavigationExecutor`

Do not create one giant automation service containing every Android API.

---

# 9. SNAPSHOT BEFORE MUTATION

This is a critical invariant.

Before changing a reversible setting:

**read its current state and save it.**

Example:

Before Meeting Mode:

`ringer = VIBRATE`

`brightness = 62%`

`DND = OFF`

If Meeting Mode changes those values, restore must return to:

`VIBRATE`

`62%`

`OFF`

not to hardcoded defaults.

Never assume the user's original state.

---

# 10. AUTOMATION SNAPSHOT

`AutomationSnapshot` should contain only state Thraksha intends to modify.

Include:

* routine/run ID;
* timestamp;
* capability;
* previous state;
* whether state was readable;
* restoration eligibility.

Do not capture unrelated device state.

Prefer persistent snapshot storage so app/process restart does not automatically lose restoration information.

Use existing encrypted storage where appropriate.

Do not weaken audit/database integrity.

---

# 11. PLAN BEFORE EXECUTION

Every routine must become an explicit `AutomationPlan` before execution.

Example:

**Meeting Mode**

1. Enable DND
2. Adjust ringer
3. Adjust brightness
4. Launch/open selected meeting app

Before execution, classify every action:

`SUPPORTED`

`USER ACTION REQUIRED`

`UNSUPPORTED`

The UI must be able to show the plan before running it.

Do not silently skip unsupported actions.

---

# 12. PREFLIGHT

Before executing:

* determine current privilege;
* verify required permission/special access;
* verify current Android capability;
* ensure executor exists;
* ensure current state can be read if restoration is promised;
* detect conflicting active routine;
* calculate resulting plan.

If a required capability is unavailable, decide honestly between:

`PARTIAL PLAN`

or:

`CANNOT RUN`.

Do not fake completeness.

---

# 13. EXECUTION ORDER

Execute actions deterministically.

For each:

`read/snapshot`

→ `execute`

→ `verify`

→ record result

→ continue/stop according to routine policy.

Define whether an action is:

**REQUIRED**

or

**OPTIONAL**.

Required failure may stop the routine.

Optional failure may produce a partial result.

Do not leave this behavior implicit.

---

# 14. TRANSACTION-LIKE BEHAVIOR

Automation is not a real database transaction, but treat it similarly.

If:

Action 1 succeeds

Action 2 succeeds

Action 3 required action fails

then do not simply leave the device half-modified without explanation.

Support a routine policy such as:

`ROLLBACK_ON_REQUIRED_FAILURE`

where already-applied reversible actions are restored from snapshot.

Record rollback results individually.

If rollback itself fails:

`PARTIAL / RESTORE FAILED`

must be visible.

---

# 15. ROUTINE LIFECYCLE

Recommended states:

* IDLE
* PREFLIGHT
* READY
* EXECUTING
* ACTIVE
* PARTIAL
* FAILED
* RESTORING
* RESTORED

Expose through `StateFlow`.

Compose must observe the real engine state.

Do not use UI-local booleans as authoritative automation state.

---

# 16. MEETING MODE

Implement a deterministic built-in Meeting routine using only capabilities proven by the capability audit.

Desired product intent:

> Prepare the phone for a meeting and reduce interruptions.

Potential actions, only if supported:

* DND/interruption policy;
* ringer adjustment;
* sensible brightness adjustment;
* optional meeting-app launch;
* optional screen timeout adjustment.

Do not force actions merely to make the routine look larger.

A two-action routine that genuinely works is better than six fake actions.

---

# 17. FOCUS MODE

Desired intent:

> Reduce distractions for a focused work session.

Potential actions:

* DND;
* ringer;
* brightness/display configuration;
* open a chosen productivity app;
* controlled app suspension only if explicitly safe and appropriate.

Do not suspend arbitrary real applications automatically.

If app-level restriction requires unsupported authority:

`USER ACTION REQUIRED`.

---

# 18. DRIVING MODE

Desired intent:

> Configure the phone for reduced interaction while driving.

Safety is paramount.

Potential actions only if legitimate:

* DND/interruption policy;
* appropriate ringer behavior;
* launch navigation app;
* increase/readjust brightness if appropriate;
* open relevant settings.

Do NOT implement automation that encourages interaction while driving.

Do NOT fake Android Auto integration.

Do NOT claim driving detection unless genuinely implemented.

For Phase 9, Driving Mode is **user-started**, not automatically inferred from movement.

---

# 19. CUSTOM ROUTINE

Add a small deterministic custom routine builder if practical after built-ins work.

User selects from supported actions.

Example:

`Custom Routine`

* Enable DND
* Set brightness
* Launch app

Do not build a full Tasker competitor.

The purpose is to prove the engine is composable rather than hardcoded specifically for three named routines.

---

# 20. ROUTINE DURATION

Meeting and Focus routines should support a duration.

Example:

`30 minutes`

`1 hour`

`Until I stop`

Do not depend on AI.

The engine should retain enough state to know when restoration is due.

For the demo, if reliable automatic duration expiry requires lifecycle/background scheduling work, implement it using appropriate Android mechanisms rather than an in-memory timer that dies with the process.

Document the chosen mechanism.

---

# 21. MANUAL STOP

Every active reversible routine must expose:

**STOP & RESTORE**

This should:

→ load the routine snapshot

→ restore each changed capability

→ verify restoration

→ produce structured result

→ audit.

Do not merely stop the routine state while leaving device settings changed.

---

# 22. PROCESS DEATH / APP RESTART

Automation state must survive realistic app lifecycle events where practical.

If Thraksha is killed while Meeting Mode is active, reopening the app should not forget that it changed device state.

Persist:

* active routine;
* run ID;
* snapshot;
* changed actions;
* restoration state.

On restart:

→ reconstruct ACTIVE state

or

→ clearly report recovery required.

Do not silently lose restoration responsibility.

---

# 23. CONFLICTING ROUTINES

Do not allow Meeting + Focus + Driving to independently overwrite the same settings without a defined model.

For the demo, prefer:

**one active routine at a time.**

If another routine is started:

show:

`Another routine is active.`

Offer:

`STOP & RESTORE CURRENT ROUTINE`

before starting the next.

This avoids nested snapshot complexity.

---

# 24. USER-ASSISTED ACTIONS

Some Android operations may require user interaction.

Represent them as first-class plan items:

`USER ACTION REQUIRED`

Example:

`Grant Notification Policy Access`

The routine should not claim complete execution until required user-assisted prerequisites are satisfied.

Where possible, deep-link to the exact Android settings screen.

After returning, re-check the state.

---

# 25. PERMISSION/SPECIAL-ACCESS ONBOARDING

If DND or another capability requires special access:

show a concise explanation:

**Why Thraksha needs this**

**What it enables**

**How to revoke it**

Then open the legitimate Android settings screen.

Do not repeatedly nag after denial.

---

# 26. AUTOMATION POLICY BOUNDARY

Automation should not bypass existing security architecture.

However, do not force ordinary automation actions into malware-specific PolicyEngine semantics if that creates an unnatural design.

Create a clear boundary:

`AutomationPlanner`

→ `AutomationSafetyPolicy`

→ executor.

Reuse shared concepts such as:

* authority;
* verification;
* ActionResult;
* audit;

without conflating:

`user wants DND`

with:

`security threat requires containment`.

Keep automation and security domains distinct but interoperable.

---

# 27. AUTOMATION SAFETY POLICY

Create deterministic safety rules.

Examples:

* destructive actions prohibited;
* only allowlisted automation capabilities executable;
* no arbitrary shell command;
* no arbitrary intent URI supplied by future AI;
* no arbitrary package suspension;
* no unvalidated numeric setting values;
* brightness clamped to safe valid range;
* duration bounded;
* package launch target validated;
* restoration required for reversible mutations.

This becomes particularly important for Phase 10 when AI generates routine intents.

---

# 28. FUTURE AI BOUNDARY

Design Phase 9 so Phase 10 can submit only structured requests.

Future AI must NOT call Android executors directly.

Prepare an input model conceptually like:

`AutomationIntent`

with fields such as:

* routine type;
* duration;
* optional target app;
* allowed preferences.

Phase 9 should validate this structure before generating an `AutomationPlan`.

Do NOT integrate an LLM now.

---

# 29. ROUTINE PREVIEW UI

Before starting a routine, show:

**MEETING MODE**

Planned actions:

`Enable Do Not Disturb — supported`

`Adjust brightness — supported`

`Open meeting app — supported`

`Restrict distracting apps — user action required`

Then:

**START ROUTINE**

The user should know what Thraksha intends to change before execution.

---

# 30. ACTIVE ROUTINE UI

While active show:

**MEETING MODE ACTIVE**

* DND — ACTED ✓
* Brightness — ACTED ✓
* Meeting app — OPENED ✓

If duration exists:

`Ends in 43 min`

Then:

**STOP & RESTORE**

Do not show a green success check for an unverified action.

---

# 31. RESTORE UI

After stopping:

**MEETING MODE RESTORED**

* DND → previous state ✓
* Brightness → previous 62% ✓
* Ringer → previous state ✓

If something fails:

**PARTIAL RESTORE**

and identify exactly what remains changed.

---

# 32. AUDIT INTEGRATION

Automation must use the existing tamper-evident audit architecture.

Record significant events such as:

* routine requested;
* plan generated;
* routine started;
* action attempted;
* action verified;
* action failed;
* routine active;
* restore requested;
* state restored;
* restore failed.

Do not log every internal implementation detail.

Audit should answer:

> What did Thraksha change, why, when, and did it successfully restore it?

---

# 33. SECURITY + AUTOMATION AUDIT

Keep one coherent audit history.

Security and automation events should be distinguishable by category/source.

Example:

`AUTOMATION — Meeting Mode started`

`AUTOMATION — DND enabled and verified`

`SECURITY — Network threat detected`

`AUTOMATION — Meeting Mode restored`

Do not create a completely separate untrusted automation log.

---

# 34. SECURITY INTERACTION

An active automation routine must not disable security monitoring.

Meeting/Focus/Driving Mode must not:

* stop real-device scanning infrastructure;
* weaken ThreatPack verification;
* disable audit;
* disable Network Guard unexpectedly;
* change security privilege;
* suppress critical Thraksha security state internally.

If a routine changes notification/DND behavior, ensure Thraksha's own security notification behavior is understood and documented.

---

# 35. TESTS — PLANNER

Add unit tests for:

* Meeting plan;
* Focus plan;
* Driving plan;
* custom plan;
* unsupported capability;
* user-assisted capability;
* required vs optional action;
* invalid duration;
* invalid target package;
* conflicting routine;
* safety-policy rejection.

Plans must be deterministic.

---

# 36. TESTS — SNAPSHOT/RESTORE

Test:

* state captured before mutation;
* snapshot contains only changed capabilities;
* restore uses actual previous value;
* pre-existing DND restored correctly;
* pre-existing vibrate restored correctly;
* brightness restored correctly;
* failed action does not corrupt snapshot;
* partial routine restores successful prior actions;
* process reconstruction loads active snapshot;
* second restore is idempotent where possible.

---

# 37. TESTS — EXECUTORS

For each executor:

* capability detection;
* current-state read;
* successful action;
* verification success;
* verification failure;
* unsupported state;
* permission missing;
* restore;
* restore verification;
* exception handling.

Do not mock verification so heavily that tests prove only mocks.

Use instrumented tests for actual Android state transitions where safe.

---

# 38. DEVICE TEST — S20 FE

Use the connected Samsung.

Before testing, record actual current settings.

Run Meeting Mode.

Verify each claimed action through Android state, not UI state alone.

Then:

**STOP & RESTORE**

Verify the exact original settings return.

Repeat for Focus Mode.

Run the safe supported portion of Driving Mode.

Test:

* permission/special-access denial;
* user-assisted path;
* repeated start;
* repeated stop;
* app restart while routine active if practical;
* restoration after failure.

Leave the phone in its original state after tests.

---

# 39. DEVICE OWNER AVD

Use `thraksha_do` for Full Power automation verification.

Test the same routines.

Then test any additional Device Owner-only automation capability that the capability audit proves useful and safe.

Do not add privileged actions solely because Device Owner makes them possible.

The investor demo needs useful automation, not maximum policy control.

Restore emulator state after tests.

---

# 40. PERFORMANCE

Measure:

* preflight latency;
* plan generation;
* snapshot latency;
* action execution;
* verification;
* total routine activation time;
* restore time.

Routine activation should feel immediate.

Record results in the progress report.

---

# 41. NOTIFICATIONS

While a timed routine is active, a persistent/appropriate notification may show:

`Meeting Mode active`

`Tap to view or stop`

When restoration occurs:

`Meeting Mode ended — previous settings restored`

Use calm product language.

Do not create notification spam.

---

# 42. INVESTOR DEMO ROUTINE

Choose one **hero automation** after capability testing.

Most likely:

**Meeting Mode**

The final sequence should be visibly demonstrable:

Before:

phone has normal state.

Tap:

**Meeting Mode — 30 min**

Preview:

`Thraksha will change 3 settings.`

Start.

Then visibly prove:

`DND changed`

`ringer changed`

`brightness changed`

or whichever capabilities are genuinely supported.

Show:

**3/3 actions verified**

Then:

**STOP & RESTORE**

Show:

**3/3 previous states restored**

This should be the central Phase 9 proof.

---

# 43. DO NOT OPTIMIZE FOR NUMBER OF ACTIONS

A routine with three real verified actions is stronger than one with ten questionable actions.

Prioritize:

1. real Android state change;
2. verification;
3. restoration;
4. reliability;
5. understandable investor value.

Only then add more capabilities.

---

# 44. SCREENSHOTS

Store under:

`temp/screenshots/`

Capture:

* automation home;
* Meeting Mode preview;
* capability status;
* routine executing;
* routine active;
* verified actions;
* Stop & Restore;
* verified restoration;
* Focus Mode;
* Driving Mode;
* user-action-required case;
* failure/partial state if safely reproducible;
* automation audit entries.

---

# 45. REQUIRED DOCUMENTS

Create:

`temp/PHASE9_AUTOMATION_CAPABILITY_MATRIX.md`

`temp/PHASE9_ROUTINE_VALIDATION.md`

`temp/THRAKSHA_DEMO_PHASE_9_PROGRESS.md`

The progress report must record:

* baseline;
* architecture;
* files created/modified;
* capability audit;
* supported actions;
* unsupported actions;
* special-access requirements;
* routine definitions;
* safety policy;
* snapshot design;
* restoration behavior;
* unit tests;
* instrumented tests;
* S20 FE results;
* Device Owner AVD results;
* timings;
* screenshots;
* audit verification;
* limitations;
* divergences.

---

# 46. FINAL REGRESSION

Before completion:

* clean build all modules;
* full unit suite;
* full instrumented suite;
* S20 FE automation verification;
* Device Owner AVD verification;
* Meeting Mode start/restore;
* Focus Mode start/restore;
* Driving Mode safe path;
* process/lifecycle recovery;
* audit-chain verification;
* ThreatPack verification;
* Rulepack verification;
* Phase 8 real-device scanner regression;
* Phase 8.1 evidence regression;
* User ACT regression;
* Network Guard regression;
* GoodCaller/VillainCaller regression;
* destructive-API safety grep;
* private-key inspection;
* git working-tree inventory.

Do not commit automatically.

---

# 47. ACCEPTANCE CRITERIA

Phase 9 is complete only when:

* automation capabilities are documented from actual Android behavior;
* at least one multi-action routine works end-to-end on the S20 FE;
* Meeting Mode is preferably the hero routine;
* Focus Mode works with supported actions;
* Driving Mode has a safe user-started implementation;
* routine plans are deterministic;
* unsupported actions are shown honestly;
* state is captured before modification;
* every claimed successful action is verified where technically possible;
* STOP & RESTORE returns settings to their actual previous values;
* restoration survives realistic lifecycle behavior;
* partial failure is handled honestly;
* conflicting routines are prevented;
* user-assisted actions deep-link appropriately;
* no ADB/root/private API is used as runtime automation;
* no destructive action exists;
* automation events enter the existing audit chain;
* security monitoring is not weakened;
* Phase 1–8.1 functionality remains passing;
* nothing is automatically committed.

---

# 48. STOP CONDITIONS

STOP and document rather than simulate if:

* Android does not permit a proposed automation action;
* an action requires root/ADB/private APIs at runtime;
* a state cannot be verified but UI would claim ACTED;
* restoration cannot be implemented safely for a mutable setting;
* implementation would overwrite unknown previous user state;
* Meeting/Focus/Driving requires accessibility click automation;
* a routine would weaken Thraksha security;
* Device Owner assumptions differ from actual runtime behavior;
* lifecycle recovery cannot safely determine whether restoration is required;
* existing audit/security architecture regresses.

Reduce the routine scope instead of faking capability.

---

# 49. FINAL PHASE 9 ARCHITECTURE

Target:

`User chooses Meeting Mode`

↓

`AutomationIntent`

↓

`AutomationPlanner`

↓

`AutomationSafetyPolicy`

↓

`Capability Preflight`

↓

`AutomationPlan`

↓

`Snapshot Current Device State`

↓

`Action Executors`

↓

`Android`

↓

`Verify Actual State`

↓

`ACTIVE`

↓

`SecurityEventBus / AuditLog`

Later:

`STOP / duration expires`

↓

`Load Snapshot`

↓

`Restore Previous States`

↓

`Verify Restoration`

↓

`RESTORED`

↓

`AuditLog`

---

# 50. PHASE 9 PRODUCT STORY

At the end of Phase 9, we should be able to demonstrate:

> **“Thraksha doesn't just toggle settings. It understands the current device state, creates a safe automation plan, changes only what is necessary, verifies that Android actually applied those changes, and restores exactly what was there before.”**

Then Phase 10 can safely add the on-device language model:

`Natural language`

→ `structured AutomationIntent`

→ **the exact deterministic Phase 9 engine**

rather than allowing an AI model to directly control Android.

That separation is the most important architectural requirement for what comes next.
