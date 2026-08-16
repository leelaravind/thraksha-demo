# THRAKSHA DEMO — PHASES 1–3 IMPLEMENTATION PROGRESS

Implementation run against `temp/THRAKSHA_DEMO_IMPLEMENTATION_PHASES_1_3.md`,
starting from commit `bad1633` as described by `THRAKSHA_CURRENT_PROJECT_AUDIT.md`.

**Demo device (confirmed this run):** Samsung `SM-G781B` (S20 FE), Android 13 / API 33,
serial `RZCW40LVBJD`. This resolves audit question Q3 and audit item §13.4.2.

---

## PHASE 0 — BASELINE (before any change)

Recorded per guide §1.1, before modifying anything.

| Check | Command | Result |
|---|---|---|
| Unit tests | `./gradlew testDebugUnitTest --rerun-tasks` | **BUILD SUCCESSFUL** — 7 tests, 0 failures |
| Debug APK | `./gradlew assembleDebug` | **BUILD SUCCESSFUL** |
| Instrumented tests | — | Not run at baseline; device was `unauthorized` at that moment |

**Pre-existing failures: none.** The audit's "UNCLEAR / NEEDS RUNTIME TESTING" on whether
HEAD builds and whether the suite passes is now resolved: **both pass.**

Pre-existing warnings left untouched (out of scope, per "do not perform unrelated cleanup"):

* AGP 8.7.0 tested only to `compileSdk 35`, project uses 36.
* `package="com.thraksha.guardian"` in `AndroidManifest.xml` is ignored by AGP 8.7 (deprecation warning only).

---

## PHASE 1 — FOUNDATION STABILIZATION ✅ COMPLETE

### Files created

| File | Purpose |
|---|---|
| `app/src/main/java/com/thraksha/guardian/security/FoundationStatus.kt` | Process-wide `StateFlow` of secure-foundation readiness: `Initialising` / `Ready` / `Failed(message, cause)`. |
| `app/src/main/java/com/thraksha/guardian/security/DemoMode.kt` | `FULL_POWER` / `ADVICE_MODE`, derived **only** from `PrivilegeLevel`. |
| `app/src/main/java/com/thraksha/guardian/ui/state/PrivilegeState.kt` | `rememberPrivilegeLevel()` — Compose state re-read on every `ON_RESUME`. |
| `app/src/test/java/com/thraksha/guardian/AuditLogConcurrencyTest.kt` | 50 concurrent appends must yield unique ids and a verifying chain. |

### Files modified

| File | Change |
|---|---|
| `app/src/main/AndroidManifest.xml` | Added `<queries>` naming exactly the two decoy packages (§1.2). |
| `data/audit/AuditLog.kt` | `append()` now runs under a companion-scoped `Mutex` (§1.3). |
| `ThrakshaApplication.kt` | Every init step is `runCatching`-guarded and reports through `FoundationStatus` (§1.4). |
| `security/events/SecurityEventBus.kt` | Exposed `subscriptionCount` so startup producers can wait out the `replay = 0` window. |
| `services/ThrakshaVpnService.kt` | `ENABLED = false` gate; `start()` and `onStartCommand` refuse to establish a TUN (§1.6). |
| `ui/screens/DashboardScreen.kt` | Reactive privilege, FULL POWER / ADVICE badge, foundation-failure banner, VPN control removed, fake threat-emit button removed. |

### Architecture decisions

1. **`<queries>` over `QUERY_ALL_PACKAGES`** (§1.2). Thraksha declares visibility into exactly
   `com.thraksha.demo.goodcaller` and `com.thraksha.demo.villaincaller`. Adding a controlled
   sample later means adding one `<package>` line. Broad visibility is never requested — which
   also means Thraksha's *own* profile stays clean while it flags VillainCaller for the
   opposite.

2. **`Mutex` on the `AuditLog` companion, not the instance.** More than one `AuditLog` can wrap
   the same singleton DAO (the bus collector builds one; tests build others). An instance lock
   would not serialise them. Hash format, genesis behaviour, append-only API and
   `verify()` semantics are byte-for-byte unchanged — the existing audit tests were not touched
   and still pass.

3. **Startup failure is surfaced, never papered over** (§1.4). If `System.loadLibrary("sqlcipher")`
   or the encrypted-store open fails, the process does **not** crash and does **not** fall back
   to an unencrypted or freshly-created database. `FoundationStatus` goes to `Failed` and the
   dashboard renders **"FOUNDATION UNAVAILABLE"** with the reason instead of "FOUNDATION ACTIVE".
   Cryptographic fail-closed behaviour is unchanged.

