# THRAKSHA GUARDIAN — SCAN REPORT

## 1. Header
- **Repo path:** `C:\Users\kplee\AndroidStudioProjects\ThrakshaGuardian`
- **Scan datetime:** 2026-07-21 (evening scan; supersedes the earlier 2026-07-21 report at this path)
- **Method:** read-only static inspection — 7 scanner passes, each independently re-verified by an adversarial second pass (every claim re-read at the cited lines), plus a completeness audit. No code was modified; no build/gradle/git-state-changing command was run.
- **Git state:** NOT initialized (no `.git`; root `.gitignore` and `app/.gitignore` exist)
- **Overall health:** Clean, well-organized Compose/services skeleton — but it implements the OLD remote architecture (Ollama-on-LAN + Replit backend + TR overlay). The locked offline architecture is largely unimplemented; the ReAct loop is one routing change (plus a small JSON parser) away from working against a mock action source.

## 2. Executive Summary
- **23 Kotlin files** (21 main + 2 template tests), all packages under `com.thraksha.guardian`; applicationId, namespace, and the device-admin component all **match the locked identifiers exactly**. minSdk 26 / target+compile 36.
- **Gating blocker, revised:** there is **no full live JWT in the repo**. [docs/PROJECT_STATUS_REPORT.md:519](docs/PROJECT_STATUS_REPORT.md) contains only a 20-char header fragment `eyJhbGci…[MASKED]` truncated with a literal `...` in the source (the fragment base64-decodes to exactly `{"alg":"HS256",` — no payload, no expiry/claims recoverable). Line 520 holds a **full production userId GUID `907d5c07…[MASKED]`**, and §17 of the same doc exposes the device ADB serial and LAN topology. The full token lives only in on-device SharedPreferences. Action before `git init`: redact PROJECT_STATUS_REPORT.md lines ~515–522 (and §17 identifiers) and revoke the token at the Replit backend if it is still live.
- **ReAct gap confirmed as a routing issue:** `processCommand()` ([CompanionForegroundService.kt:190–212](app/src/main/java/com/thraksha/guardian/services/CompanionForegroundService.kt)) Toasts the AI reply (line 196) and posts it to the remote backend (198–204). `executeAction()` / `performTap()` / `clickNodeWithText()` (+ `performSwipe()`) all exist and are functionally complete in ThrakshaAccessibilityService.kt, but **`executeAction()` has zero callers** — only `performTap` is reachable, from one Dashboard debug button.
- **No JSON action schema or parser exists.** The `AutomationAction` sealed class (ThrakshaAccessibilityService.kt:168–181) is a ready-made internal action model, but nothing converts model output text into it (no kotlinx.serialization/Moshi/JSONObject; Gson is used only for HTTP envelopes).
- **The TR floating overlay is fully present and is the app's ONLY invocation path** (tap TR → SpeechRecognizer → processCommand). Removing it per the locked decision is a refactor, not a delete — it must be replaced by the Phase-5 persistent-notification invocation or the voice pipeline goes dark.
- **Locked data/security layer is 0% implemented:** no SQLCipher, no Keystore usage, no bundled rulepack, no encrypted logs, no 5-tier/confidence-threshold code anywhere. Only DataStore preferences (which store dropped-arch URLs). Bonus exposure: OllamaClient logs full request/response bodies to logcat **unconditionally in all build types** (OllamaClient.kt:252).
- **Dead weight:** 67.6 MiB Vosk speech model in assets with no Gradle dependency and zero code references (~40.5 MB compressed inside the 65.8 MB debug APK; ~241 MB counting all build-tree copies).
- **Play Protect risk:** five unused telephony permissions + SYSTEM_ALERT_WINDOW + RECORD_AUDIO + notification/accessibility/device-admin access + cleartext-HTTP config — a stalkerware-pattern permission profile that stripping the dropped arch mostly resolves.

## 3. Build & Manifest

