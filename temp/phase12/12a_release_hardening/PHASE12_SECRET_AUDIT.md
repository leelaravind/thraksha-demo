# PHASE 12A — SECRET AND DEV-TOOLING AUDIT

Scanned **both** the source tree and the built release APK (extracted to
`apk_extract/`, 955 entries), as required by §10 — source-only findings would not be
sufficient evidence.

## Result: no production secret found

## 1. Built release APK

| Check | Method | Result |
| --- | --- | --- |
| Private key material | grep for `PRIVATE KEY` / `BEGIN RSA` / `BEGIN EC` / `BEGIN OPENSSH` across every extracted entry | **none** |
| Stitch / MCP / Claude / Anthropic artefacts | string search across all entries **and** `strings` over `classes*.dex` | **none** |
| API keys / OAuth / bearer tokens | patterns `api_key`, `apiKey`, `API_KEY`, `AIza…`, `ya29.…`, `sk-…`, `Bearer `, `client_secret` | **none** (one false positive, §3) |
| Google usercontent / model-dev endpoints | `strings` over dex for `usercontent.google`, `api.claude`, `anthropic` | **none** |
| Developer paths / personal identifiers | regex for `C:\Users\<name>`, `/home/<name>`, `kplee` over dex | **none** |
| `BuildConfig` constants | dex strings | only AGP defaults: `DEBUG`, `APPLICATION_ID`, `BUILD_TYPE`, `VERSION_NAME`. **No custom `buildConfigField` exists in the build.** |
| Pack signing keys | inspected both `_public.key` assets | **public keys only** — X.509 `SubjectPublicKeyInfo` RSA (`MIIBIjANBgkqhkiG9w0BAQEF…`). A PKCS#8 private key would begin `MIIEv…`; neither file does. |
| HTTP stack | dex strings for `okhttp3`, `retrofit2`, `com.google.firebase` | **none present in the APK** |

## 2. Source tree

Scanned `app/src/main`, `app/build.gradle.kts`, `build.gradle.kts`, `settings.gradle.kts`,
`gradle.properties` for key/secret/token/password/bearer/`-----BEGIN` patterns.

Every hit was a false positive, and each is benign on inspection:

| Hit | What it actually is |
| --- | --- |
| `RequestScopeGuard.kt:54-55` — "reset my password", "unlock code", "change my pin" | The **refusal list**: phrases the AI scope guard rejects. This is the hostile-prompt defence, i.e. the opposite of a secret. |
| `AuditPresentation.kt:117` — `TOKENS.forEach { (token, replacement) …}` | The audit-timeline word-substitution table (`DO_NOT_DISTURB` → "Do Not Disturb"). |
| `res/xml/device_admin.xml` — `<limit-password/>`, `<reset-password/>`, `<expire-password/>` | Device-admin **policy declarations**, required by the DeviceAdminReceiver schema. |
| `KeystoreManager.getOrCreatePassphrase` | Generates the SQLCipher passphrase inside the Android Keystore at runtime. Nothing is hardcoded; the key never leaves the Keystore. |

`gradle.properties` contains only JVM args, Kotlin code style, configuration cache and
AndroidX flags. `local.properties` contains only `sdk.dir`.

## 3. The one flagged string, resolved

The pattern `sk-` matched `assets/rulepack.json`. Parsed the file: it is
`{"version", "rules"}`, 4,347 bytes, and its only tokens ≥32 characters are four
human-readable rule IDs — `caller-location-permission-mismatch`,
`caller-media-permission-mismatch`, `caller-package-visibility-mismatch`,
`caller-surveillance-capability-mismatch`. The match came from the substring `sk-` inside
`…risk-…`. **Not a secret.**

## 4. Pack signing private keys — present locally, never shipped, never committed

`keys/rulepack_private.pem` and `keys/threatpack_private.pem` exist on the build machine.

* `.gitignore:37` ignores `keys/` outright — confirmed with
  `git check-ignore -v`, which reports `.gitignore:37:keys/` for both files.
* `git status` shows zero entries under `keys/`.
* Neither appears in the APK (§1).

This is the correct posture: the packs are signed offline, the app verifies with the
bundled **public** keys, and the private keys stay on the build machine.

## 5. Dev/test tooling exclusion (§9 checklist)

| Item | In release APK? |
| --- | --- |
| Stitch API key / MCP endpoint credentials | no |
| `tools/stitch_mcp_proxy.py` | no — lives outside the app module, not a Gradle input, not referenced by any app source |
| Claude configuration | no |
| `temp/` documents, Phase reports, design references | no |
| Screenshots | no |
| Evaluation datasets (`PHASE10_INTENT_EVAL.json`) | no |
| Signing private keys of any kind | no |
| Internal research documents | no |
| Model file | no — developer-provisioned, not bundled |

Only source **comments** reference Stitch, attributing the design system's provenance.
They contain no credential, endpoint or key.

## 6. Item to note, not a secret

`Settings → Developer diagnostics` ships in the release build. It performs read-only
service-reachability checks and opens Android settings screens; it cannot create a finding
or an audit entry. It is a UX/surface concern rather than a secret-exposure concern and is
logged as LOW in the 12C register.
