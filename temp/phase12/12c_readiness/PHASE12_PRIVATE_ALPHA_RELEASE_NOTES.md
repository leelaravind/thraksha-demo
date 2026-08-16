# THRAKSHA GUARDIAN — PRIVATE ALPHA RELEASE NOTES

Version 1.0 (build 1) · `thraksha-guardian-1.0-privatealpha-rc1.apk`
SHA-256 `80e7aaea2619257159b05d2f13c57f81dad10e44b502b748bfb1788e9bb337c6`
Android 8.0+ · arm64-v8a / x86_64 · Owner-only experimental build

---

## What Thraksha is

An on-device security and automation app. It checks what the apps on your phone are able to
do, can watch one app's outbound connections when you ask it to, and runs simple device
routines you start yourself — all locally, with a tamper-evident record of everything it did.

## What works

**Protect**
- Scans installed packages: what each app **declares**, what Android currently **allows**,
  and — only where Thraksha itself verified it — what it was **seen doing**.
- Matches against a cryptographically signed offline threat pack and signed profile rules.
- Evidence trail per capability (Declared → Allowed → Seen happening), with technical detail
  behind progressive disclosure.
- User ACT: only responses that are technically possible right now are offered, and
  "done and verified" appears only when the OS state was re-checked.

**Automate**
- Ask Thraksha in your own words. Understanding runs **entirely on this phone** — verified
  in airplane mode.
- Every routine shows a deterministic plan preview and requires an explicit **START**.
- Meeting, Focus and Driving. Current settings are snapshotted first and restored exactly
  on stop or expiry.

**Audit**
- A hash-chained, encrypted timeline of everything the app did, readable in plain language,
  with the raw record one tap away.

**Privacy**
- No account, no cloud service, no analytics. **The APK contains no HTTP client at all.**
- Your typed requests are never stored — only their length is recorded.
- Storage is SQLCipher-encrypted with an Android Keystore-wrapped passphrase; backup is
  disabled.

## What is experimental

- **On-device AI interpretation.** It occasionally misreads a detail — in testing,
  "focus for 30 minutes" was understood as 60 minutes. The plan preview exists precisely so
  you catch that before anything runs. **Read it every time.**
- **First AI request takes ~10–25 seconds** (model load plus inference). Later requests are
  faster while the model stays loaded.
- **Memory:** while loaded the app holds roughly 2–2.9 GB. Android reclaims it when memory
  is genuinely needed, and the model reloads transparently on your next request.

## Known limitations — read these

1. **This build cannot see your third-party apps.** It sees system packages plus two
   bundled demo apps — 322 of them on the test phone, versus 441 with broad visibility. The
   app states this on the results screen. **A quiet Protect screen is not a statement about
   the apps you installed.**
2. **Signed with a temporary debug key.** Not distributable, and a future properly-signed
   build cannot update this install — it would need uninstalling first, which destroys the
   database and audit chain.
3. **The AI model is not included** and must be provisioned separately from a computer. It
   is licensed under the Gemma Terms of Use; redistribution has **not** been legally
   reviewed and is not claimed. Do not share it.
4. **Do not enable Device Admin or Device Owner** on an everyday phone. Device Admin also
   blocks uninstall until you deactivate it manually in Settings.
5. **One onboarding dialog overstates what the app does** (it claims the AI stays "active
   24/7"). It does not. Dismiss it with "Maybe Later"; the wording is fixed in the next
   build.
6. **Network Guard monitors one scoped app**, only while you have it switched on, and reads
   destination metadata only — never contents.
7. **No long-term soak data.** Roughly 1.5 hours of dense testing; battery behaviour over a
   full day is uncharacterised.

## Unsupported

32-bit-only devices · Android below 8.0 · multi-user / work profiles · tablets (untested) ·
any distribution beyond the owner's own device.

## Safe operating mode

**Advice Mode, least privilege.** Thraksha detects and advises; it does not automatically
contain arbitrary apps. Every action that changes something is initiated by you.

## Recovery

Stop & Restore for settings · turn off Network Guard · revoke access in Android Settings ·
uninstall (removes app, data, audit chain and model). Full detail in
`PHASE12_PRIVATE_ALPHA_INSTALL_CHECKLIST.md`.

## Not claimed

Not production-ready. Not commercially certified. Not a replacement for any security
product. It makes no absolute safety claim anywhere in its interface — the strongest thing
it will say is *"No urgent threats found"*, and it tells you what it could not check.
