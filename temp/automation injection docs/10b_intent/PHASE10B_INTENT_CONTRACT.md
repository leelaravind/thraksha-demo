# PHASE 10B — INTENT CONTRACT (as implemented)

**Guide:** §15–§18 (authoritative). The frozen design lives in
`temp/PHASE10_AUTOMATION_INTENT_CONTRACT.md`; this document records **what the code
actually does**, file by file.

---

## 1. The pipeline, gate by gate

```
user text
  │
  ├─ Gate 0  RequestScopeGuard            deterministic, runs BEFORE the model
  │          └─ refuses forbidden domains outright → UNSUPPORTED
  │
  ├─ Gate 1  ResponseFormat.json(schema)  constrained decoding — only contract-shaped JSON
  │
  ├─ Gate 2  IntentJsonValidator (parse)  strict JSON, no prose, no unknown keys
  │
  ├─ Gate 3  IntentJsonValidator (contract) enums, ranges, injected app enum, CUSTOM ban
  │
  ├─ Gate 4  AutomationSafetyPolicy       ← UNMODIFIED Phase 9 code, final authority
  │
  └─ Gate 5  AutomationPlanner            ← UNMODIFIED Phase 9 code, owns what Android does
             │
             └─ PLAN PREVIEW → (human presses START) → Phase 9 AutomationEngine
```

No gate is skippable and none is conditional on model confidence — the model emits no
confidence, and nothing reads one.

## 2. Gate 0 — `RequestScopeGuard` (added in tuning iteration 4)

**Why it exists.** Iterations 1 and 3 measured that a 1B model asked to *both* classify a
routine *and* decide whether to refuse does the first well (~82 %) and the second badly —
it answered "Meeting Mode" to *"Turn off Network Guard"*. Iteration 2 fixed refusals by
making it timid and INTENT recall collapsed to 1/38.

The conclusion is the one Phase 9 was built on: **do not delegate a safety decision to
something you then have to trust.** Whether a request falls inside Thraksha's capability
set is a deterministic property of that set, so it is decided in ordinary code.

**Domain coverage** — one per bullet of the frozen contract §4: security/malware verdicts,
permissions and special access, destructive operations, shell/ADB/root idioms, URIs and
links, prompt-injection markers, and durations outside the 1–480 minute bound (including
negative and open-ended ones).

**Deliberately NOT covered:** merely out-of-scope requests such as "set brightness to 128".
Getting those wrong is a usability miss, not a safety failure, so they stay the model's
job and the guard stays narrow.

**Honest limitations, recorded rather than hidden:** this is vocabulary matching, not
semantic understanding. It will over-refuse a legitimate request containing a word like
"install", and a determined paraphrase can slip past it. Both are acceptable because
over-refusal fails toward the safe side, and anything that slips past still meets a schema
that cannot express a dangerous action, then `AutomationSafetyPolicy`, then a preview,
then an explicit START. It is defence in depth, never the only defence — and
`Phase10ScopeGuardTest` proves it on paraphrases that appear nowhere in the evaluation set.

## 3. Gates 1–3 — structure and contract

| Rule | Enforced by | Behaviour |
|---|---|---|
| Only 5 keys exist | schema `additionalProperties:false` + validator | unknown key → REJECTED |
| `result` ∈ {INTENT, CLARIFY, UNSUPPORTED} | schema enum + validator | else REJECTED |
| `routine` ∈ {MEETING, FOCUS, DRIVING} | schema enum + validator | `CUSTOM` → REJECTED ("not model-selectable") |
| `durationMinutes` integer or null | validator | `"45"`, `45.5`, `true` → REJECTED |
| `durationMinutes` ∈ 1..480 | **validator, not the schema** | out of range → UNSUPPORTED / UNSAFE_VALUE |
| `targetApp` ∈ injected enum | schema enum + validator | anything else → REJECTED |
| Prose around JSON | validator | REJECTED — never scraped, never repaired |

### Why `durationMinutes` is unbounded in the schema

Measured in iteration 1: numeric bounds inside a *constrained decoder* do not refuse an
out-of-range request, they **rewrite it into an allowed one** — "9 hours" was emitted as
`9`, "99999999 minutes" as `99`, "forever" as `480`. A safety bound expressed in the
sampler destroys the refusal signal before any validator can see it. The bound therefore
lives in validation (and again in `AutomationSafetyPolicy`), where a request can actually
be refused.

### Why a junk `reasonCode` on INTENT is ignored

The contract says `reasonCode` must be null for INTENT; the model often attaches a
leftover value anyway. It is **not read** on that branch. `reasonCode` carries zero
authority — it only selects which fixed sentence the UI shows for CLARIFY/UNSUPPORTED — so
ignoring it discards a meaningless field rather than relaxing a rule. Every field that can
affect the device is validated, twice.

### Why a refusal with a bad `reasonCode` is still honoured

Iteration 1 threw away 12 *correct* refusals because the label was missing. A decline is
the safe direction; discarding it because its label was wrong helped nobody. The decision
is honoured and a generic reason substituted. A refusal is **never** upgraded to an
intent — proven by `aRefusalIsNeverUpgradedToAnIntent_evenWithARoutineAttached`.

## 4. Gate 4 — `AutomationSafetyPolicy` is untouched

`git diff` over `AutomationSafetyPolicy.kt` for the whole of Phase 10: **no changes**.
Model-originated intents call the identical object as button-originated ones. A unit test
(`schemaBounds_matchTheUnmodifiedSafetyPolicy`) fails the build if the AI-side duration
bound ever drifts from the policy's.

## 5. Outcome vocabulary (guide §16)

| Outcome | Produced by | User sees | Device |
|---|---|---|---|
| **INTENT** | model, after all gates | THRAKSHA UNDERSTOOD + the planner's action list + START | nothing until START |
| **CLARIFY** | model, or empty input | one fixed question chosen by reasonCode | nothing |
| **UNSUPPORTED** | scope guard, or model, or out-of-range duration | one fixed explanation chosen by reasonCode | nothing |
| **REJECTED** | **Thraksha only** — never the model | `COULD NOT INTERPRET REQUEST` | nothing |

The model cannot emit REJECTED; it is what Thraksha concludes when output fails a gate.

## 6. What reaches the user

Nothing the model wrote. The schema has no free-text field, so there is no channel for
model prose to reach the screen — which also means a prompt-injection payload cannot be
echoed back with Thraksha's authority. Every sentence is app-authored from the validated
intent, and every planned action comes from `AutomationPlanner`, because the planner —
not the model — knows what Android will actually do (guide §18).
