# Thraksha Guardian — Project Status Report

**Package:** `com.thraksha.guardian`  
**Report date:** May 28, 2026  
**Purpose:** Resume guide before integrating fixes and continuing development  

---

## 1. Executive Summary

Thraksha Guardian is an **early-stage Android security companion app** built with **Kotlin + Jetpack Compose**. It currently provides:

- A branded splash + debug dashboard UI
- Backend health monitoring (Replit API)
- Configurable Ollama LLM client (local LAN)
- A foreground floating-button service with voice → AI → backend pipeline
- Skeleton services for Accessibility, VPN, Notification listening, and Device Admin

**What it is NOT yet:** a ReAct agent, Plan-Verify-Learn system, or production Security Guardian. Those are planned phases with mostly logging stubs or TODO comments in place.

**Recent improvement (since initial diagnostics):** Settings screen + `ThrakshaConfig` DataStore — URLs and poll interval are no longer only hardcoded (defaults still exist as fallbacks).

---

## 2. Architecture at a Glance

```
┌─────────────────────────────────────────────────────────────────┐
│                        MainActivity                              │
│  Splash → Dashboard / Settings  │  Connection poll loop         │
│  Permission chain → start CompanionForegroundService            │
└────────────┬────────────────────────────────────────────────────┘
             │
    ┌────────┼────────┬──────────────┬─────────────────┐
    ▼        ▼        ▼              ▼                 ▼
 Thraksha  Ollama   Backend      Connection        Services (manual
 Config    Client   Service      Checker           enable unless debug)
 (DataStore)        (Retrofit/   (health API)
                     OkHttp)
```

### Source file map (21 Kotlin files)

| Area | Files |
|------|-------|
| Entry | `MainActivity.kt` |
| Config | `config/ThrakshaConfig.kt` |
| UI | `ui/screens/*`, `ui/components/*`, `ui/sheets/*`, `ui/theme/*` |
| Network | `network/ConnectionChecker.kt`, `network/BackendService.kt` |
| AI | `ai/OllamaClient.kt` |
| Services | `CompanionForegroundService`, `ThrakshaVpnService`, `ThrakshaAccessibilityService`, `ThrakshaNotificationListener` |
| Security | `security/ThrakshaDeviceAdminReceiver.kt` |

---

## 3. App Flow (What Happens on Launch)

1. **Splash** (~2.8s animated logo)
2. **MainActivity** starts three background coroutines:
   - Load Ollama config from DataStore
   - Poll backend health every N seconds (configurable, default 30)
   - Register device with backend (errors silently ignored)
3. **Permission chain** (after splash):
   - Samsung battery dialog (if Samsung + not whitelisted)
   - `POST_NOTIFICATIONS` + `RECORD_AUDIO`
   - Overlay permission (`SYSTEM_ALERT_WINDOW`)
   - Start `CompanionForegroundService` (floating "TR" button)
4. User lands on **Dashboard** (debug controls) or opens **Settings** via gear icon

```kotlin
// MainActivity.kt — startup coroutines
lifecycleScope.launch {
    OllamaClient.refreshFromConfig(this@MainActivity)
}

lifecycleScope.launch {
    val config = ThrakshaConfig.getInstance(this@MainActivity)
    while (true) {
        checkConnection()
        val intervalSeconds = config.getPollIntervalSeconds()
        delay(intervalSeconds * 1000L)
    }
}

lifecycleScope.launch {
    try {
        BackendService.registerDevice(this@MainActivity)
    } catch (_: Exception) {
        // Backend registration failed - continue anyway
    }
}
```

---

## 4. Configuration Layer (NEW)

**File:** `config/ThrakshaConfig.kt`  
**Storage:** DataStore Preferences (`thraksha_config`) — separate from auth SharedPreferences (`backend_prefs`).

| Key | Default | Used by |
|-----|---------|---------|
| `ollama_url` | `http://192.168.1.100:11434` | OllamaClient |
| `ollama_model` | `llama3.2:3b` | OllamaClient |
| `backend_url` | `https://thraksha-assistant-backend.replit.app` | BackendService, ConnectionChecker |
| `poll_interval_seconds` | 30 (range 10–120) | MainActivity health loop |

