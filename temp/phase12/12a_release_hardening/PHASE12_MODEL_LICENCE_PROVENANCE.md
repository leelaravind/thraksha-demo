# PHASE 12A — MODEL HARDENING, PROVENANCE AND LICENCE

## 1. Exact approved model identity

| Field | Value |
| --- | --- |
| File name | `gemma-4-E2B-it.litertlm` |
| Expected size | `2,588,147,712` bytes (2.41 GiB) |
| **Expected SHA-256** | `181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c` |
| Runtime | LiteRT-LM (`com.google.ai.edge.litertlm:litertlm-android:0.16.0`) |
| Location | `getExternalFilesDir("models")` — app-specific external storage |
| Bundled in the APK? | **No** (0 `.litertlm` entries in the release APK) |

`LocalModelRepository.modelFile()` is documented as *"The single path. No other component
may construct a model path."* — verified: no other production file references a model path.

## 2. Integrity gate

`LocalModelRepository.verify()` runs cheapest-first and never throws:

```
exists → canRead → exact byte length → LiteRT-LM container magic ("LITERTLM") → SHA-256
```

Each failure produces a distinct, honest state rather than a generic error:

| Condition | Published state | User-facing text |
| --- | --- | --- |
| File absent | `MISSING` | "Model not installed" |
| Not readable | `ERROR` | "MODEL INVALID — file is not readable" |
| Wrong size | `ERROR` | "MODEL INVALID — expected N bytes, found M" |
| Bad magic / hash | `ERROR` | "MODEL INVALID …" |
| Verified, not yet loaded | `READY` | "Local AI available" |
| Engine initialised | `LOADED` | "Local AI ready" |
| Unsupported ABI/runtime | `UNSUPPORTED` | "Not supported on this device" |

**`LOADED` is the only phase in which the app claims AI is ready**, and reaching it requires
both a passing SHA gate *and* a successful `Engine.initialize()`. "The file exists" is never
treated as readiness.

## 3. Failure behaviour — covered by instrumented tests, not by assertion

| Requirement | Test |
| --- | --- |
| Exact pinned artifact verified | `Phase10ARuntimeInstrumentedTest.a01_modelIntegrityGate_verifiesTheExactPinnedArtifact` |
| Invalid hash rejected, automation unaffected | `Phase10CIntegrationInstrumentedTest.invalidModelHash_isRejected_andAutomationStillWorks` |
| Missing model reported, automation unaffected | `Phase10CIntegrationInstrumentedTest.missingModel_reportsMissing_andAutomationStillWorks` |
| Unload releases state, reload works | `Phase10ARuntimeInstrumentedTest.a07_unloadThenReload_worksAndReleasesState` |
| Model output can never carry an executor or intent URI | `Phase10CIntegrationInstrumentedTest.modelOutputCanNeverCarryAnExecutorOrIntentUri` |
| Hostile/destructive prompts produce no intent and no Android action | `Phase10CIntegrationInstrumentedTest.securityAndDestructivePrompts_produceNoIntentAndNoAndroidAction` |
| Active routine independent of model lifecycle | `Phase10CIntegrationInstrumentedTest.activeRoutine_isIndependentOfModelLifecycle` |

All pass on the S20 FE (see `PHASE12_REGRESSION_RESULTS.md`). These tests deliberately
corrupt/remove the model themselves, so no manual tampering with the provisioned file was
needed.

Additionally observed live on the release build during 12A: the pending **plan preview
survived a model unload** triggered by memory pressure — correct, because the plan is
deterministic planner output and holds no reference to the model.

## 4. The model cannot act

Structural, not behavioural:

* The model's only output channel is JSON validated by `IntentJsonValidator` against a
  fixed schema with a closed key set (`result`, `routine`, `durationMinutes`, `targetApp`,
  `reasonCode`).
* A valid parse produces an `AutomationIntent` — a data class naming a **routine type**,
  never an executor, class, command or URI.
* `AutomationSafetyPolicy.validateIntent()` is then final: it bounds duration, validates any
  target package against an allow-list, and rejects `com.thraksha.guardian` as a launch
  target.
* `RequestScopeGuard` screens the *input* before the model runs, refusing whole domains
  (credentials, payments, device wipe) with ordinary deterministic code rather than relying
  on a small model's judgement.
* `AutomationPlanner` — not the model — decides which actions a routine performs.
* Nothing executes until the user presses **START**.

## 5. Observed interpretation inaccuracy (honest note)

During 12A the request *"focus for 30 minutes"* was understood as **"Focus Mode — 60
minutes"**. Investigated: this is not a clamp — `AutomationSafetyPolicy` only *rejects*
out-of-bounds durations, and no default overrides a parsed value. The model returned 60.

The pipeline behaved correctly: it displayed exactly what it had understood, before doing
anything, and the user could cancel. This is precisely the failure the explicit
preview → START gate exists to catch. Recorded as an accuracy limitation (MEDIUM) in the
12C risk register, not as a defect.

## 6. Provenance and licence — UNRESOLVED, and deliberately so

| Aspect | Status |
| --- | --- |
| Model family | Google Gemma (E2B instruction-tuned), converted to LiteRT-LM container |
| Licence | **Gemma Terms of Use** — not an OSI open-source licence. It carries use restrictions and redistribution conditions. |
| Redistribution clearance | **NOT ESTABLISHED.** No legal review has been performed. |
| Mitigation in this build | The model is **not redistributed**: it is absent from the APK and is provisioned by the developer onto the device out-of-band. |
| LiteRT-LM runtime licence | Apache-2.0 — no redistribution issue |

Per guide §15 this separates cleanly:

* **Technical readiness — met.** The pinned artifact is identity-checked, integrity-gated
  and sandboxed from execution authority.
* **Commercial/legal redistribution readiness — NOT met, and not claimed.**

A private alpha may proceed on this basis *only because* the model is developer-provisioned
rather than shipped, and that limitation is stated in the release notes and the risk
register. **No redistribution clearance is claimed anywhere in Phase 12.**

## 7. Threat-pack / rulepack provenance

Both are signed offline with RSA private keys held outside the repository (`keys/`,
git-ignored) and verified in-app against bundled **public** keys. Threat-pack sourcing and
licensing are documented separately in `temp/THREAT_INTELLIGENCE_PROVENANCE.md`; the
shipped indicators are demo/test records, and the app labels matches accordingly.
