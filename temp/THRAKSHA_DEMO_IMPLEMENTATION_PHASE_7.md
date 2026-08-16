# THRAKSHA DEMO — PHASE 7 IMPLEMENTATION GUIDE

## NETWORK PROTECTION / LIVE OUTBOUND GUARD

## PURPOSE

Phase 7 adds the third security pillar to the Thraksha demo:

1. **Contextual App Profile Analysis** — already working.
2. **Signed Threat Intelligence / Antivirus** — already working.
3. **Live Network Protection** — this phase.

The objective is NOT to build a commercial general-purpose VPN.

The objective is to demonstrate one technically honest and repeatable live network-security scenario:

`VillainCaller`
→ makes a harmless synthetic outbound network attempt
→ Thraksha's VPN genuinely intercepts the packet
→ Thraksha sees destination metadata
→ signed network threat intelligence identifies the destination
→ PolicyEngine evaluates the finding
→ ADVICE MODE observes/advises
→ FULL POWER blocks the packet
→ result is verified and audited.

GoodCaller and normal device traffic must remain unaffected.

---

# 0. KNOWN-GOOD BASELINE — DO NOT REGRESS

Phases 1–6 are complete.

Preserve the working:

- SQLCipher encrypted store
- Android Keystore integration
- hash-chained AuditLog
- Rulepack v2 + verification
- ThreatPack v1 + verification
- AppInventory
- AppBaselines
- RuleEngine
- ThreatIntelligenceScanner
- SecurityAuditEngine
- PolicyEngine
- AdvisoryEnforcer
- DeviceOwnerEnforcer
- SecurityEventBus
- FoundationStatus
- DemoMode
- GoodCaller
- VillainCaller
- Device Owner enforcement
- verified permission denial
- package suspension/quarantine
- restoration
- Advice Mode
- Full Power
- launch/manual security audit

Existing expectations must remain:

`GoodCaller`
→ Profile CLEAN
→ Threat Intelligence CLEAN
→ WATCHING.

`VillainCaller — Advice Mode`
→ profile + threat-intel findings
→ ADVISED.

`VillainCaller — Full Power`
→ eligible containment
→ verified ACTED.

Phase 7 must be additive.

---

# 1. CRITICAL NETWORK SCOPE

Do NOT build a full device-wide VPN.

Do NOT attempt:

- general TCP proxying;
- TCP stream reassembly;
- TLS interception;
- HTTPS decryption;
- certificate MITM;
- content inspection;
- arbitrary DNS filtering;
- a complete userspace TCP/IP stack;
- routing every application through Thraksha;
- root networking;
- packet injection into other apps;
- remote command-and-control infrastructure.

Those are outside the investor demo scope and create unnecessary reliability risk.

The Phase 7 network tunnel must be scoped specifically to:

`com.thraksha.demo.villaincaller`

using Android's per-application `VpnService` routing capability.

This is critical.

**Only VillainCaller should enter the Thraksha tunnel.**

GoodCaller, Guardian, Settings, launcher, browser and normal device traffic must bypass it.

This prevents the old VPN implementation from black-holing the device.

---

# 2. TECHNICAL HONESTY

Thraksha may claim only what it actually observes.

For an intercepted packet, Thraksha may legitimately know metadata such as:

- IP version;
- protocol;
- destination IP;
- destination port;
- source information where available;
- packet size;
- timestamp;
- which controlled application is routed through the VPN.

Do NOT claim:

- "photos were inside this packet";
- "contacts were being uploaded";
- "passwords were being stolen";
- "we decrypted HTTPS";
- "we inspected TLS contents";
- "we know the semantic payload";
- "this endpoint is real malware infrastructure" unless backed by genuine production intelligence.

For this demo, the endpoint must be labelled as a:

`DEMO TEST NETWORK INDICATOR`

The purpose is to prove the network-security architecture, not invent a real malicious server.

---

# 3. CONTROLLED NETWORK SCENARIO

Extend VillainCaller with one harmless action:

`Run Demo Network Attempt`

When tapped, VillainCaller sends a small synthetic UDP datagram.

Use no private/user data.

Payload may be a fixed demo byte sequence such as:

