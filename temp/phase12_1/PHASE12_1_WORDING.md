# PHASE 12.1 — WORDING CORRECTION + ABSOLUTE-CLAIM SWEEP (TASK C §18–19)

## 1. The reported defect

Samsung-facing battery-optimisation dialog, `ui/components/WhitelistingDialog.kt`.

It contained **two** overclaims, not one:

```kotlin
// before
"To keep Thraksha's \"eyes and ears\" active 24/7, you must disable battery
 optimization for this app.\n\nOn the next screen, please select \"Unrestricted\"
 or disable optimization."

"This ensures the AI stays awake even when your phone is in your pocket."
```

Why each is false:

| Claim | Reality |
| --- | --- |
| `"eyes and ears" active 24/7` | Thraksha monitors only while its services are running. There is no continuous surveillance, and the app makes no attempt at it. |
| `ensures the AI stays awake` | A battery-optimisation exemption makes Android *less likely* to pause background work. It guarantees nothing, and the local LLM is loaded on demand — it is not resident. |

## 2. Replacement

```kotlin
// after
"Samsung's battery optimisation can pause Thraksha's background services while you
 are not using the app. Exempting Thraksha makes it less likely they are paused.
 \n\nOn the next screen, please select \"Unrestricted\" or turn optimisation off."

"Protection monitoring is available while Thraksha is active. This setting helps it
 keep running — it does not guarantee uninterrupted operation."
```

The second line uses the guide's own suggested phrasing (§18) and adds the explicit
non-guarantee. The dialog's purpose — get the user to the Samsung setting — is unchanged;
only the claim about what that setting achieves is corrected. No layout, no control flow,
no other UI touched.

## 3. Targeted absolute-claim sweep (§19)

Swept `app/src/main` (Kotlin + resources) for: `always protected`, `100%`, `fully secure`,
`totally/completely secure`, `virus free`, `malware free`, `monitoring 24/7`, `always on`,
`round the clock`, `blocks all`, `stops all`, `prevents all`, `all threats`, `every threat`,
`connection is secure`, `device is safe`, `you are safe`, `guaranteed`, `ensures`,
`threat free`, `complete/total protection`, `maximum security`, `bank-grade`,
`military-grade`, `unhackable`, `impenetrable`.

### Result: one defective location, already fixed above. No other user-facing overclaim exists.

Every other hit is one of three benign categories:

| Category | Examples |
| --- | --- |
| **Code comments / internal identifiers** | `AutomationSafetyPolicy.kt:51` "Packages a routine must never be asked to launch"; `NetworkGuardState.kt:13` the literal outcome enum `"BLOCKED"`. |
| **Factual state reporting** | `NetworkGuardScreen` renders `"Blocked"` only for a packet that was genuinely dropped before forwarding; `NetworkGuardEngine.kt:177` even qualifies a success as *"remote delivery is not claimed"*. `CompanionForegroundService` says `"Security monitoring is running."` — true whenever that notification is visible. |
| **Existing anti-overclaim disclaimers** | `SettingsInfoScreens.kt:153` — *"No security tool can promise a device is free of every threat."* `FindingsScreen.kt:225` — *"'Watching' … is not a guarantee that the app is safe."* |

The Protect surface already carries a documented no-absolute-claims invariant
(`ProtectPresentation.kt:24–25`): *"There is no branch that produces 'safe', 'secure' or
'protected'; the best outcome the app can state is 'No urgent threats found'."* The sweep
confirms that invariant holds in the shipped code.

`res/values/strings.xml` contains six strings, all functional (app name, notification
channel labels, the accessibility-service description which already declares
*"This is not a disability assistance tool"*). No claims present.

`WhitelistingDialog.kt` is pre-Phase-11B UI that the Phase 11B rewrite did not reach, which
is why it was the sole survivor.

## 4. Not rewritten

Per §19, only genuinely misleading wording was changed. No screen was redesigned, no copy
was "improved", and no other file's text was touched.

## 5. Verification

`:app:assembleDebug :app:testDebugUnitTest` → **BUILD SUCCESSFUL**, 213 unit tests,
0 failures, after the wording and versioning changes.

On-device screenshot of the corrected dialog: pending RC2 install
(`screenshots/12_1_wording_dialog.png`).
