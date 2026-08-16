# THRAKSHA GUARDIAN — CURRENT PROJECT AUDIT

**Audit date:** 2026-08-13
**Audited commit:** `bad1633` — *"Phase 2 (9/8): dashboard wiring — privilege level, execution mode, live audit feed, test-emit; admin checks consolidated to SecurityCapability"* (2026-07-22 16:16 +0100)
**Working tree:** clean except untracked `temp/` (contains only `audit_instructions.md`)
**Method:** read-only static inspection of every tracked file (80 files; 21 main Kotlin sources, 4 unit tests, 4 instrumented tests) plus reference/call-path tracing via repo-wide grep. **No application code, Gradle, manifest, resource, or asset file was modified.** No build was run; no device was attached.
**Scope note:** every claim below is anchored to a file path and, where useful, a line number. Anything not provable from source is explicitly marked **UNCLEAR / NEEDS RUNTIME TESTING**.

---

## 1. Executive Summary

Thraksha Guardian is a **single-module Android app** (`:app`, `com.thraksha.guardian`) that today contains a **genuinely solid, tested security *spine* and essentially zero security *sensing* or *acting*.**

What is real and works:

* An **encrypted local store** — Room over SQLCipher, passphrase generated once and AES-GCM-wrapped by a hardware-backed AndroidKeyStore key. Verified on-device by `EncryptedStoreTest` including a proof that the DB file has no plaintext SQLite header.
* A **hash-chained, append-only audit log** with tamper detection, covered by both an off-device unit test and an on-device instrumented test that corrupts a row via raw SQL and proves verification fails.
* A **signed rulepack** — 12 rule declarations, RSA-2048/SHA-256 signature over the exact asset bytes, verifier that rejects a single flipped byte, plus `.gitattributes` protecting the bytes from line-ending conversion.
* A **process-wide event bus** and an `Application`-level subscriber that writes bus events into the audit chain.
* A **Compose dashboard** that renders privilege level, execution mode, and a live `Flow`-backed feed of the audit chain.

What is **not** there — and this is the crux for the demo:

* **There is not a single real detector in the app.** The only producer of `SecurityEvent` anywhere in the codebase is a debug button labelled *"Emit Test Threat Event"* (`DashboardScreen.kt:203-219`). The entire detect → event → audit → UI pipeline is real plumbing driven by a manual tap.
* **There is not a single enforcement call in the app.** A repo-wide grep for `setPackagesSuspended|setPermissionGrantState|setApplicationHidden|setUserRestriction|lockNow|wipeData|setUninstallBlocked` returns **zero hits**. `ThrakshaDeviceAdminReceiver` only logs and Toasts. Device Owner capability is *detected* (`SecurityCapability`) and *displayed*, never *used*.
* **The signed rulepack is inert.** `RulepackLoader` has **zero production callers** — its only call site in the whole repo is `FoundationInstrumentedTest.kt:48`. There is no rule engine, no evaluator, no mapping from a `Rule` to a detector or a decision.
* **`ExecutionMode` gates nothing.** It is persisted, read back, and printed on the dashboard. No code branches on it.
* **`SecureLogger` has zero callers anywhere** — implemented, wired to the DB, never invoked.
* **The VPN monitors nothing** and, worse, statically has no path that forwards traffic to the network (see §8) — it reads packets from the TUN and writes them straight back into the same TUN.
* **The app cannot even see other apps.** The manifest has **no `<queries>` element and no `QUERY_ALL_PACKAGES`** (`AndroidManifest.xml`, 0 matches for `queries`). Under Android 11+ package-visibility filtering with `targetSdk 36`, the app cannot enumerate `GoodCaller`/`VillainCaller` at all as written.
* **The decoy apps do not exist in this repository.** `settings.gradle.kts` includes only `:app`.

**Bottom line for the demo conversion:** the expensive, credibility-carrying part (encrypted tamper-evident evidence trail, signed policy artefact, privilege detection) is **built and tested — reuse it wholesale.** The demo-visible part (behaviour sensing, rule evaluation, mode-gated action, dashboard states, decoys) is **almost entirely new work**, but it is *additive* — it plugs into existing seams (`SecurityEventBus`, `AuditLog`, `ConfigStore`, `SecurityCapability`) rather than requiring a rewrite. There is very little to tear down: the codebase is small, clean, and has almost no dead weight beyond three unused components and two template tests.

The **hardest honesty problem** the demo faces is documented in §13: *Android does not let a normal app observe another app's photo/media access or another app's reading of the installed-app list.* Those two headline demo scenarios cannot be detected as live events by any supported API. They can be handled truthfully (detect the *capability*, detect the *outbound exfiltration*, or have the decoy self-report through a channel the UI labels as instrumented) — but they must not be presented as OS-level interception.

---

## 2. Repository / Module Map

Single Gradle module. `settings.gradle.kts` → `rootProject.name = "Thraksha Guardian"`, `include(":app")`.

```
ThrakshaGuardian/
├── build.gradle.kts                 AGP 8.7.0, Kotlin 2.2.20, KSP 2.2.20-2.0.4, kotlin-serialization
├── settings.gradle.kts              only :app
├── gradle.properties                configuration-cache=true, useAndroidX, enableJetifier=true
├── gradle/
│   ├── libs.versions.toml           UNUSED (zero `libs.` references in any .kts) and version-drifted
│   ├── wrapper/                     Gradle 9.4.1, SHA-256 pinned
│   └── gradle-daemon-jvm.properties toolchainVersion=21 (foojay)
├── .gitattributes                   `-text` on the 3 rulepack assets (protects the signature)
├── .gitignore                       hardened: keys/, *.pem, *.jks, .idea/, build artefacts
├── keys/rulepack_private.pem        LOCAL ONLY, gitignored, NOT in assets — correct
├── SCAN_REPORT.md                   HISTORICAL (2026-07-21) — describes the pre-Phase-1 architecture
├── docs/PROJECT_STATUS_REPORT.md    HISTORICAL (2026-05-28) — describes the deleted remote architecture
├── temp/audit_instructions.md       untracked, this task's brief
└── app/
    ├── build.gradle.kts
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── assets/              rulepack.json (2384 B), rulepack.sig (344 B), rulepack_public.key (392 B)
        │   ├── res/                 values/{colors,strings,themes}.xml, xml/{accessibility_service_config,
        │   │                        device_admin,backup_rules,data_extraction_rules}.xml, launcher icons
        │   └── java/com/thraksha/guardian/
        │       ├── ThrakshaApplication.kt          process init + bus→audit wiring
        │       ├── MainActivity.kt                 splash → permissions → start foreground service
        │       ├── data/
        │       │   ├── audit/{AuditHasher,AuditLog}.kt
        │       │   ├── config/{ConfigStore,ExecutionMode}.kt
        │       │   ├── crypto/KeystoreManager.kt
        │       │   ├── db/{Daos,DatabaseProvider,Entities,ThrakshaDatabase}.kt
        │       │   └── log/SecureLogger.kt          ← ZERO CALLERS
        │       ├── security/
        │       │   ├── SecurityCapability.kt        privilege detection (used)
        │       │   ├── ThrakshaDeviceAdminReceiver.kt  status/log only
        │       │   ├── events/{SecurityEvent,SecurityEventBus}.kt
        │       │   └── rulepack/{RulepackLoader,RulepackModels,RulepackVerifier}.kt  ← Loader: ZERO PRODUCTION CALLERS
        │       ├── services/
        │       │   ├── CompanionForegroundService.kt   notification-only keepalive shell
        │       │   ├── ThrakshaVpnService.kt           TUN + echo loop, no inspection
        │       │   ├── ThrakshaAccessibilityService.kt automation API, no security use
        │       │   └── ThrakshaNotificationListener.kt parse + log, 5 stubs
        │       └── ui/
        │           ├── components/{BrandingComponents,StatusBarComponent,WhitelistingDialog}.kt
        │           ├── screens/{DashboardScreen,SplashScreen}.kt
        │           └── theme/{Color,Theme,Type}.kt
        ├── test/java/.../            AuditLogTest, RulepackVerifierTest, SecurityEventBusTest, ExampleUnitTest
        └── androidTest/java/.../     AuditLogInstrumentedTest, EncryptedStoreTest,
                                      FoundationInstrumentedTest, ExampleInstrumentedTest
```

### Build configuration (`app/build.gradle.kts`)

| Setting | Value | Note |
|---|---|---|
| `namespace` / `applicationId` | `com.thraksha.guardian` | lines 10, 14 |
| `compileSdk` / `targetSdk` | 36 / 36 | lines 11, 16 |
| `minSdk` | 26 | line 15 |
| Compose | BOM 2024.10.01, compose plugin 2.2.20 | lines 4, 62 |
| Room | 2.8.4 via KSP | lines 89-90 |
| SQLCipher | `net.zetetic:sqlcipher-android:4.17.0@aar` | line 97 |
| androidx.sqlite | pinned 2.6.2 (Room ↔ SQLCipher API match) | lines 93-94 |
| Serialization | kotlinx-serialization-json 1.9.0 | line 100 |
| `jvmTarget` | 1.8 | line 42 (with a Java-21 daemon toolchain) |

**Build-config smells (non-blocking, all pre-existing):**
* `composeOptions { kotlinCompilerExtensionVersion = "1.5.8" }` (line 51) is obsolete and ignored under the Kotlin 2.x Compose plugin.
* `AndroidManifest.xml:4` still carries the deprecated `package="com.thraksha.guardian"` attribute alongside AGP 8.7's `namespace`. It matches the namespace, and the project demonstrably built with it present, so it is a deprecation warning rather than a break.
* `android.enableJetifier=true` (`gradle.properties`) is unnecessary — no legacy support-library dependencies remain.
* `gradle/libs.versions.toml` is orphaned (zero `libs.` references) and drifted (e.g. `composeBom = "2026.02.01"` vs the hardcoded `2024.10.01`).
* `release { isMinifyEnabled = false }` — no shrinking; `proguard-rules.pro` is the untouched template.

**Build status:** `app/build/outputs/apk/debug/app-debug.apk` exists, 28,499,903 bytes, timestamped 2026-07-22 16:17 — one minute after the HEAD commit. That is strong evidence HEAD compiles and packages. **UNCLEAR / NEEDS RUNTIME TESTING:** not re-verified in this run (building was out of scope).

---

## 3. Current Architecture (actual runtime flow)

This is what the code actually does, traced call-by-call. It is materially shorter than the reference flow in the brief because **three of the nine stages do not exist**.

### 3.1 Process start

`ThrakshaApplication.onCreate()` (`ThrakshaApplication.kt:26-32`):

1. `System.loadLibrary("sqlcipher")` (line 29) — must precede any DB open.
2. `seedConfigDefaults()` (34-38) → `appScope.launch { ConfigStore(this).ensureDefaults() }`, wrapped in `runCatching` (failures are silently swallowed).
   → `ConfigStore.<init>` → `DatabaseProvider.get(ctx)` → `KeystoreManager.getOrCreatePassphrase(ctx)` → `SupportOpenHelperFactory(passphrase)` → `Room.databaseBuilder(...).build()`.
   → `ensureDefaults()` (`ConfigStore.kt:17-24`) writes `execution_mode=OBSERVE` and tiers 60/80/95/99 **only if the config table is empty**.
