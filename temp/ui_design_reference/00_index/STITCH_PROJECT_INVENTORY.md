# STITCH PROJECT INVENTORY — Phase 11B

**Retrieved:** 2026-08-15 via Stitch MCP (`stitch` server, connected, 15 tools).

## Project

| Field | Value |
| --- | --- |
| Resource name | `projects/11183523763892934666` |
| Title | Thraksha Guardian Security Hub |
| Project type | `TEXT_TO_UI_PRO` |
| Device type | `MOBILE` (390 × N dp frames, exported at 780 px @2x) |
| Design system | **Guardian Prime** (embedded `designMd`, dark-first) |
| Created / updated | 2026-08-15T16:05:46Z / 2026-08-15T16:52:02Z |
| Visibility | PRIVATE, role OWNER |
| Screens | **33** (32 UI screens + 1 generated logo asset) |

The project was **not modified** during Phase 11B. No screens were generated, edited or
deleted — retrieval only (`list_projects`, `list_screens`, HTML/PNG export).

## Preserved locally

Every screen's exported Tailwind HTML **and** its rendered PNG were downloaded:

```
temp/ui_design_reference/html/<slug>.html          32 files
temp/ui_design_reference/screenshots/stitch_<slug>.png   32 files
```

Signed Google export URLs are deliberately **not** recorded in this repository-visible
document (they are short-lived credentials-bearing URLs). Screens are identified by
Stitch screen ID below; the assets themselves are the durable reference.

## Design system — Guardian Prime (authoritative token source)

Dark-first Material 3 derivative. Key tokens as published by Stitch:

| Role | Dark value | Notes |
| --- | --- | --- |
| background / surface | `#121315` | true canvas |
| surface-container-lowest → highest | `#0d0e10` `#1b1c1e` `#1f2022` `#292a2c` `#343537` | tonal layering |
| card surface (used inline in screens) | `#161719` | "Level 1" |
| primary | `#a4e6ff` | on-primary `#003543` |
| primary-container | `#00d1ff` | Technical Teal — primary CTA fill |
| secondary-container | `#1e24db` | nav pill / Deep Indigo `#4d57ff` family |
| on-surface / on-surface-variant | `#e3e2e5` / `#bbc9cf` | |
| outline / outline-variant | `#859399` / `#3c494e` | |
| error / error-container | `#ffb4ab` / `#93000a` | on-error-container `#ffdad6` |
| primary-fixed-dim | `#4cd6ff` | "OK/active" status accent |

Type: **Inter** (headline-lg 32/40 −0.02em 700, headline-lg-mobile 28/36, headline-md
24/32 600, body-lg 16/24, body-md 14/20, label-md 12/16 500) and **JetBrains Mono**
(label-caps 12/16, +0.1em, uppercase) for technical labels/status chips.

Shape: buttons/inputs 12 dp, cards 24 dp, chips/nav-indicator pill. Spacing: 8 dp base,
16 dp mobile margin, 20 dp container padding, 24–32 dp between logical sections.
Elevation by tonal layer + very soft ambient shadow; status conveyed by a 4 px left
ribbon on cards, never by tinting the whole card.

## Screen inventory

Legend — **Impl**: `BUILT` implemented in Compose · `PARTIAL` selectively adopted ·
`REF` visual reference only · `REJECTED` not implemented (see reasons).
Theme variants are **not** separate implementations: one Compose screen, tokenised.

