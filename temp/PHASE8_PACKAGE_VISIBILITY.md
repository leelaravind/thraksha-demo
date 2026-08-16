# PHASE 8 — PACKAGE VISIBILITY INVESTIGATION & DECISION

**Date:** 2026-08-14
**Applies to:** Thraksha Guardian `com.thraksha.guardian`, `targetSdk 36`, `minSdk 26`
**Demo devices:** Samsung SM-G781B (S20 FE, Android 13 / API 33), AOSP `thraksha_do` AVD (Android 13, Device Owner)

---

## 1. The Android mechanism

Since Android 11 (API 30), an app targeting API 30+ sees a **filtered** view of the
package registry ("package visibility filtering"). `PackageManager.getInstalledPackages()`,
`getPackageInfo()`, `getInstalledApplications()` etc. silently omit — or throw
`NameNotFoundException` for — any package the caller has not been granted visibility into.

Visibility is granted by, in increasing breadth:

1. **Automatic visibility** — no declaration needed: the app itself, the system
   ("packages that any app can always see": e.g. the installer that installed you,
   apps that launched you, some core system components).
2. **Targeted `<queries><package …/></queries>`** — named packages only. This is what
   Thraksha used through Phase 7: exactly `com.thraksha.demo.goodcaller` and
   `com.thraksha.demo.villaincaller`.
3. **Intent-based `<queries><intent …/></queries>`** — every package that can resolve
   the declared intent (e.g. all launcher apps).
4. **`QUERY_ALL_PACKAGES`** (normal-protection install-time permission) — the full
   registry view, equivalent to pre-Android-11 behaviour. Not a runtime prompt; granted
   at install.

Device Owner status does **not** by itself bypass visibility filtering for
`PackageManager` enumeration on the APIs Thraksha uses.

## 2. What Thraksha could see before Phase 8

With only the targeted `<queries>` (state at the Phase 7 baseline):

* the two decoys (when installed), Guardian itself, and the small automatic set;
* **not** the ~441 packages actually present on the S20 FE (shell ground truth
  2026-08-14: `adb shell pm list packages | wc -l` → **441** total, **26**
  third-party/user apps).

That is insufficient for the Phase 8 acceptance test ("analyze applications whose
identities were not known when Thraksha was built"), so broader visibility **is
required** for the generalized scanner.

## 3. Options considered

| Option | Coverage | Play posture | Verdict |
|---|---|---|---|
| Keep targeted `<queries>` | 2 decoys only | Clean | Fails Phase 8's core requirement |
| Intent-based `<queries>` (`ACTION_MAIN`/`CATEGORY_LAUNCHER`) | Launcher apps only (~subset of user apps, misses non-launcher services/admin apps) | Clean | Partial coverage; a security scanner that cannot see non-launcher packages has a structural blind spot it would have to disclose on every scan |
| `QUERY_ALL_PACKAGES` in the **main** manifest | Full registry | Play-restricted; would ship in release | Over-broad for release without a Play declaration |
| **`QUERY_ALL_PACKAGES` in a `debug`-only manifest overlay** | Full registry in the demo build | Release build unchanged (targeted `<queries>` only) | **CHOSEN** |

## 4. Decision

`app/src/debug/AndroidManifest.xml` declares `QUERY_ALL_PACKAGES` for the **debug build
type only** — the build the private investor demo installs via `adb`. The main manifest
is unchanged and continues to declare only the two targeted `<package>` entries.

Rationale:

* **Deliberate, not silent:** the broadening is an explicit, commented, build-scoped
  manifest overlay plus this document — satisfying the "do not silently broaden
  permissions" constraint.
* **A real security scanner's need is genuine:** the entire point of Phase 8 is
  enumerating packages unknown at build time; named or intent-scoped queries cannot
  express "whatever happens to be installed".
* **Release stays clean:** `assembleRelease` produces an APK with no broad visibility;
  the generalized scanner in such a build discovers only the automatic-visibility set
  and must (and does) report its reduced scope honestly rather than claiming a full scan.

## 5. Google Play / production implications (demo vs production)

* `QUERY_ALL_PACKAGES` is a **Play-policy-restricted** permission. Play requires a
  Permissions Declaration Form and restricts it to permitted use cases; *"search"* and
  *"antivirus/device security"* apps are among the historically permitted categories.
  Approval is **not automatic** and Play may reject or demand scope reduction.
* **This demo build makes no Play-eligibility claim.** It is a privately installed
  (adb side-loaded) investor build. Nothing in this phase should be read as "this would
  pass Play review as-is".
* A production/Play distribution would need one of:
  1. a successful `QUERY_ALL_PACKAGES` declaration as a device-security app;
  2. reduced-scope scanning via intent-based `<queries>` with the blind spots disclosed
     in-product; or
  3. distribution outside Play (enterprise/MDM), where the policy does not apply.
* The residual Phase 1–7 permission-optics point stands (accessibility + notification
  listener + device admin + VPN is a heavy profile for review) — unchanged by Phase 8.

## 6. Runtime proof (verified on device, 2026-08-14)

* Shell ground truth: `adb shell pm list packages` → **441** packages (26 third-party).
* App-side, debug build, S20 FE: `AppInventory.inventoryDevice()` discovered **441
  packages** in the dashboard scan (**26 user / 415 system** — exactly matching the
  shell view; 442 during the instrumented run, which adds the test APK), including
  hundreds of packages never named in any Thraksha source
  (`DeviceScanInstrumentedTest.debugBuild_holdsFullVisibility_andDiscoversTheRegistry`
  asserts > 50 packages, > 40 unknown-at-compile-time, and
  `visibilityScope() == FULL` — PASSED on the S20 FE).
* Release variant check: the merged release manifest
  (`app/build/intermediates/merged_manifests/release/.../AndroidManifest.xml`) contains
  **no** `QUERY_ALL_PACKAGES` `<uses-permission>` (the only textual occurrence is the
  explanatory comment); the debug APK's `aapt dump permissions` shows the permission
  present. The broadening is therefore provably debug-scoped.
* The scanner records its **visibility scope** (`FULL` when `QUERY_ALL_PACKAGES` is
  present and effective, `REDUCED` otherwise) in every scan result, and the UI renders
  a REDUCED VISIBILITY warning, so a release build can never silently present the
  automatic-visibility set as a full device scan.

## 7. Privacy guardrails that accompany the broadening

Broad visibility is the *input*; these constraints bound what is done with it:

* All scanning is **local**. No package name, hash, fingerprint, permission list or
  finding ever leaves the device (no runtime network path exists in the scanner).
* No APK contents are copied or persisted; only hashes/fingerprints and structured
  evidence are stored, inside the SQLCipher-encrypted store.
* No attempt is made to read any other app's private data — inputs are
  `PackageManager` metadata and the world-readable APK files Android exposes to every
  app for exactly this purpose.
* Audit rows record scan *summaries* and *findings*, not the full inventory of the
  user's installed apps.