**Identity & SDKs** ([app/build.gradle.kts](app/build.gradle.kts)): namespace `com.thraksha.guardian` (line 8), compileSdk 36 (9), applicationId `com.thraksha.guardian` (12), **minSdk 26** (13), targetSdk 36 (14), versionCode 1 (15), versionName "1.0" (16). Kotlin files: **23** (21 main, 1 unit test, 1 instrumented test); all 23 `package` declarations conform — none stray.

**Toolchain:** AGP 8.7.0 + Kotlin 2.2.20 (build.gradle.kts:3–4), Compose compiler plugin 2.2.20 (app:4), Gradle wrapper 9.4.1, checksum-pinned (gradle-wrapper.properties:4–5); Java 21 daemon toolchain via foojay resolver (gradle-daemon-jvm.properties:12) vs `jvmTarget "1.8"` sources (app:40). Smells: `kotlinCompilerExtensionVersion "1.5.8"` (app:49) is obsolete/ignored under the Kotlin 2.x compose plugin; deprecated `package=` attribute in AndroidManifest.xml:4 alongside AGP 8.7 namespace; `android.enableJetifier=true` (gradle.properties:18) unnecessary; `gradle/libs.versions.toml` is **unused** (zero `libs.` references in any .kts) and version-drifted.

**Dependencies** (app/build.gradle.kts:59–104): Compose BOM 2024.10.01, core-ktx 1.12.0, appcompat 1.6.1, material 1.11.0, constraintlayout 2.1.4, compose ui/material3/icons, activity-compose 1.9.3, lifecycle 2.8.7, coroutines 1.7.3, DataStore 1.0.0, JUnit/Espresso.
- ⚠ **DROPPED-ARCH (all actively used):** `okhttp:4.12.0` (83), `okhttp logging-interceptor` (84), `retrofit2:2.9.0` (85), `converter-gson` (86), `gson:2.10.1` (87). Retrofit's sole consumer is ConnectionChecker (imports at ConnectionChecker.kt:13–15); OkHttp serves OllamaClient + BackendService; Gson serves their envelopes (worth keeping for the future action-schema parser).
- **Absent:** SQLCipher/zetetic, Room, security-crypto/Keystore libs, Vosk, and any ML/LLM runtime (llama/mediapipe/mlc/tflite/gemini/aicore/onnx: zero hits — the missing engine is expected per locked plan; the missing data layer is a gap).

**Permissions** ([AndroidManifest.xml](app/src/main/AndroidManifest.xml), 17 total):

| Permission | Line | Verdict |
|---|---|---|
| SYSTEM_ALERT_WINDOW | 7 | ⚠ TR overlay only — remove with the overlay |
| INTERNET / ACCESS_NETWORK_STATE | 10–11 | ⚠ Only serve the dropped remote arch (offline phase) |
| RECORD_AUDIO / VIBRATE | 14–15 | Keep (voice) |
| FOREGROUND_SERVICE + MICROPHONE / DATA_SYNC / SPECIAL_USE | 18–21 | Keep; prune DATA_SYNC (backend-sync leftover, also in fgs type at line 84 and CompanionForegroundService.kt:103–105) |
| POST_NOTIFICATIONS | 24 | Keep (Phase-1 invocation) |
| NEARBY_WIFI_DEVICES (comment: "Android 16 LAN Access for Ollama", line 26) | 27–28 | ⚠ Dropped-arch, zero code usage — remove |
| REQUEST_IGNORE_BATTERY_OPTIMIZATIONS | 31 | Keep |
| CALL_PHONE, SEND_SMS, READ_SMS, RECEIVE_SMS, READ_PHONE_STATE | 34–38 | ⚠⚠ "(Day 3 APIs when implemented)" — **zero usage** (grep for SmsManager/TelecomManager/ACTION_CALL/Telephony/tel:/smsto: = 0 hits). High Play Protect risk — strip |

