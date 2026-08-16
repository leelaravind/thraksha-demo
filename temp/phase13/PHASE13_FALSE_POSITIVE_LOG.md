# PHASE 13 — FALSE-POSITIVE REVIEW

Guide reference: §7. Build: frozen RC2. **No rule was modified and no allowlist was created.**

## Status: ⚠ PARTIAL — population identified, per-item analysis incomplete

Device A's scan surfaces **14 "needs attention" items** out of 441 packages:

* **1** = VillainCaller, the controlled decoy, KNOWN THREAT via ThreatPack indicator — a
  true positive by construction, not a candidate.
* **13** = REVIEW items on legitimate, owner-installed or system applications. These are the
  false-positive candidates.

Three were captured in detail before the device disconnected; the remaining ten are
identified as a population but not individually documented.

## Analysed

### FP-01 — Google TV (`com.google.android.videos`) — REVIEW

| Field | Value |
| --- | --- |
| Rule fired | generic capability-exposure rule (network + broad package visibility) |
| Declared | `INTERNET`, `QUERY_ALL_PACKAGES` |
| Granted | install-time, therefore granted |
| Observed | **none** — no runtime observation exists |
| Context | Google-signed system-adjacent media app |
| Understandable? | **Yes.** The wording is "This app is currently allowed to use network and package visibility" — a DECLARED/GRANTED statement, not an accusation. |
| Severity appropriate? | **Borderline.** `QUERY_ALL_PACKAGES` genuinely is broad exposure and Thraksha flags it consistently — including on itself. But a Google-signed store app holding it is unremarkable, and surfacing it competes for attention with real findings. |
| Verdict | **Benign-but-noisy true positive.** Not a false statement; arguably not worth the user's attention. |

### FP-02 — Samsung Notes (`com.samsung.android.app.notes`) — REVIEW

| Field | Value |
| --- | --- |
| Rule fired | generic capability-exposure (overlay + media/storage + 1 more) |
| Declared | draw-over-other-apps, media/storage, and one further sensitive capability |
| Granted | per-permission; overlay is user-grantable |
| Observed | **none** |
| Context | OEM-preinstalled note-taking app; overlay is plausible for its floating-note feature |
| Understandable? | **Yes** — capabilities are named explicitly. |
| Severity appropriate? | **Yes for exposure, no for urgency.** Draw-over-other-apps is a genuine overlay-attack primitive; it is also exactly what this app legitimately uses. |
| Verdict | **Benign true positive.** Correct to notice, over-weighted to surface as "needs attention". |

### FP-03 — Tips (`com.samsung.android.app.tips`) — REVIEW

| Field | Value |
| --- | --- |
| Rule fired | generic capability exposure |
| Declared / Granted | capability set as declared |
| Observed | **none** |
| Context | OEM help/tips app |
| Understandable? | Yes |
| Severity appropriate? | **Questionable** — low-risk OEM utility |
| Verdict | **Noise candidate.** |

## Not analysed — 10 remaining REVIEW items

⛔ Blocked: Device A dropped off USB before the remaining items could be opened and their
per-item evidence (rule, DECLARED/GRANTED/OBSERVED stages, context assessment) captured.
They are counted, not characterised.

## What this population already tells us

* **`High-exposure profiles: 0`.** No legitimate app was escalated beyond REVIEW on static
  capability alone — the Phase 8.1 recalibration is doing its job.
* **`Known-threat matches: 1`**, and it is the decoy. **Zero** legitimate apps were called a
  threat. The dangerous class of false positive — accusing a real app of being malware —
  **did not occur**.
* Every REVIEW statement is DECLARED/GRANTED phrasing. **No finding claimed OBSERVED
  behaviour**, correctly, since Network Guard was off and no runtime evidence existed.
* The recurring pattern is **breadth, not falsity**: findings are true but some are
  low-value. That is a calibration question, not a correctness bug.

## Honest limitation on calibration

13 REVIEW items from **one** app population on **one** device is not a calibration baseline.
A different phone with different installed apps could produce a materially different
false-positive rate, and guide §7 exists precisely to compare that across devices. Without
Device B, **the false-positive rate of RC2 is effectively uncharacterised.**

No package-specific allowlist was created (guide §7), and no rule was weakened to reduce the
count.
