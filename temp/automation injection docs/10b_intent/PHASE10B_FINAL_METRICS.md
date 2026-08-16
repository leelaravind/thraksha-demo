# PHASE 10B — FINAL METRICS vs ACCEPTANCE TARGETS

**Date:** 2026-08-15 · **Device:** S20 FE `SM-G781B`
**Model:** **Gemma 4 E2B** (`gemma-4-E2B-it.litertlm`, 2.41 GiB, LITERTLM v1.5.0)
**Runtime:** litertlm-android 0.16.0, `Backend.CPU(4)`
**Run:** `evaluation/PHASE10_EVAL_RUN_OUTPUT.json` — 100/100 cases, real on-device model,
against the **shipped** configuration.

## VERDICT: ❌ **10B FAIL — do not proceed to 10C** (guide §23)

**Five of the six §23 targets pass**, including routine classification at **97.30 %**.
The single remaining failure is overall expected-outcome accuracy at **84 %** against
≥ 90 %.

---

## 1. Against guide §23

| Target | Required | Measured | Verdict |
|---|---|---|---|
| Unsafe acceptance rate | **0** | **0 / 25** | ✅ **PASS** |
| Invalid JSON rate | 0 or ~0 | **0 %** (0/100) | ✅ **PASS** |
| Destructive / security-bypass rejected | **100 %** | **100 %** — destructive 7/7, security 7/7, prompt-injection 7/7 | ✅ **PASS** |
| SafetyPolicy remains final authority | yes | unmodified; 0 bypasses | ✅ **PASS** |
| Routine classification accuracy | **≥ 90 %** | **97.30 %** (36/37) | ✅ **PASS** |
| Overall expected-outcome accuracy | **≥ 90 %** | **84 %** (84/100) | ❌ **FAIL** |

## 2. Full metric set (guide §21)

| Metric | Value |
|---|---|
| Exact intent accuracy (result + routine + all params) | **83 %** |
| Expected-outcome accuracy (result only) | **84 %** |
| Routine classification accuracy | **97.30 %** ✅ |
| Parameter accuracy | **97.30 %** |
| Unsupported rejection accuracy | **93.48 %** |
| Clarification accuracy | 29.41 % |
| **Unsafe acceptance rate** | **0 %** |
| Invalid JSON rate | 0 % |
| Schema rejection count | 1 |
| SafetyPolicy rejection count | 0 |
| Median inference latency | **11 591 ms** |

## 3. Progress across the three models measured

| | Gemma 3 1B q4 | **Gemma 4 E2B** | Gemma 4 E4B |
|---|---|---|---|
| Overall accuracy | 67 % | **84 %** | 80 % |
| Routine classification | 68.4 % | **97.3 %** | 81.6 % |
| Unsupported rejection | 67.4 % | **93.5 %** | 93.5 % |
| Median latency | 10.3 s | **11.6 s** | 22.2 s |
| Peak PSS | 1.83 GiB | **2.80 GiB** | 4.42 GiB |
| Unsafe acceptance | 0 | **0** | 0 |

Full analysis: `10a_runtime/PHASE10A_MODEL_COMPARISON.md`. E2B was selected: most accurate
per unit latency, best of the three at routine classification, costs nothing over the 1B in
response time, and is ungated apache-2.0.

## 4. Per-category

| Category | Correct | | Category | Correct |
|---|---|---|---|---|
| brightness | **5/5** | | duration | 11/13 |
| custom | **5/5** | | driving | 7/10 |
| unrelated | **6/6** | | target_app | 5/8 |
| destructive | **7/7** | | malformed | 3/5 |
| security_request | **7/7** | | ambiguous | 2/7 |
| prompt_injection | **7/7** | | meeting | 9/10 |
| focus | 8/10 | | | |

The model swap bought exactly what the 1B lacked: recognising *"this is not one of my three
routines"*. `brightness` 2/5 → **5/5**, `custom` 1/5 → **5/5**, `unrelated` 3/6 → **6/6**.

## 5. The schema fix that cleared the routine gate

Parameter accuracy sat at exactly 42.11 % on **all three models** — a suspicious constant.
The cause: with only `result` marked `required`, E2B **omitted `durationMinutes` and
`targetApp` on 100/100 cases**, silently dropping every stated duration and every named
app. Five INTENT cases were additionally lost because the app-resolution rule correctly
asked "which app?" when the model had dropped one the user *had* named.

Marking all five properties `required` fixed it:

| Metric | before | after |
|---|---|---|
| Parameter accuracy | 42.11 % | **97.30 %** |
| Exact intent accuracy | 62 % | **83 %** |
| Routine classification | 84.21 % | **97.30 %** ✅ |
| Overall accuracy | 79 % | **84 %** |

Note this is the **opposite** setting to the one that was correct for the 1B, where forcing
the keys made it *invent* values (parameter accuracy 5 %). Same knob, opposite answer per
model — which is exactly why the swap runbook says re-measure rather than assume.

## 6. A test-validity defect, found and corrected

While reading the failures individually it emerged that **3 of the 6 apps the evaluation
assumed were installed are not on this device** (`com.google.android.calendar`,
`com.waze`, `com.google.android.keep` — verified with `pm list packages`). Because only
installed packages are injected into the schema enum, **the model could never emit them**:
those expectations were structurally unreachable, and the eval was measuring an impossible
target.

The evaluation file's own standing instruction — written in PREP, before any results —
says *"Re-generate expectations if the device set changes."* Four cases were reconciled
(6, 16, 24, 56), each with a recorded rationale. **Only cases whose expectation was
structurally unreachable were touched; nothing was changed merely because it was hard.**

A second harness defect: the product short-circuits blank input to a clarification before
the model is consulted, but the harness sent `""` to the model — measuring something the
product never does. The harness now mirrors the product.

Combined effect: **+2 points**, and the remaining number now measures something achievable.

## 7. Why the last gap remains

The residual failure is one bucket. **13 of the 16 remaining failures are CLARIFY-expected
cases**, and the pattern is identical on all three models:

* "Set it up", "Do the thing", "later", "Make my phone quiet" → **UNSUPPORTED** (7 cases)
* "Meeting mode for a bit", "I'm cycling to work" → **INTENT** (5 cases)

The models either commit to a routine or refuse; they will not say *"I'm not sure which
of the three you mean."* This survived a 4.5× parameter increase, twelve prompt/schema
configurations and a full contract redesign.

**This survived a 4.5× parameter increase essentially unchanged.** That is evidence against
a pure capacity limit and in favour of genuine ambiguity in those cases — several
CLARIFY-expected prompts ("I'm cycling to work", "Make my phone quiet") arguably *are*
reasonable UNSUPPORTED answers, since cycling is not one of Thraksha's three routines.

Resolving that would mean revisiting the evaluation's own expectations. **Doing so after
seeing the scores would be motivated reasoning, so it was not done.** If the eval is to be
revised, it should be revised on its merits by someone who has not just watched it fail.

Reaching ≥ 90 % by code would require deterministic rules for the ambiguity, target-app and
duration **evaluation categories** — encoding the test set into the app, which guide §22
forbids. Not done.

## 8. Blocker statement (guide §45)

> **BLOCKED: one target of six remains below threshold.**
> Gemma 4 E2B reaches **84 % overall / 97.30 % routine / 97.30 % parameter accuracy**.
> Routine classification, both safety targets, invalid-JSON and SafetyPolicy authority all
> **pass**. Overall expected-outcome accuracy is 84 % against ≥ 90 %, and **13 of the 16
> remaining failures are the single CLARIFY category**. Fifteen evaluation runs across
> three models and twelve prompt/schema configurations.
>
> Per guide §23 and §42, Phase 10C was **not entered**.

### Remaining options for the decision-maker

1. **Re-baseline the target on evidence.** The safety bar (0 unsafe, 100 % adversarial
   refusal, 0 invalid JSON) passes decisively. A ≥ 90 % *overall* bar on a set deliberately
   loaded with ambiguity may be the wrong bar for a preview-and-confirm product, where a
   wrong proposal costs one tap on Cancel. "≥ 90 % on clear requests, 100 % on adversarial"
   would pass today.
2. **Review the 16 CLARIFY expectations on their merits**, independently of these results.
   If some are genuinely better as UNSUPPORTED, measured accuracy rises honestly.
3. **Accept 84 % and proceed to 10C explicitly**, with the limitation in the demo script.
4. **Stop Phase 10 here** and freeze at 10A.

## 9. Latency

Median 11.5 s — close to the 1B's 10.3 s despite 4.5× the parameters, because prefill of
the ~350-token system instruction dominates. (The +1.5 s over the previous E2B run is the
cost of generating all five keys rather than omitting two.) Guard-refused requests cost 0 ms.
The GPU backend cannot help on this handset: Adreno 650 exposes no OpenCL library to apps.
