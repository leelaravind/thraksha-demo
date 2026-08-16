# MODEL ATTRIBUTION & PROVENANCE

> **UPDATE 2026-08-15 — the shipped model changed.** After Gemma 3 1B IT q4 failed the
> Phase 10B accuracy gate, the owner approved a larger model. The **currently pinned
> artifact is `gemma-4-E2B-it.litertlm`** (§9). Sections 1–8 describe the original 1B
> artifact and are retained as the historical record; §9 covers what actually ships.

**Recorded:** 2026-08-15
**Guide:** `temp/THRAKSHA_DEMO_IMPLEMENTATION_PHASE_10_PREP.md` §5 (authoritative)

This file records **what we actually know** about the on-device model. Where something
was not verifiable at the time of writing it is marked
**REQUIRES CONFIRMATION** rather than filled in with an assumption. No license text is
reproduced or paraphrased here, and no permission is asserted on Google's behalf.

---

## 1. Model identity

| Field | Value |
|---|---|
| Model family | Gemma 3 |
| Variant | **Gemma 3 1B IT** (1B parameters, instruction-tuned) |
| Publisher of the base model | Google |
| Artifact filename | `Gemma3-1B-IT_multi-prefill-seq_q4_ekv4096.litertlm` |
| Artifact format | LiteRT-LM container v1.0.0 (`model_type = TF_LITE_PREFILL_DECODE`, `author = The ODML Authors`) |
| Quantisation | 4-bit weights (`q4` per filename convention) |
| KV cache / context budget | 4096 tokens (`ekv4096` per filename convention) |
| Size | 584 417 280 bytes (557.34 MiB) |
| SHA-256 | `1325ae366d31950f137c9c357b9fa89448b176d76998180c08ceaca78bba98be` |
| Runtime target | **LiteRT-LM** (`com.google.ai.edge.litertlm:litertlm-android`) |

Full integrity evidence: `temp/PHASE10_MODEL_INTEGRITY.md`.

## 2. Source

| Field | Value |
|---|---|
| Source repository (as recorded by the acquirer in `model/README.md`) | **`litert-community/Gemma3-1B-IT`** on Hugging Face — <https://huggingface.co/litert-community/Gemma3-1B-IT> |
| Access model of that repository (checked 2026-08-15) | **Gated.** Publicly listed, but the files are released only after the user reviews and agrees to the Gemma license and is signed in. |
| License label shown on that repository | **`gemma`** |
| Date acquired onto this workstation | file mtime **2026-08-15T00:23:52Z**; recorded by the acquirer as part of Phase 10 prep |
| Acquired by | project owner (developer workstation), not by an automated pipeline |

### Upstream file identity — CONFIRMED 2026-08-15

An earlier check (rendered model card only) did not show this filename and the item was
recorded as REQUIRES CONFIRMATION. Re-checked against the Hugging Face **API** file
listing for `litert-community/Gemma3-1B-IT`, which returns the complete repository tree:

```
Gemma3-1B-IT_multi-prefill-seq_q4_ekv4096.litertlm   ← our artifact, present
gemma3-1b-it-int4.litertlm
Gemma3-1B-IT_q4_ekv1280_sm8550.litertlm  … (device-specific variants)
+ 20 further .task variants
```

**The exact filename is published by that repository.** The rendered card had simply
surfaced a different variant. Repository metadata at the same check: `gated: auto`,
`license: gemma`.

This closes open item #1 in §8. The specific *revision* the file was pulled from is still
not pinned (Hugging Face does not expose that from the file alone), but the artifact's
identity and origin are no longer in doubt, and its container metadata independently
matches (`LITERTLM` v1.0.0, `author = The ODML Authors`).

## 3. Applicable terms (reference only — not reproduced, not interpreted)

* **Gemma Terms of Use** — <https://ai.google.dev/gemma/terms>
* **Gemma Prohibited Use Policy** — <https://ai.google.dev/gemma/prohibited_use_policy>
* The Hugging Face repository labels the model `gemma` and gates download behind
  acceptance of that license.

Distinct from the weights, the **runtime library** is separately licensed:

