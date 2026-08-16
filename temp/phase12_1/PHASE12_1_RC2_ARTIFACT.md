# PHASE 12.1 — RC2 ARTIFACT

Guide reference: §§20–22.

## 1. The candidate

| Field | Value |
| --- | --- |
| Filename | `thraksha-guardian-1.0-privatealpha-rc2.apk` |
| Built from | `app/build/outputs/apk/release/app-release.apk` |
| **SHA-256** | `93c15f227bc724ef02eb5cb79cabcac308f2dd53aa531a19d0665da8ed05c7de` |
| Size | 67,476,953 bytes (955 entries) |
| Build date | 2026-08-16 03:53 BST |
| applicationId | `com.thraksha.guardian` |
| versionCode / versionName | **`2`** / **`1.0-privatealpha-rc2`** |
| minSdk / targetSdk / compileSdk | 26 / 36 / 36 |
| ABIs | `arm64-v8a`, `x86_64` |
| **debuggable** | **false** |
| Minified (R8) | no — unchanged from RC1 (Phase 12A §5 rationale still applies) |
| Model bundled? | **no** — 0 `.litertlm` entries; developer-provisioned |

Stored in `temp/phase12_1/artifact/`. Contents listing: `artifact/rc2_apk_contents.txt`.

Built from a **clean** tree (`gradle clean` immediately beforehand).

### Reproducing it

```
java -jar gradle/wrapper/gradle-wrapper.jar clean
java -jar gradle/wrapper/gradle-wrapper.jar :app:assembleRelease
```

Requires `keystore.properties` (untracked) or the `THRAKSHA_*` environment variables.
Without them the build **fails** rather than falling back to debug signing.

Not byte-for-byte reproducible: the APK embeds a build timestamp and a fresh signature
salt. Inputs are pinned (no dynamic dependency versions).

## 2. Versioning scheme (§20)

| Field | RC1 | RC2 | Rationale |
| --- | --- | --- | --- |
| `versionCode` | 1 | **2** | The OS update ordinal. Must increase for a later build to install over an earlier one. Every subsequent private-alpha build increments it. |
| `versionName` | `1.0` | **`1.0-privatealpha-rc2`** | Human-facing only (Settings → About renders `Version <name> (<code>)`). Carries the full candidate identity so a field phone is identifiable from its About screen alone, and matches the distributed filename exactly. |

Note RC2 **cannot** update an RC1 install — the signer changed. RC1 had to be uninstalled.
From RC2 onward, `versionCode` progression makes in-place updates work.

## 3. Signer — verified independently (§16)

`apksigner verify --print-certs --verbose`:

```
Verifies
Verified using v1 scheme (JAR signing): false
Verified using v2 scheme (APK Signature Scheme v2): true
Verified using v3 scheme (APK Signature Scheme v3): true
Verified using v3.1 scheme: false
Verified using v4 scheme: false
Number of signers: 1
Signer #1 certificate DN: CN=Thraksha Guardian, OU=Private Alpha, O=Thraksha,
                          L=Tirumula, ST=Andhra Pradesh, C=IN
Signer #1 certificate SHA-256 digest: 0d96977d15ce5b17c4167a03f70fc72401f580a8d4f2258e059fa113516ac928
Signer #1 certificate SHA-1   digest: c7bafcb654883e0b67758471d1ee129e6e53b2d7
Signer #1 key algorithm: RSA
Signer #1 key size (bits): 4096
Signer #1 public key SHA-256 digest: 8565b7ba3c25db2a32a67bab5d5eda9fef3e8cd41366961982970e4f29d9be8d
```

**Signer is NOT `CN=Android Debug`.** ✅ Guide §16 satisfied.

For contrast, RC1: `C=US, O=Android, CN=Android Debug`, SHA-256
`2a7c7cf3fa6214329168e2037d8a6ecdd92f5e7ab981f439d291ebccac3a19d4`, v2 only.

On-device confirmation after install: `apkSigningVersion=3`.

## 4. debuggable = false — two independent checks

1. `aapt2 dump badging` emits **no** `application-debuggable` line (0 occurrences).
2. `aapt2 dump xmltree --file AndroidManifest.xml` shows **no** `android:debuggable`
   attribute at all.
3. On-device `dumpsys package` flags: `[ HAS_CODE ALLOW_CLEAR_USER_DATA ]` — no
   `DEBUGGABLE`.

## 5. Manifest — the Task A fix present in the shipped binary

```
uses-permission: name='android.permission.QUERY_ALL_PACKAGES'
package: name='com.thraksha.guardian' versionCode='2' versionName='1.0-privatealpha-rc2'
native-code: 'arm64-v8a' 'x86_64'
```

Confirmed held at runtime: `dumpsys package com.thraksha.guardian` reports the permission
on the installed RC2.

## 6. APK content audit (§22) — all clean

Scan of the entry listing, counts:

| Pattern | Hits |
| --- | --- |
| `keystore` · `.jks` · `.p12` · `.pem` | 0 · 0 · 0 · 0 |
| `password` · `secret` | 0 · 0 |
| `stitch` · `.mcp` · `mcp.json` · `claude` | 0 · 0 · 0 · 0 |
| `/temp/` · `screenshot` · `eval` · `.md` | 0 · 0 · 0 · 0 |
| `androidTest` (test hooks) | 0 |
| `.litertlm` (model weights) | 0 |

Deep **binary** scan of the whole APK (not just entry names):

| Marker | Hits |
| --- | --- |
| `BEGIN PRIVATE KEY` | 0 |
| `BEGIN RSA PRIVATE KEY` | 0 |
| `BEGIN ENCRYPTED PRIVATE KEY` | 0 |
| `thraksha-private-alpha` (the key alias) | 0 |
| `keystore.properties` | 0 |
| `storePassword` / `keyPassword` | 0 / 0 |

### Present and correct

| Component | Detail |
| --- | --- |
| Native libraries | `liblitertlm_jni.so`, `libsqlcipher.so`, `libandroidx.graphics.path.so` — both ABIs, nothing else |
| Signed pack assets | `rulepack.json/.sig/_public.key`, `threatpack.json/.sig/_public.key` |
| Key material | **public keys only** — both `_public.key` files begin `MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8A`, the standard X.509 `SubjectPublicKeyInfo` RSA public-key prefix. No private key of any kind. |

**No signing secret, development artefact or test hook is present in the shipped APK.**