4. **`FULL POWER` is bound to `PrivilegeLevel` only** (§0.1). `DEVICE_OWNER → FULL_POWER`;
   `DEVICE_ADMIN` and `NORMAL → ADVICE_MODE`. `ExecutionMode` remains a separate policy dial and
   is now labelled *"Execution mode (policy)"* on the dashboard so the two axes cannot be
   confused. This implements audit question Q1 option (a).

5. **`markReady()` is deferred until the collector is actually subscribed.** The bus has
   `replay = 0`, so `FoundationStatus.Ready` is only published after
   `SecurityEventBus.subscriptionCount` reaches 1. The Phase 3 launch audit gates on this, which
   closes audit risk R9 rather than hoping the race does not happen.

6. **VPN disabled in code, not just hidden.** Removing the button alone would still leave
   `ThrakshaVpnService.start()` callable. `ENABLED = false` makes both the public `start()` and
   `onStartCommand` refuse, so no code path can black-hole device traffic this phase (audit R1).

7. **The fake "Emit Test Threat Event" button was removed now rather than in Phase 3.** Its
   output was indistinguishable from a real finding in the audit feed. Removing it during
   Phase 1 guarantees that every `THREAT` row observed from here on came from real detection.
   The remaining diagnostics are relabelled *"DEVELOPER DIAGNOSTICS"* and only check service
   permission state — they produce no findings.

### Build & test results

| Check | Result |
|---|---|
| `./gradlew :app:assembleDebug` | **BUILD SUCCESSFUL** |
| `./gradlew :app:testDebugUnitTest` | **BUILD SUCCESSFUL** — 8 tests, 0 failures, 0 errors |

Per-class: `AuditLogConcurrencyTest` 1/1, `AuditLogTest` 2/2, `RulepackVerifierTest` 2/2,
`SecurityEventBusTest` 2/2, `ExampleUnitTest` 1/1. **All pre-existing foundation tests still pass
unmodified.**

**Negative control on the new concurrency test.** To prove the test is not vacuous, the `Mutex`
was temporarily removed and the suite re-run: `AuditLogConcurrencyTest > concurrentAppends…`
**FAILED**. The `Mutex` was then restored and it passes. The test genuinely covers the race the
audit identified (R8).

### Runtime checks performed (on `SM-G781B`, Android 13)

| Check | Result |
|---|---|
| Install `app-debug.apk` | Success |
| Cold launch | No crash; process alive |
| `AndroidRuntime:E` during startup | No entries |
| `FoundationStatus` failure logs | None — foundation initialised cleanly |
| Dashboard banner | **"FOUNDATION ACTIVE"** — i.e. `Ready` was published, so the audit collector is confirmed subscribed |
| Mode badge | **"ADVICE MODE"** with the honest explanation line |
| Privilege row | `DEVICE_ADMIN` (device admin was already enrolled) — correctly *not* FULL POWER |
| VPN control | Absent from the dashboard; replaced by an explicit unavailability note |
| Fake threat-emit button | Absent |

Screenshot: `phase1_dashboard.png`.

### Known limitations / remaining uncertainties

* **Package visibility is not yet proven at runtime.** The `<queries>` entries are declared, but
  nothing is installed at those package names yet, so discovery can only be verified in Phase 2.
* **The privilege *transition* was not exercised on device.** The `ON_RESUME` re-read is
  implemented and the initial read is correct, but flipping Device Admin off and on again would
  have modified the user's device state, so the refresh path is verified by construction rather
  than observation. It is directly observable during a demo by toggling Device Admin in Settings
  and returning to the app.
* **Instrumented tests were not run this phase.** They are run in Phase 3 once the detection path
  they would exercise exists. Not a Phase 1 acceptance item.
* **`FoundationStatus.Failed` rendering is untested at runtime** — provoking it would require
  invalidating the Keystore key. The code path is straightforward and compiles; the failure
  branch is unproven on device.
* The `AuditEntity` schema is still v1 and still a flat `details` string (audit R10). Deliberate:
  changing it would require a Room migration, and the guide's stop conditions warn against
  disturbing the audit-chain format. Revisited in Phase 3.

### Phase 1 acceptance criteria

| Criterion | Status |
|---|---|
| Project builds successfully | ✅ |
| Unit tests pass or failures documented | ✅ 8/8 pass |
| Existing crypto/audit/rulepack tests intact | ✅ untouched, passing |
| Targeted package visibility for both decoys | ✅ declared (runtime proof in Phase 2) |
| Privilege state refreshes reliably | ✅ implemented; transition not exercised on device |
| Audit append concurrency-safe | ✅ `Mutex` + negative-control-verified test |
| Initialization errors handled honestly | ✅ `FoundationStatus` + dashboard banner |
| VPN cannot be activated from the demo path | ✅ control removed **and** `ENABLED = false` |