* `com.google.ai.edge.litertlm:litertlm-android` — POM declares
  **Apache License 2.0** (<http://www.apache.org/licenses/LICENSE-2.0.txt>), developer
  "The LiteRT-LM Authors", project <https://github.com/google-ai-edge/LiteRT-LM>.
  The AAR ships `LICENSE` and a 2 MB `THIRD_PARTY_NOTICE.txt` that must be surfaced in
  the app's open-source notices when the dependency is added.

**No license terms are invented, summarised as binding, or waived in this document.**

## 4. Redistribution constraints — REQUIRES LEGAL REVIEW BEFORE ANY DISTRIBUTION

Established facts that bear on redistribution:

1. The upstream repository is **gated behind acceptance of the Gemma license**, which is
   itself evidence that redistribution is not unconditional.
2. The Gemma Terms of Use impose obligations on downstream distribution of the model and
   its derivatives (including use restrictions that must be passed on). **The exact
   obligations are not restated here — read the linked terms.**
3. Consequently, for this project:
   * the weights are **gitignored and never committed** (`/model/*.litertlm`);
   * the weights are **not bundled into a distributed APK** in this prep phase
     (see `temp/PHASE10_MODEL_DELIVERY.md` — developer provisioning was selected, and
     avoiding redistribution is one of the reasons);
   * the weights are **developer-provisioned onto demo hardware only**.

**Any packaging of the model into a shipped/distributed artifact — Play Store, sideload
bundle, OTA, investor hand-out device — must be reviewed against the Gemma Terms of Use
first. This document does not clear that.**

## 5. Purpose and scope of use in Thraksha

| Field | Value |
|---|---|
| Purpose | **Local natural-language → structured `AutomationIntent` interpretation only** |
| Phase | Phase 10 (preparation recorded here; inference not yet integrated) |
| Inference location | **Fully on-device.** No cloud inference, no API fallback, no telemetry. |
| Network requirement at runtime | **None.** Must work with Wi-Fi and mobile data OFF (`temp/PHASE10_OFFLINE_VERIFICATION.md`) |
| Data leaving the device | **None** — user text is processed locally and is not transmitted |
| **Explicitly NOT used for** | malware classification, threat-intelligence verdicts, scanner findings, enforcement decisions, or any security verdict. Security stays deterministic and signed-evidence driven (guide §24) |
| Authority granted to the model | **None.** It emits a candidate `AutomationIntent` only; `AutomationSafetyPolicy` → `AutomationPlanner` → user preview → user START → Phase 9 engine all sit between it and any device change (`temp/PHASE10_AUTOMATION_INTENT_CONTRACT.md`) |

## 6. Demo-only caveat

This is a **private investor-demo configuration**:

* the model is provisioned by the developer to specific demo hardware;
* no Play Store distribution eligibility is claimed or assessed;
* no fine-tuning, adaptation or derivative model is produced — the artifact is used
  **as-is, read-only**;
* the evaluation set in `temp/PHASE10_INTENT_EVAL.json` is **evaluation data only** and
  is explicitly not used for training or fine-tuning.

## 7. Attribution statement to surface in-app / in materials

> Gemma is provided by Google. This application runs **Gemma 3 1B IT** locally on the
> device via **LiteRT-LM** (Apache License 2.0). Use of the Gemma model is subject to the
> Gemma Terms of Use (<https://ai.google.dev/gemma/terms>) and the Gemma Prohibited Use
> Policy. The model is used solely to interpret the user's own spoken/typed automation
> request on-device; no user text leaves the device.

## 8. Open items

| # | Item | Owner |
|---|---|---|
| 1 | ~~Confirm the exact upstream advertised filename~~ — **CLOSED 2026-08-15**, see §2. Pinning the exact upstream *revision* remains optional and low-value. | acquirer |
| 2 | Legal review of Gemma Terms obligations **before** any distribution beyond developer-provisioned demo hardware | project owner |
| 3 | Add the LiteRT-LM `LICENSE` + `THIRD_PARTY_NOTICE.txt` to the app's OSS notices when the dependency is introduced | Phase 10 implementation |


---

## 9. CURRENTLY PINNED ARTIFACT — Gemma 4 E2B

| Field | Value |
|---|---|
| Model | **Gemma 4 E2B IT** (effective-2B, instruction-tuned) |
| Artifact | `gemma-4-E2B-it.litertlm` |
| Size | **2 588 147 712 bytes** (2.41 GiB) |
| SHA-256 | `181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c` |
| Container | LiteRT-LM **v1.5.0**, metadata `tf_lite_prefill_decode`, `tf_lite_mtp_drafter`, `tf_lite_vision_adapter` |
| Source repository | **`litert-community/gemma-4-E2B-it-litert-lm`** — <https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm> |
| Access model (checked via the HF API, 2026-08-15) | **`gated: false`** — no login or license acceptance required to download |
| License label on the repository | **`apache-2.0`** |
| Date acquired | 2026-08-15, downloaded directly to the developer workstation |
| Selected because | best accuracy per unit latency of three measured artifacts — see `temp/automation injection docs/10a_runtime/PHASE10A_MODEL_COMPARISON.md` |

### Why this materially improves the licensing posture

The original 1B artifact came from a **gated, `gemma`-licensed** repository, which is why
§4 marks redistribution as requiring legal review. This artifact is published **ungated
under an `apache-2.0` label**.

**That is a repository label, not a legal opinion.** Gemma-family models have historically
carried the Gemma Terms of Use, and an `apache-2.0` tag on a model repo does not by itself
establish that the *weights* are Apache-licensed. **The §4 constraint therefore still
stands until reviewed**: do not bundle the weights into any distributed artifact without
checking the actual terms attached to this specific repository. What has changed is that
the review is now likely to be *easier*, not that it has been done.

### Unchanged by the swap

Purpose (§5), demo-only caveat (§6) and the model's total lack of authority over Android
are all unchanged — the model still only proposes an `AutomationIntent`, behind
`RequestScopeGuard`, schema validation, `AutomationSafetyPolicy`, the deterministic planner,
a rendered preview and an explicit user START.

### Attribution statement for this artifact

> This application runs **Gemma 4 E2B IT** locally on the device via **LiteRT-LM**
> (Apache License 2.0). The model is used solely to interpret the user's own typed
> automation request on-device; no user text leaves the device. Model weights are obtained
> from the `litert-community` distribution and are subject to the terms attached to that
> distribution.
