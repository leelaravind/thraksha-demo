THRAKSHA DEMO — PHASE 8.1 IMPLEMENTATION GUIDE
EVIDENCE PROGRESSION + RUNTIME OBSERVATION + USER ACT
1. PURPOSE

Phase 8.1 improves the real-world scanner so Thraksha distinguishes three fundamentally different kinds of evidence:

STAGE 1 — DECLARED
What the application says it may use.

STAGE 2 — GRANTED
What Android currently allows the application to use.

STAGE 3 — OBSERVED
What Thraksha can genuinely prove the application actually did.

The objective is to reduce misleading static-risk flags and make every finding understandable.

The investor-facing model becomes:

DECLARED → GRANTED → OBSERVED → CONTEXT → DECISION → USER ACT

Phase 8 remains the known-good generalized scanner baseline. Do not rewrite it unnecessarily. The current scanner already analyzes real installed apps, fingerprints APKs and signing identities, runs generic/contextual rules, and checks a signed real-world ThreatPack.

2. CORE PRINCIPLE

Never treat:

declared capability

as equivalent to:

used capability.

Never treat:

granted permission

as equivalent to:

observed access.

A capability can exist without being exercised.

Examples:

READ_CONTACTS declared
≠
Contacts accessed

ACCESS_FINE_LOCATION granted
≠
Location used

Every evidence item must explicitly say which stage it belongs to.

3. EVIDENCE MODEL

Create a reusable structured evidence model.

Conceptually:

CapabilityEvidence

Fields should include:

capabilityId
capabilityLabel
evidenceStage
declared
granted
observed
observationSource
observedAt
confidence/reliability
contextAssessment
explanation
limitations

Recommended enum:

EvidenceStage

DECLARED
GRANTED
OBSERVED

Do not encode stages as free-form UI strings.

4. STAGE 1 — DECLARED

Stage 1 comes from static/package metadata.

Examples:

permission requested in manifest;
AccessibilityService declared;
NotificationListenerService declared;
VPN service declared;
DeviceAdminReceiver declared;
overlay capability declared;
exported component;
package-visibility capability.

Display as:

DECLARED

Meaning:

This application declares this capability.

This is not runtime behavior.

Generic risk rules may use declared evidence, but the UI must not imply the app exercised the capability.

5. STAGE 2 — GRANTED

Stage 2 represents capabilities currently available to the application.

For runtime permissions, query Android grant state.

Examples:

Contacts: GRANTED
Location: GRANTED
Camera: DENIED
Microphone: GRANTED

For special-access capabilities where Android exposes state, model them separately and accurately.

Examples may include:

overlay allowed;
notification-listener enabled;
accessibility service enabled;
VPN role active where relevant;
device-admin active.

Do not force all special capabilities into the runtime-permission model.

If grant/enablement state cannot be determined:

UNKNOWN

not:

DENIED.

6. STAGE 3 — OBSERVED

This is the most important requirement.

Stage 3 may only exist where Thraksha has legitimate evidence that an action occurred.

Do NOT infer OBSERVED from Stage 1 + Stage 2.

Examples already supported:

Network

Phase 7 genuinely observes VillainCaller sending a packet through the TUN.

That qualifies as:

OBSERVED → outbound network attempt

because the packet was actually intercepted.

Other capabilities

Investigate what Android APIs can legitimately expose regarding recent app permission use / access indicators / app operation history for the target API and privilege level.

Do not assume availability.

Before implementation, create:

temp/PHASE8_1_RUNTIME_OBSERVABILITY.md

Document for each target capability:

Contacts
Location
Camera
Microphone
Media
Accessibility
Overlay
Notification listener
Network

whether Thraksha can determine:

declared;
granted/enabled;
actual/recent usage;
timestamp;
foreground/background context;
API restrictions;
privilege requirements.

If actual usage cannot be proven, Stage 3 must show:

NOT OBSERVABLE

or:

NO OBSERVATION AVAILABLE

Do not synthesize evidence.

7. RUNTIME OBSERVATION SOURCE