**Services & receiver** (manifest): ThrakshaVpnService — BIND_VPN_SERVICE, not exported, `foregroundServiceType="dataSync"` (68–77); CompanionForegroundService — not exported, `microphone|dataSync|specialUse` + `tools:ignore="ForegroundServiceType"` + SPECIAL_USE subtype "Voice-activated security monitoring and assistant." (80–89); ThrakshaNotificationListener — BIND_NOTIFICATION_LISTENER_SERVICE, not exported (92–100); ThrakshaAccessibilityService — BIND_ACCESSIBILITY_SERVICE, **exported=true** (103–114); ThrakshaDeviceAdminReceiver — BIND_DEVICE_ADMIN, exported (117–131) → component **`com.thraksha.guardian/.security.ThrakshaDeviceAdminReceiver` matches the locked identifier**. Policies ([device_admin.xml](app/src/main/res/xml/device_admin.xml):3–18): limit-password, watch-login, reset-password, force-lock, wipe-data, expire-password, encrypted-storage, disable-camera, disable-keyguard-features — all declared, none exercised in code. Oddity: an application-level `android.accessibilityservice` meta-data with `isAccessibilityTool=false` at manifest 52–55 (misplaced — this belongs in the a11y service XML config, and `accessibility_service_config.xml` has no such attribute).

**Config XMLs:** [network_security_config.xml](app/src/main/res/xml/network_security_config.xml):3–8 permits **cleartext** to `192.168.1.100`, `192.168.47.1`, `thraksha-assistant-backend.replit.app`, `localhost` (all dropped-arch; file wired at manifest:46). [accessibility_service_config.xml](app/src/main/res/xml/accessibility_service_config.xml):3–10 is maximally broad: `typeAllMask`, all packages, `canPerformGestures="true"`, `canRetrieveWindowContent="true"`.

## 4. Services & Boot

**Headline drift:** there is **no BootReceiver and no BOOT_COMPLETED / RECEIVE_BOOT_COMPLETED anywhere** (repo-wide grep: zero hits) — nothing literally starts at boot. The prior "2 of 4 start at boot" claim is wrong for current code; 2 of 4 start automatically *by other means*:

| Service | Class decl | Starts automatically? | How |
|---|---|---|---|
| CompanionForegroundService | CompanionForegroundService.kt:50 | ✅ on app launch | MainActivity chain: LaunchedEffect (120–122) → checkSamsungAndStart (192–202) → handlePermissionsAndStart (218–240) → checkOverlayPermission (242–252) → startFloatingService (254–266) |
| ThrakshaNotificationListener | ThrakshaNotificationListener.kt:12 | ✅ system-bound | OS binds it (and rebinds after reboot) once the user grants notification access; no app code starts it. Dashboard deep-links to Settings (DashboardScreen.kt:403–405) |
| ThrakshaAccessibilityService | ThrakshaAccessibilityService.kt:16 | Only after user enables in Settings | manifest 103–114; Dashboard deep-link (DashboardScreen.kt:302–310) |
| ThrakshaVpnService | ThrakshaVpnService.kt:34 | ❌ manual | Dashboard button → `prepareIntent`/`start()` (DashboardScreen.kt:321–327; ThrakshaVpnService.kt:254–269) |

Notes: **Samsung dead-end bug** — on a Samsung device not battery-whitelisted, WhitelistingDialog's *confirm* path (MainActivity.kt:172–175) opens battery settings and never resumes the permission chain, so the Companion service is not started that launch; only *dismiss* (176–179) continues. Companion has an Android-15 fgs-timeout self-restart (`onTimeout`, CompanionForegroundService.kt:121–124). `stopFloatingService()` (MainActivity.kt:268–275) is dead code — no caller, so there is no in-app stop path for the overlay service. App launch also auto-starts dropped-arch network activity: `OllamaClient.refreshFromConfig` (MainActivity.kt:81–83), an infinite backend health-poll loop (85–92), and `BackendService.registerDevice` with a swallowed-error catch (94–100).

