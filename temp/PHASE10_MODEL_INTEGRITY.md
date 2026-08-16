# PHASE 10 PREP — MODEL INTEGRITY RECORD

**Date:** 2026-08-15
**Guide:** `temp/THRAKSHA_DEMO_IMPLEMENTATION_PHASE_10_PREP.md` §3, §4 (authoritative)
**Scope:** identity + integrity of the on-device language model artifact. The binary was
**read only** — never modified, moved, renamed or re-encoded.

---

## 1. The artifact

| Property | Value |
|---|---|
| Path | `model/Gemma3-1B-IT_multi-prefill-seq_q4_ekv4096.litertlm` |
| Exact filename | `Gemma3-1B-IT_multi-prefill-seq_q4_ekv4096.litertlm` |
| Byte size | **584 417 280 bytes** |
| Size (MiB / MB) | 557.34 MiB / 584.42 MB |
| **SHA-256** | **`1325ae366d31950f137c9c357b9fa89448b176d76998180c08ceaca78bba98be`** |
| Modified (UTC) | `2026-08-15T00:23:52.619460Z` |
| Readable / openable | **YES** — opened for binary read, header parsed, no errors |

SHA-256 was computed twice from independent tools (host `sha256sum`, and again on the
S20 FE after transfer — see §4). Both agree.

## 2. Container identity — the file really is a LiteRT-LM bundle

Read from the first bytes of the file (read-only inspection):

```
offset 0x00  4c 49 54 45 52 54 4c 4d          "LITERTLM"      ← container magic
offset 0x08  01 00 00 00  00 00 00 00  00 00 00 00           ← version 1.0.0
offset 0x14  80 01 00 00                      384             ← first section offset
```

FlatBuffer metadata decoded from the header section:

| Key | Value |
|---|---|
| `author` | `The ODML Authors` |
| `model_type` | `TF_LITE_PREFILL_DECODE` |

Further embedded content confirmed by string inspection of the first 256 KB:

* a SentencePiece-style tokenizer with the **Gemma chat control tokens**
  `<start_of_turn>user`, `<start_of_turn>model`, `<end_of_turn>`, `<mask>`,
  `<unused0…>` — i.e. a **Gemma-family instruction-tuned** vocabulary;
* section names including `tokenizer`, `sampler`, `vocab`, `backend`.

**Conclusion:** the artifact is a genuine **LiteRT-LM v1.0.0 container** carrying a
TFLite prefill/decode Gemma IT model. This is the format the LiteRT-LM runtime consumes
(see `temp/PHASE10_LITERT_RUNTIME.md`).

### What the filename encodes (naming convention, not a claim about contents)

| Token | Reading |
|---|---|
| `Gemma3-1B-IT` | Gemma 3, 1B parameters, instruction-tuned |
| `multi-prefill-seq` | multiple prefill sequence-length signatures in the bundle |
| `q4` | 4-bit weight quantisation |
| `ekv4096` | **4096-token KV cache** → practical context budget ≈ 4096 tokens |

The `ekv4096` figure is a hard design input for the prompt contract: system instruction
+ user text + generated JSON must fit inside ~4096 tokens
(see `temp/PHASE10_MODEL_PROMPT.md` §6).

## 3. Git safety (guide §4) — VERIFIED

`.gitignore` was extended with a model-weights block:

```gitignore
# On-device LLM weights (Phase 10) — NEVER commit. …
/model/*.litertlm
/model/*.task
/model/*.tflite
/model/*.bin
```

Verification performed after the change:

| Check | Command | Result |
|---|---|---|
| Binary ignored | `git check-ignore -v model/…​.litertlm` | **`.gitignore:44:/model/*.litertlm`** → ignored ✅ |
| Binary tracked? | `git ls-files model/` | empty → **not tracked** ✅ |
| Binary staged? | `git diff --cached --name-only` | empty → **nothing staged** ✅ |
| Docs still trackable | `git check-ignore model/README.md` | not ignored → **trackable** ✅ |
| `git status model/` | — | shows only `?? model/README.md` ✅ |

`model/README.md` and `model/MODEL_ATTRIBUTION.md` remain version-controllable, exactly
as guide §4 requires. The 557 MiB binary can never enter the repository through a
`git add .`.

## 4. On-device integrity proof (delivery path)

The artifact was transferred to the S20 FE with `adb push` and re-hashed **on the phone**:

| Step | Result |
|---|---|
| Destination | `/sdcard/Android/data/com.thraksha.guardian/files/models/` (app-specific external dir) |
| Transfer | 584 417 280 bytes in **12.7 s** (≈ 44 MB/s, USB) |
| On-device owner | `u0_a331 sdcard_rw` — the Guardian app's own uid |
| On-device SHA-256 | `1325ae366d31950f137c9c357b9fa89448b176d76998180c08ceaca78bba98be` |
| Match with host | **IDENTICAL** ✅ |
| On-device hash time | **0.81 s** (warm) for 557 MiB |
| App-sandbox read | `run-as com.thraksha.guardian head -c 8 …` → `LITERTLM` ✅ |

The last row matters: the application process itself can open the file **with no storage
permission at all**. See `temp/PHASE10_MODEL_DELIVERY.md`.

The 0.81 s hash time also proves the runtime integrity gate is cheap enough to run on
every app start before declaring the model READY (guide §10).

## 5. Integrity policy for Phase 10 (frozen here)

1. The SHA-256 in §1 is the **single pinned expected digest**. It is the value the
   `LocalModelProvider` will compare against (see delivery doc §6).
2. A file that exists but does not match → state `ERROR` / "MODEL INVALID". It is
   **never** loaded, and Thraksha **never** silently falls back to another model file.
3. The digest is recomputed from the real bytes on device — never trusted from a
   sidecar file that could be edited alongside the model.
4. If the artifact is ever re-downloaded or replaced, this document must be regenerated;
   a changed digest is a deliberate, reviewed event, not a silent swap (guide §27).

## 6. Status

**PASS** — the exact file exists, is readable, is a valid LiteRT-LM v1.0.0 Gemma IT
container, has a recorded byte size and SHA-256, is verified byte-identical after
transfer to the target phone, and is gitignored/untracked/unstaged while its
documentation stays trackable.

No blocker at this step.