**Next step:** Phase 2 — create `:goodcaller` and `:villaincaller` modules and prove Thraksha can
discover both through the declared `<queries>`.

---

## PHASE 2 — CONTROLLED DECOY APPLICATIONS ✅ COMPLETE

### Files created

| File | Purpose |
|---|---|
| `goodcaller/build.gradle.kts` | `:goodcaller` module, `applicationId com.thraksha.demo.goodcaller`. |
| `goodcaller/src/main/AndroidManifest.xml` | Caller-ID baseline permissions only. |
| `goodcaller/src/main/java/.../MainActivity.kt` | Single programmatic screen. |
| `goodcaller/src/main/res/values/strings.xml` | App name `GoodCaller`. |
| `villaincaller/build.gradle.kts` | `:villaincaller` module, `applicationId com.thraksha.demo.villaincaller`. |
| `villaincaller/src/main/AndroidManifest.xml` | Baseline **plus** the deliberate mismatch set. |
| `villaincaller/src/main/java/.../MainActivity.kt` | Single programmatic screen + inert `Run Demo Behaviour`. |
| `villaincaller/src/main/res/values/strings.xml` | App name `VillainCaller`. |
| `app/src/main/java/.../security/inventory/AppType.kt` | `AppType` enum + `DemoAppRegistry` (`packageName → AppType`). |
| `app/src/androidTest/java/.../PackageVisibilityInstrumentedTest.kt` | Proves the `<queries>` declaration actually works on device. |

### Files modified

| File | Change |
|---|---|
| `settings.gradle.kts` | `include(":goodcaller")`, `include(":villaincaller")`. |

### Architecture decisions

1. **Same-repo Gradle modules** (audit Q7, guide §2). Keeps package IDs, permission profiles and
   the Guardian's `<queries>` in lockstep — a decoy whose manifest drifts from the registry would
   silently break the demo.

2. **No Compose, no Room, no network stack in either decoy.** They depend only on `core-ktx` and
   `appcompat`, and their UI is built in code with no layout XML. A sample that does almost
   nothing is much easier to defend as harmless, and both APKs come in around 3.1 MB.

3. **Both decoys share an identical caller-ID baseline.** `READ_PHONE_STATE`, `READ_CONTACTS`,
   `READ_CALL_LOG`, `INTERNET`, `POST_NOTIFICATIONS`. VillainCaller adds capabilities on top
   rather than differing across the board — so the only variable between "clean" and "flagged" is
   the mismatch set itself. Without this, a demo verdict could be explained by any incidental
   difference.

4. **The mismatch is 100 % static and 100 % declared.** VillainCaller adds `READ_MEDIA_IMAGES`
   (+ `READ_EXTERNAL_STORAGE` capped at `maxSdkVersion 32`), `QUERY_ALL_PACKAGES` and
   `ACCESS_FINE_LOCATION`. It **never exercises any of them.** This is the guide's §0.2 honesty
   requirement made concrete: Thraksha detects a *declared capability* that another app can
   genuinely read via `PackageManager`, not an *action*, which no Android API would let it observe.

5. **`Run Demo Behaviour` is deliberately inert** and says so in its own Toast. A button that
   appeared to trigger the detection would imply Thraksha was watching runtime behaviour. The
   findings are identical whether or not it is ever pressed.

6. **`DemoAppRegistry` is a hard-coded exhaustive map, and unlisted packages are `UNKNOWN`.**
   No inference, no heuristics, no ML (guide §2.4). Unknown-type apps are skipped by the audit
   rather than guessed at, so the audit cannot produce a verdict it cannot justify.

### Build results

| Target | Result |
|---|---|
| `:goodcaller:assembleDebug` | **BUILD SUCCESSFUL** — `goodcaller-debug.apk`, 3,169,594 B |
| `:villaincaller:assembleDebug` | **BUILD SUCCESSFUL** — `villaincaller-debug.apk`, 3,170,486 B |
| `:app:assembleDebug` | **BUILD SUCCESSFUL** |

### Runtime checks performed (on `SM-G781B`, Android 13)

**All three APKs installed and coexisting** (`adb shell pm list packages`):

```
package:com.thraksha.guardian
package:com.thraksha.demo.goodcaller
package:com.thraksha.demo.villaincaller
```

**Observable permission profiles** (`adb shell dumpsys package …`, ground truth from the OS):