## 5. ReAct Pipeline (the core gap)
- **`processCommand(prompt)`** — [CompanionForegroundService.kt:190–212](app/src/main/java/com/thraksha/guardian/services/CompanionForegroundService.kt): calls `OllamaClient.generateResponse(prompt)` (192); on Success → `Toast.makeText(…, "AI: ${result.data}", …)` (**196**) then `BackendService.saveVoiceCommand(command=prompt, intent=result.data, confidence=1.0f)` (197–204); on Error → Toast (207). **The reply terminates in UI feedback + a remote log write. No action routing exists.**
- **Gesture layer — exists, complete, mostly unreachable** ([ThrakshaAccessibilityService.kt](app/src/main/java/com/thraksha/guardian/services/ThrakshaAccessibilityService.kt)):
  - `performTap(x,y)` :66–73 — Path + `dispatchGesture` (296–333). Callers: `executeAction`:143 and **DashboardScreen.kt:288** (debug "Test Accessibility Tap") — the only live caller.
  - `clickNodeWithText(text)` :95–121 — node-tree search (`findNodeByText` 187–210, matches text/contentDescription) + click with clickable-ancestor walk (233–259). Caller: `executeAction`:162 only. **Zero external callers.**
  - `executeAction(AutomationAction)` :141–166 — exhaustive `when` over the sealed class; KDoc even says "for backend / Ollama-driven flows". **Zero callers anywhere.**
  - `performSwipe` :75–93 and `performGlobalBack/Home/Recents` :127–132 — reachable only via dead `executeAction`; `findClickableNodes` :212–217 has zero callers at all.
- **JSON action schema/parser: MISSING.** `AutomationAction` (sealed class, :168–181: Tap/Swipe/ClickText/GlobalBack/GlobalHome/GlobalRecents) is the internal model; nothing parses model text into it. No kotlinx.serialization/Moshi/JSONObject in app code; Gson usage is confined to HTTP envelopes (OllamaClient.kt:104, 180–181; BackendService payloads).
- **AI source:** the leftover remote `OllamaClient` only — OkHttp POST to `$baseUrl/api/generate` (OllamaClient.kt:109–113), defaults `http://192.168.1.100:11434` / `llama3.2:3b` (ThrakshaConfig.kt:72–73, DataStore-overridable). Secondary remote dependency in the same flow: BackendService → `https://thraksha-assistant-backend.replit.app` (`/api/voice/process`:178, `/api/auth/register`:110). **No on-device inference runtime exists** — expected per the locked plan. Also: OllamaClient's logging interceptor is `Level.BODY` unconditionally (OllamaClient.kt:252), writing every voice prompt and full AI reply to logcat in all build types.
- **Invocation flow today:** tap TR overlay (CompanionForegroundService.kt:284–286) → Google `SpeechRecognizer` (163–188) → `onResults` → `processCommand` (150–157). The overlay the locked plan removes is the *sole* trigger.
- **To close the loop:** (1) define the locked JSON action schema + Gson/kotlinx parser → `AutomationAction`; (2) route `processCommand` → parser → `ThrakshaAccessibilityService.instance?.executeAction(...)` (instance accessor exists, :378–383); (3) drive it first from a deterministic mock action source, independent of any engine.

## 6. Stubs & Partial Implementations

