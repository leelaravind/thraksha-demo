THRAKSHA DEMO — PHASE 8 IMPLEMENTATION GUIDE
REAL-WORLD DEVICE SCANNER + REAL THREAT INTELLIGENCE
1. PURPOSE

Phase 8 converts Thraksha from a demo primarily centered on known decoy packages into a generalized scanner capable of analyzing real applications installed on an Android test device.

The central acceptance test is:

Install Thraksha on an ordinary Android test phone, press Scan This Device, and analyze applications whose identities and results were not known when Thraksha was built.

The scan must derive results from actual Android-observable evidence.

No finding may exist merely because a package is hardcoded as expected-dangerous.

GoodCaller and VillainCaller remain controlled regression/demo samples, but the generalized scanner must work without them.

2. PRESERVE PHASES 1–7

Treat Phases 1–7 as the known-good baseline.

Do not unnecessarily redesign:

AppInventory
ObservedApp
RuleEngine
AppBaselines
SecurityAuditEngine
ThreatIntelligenceScanner
Rulepack verification
ThreatPack verification
PolicyEngine
SecurityResponseCoordinator
AdvisoryEnforcer
DeviceOwnerEnforcer
NetworkGuardEngine
NetworkThreatEvaluator
SecurityEventBus
encrypted SQLCipher database
Keystore integration
hash-chained AuditLog
GoodCaller/VillainCaller
Advice Mode
Full Power
Network Guard

Phase 8 is additive.

Run the complete existing test baseline before modifying code.

3. PRIMARY PRODUCT CHANGE

Introduce a genuine:

SCAN THIS DEVICE

workflow.

Conceptual architecture:

DeviceScanEngine

→ discover legitimately visible installed apps

→ extract observable application metadata

→ fingerprint apps

→ generic static analysis

→ contextual analysis where a reliable baseline exists

→ real signed ThreatPack lookup

→ aggregate findings

→ classify scan result

→ PolicyEngine where appropriate

→ SecurityEventBus

→ encrypted audit history.

The scanner must not require prior knowledge of package names.

4. PACKAGE VISIBILITY — FIRST BLOCKER

Current Thraksha intentionally uses targeted <queries> for the controlled decoys.

That is insufficient for generalized scanning.

Before implementation, investigate the Android package-visibility mechanism available to this privately installed demo build.

Document:

Android/API behavior;
what apps PackageManager can currently see;
whether broader package visibility is required;
implications of QUERY_ALL_PACKAGES;
implications for eventual Google Play distribution;
difference between private/investor build and production distribution.

Do not silently broaden permissions.

If the demo requires QUERY_ALL_PACKAGES, isolate it to an explicit demo/build strategy if practical and document why.

Do not claim Play Store eligibility from this implementation.

Create documentation:

temp/PHASE8_PACKAGE_VISIBILITY.md

Runtime-prove the number of applications discovered.

5. GENERALIZED APP INVENTORY

Evolve AppInventory carefully rather than creating a duplicate scanner.

For every legitimately visible installed app collect, where Android exposes it:

package name;
application label;
version name/code;
system/user-app status;
enabled state;
requested permissions;
granted permission state where meaningful;
signing information;
signing certificate SHA-256;
base APK path internally;
base APK SHA-256;
installer/source information where available;
exported components;
relevant manifest-declared capabilities.

Do not persist APK contents.

Do not copy APKs into Thraksha storage.

Do not read another application's private data.

6. COMPONENT ATTACK-SURFACE INVENTORY

Where Android APIs expose it, inventory:

exported activities;
exported services;
exported receivers;
exported providers;
declared services with security-sensitive roles;
permission protection on exported components where observable.

Represent these as structured evidence.

Do not automatically call an exported component malicious.

Exported attack surface is a review signal, not malware proof.

7. GENERIC CAPABILITY MODEL

Extend ObservedApp carefully so generalized apps can express relevant capabilities.

Examples include:

dangerous/sensitive permissions;
location;
camera;
microphone;
contacts;
SMS/phone capabilities;
media/storage;
package visibility;
overlay capability;
accessibility service declaration;
notification-listener declaration;
VPN service declaration;
device-admin declaration;
boot/background capabilities;
exported attack surface.

