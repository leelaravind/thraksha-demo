# PHASE 12.1 — REGRESSION RESULTS

Guide reference: §28. Covers the regressions affected by the Task A / B / C changes.

Build under test: working tree with Task A (visibility), Task B (signing config) and
Task C (wording + versioning) applied. `versionCode 2`, `versionName 1.0-privatealpha-rc2`.

## 1. Unit suite

```
java -jar gradle/wrapper/gradle-wrapper.jar :app:assembleDebug :app:testDebugUnitTest
```

| Result | Value |
| --- | --- |
| Outcome | **BUILD SUCCESSFUL** |
| Tests | **213** |
| Failures / errors / skipped | **0 / 0 / 0** |

Run twice: once after the Task A + B changes, once after the Task C wording and versioning
changes. Green both times.

## 2. S20 FE instrumented suite (`RZCW40LVBJD`, SM-G781B, Android 13)

```
ANDROID_SERIAL=RZCW40LVBJD java -jar gradle/wrapper/gradle-wrapper.jar \
  :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.notClass=\
com.thraksha.guardian.Phase10BEvaluationInstrumentedTest,\
com.thraksha.guardian.Phase10ABackendBenchmarkTest
```

Phase 10B evaluation and the Phase 10A backend benchmark are excluded per guide §28
("No need to repeat exploratory AI model evaluation"). Everything else ran.

| Result | Value |
| --- | --- |
| Outcome | **BUILD SUCCESSFUL** (14m 49s) |
| Tests | **90** |
| Failures | **0** |
| Errors | **0** |
| Skipped | **3** (all `assumeTrue` guards, listed below) |
| Wall time | 873.1 s |

Per class:

| Class | Passed | Skipped |
| --- | --- | --- |
| AuditLogInstrumentedTest | 1 | — |
| AuditTrailPersistenceInstrumentedTest | 1 | — |
| DeviceOwnerEnforcementInstrumentedTest | 5 | 1 |
| DeviceScanInstrumentedTest | 7 | — |
| EncryptedStoreTest | 2 | — |
| ExampleInstrumentedTest | 1 | — |
| FoundationInstrumentedTest | 3 | — |
| NetworkGuardInstrumentedTest | 7 | — |
| PackageVisibilityInstrumentedTest | 4 | — |
| Phase10ARuntimeInstrumentedTest | 8 | — |
| Phase10CIntegrationInstrumentedTest | 9 | — |
| Phase81EvidenceInstrumentedTest | 10 | — |
| Phase81FullPowerActInstrumentedTest | — | 1 |
| Phase9AutomationInstrumentedTest | 11 | 1 |
| PolicyPipelineInstrumentedTest | 5 | — |
| SecurityAuditEngineInstrumentedTest | 8 | — |
| ThreatIntelInstrumentedTest | 5 | — |

### The 3 skips are correct, not masked failures

| Skipped test | Reason |
| --- | --- |
| `DeviceOwnerEnforcementInstrumentedTest.deviceOwner_containsVillain_verifiesState_andRestores` | Requires a Device Owner. The S20 FE deliberately has none (Advice Mode). **Run on the DO AVD instead — passed, see §3.** |
| `Phase81FullPowerActInstrumentedTest.userActSuspend_isVerifiedActed_andReversible` | Same — needs Device Owner. **Run on the DO AVD — passed, see §3.** |
| `Phase9AutomationInstrumentedTest.blockedPlan_executesNothing_whenRequiredAccessMissing` | Asserts behaviour when required access is *missing*; the phone has the access granted, so the precondition does not hold. |

Neither DO test is left unproven — both were executed on the AVD where the precondition
holds.

## 3. Device Owner AVD (`emulator-5554`, x86_64)

`connectedAndroidTest` could not be used here: AGP uninstalls the app after the run, and
Android refuses to uninstall a Device Owner package — that would either fail the build or,
worse, strip the AVD's provisioning. The APKs were therefore installed as an **update**
(same debug signer, so Device Owner status is preserved) and the tests driven directly:

```
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5554 shell am instrument -w -e class <DO-relevant classes> \
  com.thraksha.guardian.test/androidx.test.runner.AndroidJUnitRunner
```

Device Owner confirmed intact before and after:
`admin=com.thraksha.guardian/.security.ThrakshaDeviceAdminReceiver,DeviceOwner,Affiliated`

| Result | Value |
| --- | --- |
| Outcome | **OK (36 tests)** |
| Failures | **0** |
| Wall time | 73.2 s |

