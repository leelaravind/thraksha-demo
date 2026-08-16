# Thraksha Guardian

An **offline, on-device Android security app**. It scans the applications installed on the
owner's phone, explains what each one is *actually allowed* to do, and can run small
automation routines that it always restores afterwards.

Everything runs locally. There is no account, no cloud service and no network egress for
analysis — including the language model.

> **Status: frozen private alpha.** See [`PROJECT_CLOSING_STATUS.md`](PROJECT_CLOSING_STATUS.md)
> for the current state, the signing-key warning, and how to resume.

---

## What it does

**Protect** — enumerates every installed package, reads declared capabilities, computes APK
fingerprints, matches a signed threat pack, and applies generic capability rules. On the
reference device that is 441 packages in ~14 seconds.

**Automate** — Meeting / Focus / Driving routines. Each one previews exactly what it will
change, does nothing until you press START, verifies each change actually took effect, and
restores your previous settings exactly when it ends.

**Ask Thraksha** — describe what you want in your own words. A local Gemma model (via
LiteRT-LM) proposes a routine; it can only ever *propose*. Thraksha validates the output
against a fixed set of allowed routines, builds the plan itself, and waits for you.

**Audit** — a hash-chained, SQLCipher-encrypted log of everything the app did, in order.

---

## Design rules the code actually enforces

* **No absolute claims.** There is no code path that renders "safe", "secure" or "protected".
  The best outcome the app can state is *"No urgent threats found"*.
* **Evidence stages are honest.** Findings are DECLARED / GRANTED / OBSERVED. A capability an
  app merely *declares* is never reported as behaviour it performed.
* **Advice Mode by default.** Containment requires Device Owner *and* an explicit user
  decision, and it refuses any target that is not allowlisted — proven in tests even with
  Device Owner authority.
* **Scanning modifies nothing.** No package is suspended, disabled or altered by a scan.
* **The model cannot act.** It proposes; it cannot change a setting, run a command or make a
  security decision.
* **Thraksha scans itself** under the same rules, with no special-casing.

---

## Architecture

```
security/
  inventory/    AppInventory — the only PackageManager caller in the security path
  scan/         DeviceScanEngine, GenericRiskRules
  evidence/     DECLARED/GRANTED/OBSERVED model, StatusMapper
  threatintel/  signed ThreatPack loader + verifier
  policy/       PolicyEngine — advice vs enforcement
  act/          User ACT — explicit, allowlisted, reversible containment
  network/      NetworkGuard — scoped local VpnService checkpoint
automation/     SafetyPolicy, Planner, Engine, snapshot + verified restore
ai/             LocalModelRepository, OnDeviceIntentModel, RequestScopeGuard
data/           KeystoreManager, SQLCipher Room store, hash-chained AuditLog
ui/             Compose — Protect / Automate / Audit
```

Signed asset packs (`rulepack`, `threatpack`) ship with their **public** keys only and are
verified at load; an unverifiable pack reads as *unavailable*, never as "no threats found".

---

## Building

Requires JDK 17+, Android SDK 36, and release signing credentials.

```bash
java -jar gradle/wrapper/gradle-wrapper.jar :app:assembleDebug
java -jar gradle/wrapper/gradle-wrapper.jar :app:testDebugUnitTest
```

For a **release** build, copy `keystore.properties.template` → `keystore.properties` and fill
in the four values (the file is git-ignored, and the keystore itself must live outside the
repository — the build enforces this). Without credentials the release build **fails
deliberately**; there is no debug-signing fallback.

```bash
java -jar gradle/wrapper/gradle-wrapper.jar :app:assembleRelease
```

### On-device model

The ~2.4 GiB `.litertlm` model is **not** in this repository. Provision it manually:

```bash
adb shell mkdir -p /sdcard/Android/data/com.thraksha.guardian/files/models
adb push gemma-4-E2B-it.litertlm /sdcard/Android/data/com.thraksha.guardian/files/models/
```

The app verifies its SHA-256 on load and reports *"Integrity verified"*. If the model is
missing, Meeting / Focus / Driving keep working exactly as before — only natural-language
requests are unavailable.

---

## Permissions worth explaining

`QUERY_ALL_PACKAGES` is declared and used **read-only**. A security scanner that cannot see
the apps the user installed cannot do its job — without it the release build saw 322 of 441
packages and none of the user's real apps. Google Play restricts this permission; device
security is a permitted use case, but **no Play declaration has been submitted and no review
passed**. This is a sideloaded, owner-installed build.

---

## Testing

| Suite | Count |
| --- | --- |
| Unit | 213 |
| Instrumented (S20 FE) | 90 |
| Instrumented (Device Owner AVD) | 90 |

Full phase-by-phase evidence, screenshots and verification records live under `temp/`.

---

## Licence / distribution

Private alpha. Not distributed, not published, not Play-approved. The model is used under the
Gemma Terms of Use and is not redistributed here.