| Item | State vs locked spec | Evidence |
|---|---|---|
| `containsOtp()` | Stub — `return false`, `@Suppress("unused")`; its only call site is commented out; `OTP_REGEX_PATTERNS = emptyList()` | [ThrakshaNotificationListener.kt:117–120](app/src/main/java/com/thraksha/guardian/services/ThrakshaNotificationListener.kt), call 58–61, patterns :190. Sibling stubs: `redactNotification` 123–126, `isSuspiciousNotification` 129–135, `isSuspiciousAppNotification` 138–144, `alertSecurityGuardian` 147–153; `summarizeContent` 204–211 has zero callers. Listener otherwise parses + logs only (processNotification 37–75; MONITORED_PACKAGES 180–185) |
| VPN | Pure pass-through: TUN established for **all traffic** (`addRoute("0.0.0.0", 0)`, :89 w/ constants 245–246), then `input.read(buffer)` → `output.write(buffer, 0, length)` echo loop — zero inspection, parsing, or filtering | [ThrakshaVpnService.kt:83–98](app/src/main/java/com/thraksha/guardian/services/ThrakshaVpnService.kt) (establish), 123–152 (`processPackets`, write-back at 140) |
| DeviceAdmin | Status/logging only. Repo-wide grep for `lockNow|wipeData|setCameraDisabled|addUserRestriction|setLockTaskPackages`: **zero enforcement calls**. All DevicePolicyManager use = status checks (receiver :4/31/35/36; DashboardScreen.kt:356, 426). Callbacks just Toast/log (44–87) | [ThrakshaDeviceAdminReceiver.kt](app/src/main/java/com/thraksha/guardian/security/ThrakshaDeviceAdminReceiver.kt) |
| Encrypted DB | **MISSING** — no SQLCipher/zetetic, no Room, no SQLiteOpenHelper, no Keystore/EncryptedSharedPreferences/MasterKey anywhere (code + build files: zero hits) | repo-wide greps, re-verified twice |
| Bundled rulepack | **MISSING** — `assets/` contains only the Vosk model; `res/raw` doesn't exist; no rulepack/signature-verify code | assets listing; grep `rulepack|verifySign` = 0 |
| Encrypted logs | **MISSING** — no file logging at all (only the VPN TUN fd write); everything goes to plaintext logcat | grep FileWriter/openFileOutput etc. |
| 5-tier verification (60/80/95/99) | **MISSING** — only `confidence = 1.0f` hardcoded into the backend payload (CompanionForegroundService.kt:202) and an unrelated `CLICK_THRESHOLD` const | grep confidence/threshold/tier |
| ThrakshaConfig | Configures **only dropped-arch values**: Ollama URL/model, backend URL, poll interval (defaults :72–75) | [ThrakshaConfig.kt](app/src/main/java/com/thraksha/guardian/config/ThrakshaConfig.kt) |
| Enrollment | `ENROLLMENT_SECRET = ""` (BackendService.kt:38, "TODO Day 3") → field omitted → `/api/auth/register` is effectively unauthenticated | BackendService.kt:35–38, 103–105 |
| Dashboard metrics | Hardcoded placeholder — "Waiting for Security Guardian (Day 6)" | DashboardScreen.kt:159–174 |

