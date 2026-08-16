# PHASE 12A — RELEASE ARTIFACT

## The candidate

| Field | Value |
| --- | --- |
| Filename | `thraksha-guardian-1.0-privatealpha-rc1.apk` |
| Built from | `app/build/outputs/apk/release/app-release.apk` |
| **SHA-256** | `80e7aaea2619257159b05d2f13c57f81dad10e44b502b748bfb1788e9bb337c6` |
| Size | 67,476,901 bytes (64 MiB), 955 entries |
| Build timestamp | 2026-08-15T22:45:06+0100 |
| applicationId | `com.thraksha.guardian` |
| versionCode / versionName | `1` / `1.0` |
| minSdk / targetSdk / compileSdk | 26 / 36 / 36 |
| ABIs | `arm64-v8a`, `x86_64` |
| **debuggable** | **false** |
| Minified (R8) | no — see `PHASE12_RELEASE_BUILD_AUDIT.md` §3 |
| Model bundled? | **no** — 0 `.litertlm` entries; the model is developer-provisioned |

Stored alongside this document in `temp/phase12/12a_release_hardening/`.

### Reproducing it

```
java -jar gradle/wrapper/gradle-wrapper.jar :app:assembleRelease
# -> app/build/outputs/apk/release/app-release.apk
```

Note the APK is **not** byte-for-byte reproducible across machines: it embeds a build
timestamp and the signature is produced with the local debug keystore. The *inputs* are
pinned (dependency versions are all explicit, no dynamic `+` or `latest.release`), so a
rebuild from the same commit yields a functionally identical APK with a different digest.

## Signing identity — TEMPORARY, and a real limitation

| Field | Value |
| --- | --- |
| Signer | `C=US, O=Android, CN=Android Debug` |
| Certificate SHA-256 | `2a7c7cf3fa6214329168e2037d8a6ecdd92f5e7ab981f439d291ebccac3a19d4` |
| Certificate SHA-1 | `0d56514df36266a7fdea1311648552560e0140f9` |
| Key | RSA 2048 |
| Schemes | v2 only (v1 false, v3 false) — sufficient for minSdk 26 |

**This project has no release signing identity.** There is no `signingConfig` in
`app/build.gradle.kts` beyond the one added here, no keystore under the project, and no
`keystore.properties`. Phase 12 explicitly must not invent or embed signing credentials —
key generation and custody are the owner's decision, not the build's — so the candidate is
signed with Android's standard **debug** key and `build.gradle.kts` records that as
temporary.

### What this costs, stated plainly

1. **The debug key is not secret.** Its private key ships with the Android SDK on every
   developer machine. Anyone can produce an APK that this device will accept as an update
   to this install. That is acceptable only because distribution is owner-to-own-device.
2. **No upgrade path to a real key.** Android refuses an update whose signer differs. A
   future properly-signed build **cannot** update an install made from this APK — it must
   be uninstalled first, which destroys the encrypted database and the audit chain.
3. **Not distributable.** This APK must not be shared, published, or side-loaded by anyone
   other than the owner.

### What must happen before any wider distribution

Generate a release keystore under the owner's control, store the credentials outside the
repository (e.g. `keystore.properties`, git-ignored), wire a real `signingConfig`, and
re-cut the candidate. That is a deliberate, owner-owned step and is **out of scope for
Phase 12**; it is carried as a risk in the 12C register.

## Contents verified present

| Component | Detail |
| --- | --- |
| Native libraries | `liblitertlm_jni.so`, `libsqlcipher.so`, `libandroidx.graphics.path.so` — for both ABIs, no others |
| Signed assets | `rulepack.json/.sig/_public.key`, `threatpack.json/.sig/_public.key` |
| Key material | **public keys only** — both `_public.key` files are X.509 `SubjectPublicKeyInfo` RSA public keys (`MIIBIjANBgkqhkiG9w0BAQEF…`). No private key of any kind is present. |
| Dex | `classes.dex`, `classes2.dex`, `classes3.dex` |

## Contents verified absent

Model file · Stitch/MCP/Claude artefacts · MCP proxy · temp docs · screenshots ·
evaluation datasets · signing private keys · pack signing private keys · HTTP stack ·
analytics/crash-reporting SDK. Evidence in `PHASE12_SECRET_AUDIT.md`.
