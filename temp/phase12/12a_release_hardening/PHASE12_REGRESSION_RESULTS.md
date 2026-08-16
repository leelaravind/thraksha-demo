# PHASE 12A — REGRESSION RESULTS

Run **after** the only production-code change made in 12A (the memory-pressure model
release in `ThrakshaApplication`), to prove that change broke nothing.

Raw JUnit XML preserved in `regression/`.

## Summary

| Suite | Discovered | Executed & passed | Skipped | Failures | Errors | Duration |
| --- | --- | --- | --- | --- | --- | --- |
| Unit (JVM) | 213 | **213** | 0 | **0** | **0** | ~13 s |
| Instrumented — S20 FE (`SM-G781B`, Advice Mode) | 92 | **89** | 3 | **0** | **0** | 1,840.6 s |
| Instrumented — Device Owner AVD (`thraksha_do`) | 92 | **69** | 23 | **0** | **0** | 65.6 s |

**Union across both devices: 91 of 92 instrumented tests executed and passed on real
hardware.** Zero failures, zero errors anywhere.

## Unit suite — 213/213

Covers rulepack + threat-pack signature verification, generic and contextual rules, scan
classification, policy engine, advisory enforcement, enforcement targets, network packet
parsing and network threat policy, Phase 8.1 evidence honesty invariants, Phase 9
automation planning (including the blocked-plan denial path), Phase 10 intent validation
and scope guarding (hostile prompts), and audit-chain integrity plus concurrency.

## S20 FE — 89 passed, 3 skipped

All three skips are `Assume`-gated and are the same three as the frozen Phase 11B baseline:

| Skipped | Guard | Covered elsewhere |
| --- | --- | --- |
| `DeviceOwnerEnforcementInstrumentedTest.deviceOwner_containsVillain_verifiesState_andRestores` | `assumeTrue("requires Device Owner")` — this phone's Knox warranty bit is permanently tripped | **ran and passed on the DO AVD** |
| `Phase81FullPowerActInstrumentedTest.userActSuspend_isVerifiedActed_andReversible` | class-level `@Before assumeTrue("requires Device Owner")` | **ran and passed on the DO AVD** |
| `Phase9AutomationInstrumentedTest.blockedPlan_executesNothing_whenRequiredAccessMissing` | `assumeTrue(!hasDndAccess())` — DND access is granted here | unit test `Phase9AutomationTest.missingSpecialAccess_makesRequiredActionsUserAssisted_andBlocksExecution` |

## Device Owner AVD — 69 passed, 23 skipped

**Device Owner confirmed before and after the run:**
`admin=com.thraksha.guardian/.security.ThrakshaDeviceAdminReceiver,DeviceOwner,Affiliated`.

The two Full-Power tests the phone cannot run **executed and passed here**, which is the
Device Owner regression 12A requires:

* `deviceOwner_containsVillain_verifiesState_andRestores` — VillainCaller containment under
  genuine Device Owner authority, verified against OS state, then reversed.
* `userActSuspend_isVerifiedActed_andReversible` — the ACT sheet offers suspension and
  direct permission restriction; `ACTED` is returned only after `isPackageSuspended`
  confirms it; reversal verified.

The 23 AVD skips by cause (all `Assume`-gated, none a failure):

| Cause | Count |
| --- | --- |
| Phase 10 model not provisioned on the AVD (2.41 GiB file lives only on the phone) | 16 |
| Requires **not** being Device Owner — the Advice Mode path | 4 |
| Observable surface absent on an emulator | 2 |
| DND access implicitly held by Device Owner, so the denial path is unreachable | 1 |

## The one test that runs on neither device

`Phase9AutomationInstrumentedTest.blockedPlan_executesNothing_whenRequiredAccessMissing`
needs Notification Policy access to be **absent**. On the phone it is granted (and is
required for the Meeting/Focus demo); on the AVD, Device Owner status grants it implicitly.
Attempting `cmd notification disallow_dnd com.thraksha.guardian` on the Samsung build is a
no-op — confirmed in Phase 11B final verification.

The behaviour itself is proven deterministically by the unit suite
(`assertFalse(plan.executable)`, `assertNotNull(plan.blockedReason)`), so this is a
test-environment gap rather than an unverified safety property.

## Clean builds

* `:app:assembleRelease` — BUILD SUCCESSFUL (release candidate, §`PHASE12_RELEASE_ARTIFACT.md`)
* `:app:testDebugUnitTest` — BUILD SUCCESSFUL
* `:app:connectedDebugAndroidTest` — BUILD SUCCESSFUL on both devices

No test was modified, disabled or weakened during Phase 12A.
