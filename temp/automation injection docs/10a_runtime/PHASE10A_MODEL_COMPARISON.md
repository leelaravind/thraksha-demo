# PHASE 10 — MODEL COMPARISON (measured end-to-end on the S20 FE)

**Date:** 2026-08-15 · **Device:** `SM-G781B`, Android 13, Snapdragon 865, 7.44 GiB RAM
**Runtime:** `com.google.ai.edge.litertlm:litertlm-android:0.16.0`, `Backend.CPU(4)`
**Trigger:** the owner's decision, after Gemma 3 1B q4 failed the 10B accuracy gate, to
*"provide a larger model"*. Three artifacts were provisioned and put through the **same**
10A gate and the **same** unchanged 100-case evaluation.

---

## 1. The three artifacts

| | Gemma 3 1B IT q4 | **Gemma 4 E2B** | Gemma 4 E4B |
|---|---|---|---|
| Repo | `litert-community/Gemma3-1B-IT` | `litert-community/gemma-4-E2B-it-litert-lm` | `litert-community/gemma-4-E4B-it-litert-lm` |
| File | `…q4_ekv4096.litertlm` | `gemma-4-E2B-it.litertlm` | `gemma-4-E4B-it.litertlm` |
| Size | 0.54 GiB | **2.41 GiB** | 3.41 GiB |
| Container | LITERTLM v1.0.0 | LITERTLM v1.5.0 | LITERTLM v1.5.0 |
| Gated | **yes** (gemma license) | **no** (apache-2.0) | **no** (apache-2.0) |
| SHA-256 | `1325ae36…8bba98be` | `18193810…7139a63c` | `0b2a8980…45bd52e0` |

## 2. Runtime cost (10A gate — all three PASSED 8/8)

| Metric | 1B q4 | **E2B** | E4B |
|---|---|---|---|
| SHA-256 verify | 0.84 s | 3.19 s | 6.40 s |
| Load (cold) | 4.19 s | 6.63 s | **22.03 s** |
| Reload (warm) | 1.52 s | 5.20 s | 7.94 s |
| PSS after load | 1.24 GiB | 1.39 GiB | 2.88 GiB |
| **PSS peak during inference** | 1.83 GiB | **2.80 GiB** | **4.42 GiB** |
| Released on unload | 1.29 GiB | 1.68 GiB | 2.78 GiB |
| **Median inference latency** | 10.3 s | **10.1 s** | **22.2 s** |
| p95 latency | 11.1 s | 10.3 s | 22.6 s |
| Smoke set (18 prompts) | 18/18 conformant | 18/18 | 18/18 |
| Invalid JSON | 0 | 0 | 0 |
| 12-run stability | +3 MB PSS | +2.5 MB | −2.6 MB |

**The memory prediction was wrong, and usefully so.** From the 1B's file→resident ratio
(×1.95) I forecast E2B at 2.9–4.7 GiB and declared E4B infeasible. Actual ratios are far
lower (≈1.2×): E2B peaks at 2.80 GiB and **E4B loads fine at 4.42 GiB**. Loading it was
the only way to know — the estimate would have wrongly excluded a viable option.

## 3. Accuracy (10B — identical 100-case evaluation, identical prompt/schema)

| Metric | 1B q4 | **E2B** | E4B |
|---|---|---|---|
| **Overall expected-outcome accuracy** | 67 % | **79 %** | 80 % |
| **Routine classification accuracy** | 68.4 % | **84.2 %** | 81.6 % |
| Exact intent accuracy | 48 % | 62 % | 63 % |
| Parameter accuracy | 42.1 % | 42.1 % | 42.1 % |
| Unsupported rejection accuracy | 67.4 % | **93.5 %** | 93.5 % |
| Clarification accuracy | 25 % | 25 % | 37.5 % |
| **Unsafe acceptance** | **0** | **0** | **0** |
| **Invalid JSON** | **0** | **0** | **0** |
| destructive / security / injection | 7/7 · 7/7 · 7/7 | 7/7 · 7/7 · 7/7 | 7/7 · 7/7 · **6/7** |

Per-category, 1B → E2B: `brightness` 2/5 → **5/5**, `custom` 1/5 → **5/5**,
`unrelated` 3/6 → **6/6**, `malformed` 1/5 → 3/5. The step up mostly bought the ability
to recognise *"this is not one of my three routines"*, which is exactly where the 1B was
weakest.

## 4. Selection: **Gemma 4 E2B**

| Criterion | Why E2B wins |
|---|---|
| Accuracy per second | 79 % at 10.1 s vs E4B's 80 % at 22.2 s — **+1 point for 2.2× the wait** |
| Routine classification | **84.2 %**, the best of the three — E4B is *worse* at 81.6 % |
| Latency | 10.1 s, indistinguishable from the 1B; the demo does not get slower |
| Memory | 2.80 GiB peak on a 7.44 GiB device — comfortable, vs E4B's 4.42 GiB |
| Load time | 6.6 s vs E4B's 22.0 s — matters for first-use in a live demo |
| Licensing | ungated **apache-2.0**, a materially better posture than the gated gemma-licensed 1B |
| Safety | unsafe acceptance 0 and a perfect 7/7/7 on the adversarial categories — E4B dropped one prompt-injection case |

E4B is not merely poor value here — on the two things that matter most for this product
(routine classification and adversarial refusal) it is **measurably worse than E2B** while
costing twice the time and 1.6 GiB more RAM.

## 5. What this did and did not fix

**Fixed:** the 1B's inability to decline non-routine requests. Unsupported rejection went
67 % → 93.5 %, and three whole categories went to full marks.

**Not fixed:** the 10B accuracy gate. E2B reaches **79 % overall / 84.2 % routine** against
a **≥ 90 %** target. The residual failure is concentrated in two places, and it is the
*same shape* on all three models:

* **CLARIFY (4/16)** — 8 of the 16 became UNSUPPORTED on every model tried. The models
  treat "vague but routine-ish" as "not an automation request".
* **target_app (4/8)** and **ambiguous (2/7)**.

Because the failure survives a 4.5× parameter increase unchanged, it is unlikely to be a
pure capacity limit; it more plausibly reflects genuine ambiguity in those cases. Resolving
that would mean revisiting the evaluation's own expectations — which is **not** something
to do after seeing the scores, so it was not done.

## 6. Provenance note

Both Gemma 4 artifacts come from **ungated, apache-2.0-labelled** `litert-community`
repositories, unlike the gated gemma-licensed 1B. If E2B is adopted permanently,
`model/MODEL_ATTRIBUTION.md` §4's redistribution constraint relaxes considerably — worth
re-running the legal review with that in mind.

⚠️ `litert-community/Gemma3-4B-IT` was **rejected as unusable**: it publishes only `.task`
files (MediaPipe format), which this runtime cannot load and which would correctly fail the
container-magic gate.
