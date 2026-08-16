# PRIVATE ALPHA — INSTALL, OPERATE AND RECOVERY CHECKLIST

Build: `thraksha-guardian-1.0-privatealpha-rc1.apk`
SHA-256 `80e7aaea2619257159b05d2f13c57f81dad10e44b502b748bfb1788e9bb337c6`

**Owner-only.** Do not share this APK or the model file.

---

## Before you install

- [ ] **Back up anything important on the phone.** Nothing here wipes data, but install
      experimental software with a current backup.
- [ ] **Verify the APK** — it must match exactly:
      `sha256sum thraksha-guardian-1.0-privatealpha-rc1.apk`
      → `80e7aaea2619257159b05d2f13c57f81dad10e44b502b748bfb1788e9bb337c6`
- [ ] **Verify the signer** (expect the temporary debug identity — see risk H2):
      `apksigner verify --print-certs <apk>`
      → `CN=Android Debug`, cert SHA-256 `2a7c7cf3fa6214329168e2037d8a6ecdd92f5e7ab981f439d291ebccac3a19d4`
- [ ] **Check compatibility:** Android 8.0+ (minSdk 26) and an **arm64-v8a** device.
      32-bit-only phones cannot install this build.
- [ ] **Check free storage: ≥ 3 GB.** The model alone is 2.41 GiB and is *not* in the APK.
- [ ] **Understand what you will be asked for:** Do Not Disturb access, Modify system
      settings, and the notification permission. Nothing else is required.
- [ ] **Do NOT plan to enable Device Owner or Device Admin** on an everyday phone
      (see H2/M1/M2). The alpha runs in **Advice Mode**.

## Install

1. Install the APK (`adb install <apk>`, or open it on the phone and allow the installer).
2. Launch **Thraksha Guardian**.
3. On the Samsung battery-optimisation dialog choose **"Maybe Later"** — the exemption is
   not needed, and the dialog's wording overstates what the app does (risk M3).
4. **Provision the model** (the one step that legitimately needs a computer):
   ```
   # Git Bash users: MSYS_NO_PATHCONV=1 is required, or the push silently mis-targets
   MSYS_NO_PATHCONV=1 adb push gemma-4-E2B-it.litertlm \
     /sdcard/Android/data/com.thraksha.guardian/files/models/gemma-4-E2B-it.litertlm
   ```
   Expect 2,588,147,712 bytes; source SHA-256
   `181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c`.
5. Force-stop and relaunch the app.
6. **Verify the model through the app:** Settings → **On-device AI** should read
   **AVAILABLE** (or **Ready** once loaded). If it says *"MODEL INVALID"* or *"Not
   installed"*, the push failed — re-do step 4. Do not proceed with a failed gate.

## First run

- [ ] Grant permissions **deliberately**, one at a time, from
      Settings → **Permissions & access** — each row explains why it is needed and what
      happens without it.
- [ ] **Run an initial scan** (Protect → Scan Device).
- [ ] **Read the results honestly.** Expect a banner saying the build cannot see every
      installed package — that is true and important (risk H1). A quiet result is **not** a
      statement about your own installed apps.
- [ ] **Inspect a finding**: tap through to the evidence trail
      (Declared → Allowed → Seen happening). Note where it says Android does not report
      something — that is deliberate honesty, not a gap in the UI.
- [ ] **Test one Meeting routine**: Automate → Meeting → **read the plan preview** →
      START → watch the changes apply → **STOP & RESTORE**.
- [ ] **Verify restoration**: DND off, ringer and brightness back as they were.
- [ ] **Check Audit**: the scan, the routine, and the restoration should all be listed.

## Operating rules

- **Always read the plan preview before pressing START.** The model occasionally
  misreads a duration (risk M6); the preview is the safeguard and it works.
- **Stay in Advice Mode.** Do not provision Device Owner on your everyday phone.
- **Do not activate Device Admin** — it blocks uninstall until manually deactivated (M1).
- Expect **~10–25 s** for the first AI request (model load + inference), less afterwards.
- Expect the app to hold **~2–2.9 GB** while the model is loaded. Android will reclaim it
  when memory is genuinely needed (M4).
- Network Guard is **off** unless you turn it on, and it monitors **one** scoped app.

---

## Emergency recovery — in order of escalation

| Situation | Action |
| --- | --- |
| A routine changed settings and you want them back now | Open Automate → **STOP & RESTORE** |
| A routine is stuck or the app was killed mid-routine | Reopen the app — recovery runs at startup and either restores or reports honestly. Then STOP & RESTORE if still active. |
| Network Guard is on and you want it off | Protect → Network Guard → **Turn off** (or Android Settings → Connections → VPN) |
| You granted an access and want it back | Android Settings → Apps → Thraksha Guardian → permissions; DND at Settings → Notifications → Do Not Disturb → App access |
| The app misbehaves | Force-stop it. It declares no `BOOT_COMPLETED` receiver, so it will not restart itself. |
| **You want it gone** | Uninstall normally. Uninstall removes the app sandbox, the encrypted database, the audit chain **and the 2.41 GiB model**. |
| **Uninstall is refused** (`DELETE_FAILED_DEVICE_POLICY_MANAGER`) | Settings → Security and privacy → Other security settings → **Device admin apps** → Thraksha Guardian → **Deactivate**, then uninstall. ADB cannot force this. |
| You want the data gone but the app kept | Android Settings → Apps → Thraksha Guardian → Storage → **Clear data**. Irreversible — the audit chain and encrypted DB are destroyed by design; there is no escrow. |

## What uninstall does and does not do

**Does:** removes the app, the SQLCipher database, the wrapped passphrase, the cache, the
audit chain, and the app-specific external directory containing the model. The Keystore key
is destroyed with the app. *Verified on-device during 12B.*

**Does not:** revert Android settings a routine changed — but a routine restores those on
STOP or expiry anyway, so **stop any active routine before uninstalling.**
