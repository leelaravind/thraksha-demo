# THRAKSHA DEMO — PHASE 10 PROGRESS (10A → 10B → 10C)

**Date:** 2026-08-15
**Guide:** `temp/THRAKSHA_DEMO_IMPLEMENTATION_PHASE_10_ABC.md` (authoritative)
**Device:** Samsung `SM-G781B` (S20 FE), Android 13, arm64-v8a, 7.44 GiB RAM
**Model (shipped):** **`gemma-4-E2B-it.litertlm`** · 2.41 GiB · LITERTLM v1.5.0
· SHA-256 `181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c`
**Runtime:** `com.google.ai.edge.litertlm:litertlm-android:0.16.0` · `Backend.CPU(4)`

---

# HEADLINE

| Phase | Verdict |
|---|---|
| **10A — runtime bring-up** | ✅ **PASS** |
| **10B — intent + 100-case evaluation** | ✅ **PASS** against the owner-approved re-baselined criteria (safety targets unchanged) |
| **10C — full integration** | ✅ **PASS** — 9/9, run twice, once with the radios off |

> Thraksha understands natural-language intent entirely on the device. The model cannot
> control Android: it converts language into a constrained structured intent, the
> deterministic safety engine validates it, the planner shows the user exactly what will
> change, and only an explicit START executes anything — after which every action is
> verified and reversible through the unchanged Phase 9 engine.

**Proven on the handset, through the real UI:** *"I am going into a meeting for 45
minutes"* → **THRAKSHA UNDERSTOOD · Meeting Mode — 45 minutes** + four planner-derived
actions → device untouched → **START** → `91/AUTO/zen0/NORMAL` becomes
`77/MANUAL/zen1/VIBRATE` → **STOP & RESTORE** → `91/AUTO/zen0/NORMAL`, the exact pre-state.

# 1. PHASE 10A — PASS

Three models were provisioned and measured end-to-end
(`10a_runtime/PHASE10A_MODEL_COMPARISON.md`):

| | Gemma 3 1B q4 | **Gemma 4 E2B** | Gemma 4 E4B |
|---|---|---|---|
| Size | 0.54 GiB | **2.41 GiB** | 3.41 GiB |
| Load (cold) | 4.19 s | **6.63 s** | 22.03 s |
| Peak PSS | 1.83 GiB | **2.80 GiB** | 4.42 GiB |
| Median latency | 10.3 s | **11.5 s** | 22.2 s |
| Overall accuracy | 67 % | **84 %** | 80 % |
| Routine classification | 68.4 % | **97.3 %** | 81.6 % |

**E2B selected**: best accuracy per unit latency, best routine classification, comfortable
memory, and *ungated apache-2.0* rather than the gated gemma-licensed 1B. E4B was rejected —
2.2× the latency for one point of overall accuracy, worse routine classification, and it
dropped a prompt-injection case.

Other 10A results: SHA gate 3.19 s · constrained JSON 100 % conformant · 12-run stability
with no drift · unload releases 1.68 GiB in 289 ms · **GPU unusable on this handset**
(`Backend.GPU()` initialises then fails every inference — `Can not find OpenCL library`).

# 2. PHASE 10B — PASS (re-baselined criteria)

The owner reviewed the measured results and approved a re-baseline
(`10b_intent/PHASE10B_ACCEPTANCE_REBASELINE.md`). **No safety criterion was relaxed.**

| Criterion | Required | Measured | |
|---|---|---|---|
| Unsafe acceptance | **0** | **0 / 25** | ✅ |
| Adversarial refusal (destructive · security · injection) | **100 %** | **100 %** (20/20) | ✅ |
| Invalid JSON | 0 | **0 %** (0/100) | ✅ |
| SafetyPolicy final authority | yes | unmodified, 0 bypasses | ✅ |
| Routine classification | ≥ 90 % | **97.30 %** | ✅ |
| Parameter accuracy | ≥ 90 % | **97.30 %** | ✅ |
| Non-CLARIFY outcome accuracy | ≥ 90 % | **95.18 %** (79/83) | ✅ |
| *(tracked, not gated)* CLARIFY accuracy | — | 29.41 % | ⚠️ known limitation |

Overall expected-outcome accuracy is **84 %**; the original ≥ 90 % *overall* target was
replaced because it is dominated by the CLARIFY category, where a miss costs one tap on
Cancel and cannot touch the device.

**Fifteen evaluation runs**, twelve prompt/schema configurations, three models
(`10b_intent/PHASE10B_TUNING_LOG.md`). Two findings worth carrying forward:

* **A safety bound in a constrained decoder does not refuse — it rewrites.** With
  `minimum`/`maximum` on `durationMinutes`, "9 hours" was emitted as `9` and "forever" as
  `480`. The bound now lives in validation, where a request can actually be refused.
* **The same schema knob had the opposite correct setting per model.** Requiring all five
  properties made the 1B *invent* values (parameter accuracy 5 %); with E2B, leaving them
  optional made it *omit* them on 100/100 cases. Requiring them took parameter accuracy
  42 % → 97 % and routine classification 84 % → 97 %. Configuration decisions must be
  re-measured after a model change, never carried across.

A **test-validity defect** was also found and corrected: 3 of the 6 apps the evaluation
assumed were installed are not on this device, making four expectations structurally
unreachable. Fixed per the eval file's own standing instruction; only unreachable cases
were touched.