## 7. Security & Secrets
- **Git:** not initialized. Root [.gitignore](.gitignore) (15 lines) covers `.gradle`, `/build`, `local.properties` (twice, lines 3+15), `.idea` caches, `*.iml` — **missing `*.jks`, `*.keystore`, `.env`** (no such files currently exist; verified by find). `app/.gitignore` = `/build` only.
- **The JWT (§3 claim located & downgraded):** [docs/PROJECT_STATUS_REPORT.md:519](docs/PROJECT_STATUS_REPORT.md) — `<string name="auth_token">eyJhbGci…[MASKED]...</string>`. The value is a **20-char header-only fragment, truncated with a literal `...` in the source**; it decodes to exactly `{"alg":"HS256",` — no payload/exp/issuer recoverable, so the committed text is not itself a usable credential. However line **520** carries a full production **userId GUID `907d5c07…[MASKED]`** (the only GUID in the repo — regex-swept), and §17 (lines ~458–531) exposes the device ADB serial `[REDACTED-DEVICE-SERIAL]`, LAN IPs, and backend behavior. **Action:** redact lines ~515–522 + §17 identifiers, and revoke/rotate the real token at the backend (it lives on-device in `backend_prefs`), before `git init`.
- **Full secret sweep results** (source+docs, excluding build/.gradle/.idea/vosk; patterns: eyJ, Bearer, api[_-]key, secret, token, password/passwd, ENROLLMENT_SECRET, -----BEGIN, AIza, AKIA, ghp_, gho_, sk-): the only credential-adjacent findings are the doc fragment above; `ENROLLMENT_SECRET = ""` (empty — a gap, not a leak); runtime `Bearer $token` header construction (BackendService.kt:179, 219). Everything else = false positives (prose/API tables). Targeted `eyJ` sweep over `app/build` intermediates: **0 matches**. No keystore/`.env`/signingConfig anywhere; `local.properties` = `sdk.dir` only; `gradle.properties` clean.
- **Runtime credential-hygiene issues in dropped code** (moot once the backend code is deleted, listed for completeness): token stored in plaintext SharedPreferences (BackendService.kt:123–127); token duplicated into the JSON request body (:167) in addition to the header; userId logged to logcat (:129) and full server response bodies logged on failure (:132, 191); BODY-level HTTP logging debug-gated in BackendService (:52–56) but **unconditional in OllamaClient (:252)**; `ANDROID_ID` harvested and sent off-device (:96, 101).
- **Play-Protect / static risks:** the permission combo (overlay + mic + internet + notification access + accessibility with gestures + device admin w/ wipe policy + 5 unused telephony perms) pattern-matches commercial stalkerware; cleartext-HTTP allowances (network_security_config.xml:3–8); exported accessibility service (normal, BIND-permission-guarded); `android:allowBackup="false"` (manifest:41) is good. Stripping the telephony block + dropped-arch perms is the fastest de-risk.

## 8. Reconciliation vs Locked Decisions

| Leftover dropped-arch item | Path | Still wired in? | Safe to remove? |
|---|---|---|---|
| OllamaClient (LAN HTTP) | ai/OllamaClient.kt (262 lines) | Yes — MainActivity:82; CompanionForegroundService:192; SettingsScreen:208–214, 321; DashboardScreen:202 | After rerouting `processCommand` to the mock/schema source and dropping the two UI test buttons |
| BackendService (Replit) | network/BackendService.kt (231) | Yes — MainActivity:96; CompanionForegroundService:198–204; DashboardScreen:226–256. Sole `BuildConfig` consumer (:10, :52 — `buildConfig=true` becomes removable with it) | Yes, with its call sites |
| ConnectionChecker | network/ConnectionChecker.kt (~206; Retrofit `@GET("/api/health")` :57, builder :88–94) | Yes — MainActivity:30, 46–52, 85–92 (poll loop), 188; SettingsScreen:256–268; DashboardScreen; ConnectionStatusIndicator; ConnectionStatusBottomSheet | Yes, together with its two status-UI files; **sole Retrofit consumer** (:13–15) → retrofit + converter-gson deps go with it |
| ThrakshaConfig (dropped-arch values) | config/ThrakshaConfig.kt:72–75 | Yes — referenced by all three above + MainActivity + SettingsScreen | Prune/replace once the cluster goes (keep the DataStore pattern for future config) |
| **TR floating overlay** | CompanionForegroundService.kt: overlay imports :13/28–34, fields :52–54, `setupFloatingButton` :222–289 (TYPE_APPLICATION_OVERLAY :233, addView :288), `createButtonView` :291–313 (`text = "TR"` :298); MainActivity overlay flow :69–76, 242–252; manifest :6–7 | Yes — **it is the sole invocation path** for the entire voice→AI flow | **Not a clean delete** — refactor: keep the foreground service + voice recognizer, strip the overlay view + SYSTEM_ALERT_WINDOW, and land Phase-5 notification invocation in the same change |
| NEARBY_WIFI_DEVICES ("for Ollama") | manifest 26–28 | Zero code usage | Yes |
| FOREGROUND_SERVICE_DATA_SYNC (backend-sync leftover) | manifest :20, :84; CompanionForegroundService.kt:103–105 | In fgs type declarations only | Yes, prune with backend removal |
| Cleartext network config | network_security_config.xml:4–7 + manifest attr :46 | Yes (manifest reference) | Remove domains AND the manifest attribute/file together |
| INTERNET / ACCESS_NETWORK_STATE | manifest 10–11 | Only by the dropped cluster | Yes, this phase |
| Retrofit/OkHttp/logging deps | app/build.gradle.kts:83–87 | Yes (see above) | After code removal; consider keeping Gson for the action-schema parser |
| Supabase / cloud-LLM SDKs or keys (Gemini/AICore/GPT/Claude/Groq/OpenAI) | — | **Not present** — zero hits in code/build files (only historical doc prose) | n/a |

