# PHASE 12.1 — RC2 REAL DEVICE SCAN

Guide reference: §§7, 8, 24. Device: Samsung Galaxy S20 FE (SM-G781B) `RZCW40LVBJD`,
Android 13. Build: RC2 release APK, signer `CN=Thraksha Guardian`, `debuggable=false`.

## 1. The headline result

| Metric | Ground truth | RC1 (release) | **RC2 (release)** |
| --- | --- | --- | --- |
| Total packages | 441 | 322 | **441** ✅ |
| System packages | 415 | 319 | **415** ✅ |
| User / third-party apps | 26 | 3 | **26** ✅ |
| Genuine owner-installed apps visible | 23 | **0** | **23** ✅ |
| `VisibilityScope` | — | `REDUCED` | `FULL` (no reduced-visibility warning shown) |

**RC2's release scanner reaches exact parity with device ground truth.** Not "substantially
improved" — identical. The 119-package gap RC1 had is closed completely.

Ground truth from `adb shell pm list packages` (441), `-s` (415), `-3` (26).

## 2. Scan details, read off the release build

From the app's own Scan results → SCAN DETAILS panel
(`screenshots/12_1_rc2_scan_details.png`):

```
441 apps checked · 14 items need attention

User apps                    26
System packages             415
Signing identities checked  441
APK fingerprints computed   441
Findings recorded            56
Known-threat matches          1
High-exposure profiles        0
Duration                   13.6 s
```

Every discovered package was genuinely processed, not merely counted: **441 signing
identities checked and 441 APK fingerprints computed**. Fingerprinting requires reading the
actual APK, so this rules out a scan that inflates its count by listing packages it cannot
inspect.

## 3. Genuine third-party apps analysed (§8)

Real, owner-installed applications now appear in the results with honest classifications:

| App | Status | Basis |
| --- | --- | --- |
| VillainCaller | **KNOWN THREAT** | ThreatPack indicator match (controlled sample) |
| Google TV (`com.google.android.videos`) | REVIEW | "currently allowed to use network and package visibility" |
| Samsung Notes (`com.samsung.android.app.notes`) | REVIEW | "currently allowed to use draw over other apps and media / storage and 1 more" |
| Tips (`com.samsung.android.app.tips`) | REVIEW | capability exposure |

Also present in the visible inventory and scanned: Facebook, LinkedIn, Outlook, Office,
Samsung Pay, Samsung Internet, Samsung Health, Google Photos/Docs, YouTube Music, Samsung
Members, Smart Switch, SmartThings — the full 23.

### The wording is evidence-grade

Every finding is phrased as what the app is **currently allowed** to do — a DECLARED /
GRANTED statement — never as observed behaviour. Samsung Notes is not accused of drawing
over anything; it is reported as *permitted* to. `High-exposure profiles: 0` means no real
app was escalated on static capability alone.

## 4. ThreatPack and Rulepack still honest (§9)

* Known-threat matches: **1** — VillainCaller only. Not one of the 23 real apps was
  flagged as a known threat, despite broad visibility exposing them all to the same
  pipeline.
* GoodCaller: **not** flagged. The good/villain discrimination survives broad visibility.
* Post-reboot audit shows the signed packs verifying through the production loader:
  `"Security check: 2 app(s) audited, 7 finding(s), rulepack v2 verified, 5 rule(s)…"`.
* Guardian scans itself under the same rules (no special-casing) and now honestly declares
  its own `QUERY_ALL_PACKAGES` in release, which RC1 under-reported.

## 5. Scanning modified nothing (§8, §24)

Checked immediately after the scan:

| Check | Result |
| --- | --- |
| Packages suspended | **0** |
| Packages disabled/uninstalled | **0** — count still 441 |
| `com.facebook.katana` | `installed=true suspended=false` |
| `com.linkedin.android` | `installed=true suspended=false` |
| `com.microsoft.office.outlook` | `installed=true suspended=false` |
| `com.google.android.videos` | `installed=true suspended=false` |
| `com.samsung.android.app.notes` | `installed=true suspended=false` |

**No automatic containment of any arbitrary real app.** This is enforced in code, not just
observed: on the DO AVD, `arbitraryPackage_isRefused`,
`goodCaller_isRefusedAsTarget_regardlessOfAuthority`, `guardian_cannotTargetItself` and
`restore_refusesNonAllowlistedTargets` all pass — containment refuses non-allowlisted
targets even *with* Device Owner authority.

## 6. Advice Mode is the default (§29)

* Settings → Protection reads **"Advice mode"** on a fresh install, with no user action.
* `dpm list-owners` → **"no owners"**. No Device Owner, no Profile Owner provisioned.
* Audit entries read `Policy decision — ADVISE VillainCaller`, i.e. advice issued, not
  enforcement executed.

## 7. Determinism and persistence

The scan was run twice — once on first launch, once after a full device reboot. Identical:

| | First scan | Post-reboot scan |
| --- | --- | --- |
| Apps checked | 441 | 441 |
| User / system | 26 / 415 | 26 / 415 |
| Findings | 56 | 56 |
| Known-threat matches | 1 | 1 |
| High-exposure profiles | 0 | 0 |
| Duration | 13.6 s | 16.4 s |

## 8. Reduced-visibility warning (§24)

Correctly **absent**. Both surfaces are gated on the live `visibilityScope()`
(`FindingsScreen.kt:131`, `ProtectPresentation.kt:256`), which returns `FULL` now that RC2
holds the permission. No code change was needed — the honest-reporting path RC1 exercised
simply stopped triggering, which is the correct outcome.

## Screenshots

`12_1_rc2_scan_result.png` · `12_1_rc2_findings.png` · `12_1_rc2_scan_details.png` ·
`12_1_rc2_post_reboot_scan.png` · `12_1_rc2_post_reboot_scan_details.png` ·
`12_1_rc2_settings.png`
