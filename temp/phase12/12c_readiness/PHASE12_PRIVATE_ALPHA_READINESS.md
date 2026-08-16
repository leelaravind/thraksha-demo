# PHASE 12C — PRIVATE ALPHA READINESS (GO / NO-GO)

Build under decision: `thraksha-guardian-1.0-privatealpha-rc1.apk`
SHA-256 `80e7aaea2619257159b05d2f13c57f81dad10e44b502b748bfb1788e9bb337c6`
Signer: `CN=Android Debug` (temporary) · debuggable **false** · v1.0 (1) · minSdk 26

The question being answered (guide §42): *can this exact build be installed on a normal
personal Android phone, operate without development machinery, scan real applications
without harming them, perform bounded automation safely, recover correctly, and leave the
device in a known state?*

---

## Evidence summary

| Gate | Result |
| --- | --- |
| **12A — Release & Safety Hardening** | **PASS** — 13/13 criteria |
| **12B — Clean-device / Real-world Soak** | **PASS** — 11/11 criteria |
| Unit tests | **213 / 213**, 0 failures |
| Instrumented — S20 FE | **89 passed**, 3 `Assume`-skipped, **0 failures, 0 errors** |
| Instrumented — Device Owner AVD | **69 passed**, 23 `Assume`-skipped, **0 failures, 0 errors** |
| Combined instrumented coverage | **91 of 92 executed on hardware** |
| Crashes / ANRs across all of Phase 12 | **0 / 0** |

### The load-bearing demonstrations

* **Offline AI proven, not asserted.** In airplane mode, Ask Thraksha returned
  "Meeting Mode — 45 minutes" with a full deterministic plan. The APK contains **no HTTP
  client at all**, so there is no cloud path to fall back to.
* **Automation restores.** Meeting: `zen 0→1→0`, `brightness 13→77→13` — numerically exact
  against real OS state. Driving: three actions VERIFIED, then "Your previous settings are
  back". Nothing starts without an explicit START.
* **Scanner is non-destructive.** A real scan of 322 packages modified, suspended or
  quarantined nothing. Guardian's own self-finding disappeared when device admin was
  deactivated — it follows evidence, not a static list.
* **Survives reboot and upgrade.** Encrypted DB and audit chain intact across both; model
  re-verified through the SHA gate; the app does not auto-start on boot.
* **Fails honestly.** Missing/corrupt model, invalid packs and denied access each produce a
  distinct, truthful state. Reduced package visibility is stated in the UI unprompted.
* **Reclaims memory.** The 12A fix released 2.90 GB → 0.97 GB on a real pressure signal and
  reloaded transparently.

### Open risks

**No BLOCKERS.** Two HIGH (reduced package visibility H1; temporary debug signing H2),
nine MEDIUM, ten LOW — all catalogued in
`PHASE12_PRIVATE_ALPHA_RISK_REGISTER.md` with mitigations.

Both HIGH items are **limitations of scope and distribution, not safety failures**, and both
are disclosed to the user by the app or the release notes.

---

## Everyday-phone operating mode (guide §35)

* **Advice Mode / least privilege** — the app's default and the mode verified throughout
  12B (Settings → Protection reads "Advice mode" on a clean install).
* **Device Owner must NOT be provisioned** on an everyday personal phone. Device Admin
  should not be activated either — it blocks uninstall (M1) and its declared policy set is
  overbroad (M2).
* **Automatic containment of arbitrary real apps stays disabled.** Containment requires
  Device Owner authority *and* an explicit User ACT.
* **Every state-changing action is explicit** — scans, routines and responses are all
  user-initiated, and routines require a plan preview plus a START press.
* On a clean install all three special accesses default to **NOT GRANTED**, verified.

---

## Judgement

The build does what it claims and is candid about what it cannot do. The safety
architecture that Phases 1–11 established — explicit START, snapshot/verified restore,
evidence honesty, tamper-evident audit, a model that cannot reach an executor — held under
every test applied here, including a reboot, an in-place upgrade, memory pressure and total
network loss.

The two HIGH risks are real and must be understood before installing, but neither can harm
the phone: one narrows what the scanner can see (and the app says so on screen), the other
constrains distribution and the upgrade path (and the app is for the owner only).

One caveat is worth stating plainly rather than burying: **with reduced package visibility,
Protect will look reassuringly quiet on a personal phone because it is largely inspecting
system packages.** The value of this alpha lies principally in Automate, Ask Thraksha,
Network Guard and the audit trail. If the scanner's reach on your own apps is the point,
resolve H1 first and re-cut the build.

---

# PASS — PRIVATE ALPHA READY

> Thraksha is reasonable for controlled installation on the owner's everyday Android device
> as a private experimental alpha in Advice Mode, subject to the documented limitations.

This is **not** production readiness, and no commercial or redistribution clearance is
claimed. Conditions of this verdict:

1. Owner-only installation. The APK and the model are not to be shared.
2. Advice Mode; no Device Owner, no Device Admin activation.
3. Install and recovery per `PHASE12_PRIVATE_ALPHA_INSTALL_CHECKLIST.md`.
4. Limitations in `PHASE12_PRIVATE_ALPHA_RELEASE_NOTES.md` are read and accepted —
   in particular H1 (the scanner cannot see your third-party apps) and H2 (temporary debug
   signing, so no in-place upgrade to a signed build later).