```kotlin
// ThrakshaConfig.kt — singleton + defaults
companion object {
    const val DEFAULT_OLLAMA_URL = "http://192.168.1.100:11434"
    const val DEFAULT_OLLAMA_MODEL = "llama3.2:3b"
    const val DEFAULT_BACKEND_URL = "https://thraksha-assistant-backend.replit.app"
    const val DEFAULT_POLL_INTERVAL_SECONDS = 30

    fun getInstance(context: Context): ThrakshaConfig { ... }
}
```

**Settings UI:** `ui/screens/SettingsScreen.kt`  
- Edit all config values  
- Test Ollama / backend before save  
- Save → DataStore + `OllamaClient.refreshFromConfig()`

---

## 5. Network Layer

### 5.1 ConnectionChecker

- **Endpoint:** `GET {backendUrl}/api/health`
- **Checks:** device network → backend status → database status in response JSON
- **UI:** `ConnectionStatusIndicator` + `ConnectionStatusBottomSheet`

```kotlin
// ConnectionChecker.kt — dynamic backend URL
private suspend fun resolveBackendUrl(context: Context, override: String?): String {
    val raw = override?.trim()?.trimEnd('/')
        ?: ThrakshaConfig.getInstance(context).getBackendUrl()
    return if (raw.endsWith("/")) raw else "$raw/"
}
```

### 5.2 BackendService

| Endpoint | Method | Auth |
|----------|--------|------|
| `/api/auth/register` | POST | None (deviceId + deviceName) |
| `/api/voice/process` | POST | Bearer token |
| `/api/security/log` | POST | Bearer token (**implemented, never called**) |

```kotlin
// BackendService.kt — token storage (SharedPreferences, not DataStore)
context.getSharedPreferences("backend_prefs", Context.MODE_PRIVATE)
    .edit()
    .putString("auth_token", token)
    .putString("user_id", userId)
    .apply()
```

**Known issue:** Registration failures are swallowed in MainActivity — user sees no error.

---

## 6. AI Layer

### OllamaClient

- Real HTTP client (OkHttp), not a stub
- `testConnection()` → `GET /api/tags`
- `generateResponse(prompt)` → `POST /api/generate` with configured model

```kotlin
// OllamaClient.kt — config-driven model
suspend fun generateResponse(prompt: String): OllamaResult<String> = withContext(Dispatchers.IO) {
    val requestData = mapOf(
        "model" to model,
        "prompt" to prompt,
        "stream" to false,
    )
    // POST to $baseUrl/api/generate
}
```

### Voice pipeline (CompanionForegroundService)

```
Tap floating "TR" button
    → Google SpeechRecognizer
    → OllamaClient.generateResponse(transcript)
    → Toast with AI response
    → BackendService.saveVoiceCommand(...)
```

```kotlin
// CompanionForegroundService.kt — processCommand
private fun processCommand(prompt: String) {
    launch {
        val result = OllamaClient.generateResponse(prompt)
        when (result) {
            is OllamaClient.OllamaResult.Success -> {
                Toast.makeText(this@CompanionForegroundService, "AI: ${result.data}", Toast.LENGTH_LONG).show()
                launch(Dispatchers.IO) {
                    BackendService.saveVoiceCommand(
                        context = this@CompanionForegroundService,
                        command = prompt,
                        intent = result.data,
                        confidence = 1.0f,
                    )
                }
            }
            // ...
        }
    }
}
```

### What's missing for "AI agent"

- No ReAct loop (thought → action → observation)
- No planner / verifier / learner
- Accessibility `executeAction()` exists but is **not wired to Ollama**
- Dashboard "Voice Activation Paused" button is a stub (overlay voice still works)

---

## 7. Services Status

| Service | Auto-start? | Functional today | Notes |
|---------|-------------|------------------|-------|
| **CompanionForegroundService** | Yes (after permissions) | ✅ Overlay + voice + Ollama | Core working feature |
| **ThrakshaAccessibilityService** | Manual (Settings) | 🟡 Gestures work via debug button | Events logged only |
| **ThrakshaNotificationListener** | Manual (Settings) | 🟡 Logs notifications | OTP/phishing = Day 6 TODOs |
| **ThrakshaVpnService** | Manual (Dashboard) | 🟡 TUN pass-through only | No packet inspection |
| **ThrakshaDeviceAdminReceiver** | Manual / ADB | 🟡 Status check only | No lockNow/wipe calls |

### Accessibility — automation API ready but isolated

