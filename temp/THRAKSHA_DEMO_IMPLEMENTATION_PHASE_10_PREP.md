
This is **not the full Phase 10 AI implementation yet**. This prep phase should make the model/runtime assets trustworthy and the deployment path reproducible before Claude starts integrating inference.

# THRAKSHA DEMO — PHASE 10 PREPARATION GUIDE

## GEMMA 3 1B + LITERT-LM ASSET INTEGRITY, RUNTIME SETUP, DELIVERY, INTENT CONTRACT, EVALUATION

## 1. PURPOSE

Prepare the existing Thraksha project for Phase 10 on-device language understanding using:

**Model:** Gemma 3 1B IT
**Artifact:** `model/Gemma3-1B-IT_multi-prefill-seq_q4_ekv4096.litertlm`
**Runtime target:** LiteRT-LM
**Execution:** fully on-device after model provisioning

The objective of this prep phase is to establish:

1. model integrity;
2. Git safety;
3. licensing/attribution record;
4. exact LiteRT-LM runtime dependency;
5. a reliable model-delivery strategy to the S20 FE;
6. the structured `AutomationIntent` output contract;
7. the prompt contract;
8. an evaluation dataset;
9. fallback/error behavior;
10. an offline-verification plan.

Do NOT yet connect free-form model output to Android executors.

Phase 9 remains the known-good deterministic automation baseline.

---

# 2. READ FIRST

Read:

1. `temp/THRAKSHA_DEMO_PHASE_9_PROGRESS.md`
2. `temp/PHASE9_AUTOMATION_CAPABILITY_MATRIX.md`
3. `temp/PHASE9_ROUTINE_VALIDATION.md`
4. Phase 9 automation models/code, especially:

   * `AutomationIntent`
   * `AutomationSafetyPolicy`
   * `AutomationPlanner`
   * `AutomationEngine`
5. `model/README.md`
6. the downloaded `.litertlm` artifact metadata.

Treat the existing Phase 9 safety boundary as immutable:

`Model output`
→ `AutomationIntent`
→ `AutomationSafetyPolicy`
→ `Planner`
→ `Executors`

The model must never call executors directly.

---

# 3. MODEL FILE CHECK

Verify that this exact file exists:

`model/Gemma3-1B-IT_multi-prefill-seq_q4_ekv4096.litertlm`

Record:

* exact filename;
* byte size;
* SHA-256;
* modification timestamp;
* readable/openable status.

Create:

`temp/PHASE10_MODEL_INTEGRITY.md`

Include the exact SHA-256 and file size.

Do not modify the model binary.

---

# 4. GIT SAFETY

Inspect `.gitignore`.

Ensure model binaries are ignored, at minimum:

`/model/*.litertlm`
`/model/*.task`

Verify:

`git status`

does not stage/track the large model file.

Do NOT remove `model/README.md` from version control if that documentation is intended to be tracked.

Record the result in the model-integrity doc.

---

# 5. MODEL SOURCE / LICENSE RECORD

Document the model source and license/terms reference.

Create:

`model/MODEL_ATTRIBUTION.md`

Record:

* Model: Gemma 3 1B IT
* Runtime target: LiteRT-LM
* Artifact filename
* Source repository/page
* Date acquired
* Purpose: local intent interpretation for Thraksha
* Model binary intentionally gitignored
* Runtime inference intended to remain local
* Applicable Gemma license/terms reference
* Any redistribution constraints
* Any investor-demo-only caveat if relevant

Do not invent license terms.

If exact local license text is unavailable, document the source/terms URL/reference and mark redistribution obligations as requiring review.

---

# 6. LITERT-LM RUNTIME SELECTION

Determine the exact current Android dependency/library required to run this `.litertlm` artifact.

Do not download random JAR/AAR files manually.

Use the official Android dependency path.

Create:

`temp/PHASE10_LITERT_RUNTIME.md`

Record:

* exact dependency coordinates;
* exact version;
* minimum Android/API requirements;
* ABI requirements;
* supported backends/delegates;
* initialization API;
* model loading API;
* inference API;
* structured/text-generation limitations;
* threading requirements;
* lifecycle requirements;
* memory implications;
* whether the selected model/runtime pairing is supported.