`THRAKSHA_DEMO_NETWORK_PROBE`

but Thraksha should NOT use payload contents as the security trigger.

Security decisions must be based on metadata/threat intelligence.

Use a documentation-only reserved test IP, for example from an RFC TEST-NET range.

Choose one fixed endpoint and document it as:

`DEMO_NETWORK_TARGET`

Example conceptual endpoint:

`203.0.113.x:443`

Do not use a real malicious domain/IP.

Do not depend on a backend.

Do not require the destination to reply.

VillainCaller should clearly describe the action as:

**"Send harmless synthetic outbound demo packet."**

No actual personal information must leave the device.

---

# 4. NETWORK DEMO SEMANTICS

The same intercepted packet should produce different policy outcomes depending on mode.

## ADVICE MODE

Desired flow:

`VillainCaller sends packet`
→ Thraksha VPN intercepts
→ destination metadata extracted
→ signed ThreatPack matches demo network indicator
→ network finding produced
→ PolicyEngine → ADVISE
→ Thraksha forwards/allows the synthetic outbound packet
→ UI shows:

`NETWORK THREAT DETECTED`
`ADVISED`
`No automatic block performed`

The packet does NOT need a server response.

"Allowed" means Thraksha chose not to block the outbound attempt.

Do not claim successful delivery to a remote server.

---

## FULL POWER

Desired flow:

`VillainCaller sends packet`
→ Thraksha VPN intercepts
→ destination metadata extracted
→ ThreatPack match
→ PolicyEngine → ACT
→ Thraksha drops packet BEFORE forwarding
→ ActionResult verifies it was not forwarded
→ UI shows:

`NETWORK THREAT DETECTED`
`BLOCKED`
or
`ACTED — outbound attempt blocked`

Audit the result.

The global Device Owner / FULL POWER semantics must remain consistent with previous phases.

Even though Android VPN consent itself provides network-routing authority, automatic blocking in this investor demo remains gated by the existing Full Power policy architecture.

Do not create a second contradictory authority model.

---

# 5. VPN CONSENT

Android requires user consent before a VpnService can operate.

Create a clear Network Guard lifecycle:

`DISABLED`
`CONSENT_REQUIRED`
`READY`
`STARTING`
`ACTIVE`
`ERROR`

Use `VpnService.prepare()` correctly.

The dashboard should expose a deliberate control:

`Enable Network Guard`

If consent is required, launch the Android VPN consent UI.

After consent:

→ start ThrakshaVpnService.

Do not repeatedly request consent.

Do not fake ACTIVE state.

The UI state must reflect the real service/tunnel state.

---

# 6. NETWORK GUARD STATE

Create a process-wide observable state source, conceptually:

`NetworkGuardState`

Suggested states:

- Disabled
- ConsentRequired
- Starting
- Active
- Error(reason)

Optional metadata while active:

- scopedPackage
- tunnelAddress
- packetsObserved
- packetsAllowed
- packetsBlocked
- lastObservation

Use `StateFlow`.

Do not use a local Compose `remember` boolean as the source of truth.

The Phase 1 audit already identified that mistake in the old VPN UI.

---

# 7. REWORK THRAKSHA VPN SERVICE

The existing `ThrakshaVpnService` is disabled because its former TUN loop could black-hole traffic.

Replace the old echo behaviour.

Do not simply remove `ENABLED = false` without changing the implementation.

Build the tunnel intentionally.

Recommended builder configuration:

- session name: `Thraksha Network Guard`
- appropriate MTU
- private TUN address
- IPv4 route required for demo target
- `addAllowedApplication("com.thraksha.demo.villaincaller")`

The important invariant is:

**Only VillainCaller enters this VPN.**

If `addAllowedApplication()` fails:

→ fail closed;
→ NetworkGuardState.Error;
→ do not establish a broad tunnel.

Never silently fall back to capturing all applications.

---

# 8. PACKET PROCESSING MODEL

Create a clean network-domain model.

Example:

`NetworkObservation`

Fields may include:

- timestamp
- packageName
- ipVersion
- protocol
- sourceIp
- sourcePort
- destinationIp
- destinationPort
- packetLength

