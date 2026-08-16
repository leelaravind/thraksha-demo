

This phase should be treated as the **final product/UI implementation phase**. Stitch is now directly accessible from Claude Code through MCP, so Claude should retrieve the approved designs itself, create a local design inventory/reference set, map every screen to real Thraksha state, and only then start modifying Compose.

# THRAKSHA DEMO — PHASE 11B IMPLEMENTATION GUIDE

## STITCH-DRIVEN COMPOSE REDESIGN + REAL STATE WIRING + INVESTOR HARDENING

## 1. PURPOSE

Phase 11B converts the existing fully functional Thraksha Guardian demo into a polished, responsive, consumer-friendly investor experience.

The objective is NOT to add new security, automation or AI capabilities.

The objective is to:

* retrieve the approved Google Stitch designs through MCP;
* preserve those designs locally as implementation references;
* map every design to existing real Thraksha behavior;
* create one coherent Compose design system;
* redesign Protect, Automate, Audit and supporting screens;
* wire every UI element to real Phase 1–10 state;
* preserve all existing security/automation/AI architecture;
* support light and dark themes;
* support different Android phone dimensions;
* minimize technical friction;
* verify the final result on the Samsung S20 FE;
* run complete regression testing before freezing the demo.

Core rule:

> **Stitch is the VISUAL specification.
> Existing Thraksha code is the FUNCTIONAL specification.
> If they disagree, runtime truth wins.**

---

# 2. KNOWN-GOOD BASELINE

Treat Phases 1–10 as frozen functional baseline.

Preserve:

* SQLCipher storage;
* Android Keystore;
* hash-chained AuditLog;
* SecurityEventBus;
* scanner;
* AppInventory;
* ThreatPack;
* Rulepack;
* Network Guard;
* evidence progression;
* User ACT;
* Advice Mode;
* Device Owner / Full Power behavior;
* Phase 9 AutomationEngine;
* AutomationIntent;
* AutomationSafetyPolicy;
* AutomationPlanner;
* executors;
* snapshot / restore;
* Gemma / LiteRT-LM integration;
* constrained AI output;
* AI validation;
* explicit START requirement;
* offline inference;
* deterministic automation fallback.

Do NOT redesign backend architecture merely to simplify UI implementation.

---

# 3. STITCH MCP — PRECHECK

Before modifying application code:

Verify Stitch MCP is available.

Expected:

`stitch — connected — 15 tools`

Use Stitch MCP to:

1. list projects;
2. identify the approved Thraksha project;
3. inspect project metadata;
4. list all screens;
5. retrieve every approved screen;
6. identify light/dark variants;
7. identify supporting screens;
8. identify reusable visual patterns.

Do NOT generate new designs unless a required implementation reference genuinely does not exist.

Do not alter the approved Stitch project during implementation unless explicitly necessary.

---

# 4. CREATE LOCAL DESIGN REFERENCE STRUCTURE

Create:

```text
temp/ui_design_reference/
```

Recommended structure:

```text
temp/ui_design_reference/
├── 00_index/
│   ├── STITCH_PROJECT_INVENTORY.md
│   ├── SCREEN_STATE_MAPPING.md
│   └── DESIGN_IMPLEMENTATION_STATUS.md
│
├── protect/
├── automate/
├── audit/
├── settings/
├── onboarding/
├── supporting/
│
├── light/
├── dark/
│
└── screenshots/
```

Retrieve and preserve enough Stitch information locally so implementation does not depend on repeatedly rediscovering the project.

Where Stitch MCP exposes downloadable/exportable screen assets or references, save them locally.

Where direct binary export is not exposed, record:

* screen ID;
* project ID;
* screen name;
* theme;
* layout hierarchy;
* major components;
* spacing;
* typography;
* status semantics;
* interactions;
* relevant Stitch metadata.

Never invent an asset simply because Stitch MCP does not expose a download.

---

# 5. STITCH PROJECT INVENTORY

Create:

`temp/ui_design_reference/00_index/STITCH_PROJECT_INVENTORY.md`

For every Stitch screen record:

* Stitch screen ID;
* screen name;
* Light / Dark;
* product area;
* implementation state;
* corresponding existing Thraksha screen/state;
* whether design is approved;
* whether any content is fictional/unsupported;
* implementation notes.

The inventory must cover all approved screens before coding begins.

---

# 6. SCREEN → REAL STATE MAPPING

Create:

`temp/ui_design_reference/00_index/SCREEN_STATE_MAPPING.md`

