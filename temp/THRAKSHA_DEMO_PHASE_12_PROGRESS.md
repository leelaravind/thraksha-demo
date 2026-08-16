# THRAKSHA — PHASE 12 PROGRESS

Date: 2026-08-15 → 2026-08-16 · Baseline: frozen Phase 11B
Nothing committed or staged.

| Task | Verdict |
| --- | --- |
| **12A — Release & Safety Hardening** | **PASS** — 13/13 criteria |
| **12B — Clean-device / Real-world Soak** | **PASS** — 11/11 criteria |
| **12C — Private Alpha Readiness** | **PASS — PRIVATE ALPHA READY** |

Phase 12 ran sequentially with gate discipline: 12B was entered only after 12A passed, and
12C only after 12B passed. 12B was interrupted mid-way by a hardware-access blocker (the
handset stopped responding to ADB after the reboot test); work was **stopped and documented**
rather than assumed, and resumed only once the device was physically reconnected.

---

## 1. 12A result — PASS

All thirteen §21 gate criteria met with evidence. Full assessment in
`phase12/12a_release_hardening/PHASE12_12A_GATE.md`.

### Release artifact

| Field | Value |
| --- | --- |
| Filename | `thraksha-guardian-1.0-privatealpha-rc1.apk` |
| **SHA-256** | `80e7aaea2619257159b05d2f13c57f81dad10e44b502b748bfb1788e9bb337c6` |
| Size | 67,476,901 bytes (64 MiB), 955 entries |
| Version | 1.0 (code 1) · minSdk 26 · targetSdk 36 |
| ABIs | arm64-v8a, x86_64 |
| **debuggable** | **false** (confirmed in the installed package flags) |
| R8 | off — deliberate, four reflective/JNI surfaces would need re-verification |
| **Signer** | `CN=Android Debug` — **TEMPORARY**, SHA-256 `2a7c7cf3fa6214329168e2037d8a6ecdd92f5e7ab981f439d291ebccac3a19d4` |
| Model bundled | **no** — developer-provisioned |

**No release signing identity exists and none was invented.** Consequences documented:
the debug key is public, the APK must not be distributed, and a future properly-signed
build **cannot update** an install made from this one.

### Audits completed

