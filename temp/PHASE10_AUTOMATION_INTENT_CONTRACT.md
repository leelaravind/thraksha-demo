# PHASE 10 PREP — AUTOMATION INTENT CONTRACT (FROZEN)

**Date:** 2026-08-15
**Guide:** `temp/THRAKSHA_DEMO_IMPLEMENTATION_PHASE_10_PREP.md` §11, §12, §21, §22
(authoritative)
**Status:** frozen for Phase 10. Widening any rule below requires re-opening this
document — it is not an implementation detail.

---

## 1. Principle

The model's **entire authority** is to propose a value of the Phase 9 `AutomationIntent`
data class. It proposes; it never decides, and it never acts.

```
user text → Gemma (local) → JSON → schema validation → AutomationIntent
          → AutomationSafetyPolicy → AutomationPlanner → PLAN PREVIEW
          → user presses START → Phase 9 AutomationEngine → executors
          → verify → restore → audit
```

Every arrow after "JSON" is deterministic Phase 9 code that already exists and is
already tested. **Phase 10 adds one new arrow at the front and nothing else.**

## 2. Phase 9 `AutomationIntent` — preserved unchanged

From `app/src/main/java/com/thraksha/guardian/automation/AutomationModels.kt`:

```kotlin
data class AutomationIntent(
    val routineType: RoutineType,                       // MEETING | FOCUS | DRIVING | CUSTOM
    val durationMinutes: Int? = null,                   // null = until stopped
    val targetApp: String? = null,                      // validated package name
    val customActions: List<ActionTarget> = emptyList(),// CUSTOM only
)
```

**This class is NOT modified by Phase 10.** No field is added, removed, renamed or
re-typed. The existing comment on it — *"The only input surface for requesting
automation — what Phase 10's model will be allowed to produce"* — is honoured literally.

## 3. What the model MAY populate — the frozen field table

| Field | Model may set? | Allowed values | Enforced by |
|---|---|---|---|
| `routineType` | **YES** | `MEETING`, `FOCUS`, `DRIVING` | JSON-schema enum (constrained decoding) + Kotlin enum parse |
| `routineType = CUSTOM` | **NO — forbidden in Phase 10** | — | app rejects any output carrying CUSTOM (see §4) |
| `durationMinutes` | **YES** | integer `1…480`, or `null` (= until stopped) | schema `minimum`/`maximum` + `AutomationSafetyPolicy.validateIntent` |
| `targetApp` | **YES, but only from a runtime-generated enum** | one of the packages the app injected into the schema — i.e. genuinely installed + launchable candidates | schema `enum` + `AutomationSafetyPolicy` shape/forbidden check + Phase 9 `probe.launchSupport` |
| `customActions` | **NO** | must be absent/empty; the app hard-forces `emptyList()` | app constructs the intent; schema has no such property |

Everything the routine actually does — which capabilities, which values, required vs
optional, execution order — remains **hardcoded in the deterministic Phase 9 planner
templates**. The model cannot influence brightness values, DND filter levels, ringer
modes, screen timeouts, or action ordering, because those are not in its schema at all.

### Why `CUSTOM` is excluded (and how it could return later)

`CUSTOM` is the one routine type whose actions come from the *intent* rather than a
planner template. Letting a 1B model author `customActions` would hand it the choice of
capability **and** value — a materially wider surface than choosing among three
pre-designed routines, for no demo benefit. The Phase 9 custom-routine button remains
available to the user directly.

If it is ever wanted from natural language, it returns only as an explicit, separately
reviewed extension: a closed enum of pre-approved action *presets* (not free
capability/value pairs). That is a future decision, not an implicit one.

### Why `targetApp` is an injected enum, not a free string

The app enumerates installed, launchable candidate packages at request time and injects
them into the JSON schema as an `enum`. With LiteRT-LM constrained decoding
(`ResponseFormat.json`), the model is then **structurally unable** to emit a package name
that is not installed — no hallucinated `com.evil.app`, no typo'd package, no
non-existent target. Belt and braces: the Kotlin side still re-checks shape, the
forbidden-target list, and launchability via the Phase 9 probe.

## 4. What the model may NEVER produce

Structurally impossible — these have **no representation in the schema**, so constrained
decoding cannot emit them and the parser has nowhere to put them:

* shell commands, command lines, or any executable string;
* arbitrary Android `Intent` actions, component names, or **URIs of any kind**;
* executor class/method names (`DndExecutor`, `apply`, `restore`, …);
* raw Android API calls (`DevicePolicyManager`, `Settings.System`, `AudioManager`, …);
* file paths, file contents, or storage operations;
* network requests, hosts, URLs, or endpoints;
* permission-grant or special-access commands;
* package suspension / restriction / uninstall requests;
* changes to `PolicyEngine`, Network Guard, the scanner, ThreatPack, Rulepack or the
  execution mode;
* audit-log rows, or any text that lands in the audit chain unfiltered;
* security verdicts about apps (guide §24 — the model is **never** consulted about
  malware, threat intel, scanner findings or enforcement);
* free-form prose shown to the user (see §5);
* a request to skip the preview or to auto-start a routine.

If a user asks for any of these, the correct model output is a structured
`UNSUPPORTED` (§5), never an invented action.

## 5. Output envelope — no model-authored text ever reaches the user

The model emits exactly one JSON object of this shape:

```jsonc
{
  "result": "INTENT" | "CLARIFY" | "UNSUPPORTED",   // required
  "routine": "MEETING" | "FOCUS" | "DRIVING" | null,
  "durationMinutes": 1..480 | null,
  "targetApp": "<one of the injected enum values>" | null,
  "reasonCode": "<enum, see below>" | null
}
```

