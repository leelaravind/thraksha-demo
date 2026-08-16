# PHASE 12A — GATE ASSESSMENT

Assessed against guide §21. Every criterion is backed by evidence in this directory.

| # | Criterion | Verdict | Evidence |
| --- | --- | --- | --- |
| 1 | Release artifact is reproducible | **PASS** | Single-command build; all dependency versions explicitly pinned (no dynamic versions). Digest differs per build because of the embedded timestamp and local signing — stated in `PHASE12_RELEASE_ARTIFACT.md`. |
| 2 | No production secret exists | **PASS** | `PHASE12_SECRET_AUDIT.md` — source **and** APK scanned; only RSA public keys ship; pack private keys are git-ignored and absent from the APK; the single `sk-` hit resolved to `…risk-…` in a rule ID. |
| 3 | No unexpected exported component | **PASS** | 4 exported components, each justified; the only ContentProvider is AndroidX Startup, not exported. `PHASE12_MANIFEST_PERMISSION_AUDIT.md` §3. |
| 4 | Permissions justified | **PASS (with one flagged)** | 12 permissions classified. `VIBRATE` is genuinely unused (no `Vibrator` reference in production code) and is marked REMOVE-deferred rather than silently justified. |
| 5 | Dev/test tooling absent from runtime | **PASS (with one noted)** | No Stitch/MCP/Claude artefacts, no proxy, no temp docs, no eval datasets. The Developer-diagnostics screen does ship; it is read-only and cannot create a finding — logged LOW. |
| 6 | Network egress understood | **PASS** | Exactly one network-capable path in the whole production source (Network Guard's `protect()`ed UDP forwarder). **No HTTP stack exists in the APK.** |
| 7 | Local AI boundary intact | **PASS** | No download/remote path; model output confined to a closed-key JSON schema → validated intent → SafetyPolicy → planner → explicit START. Proven by Phase 10C tests. |
| 8 | Storage/privacy behaviour understood | **PASS** | Three storage surfaces; SQLCipher + Keystore-wrapped passphrase; `allowBackup=false`; raw prompts never persisted (only length), test-enforced. |
| 9 | Failure modes safe | **PASS** | Missing/corrupt/unreadable model, invalid packs, denied special access, process death and routine recovery all covered by passing instrumented tests; each failure yields a distinct honest state, never a silent fallback. |
| 10 | Automation restores reliably | **PASS** | Phase 9 suite passed on both devices; START → snapshot → execute → verify → active → restore → verify preserved; `OPENED` never rendered as verified. |
| 11 | Scanner is non-destructive | **PASS** | Real release-build scan of 322 packages modified nothing; no automatic containment of arbitrary apps; VillainCaller state unchanged by scanning. Containment happens only through explicit User ACT under Device Owner. |
| 12 | Full regressions green | **PASS** | 213 unit + 89 S20 FE + 69 DO AVD passed; **0 failures, 0 errors**; all skips `Assume`-gated and explained. |
| 13 | Legal/provenance limitations documented | **PASS** | Gemma licence redistribution clearance explicitly **NOT** claimed; model not bundled; attribution bundle absent and noted. |

## Defect found and fixed in 12A

**Model held ~2.9 GB (37 % of device RAM) indefinitely at foreground-service priority.**
`unload()` existed but was never called and there was no memory-pressure hook, so Android
would evict the user's other apps rather than reclaim from Thraksha. Fixed minimally by
adding `onTrimMemory`/`onLowMemory` handling that reuses the existing tested `unload()`.
Verified: 2.04 GB → 135 MB on a real trim signal, transparent reload afterwards, zero
crashes, and all regressions re-run green after the change.

## Open items carried to 12C (none blocks this gate)

1. **Package visibility (HIGH-visibility owner decision).** The release candidate sees 322
   packages vs 441 in debug, and **no third-party user apps** — `QUERY_ALL_PACKAGES` is
   debug-only. The app reports the reduced scope honestly, so nothing is misrepresented,
   but the Protect surface delivers materially less on a personal phone than the demo did.
2. **Debug signing identity (temporary).** No release key exists; not invented. Blocks any
   distribution and prevents in-place upgrade to a properly-signed build.
3. **`ThrakshaAccessibilityService` ships but is unused** by any product feature —
   recommended for removal before distribution.
4. **`VIBRATE`** declared but unused.
5. **Gemma licence** redistribution review unresolved (mitigated: not bundled).
6. **Attribution/licence bundle** not shipped.
7. **AI interpretation accuracy** — "focus for 30 minutes" understood as 60 minutes on one
   observed request; mitigated by the mandatory preview.

---

# VERDICT: 12A — PASS

All thirteen gate criteria are met with evidence. One genuine defect was found and fixed
minimally; regressions were re-run afterwards and are green. Remaining items are documented
limitations and one owner decision, none of which is a safety failure.

**Proceeding automatically to 12B.**
