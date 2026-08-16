# PHASE 12A — DATA, STORAGE AND PRIVACY AUDIT

The production source touches exactly **three** storage surfaces. There is no other
persistence path in the app.

| Surface | Used by | Contents |
| --- | --- | --- |
| Encrypted Room DB (app-private internal storage) | `DatabaseProvider` → SQLCipher | scan/audit/config/automation state |
| `SharedPreferences` (app-private) | `KeystoreManager` | the **wrapped** DB passphrase (IV + ciphertext, Base64) |
| `getExternalFilesDir("models")` | `LocalModelRepository` | the developer-provisioned model file |
| `cacheDir` | LiteRT-LM `EngineConfig` | runtime scratch space for the inference engine |

## What is stored

| Data | Location | Encrypted | Retention | On uninstall |
| --- | --- | --- | --- | --- |
| Scan results / findings | **in memory only** (`DeviceScanEngine.lastResult`) | n/a | lost on process death | n/a |
| Evidence (`CapabilityEvidence`, observations) | in memory; observations in `RuntimeObservationStore` | n/a | process lifetime | n/a |
| Audit chain (`audit_log`) | SQLCipher DB, internal storage | **yes** | append-only, never pruned | removed with the sandbox |
| Automation snapshot + active run | `config` table (`automation.active_run`), SQLCipher | **yes** | cleared on restore/acknowledge | removed |
| Execution mode, appearance preference | `config` table, SQLCipher | **yes** | until changed | removed |
| DB passphrase | SharedPreferences, **wrapped by an Android Keystore key** | key is non-exportable, hardware-backed where available | until uninstall | removed; the Keystore key is also destroyed |
| AI request text | **not stored — see below** | — | — | — |
| Model file (2.41 GiB) | `/sdcard/Android/data/com.thraksha.guardian/files/models/` | no (plain file) | until deleted | **removed with the app-specific external dir on uninstall** |
| Inference scratch | `cacheDir` | no | OS-reclaimable | removed |

## Raw prompts are not persisted — verified

`AiIntentInterpreter` records only the **length** of a request:

```kotlin
audit("AI_INTENT_REQUESTED", "request received (${trimmed.length} chars)")
```

Every other AI audit line records a reason code or validation stage
(`AI_INTENT_REJECTED — out of scope (…)`, `AI_INTENT_PARSED — clarification needed (…)`),
never the user's words and never the model's raw output. The audit answers *"an AI request
happened, and what the deterministic layers decided about it"* — not *"what the user
typed"*.

This is additionally enforced by test:
`Phase10CIntegrationInstrumentedTest.aiEvents_landInTheAuditChain_withoutStoringTheUserPrompt`
(passes on the S20 FE).

## Encryption

* Database: SQLCipher (`net.zetetic:sqlcipher-android` 4.17.0, `libsqlcipher.so`).
* Passphrase: generated at runtime and **wrapped by an AES key held in the Android
  Keystore**; only the IV and ciphertext are written to SharedPreferences. The plaintext
  passphrase exists only in memory during unwrap. Nothing is hardcoded.
* Failure is fail-closed and visible: if the Keystore key is invalidated (e.g. by a device
  restore) or the DB cannot be opened, `FoundationStatus.Failed` is published and the UI
  says *"Protection is not running"* — the app does **not** silently fall back to an
  unencrypted or freshly-created database.

## Backup / extraction

`android:allowBackup="false"` is present in the merged release manifest (verified in the
APK). Consequently the encrypted DB and the wrapped passphrase are **not** included in
cloud backup or device-to-device transfer — which is correct, since a restored DB whose
Keystore key did not travel with it would be unreadable anyway.

`backup_rules.xml` and `data_extraction_rules.xml` are still the unmodified AGP templates
(entirely commented out). They are inert while `allowBackup="false"`. Cosmetic cleanup
item, no behavioural effect — logged LOW in 12C.

## Deletion behaviour

* **Uninstall** removes the app sandbox: encrypted DB, SharedPreferences, cache, and the
  app-specific external directory containing the model. The Keystore key is destroyed with
  the app.
* **Clear data** (`ALLOW_CLEAR_USER_DATA` is set) destroys the audit chain and the wrapped
  passphrase. This is unrecoverable by design — there is no escrow.
* There is no in-app "delete my data" control. For an owner-only alpha, Android's own
  Clear data / Uninstall are the deletion mechanisms; documented in the 12C checklist.

## Sensitive-data implications

* The audit chain records **package names, rule IDs, severities and verified state
  changes**. On a personal phone the installed-app list is itself personal information —
  though in this release candidate the scanner sees only system packages plus two declared
  demo apps (see manifest audit §4), so third-party app names are largely absent.
* Network Guard records **destination address, port, protocol and size** for the single
  scoped package — never payload contents.
* Nothing is transmitted off-device: there is no HTTP stack in the APK at all
  (`PHASE12_NETWORK_EGRESS_AUDIT.md`).
