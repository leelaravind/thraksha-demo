# THRAKSHA — PHASE 12.1 PROGRESS

Guide: `temp/THRAKSHA_DEMO_IMPLEMENTATION_PHASE_12_1_RC2.txt`
Started: 2026-08-16
Baseline: Phase 12 RC1 (`thraksha-guardian-1.0-privatealpha-rc1.apk`), treated as known-good.

## VERDICT: **PASS — RC2 REAL-WORLD PRIVATE ALPHA READY**

All three tasks complete. Every §31 criterion verified against the actual release APK on the
S20 FE. RC2 SHA-256 `93c15f227bc724ef02eb5cb79cabcac308f2dd53aa531a19d0665da8ed05c7de`,
signer `CN=Thraksha Guardian` (RSA 4096, v2+v3), `debuggable=false`, release scanner sees
**441/26/415** — exact ground-truth parity.
Final report: `phase12_1/PHASE12_1_FINAL_REPORT.md`.

## Status

| Task | Scope | Status |
| --- | --- | --- |
| A | Release package visibility | **COMPLETE** — proven on the RC2 release APK: 441/26/415, exact ground-truth parity |
| B | Private-alpha signing identity | **COMPLETE** — signed `CN=Thraksha Guardian`, RSA 4096, v2+v3, no debug fallback |
| C | Wording fix + RC2 build + verification | **COMPLETE** — RC2 built, audited, fresh-installed, and fully verified on device |

---

## TASK A — RELEASE PACKAGE VISIBILITY

Full audit: `phase12_1/PHASE12_1_PACKAGE_VISIBILITY.md`

### Cause — proven at four levels

`QUERY_ALL_PACKAGES` was declared only in `app/src/debug/AndroidManifest.xml`, a build-type
overlay that never merges into `release`.

1. Source layout — permission in the debug overlay; main manifest declared the opposite in
   a comment.
2. No other variant-dependent cause: no product flavors, no `src/release/`, no `sourceSets`
   overrides, no `manifestPlaceholders`, R8 off, and **no `BuildConfig.DEBUG` branch
   anywhere in the scanner** (`AppInventory.inventoryDevice()` is unconditional;
   `visibilityScope()` reads the permission live off the installed package).
3. The installed RC1 on the S20 FE: `dumpsys package` shows no `QUERY_ALL_PACKAGES` and
   `queriesPackages=[goodcaller, villaincaller]`.
4. Arithmetic matches exactly: RC1's 322 = 319 platform-force-visible system packages +
   Thraksha + the 2 declared decoys. No residue for a second cause.

### Impact quantified

S20 FE ground truth: **441** packages, **415** system, **26** third-party.
RC1 saw **322** — of which exactly **3** were third-party (itself + 2 decoys).
**All 23 genuine owner-installed apps** (Facebook, LinkedIn, Outlook, Office, Samsung Pay,
Samsung Internet, Samsung Health, Samsung Notes, Google Photos/Docs, …) were invisible.

### Fix applied

* `QUERY_ALL_PACKAGES` moved into `app/src/main/AndroidManifest.xml` with in-place
  justification (core product function, read-only use, Play policy is a separate future
  requirement, no Play claim made).
* `app/src/debug/AndroidManifest.xml` deleted — it held nothing else, and a redundant
  overlay is the exact shape of the original bug.
* Targeted `<queries>` retained deliberately (fallback if broad visibility is ever
  withdrawn).
* Two stale KDoc blocks in `AppInventory.kt` corrected. **No behavioural code changed.**

### Verified — including on the shipped release APK ✅

* Merged release manifest contains the permission. Archived:
  `phase12_1/evidence/merged_manifest_release_rc2.xml`.
* `aapt2 dump badging` on the RC2 APK: `uses-permission: name='android.permission.QUERY_ALL_PACKAGES'`.
* Installed RC2 on the S20 FE holds it at runtime (`dumpsys package`).
* **Release scan: 441 apps checked — User 26 / System 415 — exact ground-truth parity**
  (RC1: 322 / 3 / 319). 441 signing identities checked, 441 APK fingerprints computed.
* All 23 genuine owner-installed apps discoverable and scanned; Google TV, Samsung Notes
  and Tips appear with honest REVIEW status.
* `VisibilityScope.FULL` — reduced-visibility warning correctly absent, no code change
  needed.
* Non-destructive: 0 suspended, 441 packages intact. Known-threat matches: 1 (VillainCaller
  only); GoodCaller correctly not flagged.
* Identical counts after a full reboot.

---

## TASK B — PRIVATE-ALPHA SIGNING

Full detail: `phase12_1/PHASE12_1_SIGNING.md`

### Configuration complete

* Credentials resolved from untracked `keystore.properties` (git-ignored) with environment
  variable fallback (`THRAKSHA_KEYSTORE_FILE` / `_PASSWORD` / `THRAKSHA_KEY_ALIAS` /
  `THRAKSHA_KEY_PASSWORD`).
* Tracked, value-free `keystore.properties.template` documents the format, storage rules
  and key-continuity warning.
* Keystore-outside-repo **enforced**: build fails at configuration time if `storeFile`
  resolves inside `rootDir`.