```kotlin
// ThrakshaAccessibilityService.kt
fun executeAction(action: AutomationAction): ActionResult = when (action) {
    is AutomationAction.Tap -> performTap(action.x, action.y) ...
    is AutomationAction.ClickText -> clickNodeWithText(action.text) ...
    AutomationAction.GlobalBack -> performGlobalBack()
    // ...
}
// ↑ Never called from Ollama, Backend, or voice pipeline
```

### Notification listener — Day 6 placeholders

```kotlin
// ThrakshaNotificationListener.kt
// TODO Day 6: OTP pattern detection
// TODO Day 6: Phishing detection
// TODO Day 6: Suspicious app notifications
private fun containsOtp(text: String?): Boolean = false  // stub
```

### VPN — runs but monitors nothing

```kotlin
// ThrakshaVpnService.kt — pass-through only
when {
    length > 0 -> output.write(buffer, 0, length)  // no inspection
}
```

---

## 8. UI Screens

| Screen | Path | Purpose |
|--------|------|---------|
| Splash | `ui/screens/SplashScreen.kt` | Brand intro |
| Dashboard | `ui/screens/DashboardScreen.kt` | Debug controls, service tests |
| Settings | `ui/screens/SettingsScreen.kt` | Config editor (NEW) |

Dashboard still shows **"SETUP INCOMPLETE"** and hardcoded activity feed — not production-ready.

Navigation: boolean flags in MainActivity (`isSplashFinished`, `showSettings`) — no Navigation Compose.

---

## 9. Permissions

| Permission | Declared | Runtime request | Actually used |
|------------|----------|-----------------|---------------|
| SYSTEM_ALERT_WINDOW | ✅ | Settings intent | Floating button |
| INTERNET | ✅ | — | All HTTP |
| ACCESS_NETWORK_STATE | ✅ | — | ConnectionChecker |
| RECORD_AUDIO | ✅ | ✅ | SpeechRecognizer |
| POST_NOTIFICATIONS | ✅ | ✅ (API 33+) | FGS notifications |
| FOREGROUND_SERVICE* | ✅ | — | Companion + VPN |
| CALL_PHONE, SEND_SMS, READ_SMS, READ_PHONE_STATE | ✅ | ❌ | **Not used — remove or implement** |
| NEARBY_WIFI_DEVICES | ✅ | ❌ | **Not used** |

---

## 10. Build Configuration

```kotlin
// app/build.gradle.kts
minSdk = 26
targetSdk = 36
compileSdk = 36
```

**Present:** Compose, OkHttp, Retrofit, Gson, Coroutines, DataStore  
**Missing (for roadmap):** Room, WorkManager, Navigation Compose, Hilt/Koin, Supabase, Gemini/AICore, ReAct agent libs

---

## 11. Feature Matrix — Install APK Today

| Feature | Status | Blocker / note |
|---------|--------|----------------|
| App launches | ✅ | — |
| Splash + Dashboard | ✅ | — |
| Settings screen | ✅ | Configure Ollama IP for your LAN |
| Backend health indicator | ⚠️ | Needs Replit backend reachable |
| Device registration | ⚠️ | Silent failure on error |
| Floating button | ⚠️ | Needs overlay + mic permissions |
| Voice → Ollama | ⚠️ | Needs Ollama on LAN + correct Settings URL |
| Voice → backend save | ⚠️ | Needs registration + backend up |
| Accessibility automation | ⚠️ | Manual enable + debug button only |
| VPN security | ❌ | Pass-through only |
| OTP / phishing protection | ❌ | Not implemented |
| ReAct / agent logic | ❌ | Not started |
| Device lockdown | ❌ | Policies declared, no API calls |

---

## 12. Known Issues & Technical Debt

### Critical
1. **No agent loop** — Ollama returns text to Toast only; no actions executed
2. **Registration errors hidden** — empty catch in MainActivity
3. **Auth token in plain SharedPreferences**
4. **Empty enrollment secret** — open registration (`BackendService` TODO Day 3)

### High
5. Unused SMS/phone permissions (Play Store risk)
6. `logSecurityEvent()` never called
7. VPN routes all traffic with zero benefit today
8. Dashboard voice button says "paused" but overlay voice works (confusing UX)
9. `stopFloatingService()` dead code — no UI to stop service

### Medium
10. No local event database (Room)
11. No WorkManager for background sync
12. Hardcoded debug tap coordinates (500, 1000)
13. Orphaned `gradle/libs.versions.toml` (not used by app module)

---

## 13. Recommended Resume Order