| Area | Outcome |
| --- | --- |
| Manifest / permissions | 12 permissions, each classified. `VIBRATE` found genuinely unused → REMOVE-deferred. |
| Exported components | 4, all justified; only ContentProvider is AndroidX Startup, not exported. **No unexpected export.** |
| Secrets (source **and** APK) | **None.** Only RSA *public* keys ship; pack private keys git-ignored and absent; single `sk-` hit resolved to `…risk-…` in a rule ID. |
| Dev/test tooling | No Stitch/MCP/Claude artefacts, no proxy, no temp docs, no eval datasets in the APK. |
| Network egress | **One** network-capable path in the entire production source (Network Guard's `protect()`ed UDP forwarder). **No HTTP stack exists in the APK at all** — the "no cloud fallback" claim is structural. |
| Data / privacy | 3 storage surfaces; SQLCipher + Keystore-wrapped passphrase; `allowBackup=false`; **raw prompts never persisted** (length only), test-enforced. |
| Model hardening | Pinned identity + size + container magic + SHA-256 gate; every failure yields a distinct honest state; model cannot name an executor. |
| Dependencies / SBOM | All versions pinned, no dynamic versions; Apache-2.0 throughout except SQLCipher (BSD-style); **no analytics/crash/ads SDK**. |
| Licence / provenance | Gemma redistribution clearance **NOT claimed**; mitigated by not bundling the model. |

### Defect found and fixed in 12A

**The on-device model was never released.** `unload()` existed and was test-verified but no
production code called it, and there was no `onTrimMemory`/`onLowMemory` handler. After one
Ask Thraksha request the process held **~2.9 GB — 37 % of the phone's 7.6 GB RAM** — at
foreground-service priority (`oom cur=200`), indefinitely, including while backgrounded.
Android would evict the user's *other* apps rather than reclaim from Thraksha.

Minimal fix in `ThrakshaApplication`: release the model on genuine memory pressure, reusing
the existing tested `unload()`. Verified on the release build:

| Point | TOTAL PSS |
| --- | --- |
| Model loaded, inference complete | 2,043,618 KB |
| After `am send-trim-memory RUNNING_CRITICAL` | 973,594 KB |
| Settled idle | **138,487 KB** |
| Next request (transparent reload) | 2,043,618 KB |

Log line confirms the release; process survived; the pending plan preview correctly
survived the unload. **This is the only production-code change in Phase 12.**

### 12A regressions — all green, re-run after the fix

| Suite | Discovered | Passed | Skipped | Failures | Errors |
| --- | --- | --- | --- | --- | --- |
| Unit | 213 | **213** | 0 | **0** | **0** |
| S20 FE instrumented | 92 | **89** | 3 | **0** | **0** |
| Device Owner AVD | 92 | **69** | 23 | **0** | **0** |

Union: **91 of 92 executed on hardware.** Device Owner enforcement and Full-Power User ACT
suspension both ran and passed on the AVD. All skips `Assume`-gated and individually
explained.

---

## 2. 12B result — PASS

Full detail: `phase12/12b_soak/PHASE12_REAL_DEVICE_SOAK.md`.

Tested with the **exact** 12A artifact, unmodified, on the S20 FE.

| § | Item | Result |
| --- | --- | --- |
| 23 | Clean uninstall → fresh install | **PASS** (uninstall required deactivating Device Admin first — see below) |
| 23.8–9 | Model provisioned (2.41 GiB) and **SHA verified through the app** | **PASS** — Settings shows On-device AI AVAILABLE |
| 24 | Fresh-install verification: launch, branding, themes, permissions, scanner, findings, plan preview, Meeting, Driving, restoration, audit, Settings, empty/error states | **PASS** |
| 25 | Reboot: no auto-start, no settings changed, **audit chain intact**, model re-verified, scanner works, Network Guard honest | **PASS** |
| 26 | Upgrade in place: `firstInstallTime` preserved, DB + audit chain + model + grants survived | **PASS** |
| 27 | Uninstall: package, sandbox, encrypted DB and 2.41 GiB model all removed; device settings untouched | **PASS** |
| 28 | Real scan: 322 packages, REDUCED visibility stated, 1 known-threat match, 0 crashes | **PASS** |
| 29 | Soak: 5 background/foreground cycles, repeated scans/inference, model unload/reload, permission revoke/regrant | **PASS** — 0 crashes, 0 ANRs, +5 MB drift (no leak) |
| 30 | Final device state | **clean** |

### Headline demonstrations

* **Offline AI proven in airplane mode** — "Meeting Mode — 45 minutes" plus full plan with
  no network at all.
* **Exact restoration** — Meeting: `zen 0→1→0`, `brightness 13→77→13`. Driving: 3 actions
  VERIFIED then "Your previous settings are back". Driving correctly leaves DND alone.
* **Least privilege on a clean install** — all three special accesses default to NOT GRANTED.
* **Memory reclaim on the soak install** — 2.90 GB → 0.97 GB on a real pressure signal.

### Blocker encountered and resolved

After `adb reboot`, the handset never re-enumerated. ~20 minutes of recovery across four
strategies failed; the emulator stayed connected throughout, isolating the fault to the
phone's USB/ADB session. Work was **stopped and documented at that point** — no PASS was
assumed and 12C was not entered. Once the device was physically reconnected, §25–§30 were
executed in full.

### Second defect found in 12B (not fixed in this build, deliberately)

The Samsung battery-optimisation dialog overclaims: *"eyes and ears active 24/7"*, *"the AI
stays awake even when your phone is in your pocket"*. Both false — the AI runs on demand and
is now released under memory pressure. Not fixed because §22 requires testing the **exact**
12A artifact; changing the string would have invalidated every 12B result. Queued for the
next build and disclosed in the release notes.

---

## 3. 12C result — PASS — PRIVATE ALPHA READY

Deliverables in `phase12/12c_readiness/`:

| Document | Content |
| --- | --- |
| `PHASE12_PRIVATE_ALPHA_RISK_REGISTER.md` | **0 BLOCKER · 2 HIGH · 9 MEDIUM · 10 LOW**, each with mitigation |
| `PHASE12_PRIVATE_ALPHA_INSTALL_CHECKLIST.md` | Pre-install verification, install, first run, operating rules, escalating recovery table |
| `PHASE12_PRIVATE_ALPHA_RELEASE_NOTES.md` | What works, what is experimental, known limitations, unsupported scenarios |
| `PHASE12_PRIVATE_ALPHA_READINESS.md` | Go/no-go decision |

### Verdict

> **PASS — PRIVATE ALPHA READY**
>
> Thraksha is reasonable for controlled installation on the owner's everyday Android device
> as a private experimental alpha in Advice Mode, subject to the documented limitations.

Not production-ready; no commercial or redistribution clearance claimed.

**Conditions:** owner-only installation · Advice Mode, no Device Owner and no Device Admin
activation · follow the install/recovery checklist · read and accept H1 (the scanner cannot
see your third-party apps) and H2 (temporary debug signing — no in-place upgrade later).

### Two HIGH risks

* **H1 — reduced package visibility.** 322 packages vs 441, and **no third-party user
  apps**. The app states this unprompted, so nothing is misrepresented, but Protect will
  look quiet on a personal phone because it is mostly inspecting system packages. **Owner's
  decision** whether to add `QUERY_ALL_PACKAGES` and re-cut.
* **H2 — temporary debug signing.** Not distributable; a properly-signed build cannot update
  this install.

---

## 4. Risk items carried into the register

| # | Item | Severity |
| --- | --- | --- |
| 1 | **Package visibility:** release build sees 322 packages and **no third-party user apps** (`QUERY_ALL_PACKAGES` is debug-only). Honestly reported, but the Protect surface delivers far less on a personal phone than the demo. **Owner decision.** | HIGH (capability) |
| 2 | **Debug signing identity** — blocks distribution and any in-place upgrade to a real key | HIGH |
| 3 | **Device Admin blocks uninstall** until manually deactivated | MEDIUM |
| 4 | `device_admin.xml` declares 10 policies (incl. wipe-data, disable-cameras) the app never uses — consent dialog looks alarming | MEDIUM |
| 5 | Battery-optimisation dialog copy overclaims; also un-restyled | MEDIUM |
| 6 | `ThrakshaAccessibilityService` ships but is unused by any feature | MEDIUM |
| 7 | Gemma licence redistribution unresolved (mitigated: not bundled) | MEDIUM |
| 8 | AI interpretation accuracy — "focus for 30 minutes" understood as 60; preview is the mitigation | MEDIUM |
| 9 | Model resident set ~2.0–2.9 GB while loaded, even after the trim fix | MEDIUM |
| 10 | `VIBRATE` declared but unused | LOW |
| 11 | Developer-diagnostics screen ships in the release build | LOW |
| 12 | Attribution/licence bundle not shipped | LOW |
| 13 | 32-bit-only devices cannot install (arm64-v8a / x86_64 only) | LOW |

---

## 5. Git status

Nothing staged, nothing committed. HEAD unchanged at `bad1633`.

Phase 12 changed exactly **two** source files:

| File | Change |
| --- | --- |
| `app/build.gradle.kts` | Release build type: temporary debug `signingConfig` + documented R8 decision |
| `app/src/main/java/com/thraksha/guardian/ThrakshaApplication.kt` | `onTrimMemory`/`onLowMemory` model release (the 12A defect fix) |

Everything else added is evidence under `temp/phase12/`.

---

## 6. To resume

1. Unlock the S20 FE, accept the USB-debugging prompt, reseat the cable if needed.
2. Confirm `adb devices` shows `RZCW40LVBJD` as `device`.
3. Resume 12B at §25 (reboot verification); §23–24 and §28–29 partial results above stand.
4. On 12B PASS, proceed to 12C.