* v1 off, **v2 + v3 on**.
* **No debug fallback.** `signingConfig` is `null` without credentials, and a hard gate on
  `packageRelease`/`assembleRelease`/`bundleRelease` fails the build rather than letting
  AGP silently emit `app-release-unsigned.apk`.

### Gate verified

`:app:assembleRelease` with no credentials → `BUILD FAILED` at `:app:packageRelease` with
the explicit missing-credential message. `app/build/outputs/apk/release/` confirmed absent
afterwards. Debug builds and unit tests unaffected.

### No secrets anywhere

No password invented, no keystore generated, nothing signing-related written to any tracked
file, source file, manifest, document or log.

### ✅ COMPLETE — keystore supplied by the owner, signer verified

Keystore created by the owner at `C:/Users/kplee/secure/thraksha/thraksha-private-alpha.jks`
(outside the repo), alias `thraksha-private-alpha`. `keystore.properties` present, confirmed
untracked and git-ignored, never staged.

Independently verified on the RC2 APK with `apksigner`:

| Field | Value |
| --- | --- |
| Signer | `CN=Thraksha Guardian, OU=Private Alpha, O=Thraksha, L=Tirumula, ST=Andhra Pradesh, C=IN` |
| Cert SHA-256 | `0d96977d15ce5b17c4167a03f70fc72401f580a8d4f2258e059fa113516ac928` |
| Cert SHA-1 | `c7bafcb654883e0b67758471d1ee129e6e53b2d7` |
| Key | RSA 4096 |
| Schemes | v1 false, **v2 true, v3 true** |
| debuggable | **false** (badging, xmltree and on-device flags all agree) |

**NOT `CN=Android Debug`.** Deep binary scan of the APK: 0 hits for private-key markers,
the key alias, `keystore.properties`, or password fields. No password was ever printed,
echoed or logged.

---

## TASK C — WORDING + RC2

**COMPLETE.** RC2 built from a clean tree, audited, fresh-installed and fully verified on
the S20 FE.

Full detail: `phase12_1/PHASE12_1_WORDING.md`, `PHASE12_1_RC2_ARTIFACT.md`,
`PHASE12_1_REAL_SCAN.md`

### §18 wording defect — fixed

`ui/components/WhitelistingDialog.kt` contained **two** overclaims, not one:

* `"To keep Thraksha's \"eyes and ears\" active 24/7 …"` — Thraksha monitors only while
  its services run; there is no continuous surveillance.
* `"This ensures the AI stays awake even when your phone is in your pocket."` — a
  battery-optimisation exemption makes Android *less likely* to pause background work. It
  guarantees nothing, and the local LLM is loaded on demand, not resident.

Both replaced with wording that states actual runtime behaviour, using the guide's
suggested phrasing plus an explicit non-guarantee. Dialog purpose, layout and control flow
unchanged.

### §19 absolute-claim sweep — one defect, no others

Swept `app/src/main` for 26 claim patterns (`always protected`, `100%`, `fully secure`,
`virus free`, `blocks all`, `all threats`, `guaranteed`, `device is safe`, `bank-grade`, …).
`WhitelistingDialog` was the **only** user-facing offender. Every other hit is a code
comment, factual state reporting (`"Blocked"` only for a genuinely dropped packet), or an
**existing anti-overclaim disclaimer**. The Protect surface's documented no-absolute-claims
invariant (`ProtectPresentation.kt:24–25`) was confirmed to hold in shipped code. Nothing
else was rewritten.

### §20 versioning — decided

| Field | RC1 | RC2 | Rationale |
| --- | --- | --- | --- |
| `versionCode` | 1 | **2** | OS update ordinal; must increase so RC2+ builds can update each other. |
| `versionName` | `1.0` | **`1.0-privatealpha-rc2`** | Human-facing only (Settings → About renders `Version <name> (<code>)`). Carries full candidate identity so a field phone is identifiable from its About screen, and matches the distributed filename exactly. |

### §23 pre-uninstall state — captured

`phase12_1/evidence/PRE_RC2_DEVICE_STATE.txt`. Key findings:

* **No Device Owner and no Profile Owner** (`dpm list-owners` → "no owners"), so guide §23
  step 3 is a no-op. Advice Mode already in force.
* No active automation routine. Baseline settings recorded for post-restore comparison.
* **The uninstall also deletes the 2.4 GiB model**, because it lives in Guardian's external
  files dir. Mitigated: the repo copy `model/gemma-4-E2B-it.litertlm` was hashed and
  **matches `LocalModelRepository.EXPECTED_SHA256` exactly**
  (`181938105e0eefd10596…`), so re-pushing it after install will pass on-device
  verification.
* Also destroyed: encrypted Room DB, the hash-chained audit log (a fresh install correctly
  starts a new chain from genesis — data loss, not tampering), and ConfigStore preferences.

### Verified

`:app:assembleDebug :app:testDebugUnitTest` → **BUILD SUCCESSFUL**, 213 unit tests,
0 failures, after the wording and versioning changes.

### Regressions run (guide §28) — full detail in `phase12_1/PHASE12_1_REGRESSION_RESULTS.md`