**Conflicts with locked decisions:** (1) live remote AI + backend path vs offline-only; (2) TR overlay present (and load-bearing) vs "removed entirely"; (3) data/security layer absent vs SQLCipher+Keystore/signed rulepack/encrypted logs/5-tier; (4) no internal pub-sub event bus — components call singletons directly (`ThrakshaAccessibilityService.instance`, `OllamaClient`, `BackendService` objects); (5) no JSON action-schema boundary on AI output; (6) five unused telephony permissions. **Matches:** package name, applicationId/namespace, device-admin component, minSdk 26, no Gemini Nano/AICore, no wake-word engine wired (Vosk is inert data, not code).

## 9. Dead Assets
- **Vosk model** — `app/src/main/assets/vosk-model-small-en-us-0.15/`: 15 files, **70,899,003 bytes (67.6 MiB)** (largest: Gr.fst 24.0 MB, HCLr.fst 22.4 MB, final.mdl 16.0 MB). **No Gradle dependency, zero code references** (voice uses `android.speech.SpeechRecognizer`). Copies on disk: source (67.6 MB) + `app/build/intermediates` merged (67.6 MB) + compressed (40.5 MB) + inside `app-debug.apk` (40.5 MB of its 65.8 MB total) ≈ **241 MB** footprint. Deleting the folder shrinks the APK by ~40 MB (~62%).
- **Other dead weight:** `res/values/colors.xml` — all 7 template colors unreferenced (Compose colors live in ui/theme/Color.kt); `strings.xml:2` `app_name` unreferenced (manifest hardcodes labels at :45/96/107; the other 5 strings are live); empty template dir skeletons (`app/src/main/java/com/example/`, test/androidTest `com/example/thrakshaguardian/`, and a malformed empty tree `app/src/main/res/app/src/main/java/com/thraksha/guardian/services/`); template tests (ExampleUnitTest `2+2=4`, ExampleInstrumentedTest); unused drifted `gradle/libs.versions.toml`.
- **Historical docs** (expectedly conflicting with locked decisions — keep or archive, but redact): `docs/PROJECT_STATUS_REPORT.md` (May 28–29, 2026; contains the sensitive fragments per §7) and this file's predecessor.

## 10. Task List (this phase, grounded in findings)
Already done — skip: `.gitignore` exists (needs 3 additions); package/receiver/appId identifiers correct; minSdk 26; gesture primitives + `AutomationAction` model built; a11y service config already grants gestures.