| # | Stitch screen ID | Name | Theme | Area | Impl | Thraksha counterpart | Fictional content present? |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | `f8cd8d37dfc34d398be0bda964421dbf` | Protect Dashboard | Dark | Protect | REF | ProtectScreen | yes — "254 Apps Analyzed", "VPN: Encrypted", fixed evidence text |
| 2 | `7c363948e1cf4f90861baebb6e60c77b` | Protect Dashboard (Light Mode) | Light | Protect | REF | ProtectScreen | yes — same as #1 |
| 3 | `230268fd1d88412988551d923ca6df8f` | Protect Dashboard (Responsive Dark) | Dark | Protect | PARTIAL | ProtectScreen | yes — same as #1 + inline evidence block |
| 4 | `a87ccfba145b4f119c4df14899358fea` | Protect Dashboard (Responsive Light) | Light | Protect | PARTIAL | ProtectScreen | yes — same as #3 |
| 5 | `5e2c142e49084a9cbc7fdd8c7b7c6bde` | **Protect Dashboard (Refined Dark)** | Dark | Protect | **BUILT** | ProtectScreen | yes — "Your device is safe", "Connection is secure" |
| 6 | `78ea97f0c8be44d493d60615aea62a67` | **Protect Dashboard (Refined Light)** | Light | Protect | **BUILT** | ProtectScreen | yes — "Your device is protected", "Real-time protection is active" |
| 7 | `a9a00566d20648128e4cf39c39b6a036` | Scan in Progress (Light) | Light | Protect | BUILT | ProtectScreen scanning state | yes — scripted rotating status strings |
| 8 | `d3e187c5fabb4818821582585a3b7b23` | Scan in Progress (Dark) | Dark | Protect | BUILT | ProtectScreen scanning state | no (generic copy) |
| 9 | `c2780711b3494eb5a9f16bafa35eb172` | Scan Results – Findings (Light) | Light | Protect | BUILT | ProtectScreen results | yes — "254 apps", named app, "No suspicious connections" |
| 10 | `bc1de9ebc45c4c07baeaa90a79280d3d` | Scan Results – Partial (Dark) | Dark | Protect | BUILT | ScanOutcome.PARTIAL banner | yes — "System Root Directories", "External Network Interfaces" |
| 11 | `3564528285434f4eabcbc71c07a439b9` | Finding Details (Light) | Light | Protect | BUILT | FindingDetailScreen | yes — fabricated app + dates |
| 12 | `fe54b047377f4c2fb224f7730cd7910b` | Finding Details (Dark) | Dark | Protect | BUILT | FindingDetailScreen | yes — "Fitness Tracker Pro", lat/long, API call string |
| 13 | `1811618862cf419f94f5d9b37cc7ddcd` | Automate Home (Light) | Light | Automate | PARTIAL | AutomateScreen | yes — Suggested routines, Wind Down/Workout/Morning Brief, toggles |
| 14 | `0fdeaa06e98c4645a83633610c76125f` | Automate Home (Dark) | Dark | Automate | **BUILT** | AutomateScreen | yes — "secures mic/cam", "Blocks social media", "forces navigation pin" |
| 15 | `30dded00c43444e78bb6e4051a5e474c` | Ask Thraksha – Clarify (Light) | Light | Automate | BUILT | AutomateScreen CLARIFY state | no (structure only) |
| 16 | `6b6953f044e143d991fd6ceb7cd4e8c5` | Plan Preview (Light) | Light | Automate | BUILT | PlanPreviewSheet | yes — invented 3-action plan |
| 17 | `0066a5d7ddc24ef5b51bd43e645c2788` | Plan Preview (Dark) | Dark | Automate | **BUILT** | PlanPreviewSheet | yes — "Pause background sync", "Set status to Busy" |
| 18 | `c3b0cc750b9e4ea095b58082a5b8d8e5` | Active Automation (Light) | Light | Automate | BUILT | AutomateScreen ACTIVE state | yes — "Calendar integration", "Silenced 3 notifications", enterprise presence |
| 19 | `3eb8fc57c4b34a26905b0ff9b47edf66` | Restoration Complete (Dark) | Dark | Automate | BUILT | AutomateScreen RESTORED state | no |
| 20 | `f9dcf3bb9606472fb496c1e4a554d9f3` | Audit Timeline (Light) | Light | Audit | PARTIAL | AuditScreen | yes — backup automation, login blocking, IP blacklisting, "Files: 142k" |
| 21 | `99ddca049fd444639957a4863a78ee7a` | Audit Timeline (Dark) | Dark | Audit | **BUILT** | AuditScreen | yes — same family as #20 |
| 22 | `0be7f466c58f46729156158810e47d74` | Audit Event Detail (Dark) | Dark | Audit | BUILT | AuditEventDetailScreen | yes — file counts, CPU %, engine version, synthetic log |
| 23 | `e9a56f1e8766446ea0b01bc47e607c6f` | Settings (Light) | Light | Settings | PARTIAL | SettingsScreen | yes — user profile card, Sign Out |
| 24 | `a167a3a2888c4b49ba9f0753bc6087cf` | Settings (Dark) | Dark | Settings | **BUILT** | SettingsScreen | no |
| 25 | `2a124629b6de45bbaf57f34e5eb160b7` | Permissions & Access (Light) | Light | Settings | PARTIAL | PermissionsScreen | yes — "Background Monitoring", "Revoke", defensive-response copy |
| 26 | `9b40c78e51d147eba402046dae761188` | Permission Guidance (Light) | Light | Settings | REJECTED | — | yes — **Accessibility Service** for security automation |
| 27 | `3d3f2ec6806b4cda90af038f41383f40` | Permission Guidance (Dark) | Dark | Settings | REJECTED | — | yes — **Accessibility Service** requirement |
| 28 | `29e92fd68126460cb71c8d70ed971aaa` | On-device AI (Light) | Light | Settings | BUILT | OnDeviceAiScreen | yes — "secure enclave", "NPU Active", "TG-Guard-v4.2-quantized" |
| 29 | `91fc043e756c4488b26b1d072e26c53f` | Privacy & Data (Light) | Light | Settings | BUILT | PrivacyDataScreen | yes — "< 5ms", `/opt/thraksha/data`, plaintext logs, model update sync |
| 30 | `5a3b9942081d4cd0b3896b4d9912cd5c` | About Thraksha (Light) | Light | Settings | BUILT | AboutScreen | yes — Suricata/OSSEC/Zeek/YARA/OpenSSL, Privacy Policy/ToS links |
| 31 | `a39ce0bf72ba49708bf7fa4f3af4c80f` | Welcome (Light) | Light | Onboarding | REF | existing SplashScreen | yes — "Log In" |
| 32 | `c7c9c8fbccb84a08aa6b36638a0ace1a` | Welcome (Dark) | Dark | Onboarding | REF | existing SplashScreen | yes — "Learn More" destination does not exist |
| 33 | `2c6957c41e4043ffaeba0c945ca73c32` | Thraksha TR Monogram Logo | — | Branding | **REJECTED** | `BrandingComponents.kt` | generated artwork — superseded by existing in-repo brand |

