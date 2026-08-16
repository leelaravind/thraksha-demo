# PHASE 12A — RELEASE BUILD AUDIT

Date: 2026-08-15 · Module: `:app` · AGP 8.7.0 · Kotlin 2.2.20 · Gradle wrapper

## 1. Build type comparison

| Property | `debug` | `release` (candidate) |
| --- | --- | --- |
| `debuggable` | true (implicit) | **false** — confirmed in the installed package: `flags=[ HAS_CODE ALLOW_CLEAR_USER_DATA ]`, no `DEBUGGABLE` flag, and `aapt2 dump badging` emits no `application-debuggable` |
| Minification (R8) | off | **off — deliberate**, see §3 |
| Resource shrinking | off | off |
| Signing | Android debug key | Android debug key — **temporary**, see `PHASE12_RELEASE_ARTIFACT.md` |
| `QUERY_ALL_PACKAGES` | **present** (debug-only manifest overlay) | **absent** — see §5 and `PHASE12_MANIFEST_PERMISSION_AUDIT.md` |
| Permissions total | 13 | **12** |

## 2. Fixed build configuration

| Setting | Value |
| --- | --- |
| applicationId / package | `com.thraksha.guardian` |
| versionCode / versionName | `1` / `1.0` |
| compileSdk / targetSdk / minSdk | 36 / 36 / **26** (Android 8.0+) |
| Java / Kotlin target | 1.8 / 1.8 |
| Compose | enabled, compiler ext 1.5.8, BOM 2024.10.01 |
| `buildConfig` | enabled — **no custom `buildConfigField`s**; only the four AGP defaults ship (`DEBUG`, `APPLICATION_ID`, `BUILD_TYPE`, `VERSION_NAME`) |
| ABI filters | `arm64-v8a`, `x86_64` — verified in the APK: `native-code: 'arm64-v8a' 'x86_64'` |
| Packaging excludes | `/META-INF/{AL2.0,LGPL2.1}` |

The ABI restriction is intentional and pre-existing: LiteRT-LM ships `liblitertlm_jni.so`
for these two ABIs only. A 32-bit APK would install and then fail at model load. Both
targets are covered (S20 FE = arm64-v8a, `thraksha_do` AVD = x86_64).

**Consequence for private alpha:** 32-bit-only devices (armeabi-v7a) cannot install this
build at all. Recorded as a compatibility risk in 12C.

## 3. R8 / minification decision — deliberately OFF

`isMinifyEnabled = false` was retained for the release candidate. Reasoning:

The APK ships four surfaces that R8 cannot shrink safely without bespoke, individually
re-verified keep rules:

| Surface | Why R8 is risky |
| --- | --- |
| LiteRT-LM (`liblitertlm_jni.so`) | JNI entry points resolved by name from native code |
| SQLCipher (`libsqlcipher.so`) | JNI + `SupportSQLiteOpenHelper` reflection |
| Room | KSP-generated DAO implementations located reflectively |
| kotlinx.serialization | generated serializers looked up reflectively |

Enabling shrinking would invalidate the frozen Phase 11B verification in exchange for
code obfuscation, which is not a security control for an app the owner installs on their
own device from a file they were handed. The trade accepted is a larger APK (64.4 MB) and
non-obfuscated code.

**This is a documented deviation from typical release practice, not an oversight.** If the
build is ever prepared for real distribution, R8 should be enabled and every path above
re-verified against the Phase 11B suites.

## 4. Test hooks and developer flags in production code

Searched the production source set for test-only entry points:

| Symbol | Assessment |
| --- | --- |
| `DeviceScanEngine.resetForTest()`, `RuntimeObservationStore.resetForTest()`, `FoundationStatus.resetForTest()` | `internal` visibility, called only from `androidTest`. They reset in-memory state; they cannot fabricate a finding or bypass a gate. Retained — removing them would require touching frozen engines. |
| `DemoMode` | Not a test hook: it labels the authority Android actually grants (Device Owner vs not) and is shown to the user. |
| Developer diagnostics screen | **Ships in the release build.** Reachable via Settings → Developer diagnostics. It performs read-only service-reachability checks and Android settings deep links; it cannot produce a security finding or an audit entry. Flagged in 12C as a LOW risk (surface that a consumer build would normally hide). |
| Debug-only `QUERY_ALL_PACKAGES` overlay | Correctly scoped to `app/src/debug/AndroidManifest.xml`; verified absent from the release APK. |

No fake-failure hooks, no simulated-threat emitters, and no build-time feature flags were
found. The "Emit Test Threat Event" control noted in earlier phases is already gone.

## 5. Material finding — package visibility differs between build types

The generalized device scanner's reach is **not** the same in the release candidate.

| | debug build | release candidate |
| --- | --- | --- |
| Packages analysed on the S20 FE | **441** | **322** |
| Third-party user apps visible | yes | **no** (only the 2 declared `<queries>` packages) |
| Visibility scope reported | FULL | **REDUCED** |

Measured on the same device, minutes apart. The release build sees all 319 system
packages plus Guardian itself, VillainCaller and GoodCaller — the two packages named in
the main manifest's `<queries>` block.

The app reports this honestly and unprompted: the results screen states *"This build
cannot see every installed package, so these results cover only the apps Android exposes
to Thraksha."* `VisibilityScope.REDUCED` is detected and surfaced correctly, so **this is a
capability limitation, not a correctness defect** — nothing is misrepresented.

It is nonetheless the single most consequential difference between the demo build and the
private-alpha candidate, and it is carried into `PHASE12_MANIFEST_PERMISSION_AUDIT.md` §4
as an explicit owner decision.

## 6. Defect found and fixed during this audit

`OnDeviceIntentModel.unload()` existed and was test-verified, but **no production code
ever called it**, and the Application had no `onTrimMemory`/`onLowMemory` handler. After a
single Ask Thraksha request the process held ~2.9 GB — **37 % of the phone's 7.6 GB** — at
foreground-service priority (`oom cur=200`) indefinitely, including while backgrounded
(verified stable across 30 s in background). Android would therefore evict the user's other
apps rather than reclaim from Thraksha.

Minimal fix applied in `ThrakshaApplication`: release the model on genuine memory pressure
(`TRIM_MEMORY_RUNNING_LOW/RUNNING_CRITICAL/BACKGROUND/MODERATE/COMPLETE` and
`onLowMemory()`), reusing the existing, already-tested `unload()`. `UI_HIDDEN` and
`RUNNING_MODERATE` are excluded so a brief app switch does not cost a ~25 s reload.

Verified on the release build on-device:

| Point | TOTAL PSS |
| --- | --- |
| Idle after launch + scan | 114,749 KB |
| Model loaded, inference complete | 2,043,618 KB |
| After `am send-trim-memory RUNNING_CRITICAL` | 973,594 KB |
| Settled, idle | **138,487 KB** |
| Next request (transparent reload + inference) | 2,043,618 KB |

Log confirms `ThrakshaApplication: onTrimMemory(15): releasing the on-device model`.
Process survived, zero crashes, and the pending plan preview correctly survived the unload
(the plan is deterministic data and does not depend on the model).

This is the only production-code change made in 12A.