| | GoodCaller | VillainCaller |
|---|---|---|
| `READ_PHONE_STATE` / `READ_CONTACTS` / `READ_CALL_LOG` / `INTERNET` / `POST_NOTIFICATIONS` | ✅ | ✅ |
| `READ_MEDIA_IMAGES` | ❌ absent | ✅ present |
| `QUERY_ALL_PACKAGES` | ❌ absent | ✅ present |
| `ACCESS_FINE_LOCATION` | ❌ absent | ✅ present |

Two incidental entries appear on the device and are **not** part of the demo mismatch:
`DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` is auto-added by `androidx.core` to *both* apps, and
`ACCESS_COARSE_LOCATION` is implied by the platform from `ACCESS_FINE_LOCATION` on VillainCaller.
`READ_EXTERNAL_STORAGE` correctly does not appear — the device is API 33 and it is capped at 32.

**Instrumented tests:** `./gradlew :app:connectedDebugAndroidTest` → **11 tests, 0 failures,
0 errors, 0 skipped.**

The 4 new `PackageVisibilityInstrumentedTest` cases **ran rather than being skipped**, which is
the actual proof of discovery: they call `getPackageInfo(pkg, GET_PERMISSIONS)` from Thraksha's
own process, and under Android 11+ visibility filtering an undeclared package throws
`NameNotFoundException` as though it were not installed. Both decoys resolved, so the targeted
`<queries>` element genuinely works — audit blocker **B1 is closed without `QUERY_ALL_PACKAGES`.**

All 7 pre-existing instrumented tests (`EncryptedStoreTest`, `AuditLogInstrumentedTest`,
`FoundationInstrumentedTest`, `ExampleInstrumentedTest`) still pass unmodified.

Screenshots: `phase2_goodcaller.png`, `phase2_villaincaller.png`.

### Known limitations

* **`QUERY_ALL_PACKAGES` on VillainCaller is a declared capability, not an observed query.**
  Thraksha can prove VillainCaller *is able to* enumerate installed apps; it cannot observe it
  doing so. No Android API exposes that, as recorded in audit §13.3.
* **Runtime grant state is mostly meaningless here.** Neither decoy ever requests its dangerous
  permissions at runtime, so they sit ungranted. The Phase 3 detection is built on *requested*
  permissions for exactly this reason — a not-yet-granted dangerous capability is still a real
  profile mismatch.
* **Neither decoy is signed with a release key.** Both are debug-signed, so signing-certificate
  metadata is not a meaningful demo signal this run.

### Phase 2 acceptance criteria

| Criterion | Status |
|---|---|
| Both modules build | ✅ |
| Both produce separate APKs | ✅ |
| Both coexist on one device | ✅ verified alongside Guardian |
| Package IDs are stable | ✅ fixed in `build.gradle.kts` + registry + `<queries>` |
| Thraksha can discover both packages | ✅ proven by non-skipped instrumented tests |
| GoodCaller exposes only the intended caller-ID profile | ✅ verified via `dumpsys` |
| VillainCaller exposes the intentionally suspicious profile | ✅ verified via `dumpsys` |
| No actual malicious or privacy-invasive behaviour | ✅ neither app reads user data, stores anything, or makes network requests |

**Next step:** Phase 3 — `AppInventory`, caller-ID baseline, re-signed demo rulepack on the
production path, `RuleEngine`, `SecurityAuditEngine`, launch audit and `Run Security Check`.

---

## PHASE 3 — DETECTION BRAIN + LAUNCH SECURITY AUDIT ✅ COMPLETE

### Files created

| File | Purpose |
|---|---|
| `security/inventory/ObservedApp.kt` | `ObservedApp` + `Capability` — the Android↔engine boundary type. |
| `security/inventory/AppInventory.kt` | The only class in the security path that touches `PackageManager`. |
| `security/baseline/AppBaseline.kt` | `AppBaseline` + `AppBaselines` — the deterministic CALLER_ID baseline. |
| `security/engine/Finding.kt` | Structured finding: rule, package, app, type, severity, confidence, reason, evidence. |
| `security/engine/RuleEngine.kt` | Pure evaluator over a verified rulepack. |
| `security/engine/SecurityAuditEngine.kt` | The single `runAudit()` entry point + `AuditResult` model. |
| `tools/sign_rulepack.sh` | Repeatable developer-side rulepack signing (closes audit R11). |
| `app/src/test/.../RuleEngineTest.kt` | 9 off-device tests against the **real signed asset**. |
| `app/src/androidTest/.../SecurityAuditEngineInstrumentedTest.kt` | 8 on-device end-to-end pipeline tests. |
| `app/src/androidTest/.../AuditTrailPersistenceInstrumentedTest.kt` | Proves findings reach the encrypted hash-chained trail. |

