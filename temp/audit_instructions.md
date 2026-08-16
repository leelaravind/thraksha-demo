# GOAL: Audit Existing Thraksha Project Before Demo Conversion

You are working inside an existing Android Studio project for **Thraksha Guardian**.

Your first job is **NOT to modify, refactor, delete, generate, or fix code**.

Your job is to thoroughly crawl and understand the existing project and produce an accurate technical audit so we can decide how to convert the partially completed application into a focused **investor/concept demo**.

## PRODUCT CONTEXT

Thraksha is an Android security guardian designed to monitor what applications do after installation.

Core idea:

**The OS checks whether an app is allowed onto the phone. Thraksha watches what the app does after it gets inside — and, when it has sufficient authority, acts.**

The final production vision is larger, but we are NOT trying to complete the full product now.

We want to reuse the strongest parts of the existing project and turn it into a reliable, visually clear demo.

The intended demo will eventually focus on:

- Behaviour monitoring
- App-type-aware permission/behaviour rules
- A well-behaved decoy app such as `GoodCaller`
- A controlled misbehaving decoy such as `VillainCaller`
- Detecting suspicious photo/media access
- Detecting installed-app-list access
- Detecting suspicious outbound behaviour
- Device Owner / Full Power mode
- Normal-install / Advice mode
- Actual intervention where Android authority permits it
- Alert-only behaviour where authority does not permit intervention
- Panic/Safe Mode
- Security action button
- Audit/event history
- Clear dashboard states:
  - Watching
  - Threat Detected
  - Acted
  - Advised
- Clear FULL POWER / ADVICE MODE indicator

However, **do not assume any of these need to be built from scratch.**

The existing project is partially implemented and may already contain reusable foundations.

## YOUR TASK

Perform a deep read-only audit of the entire repository.

Start by mapping the project structure.

Inspect all relevant:

- Gradle/build configuration
- AndroidManifest files
- Kotlin/Java source
- Compose/XML UI
- packages/modules
- services
- receivers
- workers
- repositories
- databases/storage
- security components
- DevicePolicyManager/Device Owner logic
- VPN/network components
- permission monitoring
- rule/rulepack systems
- event systems
- audit logging
- cryptography/encryption
- dashboard
- panic/lockdown functionality
- app scanning/monitoring
- notification systems
- tests
- assets/resources/configuration
- TODO/FIXME/stub/mock/placeholder code

Search references and call paths instead of judging files only by their names.

## DETERMINE WHAT ACTUALLY EXISTS

For every major subsystem classify it as:

**WORKING** — appears substantially implemented and wired together.

**PARTIAL** — meaningful implementation exists but is incomplete, disconnected, mocked, or unreliable.

**STUB/PLACEHOLDER** — structure exists without real functionality.

**MISSING** — required capability does not exist.

**UNCLEAR** — cannot safely determine without running/testing it.

Do not claim functionality exists merely because a class, interface, button, or method exists.

Trace whether it is actually connected and used.

## SPECIFICALLY INVESTIGATE

Determine whether the project already contains usable implementations for:

1. Device Owner detection
2. Device Admin receiver
3. DevicePolicyManager actions
4. Permission revocation
5. App suspension/freezing/quarantine
6. VPN/VpnService
7. Per-app network identification/control
8. Panic/lockdown mode
9. Whitelisting/trusted apps
10. Security event/event-bus architecture
11. Rulepack/rule engine
12. Behaviour monitoring
13. App categorisation/baselines
14. Audit logging
15. Tamper-resistant/hash-chained logging
16. SQLCipher/encrypted storage
17. Android Keystore usage
18. Dashboard/security status UI
19. Existing threat cards/alerts
20. Mode detection and UI representation
21. Any assistant/action execution components
22. Existing tests and demo/test utilities

Also identify functionality unrelated to the focused demo that could safely remain untouched.

## ARCHITECTURE TRACE

Explain the real runtime flow as it currently exists.

For example:

App startup  
→ initialization  
→ mode detection  
→ services  
→ monitoring/detection  
→ event generation  
→ rules/evaluation  
→ response/action  
→ persistence/audit  
→ UI update

Use the project's actual classes/functions in this trace.

If the architecture differs from this example, document the actual architecture.

## DEMO GAP ANALYSIS

After understanding the existing implementation, compare it against the intended focused demo.

For each demo capability state:

- Already available
- Reusable with minor modification
- Requires significant modification
- Must be built new
- Should be excluded from the demo

Prioritize **reuse over rewriting**.

We do NOT want to finish every unfinished production feature.

The objective is the smallest technically credible path from the current project to a stable demo.

## IDENTIFY RISKS

Flag:

- broken/incomplete integrations
- duplicate implementations
- dead code
- architectural conflicts
- Android API limitations
- permissions that cannot work as currently designed
- Device Owner assumptions
- VPN limitations
- background execution problems
- compile/runtime risks
- security-sensitive mistakes
- hard-coded/mock behaviour presented as real functionality
- anything likely to fail on Android 13
- anything that could make the demo unreliable

Clearly separate:

**REAL Android capability**

from

**SIMULATED/DEMO behaviour**

from

**IMPOSSIBLE/UNSUPPORTED behaviour**

Never recommend pretending a simulated action is a real OS security action.

## OUTPUT

Create a report named:

`THRAKSHA_CURRENT_PROJECT_AUDIT.md`

Place it in the project root.

Structure it as:

1. Executive Summary
2. Repository/Module Map
3. Current Architecture
4. Existing Feature Inventory
5. Security Infrastructure Audit
6. UI/Dashboard Audit
7. Device Owner & Advice Mode Audit
8. VPN/Network Audit
9. Rules & Behaviour Detection Audit
10. Audit Log/Storage Audit
11. Panic/Safe Mode Audit
12. Dead/Incomplete/Placeholder Code
13. Android/API Feasibility Issues
14. Demo Gap Analysis
15. What We Should Reuse
16. What We Should Modify
17. What We Should Build New
18. What We Should Ignore/Defer
19. Recommended Demo Architecture
20. Recommended Implementation Order
21. Risks/Blockers
22. Questions Requiring Human Decision

Include actual file paths, class names, functions, and relevant relationships as evidence.

## CRITICAL RULES

**DO NOT MODIFY APPLICATION CODE.**

**DO NOT DELETE ANYTHING.**

**DO NOT REFACTOR ANYTHING.**

**DO NOT IMPLEMENT THE DEMO YET.**

**DO NOT INSTALL NEW DEPENDENCIES.**

**DO NOT CHANGE GRADLE OR MANIFEST FILES.**

You may only inspect/search/read the repository and create/update:

`THRAKSHA_CURRENT_PROJECT_AUDIT.md`

Be skeptical.

If something cannot be proven from static inspection, mark it **UNCLEAR / NEEDS RUNTIME TESTING** instead of guessing.

The goal of this run is:

**UNDERSTAND FIRST. CODE LATER.**

Begin by crawling the complete repository, then produce the audit.