For every visual screen/state classify:

`STITCH DESIGN`

→ `REAL THRAKSHA STATE`

→ `REAL DATA SOURCE`

→ `REAL ACTION`

Example:

`Protect Dashboard`

→ scanner/security summary

→ SecurityAuditEngine / ThreatPack / NetworkGuard state

→ Scan Device.

Another:

`Meeting Mode Active`

→ AutomationEngine ACTIVE

→ active run + verified action results

→ STOP & RESTORE.

No UI implementation begins until this mapping is substantially complete.

---

# 7. REMOVE STITCH-INVENTED FUNCTIONALITY

Audit all Stitch designs before implementation.

Explicitly reject unsupported concepts such as any design that invents:

* Accessibility automation if not used;
* background sync executor;
* arbitrary app blocking;
* login anomaly blocking;
* backup automation;
* unsupported routines;
* fake threat detections;
* fake cloud protection;
* fake “real-time feed” behavior;
* fake device-security percentages;
* unsupported AI actions.

Current automation routines remain:

* Meeting
* Focus
* Driving

Any Stitch concept that does not map to real Thraksha functionality must be:

* removed;
* replaced with the real corresponding feature;
* or marked unavailable.

Do not implement functionality merely because Stitch displayed it.

---

# 8. BRANDING

The authoritative Thraksha identity is the existing branding already implemented in the Android project.

Locate the existing implementation, including:

`BrandingComponents.kt`

Reuse the existing Thraksha brand/logo.

Do NOT implement the generated Stitch TR monogram/logo.

If the existing branding is vector/code-rendered, keep it that way unless there is a concrete reason to change it.

Create a single shared brand component rather than duplicating logo logic across screens.

---

# 9. UI PRODUCT PRINCIPLE

The final app must be:

* neat;
* uncluttered;
* calm;
* understandable to non-technical users;
* premium;
* responsive;
* accessible.

Primary rule:

> **SIMPLE FIRST — DETAILS ON DEMAND**

Main surfaces should answer:

1. What is happening?
2. Does anything need attention?
3. What can I do?

Technical details belong behind:

* View Evidence
* Technical Details
* More Information

Do not expose raw:

* permission constant names;
* hashes;
* rule IDs;
* ThreatPack schema;
* internal engine names;
* Android API terminology

on consumer-facing primary screens unless genuinely necessary.

---

# 10. LANGUAGE ACCURACY

Do not make absolute security claims.

Avoid:

`Your device is safe`

`100% secure`

`Virus free`

`Connection secure`

unless technically proven in that context.

Prefer:

* Protection active
* No urgent threats detected
* Monitoring active
* No known threat matches
* Review recommended
* 1 item needs attention
* Previous settings restored
* Local AI ready

All text must reflect actual implementation truth.

---

# 11. SHARED COMPOSE DESIGN SYSTEM FIRST

Before redesigning individual screens, create/refine shared design primitives.

Suggested structure:

```text
ui/design/
```

Possible components:

* `ThrakshaTheme`
* `ThrakshaColors`
* `ThrakshaTypography`
* `ThrakshaSpacing`
* `ThrakshaShapes`
* `ThrakshaStatusChip`
* `ThrakshaCard`
* `ThrakshaPrimaryButton`
* `ThrakshaSecondaryButton`
* `ThrakshaSectionHeader`
* `ThrakshaTopBar`
* `ThrakshaBottomNavigation`
* `ThrakshaEmptyState`
* `ThrakshaErrorState`
* `ThrakshaLoadingState`
* `ThrakshaEvidenceRow`

Avoid duplicating visual constants throughout Compose files.

---

# 12. LIGHT + DARK THEMES

Implement full theme parity.

Every approved screen must work in:

* LIGHT
* DARK

Do not maintain separate screen implementations.

Theme should modify design tokens rather than duplicate layout trees.

Verify:

* contrast;
* surface hierarchy;
* status colors;
* disabled controls;
* text readability;
* dividers;
* icons;
* cards;
* alerts.

Respect system theme where appropriate.

---

# 13. RESPONSIVE ANDROID LAYOUT

Do not reproduce Stitch as fixed screenshots.

Target responsive layout behavior.

Primary phone width range:

~360–430dp portrait.

Also behave reasonably outside that range.

Use:

* `Scaffold`
* `WindowInsets`
* `fillMaxWidth`
* adaptive padding
* `LazyColumn` / scrolling
* weighted/flexible layout
* Material 3 principles

Avoid:

* absolute positioning;
* hardcoded pixel sizes;
* fixed-height entire pages;
* tiny text to force fit;
* overlaying bottom navigation on content.

Lower content should scroll rather than compress.

---

# 14. APP SHELL

Create a consistent shell around:

**Protect | Automate | Audit**

Bottom navigation should:

* remain visible;
* preserve destination state where practical;
* handle system navigation correctly;
* use real route/state management;
* visually match Stitch.

Settings may be reachable from top-level app bar rather than bottom navigation.

---

# 15. PROTECT — IMPLEMENTATION ORDER

Implement Protect first.

Required states:

### Protect Dashboard

Show:

* current protection state;
* last scan;
* Scan Device;
* compact Threat Intelligence status;
* Network Guard status;
* scanner summary;
* automation state where appropriate;
* compact attention card.

No detailed evidence dumped directly onto dashboard.

### Scan Progress

Wire to actual scan state:

* DISCOVERING
* ANALYZING
* COMPLETE
* PARTIAL
* ERROR

Do not use fake progress percentage if unavailable.

### Scan Results

Use actual runtime counts:

* apps checked;
* findings;
* review items;
* known-threat matches.

### Finding Details

Show consumer summary first.

Then:

`DECLARED → GRANTED → OBSERVED`

using actual Phase 8.1 evidence.

### Technical Evidence

Expandable section.

Show technical values only when requested.

### User ACT

Wire to existing real Phase 8.1 ACT system.

Never display ACTED until underlying verified result says ACTED.

### Threat Intelligence

Show real ThreatPack state.

### Network Guard

Show real Phase 7 state.

---

# 16. AUTOMATE — HERO PRODUCT AREA

Implement Automate after Protect.

ASK THRAKSHA should be the hero interaction.

Required flow:

`User input`

→ `Understanding...`

→ intent result

→ plan preview

→ explicit START

→ ACTIVE

→ STOP & RESTORE

→ restoration verified.

Do not change this safety boundary.

---

# 17. ASK THRAKSHA STATES

Implement real model states:

* model unavailable;
* verifying;
* loading;
* ready;
* understanding;
* plan ready;
* clarify;
* unsupported;
* rejected;
* error.

The ~11.5 second local inference delay must look intentional.

Use:

`Understanding…`

No fake percentages.

No fake token streaming unless actual runtime supports it.

---

# 18. AI OUTPUT

No model-authored prose should become authoritative UI text.

UI should derive human-readable descriptions from:

* validated AutomationIntent;
* deterministic AutomationPlan;
* engine state.

The user should see:

`Thraksha understood: Meeting Mode — 45 minutes`

Then deterministic planner actions.

---

# 19. PLAN PREVIEW

Plan Preview must use actual Planner output.

Show:

* supported actions;
* user-assisted actions;
* optional actions;
* unsupported actions if relevant.

No Stitch-invented actions.

Explicit:

**START**

required.

---

# 20. ACTIVE AUTOMATION

Wire to actual AutomationEngine ACTIVE state.

Show:

* routine;
* duration/end;
* applied actions;
* verification status;
* user-assisted items;
* STOP & RESTORE.

Do not pretend app launch is verified ACTED if implementation classifies it as OPENED.

---

# 21. RESTORATION UI

Wire to actual restore result.

States:

* restoring;
* restored;
* partial restore;
* restore failed.

Success screen should show that previous settings were restored only when verification confirms it.

---

# 22. AUDIT

Redesign existing audit feed into a human-readable timeline.

Main timeline should use:

* Security
* Automation
* AI interpretation
* User action

as understandable categories.

Examples:

`Device scan completed`

`App needs review`

`Thraksha understood Meeting Mode`

`Automation started`

`DND verified`

`Previous settings restored`

Detailed event view may expose technical evidence.

Do not expose audit internals by default.

---

# 23. SETTINGS

Implement Settings using existing capabilities.

Suggested sections:

### General

* Appearance
* Notifications

### Protection

* protection status
* Network Guard status
* scanning/security status

### Automation

* automation permissions/access
* routine-related settings where real

### On-device AI

* model status
* local/offline status
* model readiness

### Privacy & Access

* permissions
* privacy/data

### About

* About Thraksha
* Open-source licenses

Do not invent configurable features that do not exist.

---

# 24. ON-DEVICE AI STATUS

Consumer surface:

`Local AI Ready`

or actual state.

Technical section may show:

* model name;
* model integrity status;
* runtime;
* local execution status.

Do not overwhelm primary UI.