### Files modified

| File | Change |
|---|---|
| `app/src/main/assets/rulepack.json` | **v2, demo-aligned, re-signed.** 5 enabled rules with real `params` + the original 12 retained as `enabled: false`. |
| `app/src/main/assets/rulepack.sig` | Regenerated by `tools/sign_rulepack.sh`. |
| `security/events/SecurityEvent.kt` | `ThreatDetected` gained `packageName` (defaulted null); added `ScanCompleted`. |
| `ThrakshaApplication.kt` | Collector persists `SCAN` rows; launch audit wired after the collector subscribes. |
| `ui/screens/DashboardScreen.kt` | `SECURITY AUDIT` card, `Run Security Check`, per-app result cards, honesty footnote, SCAN feed styling. |
| `app/src/test/.../RulepackVerifierTest.kt` | Updated to v2 pack; now also asserts every enabled rule declares a `check`. |
| `app/src/androidTest/.../FoundationInstrumentedTest.kt` | Updated to v2 pack expectations. |

### Architecture decisions

1. **`ObservedApp` is the hard boundary.** `RuleEngine` never sees a `PackageInfo`, `Context` or
   `PackageManager`, which is why all 9 rule tests run off-device with no Robolectric. It also
   means detection has **no** field for "apps this app read" or "hosts it contacted" — those
   cannot be observed, so no such field was invented to be filled with a guess later.

2. **Detection is driven by signed `params`, not Kotlin.** `RuleEngine` implements two generic
   checks — `PERMISSION_NOT_IN_BASELINE` and `PROFILE_MISMATCH_COUNT` — and every rule's target
   permissions, app-type scope, confidence and human-readable reason come from the **signed**
   rulepack. Changing what Thraksha detects therefore means editing and re-signing a
   cryptographically verified artefact. That is what makes the signature load-bearing instead of
   decorative.

3. **A rule naming an unimplemented `check` is skipped, never guessed.** The 12 original generic
   rules are retained in the signed pack as `enabled: false` with empty `params` — they make the
   pack look like a real catalogue (audit §18) while guaranteeing nothing appears to have been
   evaluated when no detector exists. `RulepackVerifierTest` enforces both halves of this.

4. **The baseline is what makes the verdict role-aware.** A rule only fires when the app requests
   a targeted permission **and** that permission is absent from its type's baseline. This is
   `APP TYPE + CAPABILITY + CONTEXT → VERDICT` in code, and it is directly tested: the identical
   observation of `READ_MEDIA_IMAGES` is flagged under the caller-ID baseline and **not** flagged
   under a baseline that expects it.

5. **Detection uses the *requested* set; grant state is evidence.** Neither decoy ever requests
   its dangerous permissions at runtime, so most sit ungranted. A requested-but-ungranted
   dangerous capability is still a genuine profile mismatch, and the evidence line records which
   it is (`requested, not granted` vs `granted`).

6. **The launch audit is sequenced on `subscriptionCount > 0`, not on a timer.** That is the exact
   moment every precondition holds: encrypted store open, collector subscribed (so no finding is
   lost to `replay = 0`), inventory safe to run. It runs once per process from
   `ThrakshaApplication` — never from a Compose effect, so recomposition cannot re-trigger it.

7. **One engine, two triggers.** `Run Security Check` calls `SecurityAuditEngine.runAudit()` — the
   same suspend function the launch audit calls. There is no second code path. Both are
   serialised by a `Mutex` so an impatient tap during the launch audit cannot interleave and
   double-write the chain.

8. **A `SCAN` row is written even when nothing is found.** An audit that finds nothing is itself
   evidence; the absence of a `THREAT` row must not be the only proof that a scan ran.

9. **No DB schema change.** The audit stays at v1 with its flat `details` string. The guide's stop
   conditions warn against disturbing the audit-chain format, and structured columns are not
   needed to satisfy any Phase 3 criterion. `ThreatDetected.packageName` carries attribution on
   the event, so the schema change can be made later against a real UI requirement rather than
   speculatively. **This is a deliberate divergence from audit recommendation §16/Q8.**

10. **Detection and enforcement stay separate.** `RuleEngine` and `SecurityAuditEngine` hold no
    `DevicePolicyManager`. No `ACTED` state exists in the UI, and the audit card carries an
    explicit note that no enforcement action has been taken.

### Rulepack v2

5 enabled rules, all with populated `params`:

| Rule | Severity | Confidence | Fires on |
|---|---|---|---|
| `caller-media-permission-mismatch` | HIGH | 92 | `READ_MEDIA_IMAGES` / `READ_MEDIA_VIDEO` / `READ_EXTERNAL_STORAGE` / `MANAGE_EXTERNAL_STORAGE` |
| `caller-package-visibility-mismatch` | HIGH | 88 | `QUERY_ALL_PACKAGES` |
| `caller-location-permission-mismatch` | MEDIUM | 74 | `ACCESS_FINE_LOCATION` / `ACCESS_BACKGROUND_LOCATION` |
| `caller-surveillance-capability-mismatch` | HIGH | 90 | `RECORD_AUDIO` / `CAMERA` |
| `caller-high-risk-profile` | CRITICAL | 95 | ≥2 of the above already fired |

Plus the 12 original catalogue rules, retained `enabled: false` with no detector.

**Signing.** `tools/sign_rulepack.sh` regenerates `rulepack.sig` with RSA-2048/SHA-256 over the
exact asset bytes and re-derives `rulepack_public.key` from the private key so the two cannot
drift. It verifies immediately after signing, so a bad signing run fails at the terminal rather
than on the device. Verified: the regenerated public key is **byte-identical** to the previously
committed one (392 B), so the existing key pair is unchanged.

### Build & test results

| Check | Result |
|---|---|
| `./gradlew clean :app:assembleDebug :goodcaller:assembleDebug :villaincaller:assembleDebug` | **BUILD SUCCESSFUL** |
| `./gradlew :app:testDebugUnitTest` | **17 tests, 0 failures, 0 errors** |
| `./gradlew :app:connectedDebugAndroidTest` | **20 tests, 0 failures, 0 errors, 0 skipped** |

Unit: `RuleEngineTest` 9, `RulepackVerifierTest` 2, `AuditLogTest` 2, `AuditLogConcurrencyTest` 1,
`SecurityEventBusTest` 2, `ExampleUnitTest` 1.

Instrumented: `SecurityAuditEngineInstrumentedTest` 8, `PackageVisibilityInstrumentedTest` 4,
`FoundationInstrumentedTest` 3, `EncryptedStoreTest` 2, `AuditLogInstrumentedTest` 1,
`AuditTrailPersistenceInstrumentedTest` 1, `ExampleInstrumentedTest` 1.

**Every pre-existing foundation test still passes.** No crypto, audit-chain or event-bus test was
weakened; the only test edits were the rule-count assertions, which now assert *more* than before.

### Runtime checks performed (on `SM-G781B`, Android 13)

**Launch audit, from a cold start after `pm clear`** (logcat, condensed):

```
ThrakshaApplication: Launch security audit complete: Completed(results=[
  AppAuditResult(packageName=com.thraksha.demo.goodcaller,    appName=GoodCaller,    appType=CALLER_ID, findings=[]),
  AppAuditResult(packageName=com.thraksha.demo.villaincaller, appName=VillainCaller, appType=CALLER_ID, findings=[
     caller-media-permission-mismatch      HIGH     92 ...
     caller-package-visibility-mismatch    HIGH     88 ...
     caller-location-permission-mismatch   MEDIUM   74 ...
     caller-high-risk-profile              CRITICAL 95 ...])],
  rulepackVersion=2, rulesEvaluated=5)
```

| Check | Result |
|---|---|
| Launch audit fires automatically | ✅ once per process, no user action |
| GoodCaller | ✅ **0 findings** — `CLEAN / WATCHING` |
| VillainCaller | ✅ **4 findings** — `THREAT DETECTED` |
| Rulepack on the production path | ✅ `v2 verified · 5 active rules` shown on the dashboard |
| `Run Security Check` button | ✅ re-ran the audit twice (audit rows advanced 09:06:07 → 09:06:51 → 09:08) |
| Findings on the real `SecurityEventBus` | ✅ instrumented test asserts a `ThreatDetected` carrying `com.thraksha.demo.villaincaller` |
| Findings in the encrypted audit trail | ✅ visible in the live feed with rule id, severity, app, package, reason |
| Chain still verifies after audit writes | ✅ asserted by `AuditTrailPersistenceInstrumentedTest` |
| GoodCaller absent from THREAT rows | ✅ asserted, and visually confirmed |
| `AndroidRuntime:E` | ✅ no entries |
| APK contents | ✅ `assets/` holds exactly `rulepack.json`, `rulepack.sig`, `rulepack_public.key`; **no private key**, and none tracked by git |

Screenshots: `phase3_audit.png` (per-app verdicts), `phase3_auditfeed.png` (persisted chain rows).

### Known limitations