# 3. PHASE 10C — PASS

`Phase10CIntegrationInstrumentedTest` **9/9**, run twice — once online, once with Wi-Fi and
mobile data disabled.

* **No auto-execution.** Interpretation leaves the engine `IDLE`, `activeRun` null, and
  DND/ringer/brightness untouched — asserted directly. `AutomationEngine.start` is called
  from exactly one place in the codebase: the START button.
* **Hostile prompts: 8/8 refused**, no Android action, no state change.
* **Model output can never carry** an executor name, `intent://`, `adb`, `shell`,
  `content://` or `DevicePolicy`, nor an app that was not offered.
* **ASK THRAKSHA UI** with honest states; **no model-authored text ever reaches the screen**
  (the schema has no free-text field, so injection payloads cannot be echoed back).
* **Phase 9 never depends on the model** — with the model corrupted, a full Meeting cycle
  still runs and restores.
* **Audit**: `AI_INTENT_REQUESTED` → `AI_INTENT_PARSED`/`REJECTED` → `AI_PLAN_PREVIEWED` in
  the one hash-chained log; **the raw prompt is never persisted** (only its length), proven
  with a planted marker string; `verifyChain()` still passes.

**A production bug was found and fixed here:** `verify()` could publish `READY` over a
`LOADED` state, so the UI would have shown "available" for an already-loaded model.

# 4. OFFLINE PROOF — PASS

Wi-Fi **off**, mobile data **off**, `ping 8.8.8.8` → **"Network is unreachable"**, no active
default network. The full 9-test suite — model load, inference, 8 hostile prompts, a
20-request stress run, and a complete natural-language → preview → START → verified change
→ restore → audit cycle — **passed with no connectivity**.

App-uid network delta across the entire offline run: **≈194 bytes** of loopback noise.
Static proof: no network API anywhere under `ai/` (the only `http` strings are in
`RequestScopeGuard`, used to *refuse* URIs), no cloud provider names in `app/src/main`, and
**no HTTP client classes in the runtime AAR**. There is no cloud fallback to fall back to.

# 5. REGRESSIONS — ALL GREEN

| Suite | Result |
|---|---|
| `clean` build, all modules | **BUILD SUCCESSFUL** |
| `:app:testDebugUnitTest` | **213 tests, 0 failures** |
| `connectedDebugAndroidTest` — S20 FE | **90 tests, 0 failures, 3 skipped** |

The 3 skips are the frozen Phase 9 baseline skips. Phase 8 scanner, Phase 8.1 evidence and
User ACT, ThreatPack, Rulepack, Network Guard, Device Owner, GoodCaller/VillainCaller and
audit-chain verification all pass unchanged. `AutomationIntent`, `AutomationSafetyPolicy`,
`AutomationPlanner`, `AutomationEngine` and every executor are byte-identical to their
Phase 9 state.

# 6. PERFORMANCE, MEMORY, THERMAL

| Metric | Value |
|---|---|
| Integrity gate (2.41 GiB) | 3.19 s |
| Model load cold / warm | 6.63 s / 5.20 s |
| Median inference | 11.5 s (p95 11.6 s) |
| PSS loaded / peak | ~2.0 GiB / 2.80 GiB |
| 20-request stress | net PSS **−59 MiB**, latency drift **+0.5 %**, 0 errors |
| Unload | 1.68 GiB released in 289 ms |

No leak, no thermal throttling, no latency drift. The persistent-engine strategy is kept.

# 7. LIMITATIONS (stated plainly)

1. **CLARIFY accuracy 29.41 %.** Ambiguous input ("Set it up", "later", "Meeting mode for a
   bit") yields a cancellable proposal or a decline rather than a clarifying question.
   Model-invariant across all three models tested. **Must appear in the demo script.**
2. **~11.5 s per request.** Prefill of the ~350-token system instruction dominates. The GPU
   backend cannot help — this handset exposes no OpenCL to apps.
3. **Model is developer-provisioned** (2.41 GiB via `adb push`), not bundled. Deliberate.
4. **The `apache-2.0` label on the E2B repository is not a legal opinion.** The
   redistribution review in `model/MODEL_ATTRIBUTION.md` §4 still stands.
5. **APK is 74 MB** (both ABIs' native libs). ABI splits would halve it.

# 8. NON-NEGOTIABLES — ALL HELD

no cloud inference · no tool calling · no AI security verdicts · no SafetyPolicy weakening ·
no hardcoded evaluation shortcuts · no AI auto-execution · no runtime ADB/root/private APIs ·
no silent model substitution (the swap was an explicit owner decision, documented) ·
**nothing committed**.

# 9. DEVICE STATE

S20 FE restored and verified: `brightness_mode=AUTO`, `screen_off_timeout=600000`,
`zen_mode=0`, `mode_ringer=NORMAL`; radios back to their original `wifi_on=1`,
`mobile_data=0`. No routine active, no persisted restoration duty.

# 10. READINESS FOR MANUAL VERIFICATION

**Ready.** Automated verification of 10A, 10B and 10C is complete. The next session should
perform the human work the guide reserves for it (§43): natural-language testing by hand,
airplane-mode observation, UI feel, demo choreography, subjective latency, and wording
polish.

Two things to brief that session on: the **CLARIFY limitation** (§7.1) and the **~11.5 s
response time** (§7.2) — both known, measured and documented rather than surprises.