Do not integrate inference until the model/runtime compatibility is established.

---

# 7. DEVICE CAPABILITY CHECK

Use the connected Samsung S20 FE.

Record:

* Android version/API;
* CPU ABI;
* RAM;
* available storage;
* GPU/Vulkan/OpenGL capability;
* model file free-space requirement;
* expected model memory footprint where measurable;
* whether LiteRT-LM can initialize on this device.

Create:

`temp/PHASE10_DEVICE_CAPABILITY.md`

No model execution claim until runtime initialization succeeds on the phone.

---

# 8. MODEL DELIVERY STRATEGY

Do NOT blindly place the ~557 MB model inside `app/src/main/assets`.

Investigate the safest demo delivery path.

Evaluate at least:

### Option A — development-side provisioning

Developer copies/pushes the model to an app-readable location on the S20 FE before the demo.

### Option B — packaged asset

Only if APK size/install behavior remains acceptable.

### Option C — first-run local provisioning

App imports/copies a model from a known local file/location.

For each option record:

* APK size impact;
* install time;
* storage duplication;
* update complexity;
* app sandbox accessibility;
* reproducibility;
* investor-demo reliability.

Choose one.

Recommended bias for the investor demo:

**developer-provisioned local model file**, not a giant APK, if runtime allows it cleanly.

Create:

`temp/PHASE10_MODEL_DELIVERY.md`

---

# 9. MODEL PATH CONTRACT

Define one authoritative model location abstraction.

Example concept:

`ModelRepository`
or
`LocalModelProvider`

Responsibilities:

* resolve model path;
* verify file exists;
* verify SHA-256;
* expose READY / MISSING / INVALID;
* never silently fall back to another model.

Do not hardcode multiple ad-hoc paths across UI/runtime code.

For this prep phase, implement only the path/integrity abstraction if useful.

Do not yet wire free-form inference into automation.

---

# 10. MODEL STATE

Define model lifecycle states:

* MISSING
* VERIFYING
* READY
* LOADING
* LOADED
* ERROR
* UNSUPPORTED

Use structured state, preferably `StateFlow` when integration starts.

The UI in Phase 10 must never claim “On-device AI ready” unless integrity verification and runtime initialization succeeded.

---

# 11. INTENT CONTRACT

Inspect existing Phase 9 `AutomationIntent`.

Do NOT replace it unnecessarily.

Create:

`temp/PHASE10_AUTOMATION_INTENT_CONTRACT.md`

Document exactly which fields the model may populate.

At minimum:

* routine type;
* duration;
* brightness if allowed;
* timeout if allowed;
* target app only from permitted/validated values;
* optional user preferences already supported by Phase 9.

The model must not produce:

* arbitrary shell commands;
* arbitrary Android intents/URIs;
* package suspension requests;
* security-policy overrides;
* executor names;
* raw API calls;
* file paths;
* network requests;
* permission-grant commands;
* destructive operations.

Everything outside the schema must be rejected.

---

# 12. STRUCTURED OUTPUT FORMAT

Choose a strict machine-readable output format.

Recommended:

JSON corresponding exactly to `AutomationIntent`.

Example shape only:

```json
{
  "routine": "MEETING",
  "durationMinutes": 45
}
```

Do not accept prose mixed with JSON.

Do not rely on regex extraction from arbitrary paragraphs unless no safer runtime option exists.

Define:

* allowed keys;
* required keys;
* enum values;
* numeric bounds;
* unknown-field behavior;
* null behavior;
* invalid JSON behavior.

All generated output must still pass `AutomationSafetyPolicy`.

---

# 13. PROMPT CONTRACT

Create:

`temp/PHASE10_MODEL_PROMPT.md`

The system/instruction prompt should make the model perform one narrow task:

**Interpret a user request and emit one structured `AutomationIntent`.**

It must explicitly forbid:

* explanations;
* markdown;
* direct device actions;
* unsupported capabilities;
* invented apps;
* destructive actions;
* security-control changes.