Do not claim cloud independence if the runtime state contradicts it.

---

# 25. PERMISSIONS & ACCESS

Use actual required access from the app.

Possible real examples include:

* Notification Policy Access;
* Modify System Settings;
* notification permission;
* other existing real requirements.

Do NOT invent Accessibility Service.

Each permission should show:

* why it is needed;
* current state;
* action.

Use existing Android settings deep-links.

---

# 26. ABOUT

Use existing logo.

Show:

* Thraksha Guardian
* app version
* build information if useful
* concise product description
* privacy
* licenses / acknowledgements

Do not expose development internals unnecessarily.

---

# 27. PRIVACY & DATA

Explain in simple language:

* on-device AI;
* local runtime;
* local automation processing;
* local scan behavior;
* what is stored;
* audit behavior.

Only state claims supported by implementation.

Keep technical details expandable.

---

# 28. PROGRESSIVE DISCLOSURE

Primary UI:

simple.

Secondary details:

more explanation.

Technical details:

developer/security evidence.

Example:

`1 item needs attention`

↓

Review

↓

`Location was available and recent activity was observed`

↓

View Evidence

↓

Declared / Granted / Observed

↓

Technical details

↓

permission IDs / timestamps / evidence provenance.

---

# 29. NO FUNCTIONAL MOCK DATA IN PRODUCTION UI

Mock/sample data may be used only in Compose previews/tests.

Runtime application must never hardcode:

* scan counts;
* threats;
* model responses;
* finding severity;
* automation state;
* audit events;
* threat intelligence status.

Investor demo results must come from real engines.

---

# 30. STITCH VISUAL COMPARISON

After each major surface:

1. retrieve corresponding Stitch screen;
2. run app;
3. capture real S20 FE screenshot;
4. compare layout/hierarchy;
5. fix visual mismatch without altering functional semantics.

Store implementation screenshots under:

```text
temp/ui_design_reference/screenshots/
```

Use descriptive names:

`protect_dashboard_dark.png`

`automate_plan_preview_light.png`

etc.

---

# 31. IMPLEMENT INCREMENTALLY

Required implementation order:

1. Stitch inventory
2. real-state mapping
3. design tokens/components
4. app shell/navigation
5. Protect
6. Automate
7. Audit
8. Settings/support
9. light/dark
10. responsive/accessibility
11. final visual comparison
12. regression

Do not replace the entire Compose tree in one edit.

Build/test after each major product area.

---

# 32. ACCESSIBILITY

Verify:

* touch target sizes;
* content descriptions;
* screen-reader semantics;
* contrast;
* focus order;
* dynamic text behavior where practical;
* button labels;
* status not conveyed solely through color.

Keep accessibility support distinct from Android AccessibilityService automation.

---

# 33. S20 FE VERIFICATION

The connected S20 FE is the primary real-device UI target.

Verify:

* portrait layout;
* status bar;
* navigation bar;
* keyboard;
* scrolling;
* bottom nav;
* long text;
* light theme;
* dark theme;
* permission dialogs/settings returns;
* AI input;
* model wait state;
* plan preview;
* active routine;
* restore;
* scan;
* finding review;
* audit.

Do not alter phone settings permanently.

---

# 34. SECURITY / AUTOMATION REGRESSION

UI work must not break functionality.

Run complete relevant suites after redesign.

Verify:

* Phase 8 scanner;
* Phase 8.1 evidence;
* User ACT;
* ThreatPack;
* Rulepack;
* Network Guard;
* Advice Mode;
* Device Owner;
* GoodCaller;
* VillainCaller;
* audit-chain verification;
* Phase 9 Meeting;
* Focus;
* Driving;
* snapshot/restore;
* failure rollback;
* process recovery;
* Phase 10 local AI;
* hostile prompt rejection;
* explicit START;
* offline AI;
* deterministic fallback.

---

# 35. STITCH CREDENTIAL SAFETY

Stitch MCP is a development tool only.

Do not put:

* Stitch API key;
* MCP credentials;
* Stitch endpoint credentials

in application source, Gradle, resources, assets, temp docs intended for distribution, or APK.

Search before completion.

The proxy remains under:

`tools/`

and must never enter runtime app dependencies.

---

# 36. MCP PROXY

`tools/stitch_mcp_proxy.py`

is a temporary development shim.

Do not import it into app code.

Document that it exists only because of the upstream Stitch MCP schema defect.

Phase 11B must not depend on the proxy at runtime.

---

# 37. DESIGN STATUS TRACKER

