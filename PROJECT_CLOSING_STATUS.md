# THRAKSHA GUARDIAN — PROJECT CLOSING STATUS

**Status: FROZEN / CLOSED FOR NOW** — 2026-08-16
Work is paused deliberately, not abandoned. Everything needed to resume is in this repo.

---

## 1. Where the project actually stands

| Item | State |
| --- | --- |
| Latest artifact | **RC2** — `thraksha-guardian-1.0-privatealpha-rc2.apk` |
| RC2 SHA-256 | `93c15f227bc724ef02eb5cb79cabcac308f2dd53aa531a19d0665da8ed05c7de` |
| versionCode / versionName | `2` / `1.0-privatealpha-rc2` |
| Signer | `CN=Thraksha Guardian, OU=Private Alpha, O=Thraksha, L=Tirumula, ST=Andhra Pradesh, C=IN` |
| Signer cert SHA-256 | `0d96977d15ce5b17c4167a03f70fc72401f580a8d4f2258e059fa113516ac928` |
| Signature schemes | v2 + v3 (v1 off) · `debuggable=false` |
| Last completed phase | **Phase 12.1 — PASS** (real-world private-alpha ready) |
| Phase in progress when frozen | **Phase 13 — FAIL (coverage, not defect)** |
| Model | `gemma-4-E2B-it.litertlm`, SHA-256 `181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c` |
| Runtime | LiteRT-LM `0.16.0`, fully on-device |

**No known functional defect exists in RC2.** Phase 13 did not fail on product behaviour — it
failed on coverage it could not obtain (one test device instead of two, zero of 3–7 pilot days).

---

## 2. Phase history

| Phase | Scope | Outcome |
| --- | --- | --- |
| 0 | Secret redaction, git init, local-only baseline | done |
| 1–7 | Foundation: Keystore, SQLCipher store, audit chain, rulepack, policy/rule engines, demo decoys | done |
| 8 | Generalised device scanner, ThreatPack, evidence model | done |
| 8.1 | Evidence recalibration (DECLARED / GRANTED / OBSERVED), User ACT | done |
| 9 | Automation: SafetyPolicy, Planner, Engine, snapshot/verified restore | done |
| 10 | On-device LLM (Gemma via LiteRT-LM), intent contract, scope guard | done |
| 11B | UI rebuild — Protect / Automate / Audit | done |
| 12 | Release hardening, RC1, soak, readiness | done (RC1 debug-signed) |
| **12.1** | Package visibility fix, permanent signing identity, wording fix → **RC2** | **PASS** |
| **13** | Multi-device private-beta validation | **FAIL — blocked on hardware + elapsed time** |

---

## 3. What Phase 12.1 fixed (the reason RC2 exists)

1. **Release scanner was half-blind.** `QUERY_ALL_PACKAGES` lived only in
   `app/src/debug/AndroidManifest.xml`, a build-type overlay that never merges into release.
   RC1 saw **322 of 441** packages and **zero** of the 23 genuine owner-installed apps.
   Moved to the main manifest → RC2 sees **441/441**, exact ground-truth parity.
2. **Debug signing.** RC1 was signed with the public Android debug key. RC2 uses a permanent
   owner-held key (see §5), with a build gate that **fails loudly** rather than silently
   falling back to debug signing or emitting an unsigned APK.
3. **Two consumer overclaims** in the Samsung battery dialog — *"eyes and ears active 24/7"*
   and *"ensures the AI stays awake"* — replaced with accurate wording. A sweep of 26 claim
   patterns found no other user-facing overclaim.

---

## 4. Verified behaviour (on the real RC2 release binary, S20 FE)

* Scans 441 packages in ~14 s; 441 signing identities checked, 441 APK fingerprints computed.
* **Non-destructive** — 0 packages suspended or disabled; no automatic containment of
  arbitrary apps, enforced in code and proven under Device Owner on the AVD.
* 1 known-threat match — the controlled decoy only. **Zero** legitimate apps called a threat.
* **Advice Mode default**, no Device Owner on the personal phone.
* Meeting Mode: side-effect-free preview → explicit START → real changes → verified →
  STOP & RESTORE → **exact restoration**.