**Approval basis.** All 33 screens live in the single owner-approved Thraksha project and
were treated as the approved set. Where two or more variants of the same surface exist,
the **latest refinement** was taken as the visual specification (rows 5/6 for Protect),
with earlier variants kept as reference for the evidence-progression block (rows 3/4).

## Reusable visual patterns extracted

1. **Hero status ring** — 192 dp circle, slow-rotating dashed outer ring + static inner
   arc, centred icon + mono uppercase state label, headline beneath, then one pill CTA.
2. **Status module card** — 24 dp radius, `#161719` fill, 4 px left severity ribbon,
   circular icon chip top-left, mono status chip top-right, title + one-line subtitle.
3. **Attention card** — error-tinted outline, circular warning chip, title + cause,
   trailing pill action.
4. **Evidence progression rail** — vertical connector with three nodes
   (DECLARED / GRANTED / OBSERVED), node icon chip, mono stage label, plain-language line.
5. **Timeline row** — left icon chip on a vertical rail, title + right-aligned time,
   description, mono meta chips, chevron affordance.
6. **Settings group** — mono uppercase group header, single rounded container, rows with
   icon chip + label + chevron, hairline dividers.
7. **Progressive-disclosure toggle** — mono uppercase "TECHNICAL DETAILS" row with a
   rotating chevron revealing a tonal sub-panel.
8. **Transactional shell** — bottom nav suppressed on focused/detail screens; back or
   close in a slim header; sticky bottom action bar.
9. **Bottom navigation** — 3 destinations, pill highlight behind the active icon in
   secondary-container, label below, tonal bar with hairline top border.