`reasonCode` is a **closed enum**, never a sentence:

| Applies to | Codes |
|---|---|
| `CLARIFY` | `AMBIGUOUS_ROUTINE`, `MISSING_TARGET_APP`, `AMBIGUOUS_DURATION`, `MULTIPLE_ROUTINES_REQUESTED` |
| `UNSUPPORTED` | `OUT_OF_SCOPE`, `SECURITY_REQUEST`, `DESTRUCTIVE_REQUEST`, `UNSAFE_VALUE`, `NOT_AN_AUTOMATION_REQUEST` |

**Rule: the model produces no natural-language output at all.** Every string the user
sees is authored by Thraksha from the *validated* intent and the *deterministic* plan
(guide §23). This kills three risks at once: hallucinated descriptions of what Android
will do, prompt-injection text being echoed into the UI, and the model appearing to speak
with the app's authority.

Example of the resulting UI, entirely app-generated:

> **THRAKSHA UNDERSTOOD:** Meeting Mode — 45 min
> Planned actions: *(from `AutomationPlanner`, not from the model)*
> • Set ringer to vibrate — supported
> • Dim screen brightness — supported
> • Enable Do Not Disturb (priority only) — supported (required)
> • Open calendar/meeting app (calendar) — supported
> **[ Cancel ]  [ START ROUTINE ]**

## 6. Mapping model output → `AutomationIntent`

Only `result == "INTENT"` produces an intent. The construction is total and explicit:

```kotlin
AutomationIntent(
    routineType    = RoutineType.valueOf(json.routine),   // MEETING|FOCUS|DRIVING only
    durationMinutes = json.durationMinutes,               // null ⇒ until stopped
    targetApp      = json.targetApp,                      // null ⇒ planner picks its candidate
    customActions  = emptyList(),                         // ALWAYS empty in Phase 10
)
```

`CLARIFY` → the app shows a fixed question for that `reasonCode` and asks again. **No
intent is built, no plan is generated, nothing is executed.**
`UNSUPPORTED` → the app shows a fixed explanation for that `reasonCode` and points the
user at the deterministic routine buttons. **Nothing is executed.**

### Defaults are the app's, not the model's (guide §14)

The model must **not** guess high-risk missing information. Where Phase 9 already has a
safe, visible default, the app applies it after validation:

| Situation | Behaviour |
|---|---|
| `durationMinutes` omitted for MEETING/FOCUS | app applies the Phase 9 UI default (Meeting 30, Focus 60) and **shows it in the preview** |
| `durationMinutes` omitted for DRIVING | until stopped — Phase 9's Driving default |
| `targetApp` omitted | planner's existing candidate logic; if nothing is installed the action is **omitted, never faked** (Phase 9 behaviour) |
| target app named but ambiguous/not installed | `CLARIFY / MISSING_TARGET_APP` — the app asks; it does not pick one |
| "silent forever", "all day", 9999 minutes | bounded to `1…480` by schema **and** policy; out-of-range ⇒ `UNSUPPORTED / UNSAFE_VALUE` |

## 7. Validation pipeline — five gates, all mandatory, none skippable

| # | Gate | Rejects |
|---|---|---|
| 1 | **Constrained decoding** (`ResponseFormat.json(schema)`) | anything not matching the schema, at token level |
| 2 | **Strict Kotlin parse** (kotlinx-serialization, `ignoreUnknownKeys = false`) | invalid JSON, prose around JSON, unknown keys, wrong types, missing `result` |
| 3 | **Contract check** (this document) | `CUSTOM`, non-empty `customActions`, `routine` null when `result == INTENT`, `reasonCode` outside its enum, `targetApp` not in the injected enum |
| 4 | **`AutomationSafetyPolicy.validateIntent`** — unchanged Phase 9 code | duration outside 1…480, malformed/forbidden package, custom actions on a non-custom routine |
| 5 | **`AutomationPlanner` + `CapabilityProbe`** — unchanged Phase 9 code | actions the device cannot currently perform → `USER ACTION REQUIRED` / `UNSUPPORTED`, plan blocked if a **required** action is unavailable |

Gate 2 is deliberately strict: **prose mixed with JSON is a rejection, not a parsing
challenge.** No regex scraping of paragraphs, no "find the first `{`", no repair passes.

**`AutomationSafetyPolicy` is not modified, relaxed, bypassed or special-cased for AI
input.** Model-originated intents traverse exactly the same policy object as
button-originated intents. There is no "the model was confident" path.

## 8. The trust boundary in one line

> The model chooses **which of three pre-designed routines to propose, for how long, and
> optionally which installed app to open**. Everything else — what each routine changes,
> whether the device allows it, what is snapshotted, what is verified, what is restored,
> and whether anything runs at all — is deterministic Phase 9 code plus an explicit human
> press of **START**.

## 9. No auto-execution (guide §22)

Inference completing is **not** an authorisation. The pipeline always halts at the plan
preview and waits for the user's START. There is no configuration flag, debug switch or
"confidence" threshold that skips it. `AutomationEngine.start()` is only ever called from
the same user-initiated code path Phase 9 already uses.

## 10. Explicitly out of scope for the model

* security/malware verdicts of any kind (guide §24);
* Phase 8 scanner behaviour, Phase 8.1 evidence or User ACT;
* Network Guard state;
* execution mode / privilege level;
* audit content;
* anything that is not one `AutomationIntent`.