* Offline AI with radios disabled; correct intent; **cannot auto-execute** — START is always
  required.
* Reboot safe: no auto-start, encrypted audit persists, settings untouched.
* 213 unit tests, 90 instrumented (S20 FE), 90 instrumented (DO AVD) — all green. 0 crashes,
  0 ANRs.

---

## 5. ⚠ Signing key — the one thing that must not be lost

The keystore is **outside this repository** and is **not** committed:

```
C:/Users/kplee/secure/thraksha/thraksha-private-alpha.jks     (alias: thraksha-private-alpha)
```

Credentials come from an untracked `keystore.properties` at the repo root
(git-ignored; `keystore.properties.template` shows the format with no values).

**Every future Thraksha build must use this same key.** Android identifies an installed app
by *(package name + signing certificate)*. If the key is lost, no future build can update an
existing install — the app must be uninstalled, which destroys the SQLCipher-encrypted
database and the hash-chained audit log on that device.

Keep at least one backup off this laptop and outside the repository. Do not rotate casually.

---

## 6. To resume

1. Read `temp/THRAKSHA_DEMO_PHASE_13_PROGRESS.md` — it ends with a **resume checklist**
   keyed to guide sections, listing every blocked item and the exact command to run.
2. Phase 13 needs, in order of importance:
   * an **S24 Ultra** (or any second real phone) — required for §5/§6/§10 comparisons and,
     critically, to test automation restoration on **Android 14+**, which is unverified;
   * the **S20 FE reconnected** — unlocks the intent suite, Network Guard, Focus/Driving,
     the update-continuity install, and the remaining 10 false-positive items;
   * **3–7 days of ordinary use** on an unplugged phone for the pilot log and battery data.
3. RC2 stays frozen. If a defect ever requires source changes, cut **RC3** — do not re-label
   a modified build as RC2.

---

## 7. Known limitations carried forward

| # | Limitation |
| --- | --- |
| 1 | **Play Store policy unaddressed.** `QUERY_ALL_PACKAGES` is a restricted permission. Device-security is a permitted use case, but no declaration has been submitted and no review passed. This build is sideloaded, owner-installed only — nothing here implies Play approval. |
| 2 | **Single-device validation.** All real-world evidence comes from one Samsung S20 FE on Android 13 / API 33. Behaviour on Android 14+ and other OEMs is unverified. |
| 3 | **Memory headroom.** ~2.92 GB PSS with the model loaded — ~39 % of the S20 FE's 7.44 GiB. Untested below that; `minSdk 26` admits devices that cannot realistically run the model. |
| 4 | **Battery cost unmeasured.** Requires unplugged multi-day use. |
| 5 | **No GPU/NPU acceleration is claimed.** The device advertises Vulkan compute but no NNAPI/OpenCL, and no accelerated backend was demonstrated. |
| 6 | **False-positive rate uncharacterised** — 13 REVIEW items from one app population; 3 analysed in detail. |
| 7 | **Model is developer-provisioned** (2.4 GiB), not bundled in the APK, and is excluded from this repo. |
| 8 | Not commercial-production certified. Private alpha for the owner's own device. |

---

## 8. What is and isn't in this repository

**Included:** all source, signed rulepack/threatpack assets and their **public** keys, demo
decoy apps, the full `temp/` phase documentation and evidence trail, screenshots, and the
build configuration.

**Excluded by `.gitignore`** (verified before push): the release keystore and
`keystore.properties`, `keys/` (rulepack/threatpack private keys), `local.properties`,
all `.apk` artifacts, and the 2.4 GiB `.litertlm` model weights.

**Deliberate exception:** `villaincaller/villain-demo.keystore` *is* committed. It is the
controlled malware-sample's own identity — the ThreatPack `SIGNING_CERT_SHA256` indicator
matches this certificate, so the decoy must rebuild with the same signer on every checkout.
It is not part of Guardian's trust model. This was decided in Phase 0 and is documented in
`.gitignore`.

---

*Frozen 2026-08-16. Resume from `temp/THRAKSHA_DEMO_PHASE_13_PROGRESS.md`.*