Only represent capabilities Android actually exposes.

Do not claim runtime behavior from manifest declarations.

8. APP CLASSIFICATION

Do NOT force every application into the existing CALLER_ID baseline.

Introduce a safe generalized classification model.

Minimum:

KNOWN_BASELINE
UNKNOWN

Additional categories may be added only when classification is deterministic and justified.

Potential future categories:

CALLER_ID
MESSAGING
BROWSER
BANKING
SOCIAL
PRODUCTIVITY
GAME
SYSTEM

But Phase 8 must not guess categories from app names and then generate security findings from the guess.

If category confidence is insufficient:

AppType.UNKNOWN

UNKNOWN applications receive generic analysis only.

9. GENERIC STATIC RISK RULES

Introduce a generic rule layer applicable without knowing app category.

Potential evidence includes combinations such as:

broad package visibility;
accessibility-service declaration;
overlay capability;
Device Admin capability;
VPN service capability;
unusually broad sensitive permission sets;
exposed components with weak protection;
sensitive capability combinations.

Rules must be evidence-based and deterministic.

These findings mean:

REVIEW / RISK INDICATOR

not:

MALWARE

Do not create rules merely to ensure the scanner always finds something.

A completely uneventful scan is valid.

10. RESULT VOCABULARY

Do not use binary CLEAN/THREAT terminology for arbitrary real applications.

Introduce defensible states:

NO KNOWN FINDINGS

No current rule or threat-intelligence match.

This does NOT mean mathematically proven safe.

REVIEW

One or more generic/contextual risk indicators warrant review.

HIGH-RISK PROFILE

Strong contextual/static findings exist but no authoritative known-threat match exists.

KNOWN THREAT MATCH

Strong real threat intelligence matched according to the confidence model.

SCANNER UNAVAILABLE / PARTIAL

Required intelligence or app evidence could not be obtained.

Never turn missing evidence into CLEAN.

11. REAL THREAT INTELLIGENCE

Read the two threat-intelligence research documents stored in temp/ before modifying ThreatPack.

Follow their sourcing, licensing, reliability, confidence, expiry and provenance recommendations.

Do NOT fabricate IOCs.

Do NOT manufacture hashes.

Do NOT download malware binaries.

Do NOT execute malware.

Only ingest metadata/IOC feeds whose usage is supported by the research and whose provenance can be documented.

The supplied research recommends a safe investor corpus consisting entirely of metadata/IOCs, with APK hashes as the strongest practical Android indicator, signing certificates as strong evidence when vetted, package names as corroborating evidence only, and network indicators treated as freshness-sensitive.

12. PHASE 8 INVESTOR THREAT CORPUS

Target approximately:

1,500–3,000 real APK SHA-256 indicators;
200–500 vetted signing-certificate fingerprints where safely obtainable;
1,000–2,000 fresh domains/IPs/URLs if compatible with the existing network intelligence architecture;
100–300 package-name corroboration indicators;
existing controlled VillainCaller indicators.

These are targets, not quotas.

Do not fill a quota using low-quality or fabricated data.

If licensing/provenance prevents inclusion, use fewer indicators and document why.

The research explicitly recommends this approximate few-thousand-indicator investor dataset and notes that it can remain compact while being genuinely sourced.

13. SOURCE PROVENANCE

Every real indicator must retain provenance.

Minimum metadata:

indicatorId;
indicatorType;
value;
classification;
severity;
confidence/weight;
source;
sourceReference;
retrieved/generated timestamp where applicable;
expiry/notAfter where appropriate;
enabled state.

Never allow:

REAL_WORLD_THREAT

without traceable provenance.

Create:

temp/THREAT_INTELLIGENCE_PROVENANCE.md

Document:

source;
acquisition method;
acquisition date;
indicator types used;
licensing/redistribution caveat;
transformations performed;
number accepted;
number rejected;
expiry rules.
14. INDICATOR RELIABILITY

Use different evidentiary strength for different IOC types.

APK SHA-256

Treat an exact match from trusted malware intelligence as strong known-threat evidence.