No payload field is required.

If temporary payload bytes are needed internally for UDP forwarding, they should not enter security-domain objects or audit logs.

---

# 9. PACKET PARSER

Create a pure/testable packet parser.

Suggested class:

`NetworkPacketParser`

Input:

`ByteArray + length`

Output:

- supported observation;
- unsupported packet;
- malformed packet.

Minimum Phase 7 support:

- IPv4
- UDP

Optional only if trivial and safe:

- IPv6 metadata parsing
- TCP metadata parsing

But do NOT expand scope merely for completeness.

The controlled VillainCaller probe should use exactly the protocol Phase 7 fully supports.

Parser requirements:

- validate header lengths;
- handle malformed packets safely;
- never read outside buffer boundaries;
- validate UDP length;
- extract destination IP/port;
- avoid exceptions escaping the packet loop;
- produce no security verdict itself.

Packet parsing and security policy must remain separate.

---

# 10. UDP ADVICE-MODE FORWARDING

Because only VillainCaller is routed into the VPN, the demo needs a narrowly scoped method for allowing its synthetic UDP packet in Advice Mode.

Implement an outbound-only UDP forwarder for the controlled demo packet path.

Conceptually:

TUN UDP packet
→ parse destination + UDP payload
→ create protected `DatagramSocket`
→ call `VpnService.protect(socket)`
→ send datagram to original destination
→ close socket.

No response path is required because VillainCaller does not wait for a reply.

This is NOT a general UDP VPN implementation.

It is a controlled outbound forwarding primitive for the demo.

Requirements:

- use `protect()` so the forwarding socket does not loop into the VPN;
- forwarding occurs on IO worker/coroutine;
- never forward malformed packets;
- never forward packets after policy says BLOCK;
- never inspect semantic payload contents;
- close resources reliably;
- maintain counters/results.

If forwarding fails:

→ record FAILED / NOT_FORWARDED;
→ do not claim the packet was allowed successfully.

"Forwarded" means Thraksha attempted the protected outbound send successfully.

Do not claim remote receipt.

---

# 11. FULL POWER PACKET BLOCK

In Full Power ACT path:

do NOT create the protected forwarding socket.

The intercepted packet is discarded.

Record a structured action result:

`BLOCK_NETWORK`

with status such as:

`SUCCEEDED`

and verified detail:

`Packet intercepted and intentionally dropped before forwarding.`

Verification should derive from the actual packet processing branch/state.

Do not claim remote-server verification.

Correct investor wording:

**"Thraksha intercepted the outbound attempt and did not forward it."**

---

# 12. THREATPACK NETWORK INDICATOR

Extend ThreatPack carefully.

Currently the antivirus implementation supports:

- PACKAGE_NAME
- SIGNING_CERT_SHA256
- BASE_APK_SHA256

Phase 7 should activate a network indicator type.

Recommended:

`IP`

or a more explicit:

`DESTINATION_IP`

Choose one canonical representation and document it.

The signed ThreatPack should contain a controlled record matching the fixed demo target.

Example conceptual record:

- id: `demo-network-endpoint`
- type: `IP`
- value: `<reserved TEST-NET address>`
- classification: `DEMO_TEST_NETWORK_INDICATOR`
- severity: HIGH
- weight: deterministic authored rule weight
- enabled: true
- description: explicitly says this is a controlled demo indicator.

Do NOT classify it as:

- C2;
- ransomware infrastructure;
- malware server;
- credential theft infrastructure;

unless it actually is—which it must not be for this demo.

Re-sign ThreatPack using the existing signing process.

Private key must remain gitignored and absent from APK.

---

# 13. THREAT INTELLIGENCE ARCHITECTURE

Do not make `ThreatIntelligenceScanner` responsible for raw packets.

Create a network-specific evaluator or extend the intelligence layer cleanly.

Potential design:

`NetworkThreatEvaluator`

Input:

- `NetworkObservation`
- verified ThreatPack

Output:

`Finding`

The resulting Finding should use the existing unified finding architecture.

Add a finding source if useful:

`NETWORK_BEHAVIOR`

or:

`NETWORK_THREAT_INTELLIGENCE`

Choose terminology that distinguishes live network evidence from installed-app fingerprint evidence.

Do not create an incompatible second Finding model.

Example finding:

Source:
`NETWORK_THREAT_INTELLIGENCE`

Package:
`com.thraksha.demo.villaincaller`

Indicator:
`demo-network-endpoint`

Evidence:

`Outbound UDP attempt to <test-ip>:443 matched signed network indicator.`

Classification:

`DEMO_TEST_NETWORK_INDICATOR`

---

# 14. IMPORTANT: NETWORK FINDING IS LIVE

Unlike Phases 1–6 static audit findings, this is produced by an actual packet event.

The sequence should therefore be:

`Packet observed`
→ `NetworkObservation`
→ threat evaluation
→ Finding
→ policy
→ allow/block
→ event/audit.

Do NOT force live network events through `SecurityAuditEngine.runAudit()` if doing so creates an artificial architecture.

Instead, reuse the existing policy-response layer.

If Phase 4–6 code has policy/enforcement orchestration embedded inside `SecurityAuditEngine`, perform the smallest safe extraction needed.

Recommended concept:

`SecurityResponseCoordinator`

Input:

- package/app identity
- list of Findings
- current PrivilegeLevel
- current ExecutionMode

Responsibilities:

- invoke PolicyEngine
- emit decision
- route to appropriate response mechanism
- return structured outcome.

Both:

`SecurityAuditEngine`

and

`NetworkGuardEngine`

may call this same coordinator.

Do not duplicate PolicyEngine logic.

Do not rewrite SecurityAuditEngine unnecessarily.

---

# 15. NETWORK ACTION MODEL

Phase 4 already contains:

`BlockNetwork`

as a future action.

Activate it properly.

Introduce an authority such as:

`VPN_GUARD`

if the action model needs to represent the real technical capability.

Do not falsely describe `BlockNetwork` as requiring Device Owner at the Android API level.

However, for this demo's product policy:

- ADVICE MODE → network finding is ADVISED / forwarded.
- FULL POWER + eligible AUTO_DEFEND policy → BlockNetwork can execute.

This preserves the investor story while keeping the technical model honest.

Authority and policy are different concepts.

---

# 16. EXECUTION MODE

Preserve existing semantics.

## OBSERVE

Network event:

→ observe
→ audit
→ allow/forward
→ no advice/action if current policy says observe.

## GUIDED

Network event:

→ detect
→ ADVISE
→ allow/forward unless user explicitly approves a supported network block.

For Phase 7, explicit per-packet approval is optional and may be deferred.

Do not block waiting for UI approval inside the packet processing thread.

## AUTO_DEFEND

If FULL POWER and the signed network finding meets action threshold:

→ ACT
→ block packet.

## LOCKDOWN

Do not implement full Safe Mode here.

Phase 8 owns Safe Mode.

For Phase 7, LOCKDOWN may use the same network-block eligibility as AUTO_DEFEND, but must not introduce wider containment.

Document this explicitly.

---

# 17. NETWORK GUARD ENGINE

Create a focused orchestration layer.

Conceptual flow:

`ThrakshaVpnService`
→ packet parser
→ `NetworkObservation`
→ `NetworkGuardEngine`
→ verified ThreatPack
→ `NetworkThreatEvaluator`
→ Finding
→ PolicyEngine / response coordinator
→ ALLOW or BLOCK result
→ service forwards or drops.

Keep the VpnService itself as thin as practical.

It should own:

- TUN lifecycle;
- packet read loop;
- socket protection;
- forwarding/drop execution;
- foreground notification.

It should NOT own:

- threat-intelligence business rules;
- PolicyEngine logic;
- UI formatting.

---

# 18. EVENT MODEL

Use the existing SecurityEventBus.

Add network-specific events only where valuable.

Possible events:

`NetworkAttemptObserved`

`NetworkAttemptAllowed`

`NetworkAttemptBlocked`

or represent the finding via existing ThreatDetected + ActionTaken.

Avoid excessive event types.

At minimum the audit trail must be able to answer:

- Which app attempted network traffic?
- What destination was observed?
- Which signed indicator matched?
- What did PolicyEngine decide?
- Was the packet forwarded or dropped?
- Why?

Do not log payload contents.

---

# 19. AUDIT LOG

Recommended network audit sequence:

### Advice

`NETWORK`
→ `THREAT`
→ `DECISION: ADVISE`
→ `ADVISED / ALLOWED`

### Full Power

`NETWORK`
→ `THREAT`
→ `DECISION: ACT`
→ `ACTION: BLOCK_NETWORK SUCCEEDED`

Keep the existing hash-chain intact.

No DB schema migration is required unless a genuine implementation need arises.

Do not change the audit hash algorithm.

---

# 20. VILLAINCALLER UI

Add a small clearly controlled section.

Example:

**Network Demo**

`Send Demo Outbound Packet`

Supporting text:

`Sends synthetic demo data to a reserved test address. No personal data is used.`

After tap VillainCaller may show:

`Outbound demo attempt submitted.`

Do NOT make VillainCaller claim the packet reached the destination.

Do NOT make it show "data stolen" or similar theatrical malware messaging.

The app remains a controlled sample.

---

# 21. GUARDIAN NETWORK UI

Add a compact Network Guard section.

Minimum information:

`NETWORK GUARD`

Status:

`OFF`
`CONSENT REQUIRED`
`ACTIVE`
`ERROR`

When active:

`Protected app: VillainCaller`

Optional counters:

`Observed`
`Allowed`
`Blocked`

For latest event show:

- VillainCaller
- destination IP
- destination port
- protocol
- indicator match
- decision
- outcome.

Examples:

Advice Mode:

`OUTBOUND ATTEMPT DETECTED`

`Signed demo network indicator matched`

`ADVISED — packet not automatically blocked`

Full Power:

`OUTBOUND ATTEMPT DETECTED`

`Signed demo network indicator matched`

`BLOCKED — packet intercepted before forwarding`

Do not redesign the entire investor dashboard in Phase 7.

Phase 9 owns final presentation polish.

---

# 22. FOREGROUND NOTIFICATION

The VPN's foreground notification must be truthful.

When active:

`Thraksha Network Guard`

`Monitoring VillainCaller network traffic`

Do not use:

`All device traffic protected`

because only VillainCaller is routed.

When stopped, remove/update the foreground state correctly.

---

# 23. SERVICE LIFECYCLE

Implement safe:

- start;
- stop;
- repeated start;
- repeated stop;
- consent denied;
- tunnel establishment failure;
- VillainCaller absent;
- application restart;
- service destruction.

On stop:

- close TUN;
- cancel processing coroutine;
- close sockets;
- set NetworkGuardState.Disabled;
- release resources.

No zombie VPN icon/service should remain.

---

# 24. FAILURE BEHAVIOUR

Fail closed with respect to claims.

Examples:

### ThreatPack unavailable

Network Guard may still parse metadata, but must NOT report:

`Threat intelligence clean`.

Instead:

`Threat intelligence unavailable`.

Automatic threat-intelligence-based blocking should not occur unless policy explicitly defines safe fail-closed behavior.

For this demo, prefer:

→ observe;
→ report scanner unavailable;
→ do not claim threat match.

### Parser failure

Drop malformed controlled tunnel packet safely or report unsupported.

Never crash service.

### Forwarding failure in Advice Mode

Outcome:

`FORWARD FAILED`

not:

`ALLOWED`.

### VPN tunnel failure

NetworkGuardState.Error.

Do not claim monitoring is active.

---

# 25. NETWORK THREATPACK FAILURE POLICY

Because network blocking relies on signed network intelligence, use:

`Verified ThreatPack`
→ evaluate.

`Unavailable ThreatPack`
→ no demo network threat verdict.

Do not use unsigned fallback data.

Signature failure must remain fail-closed.

The UI should clearly show:

`NETWORK THREAT INTELLIGENCE UNAVAILABLE`

rather than quietly showing zero threats.

---

# 26. TESTS — PURE UNIT TESTS

Add comprehensive unit tests.

## NetworkPacketParser

Test:

- valid IPv4 UDP packet;
- destination IP extraction;
- source/destination port extraction;
- packet length;
- variable IPv4 header length;
- truncated IP header;
- invalid IHL;
- truncated UDP header;
- invalid UDP length;
- unsupported protocol;
- malformed packet never crashes;
- deterministic parsing.

Use handcrafted packet fixtures.

No real network required.

---

## NetworkThreatEvaluator

Test:

- matching signed IP indicator → finding;
- wrong IP → no finding;
- disabled indicator → no finding;
- future/unimplemented indicator → no finding;
- classification preserved;
- severity/weight preserved;
- package attribution correct;
- no payload-based detection.

---

## Policy integration

Test:

Advice Mode + network finding
→ ADVISE.

Full Power + OBSERVE
→ no block.

Full Power + GUIDED
→ ADVISE.

Full Power + AUTO_DEFEND + eligible finding
→ ACT / BlockNetwork.

GoodCaller cannot become a network target through this demo path.

---

# 27. INSTRUMENTED TESTS

Add tests where practical for:

- NetworkGuardState lifecycle;
- targeted package configuration;
- VillainCaller package visibility;
- GoodCaller remains outside tunnel scope;
- Guardian remains outside tunnel scope;
- threatpack network indicator loads through production loader;
- network finding flows through SecurityEventBus;
- network audit rows preserve chain verification.

Tests requiring Android VPN user consent may be marked manual/runtime if consent cannot be safely automated.

Do not fake VPN consent in production code just to make tests green.

---

# 28. SAFETY INVARIANT TEST

Provide explicit proof that the tunnel cannot accidentally become device-wide.

Create a testable configuration function, conceptually:

`VpnScope`

whose only allowed package for this phase is:

`com.thraksha.demo.villaincaller`

The service must refuse startup if that package cannot be scoped.

There must be no fallback:

`addRoute(0.0.0.0/0) + no allowed package`

That exact broad-tunnel state is prohibited.

This is a critical Phase 7 safety invariant.

---

# 29. ON-DEVICE RUNTIME VERIFICATION

Test on the Samsung S20 FE first for Advice Mode.

Required sequence:

1. Install Guardian + both decoys.
2. Launch Guardian.
3. Confirm ADVICE MODE.
4. Enable Network Guard.
5. Accept real Android VPN consent.
6. Confirm Network Guard ACTIVE.
7. Confirm Guardian itself still has connectivity where applicable.
8. Confirm GoodCaller is unaffected.
9. Open VillainCaller.
10. Tap `Send Demo Outbound Packet`.
11. Confirm Guardian records genuine outbound packet metadata.
12. Confirm signed demo network indicator matches.
13. Confirm ADVISE.
14. Confirm service followed Advice-mode forward path.
15. Confirm no privileged DPM action.
16. Confirm audit chain verifies.

Then test FULL POWER on the existing Device Owner AVD.

Required sequence:

1. Guardian = DEVICE_OWNER / FULL POWER.
2. Network Guard ACTIVE.
3. AUTO_DEFEND selected.
4. Restore VillainCaller from any previous package suspension first.
5. Trigger demo network packet.
6. Confirm packet interception.
7. Confirm signed network indicator match.
8. Confirm PolicyEngine ACT.
9. Confirm packet is dropped before forwarding.
10. Confirm BLOCK_NETWORK action is reported as verified.
11. Confirm audit chain verifies.
12. Confirm GoodCaller remains unaffected.

If a physical Device Owner-capable handset becomes available later, repeat the Full Power run there.

---

# 30. DO NOT LET PACKAGE SUSPENSION HIDE THE NETWORK DEMO

Important sequencing issue:

Phase 6 AUTO_DEFEND may suspend VillainCaller during launch audit.

A suspended app cannot be opened to trigger Phase 7 network behaviour.

Therefore the Phase 7 demo flow must account for this.

Choose a clean demo strategy.

Recommended:

For the network demonstration:

1. Restore VillainCaller.
2. Use GUIDED or a dedicated demo-safe policy state during initial app launch so it remains launchable.
3. Enable Network Guard.
4. Trigger network attempt.
5. Demonstrate network decision/block separately.

