# PHASE 12A — DEPENDENCY INVENTORY / SBOM

Source: `:app:dependencies --configuration releaseRuntimeClasspath` (resolved, release
runtime only — test-only dependencies are excluded by construction), cross-checked against
the native libraries and assets actually present inside the release APK.

All versions are **explicitly pinned**. There is no dynamic version (`+`, `latest.release`)
anywhere in the build, so the dependency set cannot drift between builds.

## Direct dependencies shipped in the release APK

| Package | Version | License | Source | Purpose | Shipped |
| --- | --- | --- | --- | --- | --- |
| `androidx.core:core-ktx` | 1.12.0 | Apache-2.0 | Google Maven | Core Android KTX; also supplies the auto-generated `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | yes |
| `androidx.appcompat:appcompat` | 1.6.1 | Apache-2.0 | Google Maven | AppCompat base | yes |
| `com.google.android.material:material` | 1.11.0 | Apache-2.0 | Google Maven | Material components / theming | yes |
| `androidx.constraintlayout:constraintlayout` | 2.1.4 | Apache-2.0 | Google Maven | Layout (legacy views) | yes |
| `androidx.compose:compose-bom` | 2024.10.01 | Apache-2.0 | Google Maven | Version alignment for Compose | BOM only |
| `androidx.compose.ui:ui`, `ui-graphics`, `ui-tooling-preview` | via BOM | Apache-2.0 | Google Maven | Compose UI | yes |
| `androidx.compose.material3:material3` | via BOM | Apache-2.0 | Google Maven | Material 3 — the Phase 11B design system | yes |
| `androidx.compose.material:material-icons-extended` | via BOM | Apache-2.0 | Google Maven | Icon set used across the redesigned UI | yes |
| `androidx.activity:activity-compose` | 1.9.3 | Apache-2.0 | Google Maven | Compose activity host, `BackHandler`, result launchers | yes |
| `androidx.lifecycle:lifecycle-runtime-ktx` | 2.8.7 | Apache-2.0 | Google Maven | Lifecycle scopes | yes |
| `androidx.lifecycle:lifecycle-runtime-compose` | 2.8.7 | Apache-2.0 | Google Maven | `collectAsStateWithLifecycle` | yes |
| `androidx.lifecycle:lifecycle-viewmodel-compose` | 2.8.7 | Apache-2.0 | Google Maven | ViewModel/Compose bridge | yes |
| `androidx.room:room-runtime` | 2.8.4 | Apache-2.0 | Google Maven | Encrypted store ORM (KSP compiler is build-time only) | yes |
| `androidx.sqlite:sqlite`, `sqlite-framework` | 2.6.2 | Apache-2.0 | Google Maven | SQLite support API, pinned to match Room **and** SQLCipher's `SupportSQLiteOpenHelper` API | yes |
| `net.zetetic:sqlcipher-android` | 4.17.0 (`@aar`) | Zetetic BSD-style | Maven Central | **Encryption at rest.** Ships `libsqlcipher.so` (bundles OpenSSL-derived crypto) | yes |
| `org.jetbrains.kotlin:kotlin-stdlib` | 2.2.20 | Apache-2.0 | Maven Central | Kotlin runtime | yes |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core` / `-android` | 1.8.1 | Apache-2.0 | Maven Central | Concurrency | yes |
| `org.jetbrains.kotlinx:kotlinx-serialization-json` | 1.9.0 | Apache-2.0 | Maven Central | Signed rulepack/threatpack schema, AI intent JSON validation | yes |
| `com.google.ai.edge.litertlm:litertlm-android` | **0.16.0** | Apache-2.0 | Google Maven | **On-device LLM runtime.** Ships `liblitertlm_jni.so` | yes |

## Transitive components observed in the APK

Not covered by the parent project's licence by assumption — listed because they are
independently present:

| Component | Origin | License | Note |
| --- | --- | --- | --- |
| `androidx.profileinstaller` (+ `ProfileInstallReceiver`, `ProfileInstallerInitializer`) | via Compose | Apache-2.0 | Adds an exported receiver protected by `DUMP` — see manifest audit §3 |
| `androidx.startup` (`InitializationProvider`) | via lifecycle/emoji2/profileinstaller | Apache-2.0 | Non-exported provider |
| `androidx.emoji2` (`EmojiCompatInitializer`) | via appcompat | Apache-2.0 | |
| `androidx.graphics.path` (`libandroidx.graphics.path.so`) | via Compose UI | Apache-2.0 | Third native library in the APK |
| `DebugProbesKt.bin` | `kotlinx-coroutines-core` | Apache-2.0 | Coroutine debug-probe metadata shipped by the artifact itself; inert at runtime unless debug probes are installed |

## Native libraries in the APK — complete list

```
arm64-v8a/liblitertlm_jni.so          x86_64/liblitertlm_jni.so
arm64-v8a/libsqlcipher.so             x86_64/libsqlcipher.so
arm64-v8a/libandroidx.graphics.path.so x86_64/libandroidx.graphics.path.so
```

Nothing else. No unexplained `.so`.

## Explicitly NOT present

No analytics, crash-reporting, advertising or attribution SDK is declared or shipped —
verified both in `build.gradle.kts` (no Firebase/Crashlytics/Sentry/Bugsnag/AppCenter/
AdMob/Facebook) and by dex string scan (**no `okhttp3`, `retrofit2` or
`com.google.firebase` classes exist in the APK at all**).

## Data assets shipped

| Asset | Size | Note |
| --- | --- | --- |
| `threatpack.json` + `.sig` + `_public.key` | 2.17 MB | Signed offline threat intelligence; see licence review |
| `rulepack.json` + `.sig` + `_public.key` | 5.5 KB | Signed contextual profile rules, authored in-project |

## Licence posture

Every code dependency is Apache-2.0 except SQLCipher (Zetetic BSD-style), which is
compatible with private, non-redistributed use. **Attribution obligations are not yet
discharged:** the About screen lists components by name but the build ships no
`NOTICE`/licence-text bundle. Acceptable for an owner-only private alpha; required before
any distribution. Carried as a 12C item.

The **Gemma model licence is the material open question** and is treated separately in
`PHASE12_MODEL_LICENCE_PROVENANCE.md` — it is deliberately *not* bundled in the APK.