Signing certificate SHA-256

Potentially strong evidence when the certificate itself is specifically vetted as malicious.

Do not assume every certificate associated with one suspicious sample permanently makes every signed application malware without appropriate source semantics.

Package name

Never authoritative by itself.

Package names are mutable/spoofable and must be treated as corroboration/weak evidence.

The research specifically recommends package-name indicators never be authoritative and suggests limited lifetime/freshness.

Network indicators

Treat according to source confidence and freshness.

Expired network indicators must not remain active indefinitely.

15. THREATPACK SCHEMA EVOLUTION

Extend the existing ThreatPack rather than inventing an incompatible second database.

Support provenance and freshness.

Conceptually add fields such as:

source
sourceReference
confidence
firstSeen
lastSeen
expiresAt/notAfter
indicator reliability/tier

Maintain backwards compatibility where practical.

Increment schema version only if required.

ThreatPack must remain cryptographically signed.

Verification remains:

read bytes
→ verify signature
→ validate schema
→ activate

Never parse/use unsigned fallback intelligence.

16. EXPIRY / STALENESS

Implement deterministic expiry behavior.

Expired indicators:

→ disabled for active matching

but may remain available for audit/history.

Do not silently delete provenance.

Network IOCs should generally expire faster than immutable APK hashes.

Package-name indicators should also be time-limited.

If the entire pack becomes stale:

UI must show:

THREAT INTELLIGENCE STALE

rather than silently treating the device as clean.

17. CONFIDENCE / TRUST MODEL

Do not invent fake ML probabilities.

Use deterministic evidence tiers.

Recommended conceptual levels:

INFORMATIONAL
REVIEW
STRONG_INDICATOR
KNOWN_THREAT

A single weak package-name match must not become KNOWN_THREAT.

A trusted exact APK SHA-256 match may qualify.

Multiple corroborating indicators may strengthen the result according to explicit deterministic rules.

Document the mapping.

18. REAL-WORLD SCAN ENGINE

Create a dedicated orchestration entry point:

scanDevice()

It should:

establish scanner/intelligence readiness;
enumerate visible installed applications;
filter only where technically justified;
collect metadata;
fingerprint signing certificates;
hash base APKs;
collect static capability evidence;
run generic rules;
run contextual baseline rules where applicable;
run real ThreatPack matching;
aggregate findings per app;
classify each app;
produce overall scan statistics;
emit appropriate events;
persist scan summary/results;
return structured result to UI.

Do not hash the same APK multiple times during one scan.

19. SYSTEM APPLICATIONS

Do not flood the investor UI with hundreds of meaningless Android framework packages.

Inventory may include system apps if required for completeness, but UI should clearly distinguish:

USER APPS
SYSTEM APPS

Default investor view should emphasize user-installed applications.

Do not automatically classify system applications as trusted merely because they are system apps.

Likewise, don't flag them merely because they possess privileged capabilities unavailable to normal apps.

20. SCAN PROGRESS

Real scanning may involve dozens/hundreds of APK hashes.

Create observable scan state:

IDLE
DISCOVERING
ANALYZING
COMPLETE
PARTIAL
ERROR

Expose progress such as:

Analyzing 14 of 47 applications

Do all hashing and PackageManager-heavy work off the UI thread.

Support cancellation if straightforward and safe.

Never freeze Compose while scanning.

21. PERFORMANCE

Measure on S20 FE.

Record:

apps discovered;
user apps discovered;
inventory duration;
certificate fingerprint duration;
APK hashing duration;
generic-rule duration;
threat-intelligence lookup duration;
total scan duration;
peak/approximate memory where practical.

Do not prematurely optimize.

For the current few-thousand-indicator investor pack, JSON may remain acceptable if measurements support it.

The research recommends SQLite for future larger production packs while noting that a few-thousand-indicator demo pack is cheap enough for simpler storage.

Do not migrate to SQLite merely for architectural aesthetics.

22. REAL DEVICE SCAN UI

Add a clear action:

SCAN THIS DEVICE

Summary example:

47 apps analyzed

47 certificates checked

47 APK fingerprints checked