### Phase A — Stabilize (1–2 days)
1. Fix registration error surfacing (Toast or dashboard banner)
2. Point Settings → your Ollama LAN IP and verify Test Connection
3. Remove unused telephony permissions OR implement Day 3 APIs
4. Unify voice UX (dashboard vs overlay)
5. Wire `logSecurityEvent()` from accessibility/notification logs

### Phase B — ReAct Agent MVP (3–5 days)
1. Create `ReActAgent.kt` — prompt → parse action JSON → `executeAction()` → observe
2. Connect voice pipeline to agent instead of raw `generateResponse`
3. Add action result feedback into next LLM turn

### Phase C — Passive Logging (2–3 days)
1. Room database for events
2. Persist accessibility + notification events
3. WorkManager batch upload to backend

### Phase D — Security Guardian (Day 6 scope)
1. OTP regex + notification redaction
2. VPN packet metadata (DNS/SNI at minimum)
3. Real dashboard metrics (replace placeholders)

---

## 14. Key Code Snippets Reference

### Splash timing
```kotlin
// SplashScreen.kt
LaunchedEffect(Unit) {
    logoVisible = true
    delay(800)
    wordmarkVisible = true
    delay(2000)
    onComplete()
}
```

### Brand colors
```kotlin
// Color.kt
val DarkForestGreen = Color(0xFF1A2E1A)
val GoldenYellow = Color(0xFFF5C518)
val StrongGreen = Color(0xFF00A651)
```

### Health check response parsing
```kotlin
// ConnectionChecker.kt
if (response.status == "ok" || response.status == "up") {
    val (databaseHealthy, databaseError) = evaluateDatabaseHealth(response)
    // databaseHealthy from response.info/details["database"].status
}
```

### Device admin — check only, no lock
```kotlin
// DashboardScreen.kt — button handler
when {
    isDeviceOwner -> Toast.makeText(context, "Device Owner (Level 4)...", ...)
    isAdminActive -> Toast.makeText(context, "Device Admin (Level 3)...", ...)
    else -> openSettingsPage(context, ACTION_SECURITY_SETTINGS)
}
```

---

## 15. Testing Checklist (Manual — No Device Connected Yet)

Use this when you resume on a physical phone:

- [ ] Cold start → splash → dashboard
- [ ] Open Settings → set Ollama URL to your PC/LAN IP → Test Connection
- [ ] Test Backend → expect green if Replit is up
- [ ] Save Settings → confirm "✓ Saved"
- [ ] Grant overlay + mic → floating "TR" button appears
- [ ] Tap button → speak → verify Ollama Toast + backend save
- [ ] Enable Accessibility → Dashboard "Test Accessibility Tap"
- [ ] Enable Notification Listener → send test notification → check logcat `ThrakshaNotifListener`
- [ ] Start VPN → confirm notification → verify internet still works
- [ ] Enable Device Admin → Dashboard status Toast

**Logcat tags to watch:**
`OllamaClient`, `BackendService`, `ConnectionChecker`, `CompanionForegroundService`, `ThrakshaA11y`, `ThrakshaNotifListener`, `ThrakshaVPN`

---

## 16. Document History

| Date | Change |
|------|--------|
| Initial diagnostics | Full codebase audit — hardcoded URLs, no Settings |
| May 2026 | Added ThrakshaConfig + SettingsScreen + dynamic URLs |
| May 29, 2026 | Live ADB runtime scan — Section 17 added (Samsung SM-G781B) |

---

---

## 17. Runtime Behavior Scan (Live Device — May 29, 2026)

**Device:** Samsung **SM-G781B** (Galaxy S20 FE 5G)  
**Android:** 13 (API 33)  
**ADB ID:** `[REDACTED-DEVICE-SERIAL]`  
**WiFi IP:** `[REDACTED-IP]` (subnet `[REDACTED-SUBNET]`)  

### 17.1 Installed APK vs Source Code

| Item | On phone | In repo |
|------|----------|---------|
| Package | `com.thraksha.guardian` v1.0 (code 1) | Same |
| Last installed | **2026-05-21** | Settings + DataStore added **after** this date |
| DataStore (`thraksha_config`) | **Missing** on device | Present in source |
| Settings screen | **Not on installed APK** | Present in source |

**Action required:** Deploy latest debug build from Android Studio (`Run` ▶) or `gradlew installDebug` before testing Settings.

Legacy package also installed: `com.example.thrakshaguardian` (battery-whitelisted; consider uninstalling).