3. `wireAuditLogToEventBus()` (40-61) — constructs `AuditLog(DatabaseProvider.get(this).auditDao())` then launches a collector on `SecurityEventBus.events` mapping:
   * `ThreatDetected` → `auditLog.append(type="THREAT", details="<signalId> [<severity>] <details>", tier=confidence)`
   * `ActionTaken` → `auditLog.append(type="ACTION", details="<action> -> <target>: <result>", tier=0)`
   * `ModeChanged` → **discarded** (`Unit`, line 56)

### 3.2 UI start

`MainActivity.onCreate` (`MainActivity.kt:45-62`) → `setContent { ThrakshaTheme { ... } }` → `SplashScreen` (2.8 s of animation, `SplashScreen.kt:47-53`) → `isSplashFinished = true` → `MainContent()`.

`MainContent` (64-93): `Scaffold(topBar = StatusBarComponent)` + `DashboardScreen()`, and `LaunchedEffect(Unit) { checkSamsungAndStart() }`.

`checkSamsungAndStart` (95-105) → if Samsung **and** not battery-whitelisted → `showSamsungDialog`; **both** dialog branches (confirm at 78-84, dismiss at 85-88) call `handlePermissionsAndStart()`, so the start-up chain always resumes. → `handlePermissionsAndStart` (121-137) requests `POST_NOTIFICATIONS` on API 33+ → `startCompanionService` (139-151) → `startForegroundService(CompanionForegroundService)`.

### 3.3 The foreground service

`CompanionForegroundService` (`CompanionForegroundService.kt`) does exactly one thing: `startAsForeground()` (35-58) creates channel `thraksha_guardian_service` and posts notification 1001 *"Thraksha Guardian Active / Security monitoring is running."*. `onStartCommand` returns `START_STICKY` and nothing else (line 33). `onTimeout` (69-72) re-posts the notification for the Android 15 FGS timeout.

Its own KDoc is accurate: *"Phase 3 will attach the security-monitoring workload to this shell."* (line 22). **The notification text is currently a claim the app does not fulfil.**

### 3.4 Detection → event → audit → UI

```
[ NO DETECTOR EXISTS ]
          │
          │  the only producer in the repo:
          ▼
DashboardScreen.kt:203-219  "Emit Test Threat Event" button
          │  SecurityEventBus.tryEmit(ThreatDetected("high-risk-install", HIGH, "manual test event", 90))
          ▼
SecurityEventBus (replay=0, extraBufferCapacity=128)
          ▼
ThrakshaApplication collector (ThrakshaApplication.kt:43-59)
          ▼
AuditLog.append()  → dao.last() → id = prev+1, prevHash = prev.hash ?: "GENESIS"
                   → AuditHasher.hash(id|ts|type|details|prevHash)  [SHA-256, lowercase hex]
                   → dao.insert(AuditEntity)
          ▼
SQLCipher-encrypted `audit_log` table
          ▼
AuditDao.observeRecent(10) : Flow<List<AuditEntity>>   (Daos.kt:49)
          ▼
DashboardScreen.kt:103  collectAsState → "RECENT SECURITY EVENTS" list (lines 369-399)
```

Alongside it, two one-shot `produceState` reads (`DashboardScreen.kt:104-111`): `SecurityCapability.currentLevel(context)` and `ConfigStore(context).getExecutionMode()`.

### 3.5 What the reference flow calls for, versus what exists

| Reference stage | Reality |
|---|---|
| App startup | ✅ `ThrakshaApplication` → `MainActivity` |
| Initialization | ✅ SQLCipher lib, config defaults, bus→audit wiring |
| Mode detection | 🟡 `SecurityCapability.currentLevel()` — detects, displays, **never gates** |
| Services | 🟡 four service classes; one starts automatically and does nothing; three are user-enabled and do no security work |
| Monitoring / detection | ❌ **does not exist** |
| Event generation | 🟡 bus exists, one manual producer |
| Rules / evaluation | ❌ **does not exist** (rulepack loads only in a test) |
| Response / action | ❌ **does not exist** |
| Persistence / audit | ✅ encrypted, hash-chained, tested |
| UI update | ✅ live `Flow` feed + status rows |

---

## 4. Existing Feature Inventory

Classification key: **WORKING** = substantially implemented and wired; **PARTIAL** = real implementation, incomplete/disconnected; **STUB** = structure without functionality; **MISSING** = absent; **UNCLEAR** = needs runtime testing.

| # | Subsystem | Status | Evidence |
|---|---|---|---|
| 1 | Device Owner detection | **WORKING** | `SecurityCapability.currentLevel()` `SecurityCapability.kt:16-24`; `dpm.isDeviceOwnerApp` / `dpm.isAdminActive`. Used at `DashboardScreen.kt:105, 295, 351`. |
| 2 | Device Admin receiver | **PARTIAL** | `ThrakshaDeviceAdminReceiver.kt` registered `AndroidManifest.xml:100-114` with `@xml/device_admin`. All 6 overrides only `Log`/`Toast` (44-87). Provides `getDevicePolicyManager`/`getComponentName` helpers used by `SecurityCapability`. |
| 3 | DevicePolicyManager actions | **MISSING** | Repo-wide grep for `setPackagesSuspended\|setPermissionGrantState\|setApplicationHidden\|setUserRestriction\|lockNow\|wipeData\|setUninstallBlocked\|setGlobalSetting\|reboot(` → **0 hits in `app/src`**. Only status queries exist. |
| 4 | Permission revocation | **MISSING** | No `setPermissionGrantState` anywhere. |
| 5 | App suspension / freeze / quarantine | **MISSING** | No `setPackagesSuspended` / `setApplicationHidden` anywhere. |
| 6 | VPN / VpnService | **PARTIAL** | `ThrakshaVpnService.kt` establishes a TUN (83-98) and runs an echo loop (123-152). Zero inspection. See §8 for the traffic-blackhole risk. |
| 7 | Per-app network identification / control | **MISSING** | No `addAllowedApplication` / `addDisallowedApplication` / `getConnectionOwnerUid` anywhere. |
| 8 | Panic / lockdown mode | **MISSING** | Only the string `LOCKDOWN` as an unused `ExecutionMode` enum constant (`ExecutionMode.kt:18`) and a Toast reading *"Lockdown ready"* (`DashboardScreen.kt:298`). No panic UI, no panic action, no receiver, no tile. |
| 9 | Whitelisting / trusted apps | **MISSING** | `WhitelistingDialog.kt` is **battery-optimisation** whitelisting for Samsung, not app trust. `ConfigStore` has a generic `feature.*` flag API but no app allow-list. |
| 10 | Security event / event-bus architecture | **WORKING** | `SecurityEventBus.kt` (`MutableSharedFlow`, replay 0, buffer 128); `SecurityEvent.kt` sealed interface with 3 subtypes; unit-tested (`SecurityEventBusTest.kt`); consumed in `ThrakshaApplication.kt:43`. |
| 11 | Rulepack / rule engine | **PARTIAL (pack) / MISSING (engine)** | Pack + verifier + loader are real and tested (`RulepackVerifierTest.kt`, `FoundationInstrumentedTest.kt:46-51`). **`RulepackLoader` has zero production callers.** No evaluator, no rule→detector binding, all 12 `params` are `{}`. |
| 12 | Behaviour monitoring | **MISSING** | No package enumeration, no install/uninstall receiver, no permission polling, no usage stats, no traffic analysis. |
| 13 | App categorisation / baselines | **MISSING** | No app-type model, no category constants, no baseline storage. `rulepack.json` `category` fields describe *rule* categories, not app types. |
| 14 | Audit logging | **WORKING** | `AuditLog.kt` append-only (no update/delete API); `audit_log` table (`Entities.kt:28-37`); live `Flow` feed (`Daos.kt:49`). |
| 15 | Tamper-resistant / hash-chained logging | **WORKING (evident, not resistant)** | `AuditHasher.kt`: `SHA-256(id\|ts\|type\|details\|prevHash)`, genesis `"GENESIS"`. `AuditLog.verify()` is pure/testable. Proven by `AuditLogTest` (off-device) and `AuditLogInstrumentedTest` (on-device, real `UPDATE ... SET details='HACKED'` breaks it). **Caveat:** `verifyChain()` has **no production caller** — nothing in the app ever checks integrity or surfaces it. |
| 16 | SQLCipher / encrypted storage | **WORKING** | `DatabaseProvider.kt:27-33` + `System.loadLibrary("sqlcipher")`. `EncryptedStoreTest.roundTrip...` asserts the on-disk file does **not** start with `SQLite format 3`. |
| 17 | Android Keystore usage | **WORKING** | `KeystoreManager.kt`: 32 random bytes, AES-256-GCM wrap under alias `thraksha_db_master_key`, StrongBox attempted with TEE fallback (87-95), only IV+ciphertext in SharedPreferences. Passphrase stability asserted (`EncryptedStoreTest.kt:36-42`). |
| 18 | Dashboard / security status UI | **PARTIAL** | `DashboardScreen.kt` — pulsing shield, `"FOUNDATION ACTIVE"` banner, 3 status rows, 5 debug buttons, live audit feed. It is a **developer console**, not a product dashboard. |
| 19 | Threat cards / alerts | **MISSING** | `ActivityItem` (`DashboardScreen.kt:417-466`) is a generic list row keyed only on `type == "THREAT"` for icon/colour. No severity styling, no action affordance, no notification alert, no detail view. |
| 20 | Mode detection & UI representation | **PARTIAL** | Two independent axes exist and are both merely printed as text: `PrivilegeLevel` (`DEVICE_OWNER`/`DEVICE_ADMIN`/`NORMAL`) and `ExecutionMode` (`OBSERVE`/`GUIDED`/`AUTO_DEFEND`/`LOCKDOWN`). Third row *"Enforcement: Available/Unavailable"* (`DashboardScreen.kt:179-182`) is the closest thing to a FULL POWER indicator. No visual mode treatment. |
| 21 | Assistant / action execution components | **PARTIAL, disconnected** | `ThrakshaAccessibilityService.executeAction(AutomationAction)` (141-166) is complete and exhaustive over a 6-case sealed class — and has **zero callers**. `performSwipe`, `clickNodeWithText`, `performGlobalBack/Home/Recents`, `findClickableNodes` are reachable only through it. Only `performTap` has a live caller: the debug button at `DashboardScreen.kt:232`. |
| 22 | Tests & demo/test utilities | **PARTIAL** | 6 real tests + 2 templates (details in §4.1). The only "demo utility" is the test-emit button. |

### 4.1 Test inventory