0 known-threat matches

5 apps require review

9 findings

Results should be derived at runtime.

Do not hardcode counts.

23. PER-APP RESULT

Each app result should show enough evidence to be credible.

Example:

Example App

Status:
REVIEW

Evidence:

Requests broad package visibility

Declares accessibility service

Threat intelligence:

No known threat match

Identity:

Package: ...

Certificate SHA-256: ... abbreviated visually

Base APK SHA-256: ... abbreviated visually

Allow expansion/copy if easy, but don't overwhelm the primary investor UI.

24. KNOWN-THREAT RESULT

When a real strong indicator matches:

Display:

KNOWN THREAT MATCH

Then show:

indicator type;
classification/family if supplied by trusted source;
source;
confidence/tier;
evidence;
ThreatPack version.

Do not embellish beyond source data.

If source says only malicious APK hash, don't invent malware behavior.

25. TRUE NEGATIVE IS SUCCESS

Do not engineer the real phone to produce a threat.

If the Samsung produces:

0 known-threat matches

that is a valid and desirable result.

The research specifically recommends scanning the real device honestly, reporting zero known matches if clean, and then separately using VillainCaller to prove the positive path.

Investor choreography:

Real phone

SCAN THIS DEVICE

→ genuine unknown inventory

→ genuine fingerprints

→ genuine real-world intelligence lookup

→ honest results.

Then:

Controlled proof

VillainCaller

→ same scanner

→ controlled known match

→ PolicyEngine

→ Advice/Full Power response.

The scanner code path must be the same.

26. NO SPECIAL-CASE VERDICTS

Repository-wide verify that generalized scan results do not contain logic such as:

if packageName == villaincaller then threat

or equivalent hardcoded verdicts.

VillainCaller may exist in:

baseline registry;
controlled ThreatPack;
enforcement allowlist;
VPN scope.

But the scanner verdict must arise through normal rule/intelligence evaluation.

Add a regression test proving a different package with identical evidence receives equivalent generic treatment where applicable.

27. POLICY BEHAVIOR FOR REAL APPS

Be conservative.

REVIEW

must NOT automatically trigger Device Owner quarantine.

HIGH-RISK PROFILE

should normally ADVISE unless explicit policy says otherwise.

KNOWN THREAT MATCH

may be eligible for stronger policy response.

However, Phase 8 must NOT automatically suspend arbitrary real installed applications.

Keep automatic Device Owner containment restricted to controlled demo targets unless an explicit new safety design is approved later.

For real-world apps:

→ detect
→ explain
→ advise
→ audit.

This prevents an imperfect prototype rule from disabling a legitimate application.

28. AUDIT

Record real-device scan events without bloating the audit chain unnecessarily.

At minimum persist:

scan started;
number of apps analyzed;
scanner/intelligence state;
findings;
known-threat matches;
significant review findings;
scan completed/partial/error;
ThreatPack version.

Do not persist APK contents.

Do not persist signing certificate blobs.

Hashes/fingerprints and structured evidence are sufficient.

29. PRIVACY

The real-world scanner must remain local.

Do not upload:

installed app list;
package names;
hashes;
certificate fingerprints;
permissions;
findings.

Phase 8 uses an offline signed ThreatPack.

No cloud lookup is required for runtime scanning.

External network access may be used during developer-side threat-intelligence acquisition only, never as hidden runtime telemetry.

30. INTELLIGENCE ACQUISITION

If internet access is available to Claude Code during development, it may acquire only approved metadata/IOC feeds supported by the supplied research.

Before acquisition:

read the research;
identify approved source;
verify source/license conditions;
download metadata only;
record provenance;
normalize;
deduplicate;
validate;
build compact ThreatPack;
sign using existing developer-side tooling.

Never download APK samples.

Never download malware archives.

Never execute samples.

Never obtain indicators from random blogs/repos merely to increase count.

If external acquisition cannot be completed safely or licensing is ambiguous:

STOP that acquisition step and continue the scanner using existing intelligence.

Document the blocker.

31. BUILD-TIME INTELLIGENCE TOOLING

Prefer a developer-side tool under:

tools/

Conceptual pipeline:

source metadata
→ parse
→ normalize
→ validate
→ deduplicate
→ apply expiry
→ assign provenance
→ generate ThreatPack
→ sign
→ verify
→ output statistics.

Do not place feed-ingestion code in the Android runtime.

The mobile app should consume the compact signed result, not raw external feeds.

This matches the research recommendation that richer backend/source formats stay off-device while mobile receives a compact signed Thraksha representation.

32. CORPUS QUALITY REPORT

Generate:

temp/PHASE8_THREAT_CORPUS_REPORT.md

Include:

source;
source date;
input count;
accepted count;
rejected count;
duplicates;
expired indicators;
APK hashes;
certificates;
package indicators;
network indicators;
pack version;
signed output size;
verification result;
known limitations.

No untraceable indicator should survive normalization.

33. TESTS — INVENTORY

Add tests for:

arbitrary installed package observation;
package label/version extraction;
requested permissions;
signing fingerprint;
base APK fingerprint;
exported component metadata;
system/user distinction;
missing metadata;
unreadable APK;
app removed during scan;
package changed during scan.

Failure to inspect one app must not crash the entire scan.

Result should become PARTIAL when appropriate.

34. TESTS — GENERIC RULES

Test each generic rule with:

positive fixture;
negative fixture;
boundary condition;
UNKNOWN/missing evidence.

No missing value may be silently interpreted as dangerous.

No single generic rule should automatically label malware unless explicitly justified.

35. TESTS — THREAT INTELLIGENCE

Test:

exact trusted APK hash match;
non-match;
signing certificate match;
weak package-name-only match;
corroborated match;
expired indicator;
disabled indicator;
malformed indicator;
duplicate indicator;
unknown source;
invalid signature;
stale pack;
corrupted pack.

Invalid signed intelligence must never become active.

36. TESTS — GENERALIZATION

Create tests proving:

scanner handles package names unknown at compile time;
scanner does not depend on GoodCaller/VillainCaller;
unknown app gets generic analysis;
unknown app is not forced into CALLER_ID baseline;
no findings produces NO KNOWN FINDINGS;
generic concern produces REVIEW;
trusted hash match produces KNOWN THREAT MATCH;
missing intelligence produces PARTIAL/UNAVAILABLE, not CLEAN.

This section is critical.

37. DEVICE VERIFICATION

On S20 FE:

Install latest Guardian.
Preserve normal existing apps.
Launch Thraksha.
Press SCAN THIS DEVICE.
Record number of apps discovered.
Confirm applications other than Guardian/GoodCaller/VillainCaller are analyzed.
Verify real package labels.
Verify real signing fingerprints.
Verify real APK hashes.
Verify generic findings are evidence-derived.
Verify real ThreatPack is used.
Record honest known-threat result.
Verify UI remains responsive.
Verify audit chain.
Repeat scan and confirm deterministic results unless device state changed.

Do not preselect apps based on desired findings.

38. CONTROLLED POSITIVE VERIFICATION

After real-device scan:

Run VillainCaller through the same generalized scanner.

Expected:

→ controlled profile findings

→ controlled ThreatPack match

→ known positive result.

This proves:

same scanner + different evidence = different outcome.

Do not create a separate "demo scan engine."

39. REQUIRED SCREENSHOTS

Store under:

temp/screenshots/

Capture:

Scan This Device starting;
scan progress;
real scan summary;
real installed-app results;
at least one NO KNOWN FINDINGS result;
REVIEW result if naturally produced;
threat-intelligence details;
VillainCaller controlled KNOWN THREAT MATCH;
audit feed.

If no real app receives REVIEW, do not fabricate one.

40. PRODUCTION INTELLIGENCE — DESIGN ONLY

Do not build the production backend in Phase 8.

Document future architecture only:

external feeds
→ ingestion
→ provenance
→ normalization
→ deduplication
→ confidence/freshness
→ server intelligence DB
→ compact mobile pack
→ signing
→ secure distribution
→ verification
→ atomic activation/rollback.

The supplied research recommends this general architecture and a future move toward indexed SQLite on-device for substantially larger packs.