Classes: `DeviceOwnerEnforcementInstrumentedTest`, `Phase81FullPowerActInstrumentedTest`,
`PackageVisibilityInstrumentedTest`, `DeviceScanInstrumentedTest`,
`ThreatIntelInstrumentedTest`, `SecurityAuditEngineInstrumentedTest`,
`PolicyPipelineInstrumentedTest`.

### The results that matter most for Task A

Broad package visibility means the scanner now sees far more packages than before. The
critical question is whether that changed what Thraksha is willing to *act on*. It did not:

| Test | Result | What it proves |
| --- | --- | --- |
| `arbitraryPackage_isRefused` | **PASS** | An arbitrary real package cannot be containment-targeted, even under Device Owner and even though the scanner can now see it. This is the "no automatic arbitrary-app containment" criterion. |
| `goodCaller_isRefusedAsTarget_regardlessOfAuthority` | **PASS** | Allowlisting is enforced independently of privilege level. |
| `guardian_cannotTargetItself` | **PASS** | No self-containment. |
| `restore_refusesNonAllowlistedTargets` | **PASS** | The restore path is allowlisted too. |
| `deviceOwner_containsVillain_verifiesState_andRestores` | **PASS** | The one legitimate containment target still works end-to-end and reverses. |
| `userActSuspend_isVerifiedActed_andReversible` | **PASS** | User ACT remains verified and reversible. |

`withoutDeviceOwner_villainDecisionIsNotAuthorised_andNothingChanges` was skipped on the
AVD (`AssumptionViolatedException: requires a NON-Device-Owner state`) — correct, since the
AVD *is* Device Owner. It passed on the S20 FE, where the precondition holds. Between the
two devices every test in that class has been executed under the state it requires.

### AVD left clean

```
dpm list-owners -> DeviceOwner intact
com.thraksha.demo.villaincaller -> suspended=false   (containment reversed)
com.thraksha.demo.goodcaller    -> suspended=false   (never touched)
com.thraksha.guardian.test      -> uninstalled
```

## 3a. POST-RC2 RE-RUN (final, against the shipped source)

After the RC2 build, the affected suites were re-run against the exact source that produced
the shipped APK — including the renamed
`DeviceScanInstrumentedTest.build_holdsFullVisibility_andDiscoversTheRegistry`.

| Suite | Result |
| --- | --- |
| **Clean build** (`gradle clean` → `:app:assembleRelease`) | **BUILD SUCCESSFUL** (2m 07s) — this is how RC2 was produced |
| **Unit** (`:app:testDebugUnitTest`) | **213 tests, 0 failures, 0 errors, 0 skipped** |
| **Device Owner AVD** — full suite via `am instrument` | **OK (90 tests), 0 failures** (92.2 s) |

The DO AVD run covered every class except the two excluded by §28
(`Phase10BEvaluationInstrumentedTest`, `Phase10ABackendBenchmarkTest`), with Device Owner
provisioning intact throughout and the test APK removed afterwards.

### Why the S20 FE instrumented suite was NOT re-run after RC2

Deliberate, and it matters: `connectedAndroidTest` installs a debug build and **uninstalls
the app when it finishes**. Running it now would have destroyed the freshly installed RC2,
its encrypted database, its audit chain and the 2.4 GiB provisioned model — exactly the
damage documented in §4 below. The phone's coverage was instead obtained by:

* the pre-RC2 full run (90 tests, 0 failures) against the same source, and
* **direct verification on the RC2 release APK itself** — real scan, automation
  START/restore, offline AI, reboot, audit persistence — which is stronger evidence than an
  instrumented run on a debug build, because it exercises the artifact that actually ships.

## 4. Known gaps in this run

* ~~The renamed test needs re-execution~~ — **resolved**: re-run under the new name on the
  DO AVD in §3a, passed.
* ~~The release-APK proof is owed~~ — **resolved**: measured directly on RC2, see
  `PHASE12_1_REAL_SCAN.md` (441/26/415, exact ground-truth parity).
* ~~Clean build deferred~~ — **resolved**: RC2 was produced from a clean tree.
* Remaining, and unchanged from Phase 12: `Phase10BEvaluationInstrumentedTest` and
  `Phase10ABackendBenchmarkTest` were not run, per §28's instruction not to repeat
  exploratory model evaluation. No regression appeared that would require them.
* The DO AVD lacks the provisioned model, so a few Phase 10A/10C cases self-skip there via
  `assumeTrue`. Those same cases passed on the S20 FE (Phase10A 8/8, Phase10C 9/9), and the
  model path was additionally proven end-to-end on RC2 itself (SHA verified, "Integrity
  verified" in-app, offline inference produced a correct plan).