* **Detection is static-profile only.** Everything detected is a capability declared in a
  manifest and read through `PackageManager`. Thraksha does **not** observe another app reading
  photos, querying the package list, or sending anything — Android exposes no supported API for
  any of those, and none is simulated. The dashboard states this in-product.
* **Only `CALLER_ID` has a baseline.** `MESSAGING` / `BANKING` / `GAME` exist as enum constants
  with no baseline, so apps of those types are skipped rather than judged.
* **Scope is the two registered demo packages.** Thraksha holds no `QUERY_ALL_PACKAGES`, so it
  audits only what its `<queries>` declares. This is a deliberate trade of coverage for a clean
  permission posture.
* **Confidence values are authored constants in the signed pack**, not computed from evidence.
  They are honest as declared rule weights; they are not a statistical measure.
* **`ExecutionMode` still gates nothing.** Correct for this run — no enforcement exists — but the
  dashboard now labels it "(policy)" so it cannot be mistaken for the authority badge.
* **The audit chain grows without bound** and has no retention policy. Irrelevant at demo scale.
* **`FoundationStatus.Failed` remains unexercised at runtime** (carried over from Phase 1).

### Phase 3 acceptance criteria

| Criterion | Status |
|---|---|
| Guardian builds | ✅ |
| GoodCaller builds | ✅ |
| VillainCaller builds | ✅ |
| Existing foundation tests still pass | ✅ |
| Production code actually calls `RulepackLoader` | ✅ `SecurityAuditEngine.audit()` |
| Rulepack signature verified before evaluation | ✅ fail-closed; audit reports `Failed` instead of scanning |
| Thraksha can inventory both decoys | ✅ |
| Both decoys classified as `CALLER_ID` | ✅ |
| GoodCaller triggers zero threats | ✅ |
| VillainCaller reliably triggers the intended rules | ✅ 4 findings, deterministic across repeated + concurrent scans |
| Findings flow through the real `SecurityEventBus` | ✅ |
| Findings appear in the encrypted audit trail | ✅ chain still verifies |
| Launch audit executes reliably | ✅ gated on collector subscription |
| Manual check uses the exact same engine | ✅ same `runAudit()`, mutex-serialised |
| No fake threat-event path presented as real | ✅ removed entirely in Phase 1 |
| No enforcement claimed or performed | ✅ no DPM call anywhere; UI says so |
| VPN/network work not expanded | ✅ still gated off |
| No unsupported observation claim | ✅ stated in-product and in every rule's reason text |

---

# FINAL IMPLEMENTATION SUMMARY

## Actual end-to-end runtime flow (real class names)

```
Process start
  └─ ThrakshaApplication.onCreate()
       ├─ System.loadLibrary("sqlcipher")            → failure ⇒ FoundationStatus.Failed
       └─ initialiseSecureFoundation()   [Dispatchers.Default]
            ├─ DatabaseProvider.get(ctx)             → KeystoreManager.getOrCreatePassphrase()
            │                                          SupportOpenHelperFactory → encrypted Room
            ├─ ConfigStore.ensureDefaults()
            └─ startAuditCollector(AuditLog(auditDao))
                 ├─ collector: SecurityEventBus.events.collect { ... }
                 └─ SecurityEventBus.subscriptionCount.first { it > 0 }
                      ├─ FoundationStatus.markReady()
                      └─ runLaunchAudit()
                           │
                           ▼
                    SecurityAuditEngine.runAudit(context)        ◄── also the ONLY thing
                      [Mutex-serialised, Dispatchers.IO]              "Run Security Check" calls
                           │
                    1. RulepackLoader(context).load()
                         └─ RulepackVerifier.verify(bytes, sig, pubkey)   RSA-2048 / SHA-256
                            ✗ ⇒ AuditResult.Failed  (refuses to evaluate unverified rules)
                            ✓ ⇒ Rulepack v2, cached for the process
                           │
                    2. AppInventory(packageManager).inventory(DemoAppRegistry.demoPackages)
                         └─ getPackageInfo(pkg, GET_PERMISSIONS|GET_SERVICES|GET_RECEIVERS)
                            (visible only because of the manifest <queries> element)
                            ⇒ List<ObservedApp>   — not installed ⇒ omitted, never a crash
                           │
                    3. DemoAppRegistry.typeOf(pkg) ⇒ AppType.CALLER_ID
                       AppBaselines.forType(CALLER_ID) ⇒ AppBaseline
                            (no baseline ⇒ app skipped, never guessed)
                           │
                    4. RuleEngine(rulepack).evaluate(observedApp, baseline)
                         ├─ PERMISSION_NOT_IN_BASELINE  → per-capability Findings
                         └─ PROFILE_MISMATCH_COUNT      → compound Finding
                            ⇒ List<Finding>{ruleId, package, appName, appType,
                                             severity, confidence, reason, evidence}
                           │
                    5. SecurityEventBus.emit(...)
                         ├─ SecurityEvent.ThreatDetected(signalId, severity, details,
                         │                               confidence, packageName)  per finding
                         └─ SecurityEvent.ScanCompleted(appsScanned, findings, summary)
                           │
                           ▼
                    ThrakshaApplication collector
                         └─ AuditLog.append(type, details, tier)     [Mutex]
                              └─ AuditHasher.hash(id|ts|type|details|prevHash)
                                 → audit_log table, SQLCipher-encrypted, hash-chained
                           │
                    6. SecurityAuditEngine.state : StateFlow<AuditResult>
                           │
                           ▼
                    DashboardScreen
                      ├─ FULL POWER / ADVICE MODE   ← DemoMode.from(rememberPrivilegeLevel())
                      ├─ FOUNDATION ACTIVE / UNAVAILABLE ← FoundationStatus.state
                      ├─ SECURITY AUDIT card + "Run Security Check"
                      ├─ AppAuditCard per app: CLEAN / WATCHING | THREAT DETECTED
                      │    (rule id · severity · confidence · reason · evidence lines)
                      └─ RECENT SECURITY EVENTS ← auditDao.observeRecent(10) : Flow
```