**Phase 0 — pre-git blockers:** (1) redact docs/PROJECT_STATUS_REPORT.md lines ~515–522 + §17 device identifiers; revoke the on-device token at the Replit backend if still live; re-grep to confirm clean. (2) extend `.gitignore` with `*.jks`, `*.keystore`, `.env`. (3) `git init` + first commit.
**Phase 1 — reconcile to locked architecture:** (4) delete the dropped cluster — OllamaClient, BackendService, ConnectionChecker, ConnectionStatusIndicator, ConnectionStatusBottomSheet, backend bits of ThrakshaConfig/MainActivity/Dashboard/Settings — and refactor CompanionForegroundService to drop the TR overlay (keep service+voice). (5) strip permissions: telephony ×5, SYSTEM_ALERT_WINDOW, NEARBY_WIFI_DEVICES, INTERNET, ACCESS_NETWORK_STATE, FGS_DATA_SYNC + fgs types; remove network_security_config. Quick win alongside: delete the Vosk model (−67.6 MB source / −40 MB APK) and dead template resources/dirs.
**Phase 2 — close the ReAct loop:** (6) define the locked JSON action schema + parser → `AutomationAction` (constrained output boundary). (7) route `processCommand` → parse → `executeAction()`; prove end-to-end with a **deterministic mock action source** before any engine exists.
**Phase 3 — on-device engine:** (8) research llama.cpp vs MediaPipe LLM Inference vs MLC LLM against the S20 FE floor (6 GB, SD865, Android 13); pick one. (9) quantize + integrate a 1B–3B model behind the schema as the real action source.
**Phase 4 — guardian stubs:** (10) implement `containsOtp()` + populate `OTP_REGEX_PATTERNS`; wire the commented call at listener 58–61. (11) VPN packet/metadata inspection (DNS/SNI heuristics minimum). (12) DeviceAdmin enforcement (lockNow → disable-camera → wipe) gated by the 60/80/95/99 tiers. (13) data layer: SQLCipher+Keystore DB, signed rulepack load+verify, encrypted logs, 5-tier wiring; introduce the internal event bus while rewiring.
**Phase 5 — invocation:** (14) persistent notification + quick action + Bixby/Google assistant hand-off (replaces the TR button; wake word deferred).

## 11. Practical Plan

| # | Task | Effort | Risk | Blockers |
|---|---|---|---|---|
| 1 | Redact doc secrets + revoke token | S | Low | None — do first |
| 2 | Extend .gitignore | S | Low | None |
| 3 | git init + first commit | S | Low | 1, 2 |
| 4 | Remove dropped-arch cluster + TR overlay refactor | M | Med — touches the only invocation path; pair with 14 or a temporary launcher action | 3 (commit first for safety) |
| 5 | Strip permissions + cleartext config | S | Low | 4 |
| 6 | JSON action schema + parser | S/M | Low | None (parallel-safe) |
| 7 | Route processCommand → executeAction via mock | M | Low | 6. **Independent of engine choice — do before Phase 3** |
| 8 | Engine research/selection (S20 FE floor) | M | Med | None (parallel with 6–7) |
| 9 | Integrate quantized 1–3B model | L | High (RAM/latency on 6 GB SD865) | 7, 8 |
| 10 | containsOtp + OTP regexes | S | Low | None |
| 11 | VPN inspection heuristics | L | High (perf/battery/correctness) | None |
| 12 | DeviceAdmin enforcement w/ tier gates | M | High (lock/wipe danger — needs 13's tiers) | 13 (partial) |
| 13 | Data layer: SQLCipher/Keystore, rulepack, enc. logs, tiers, event bus | L | Med | 3 |
| 14 | Persistent-notification invocation + assistant hand-off | S | Low | Best landed together with 4 |

**Key dependency called out:** the ReAct loop (6–7) does **not** need the engine (8–9) — validate the plumbing with the deterministic mock first, then swap the engine in behind the same schema.

## 12. Unverified / Open Questions
- **"Clean build" — UNVERIFIED** (building prohibited by scan rules). A built `app-debug.apk` (65.8 MB, in `app/build/outputs`) proves a past successful build, but current-config smells remain: obsolete `kotlinCompilerExtensionVersion 1.5.8` under Kotlin 2.2.20, and the deprecated manifest `package=` attribute under AGP 8.7.
- **Whether the Replit backend is still live** (determines whether/where the on-device token can be revoked) — not testable read-only/offline.
- **Whether any full JWT exists outside this repo** (device `backend_prefs`, old handoff docs elsewhere) — in-repo, only the truncated header fragment exists.
- **Runtime states** (which services the user has enabled on the phones, whether the legacy `com.example.thrakshaguardian` package is still installed per doc §17.1) — device facts, not repo facts.
