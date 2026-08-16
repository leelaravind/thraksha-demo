# PHASE 12C — PRIVATE ALPHA RISK REGISTER

Scope: `thraksha-guardian-1.0-privatealpha-rc1.apk`
SHA-256 `80e7aaea2619257159b05d2f13c57f81dad10e44b502b748bfb1788e9bb337c6`

Classification: **BLOCKER** = must not install · **HIGH** = install only with eyes open ·
**MEDIUM** = accept and monitor · **LOW** = cosmetic/hygiene.

**BLOCKERS: none.**

---

## HIGH

### H1 — The scanner cannot see your third-party apps
`QUERY_ALL_PACKAGES` exists only in the debug manifest. Measured on the same phone,
minutes apart: debug **441** packages, release **322** — the 322 being 319 system packages
plus Guardian, VillainCaller and GoodCaller. **None of your actually-installed
third-party apps are visible.**

*Why it is not a BLOCKER:* the app says so, unprompted — "This build cannot see every
installed package, so these results cover only the apps Android exposes to Thraksha" — and
reports `VisibilityScope.REDUCED`. Nothing is misrepresented. Automate, Ask Thraksha,
Network Guard and Audit are entirely unaffected.

*What it means for you:* Protect will look quiet because it is mostly looking at system
packages, not because your phone is clean. **Do not read "no urgent threats found" as a
statement about the apps you installed.**

*Options:* accept as-is (least privilege), or add `QUERY_ALL_PACKAGES` to the main manifest
and re-cut the build (restores the 441-package reach; broadens the permission surface and
would need a Play declaration if ever published). **Owner's decision — 12A deliberately did
not make it.**

### H2 — Debug signing identity
No release keystore exists, and Phase 12 must not invent one. The APK is signed with
Android's standard **debug** key (`CN=Android Debug`,
SHA-256 `2a7c7cf3…19d4`), whose private key ships with every Android SDK.

*Consequences:* it is **not distributable** — install on your own device only; anyone could
produce an "update" this install would accept; and a future properly-signed build
**cannot update this one** — it must be uninstalled first, destroying the encrypted
database and audit chain.

*Mitigation before any wider use:* generate a keystore under your control, keep credentials
out of the repo, wire a real `signingConfig`, re-cut.

---

## MEDIUM

### M1 — Device Admin blocks uninstall
If you activate Device Admin, the app **cannot be uninstalled** — not by Settings, not by
ADB (`DELETE_FAILED_DEVICE_POLICY_MANAGER`; `dpm remove-active-admin` refuses non-test
admins) — until you deactivate it manually at
**Settings → Security and privacy → Other security settings → Device admin apps →
Thraksha Guardian → Deactivate**. Verified on the device; the path works.
*For the everyday-phone alpha, simply do not activate Device Admin.*