| Suite | Result |
| --- | --- |
| Unit (`testDebugUnitTest`) | **213 tests, 0 failures** |
| S20 FE instrumented (`connectedDebugAndroidTest`) | **90 tests, 0 failures, 0 errors, 3 assume-skips** |
| Device Owner AVD (`am instrument`, DO preserved) | **OK (36 tests), 0 failures** |

Phase 10B evaluation and the Phase 10A backend benchmark were excluded per §28.

All 3 S20 FE skips are `assumeTrue` guards, and **none is left unproven**: the two that
require a Device Owner were executed on the AVD and passed; the third asserts behaviour
when required access is missing, which does not hold on the phone.

Most important for Task A — broad visibility did **not** widen what Thraksha will act on.
On the DO AVD: `arbitraryPackage_isRefused`, `goodCaller_isRefusedAsTarget_regardlessOfAuthority`,
`guardian_cannotTargetItself` and `restore_refusesNonAllowlistedTargets` all pass, while
`deviceOwner_containsVillain_verifiesState_andRestores` and
`userActSuspend_isVerifiedActed_andReversible` still work end-to-end and reverse. AVD left
clean with Device Owner intact and VillainCaller `suspended=false`.

### ⚠ Side effect of the instrumented run — S20 FE app uninstalled early

`connectedAndroidTest` installs a debug build and uninstalls the app afterwards. RC1 shared
the Android debug signer, so the debug build updated over it and AGP's cleanup then removed
the package entirely. The phone now has **no** Guardian installed, and
`/sdcard/Android/data/com.thraksha.guardian/files/models/` was deleted with it.

Assessed impact: the §23 uninstall of RC1 has effectively already happened, earlier than
planned. The encrypted DB, audit chain and ConfigStore were scheduled for destruction by
§23 regardless, so nothing the plan intended to keep was lost. The 2.4 GiB model must be
re-pushed after RC2 is installed; its repo copy was hashed **before** the deletion and
matches `LocalModelRepository.EXPECTED_SHA256` exactly, so the restore is verifiable.
Recorded as an amendment (not a rewrite) in `evidence/PRE_RC2_DEVICE_STATE.txt` so the
sequence stays auditable. Decoys untouched; no system settings changed.

### RC2 verification on device — all green ✅

| Check | Result |
| --- | --- |
| RC2 SHA-256 | `93c15f227bc724ef02eb5cb79cabcac308f2dd53aa531a19d0665da8ed05c7de` (67,476,953 B, 955 entries) |
| APK content audit | **0 hits** — no keys, passwords, Stitch/MCP/Claude artefacts, temp docs, screenshots, test hooks or model weights; public keys only |
| Fresh install | clean — no prior Guardian on the phone |
| Model re-push | SHA `181938105e…` matches `EXPECTED_SHA256`; app reports "Integrity verified" |
| Real scan | 441 / 26 / 415 · 56 findings · 1 known-threat · 0 high-exposure · non-destructive (0 suspended) |
| Advice Mode | default; `dpm list-owners` → "no owners" |
| Automation | fresh install refused ("CANNOT RUN YET") → preview changed nothing → **explicit START** applied real changes (`zen 0→1`, `brightness 101→77`, `mode auto→manual`, calendar opened; 3 VERIFIED + 1 OPENED with the honest "foreground arrival is not claimed" caveat) → **STOP & RESTORE** verified back to 101/255 AUTO, DND off, ringer NORMAL |
| Offline AI | airplane mode, `ping` → "Network is unreachable" — correct 45-minute intent, plan preview only, **no auto-execution** |
| Reboot | no auto-start · model intact · app launches · **audit persists** (04:04/04:07 entries survive) · scan identical · settings unchanged |
| Crashes / ANRs | **0** |
| Tests | clean build ✅ · unit **213/0** · DO AVD **90/0** |
| Final device state | S20 FE and AVD both clean; adb-granted special accesses revoked; test APKs removed |

Deliberately **not** re-run on the S20 FE: `connectedAndroidTest`, because it uninstalls the
app and would have destroyed RC2, its audit chain and the 2.4 GiB model. Phone coverage came
from direct verification on the shipped release APK instead — stronger evidence than a debug
proxy.

---

## Git status

Nothing committed and nothing staged, per instruction. Working tree changes so far:

* `M  app/src/main/AndroidManifest.xml` (Task A — permission moved in)
* `D  app/src/debug/AndroidManifest.xml` (Task A — redundant overlay removed)
* `M  app/src/main/java/com/thraksha/guardian/security/inventory/AppInventory.kt` (KDoc only)
* `M  app/build.gradle.kts` (Task B signing config + Task C versioning)
* `M  app/src/main/java/com/thraksha/guardian/ui/components/WhitelistingDialog.kt` (Task C wording)
* `A  keystore.properties.template`
* `A  temp/THRAKSHA_DEMO_PHASE_12_1_PROGRESS.md`
* `A  temp/phase12_1/**`

No signing material exists anywhere in the tree. `keystore.properties` is absent and
git-ignored (`git check-ignore` confirms `.gitignore:30`).