Maintain:

`temp/ui_design_reference/00_index/DESIGN_IMPLEMENTATION_STATUS.md`

Suggested rows:

| Screen | Stitch | Compose | Wired | Dark | Light | S20 FE | Tests |
| ------ | ------ | ------- | ----- | ---- | ----- | ------ | ----- |

Do not declare Phase 11B complete while core rows remain incomplete.

---

# 38. REQUIRED PHASE 11B REPORT

Create:

`temp/THRAKSHA_DEMO_PHASE_11B_PROGRESS.md`

Record:

* Stitch project identification;
* number of screens retrieved;
* screen inventory;
* screen/state mapping;
* unsupported Stitch concepts rejected;
* branding decision;
* design-system architecture;
* files changed;
* Protect implementation;
* Automate implementation;
* Audit implementation;
* Settings/support implementation;
* dark/light status;
* responsive status;
* accessibility;
* real-device results;
* screenshots;
* tests;
* regressions;
* limitations;
* git status.

---

# 39. FINAL VISUAL ACCEPTANCE

Before completion verify:

### Protect

A non-technical user can understand:

* status;
* attention;
* Scan Device;
* Review.

### Automate

A user can understand:

* what they asked;
* what Thraksha understood;
* what will happen;
* START;
* what is active;
* how to restore.

### Audit

A user can understand:

* what happened;
* when;
* outcome.

Technical evidence must still be accessible without dominating the experience.

---

# 40. FINAL INVESTOR FLOW

The polished product should support this sequence:

### PROTECT

Open Thraksha.

See:

`Protection active`

Run:

`Scan Device`

Review genuine device result.

Open evidence if desired.

### AUTOMATE

Open:

`Ask Thraksha`

Enter:

`I have a meeting for 45 minutes.`

Show:

`Understanding…`

Then:

`Thraksha understood: Meeting Mode — 45 minutes`

Show deterministic plan.

Press:

`START`

Show verified actions.

Then:

`STOP & RESTORE`

Show successful restoration.

### AUDIT

Open Audit.

Show the timeline containing:

* scan;
* finding;
* AI interpretation;
* automation;
* verified state changes;
* restoration.

This tells the complete Thraksha story without needing technical explanation.

---

# 41. DO NOT ADD NEW FEATURES

Phase 11B is not the place to implement:

* new malware engines;
* behavioral monitors;
* new AI models;
* new automation executors;
* additional routines;
* cloud services;
* security-triggered automation expansion;
* fast-path parser;
* Siri/Gemini-like general assistant behavior.

Document future ideas instead.

---

# 42. STOP CONDITIONS

STOP and report rather than improvise if:

* Stitch MCP cannot retrieve approved screens;
* design assets are unavailable;
* screen cannot be mapped to a real state;
* Stitch requires unsupported functionality;
* a redesign requires weakening functional architecture;
* scanner/automation/AI regress;
* UI requires hardcoded fake runtime data;
* real status cannot be represented honestly;
* responsive layout cannot be achieved without major architecture breakage;
* credentials appear in source;
* redesign would require rebuilding Phase 1–10 logic.

Functional correctness wins over visual similarity.

---

# 43. ACCEPTANCE CRITERIA

Phase 11B is complete only when:

* Stitch MCP successfully retrieves approved designs;
* local design inventory exists;
* all core screens are mapped to real state;
* unsupported Stitch content is removed;
* existing Thraksha branding is preserved;
* shared Compose design system exists;
* Protect redesigned and wired;
* Automate redesigned and wired;
* Audit redesigned and wired;
* Settings/support screens implemented;
* light/dark parity exists;
* responsive layouts work;
* technical details use progressive disclosure;
* UI is consumer-friendly;
* all runtime data is genuine;
* ASK THRAKSHA preserves explicit START;
* restoration remains verified;
* S20 FE visual acceptance passes;
* full Phase 1–10 regressions pass;
* Stitch credentials are absent from app/repo-sensitive locations;
* nothing is committed automatically.

---

# 44. FINAL PHASE 11B PRODUCT PRINCIPLE

The final app should feel simple even though the system underneath it is complex.

The user should never need to understand:

* threat-pack schemas;
* Android policy APIs;
* model runtimes;
* hashes;
* permission constants;
* automation executors;
* audit-chain cryptography

to use Thraksha.

But if a technical user wants evidence, it should still be available.

The final product principle is:

> **Complex underneath. Simple on the surface. Evidence when requested.**

That is the completion target for Phase 11B.