If Android provides a legitimate app-operation/permission-use API suitable for the demo, isolate it behind a dedicated component.

Conceptual:

RuntimeObservationProvider

Input:

packageName

Output:

structured observations such as:

capability
observation time
source
reliability
available/unavailable

Do not put Android observation logic in Compose or RuleEngine.

If only partial APIs are available, implement only what can be proven.

8. OBSERVATION RELIABILITY

Create explicit observation reliability.

Suggested:

VERIFIED
RECENT_SYSTEM_SIGNAL
INDIRECT
UNAVAILABLE

For the investor demo, only VERIFIED or suitably reliable system-observed signals should produce:

OBSERVED ✓

Anything weaker should be explained.

Do not call an indirect heuristic "observed."

9. CONTEXT ASSESSMENT

After evidence stages are assembled, evaluate context.

Conceptual:

ContextAssessment

EXPECTED
UNUSUAL
SUSPICIOUS
UNKNOWN

This must remain deterministic.

Examples:

Wearable companion:

Location declared

Location granted
no suspicious observed use
known wearable context

→ EXPECTED or UNKNOWN

not automatically HIGH-RISK.

Unknown flashlight app:

Microphone declared

Microphone granted
verified microphone usage
no relevant app baseline

→ UNUSUAL / REVIEW

Do not invent app intent.

10. FALSE-POSITIVE CALIBRATION

Use the Phase 8 real-device findings as calibration data.

The Phase 8 report recorded HIGH-RISK/REVIEW findings for legitimate apps including Galaxy Wearable, Samsung Wallet, Samsung Internet, Samsung Members and Drive.

Review those findings.

For each selected real app:

identify the rule(s) that fired;
separate DECLARED from GRANTED;
check whether OBSERVED evidence exists;
determine whether the risk level is overstated;
adjust rule semantics if necessary;
preserve evidence transparency.

Do NOT create app-specific allowlists merely to silence findings.

The fix should be general.

11. GENERIC RULE SEVERITY CALIBRATION

Revisit the nine Phase 8 generic rules.

Rules based only on declared static capabilities should generally produce:

INFORMATIONAL
REVIEW
at most contextual HIGH-RISK when evidence combinations genuinely justify it

but should not visually resemble confirmed malicious behavior.

Consider separating:

CAPABILITY EXPOSURE

from:

BEHAVIORAL ANOMALY.

Example:

Broad package visibility + overlay + multiple sensitive permissions

may mean:

HIGH CAPABILITY EXPOSURE

rather than:

HIGH-RISK BEHAVIOR

unless Stage 3 evidence exists.

Preserve technical honesty.

12. UI — EVIDENCE PROGRESSION

Every significant app finding should show evidence progression.

Example:

Location

DECLARED ✓
GRANTED ✓
OBSERVED — No verified observation

or:

DECLARED ✓
GRANTED ✓
OBSERVED ✓ 10:42

Provide a concise explanation.

The investor should be able to understand:

what the app asked for,
what Android allowed,
and what Thraksha actually saw.

13. UI — APP EVIDENCE CARD

Recommended structure:

APP NAME

Status:
WATCHING / REVIEW / ADVISED / KNOWN THREAT MATCH

Then:

Evidence

Capability row(s):

Location
Declared ✓
Granted ✓
Observed —

Contacts
Declared ✓
Granted ✓
Observed —

Context

Expected / Unusual / Suspicious / Unknown

Threat Intelligence

No known match

or verified match.

Recommendation

specific advice.

Do not use a giant red threat card for a declared-only capability.

14. USER ACT

Add a user-controlled:

ACT

entry point for findings where an appropriate response exists.

ACT does NOT mean arbitrary automatic blocking.

When tapped, show only actions technically supported for that finding.

Examples:

Restrict permission
Suspend/quarantine app
Open Android app settings
Block network where Network Guard controls traffic
Keep watching
Dismiss

Do not show unavailable actions.

15. ACT ARCHITECTURE

Reuse the existing policy/enforcement system.

Target flow:

User taps ACT

→ selected Finding/Evidence

→ PolicyEngine / action validation

→ authority check

→ supported executor

→ Android action

→ verify actual resulting state

→ ActionResult

→ SecurityEventBus

→ AuditLog

→ UI.

Do not bypass PolicyEngine just because the user initiated the action.

16. ADVICE MODE USER ACT

In NORMAL / DEVICE_ADMIN:

If Thraksha lacks authority to perform the requested operation:

do not report failure as if something broke.

Return:

USER ACTION REQUIRED

and offer the appropriate Android settings deep-link where feasible.

Example:

Restrict Location

→ open application permission settings.

If direct supported action exists without Device Owner, use it only when genuinely available.

17. FULL POWER USER ACT

With Device Owner:

Supported responses may include:

deny eligible runtime permission;
suspend VillainCaller / controlled targets;
restore controlled target.

For arbitrary real apps:

automatic containment remains prohibited.

User-initiated action may still require an explicit confirmation screen.

Do not remove the existing enforcement allowlist casually.

If expanding user-initiated actions to real apps is considered, require explicit confirmation and document the safety boundary.

For the investor demo, the safest approach is:

Real apps → settings/manual restriction

VillainCaller → direct verified enforcement

unless a real-app action can be implemented with high confidence and reversibility.

18. ACTION CONFIRMATION

Potentially disruptive actions require confirmation.

Example:

Suspend this app?

Explain:

what will happen;
whether it is reversible;
why Thraksha recommends it.

Do not place destructive actions behind one accidental tap.

No wipe/reset/destructive device operation.

19. ACTION VERIFICATION

Preserve the existing principle:

API call ≠ success.

After action:

query permission state;
query suspension state;
verify Network Guard branch where relevant;
show ACTED only on verified success.

Possible outcomes:

ACTED
PARTIALLY ACTED
USER ACTION REQUIRED
FAILED
UNSUPPORTED
CANCELLED
20. NOTIFICATIONS

Add a lightweight security notification path for real-world findings.

Purpose:

When Thraksha detects a REVIEW/HIGH-RISK condition, it may notify the user without automatically stopping the app.

Notification examples:

Thraksha Security Review

Galaxy Wearable has several powerful capabilities enabled. Tap to review evidence.

Do not use:

Virus detected

unless there is a genuine strong threat-intelligence match.

Notification should deep-link to the relevant evidence card.

21. NOTIFICATION THRESHOLDS

Do not notify for every Stage 1 declaration.

Recommended:

Declared-only low-risk evidence
→ no notification.

Granted sensitive/high-impact combination
→ optional REVIEW notification.

Verified unusual observed behavior
→ notification.

Known threat match
→ high-priority notification.

Avoid notification spam.

22. WATCHING STATE

Introduce or formalize:

WATCHING

for apps with:

declared/granted capabilities;
no strong contextual anomaly;
no known threat match;
no suspicious observation.

This is particularly important for legitimate powerful apps.

The UI should communicate:

Thraksha knows this app has access, but currently has no evidence requiring action.

23. REAL-WORLD ACTION POLICY

For arbitrary installed apps:

Stage 1 only

→ WATCHING

Stage 1 + Stage 2 unusual

→ REVIEW / notify

Verified Stage 3 + unusual context

→ ADVISED

Strong known threat intelligence

→ ADVISED + ACT option

Controlled VillainCaller

→ existing automated Full Power path remains valid.

No automatic arbitrary-app quarantine in Phase 8.1.

24. THREAT INTELLIGENCE REMAINS INDEPENDENT

Do not weaken exact hash/intelligence findings merely because Stage 3 evidence is absent.

An exact strong APK SHA-256 match from trusted signed intelligence is still strong evidence.

The evidence model should show:

Declared
Granted
Observed

alongside:

Threat Intelligence: KNOWN THREAT MATCH

These are independent evidence dimensions.

25. NETWORK EVIDENCE

Integrate Phase 7 network activity into Stage 3.

For VillainCaller:

Network capability
→ declared if applicable
→ granted/available
→ OBSERVED: UDP attempt to 203.0.113.113:443
→ signed demo network indicator match
→ policy response.

This is currently the strongest genuine runtime-behavior demonstration.

Do not change the safe per-app VPN scope.

26. AUDIT

Audit should record enough to reconstruct evidence progression.

Examples:

EVIDENCE DECLARED

EVIDENCE GRANTED

EVIDENCE OBSERVED

CONTEXT

DECISION

USER ACTION

ACTION RESULT

Do not flood the chain with every harmless permission on every app.

Persist significant transitions/findings only.

27. DATA MODEL

Avoid a destructive DB migration unless genuinely required.

If evidence can remain in structured runtime models and existing textual audit payloads for the demo, prefer that.

Do not change the hash-chain format merely for richer UI.

28. GALAXY WEARABLE VALIDATION CASE

Use Galaxy Wearable as a calibration case because Phase 8 naturally flagged it.

Run at least:

State A

Watch disconnected.

Scan and record:

declared capabilities;
granted capabilities;
observable runtime evidence.
State B

Watch connected, idle.

Repeat.

State C

Use a legitimate feature that may exercise a capability if practical.

Repeat.

Document what Android can and cannot prove.

Do NOT assert that a permission was used merely because app behavior seems likely.

Create:

temp/PHASE8_1_WEARABLE_VALIDATION.md

This document should explicitly distinguish:

static exposure;
granted access;
observed behavior;
inferred/unknown behavior.
29. ADDITIONAL REAL-APP VALIDATION

Select 2–4 additional real apps from Phase 8 results.

Prefer a mix such as:

browser;
wallet/payment app;
productivity/cloud app.

Do not modify them.

Use them to verify that the revised evidence model reduces misleading red flags without suppressing genuine capability exposure.

Document results in the Phase 8.1 progress report.

30. UNIT TESTS

Add tests for:

Evidence model
declared only
declared + granted
declared + granted + observed
observed unavailable
evidence stages do not imply one another
Context
expected capability
unusual granted capability
verified unusual observed behavior
unknown baseline
Classification
declared-only does not become behavior anomaly
granted-only may produce REVIEW
observed unusual use may produce ADVISED
exact strong ThreatPack match remains strong without Stage 3
missing observation does not become observed=false-as-clean
Action
ACT exposes only supported actions
unsupported action hidden/refused
Advice Mode produces settings/manual path
Full Power direct enforcement remains verified
user cancel performs no action
failed action never reports ACTED.
31. INSTRUMENTED TESTS

Add runtime tests where Android permits:

permission requested vs granted distinction;
special-access enabled state;
observation provider behavior;
unavailable observation handling;
notification deep-link;
ACT flow;
settings intent;
audit persistence;
existing Device Owner enforcement regression;
existing Network Guard observed-event regression.

Do not fake OS observation APIs.

32. REGRESSION TESTS

All existing Phase 1–8 tests must remain passing.

Especially verify:

generalized scan still finds 441-ish real packages subject to device state;
ThreatPack v3 still verifies;
real IOC matching still works;
GoodCaller remains clean;
VillainCaller known-threat positive remains;
Device Owner containment remains;
restore remains;
Network Guard remains;
audit chain remains valid.
33. UI LANGUAGE

Use precise language.

Good:

Capability declared

Permission granted

Recent access observed

No verified observation available

High capability exposure

Review recommended

Bad:

App is spying

Contacts stolen

Camera used

unless proven.

34. INVESTOR UI TARGET

For a legitimate app such as Galaxy Wearable, target something like:

Galaxy Wearable — REVIEW

Evidence:

Location
DECLARED ✓
GRANTED ✓
OBSERVED — no verified unusual use

Overlay
DECLARED ✓
ENABLED ✓

Context:

Powerful capability set consistent with a wearable companion; no malicious behavior proven.

Recommendation:

Keep watching

Then:

[ REVIEW SETTINGS ]

not:

BLOCK NOW

unless stronger evidence exists.