No backend is required for Phase 8 completion.

41. REQUIRED PROGRESS REPORT

Create/update:

temp/THRAKSHA_DEMO_PHASE_8_PROGRESS.md

Record:

baseline;
files created/modified;
package visibility decision;
apps discovered;
architecture changes;
generic rules;
intelligence sources;
corpus statistics;
ThreatPack version;
builds;
unit tests;
instrumented tests;
device results;
performance;
screenshots;
audit verification;
limitations;
licensing caveats;
divergences;
next step.
42. FINAL VERIFICATION

Before declaring Phase 8 complete:

clean-build all modules;
run complete unit suite;
run complete applicable instrumented suite;
scan S20 FE;
prove unknown real apps are analyzed;
verify certificate fingerprints;
verify APK fingerprints;
verify generic findings;
verify real ThreatPack lookup;
verify VillainCaller controlled positive;
verify GoodCaller;
verify Advice Mode;
verify existing Full Power behavior;
verify Phase 7 Network Guard regression;
verify Rulepack signature;
verify ThreatPack signature;
verify audit chain;
inspect APK assets;
confirm no private keys;
confirm no malware binaries;
confirm no downloaded APK samples;
inspect git working tree.

Do NOT commit automatically.

43. ACCEPTANCE CRITERIA

Phase 8 is complete only when:

Thraksha can scan apps not known at compile time;
real installed applications appear in results;
scan results are evidence-derived;
app fingerprints come from actual installed APKs/certificates;
unknown apps receive generic analysis;
baselines are applied only when justified;
real-world IOC metadata is traceable to source;
no IOC is fabricated;
no malware binary is downloaded;
ThreatPack remains signed and fail-closed;
stale/expired intelligence is handled honestly;
package-name-only matches cannot independently produce authoritative malware verdicts;
real phone may honestly produce zero known-threat matches;
VillainCaller still provides controlled positive proof through the SAME scanner;
real apps cannot be automatically quarantined by immature generic rules;
existing Phase 1–7 functionality remains working;
scan performance is measured;
audit integrity remains valid;
no private user data leaves the phone during runtime scan.
44. STOP CONDITIONS

STOP and report rather than improvise if:

broad app discovery cannot be achieved legitimately;
Android package visibility behavior differs materially from assumptions;
Play-distribution implications cannot be separated from demo behavior;
threat-intelligence licensing/redistribution is unclear;
an IOC lacks provenance;
obtaining intelligence would require downloading malware;
scanner needs access to another app's private data;
a result would require claiming runtime behavior Android cannot observe;
generic rules produce unacceptable false-positive behavior;
arbitrary real apps would need automatic containment;
ThreatPack trust must be weakened;
existing audit integrity regresses;
Phase 1–7 behavior regresses and cannot be isolated.

Never manufacture a threat to make the demo look successful.

45. FINAL PHASE 8 DEMO STORY

The investor demonstration should have two parts.

PART A — REAL DEVICE

Press:

SCAN THIS DEVICE

Thraksha discovers and analyzes actual installed applications.

For example:

47 applications analyzed

47 signing identities checked

47 APK fingerprints checked

0 known threat matches

4 applications require review

The exact numbers must come from the device.

Explain:

"Thraksha did not know what applications were going to be on this phone. These results were generated from the actual installed software."

If there are zero threats, show zero.

That is evidence of scanner integrity.

PART B — CONTROLLED POSITIVE

Then demonstrate VillainCaller.

Same engine.

Same ThreatPack.

Same PolicyEngine.

Different evidence.

→ contextual findings

→ controlled known-threat match

→ THREAT DETECTED

→ Advice or Full Power response

→ encrypted audit evidence.

The message is:

Thraksha is no longer programmed merely to find the demo threat.

It can inspect a real Android device, fingerprint previously unknown installed applications, evaluate observable security characteristics, compare them against cryptographically trusted real-world intelligence, and honestly distinguish between:

NO KNOWN FINDINGS

REVIEW

HIGH-RISK PROFILE

and

KNOWN THREAT MATCH

while preserving the deterministic VillainCaller scenario as a controlled proof that the positive detection-and-response path works.