## Divergences from the implementation guide, and why

| # | Divergence | Reason |
|---|---|---|
| 1 | The fake `Emit Test Threat Event` button was removed in **Phase 1**, not Phase 3 (§3.9). | Its output was indistinguishable from a real finding in the audit feed. Removing it before any detector existed guarantees every `THREAT` row observed from that point on came from real detection. Guide §3.9 permitted removal; only the phase differs. |
| 2 | Rulepack v2 keeps the 12 original rules as `enabled: false` rather than replacing them (§3.4). | Follows audit §18. Preserves the "real catalogue" texture while guaranteeing no rule appears evaluated without a detector. `RulepackVerifierTest` enforces that disabled rules carry no params. |
| 3 | Two extra rules beyond the three the guide suggested (`caller-location-…`, `caller-surveillance-…`). | §3.4 listed "potential rule concepts". The location rule fires on VillainCaller and is what makes the compound CRITICAL rule meaningful; the surveillance rule is a deliberately non-firing control proving rules do not fire without matching evidence. |
| 4 | **No audit DB schema change** (audit §16/Q8 recommended v2 columns). | Guide stop conditions warn against abandoning the audit-chain format, and no Phase 3 criterion needs structured columns. `ThreatDetected.packageName` carries attribution on the event instead. Deferred to a real UI requirement. |
| 5 | `AppType` includes `MESSAGING`/`BANKING`/`GAME` constants with no baselines. | §3.2 asked for extensibility without over-building. They are used by `RuleEngineTest` to prove type-scoping (a `GAME` with VillainCaller's exact permissions produces zero findings). |
| 6 | `SecurityEvent.ScanCompleted` added (§3.6 said extend "only where necessary"). | Necessary: without it, a clean audit leaves no trace, and the absence of a `THREAT` row would be the only evidence a scan happened. |

## Explicitly NOT done (as instructed)

Device Owner enforcement · permission revocation · app suspension · panic/safe mode · VPN packet
monitoring · network enforcement · cloud/backend · voice · ML/adaptive detection · autonomous
assistant execution · unrelated cleanup · rewrites of working foundation code.

`ThrakshaAccessibilityService` and `ThrakshaNotificationListener` were left untouched, as
instructed.

## Honesty statement

Thraksha detects **capabilities an app declares in its manifest**, read through
`PackageManager.getPackageInfo(..., GET_PERMISSIONS)`, and judges them against a hard-coded
baseline for the app's role. It does **not** — and this build never claims to — observe another
app reading photos or media, observe another app querying the installed-app list, or inspect
encrypted HTTPS payloads. Android exposes no supported API for any of those, and none is
simulated anywhere in this run. VillainCaller's findings are real observations of its real
declared manifest; VillainCaller never performs any of the actions its permissions would allow.

## Suggested next step

Phase 4 — `PolicyEngine` mapping `(severity, confidence, ExecutionMode, PrivilegeLevel) → ACT |
ADVISE | OBSERVE`, then `AdvisoryEnforcer` first. That completes the ADVICE MODE story with no
Device Owner required and no new device, and it is where `SecurityCapability.canEnforce()` and
`ConfigStore.getTierThresholds()` finally become load-bearing.