35. CONTROLLED POSITIVE TARGET

For VillainCaller:

VillainCaller — KNOWN THREAT MATCH

Profile:
high-risk mismatch.

Threat intelligence:
strong controlled signer/APK match.

Network:
OBSERVED outbound attempt.

Decision:
ADVISE or ACT depending on privilege/mode.

Available:

[ ACT ]

Full Power:

→ verified enforcement.

This provides the contrast between a legitimate powerful app and an intentionally suspicious controlled sample.

36. PERFORMANCE

Measure additional overhead from:

grant-state checks;
observation provider;
context evaluation.

Do not significantly increase the ~21-second Phase 8 scan without justification. Phase 8 currently hashes 441 applications in about 21 seconds on the S20 FE.

Record timing in:

temp/THRAKSHA_DEMO_PHASE_8_1_PROGRESS.md

37. REQUIRED DOCUMENTS

Create/update:

temp/PHASE8_1_RUNTIME_OBSERVABILITY.md

temp/PHASE8_1_WEARABLE_VALIDATION.md

temp/THRAKSHA_DEMO_PHASE_8_1_PROGRESS.md

Document:

APIs investigated;
capabilities observable;
capabilities not observable;
privilege limitations;
evidence model;
calibration changes;
real-app validation;
action behavior;
notifications;
tests;
device results;
performance;
screenshots;
divergences.
38. REQUIRED SCREENSHOTS

Store under:

temp/screenshots/

Capture:

Stage 1/2/3 evidence UI;
Galaxy Wearable evidence card;
at least one WATCHING result;
one REVIEW result;
VillainCaller observed network evidence;
user ACT options;
Advice Mode manual/settings action;
Full Power verified action;
notification;
audit feed.
39. FINAL VERIFICATION

Before completion:

clean-build all modules;
full unit suite;
full instrumented suite;
real-device scan;
Galaxy Wearable validation;
additional real-app calibration;
controlled VillainCaller positive;
Advice Mode ACT behavior;
Full Power ACT behavior;
Network Guard regression;
ThreatPack verification;
Rulepack verification;
audit-chain verification;
no destructive APIs;
no private keys;
no real-app automatic containment;
git working-tree inventory.

Do not commit automatically.

40. ACCEPTANCE CRITERIA

Phase 8.1 is complete only when:

UI clearly distinguishes DECLARED, GRANTED and OBSERVED;
Stage 3 is never inferred from static metadata;
unsupported runtime observation is labelled honestly;
Phase 8 real-app flags are recalibrated;
legitimate powerful apps no longer look like proven malicious behavior solely from static capabilities;
generic rules remain evidence-based;
WATCHING state exists;
user ACT exists;
ACT shows only technically available responses;
Advice Mode provides useful manual/settings actions;
Full Power continues verified enforcement for controlled targets;
notifications are informative rather than alarmist;
network events populate genuine Stage 3 evidence;
known threat intelligence remains independent and strong;
all Phase 1–8 behavior remains working;
audit integrity remains valid.
41. STOP CONDITIONS

STOP and document rather than improvise if:

Android does not expose reliable runtime usage information for a capability;
Stage 3 would require inference rather than evidence;
implementing runtime observation would require inaccessible/private app data;
a user ACT option cannot actually be performed or verified;
calibration would require hardcoded app-specific exemptions;
real apps would need automatic quarantine;
notification wording would imply malware without evidence;
existing ThreatPack, PolicyEngine, Device Owner, Network Guard or audit behavior regresses.
42. FINAL PHASE 8.1 STORY

The investor should be able to look at any finding and immediately answer:

What can this app do?

→ DECLARED

What is it currently allowed to do?

→ GRANTED

What has Thraksha actually seen it do?

→ OBSERVED

Does that behavior make sense?

→ CONTEXT

What does Thraksha recommend?

→ DECISION

What can I do about it?

→ ACT

The key product statement becomes:

Thraksha does not confuse permission with behavior. It distinguishes what an app asks for, what Android permits, and what the app actually does—then shows the evidence before recommending or taking action.