Do not silently change security policy.

Make the demo choreography explicit.

The final investor demo can present profile/antivirus containment and network containment as separate controlled scenarios.

---

# 31. DO NOT DOUBLE-ACT

A network finding must not automatically cause unrelated actions such as:

- revoke media permission;
- revoke location permission;
- suspend package;

unless those actions arise from the appropriate findings/policy context.

For a live network-indicator finding, the primary response should be:

`BLOCK_NETWORK`

The policy system should respond proportionally to the evidence source.

Do not suspend VillainCaller simply because one demo network packet matched a controlled IP unless the signed policy explicitly calls for that.

Phase 7 should demonstrate targeted network response.

---

# 32. PERFORMANCE

Measure on S20 FE:

- packet parse time;
- ThreatPack match time;
- policy decision time;
- Advice-mode forwarding latency;
- Full-Power block decision latency;
- total interception-to-outcome time.

The expected demo interaction should feel effectively immediate.

Do not optimise prematurely.

Record measurements in the progress report.

---

# 33. PRIVACY

Network logs must contain metadata only.

Allowed audit information:

- package name;
- destination IP;
- destination port;
- protocol;
- indicator ID;
- classification;
- decision;
- result.

Do not persist:

- UDP payload;
- arbitrary packet contents;
- user browsing data;
- unrelated app traffic.

Because only VillainCaller enters the tunnel, no real user's normal app traffic should be collected during the demo.

---

# 34. THREATPACK UPDATE

If ThreatPack changes:

- increment pack version;
- add network indicator;
- re-sign;
- run existing corruption/signature tests;
- ensure all prior antivirus indicators continue working;
- verify GoodCaller remains clean;
- verify VillainCaller package/cert/APK indicators remain correct.

Do not break Phase 6 antivirus while adding network intelligence.

---

# 35. EXISTING VPN CODE

Do not preserve old packet-echo code merely because it exists.

The old architecture was known unsafe.

Remove or replace obsolete echo behavior.

Keep useful existing lifecycle/foreground-service pieces where sound.

Do not perform unrelated refactoring outside the VPN/network subsystem.

---

# 36. DOCUMENTATION

Create/update:

`temp/THRAKSHA_DEMO_PHASE_7_PROGRESS.md`

Record:

- baseline before modifications;
- files created;
- files modified;
- VPN architecture;
- exact scoped package;
- packet protocol supported;
- network indicator;
- policy integration;
- Advice behavior;
- Full Power behavior;
- build results;
- unit tests;
- instrumented tests;
- manual VPN-consent tests;
- S20 FE results;
- Device Owner emulator results;
- timings;
- screenshots;
- limitations;
- deviations;
- safety checks;
- remaining risks.

---

# 37. REQUIRED SCREENSHOTS

Store under:

`temp/screenshots/`

Capture at minimum:

### Advice Mode

- Network Guard consent/active state
- network attempt detected
- destination metadata
- network threat-intelligence match
- ADVISED result
- audit rows

### Full Power

- FULL POWER + Network Guard ACTIVE
- network threat detected
- BLOCKED / ACTED result
- action details
- audit rows

Also capture VillainCaller network-demo screen.

---

# 38. REQUIRED FINAL TEST RUN

At Phase 7 completion run:

- clean build Guardian;
- clean build GoodCaller;
- clean build VillainCaller;
- complete unit suite;
- complete applicable instrumented suite on S20 FE;
- complete applicable suite on Device Owner AVD;
- Rulepack signature verification;
- ThreatPack signature verification;
- audit-chain verification;
- destructive-API safety grep;
- private-key asset inspection;
- VPN scope verification;
- git working-tree inventory.

Do not commit automatically.

---

# 39. ACCEPTANCE CRITERIA

Phase 7 is complete only when ALL of these are true:

- old unsafe VPN echo loop is gone;
- VPN cannot capture the entire device by accident;
- only VillainCaller is routed into Thraksha VPN;
- Android VPN consent is genuine;
- Network Guard UI reflects actual service state;
- VillainCaller sends only synthetic demo data;
- GoodCaller produces no network finding;
- normal device apps remain outside tunnel;
- Thraksha genuinely intercepts VillainCaller's UDP packet;
- destination IP/port are parsed from the real TUN packet;
- signed ThreatPack contains the demo network indicator;
- ThreatPack signature is verified before matching;
- network indicator produces a real Finding;
- existing PolicyEngine is used;
- Advice Mode produces ADVISED and does not claim a block;
- Advice path forwards/attempts forwarding rather than intentionally dropping;
- Full Power AUTO_DEFEND produces BlockNetwork;
- Full Power block means packet is dropped before protected forwarding;
- action result accurately records what happened;
- payload is never used as the threat signal;
- payload is never persisted;
- no TLS interception exists;
- no general TCP/IP stack is added;
- no backend is required;
- no private user data is transmitted;
- audit records observation, decision and outcome;
- hash chain still verifies;
- Phases 1–6 tests remain passing;
- GoodCaller remains clean;
- profile detection remains working;
- antivirus threat intelligence remains working;
- Device Owner containment remains working;
- restoration remains working;
- network latency is measured;
- screenshots are captured;
- no private key enters APK/source control;
- nothing is automatically committed.

---

# 40. STOP CONDITIONS

STOP and document the blocker instead of improvising if:

- Android cannot reliably scope the VPN to VillainCaller;
- implementation would require routing all device traffic through an unfinished tunnel;
- Advice Mode would require pretending a dropped packet was allowed;
- Full Power would require pretending an allowed packet was blocked;
- packet metadata cannot be extracted reliably;
- network behavior requires TLS interception;
- a real malicious server/sample would be required;
- user/private data would need to be transmitted;
- VPN consent would need to be bypassed;
- GoodCaller or Guardian traffic enters the tunnel unexpectedly;
- ThreatPack verification would need weakening;
- PolicyEngine would need bypassing;
- audit integrity regresses;
- existing Device Owner behavior regresses;
- Phase 1–6 tests regress and the cause cannot be isolated;
- implementation expands into a full VPN/TCP stack.

The priority is:

**a small real network-security demonstration**

not:

**a large incomplete VPN product.**

---

# 41. TARGET RUNTIME ARCHITECTURE

By the end of Phase 7:

`VillainCaller`

→ `Send Demo Outbound Packet`

→ Android networking

→ `ThrakshaVpnService`
   scoped ONLY to VillainCaller

→ `NetworkPacketParser`

→ `NetworkObservation`
   - destination IP
   - port
   - protocol
   - packet size

→ `NetworkGuardEngine`

→ verified signed ThreatPack

→ `NetworkThreatEvaluator`

→ `NETWORK_THREAT_INTELLIGENCE Finding`

→ existing PolicyEngine / SecurityResponseCoordinator

### ADVICE MODE

→ ADVISE

→ controlled protected UDP forwarding

→ `ADVISED / NOT BLOCKED`

→ SecurityEventBus

→ encrypted hash-chained AuditLog.

### FULL POWER

→ ACT

→ `BLOCK_NETWORK`

→ packet intentionally dropped before forwarding

→ verified network action result

→ SecurityEventBus

→ encrypted hash-chained AuditLog.

---

# 42. FINAL DEMO RESULT

At the end of this phase, Thraksha should demonstrate three complementary forms of security.

## Profile Analysis

`VillainCaller claims to be Caller-ID`
+
`declares inappropriate capabilities`

→ contextual profile threat.

## Antivirus / Threat Intelligence

`VillainCaller identity/fingerprint`
+
`signed ThreatPack match`

→ known controlled threat indicator.

## Live Network Protection

`VillainCaller genuinely attempts outbound traffic`
+
`Thraksha intercepts destination metadata`
+
`signed network indicator matches`

→ live network threat.

Then:

### Advice Mode

**"I detected the network threat and told you what it means."**

### Full Power

**"I detected the network threat and stopped the outbound attempt before forwarding it."**

Everything is recorded in the existing encrypted tamper-evident audit history.

The Phase 7 story should therefore be:

**Thraksha no longer only evaluates what an application declares or what it is known to be. It can also react to what the application actually attempts to do on the network — without pretending to decrypt or understand encrypted payload contents.**