If the request cannot be represented safely:

return a structured UNSUPPORTED/CLARIFY result rather than inventing actions.

---

# 14. CLARIFICATION MODEL

Define how ambiguous requests behave.

Examples:

“I need to focus.”

→ valid Focus intent with safe defaults.

“Make everything silent forever.”

→ reject or bound duration according to policy.

“Disable security and open this APK.”

→ unsupported.

“Open some app.”

→ clarification required if target is ambiguous.

Do not allow the model to fill high-risk missing information by guessing.

---

# 15. EVALUATION DATASET

Create:

`temp/PHASE10_INTENT_EVAL.json`

Target 75–100 test prompts.

Include categories:

* Meeting Mode variants;
* Focus Mode variants;
* Driving Mode variants;
* Custom routine requests;
* durations;
* brightness requests;
* target-app requests;
* ambiguous prompts;
* malformed prompts;
* unsupported requests;
* destructive requests;
* prompt-injection-like text;
* attempts to bypass policy;
* unrelated conversation;
* non-command text;
* excessive values.

For each record include expected outcome:

* expected routine;
* expected bounded parameters;
* UNSUPPORTED;
* CLARIFY;
* REJECTED.

Do not train/fine-tune from this file.

It is evaluation only.

---

# 16. FALLBACK BEHAVIOR

Phase 9 deterministic UI must remain fully usable if the model fails.

Define behavior for:

### Model missing

Show:
`ON-DEVICE AI MODEL NOT INSTALLED`

Automation buttons remain usable.

### Integrity failure

Show:
`MODEL INVALID`

Do not load it.

### Load failure / OOM

Show:
`MODEL UNAVAILABLE`

Automation buttons remain usable.

### Inference timeout

Show:
`COULD NOT INTERPRET REQUEST`

No automation executes.

### Invalid model output

Reject output.

Do not attempt partial execution.

### SafetyPolicy rejection

Show why the requested action cannot be performed.

Never bypass Phase 9 validation because the model “seemed confident.”

---

# 17. OFFLINE REQUIREMENT

The final Phase 10 demo must work with:

* Wi-Fi OFF;
* mobile data OFF.

The model/runtime path must make no network call during inference.

Do not add telemetry or cloud fallback.

Create:

`temp/PHASE10_OFFLINE_VERIFICATION.md`

For now, define the procedure.

Actual offline inference proof belongs to the full Phase 10 implementation.

---

# 18. PERFORMANCE BASELINE PLAN

Before full integration, define metrics:

* model integrity-check time;
* model load time;
* RAM before load;
* RAM after load;
* first-token latency;
* total intent inference latency;
* tokens/sec if available;
* CPU/GPU utilization where practical;
* thermal behavior over repeated requests;
* model unload/reload behavior.

Create a section in:

`temp/PHASE10_DEVICE_CAPABILITY.md`

Do not promise acceptable performance before measurement.

---

# 19. MODEL SESSION DESIGN

Investigate whether the runtime should use:

* one persistent loaded model session;
* load-on-demand/unload;
* retained singleton/service.

For the demo, prefer reliability over aggressive memory optimization.

Document:

* memory trade-off;
* startup trade-off;
* process-death behavior;
* concurrency behavior;
* cancellation behavior.

Do not run multiple inference sessions concurrently unless proven safe.

---

# 20. THREADING

Inference must never block the Compose/UI thread.

When implementation starts:

* model load off main;
* inference off main;
* cancellation supported where runtime allows;
* UI reflects LOADING / THINKING / ERROR honestly.

No implementation required yet beyond architecture documentation if runtime code is not ready.

---

# 21. SECURITY BOUNDARY

This is non-negotiable.

The final Phase 10 path must be:

`User text`

→ local model

→ parsed structured output

→ schema validation

→ `AutomationIntent`

→ `AutomationSafetyPolicy`

→ `AutomationPlanner`

→ plan preview

→ user START

→ Phase 9 engine

→ executors

→ verify

→ restore

→ audit.

The model may NEVER:

* invoke executor methods;
* access DevicePolicyManager directly;
* access Settings APIs directly;
* construct unrestricted Intents;
* alter PolicyEngine;
* disable Network Guard;
* modify security findings;
* write audit rows directly;
* bypass preview/validation.

---

# 22. NO AUTO-EXECUTION FROM MODEL OUTPUT

For the investor demo:

Natural language must first produce a **proposed plan**.

The user must see:

**THRAKSHA UNDERSTOOD:**

`Meeting Mode — 45 min`

Planned actions...

Then explicitly press:

**START**

Do not immediately execute merely because inference completed.

This gives the user a visible trust boundary.

---

# 23. MODEL OUTPUT EXPLANATION

Do not ask Gemma to provide a long reasoning explanation.

The app should derive human-readable UI from the validated `AutomationIntent` and `AutomationPlan`.

Example:

Model output:

`MEETING, 45 min`

App-generated UI:

`I understood this as Meeting Mode for 45 minutes.`

`Thraksha will enable DND, adjust brightness, and open Calendar.`

The model should not be trusted to describe what Android will actually do.

The deterministic planner knows that.

---

# 24. NO MODEL-BASED SECURITY VERDICTS

Do not connect Gemma to:

* malware classification;
* threat-intelligence verdicts;
* scanner findings;
* enforcement decisions.

Phase 10 model scope is:

**natural-language automation intent only.**

Security remains deterministic/signed-evidence driven.

---

# 25. REQUIRED DOCUMENTS

Create/update:

* `temp/PHASE10_MODEL_INTEGRITY.md`
* `model/MODEL_ATTRIBUTION.md`
* `temp/PHASE10_LITERT_RUNTIME.md`
* `temp/PHASE10_DEVICE_CAPABILITY.md`
* `temp/PHASE10_MODEL_DELIVERY.md`
* `temp/PHASE10_AUTOMATION_INTENT_CONTRACT.md`
* `temp/PHASE10_MODEL_PROMPT.md`
* `temp/PHASE10_INTENT_EVAL.json`
* `temp/PHASE10_OFFLINE_VERIFICATION.md`
* `temp/THRAKSHA_DEMO_PHASE_10_PREP_PROGRESS.md`

---

# 26. PREP ACCEPTANCE CRITERIA

This preparation phase is complete only when:

* model binary exists;
* SHA-256 is recorded;
* model binary is gitignored/untracked;
* attribution/license record exists;
* exact LiteRT-LM dependency/version is selected;
* model/runtime compatibility is documented;
* S20 FE capability is documented;
* model delivery strategy is selected;
* authoritative model path/integrity strategy exists;
* `AutomationIntent` contract is frozen;
* structured-output format is frozen;
* prompt contract exists;
* evaluation dataset exists;
* fallback behavior is defined;
* offline test procedure exists;
* performance metrics are defined;
* Phase 9 safety boundary is preserved;
* no free-form model output reaches executors;
* no AI inference is allowed to auto-execute a routine;
* no security verdict uses the model.

---

# 27. STOP CONDITIONS

STOP and document rather than improvise if:

* the downloaded model SHA cannot be established;
* LiteRT-LM does not support the artifact;
* selected runtime requires a different model format;
* S20 FE cannot initialize the runtime;
* model delivery would require an unacceptable APK design;
* licensing/redistribution obligations are unclear;
* the intent contract cannot be mapped safely onto Phase 9;
* structured output cannot be validated reliably;
* implementing the model would require weakening SafetyPolicy;
* runtime integration would bypass user preview;
* cloud inference becomes necessary.

Do not swap to another model automatically.

If Gemma 3 1B + LiteRT-LM is blocked, document why and stop for a model decision.

---

# 28. FINAL PREP RESULT

At the end of this preparation run we should be able to say:

> **“The exact Gemma model is identified and verified, its runtime and delivery path are fixed, the phone is proven capable or a blocker is documented, and the model has a strict contract: it may interpret language into a validated AutomationIntent but it has no authority to control Android.”**

Only after this prep passes should the full Phase 10 inference implementation begin.