| File | Kind | What it actually proves |
|---|---|---|
| `test/.../AuditLogTest.kt` | unit | Valid chain verifies; mutating `details` while leaving `hash` stale breaks it. |
| `test/.../RulepackVerifierTest.kt` | unit | The **real bundled asset bytes** verify against the bundled public key, parse to 12 unique enabled rules, and a single flipped byte fails verification. |
| `test/.../SecurityEventBusTest.kt` | unit | Emit→collect round-trip (race-free via `CoroutineStart.UNDISPATCHED`); event field carriage. |
| `test/.../ExampleUnitTest.kt` | template | `2+2=4`. Dead weight. |
| `androidTest/.../EncryptedStoreTest.kt` | instrumented | Keystore passphrase stable & 32 bytes; write→close→reopen→read; DB file is not plaintext SQLite. |
| `androidTest/.../AuditLogInstrumentedTest.kt` | instrumented | 3-entry chain verifies on the real encrypted DB; raw `UPDATE` breaks verification. |
| `androidTest/.../FoundationInstrumentedTest.kt` | instrumented | Config defaults seed/read-back; `canEnforce ⟺ DEVICE_OWNER`; **the only place `RulepackLoader.load()` is ever called.** |
| `androidTest/.../ExampleInstrumentedTest.kt` | template | Package-name assertion. Dead weight. |

**UNCLEAR / NEEDS RUNTIME TESTING:** whether the suite currently passes. The instrumented tests need a device; the unit tests should pass off-device but were not executed.

---

## 5. Security Infrastructure Audit

### 5.1 Cryptography — `KeystoreManager.kt` — **WORKING**

Design is sound: the SQLCipher passphrase is 32 bytes from `SecureRandom` (line 51), wrapped with `AES/GCM/NoPadding` (61-65) under a Keystore key that never leaves secure hardware; only Base64 IV + ciphertext are persisted in plain SharedPreferences (53-56), which is correct — they are useless without the Keystore key. StrongBox is attempted on API 28+ and falls back to TEE on any exception (87-95). No user-auth binding, deliberately, so a background service can open the DB.

Observations (not defects for a demo):
* The unwrapped passphrase is returned as a `ByteArray` and never zeroed. It lives as long as `SupportOpenHelperFactory` holds it. Standard practice, worth knowing.
* If the Keystore key is ever invalidated (factory reset, some backup/restore paths, key corruption), `unwrap` throws and **`DatabaseProvider.build()` will propagate the exception**, taking down whatever called it. In `ThrakshaApplication` the config seed is inside `runCatching` (line 36) — but `wireAuditLogToEventBus()` at line 41 calls `DatabaseProvider.get(this)` **outside** any `runCatching`, so a Keystore failure there crashes the app at startup. **Demo-stability risk, MEDIUM.**

### 5.2 Encrypted storage — **WORKING**

`ThrakshaDatabase` v1, `exportSchema = false`, three entities: `app_log`, `config`, `audit_log`. `DatabaseProvider` is a correct double-checked singleton keyed on `applicationContext`. No migrations defined and no `fallbackToDestructiveMigration()` — a future schema bump without a migration will throw at open. Fine at v1; relevant the moment the demo adds tables.

### 5.3 Audit chain — **WORKING, with two caveats**

* **Tamper-*evident*, not tamper-*proof*.** An attacker who can write to the DB can recompute the entire chain (the hash uses no secret — no HMAC, no signature, no external anchor). This is a legitimate and defensible design for a demo, but should be described accurately: *"any edit to a recorded row is detectable"*, not *"the log cannot be altered"*.
* **`verifyChain()` is never called in production.** Integrity is proven only in tests. For an investor demo this is a free, high-value win: one button, one status row.
* **Concurrency:** `append()` (`AuditLog.kt:15-28`) does read-then-write without a lock. Two concurrent appends can compute the same `id`, and `@Insert` on a non-autogenerated `@PrimaryKey` defaults to `ABORT` → `SQLiteConstraintException`. Today only the single `Application` collector calls it, so it is serialised in practice. **The moment a second writer exists (a detector, a panic action), this becomes a real crash risk.** Severity MEDIUM, cost to fix trivial (a `Mutex`).

### 5.4 Signed rulepack — **verifier WORKING / consumption MISSING**

`RulepackVerifier.verify()` (`RulepackVerifier.kt:16-26`) — `SHA256withRSA` over the raw bytes, `java.util.Base64` (API 26+, fine at `minSdk 26`), `runCatching{}.getOrDefault(false)` so malformed input fails closed. `RulepackLoader.load()` (`RulepackLoader.kt:13-23`) reads the three assets and `require(...)`s verification before parsing — callers get a verified `Rulepack` or an exception, never unverified rules. Correct fail-closed design.

`.gitattributes` marks all three assets `-text`, which is a genuinely thoughtful detail — CRLF conversion on checkout would silently break signature verification on Windows.

`keys/rulepack_private.pem` exists on disk, is covered by `.gitignore` (`keys/` and `*.pem`), confirmed absent from `git ls-files`, and is **not** in `app/src/main/assets/` (assets contains only the 3 public artefacts). Correct.

**But:** the 12 rules (`rulepack.json`) are pure declarations — `id`, `name`, `category`, `severity`, `enabled`, and an empty `params: {}` for every one. Nothing consumes them. Note also that the rule catalogue (`stalkerware-profile`, `c2-beaconing`, `sim-swap`, `baseband-2g-risk`, `usb-debugging-enabled`, …) does **not** align with the intended demo scenarios (photo/media access, installed-app-list access, suspicious outbound). Only `background-location-scan` and `notification-clipboard-harvest` are adjacent. **Re-signing a demo-aligned rulepack requires the private key in `keys/` and a documented signing step — that step is not scripted anywhere in the repo.**

### 5.5 Event bus — **WORKING, one design caveat**

`replay = 0` means an event emitted before the `Application` collector has actually started collecting is **silently lost**. The collector is started inside `appScope.launch` in `onCreate` (`ThrakshaApplication.kt:42`), so there is a brief startup window where emissions vanish. Irrelevant for a human-tapped button; **relevant for a real detector that fires early**, e.g. one that scans installed packages at startup. Severity LOW-MEDIUM.

`ModeChanged` is defined, unit-tested, and **explicitly discarded** by the only consumer (`ThrakshaApplication.kt:56`). No producer exists.

### 5.6 SecureLogger — **implemented, DEAD**

`SecureLogger.kt` is complete (fire-and-forget insert into the encrypted `app_log` table) and has **zero call sites in the entire repo**. Meanwhile every service logs to plaintext logcat via `android.util.Log` (`ThrakshaVpnService`, `ThrakshaAccessibilityService`, `ThrakshaNotificationListener`, `ThrakshaDeviceAdminReceiver`, `DashboardScreen.kt:58`). The `app_log` table will always be empty at runtime, and `AppLogDao.recent()`/`count()` have no callers either.

### 5.7 Manifest permission posture

Declared (7): `INTERNET`, `ACCESS_NETWORK_STATE`, `VIBRATE`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, `FOREGROUND_SERVICE_SPECIAL_USE`, `POST_NOTIFICATIONS`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.

This is a **much cleaner posture than the historical `SCAN_REPORT.md` describes** — the telephony block, `SYSTEM_ALERT_WINDOW`, `RECORD_AUDIO`, `NEARBY_WIFI_DEVICES`, and the cleartext network config are all gone. `VIBRATE` is declared and unused (`AndroidManifest.xml:11`, comment admits "reserved for future"). `INTERNET`/`ACCESS_NETWORK_STATE` are used only by the VpnService path.

**Notable gap:** no `<queries>`, no `QUERY_ALL_PACKAGES`, no `PACKAGE_USAGE_STATS`, no `RECEIVE_BOOT_COMPLETED`, no `BIND_*` app-level permissions needed. See §13.

Residual risk profile: accessibility service with `canPerformGestures="true"` + `typeAllMask` + `packageNames="@null"` (`accessibility_service_config.xml`) + notification-listener access + device admin declaring `<wipe-data/>` is still a **stalkerware-shaped permission profile** for Play Protect / reviewer optics, even though nothing abuses it. The application-level `isAccessibilityTool=false` meta-data (`AndroidManifest.xml:37-39`) is placed on `<application>`; that attribute conventionally belongs in the accessibility service's XML config, and `accessibility_service_config.xml` does not carry it. **UNCLEAR / NEEDS RUNTIME TESTING** whether the declaration is honoured where it currently sits.

`device_admin.xml` declares `wipe-data`, `reset-password`, `force-lock`, `disable-camera`, `disable-keyguard-features`, etc. — **none exercised**. Declaring `wipe-data` for a demo device is a self-inflicted hazard worth a conscious decision (§22).

---

## 6. UI / Dashboard Audit

`DashboardScreen.kt` (467 lines) is the entire product surface. Structure top to bottom:

1. **Protection badge** (122-166) — pulsing shield, hardcoded `"FOUNDATION ACTIVE"` and `"Encrypted store · signed rulepack · audit chain"`. **These strings are static**; they do not reflect any runtime check. Honest today (those subsystems do work), but it is a hardcoded claim, not a live state.
2. **Foundation status card** (169-183) — three `StatusRow`s: Privilege level, Execution mode, Enforcement Available/Unavailable.
3. **Debug controls** (186-367) — five buttons:
   * *Emit Test Threat Event* → `SecurityEventBus.tryEmit(...)` — **the app's only event producer**.
   * *Test Accessibility (Tap Gesture)* → `performTap(500f, 1000f)` with hardcoded coordinates, or deep-links to Accessibility settings.
   * *Start/Stop VPN Monitor* → `VpnService.prepare` consent flow → `ThrakshaVpnService.start/stop`.
   * *Check Device Admin Status* → Toast per `PrivilegeLevel`, or deep-links to Security settings.
   * *Check Notification Listener* → Toast, or deep-links to listener settings.
   * *Check All Services Status* → multi-line Toast of 4 booleans.
4. **Live audit feed** (369-399) — `observeRecent(10)`, empty-state row, `ActivityItem` per entry with `Warning`/`CheckCircle` icon by `type`.

**Findings:**