### M2 — Device-admin policy declaration is overbroad
`res/xml/device_admin.xml` declares ten policies including **wipe-data ("Delete all
data")**, **reset-password** and **disable-camera**. The app uses none of them — its
Device-Owner work is package suspension and permission denial. Android's consent dialog
therefore lists alarming capabilities the app never exercises, which undercuts the
product's own honesty standard at the exact moment consent is given.
*Recommendation:* trim to what is used before any distribution.

### M3 — Battery-optimisation dialog overclaims
The Samsung onboarding dialog says Thraksha's *"eyes and ears"* stay *"active 24/7"* and
that the exemption *"ensures the AI stays awake even when your phone is in your pocket."*
Both are false: the AI runs on demand and is now released under memory pressure. The dialog
is also still in the pre-Phase-11B green/gold styling.
*Not fixed in this build* because 12B had to test the exact 12A artifact. **Fix in the next
build.** Dismiss it with "Maybe Later".

### M4 — Model resident set is large
While loaded the process holds **~2.0–2.9 GB — roughly 37 % of this phone's 7.6 GB** — at
foreground-service priority, and it stays loaded through background/foreground cycles when
the system has headroom (measured stable across 5 cycles, +5 MB drift, no leak).

The 12A fix means Android can reclaim it: on a real `RUNNING_CRITICAL` signal the process
dropped **2.90 GB → 0.97 GB**, settling at 135 MB idle, and reloads transparently on the
next request. Without that fix it never released.
*Practical effect:* on a phone that is already under memory pressure, expect other apps to
be evicted while Thraksha holds the model, and expect a ~25 s reload after a release.

### M5 — Gemma licence / redistribution unresolved
The model is under the **Gemma Terms of Use**, not an OSI licence, and no legal review has
been done. *Mitigated:* the model is **not bundled** in the APK — you provision it yourself
out-of-band. **No redistribution clearance is claimed anywhere in Phase 12.** Do not share
the model file.

### M6 — AI interpretation accuracy
Observed once: *"focus for 30 minutes"* was understood as **"Focus Mode — 60 minutes"**.
Not a clamp — the model returned 60. Other requests in the session were correct
(45-minute meeting understood as 45, twice).
*Mitigation, and it worked:* the mandatory plan preview showed the wrong duration **before
anything ran**, and Cancel was available. **Always read the preview before pressing START.**

### M7 — CLARIFY / UNSUPPORTED / REJECTED paths lightly exercised on device
These states are implemented and covered by 24 unit tests
(`Phase10IntentValidationTest`) plus the Phase 10C instrumented suite, but only the
ready/understanding/model-unavailable paths were driven through the UI on hardware.

### M8 — `ThrakshaAccessibilityService` ships but is unused
No product feature uses it; automation works through DND policy access and Modify System
Settings. It is exported (as any AccessibilityService must be) and protected by the
system-only `BIND_ACCESSIBILITY_SERVICE`, and it is inert unless you enable it manually —
which the install checklist does not ask you to do. Still the most privileged surface in the
manifest, and it should be removed before distribution.

### M9 — Extended soak not performed
~1.5 h of dense verification (install, scan, AI, two routines, reboot, upgrade,
memory-pressure) rather than the multi-hour soak §29 suggests. Battery drain over a full
day is therefore uncharacterised.

---

## LOW

| # | Item |
| --- | --- |
| L1 | `VIBRATE` permission declared but no `Vibrator` call exists in production code. |
| L2 | Developer-diagnostics screen ships in the release build. Read-only service checks; cannot create a finding or an audit entry. |
| L3 | No attribution/licence bundle shipped. About names components but no `NOTICE` text. |
| L4 | 32-bit-only devices cannot install (APK is arm64-v8a + x86_64 only, because LiteRT-LM ships only those). |
| L5 | `backup_rules.xml` / `data_extraction_rules.xml` are unmodified AGP templates — inert while `allowBackup=false`. |
| L6 | R8 disabled, so the APK is 64 MiB and unobfuscated. Deliberate: four reflective/JNI surfaces would need re-verification. |
| L7 | Battery-optimisation dialog re-appears on every launch while the exemption is declined. |
| L8 | The app flags *itself* for review when it holds device admin. Honest, but momentarily confusing. |
| L9 | Screen rotation not exercised. |
| L10 | Audit chain is never pruned; it grows without bound (slowly — tens of rows per scan). |

---

## Risks explicitly assessed and found acceptable

| Area | Finding |
| --- | --- |
| **Network egress** | Exactly one network-capable path in the entire production source (Network Guard's `protect()`ed UDP forwarder). **No HTTP stack exists in the APK.** |
| **Offline AI** | Proven on device: inference produced a correct plan **in airplane mode**. |
| **Secrets** | None in source or APK. Only RSA *public* keys ship; pack private keys are git-ignored and absent. |
| **Scanner destructiveness** | Non-destructive. Nothing modified, suspended or quarantined by scanning; containment requires explicit User ACT under Device Owner. |
| **Automation safety** | START → snapshot → execute → verify → active → restore → verify intact. `OPENED` never rendered as verified. Nothing starts without an explicit press. |
| **Privacy** | Raw prompts never persisted (length only, test-enforced). DB SQLCipher-encrypted with a Keystore-wrapped passphrase; `allowBackup=false`. |
| **Data survival** | Encrypted DB and audit chain survived both a reboot and an in-place upgrade. |
| **Failure honesty** | Missing/corrupt/unreadable model, invalid packs and denied access each produce a distinct, honest state — never a silent fallback. |
| **False positives** | On the release build the only flagged app was VillainCaller (the planted known-threat sample). Guardian's own self-finding correctly disappeared when device admin was deactivated — the scanner follows real evidence. |