### 17.2 Permissions (Runtime State)

| Permission | Granted | Notes |
|------------|---------|-------|
| POST_NOTIFICATIONS | ✅ | |
| RECORD_AUDIO | ✅ | |
| INTERNET / ACCESS_NETWORK_STATE | ✅ | |
| SYSTEM_ALERT_WINDOW | ✅ (appops allow) | System showed overlay notification on launch |
| REQUEST_IGNORE_BATTERY_OPTIMIZATIONS | ✅ | On Doze whitelist |
| FOREGROUND_SERVICE* | ✅ | |
| CALL_PHONE / SMS / READ_PHONE_STATE | ❌ | Unused — correctly not granted |
| NEARBY_WIFI_DEVICES | ❌ | Unused |

### 17.3 Services — Actual Runtime State

| Service | Running? | Evidence |
|---------|----------|----------|
| **CompanionForegroundService** | ✅ **YES** | `isForeground=true`, notification id 1001 |
| **ThrakshaNotificationListener** | ✅ **YES** | `Notification listener connected` in logcat |
| **ThrakshaAccessibilityService** | ✅ **YES** | Listed in `enabled_accessibility_services` |
| **ThrakshaVpnService** | ❌ NO | Not in active services |
| **Device Admin** | 🟡 Active admin | `ThrakshaDeviceAdminReceiver` active; **not** device owner |

### 17.4 Network Behavior (Logcat on Cold Start)

**Backend health check — SUCCESS:**
```
ConnectionChecker: Backend healthy (status=ok, db=true, 614ms)
```

**Device registration — FAIL (3 retries):**
```
POST /api/auth/register → 400 Bad Request
{"message":["enrollmentSecret is required","enrollmentSecret must be a string"]}
```

**Root cause:** Backend now requires `enrollmentSecret`; app sends empty/missing secret (`BackendService.ENROLLMENT_SECRET = ""`).

**Workaround on device:** Old JWT still stored from May 21 registration:
```xml
<!-- backend_prefs.xml on device -->
<string name="auth_token">[REDACTED-JWT]</string>
<string name="user_id">[REDACTED-USER-ID]</string>
```
Voice command saves may work until token expires — new installs will fail registration entirely.

### 17.5 Ollama Reachability

From phone shell:
```bash
curl http://192.168.1.100:11434/api/tags  →  connection failed (HTTP 000)
```

Default Ollama IP **not reachable** from this device. Phone is on `[REDACTED-IP]`; Ollama must run on a host reachable on LAN (find PC IP via `ipconfig`, update in Settings after installing latest APK).

### 17.6 Observed Launch Sequence (8 seconds)

1. App force-stopped and relaunched via ADB
2. Notification listener reconnected immediately
3. Backend register attempts started (failed 400)
4. ConnectionChecker reported healthy in parallel
5. Samsung system notification: *"Thraksha Guardian is displaying over other apps"*
6. CompanionForegroundService started as foreground
7. User returned to launcher (app backgrounded)

### 17.7 Behavioral Test Matrix (Device-Verified)

| Feature | Verified | Result |
|---------|----------|--------|
| App launches | ✅ | No crash |
| Backend health indicator | ✅ | Backend OK (~614ms) |
| Device registration | ❌ | 400 — enrollmentSecret required |
| Floating service starts | ✅ | Foreground service active |
| Overlay permission | ✅ | Working (appops + system notice) |
| Accessibility enabled | ✅ | Pre-enabled by user |
| Notification listener | ✅ | Pre-enabled; logs notifications |
| VPN | ❌ | Not started |
| Ollama at default IP | ❌ | Unreachable from phone |
| Settings screen | ⏸️ | Not on installed APK — needs redeploy |

### 17.8 Priority Fixes Confirmed by Device Scan

1. **CRITICAL:** Implement `enrollmentSecret` flow — registration broken for fresh installs
2. **CRITICAL:** Install latest APK with Settings + DataStore
3. **HIGH:** Configure Ollama URL to actual PC LAN IP (not `192.168.1.100` unless confirmed)
4. **HIGH:** Surface registration failure in UI (currently silent in MainActivity)
5. **MEDIUM:** Uninstall legacy `com.example.thrakshaguardian`
6. **MEDIUM:** Test voice pipeline manually after Ollama URL fix

---

*Sections 1–16: static codebase analysis. Section 17: live ADB scan on Samsung SM-G781B.*