* **It is a developer console.** Five of the six controls are labelled *DEBUG CONTROLS* / *SERVICE TESTS*, and several respond with `Toast` — unreadable in a screen recording and invisible from more than arm's length. Not demo-presentable as-is.
* **No dashboard state machine.** The four required states (Watching / Threat Detected / Acted / Advised) do not exist in any form — no enum, no `StateFlow`, no colour treatment, no transition.
* **No mode indicator.** FULL POWER / ADVICE MODE is at best inferable from two plain text rows.
* **`vpnRunning` is local `remember` state** (`DashboardScreen.kt:69`) — it is a UI guess, not the service's real state. It resets on recomposition-with-loss/rotation and will desynchronise if the user revokes VPN consent from system settings or the service dies. **Demo-reliability risk.**
* **`produceState` for privilege level and execution mode runs once** (104-111). Enable Device Admin from Settings and return — the dashboard still shows `NORMAL` until the Activity is recreated. **This will bite during a live demo**, because granting Device Owner/Admin is exactly the kind of thing shown on stage.
* **`ThrakshaTheme` supports light and dark** (`Theme.kt:46-67`), but `DashboardScreen` hardcodes `DarkForestGreen` / `CardSurfaceDark` / `TextWhite` throughout, so the screen is dark-only regardless of theme. `themes.xml` parents `android:Theme.Material.Light.NoActionBar`. Cosmetic inconsistency, low risk.
* **`StatusBarComponent`** (`StatusBarComponent.kt`) renders a **fake** status bar — a clock plus static full-signal/full-wifi/full-battery icons — inside the app, below the real system status bar. It ticks every second forever. This is decorative chrome that duplicates the OS; on a real device it reads as a mockup artefact. Recommend excluding from the demo (§18).
* **`SplashScreen`** costs 2.8 s on every Activity creation, including every rotation (`isSplashFinished` is a plain `mutableStateOf` field on the Activity, `MainActivity.kt:30`, not `rememberSaveable`). Minor demo friction.
* `BrandingComponents.kt` (`TRLogo`, `Wordmark`) — clean and reusable. `TRLogo` renders "T"+"R" as text glyphs; note the brand is *Thraksha*, so "TR" is a legacy mark. `Wordmark` sets no colour, inheriting `LocalContentColor`.
* `res/values/colors.xml` is the untouched Android template (purple/teal) and is entirely unreferenced.

---

## 7. Device Owner & Advice Mode Audit

### What exists

| Capability | Where | Status |
|---|---|---|
| Detect Device Owner | `SecurityCapability.currentLevel()` → `dpm.isDeviceOwnerApp(pkg)` | **WORKING** |
| Detect Device Admin | same → `dpm.isAdminActive(component)` | **WORKING** |
| Single source of truth | `SecurityCapability` object; `DashboardScreen` uses it at 105/295/351 | **WORKING** — the consolidation noted in the HEAD commit message is real |
| Gate on enforceability | `SecurityCapability.canEnforce()` (27-28) | **WORKING but UNUSED** — grep shows the only caller is `FoundationInstrumentedTest.kt:42` |
| DeviceAdminReceiver registered | `AndroidManifest.xml:100-114`, `device_admin.xml` | **WORKING** |
| Any policy enforcement | — | **MISSING (zero calls)** |
| Advice-mode path | — | **MISSING** — no notification, no alert, no advisory UI |
| Mode → behaviour branching | — | **MISSING** — nothing branches on `PrivilegeLevel` or `ExecutionMode` except three Toasts |

### The two-axis ambiguity

The codebase carries **two orthogonal mode concepts** and the demo brief describes **one**:

* `PrivilegeLevel` — what the OS *lets* the app do (`DEVICE_OWNER` / `DEVICE_ADMIN` / `NORMAL`).
* `ExecutionMode` — how aggressive the operator *wants* it to be (`OBSERVE` / `GUIDED` / `AUTO_DEFEND` / `LOCKDOWN`).

