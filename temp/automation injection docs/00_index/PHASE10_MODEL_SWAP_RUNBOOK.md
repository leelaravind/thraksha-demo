# PHASE 10 — MODEL SWAP RUNBOOK

**Purpose:** the 10B accuracy gate failed on Gemma 3 1B IT q4 (67 % vs ≥ 90 %). The owner
chose *"provide a larger model"*. Everything except the model artifact is model-agnostic
and already proven, so this is a re-run, not a rewrite. This runbook makes the swap
mechanical.

**Guide §27 is respected:** the model is **not** substituted automatically. The owner
selects and provisions the artifact; this document only prepares the ground.

---

## 1. Candidate artifacts — genuine `.litertlm`, verified against the Hugging Face API

⚠️ **`litert-community/Gemma3-4B-IT` is NOT usable.** It publishes only `.task` files
(MediaPipe LLM Inference format, 2.56–3.9 GB). Our runtime consumes `.litertlm` via
`EngineConfig.modelPath`. A `.task` file will fail the container-magic gate in
`LocalModelRepository` (it will not begin with `LITERTLM`) — which is the gate working
correctly, not a bug.

The genuine `.litertlm` step-ups are the `-litert-lm` repositories:

| Repo | File | Size | Gated? | License |
|---|---|---|---|---|
| `litert-community/gemma-4-E2B-it-litert-lm` | `gemma-4-E2B-it.litertlm` | **2.41 GiB** | **No** | apache-2.0 |
| `litert-community/gemma-4-E4B-it-litert-lm` | `gemma-4-E4B-it.litertlm` | 3.41 GiB | No | apache-2.0 |

Both are *ungated and Apache-2.0-labelled* — a **better licensing posture** than the
current 1B artifact, which comes from a gemma-licensed, gated repo. If one of these is
adopted, `model/MODEL_ATTRIBUTION.md` §4's "requires legal review before distribution"
constraint relaxes considerably.

Each repo also ships device-specific variants (`_qualcomm_sm8750`, `_Google_Tensor_G5`,
`-gpu`, `-web`). **Use the plain `gemma-4-E2B-it.litertlm`** — the S20 FE is SM8250, which
has no matching variant, and the GPU build is useless here (§3).

## 2. Feasibility on THIS handset — read before downloading 2.4 GB

Measured baseline: the 1B model's **557 MiB file → 1.06 GiB resident** (×1.95) and
**~10 s per request** on `Backend.CPU(4)`. Device: **7.44 GiB total, 3.87 GiB available**.

| Candidate | File | Estimated resident | Verdict |
|---|---|---|---|
| Gemma 3 1B q4 (current) | 0.54 GiB | 1.06 GiB (measured) | comfortable |
| **Gemma 4 E2B** | 2.41 GiB | **2.9 – 4.7 GiB** | **fits, but tight — real OOM risk** |
| Gemma 4 E4B | 3.41 GiB | 3.9 – 6.7 GiB | **will not fit** |

**Two risks the owner should accept before proceeding:**

1. **Memory.** E2B's lower estimate (2.9 GiB) fits inside 3.87 GiB available; the upper
   estimate (4.7 GiB) does not. Which applies depends on whether LiteRT-LM mmaps the
   weights or copies them, and on what else the phone is holding. **This can only be
   settled by loading it** — 10A's `a03` test answers it in about two minutes.
2. **Latency.** If throughput scales roughly with active parameters, expect **~25–40 s per
   request** versus today's 10 s. The GPU backend cannot rescue this: Adreno 650 exposes
   no OpenCL library to apps (10A benchmark), so CPU is the only option on this handset.
   A 30-second wait may be worse for an investor demo than 67 % accuracy at 10 s — that is
   a product judgement, and it should be made with these numbers in hand.

**Recommendation:** try **E2B** first. If `a03` OOMs or load exceeds ~60 s, that is a hard
device limit and gets documented as such rather than worked around.

## 3. The swap — four steps

### Step 1 — provision the artifact

```bash
# Download gemma-4-E2B-it.litertlm from
#   https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm
# into model/  (ungated: no HF login required)
```

### Step 2 — compute the pinned identity

```bash
python - <<'PY'
import hashlib, os
p = "model/gemma-4-E2B-it.litertlm"          # <-- the new file
h = hashlib.sha256()
with open(p, "rb") as f:
    for chunk in iter(lambda: f.read(1 << 20), b""):
        h.update(chunk)
print("MODEL_FILE_NAME  =", os.path.basename(p))
print("EXPECTED_SHA256  =", h.hexdigest())
print("EXPECTED_BYTES   =", os.path.getsize(p))
PY
```

### Step 3 — update the ONE pinned triple

`app/src/main/java/com/thraksha/guardian/ai/LocalModelRepository.kt` — three constants,
nothing else in the codebase names a model:

```kotlin
const val MODEL_FILE_NAME  = "gemma-4-E2B-it.litertlm"
const val EXPECTED_SHA256  = "<from step 2>"
const val EXPECTED_BYTES   = <from step 2>L
```

The integrity gate itself is **not** relaxed: size → container magic → SHA-256, in that
order, before `Engine` is ever constructed. A wrong file still fails.

### Step 4 — push and re-run the gates

```bash
adb -s <serial> push model/gemma-4-E2B-it.litertlm \
    /sdcard/Android/data/com.thraksha.guardian/files/models/
adb -s <serial> shell rm \
    /sdcard/Android/data/com.thraksha.guardian/files/models/Gemma3-1B-IT_multi-prefill-seq_q4_ekv4096.litertlm

# 10A gate (~10 min) — load, constrained JSON, stability, backends
ANDROID_SERIAL=<serial> ./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.thraksha.guardian.Phase10ARuntimeInstrumentedTest

# 10B gate (~15 min) — the same 100 cases, unchanged
ANDROID_SERIAL=<serial> ./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.thraksha.guardian.Phase10BEvaluationInstrumentedTest
adb -s <serial> pull /sdcard/Android/data/com.thraksha.guardian/files/phase10b_eval_run.json
```

## 4. What must NOT change during the swap

The whole point of the Phase 10 architecture is that the model is the only variable:

* the **prompt and schema** stay as tuned — re-tuning for a new model is a fresh,
  separately logged exercise, not part of the swap;
* the **evaluation set** stays byte-identical, or the comparison is meaningless;
* `RequestScopeGuard` stays — safety must not become model-dependent again;
* `AutomationSafetyPolicy`, `AutomationPlanner`, `AutomationEngine` remain untouched;
* the SHA gate stays mandatory.

## 5. Expected outcome

If E2B clears **≥ 90 % overall and ≥ 90 % routine** with unsafe acceptance still 0, the 10B
gate passes and 10C proceeds automatically: ASK THRAKSHA UI (already written and
compiling), offline radios-off proof, failure/lifecycle matrix, 20–30 request stress run,
and full Phase 1–9 regressions.

If it OOMs, is unacceptably slow, or still misses the target, that is recorded as a device
or model limit — and the decision returns to the owner with better data. No gate is
declared passed on partial evidence.