"FULL POWER vs ADVICE MODE" maps naturally onto `PrivilegeLevel` (authority you either have or don't). `ExecutionMode` is a separate policy dial. Conflating them would be wrong; ignoring one would waste working code. **This needs a human decision (§22, Q1).**

### Device Owner enrolment reality

`ThrakshaDeviceAdminReceiver`'s KDoc already states it (lines 14-24): `adb shell dpm set-device-owner com.thraksha.guardian/.security.ThrakshaDeviceAdminReceiver`, and it **only works on a device with no accounts added** — in practice a factory-reset device that has never signed into Google. This is a hard operational constraint on the demo, not a code problem. There is no in-app provisioning flow, no QR provisioning, no NFC provisioning, and no ADB helper documentation in the repo beyond that KDoc.

**UNCLEAR / NEEDS RUNTIME TESTING:** whether the intended demo device can be dedicated/factory-reset. Everything labelled "Full Power" depends on it.

---

## 8. VPN / Network Audit

`ThrakshaVpnService.kt`, 271 lines. Well-structured lifecycle code — and functionally a null device.

**What it does:**
* `establishVpnInterface()` (83-98): `Builder().setSession("Thraksha VPN Monitor").setMtu(1500).addAddress("10.0.0.2", 32).addRoute("0.0.0.0", 0).establish()` — **captures all IPv4 traffic**, throws if `establish()` returns null.
* `processPackets()` (123-152): `FileInputStream(tun).read(buffer)` → `FileOutputStream(tun).write(buffer, 0, length)`.
* Correct foreground-service handling, `AtomicBoolean` re-entry guard, coroutine cancellation, notification with `PendingIntent` back to `MainActivity`, `prepareIntent()` helper for consent.

**What it does not do:** there is **no `protect()` call, no outbound socket, no tunnel endpoint, and no packet parsing anywhere in the file.** Packets read from the TUN are written straight back into the TUN.

**Consequence (static analysis):** an outbound IP packet written back to the same TUN is re-injected as if it arrived *from* the tunnel. It is not delivered to the network, and its addressing is wrong for inbound delivery, so it will be dropped. **While this VPN is active, the device's IPv4 traffic should be effectively black-holed.** IPv6 is not routed by the TUN, so IPv6-capable paths may keep working, which would make the failure intermittent and confusing.

> **UNCLEAR / NEEDS RUNTIME TESTING** — the exact observed behaviour (total loss, partial loss via IPv6, or per-app variation) must be confirmed on a real device. But no code path exists that *could* forward traffic, so "monitors traffic while connectivity continues normally" is not achievable with the current implementation. The older `docs/PROJECT_STATUS_REPORT.md:438` test step *"Start VPN → verify internet still works"* was almost certainly never satisfied.

**Demo implication — and an opportunity.** Pressing *Start VPN Monitor* on stage is currently a live hazard: it is likely to break connectivity for the whole device with no visible explanation. Either exclude it, or turn the accident into a feature. A `VpnService` that drops traffic is a **real, unprivileged, OS-supported network kill switch**, and `Builder.addAllowedApplication()` / `addDisallowedApplication()` gives **real per-app network blocking with no Device Owner required**. That is one of the strongest honest "Thraksha acted" primitives available to a normal-install app, and it is ~20 lines away from the existing code.

**Also missing for network *observation*:** no DNS/SNI extraction, no IP-header parsing, no `ConnectivityManager.getConnectionOwnerUid()` (API 29+, the supported way to attribute a flow to an app), no per-app statistics.

**Foreground service typing:** the VPN declares `foregroundServiceType="dataSync"` (`AndroidManifest.xml:56`) and starts with `FOREGROUND_SERVICE_TYPE_DATA_SYNC` on API 34+ (`ThrakshaVpnService.kt:205-210`). On Android 14+ `dataSync` FGS is subject to a ~6-hour daily cap; irrelevant for a demo, relevant later. `connectedDevice`/`specialUse` may be the more accurate type for a monitoring VPN. Not a demo blocker.

---

## 9. Rules & Behaviour Detection Audit

### Rulepack — data only

`rulepack.json` v1, 12 rules, every one `enabled: true` with `params: {}`:

`high-risk-install` (install/HIGH), `accessibility-abuse` (privilege/CRITICAL), `stalkerware-profile` (profile/CRITICAL), `screen-capture-misuse` (privacy/HIGH), `malicious-vpn` (network/HIGH), `c2-beaconing` (network/CRITICAL), `boot-foreground-persistence` (persistence/MEDIUM), `notification-clipboard-harvest` (privacy/HIGH), `sim-swap` (telephony/CRITICAL), `usb-debugging-enabled` (config/MEDIUM), `baseband-2g-risk` (network/MEDIUM), `background-location-scan` (privacy/MEDIUM).

`RulepackModels.kt` is deliberately minimal: `Rulepack(version, rules)` and `Rule(id, name, category, severity, enabled, params: JsonObject)`. Its own KDoc calls `params` *"a free-form placeholder that Phase 3 detectors will read"* (line 14) — i.e. the schema anticipates an engine that was never built.

### Rule engine — **MISSING**

There is no evaluator, no `Detector` interface, no signal registry, no rule→code binding, no confidence computation, no threshold comparison. `ConfigStore.getTierThresholds()` returns 60/80/95/99 and is called **only from a test**. `SecurityEvent.ThreatDetected.confidence` is populated exactly once, with the literal `90`, from the debug button.

### Behaviour monitoring — **MISSING**

Nothing observes anything:

* **Accessibility events** — `onAccessibilityEvent` (`ThrakshaAccessibilityService.kt:45-62`) handles three event types and does nothing but `Log.d`. It never touches `SecurityEventBus`, never persists, never evaluates. The 1-second per-event-type debounce (`shouldProcessEvent`, 276-282) is a log-flood guard, not analysis.
* **Notifications** — `ThrakshaNotificationListener.processNotification` (37-75) parses extras and logs. Five `@Suppress("unused")` stubs (`containsOtp` → `return false`, `redactNotification`, `isSuspiciousNotification`, `isSuspiciousAppNotification`, `alertSecurityGuardian`) with their call sites **commented out** (58-71). `OTP_REGEX_PATTERNS = emptyList()` (line 190). `summarizeContent` has zero callers.
* **Packages** — no `PackageManager` enumeration anywhere; no install/uninstall receiver; no `<queries>`/`QUERY_ALL_PACKAGES` so enumeration would be filtered even if written.
* **Permissions** — no `checkPermission`/`getPackageInfo(GET_PERMISSIONS)` for third-party apps; no `AppOpsManager`; no `UsageStatsManager`.
* **Network** — see §8.
* **App categorisation / baselines** — no model, no storage, no seed data.

### Decoy apps — **MISSING**

`GoodCaller` and `VillainCaller` do not exist anywhere in this repository. `settings.gradle.kts` includes only `:app`. There is no reference to either name in any file.

---

## 10. Audit Log / Storage Audit

**Schema** (`Entities.kt`, DB v1):

| Table | Columns | Populated at runtime? |
|---|---|---|
| `app_log` | id (auto), timestamp, level, tag, message | **Never** — `SecureLogger` has no callers |
| `config` | key (PK), value | Yes — seeded by `ensureDefaults()` |
| `audit_log` | id (client-assigned PK), timestamp, type, details, tier, prevHash, hash | Yes — only via the bus collector |

**Access surface** (`Daos.kt`): `AuditDao` exposes `insert`, `allOrdered`, `last`, `count`, `observeRecent(limit)` — deliberately **no update, no delete**. `AuditLog` mirrors that: only `append` and `verifyChain`. Append-only is enforced by API shape, which is the right call.

**Semantics note:** `AuditEntity.tier` is overloaded. For `THREAT` rows it stores `confidence` (0-100); for `ACTION` rows it is hardcoded `0` (`ThrakshaApplication.kt:54`). The column name suggests a severity tier (matching `ConfigStore`'s 60/80/95/99 tiers), but it carries confidence. Will confuse anyone reading the schema; harmless today.

**Structural gaps for the demo:** the audit row is a flat `details: String`. There is no `packageName`, no `severity` enum column, no `ruleId` column, no `outcome` (acted/advised) column. Any dashboard filtering, per-app history, or "Acted vs Advised" tally has to parse the concatenated string built at `ThrakshaApplication.kt:46-55`. This is the single most likely place the demo will need a **schema change** (DB v2 + migration, or `fallbackToDestructiveMigration` for a demo build).

**Retention:** none. The chain grows without bound. Irrelevant at demo scale.

**`ModeChanged` events are dropped** (`ThrakshaApplication.kt:56`) — mode transitions leave no audit trace, which is exactly the kind of thing an audit log should record.

---

## 11. Panic / Safe Mode Audit

**Status: MISSING — nothing exists.**

Exhaustive evidence: repo-wide grep for `panic|Panic|PANIC|lockdown|Lockdown|LOCKDOWN|safe.?mode` across `app/src` returns exactly three hits, none of them functionality:

* `ExecutionMode.kt:18` — `LOCKDOWN,` enum constant, KDoc *"Maximum containment"*, never read.
* `DashboardScreen.kt:298` — Toast string *"Device Owner (Level 4) - Lockdown ready"*. It is not ready; nothing is wired.
* `ThrakshaDeviceAdminReceiver.kt:18` — KDoc bullet *"System-level lockdown"*.

No panic button, no security-action button, no quick-settings tile, no hardware-key trigger, no panic receiver, no containment routine, no un-panic/restore path. `onLockTaskModeEntering`/`onLockTaskModeExiting` are overridden in the receiver (64-72) but only log — and nothing ever calls `setLockTaskPackages`/`startLockTask`, so they cannot fire.

---

## 12. Dead / Incomplete / Placeholder Code

### Dead (implemented, zero callers)

| Item | Location | Note |
|---|---|---|
| `SecureLogger` (whole object) | `data/log/SecureLogger.kt` | 0 call sites; `app_log` table stays empty |
| `AppLogDao.recent()` / `.count()` | `Daos.kt:14-18` | 0 callers |
| `RulepackLoader` | `security/rulepack/RulepackLoader.kt` | only caller is `FoundationInstrumentedTest.kt:48` |
| `AuditLog.verifyChain()` | `AuditLog.kt:30` | only callers are instrumented tests |
| `SecurityCapability.canEnforce()` | `SecurityCapability.kt:27` | only caller is `FoundationInstrumentedTest.kt:42` |
| `ConfigStore.getTierThresholds()` | `ConfigStore.kt:33` | only caller is a test |
| `ConfigStore.isFeatureEnabled()` / `setFeatureEnabled()` | `ConfigStore.kt:40-44` | 0 callers anywhere |
| `ConfigStore.setExecutionMode()` | `ConfigStore.kt:31` | only internal caller is `ensureDefaults()`; no UI can change the mode |
| `SecurityEvent.ModeChanged` | `SecurityEvent.kt:29` | no producer; consumer discards it |
| `AccessibilityService.executeAction()` and everything reachable only through it — `performSwipe`, `clickNodeWithText`, `performGlobalBack/Home/Recents`, `findNodeByText`, `findClickableTarget` | `ThrakshaAccessibilityService.kt:75-166, 187-259` | 0 external callers |
| `AccessibilityService.findClickableNodes()` | `ThrakshaAccessibilityService.kt:212` | 0 callers at all |
| `ThrakshaNotificationListener.summarizeContent()` | line 204 | 0 callers |
| `Wordmark` colour-less variant, `TRLogo(withBackground=true)` | `BrandingComponents.kt` | partially used |
| `res/values/colors.xml` (7 template colours) | — | unreferenced |
| `VIBRATE` permission | `AndroidManifest.xml:11` | unused; comment admits it |

### Explicit stubs / placeholders

| Item | Location |
|---|---|
| `containsOtp()` → `return false`; `OTP_REGEX_PATTERNS = emptyList()` | `ThrakshaNotificationListener.kt:117-120, 190` |
| `redactNotification()` — logs "placeholder" | `:123-126` |
| `isSuspiciousNotification()` → `false` | `:129-135` |
| `isSuspiciousAppNotification()` → `false` | `:138-144` |
| `alertSecurityGuardian()` — logs "placeholder" | `:147-153` |
| Three commented-out call sites (`TODO Day 6`) | `:58-71` |
| `analyzeMonitoredNotification()` — logs only | `:77-86` |
| `CompanionForegroundService` — empty shell by design | `CompanionForegroundService.kt:22` |
| All 12 rulepack `params: {}` | `assets/rulepack.json` |
| Hardcoded debug tap `(500f, 1000f)` | `DashboardScreen.kt:233-234` |
| Hardcoded banner strings `"FOUNDATION ACTIVE"` etc. | `DashboardScreen.kt:152, 160` |
| Template tests `ExampleUnitTest`, `ExampleInstrumentedTest` | `test/`, `androidTest/` |
| `data_extraction_rules.xml` — untouched template with a `TODO` | `res/xml/` |
| `proguard-rules.pro` — untouched template | `app/` |

### Stale documentation (actively misleading)

`SCAN_REPORT.md` and `docs/PROJECT_STATUS_REPORT.md` both describe the **pre-Phase-1** architecture: Ollama LAN client, Replit backend, Retrofit/OkHttp/Gson, DataStore, Vosk model, SettingsScreen, TR floating overlay, telephony permissions, `network_security_config.xml`. **None of that exists at HEAD** — all removed by commit `8a449bb` *"Clean offline security baseline"*. `docs/PROJECT_STATUS_REPORT.md` retains redacted markers (`[REDACTED-JWT]`, `[REDACTED-DEVICE-SERIAL]`) from the Phase-0 secret cleanup, so the redaction held. Anyone onboarding from these documents will form a wrong model of the project. Recommend archiving with a header (§18) — no action taken in this run.

### Notable *absence* of dead weight

The repo is unusually clean: no leftover `com/example/` trees, no unused heavyweight assets (the 67 MB Vosk model referenced in `SCAN_REPORT.md` is gone), no orphaned network layer. The debug APK is 28.5 MB, most of which is SQLCipher's four native ABIs plus Compose.

---

## 13. Android / API Feasibility Issues

This section is the one that most constrains the demo. Split exactly as the brief requires.

### 13.1 REAL Android capability (supported, no root)

| Capability | API | Requirement | Present? |
|---|---|---|---|
| Detect Device Owner / Admin | `DevicePolicyManager.isDeviceOwnerApp` / `isAdminActive` | none | ✅ implemented |
| **Revoke a runtime permission from another app** | `DPM.setPermissionGrantState(admin, pkg, perm, PERMISSION_GRANT_STATE_DENIED)` | **Device Owner** | ❌ |
| **Suspend / quarantine an app** | `DPM.setPackagesSuspended(admin, pkgs, true)` | **Device Owner** | ❌ |
| Hide an app entirely | `DPM.setApplicationHidden` | **Device Owner** | ❌ |
| Block uninstall | `DPM.setUninstallBlocked` | **Device Owner** | ❌ |
| User restrictions (e.g. `DISALLOW_INSTALL_UNKNOWN_SOURCES`) | `DPM.addUserRestriction` | **Device Owner** | ❌ |
| Lock the device now | `DPM.lockNow()` | Device Admin **or** Owner | ❌ |
| Disable camera | `DPM.setCameraDisabled` | Device Admin **or** Owner (policy declared in `device_admin.xml`) | ❌ |
| **Block a specific app's network access** | `VpnService.Builder.addDisallowedApplication` / `addAllowedApplication` + a non-forwarding tunnel | user VPN consent only — **no Device Owner needed** | ❌ (Builder used without either) |
| Observe outbound destinations (DNS names, TLS SNI) | parse packets off the TUN fd | user VPN consent | ❌ (echo loop only) |
| Attribute a network flow to an app UID | `ConnectivityManager.getConnectionOwnerUid()` | API 29+ | ❌ |
| **Enumerate installed apps and their requested/granted permissions** | `PackageManager.getInstalledPackages(GET_PERMISSIONS)` + `PackageInfo.requestedPermissionsFlags` | **`<queries>` or `QUERY_ALL_PACKAGES` on Android 11+** | ❌ and **manifest has neither** |
| Detect that an app *can* read the app list | `requestedPermissions.contains(QUERY_ALL_PACKAGES)` on the target | same as above | ❌ |
| React to an app being installed | `ACTION_PACKAGE_ADDED` | **runtime-registered** receiver from the running foreground service (see caveat) | ❌ |
| Observe which app is in the foreground | `UsageStatsManager` | `PACKAGE_USAGE_STATS`, user-granted via a Settings screen | ❌ |
| Read notification content | `NotificationListenerService` | user-granted | ✅ service exists, no analysis |
| Observe UI/window transitions | `AccessibilityService` | user-granted | ✅ service exists, logs only |
| Tamper-evident local evidence | SQLCipher + Keystore + hash chain | none | ✅ **fully working** |

> **Caveat on `ACTION_PACKAGE_ADDED`:** since Android 8.0, most implicit broadcasts are not delivered to *manifest-declared* receivers. A receiver **registered at runtime** by the already-running foreground service is the reliable path. **UNCLEAR / NEEDS RUNTIME TESTING** on the target device for the manifest variant — do not design around it without testing.

### 13.2 SIMULATED / DEMO behaviour (must be labelled as such)

* **A decoy app self-reporting its own actions** to Thraksha (broadcast/AIDL/content provider). This is *instrumented telemetry from a cooperating app*, not OS-level observation. It is a legitimate demo technique **if and only if the UI and the narration say so.** It is the only way to make "VillainCaller just read your photos" appear as a live event.
* **Any pre-scripted timeline** of events.
* **Any threat surfaced through the existing "Emit Test Threat Event" path.**

### 13.3 IMPOSSIBLE / UNSUPPORTED (cannot be done by a normal or Device-Owner app)

| Claim the demo might be tempted to make | Reality |
|---|---|
| *"We saw VillainCaller open your photo library"* | **No API exists.** A third-party app cannot observe another app's `MediaStore`/file reads. Android 12+ privacy indicators are OS-rendered and not readable by apps. `AppOpsManager.getPackagesForOps` needs the signature-level `GET_APP_OPS_STATS`. Not achievable — not even as Device Owner. |
| *"We saw VillainCaller query the installed-app list"* | **No API exists.** The *query itself* is unobservable. Only the static *capability* (`QUERY_ALL_PACKAGES` in the target's requested permissions) is detectable. |
| *"We killed the malicious app"* | Force-stopping an arbitrary app requires `FORCE_STOP_PACKAGES` (signature) or root. Device Owner can **suspend** or **hide** it — which is real, and arguably better — but not force-stop it. |
| *"We revoked its permission"* (on a normal install) | Requires Device Owner. In `NORMAL`/`DEVICE_ADMIN` mode the app can only *advise* and deep-link the user to app settings. |
| *"We blocked that specific connection"* | Coarse per-app blocking via VPN is real. Selective per-connection blocking requires implementing a userspace TCP/IP stack behind the TUN — far beyond demo scope. |
| *"We read the content of its HTTPS upload"* | TLS payload is opaque. DNS names and TLS SNI (where not encrypted) are visible; the body is not. |
| *"The audit log cannot be altered"* | It is tamper-**evident**, not tamper-proof. Say "any alteration is detectable". |

### 13.4 Configuration-level feasibility issues at HEAD

1. **No package visibility.** `targetSdk 36` + no `<queries>` + no `QUERY_ALL_PACKAGES` ⇒ `getInstalledPackages()` returns a filtered list that will not contain the decoys. **Blocks demo capabilities 2, 3, 5, 6 outright.** Fixing it means a manifest change (out of scope for this run) and, if `QUERY_ALL_PACKAGES` is chosen, a Play policy consideration (a `<queries>` element naming the two decoys is cleaner and reviewer-friendly).
2. **Target device is Android 13 (API 33)** per `docs/PROJECT_STATUS_REPORT.md:461` (Samsung SM-G781B). **UNCLEAR / NEEDS RUNTIME TESTING** whether that is still the demo device. Consequences if so: `POST_NOTIFICATIONS` runtime request is required (**handled**, `MainActivity.kt:124-130`); `FOREGROUND_SERVICE_*` sub-permissions are declared (**handled**); `onTimeout` (API 35) simply never fires; `PendingIntent.FLAG_IMMUTABLE` is set (**handled**). No API-33-specific breakage found in the current sources.
3. `AccessibilityNodeInfo.recycle()` (used ~10× in `ThrakshaAccessibilityService`) is deprecated as of API 33 and is a no-op on newer platforms. Not a break; it will produce deprecation warnings.
4. **Battery/background:** the app relies on a `START_STICKY` foreground service and asks for battery-optimisation exemption on Samsung only (`MainActivity.kt:95-105`). Samsung's aggressive app-sleep behaviour is a genuine demo-reliability hazard on the S20 FE. The dialog exists but only fires for Samsung + not-yet-whitelisted; there is no post-hoc verification that the user actually granted it.
5. **No boot persistence.** No `RECEIVE_BOOT_COMPLETED`, no boot receiver. After a reboot, monitoring is dead until the user opens the app. `NotificationListenerService` and `AccessibilityService` are rebound by the OS, but they do nothing.

---

## 14. Demo Gap Analysis

Classification per the brief. "Already available" means demo-ready today, not merely present.

| # | Demo capability | Verdict | Basis |
|---|---|---|---|
| 1 | Behaviour monitoring | **Must be built new** | No detector of any kind exists (§9). Foreground-service host exists to hang it on. |
| 2 | App-type-aware permission/behaviour rules | **Must be built new** | No app categorisation, no baselines, no evaluator. Rulepack format is reusable but its 12 rules do not match the demo scenarios; a re-signed, demo-aligned pack is needed. |
| 3 | `GoodCaller` decoy | **Must be built new** | Not in the repo. New Gradle module or separate project. |
| 4 | `VillainCaller` decoy | **Must be built new** | Same. Must also carry the self-report channel (§13.2) if live media-access events are wanted. |
| 5 | Detect suspicious photo/media access | **Must be built new — and re-scoped** | Live interception is **impossible** (§13.3). Honest substitutes: (a) detect the *permission profile mismatch* — a dialer holding `READ_MEDIA_IMAGES` — 100 % real; (b) detect the *exfiltration* via VPN DNS/SNI — real; (c) decoy self-report — must be labelled. |
| 6 | Detect installed-app-list access | **Must be built new — and re-scoped** | Live interception **impossible**. Real substitute: detect that the target declares `QUERY_ALL_PACKAGES` / a broad `<queries>` set. Requires the manifest visibility fix first. |
| 7 | Detect suspicious outbound behaviour | **Must be built new (strong real foundation)** | The single most demo-credible *real* detection available. `ThrakshaVpnService` already establishes a full-route TUN with correct lifecycle; needs packet/DNS parsing + `getConnectionOwnerUid` attribution. Significant modification of an existing, working file. |
| 8 | Device Owner / Full Power mode | **Reusable with minor modification (detection) + build new (actions)** | Detection is done and consolidated. Every actual DPM action is new code. Plus an operational blocker: a factory-reset device. |
| 9 | Normal-install / Advice mode | **Requires significant modification** | `PrivilegeLevel.NORMAL` is detected; the advisory *behaviour* (notification, in-app advisory card, deep-link to app settings) is entirely absent. |
| 10 | Real intervention where authority permits | **Must be built new** | Zero DPM calls today. Highest-value new code: `setPermissionGrantState` + `setPackagesSuspended` (Device Owner) and VPN per-app block (no DO needed). |
| 11 | Alert-only where authority does not permit | **Must be built new** | No notification channel for alerts, no advisory UI. `POST_NOTIFICATIONS` is already declared and requested. |
| 12 | Panic / Safe Mode | **Must be built new** | Nothing exists (§11). Cheapest honest version in Full Power: suspend all flagged packages + `lockNow`. In Advice mode: a checklist + notification. |
| 13 | Security action button | **Must be built new** | The dashboard has debug buttons only. |
| 14 | Audit / event history | **Already available** | Encrypted, hash-chained, live `Flow` feed, tested end-to-end. Likely needs **one schema change** (add `packageName`, `severity`, `ruleId`, `outcome`) to support filtering and Acted/Advised tallies (§10). |
| 15 | Dashboard states: Watching / Threat Detected / Acted / Advised | **Must be built new** | No state model exists. The rendering primitives (`StatusRow`, `ActivityItem`, theme colours incl. `ThreatRed`, `WarningAmber`, `BrightGreen`) are all present and reusable. |
| 16 | FULL POWER / ADVICE MODE indicator | **Requires significant modification** | Data source exists (`SecurityCapability`); presentation is a plain text row; needs a decision on the two-axis question (§7). |

**Score:** 1 of 16 demo capabilities is already available. 2 need significant modification of existing code. 13 are new work. **However**, the new work is unusually well-supported: the persistence, integrity, crypto, event-transport, privilege-detection, and policy-artefact layers under it are done and tested — which is normally the slow half.

---

## 15. What We Should Reuse (unchanged or near-unchanged)

Highest confidence first. All of these are implemented **and** covered by a passing-by-design test.

1. **`KeystoreManager` + `DatabaseProvider` + `ThrakshaDatabase` + `Daos`** — the encrypted store. Zero changes needed except adding entities. Carries real investor weight ("your evidence is encrypted with a hardware-backed key").
2. **`AuditHasher` + `AuditLog`** — the tamper-evident chain. Reuse the algorithm and the append-only API shape verbatim.
3. **`RulepackVerifier`** — signature verification. Reuse as-is; only the *content* of the signed pack changes.
4. **`RulepackLoader`** — fail-closed loading. Reuse as-is; it just needs a production caller.
5. **`SecurityEventBus` + `SecurityEvent`** — the decoupling seam that makes "detector → audit → UI" a one-line wiring job. Reuse; extend the sealed interface rather than replacing it.
6. **`SecurityCapability` + `PrivilegeLevel`** — privilege detection and the `canEnforce()` gate. This is exactly the FULL POWER/ADVICE discriminator the demo needs.
7. **`ThrakshaDeviceAdminReceiver`** — registration, component-name and DPM accessors. Reuse; add enforcement methods elsewhere (not in the receiver).
8. **`ThrakshaApplication`'s bus→audit collector** — the pattern is right; only the mapping needs enriching.
9. **`ui/theme/*` + `BrandingComponents`** — brand colours (including unused `ThreatRed`, `WarningAmber`, `BrightGreen`, perfect for the four dashboard states), typography, logo.
10. **`ActivityItem` / `StatusRow` composables** — good bones for threat cards and status rows.
11. **`CompanionForegroundService`** — a correct, Android-15-aware foreground-service shell. Reuse as the host process for the detection loop; that is literally what its KDoc anticipates.
12. **`MainActivity` start-up chain** — splash → Samsung battery dialog → `POST_NOTIFICATIONS` → start service. Correct and complete.
13. **The whole test scaffold** — `EncryptedStoreTest`, `AuditLogInstrumentedTest`, `AuditLogTest`, `RulepackVerifierTest`, `SecurityEventBusTest`. These are the evidence that the foundation is real, and they are worth showing.
14. **`.gitattributes` signature protection** and the `.gitignore` key hygiene.

---

## 16. What We Should Modify

| Item | Change | Why | Size |
|---|---|---|---|
| `CompanionForegroundService` | Attach the detection loop; make the notification text reflect real state | Its notification currently claims monitoring that does not happen | M |
| `DashboardScreen` | Split demo UI from debug controls; add a state header (Watching/Threat/Acted/Advised); add a mode indicator; make privilege/mode reactive (`Flow`, not one-shot `produceState`); replace `Toast` feedback with on-screen state | It is a dev console; and the one-shot reads will show stale mode after on-stage enrolment | L |
| `ThrakshaApplication` collector | Enrich the `SecurityEvent`→`AuditEntity` mapping; stop discarding `ModeChanged`; wrap `DatabaseProvider.get()` in `runCatching` | Audit rows need structure, not a concatenated string; a Keystore failure currently crashes startup | S |
| `AuditEntity` + `AuditDao` | Add `packageName`, `severity`, `ruleId`, `outcome` columns → DB v2 + migration (or destructive fallback in the demo build) | Required for per-app history, filtering, Acted-vs-Advised tallies | M |
| `AuditLog.append` | Guard with a `Mutex` | Read-then-write id assignment will collide once >1 producer exists | S |
| `assets/rulepack.json` (+ re-sign) | Replace the 12 generic rules with demo-aligned signals, populate `params` | Current rules don't match the demo scenarios; and the demo *should* show the signed pack driving behaviour | M (plus a documented signing step) |
| `ThrakshaVpnService` | Either exclude from the demo, or convert the echo loop into (a) DNS/SNI observation and/or (b) an explicit per-app block using `addDisallowedApplication` | As-is it likely black-holes device traffic on stage (§8) | M–L |
| `ExecutionMode` handling | Give it a UI control and make something actually branch on it — or drop it from the demo surface | It is currently a decorative label | S–M |
| `AndroidManifest.xml` | Add `<queries>` for the decoys (preferred) or `QUERY_ALL_PACKAGES`; consider narrowing `device_admin.xml` (drop `wipe-data`) | Without visibility, half the demo cannot see anything; `wipe-data` on a demo device is a hazard | S |
| `SecureLogger` | Either start using it for security-relevant logs, or drop it | Dead code that claims a capability | S |
| `ThrakshaNotificationListener` / `ThrakshaAccessibilityService` | Decide in or out; if in, emit onto `SecurityEventBus` | Currently pure logcat; and they carry the heaviest permission optics | M |

---

## 17. What We Should Build New

Ordered by demo value per unit of effort.

1. **`AppInventory`** — enumerate installed packages with `GET_PERMISSIONS`, extract requested vs granted, classify by declared category / package name. *Real capability.* Foundation for capabilities 2, 5, 6.
2. **`RuleEngine`** — consume the verified `Rulepack`, evaluate signals against `AppInventory` + live sources, compute confidence, emit `SecurityEvent.ThreatDetected`. Gives the signed rulepack a purpose.
3. **`PolicyEngine`** — `(severity, confidence, ExecutionMode, PrivilegeLevel) → Decision{ACT | ADVISE | OBSERVE}`. This is where `canEnforce()` and `getTierThresholds()` finally get used, and where the FULL POWER / ADVICE split becomes behaviour rather than a label.
4. **`Enforcer` interface with three real implementations:**
   * `DeviceOwnerEnforcer` — `setPermissionGrantState`, `setPackagesSuspended`, `lockNow`. **Real OS action.**
   * `NetworkEnforcer` — VPN per-app block via `addDisallowedApplication`. **Real OS action, no Device Owner required** — this is what makes the *normal-install* demo non-empty.
   * `AdvisoryEnforcer` — high-priority notification + in-app advisory card + deep-link to the offending app's settings page. **Honest alert-only path.**
   Every enforcer emits `SecurityEvent.ActionTaken` → audit chain.
5. **`DashboardState`** — a `StateFlow<Watching | ThreatDetected | Acted | Advised>` plus the visual treatment (the theme already has the colours).
6. **Threat card UI** — severity styling, the offending package, the rule that fired, what was done (or why it could not be), and an action button.
7. **Security action / panic control** — one prominent button. Full Power: suspend flagged packages + `lockNow`. Advice: advisory checklist + notification. Both fully audited.
8. **`GoodCaller`** — a minimal, genuinely well-behaved dialer-shaped app. Requests only what a dialer needs. Its job is to *not* trip anything, proving the rules discriminate.
9. **`VillainCaller`** — same shape, but requests `READ_MEDIA_IMAGES`, `QUERY_ALL_PACKAGES`, and makes an outbound connection to a known-bad-looking host. Every one of those is **really detectable** by 1, 2 and 7 above.
10. **Decoy self-report channel** *(only if live media-access events are required)* — an explicit, clearly-labelled instrumented telemetry path. **Must be rendered in the UI as instrumented, e.g. a distinct badge, never as OS interception.**
11. **Audit-integrity surface** — call `verifyChain()` and show the result. Nearly free; it turns an existing tested capability into a visible one.
12. **Boot receiver** *(optional)* — restore monitoring after reboot.

---

## 18. What We Should Ignore / Defer

| Item | Recommendation |
|---|---|
| `ThrakshaAccessibilityService` automation API (`executeAction`, gestures, node traversal, ~250 lines) | **Defer, do not delete.** Unrelated to the demo; it is the heaviest Play-Store/optics liability in the repo. If the demo does not need UI automation, consider disabling the service in the demo build (decision required — §22 Q5). |
| `ThrakshaNotificationListener` and its 5 Day-6 stubs | **Defer.** OTP/phishing is not in the demo scope. Leave untouched. |
| `StatusBarComponent` (fake in-app status bar) | **Exclude from the demo screen.** It duplicates the OS status bar and reads as mockup chrome. |
| `SplashScreen` 2.8 s animation | Keep for brand, but consider shortening; it replays on every rotation. |
| `WhitelistingDialog` (Samsung battery) | **Keep as-is** — genuinely useful for demo reliability on Samsung. Do not confuse with app trust-listing. |
| `SecureLogger` / `app_log` table | **Defer.** Harmless dead code; not worth touching mid-demo-build. |
| `libs.versions.toml`, `enableJetifier`, `kotlinCompilerExtensionVersion`, template `colors.xml`, `proguard-rules.pro`, `ExampleUnitTest`/`ExampleInstrumentedTest`, `data_extraction_rules.xml` TODO | **Ignore.** Cosmetic; zero demo impact. |
| `SCAN_REPORT.md`, `docs/PROJECT_STATUS_REPORT.md` | **Archive with a "HISTORICAL — describes the removed remote architecture" header.** Do not delete (they document the pivot), but they will actively mislead a new reader. |
| `ExecutionMode`'s `GUIDED` and `AUTO_DEFEND` | Defer unless the demo needs four policy levels. `OBSERVE` + one acting mode is enough to tell the story. |
| Rulepack signals unrelated to the demo (`sim-swap`, `baseband-2g-risk`, `usb-debugging-enabled`, …) | Keep them **in the signed pack** as `enabled: false` or simply unreferenced — they make the pack look like a real catalogue — but build no detectors for them. |
| Full VPN packet inspection (TCP reassembly, payload analysis) | **Defer.** DNS/SNI only. |

---

## 19. Recommended Demo Architecture

Additive. Every new box plugs into an existing, tested seam.

```
                       ┌───────────────────────────────────────────┐
  process start        │  ThrakshaApplication                      │
  ──────────────────►  │   loadLibrary(sqlcipher)                  │   [EXISTS]
                       │   ConfigStore.ensureDefaults()            │   [EXISTS]
                       │   bus → AuditLog collector                │   [EXISTS, enrich]
                       └───────────────┬───────────────────────────┘
                                       │
                       ┌───────────────▼───────────────────────────┐
                       │  CompanionForegroundService               │   [EXISTS — host the loop here]
                       │   ├─ RulepackLoader.load()  (verified)    │   [EXISTS, needs a caller]
                       │   └─ RuleEngine(rulepack)                 │   [NEW]
                       └───────────────┬───────────────────────────┘
                                       │  signal sources
            ┌──────────────────────────┼──────────────────────────────┐
            ▼                          ▼                              ▼
  ┌───────────────────┐   ┌────────────────────────┐   ┌──────────────────────────┐
  │ AppInventory      │   │ NetworkObserver        │   │ DecoySelfReport          │
  │ PackageManager +  │   │ VpnService TUN:        │   │ cooperating decoy app    │
  │ GET_PERMISSIONS   │   │ DNS/SNI + owner UID    │   │ ** LABEL AS INSTRUMENTED**│
  │ [NEW] REAL        │   │ [MODIFY] REAL          │   │ [NEW] SIMULATED          │
  └─────────┬─────────┘   └───────────┬────────────┘   └────────────┬─────────────┘
            └──────────────────────────┼─────────────────────────────┘
                                       ▼
                        SecurityEvent.ThreatDetected  ──► SecurityEventBus     [EXISTS]
                                       │
                                       ▼
                       ┌───────────────────────────────────────────┐
                       │  PolicyEngine                             │   [NEW]
                       │   SecurityCapability.canEnforce()         │   [EXISTS, unused]
                       │   ConfigStore.getTierThresholds()         │   [EXISTS, unused]
                       │   → ACT | ADVISE | OBSERVE                │
                       └──────────┬───────────────┬────────────────┘
                        canEnforce│               │ !canEnforce
                                  ▼               ▼
              ┌──────────────────────────┐  ┌──────────────────────────┐
              │ DeviceOwnerEnforcer      │  │ AdvisoryEnforcer         │
              │  setPermissionGrantState │  │  high-priority notif     │
              │  setPackagesSuspended    │  │  advisory card + deeplink│
              │  lockNow                 │  │  [NEW]  REAL (alert-only)│
              │ NetworkEnforcer          │  └──────────┬───────────────┘
              │  VPN addDisallowedApp    │             │
              │ [NEW] REAL OS ACTIONS    │             │
              └──────────┬───────────────┘             │
                         └───────────────┬─────────────┘
                                         ▼
                          SecurityEvent.ActionTaken ──► bus ──► AuditLog.append()   [EXISTS]
                                         │                        SQLCipher chain
                                         ▼
                       ┌───────────────────────────────────────────┐
                       │  DashboardState : StateFlow               │   [NEW]
                       │   WATCHING → THREAT_DETECTED → ACTED      │
                       │                              └→ ADVISED   │
                       │  + FULL POWER / ADVICE badge              │
                       │  + threat cards  + audit feed             │   [feed EXISTS]
                       │  + security action button                 │
                       └───────────────────────────────────────────┘
```

**Non-negotiable design rules for this architecture:**

1. **Every UI claim maps to an enforcer that actually ran.** `ActionTaken.result` records success/failure; the dashboard reads that, not an assumption.
2. **`ADVISED` is a first-class success state, not a failure.** The story "we lacked authority, so we told you precisely what to do" is stronger and more honest than a faked action.
3. **The decoy self-report channel, if used, is visually distinguished in-app** (badge/label) and in the narration. Never rendered identically to a `NetworkObserver` or `AppInventory` finding.
4. **The signed rulepack is on the critical path** — `RuleEngine` refuses to run without a verified pack. That makes the existing `RulepackVerifier` a load-bearing demo asset instead of a test-only curiosity.

---

## 20. Recommended Implementation Order

Each step is independently demoable, so the demo is never in a broken state.

| # | Step | Depends on | Notes |
|---|---|---|---|
| 0 | Decide the §22 questions — especially the demo device / Device Owner feasibility | — | Q1 and Q2 change everything downstream |
| 1 | Manifest visibility (`<queries>` for the decoys) + a reactive privilege/mode read on the dashboard | 0 | Small, unblocks all app-inspection work |
| 2 | **`GoodCaller` + `VillainCaller`** decoys | 1 | Build the targets before the detectors — you cannot test a detector without them |
| 3 | **`AppInventory`** + permission-profile signals | 1, 2 | First **real** detection: dialer holding `READ_MEDIA_IMAGES`; app declaring `QUERY_ALL_PACKAGES` |
| 4 | Demo-aligned **rulepack** (re-signed) + **`RuleEngine`** calling `RulepackLoader` in production | 3 | Turns the signed pack into a load-bearing artefact |
| 5 | **Audit schema v2** (`packageName`, `severity`, `ruleId`, `outcome`) + `Mutex` in `append` | 4 | Do it before the audit fills with unstructured rows |
| 6 | **Dashboard v2** — state machine, mode badge, threat cards; debug controls moved behind a long-press/hidden panel | 5 | The first genuinely presentable build |
| 7 | **`PolicyEngine`** + **`AdvisoryEnforcer`** | 6 | Delivers the complete **Advice Mode** story with **no Device Owner needed** — de-risks the whole demo |
| 8 | **`DeviceOwnerEnforcer`** (`setPermissionGrantState`, `setPackagesSuspended`) | 7 + a provisioned device | The **Full Power** story. Gate strictly on `canEnforce()` |
| 9 | **`NetworkEnforcer`** — VPN per-app block | 7 | Real enforcement that works **without** Device Owner; strong fallback if step 8's device is unavailable |
| 10 | **`NetworkObserver`** — DNS/SNI + UID attribution (replaces the echo loop) | 9 | The honest version of "suspicious outbound behaviour" |
| 11 | **Panic / Safe Mode** button | 8, 9 | Composes existing enforcers; no new primitives |
| 12 | Audit-integrity surface (`verifyChain()` in the UI) | 5 | Nearly free; high credibility |
| 13 | Decoy self-report channel **only if** live media-access events are still required | 2, 6 | Last, because it is the only simulated element — build it only if the real signals do not carry the story |

**Critical sequencing insight:** steps 1-7 and 9-10 require **no Device Owner at all** and produce real, unfaked detection and real enforcement. Device Owner (step 8) should be treated as an *upgrade path that makes the demo more impressive*, never as a prerequisite. That single decision removes the largest operational risk in the project.

---

## 21. Risks / Blockers

### Blockers

| # | Blocker | Impact |
|---|---|---|
| B1 | **No package visibility** (`AndroidManifest.xml`: no `<queries>`, no `QUERY_ALL_PACKAGES`, `targetSdk 36`) | Thraksha cannot see the decoys. Blocks demo capabilities 2, 3, 5, 6. Manifest change required. |
| B2 | **Device Owner needs a factory-reset, account-free device** | Everything labelled "Full Power / real intervention via DPM" depends on dedicated hardware. **UNCLEAR / NEEDS RUNTIME TESTING** — availability unknown. |
| B3 | **Photo-access and app-list-access interception are impossible** (§13.3) | Two headline demo scenarios must be re-scoped to real, adjacent signals, or explicitly labelled as instrumented. |
| B4 | **Decoy apps do not exist** | Nothing to detect until they are built. |

### High risks

| # | Risk | Detail |
|---|---|---|
| R1 | **VPN black-holes device traffic** | `ThrakshaVpnService` echoes packets back into the TUN with no forwarding path (§8). Pressing the demo button could kill connectivity mid-presentation with no on-screen explanation. **Exclude or rework before any rehearsal.** |
| R2 | **Stale mode display** | `produceState` reads privilege/mode once (`DashboardScreen.kt:104-111`). Enrol Device Admin on stage and the dashboard keeps saying `NORMAL`. |
| R3 | **`vpnRunning` is a UI guess** | Local `remember` state (`:69`) desynchronises from reality. |
| R4 | **Keystore failure crashes startup** | `ThrakshaApplication.kt:41` calls `DatabaseProvider.get()` outside `runCatching`. Key invalidation (restore, reset, corruption) ⇒ startup crash. |
| R5 | **Samsung background killing** | The demo depends on a persistent foreground service on a device family known for aggressive app sleep. The battery dialog exists but is not verified as accepted. |
| R6 | **`device_admin.xml` declares `wipe-data`** | Declared but unexercised. A stray future call, or an alarmed reviewer, are both avoidable. |
| R7 | **Play Protect / reviewer optics** | Accessibility-with-gestures + notification listener + device admin + VPN is a stalkerware-shaped profile even with zero abuse. Fine for a controlled demo; a problem for distribution. |

### Medium risks

| # | Risk | Detail |
|---|---|---|
| R8 | **`AuditLog.append` id race** | Read-then-write without a lock; `@Insert` ABORT on PK conflict. Latent until a second producer exists — which step 3 of §20 creates. |
| R9 | **Bus `replay = 0` startup window** | Events emitted before the `Application` collector subscribes are silently lost. A startup-time scanner would lose its first findings. |
| R10 | **Audit schema is a flat string** | Any filtering/tallying requires string parsing (§10). Fix early or live with it. |
| R11 | **Rulepack re-signing is unscripted** | The private key is in `keys/rulepack_private.pem` (correctly gitignored), but there is no signing script, no README step, no CI check that `rulepack.sig` matches `rulepack.json`. A rulepack edit without re-signing makes `RulepackLoader.load()` throw — and its only production caller would be the detection loop, so **the app would fail to detect anything, loudly or silently depending on wiring.** |
| R12 | **Room v1 with no migration strategy** | Adding columns (very likely, §10) requires a migration or destructive fallback. |
| R13 | **Historical docs contradict the code** | `SCAN_REPORT.md` and `docs/PROJECT_STATUS_REPORT.md` describe a deleted architecture. |
| R14 | **Notification text over-claims** | *"Security monitoring is running"* is displayed persistently while nothing is monitored. Harmless internally; would be an integrity problem if shown to investors. |

### Low risks

`enableJetifier` unnecessary; `kotlinCompilerExtensionVersion` obsolete; `libs.versions.toml` orphaned and drifted; deprecated manifest `package=` attribute; deprecated `AccessibilityNodeInfo.recycle()`; splash replays on rotation; dashboard hardcodes dark colours despite a light/dark theme; `VIBRATE` declared and unused; `tier` column semantically overloaded; no boot persistence; no audit retention policy.

### Explicitly *not* found (good news, verified)

No hardcoded credentials, tokens, API keys, or private keys in tracked files. No network calls to any remote service. No cleartext-traffic config. No mock behaviour dressed up as real — every stub in the repo is honestly commented as a stub. No duplicate/competing implementations of any subsystem. No obviously dead heavyweight assets.

---

## 22. Questions Requiring Human Decision

| # | Question | Why it matters | Options |
|---|---|---|---|
| **Q1** | **Do "FULL POWER / ADVICE MODE" map to `PrivilegeLevel`, to `ExecutionMode`, or to a new combined concept?** | Two orthogonal mode axes already exist in code (§7). Getting this wrong means rebuilding the mode UI and the policy gate. | (a) `PrivilegeLevel` only, drop `ExecutionMode` from the demo surface *(recommended — simplest, truest to Android's authority model)*; (b) keep both, show both; (c) collapse into one new enum |
| **Q2** | **Is there a dedicated device that can be factory-reset and provisioned as Device Owner?** | Gates every real DPM intervention (B2). If no, the demo must lead with the VPN-based and advisory paths — which is entirely viable (§20). | Yes / No / Emulator only |
| **Q3** | **Which target Android version is the demo device?** `docs/` says Samsung SM-G781B on Android 13 — is that still true? | Determines package-visibility behaviour, FGS rules, VPN behaviour, and which APIs are available. | Confirm model + API level |
| **Q4** | **For photo/media access and app-list access — accept the honest re-scope, or add a labelled decoy self-report?** | These cannot be intercepted (§13.3). This is the demo's central integrity decision. | (a) Re-scope to real signals: permission-profile mismatch + outbound exfiltration *(recommended)*; (b) add self-report, clearly labelled as instrumented; (c) both |
| **Q5** | **Keep or disable `ThrakshaAccessibilityService` in the demo build?** | It is unused by the demo, is the largest single body of unused code, and carries the heaviest permission optics. | Keep dormant / disable in the demo build / remove |
| **Q6** | **Keep `ThrakshaVpnService` in the demo?** | As-is it is a live hazard (R1); reworked it is one of the strongest *real* capabilities available without Device Owner. | (a) Rework into per-app block + DNS/SNI *(recommended)*; (b) exclude entirely |
| **Q7** | **Do the decoys become Gradle modules in this repo, or separate projects?** | Affects build time, signing, install choreography on stage, and repo hygiene. Same-repo modules are far easier to keep in sync. | `:goodcaller` / `:villaincaller` modules *(recommended)* vs separate projects |
| **Q8** | **May the audit schema change (DB v2), and may the demo build use destructive migration?** | Determines whether structured threat cards and Acted/Advised tallies are cheap or expensive (§10, R12). | Yes + destructive in demo build *(recommended)* / proper migration / keep v1 and parse strings |
| **Q9** | **What exactly should Panic / Safe Mode do in each mode?** | Needs an explicit, safe definition before implementation. `wipe-data` is declared in `device_admin.xml` and must be ruled in or out. | Full Power: suspend flagged apps + `lockNow` *(recommended)*; Advice: notification + checklist. **Wipe: recommend explicitly excluded** |
| **Q10** | **Should the rulepack be re-signed for the demo, and who owns the signing step?** | The signed-pack story is a genuine differentiator, but there is no signing script and an unsigned/mismatched pack fails closed (R11). | Re-sign + script it *(recommended)* / ship the current 12 generic rules unchanged |
| **Q11** | **Archive or delete `SCAN_REPORT.md` and `docs/PROJECT_STATUS_REPORT.md`?** | They describe a removed architecture and will mislead anyone onboarding (R13). No action taken in this run. | Archive with a header *(recommended)* / delete / leave |

---

## Appendix A — Subsystem status at a glance

| Subsystem | Status |
|---|---|
| Encrypted storage (SQLCipher + Room) | 🟢 **WORKING** |
| Android Keystore passphrase management | 🟢 **WORKING** |
| Hash-chained audit log (append + verify logic) | 🟢 **WORKING** |
| Signed rulepack verification | 🟢 **WORKING** |
| Security event bus | 🟢 **WORKING** |
| Device Owner / Admin **detection** | 🟢 **WORKING** |
| Foreground-service shell | 🟢 **WORKING** (as a shell) |
| Dashboard (as a developer console) | 🟡 **PARTIAL** |
| Rulepack **consumption** | 🟡 **PARTIAL** — loader exists, zero production callers |
| Execution mode | 🟡 **PARTIAL** — stored & shown, gates nothing |
| Device Admin receiver | 🟡 **PARTIAL** — registered, logs only |
| Accessibility automation API | 🟡 **PARTIAL** — complete, disconnected |
| Notification listener | 🟡 **PARTIAL** — parses & logs, 5 stubs |
| VPN service | 🟡 **PARTIAL** — establishes TUN, monitors nothing, likely blocks traffic |
| `SecureLogger` | 🟠 **DEAD** — implemented, zero callers |
| DevicePolicyManager enforcement | 🔴 **MISSING** |
| Permission revocation / app suspension | 🔴 **MISSING** |
| Per-app network identification / control | 🔴 **MISSING** |
| Behaviour monitoring | 🔴 **MISSING** |
| Rule engine / evaluator | 🔴 **MISSING** |
| App categorisation / baselines | 🔴 **MISSING** |
| Panic / Safe Mode | 🔴 **MISSING** |
| Whitelisting / trusted apps | 🔴 **MISSING** |
| Threat cards / alerts / notifications | 🔴 **MISSING** |
| Dashboard state machine | 🔴 **MISSING** |
| Decoy apps (`GoodCaller` / `VillainCaller`) | 🔴 **MISSING** |
| Package visibility (`<queries>`) | 🔴 **MISSING** |
| Boot persistence | 🔴 **MISSING** |
| Build passes at HEAD | ⚪ **UNCLEAR / NEEDS RUNTIME TESTING** (APK dated 1 min after HEAD commit strongly indicates yes) |
| Test suite passes | ⚪ **UNCLEAR / NEEDS RUNTIME TESTING** |
| Actual VPN runtime behaviour | ⚪ **UNCLEAR / NEEDS RUNTIME TESTING** (no forwarding path exists in code) |
| Demo device model / API level | ⚪ **UNCLEAR / NEEDS RUNTIME TESTING** |

---

*End of audit. No application code, build file, manifest, resource, or asset was modified during this run; the only file created is this report.*
