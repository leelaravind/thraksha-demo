# PHASE 8.1 — GALAXY WEARABLE VALIDATION (primary calibration case)

**Date:** 2026-08-14
**Device:** Samsung SM-G781B (S20 FE), Android 13
**Target:** `com.samsung.android.app.watchmanager` (Galaxy Wearable), preinstalled system app
**Why this app:** Phase 8 flagged it HIGH-RISK PROFILE from purely static evidence
(QUERY_ALL_PACKAGES + SYSTEM_ALERT_WINDOW + 6 sensitive permission areas → the generic
capability-combination rule at HIGH). Phase 8.1's question: was that risk level overstated,
and what does each evidence stage actually show?

---

## 1. Ground truth (external, via adb shell — privileges Thraksha does not have)

Collected 2026-08-14 before any Phase 8.1 scan:

* `dumpsys package` → **28 runtime permissions, every one `granted=false`.** The app
  declares ~125 permission strings; Android currently grants it none of the
  user-consented ones.
* `appops get` (shell holds `GET_APP_OPS_STATS`) → all relevant ops at uid mode
  `ignore`, and **no `time=` entries at all** — the OS's own op history records **zero
  recorded use** of contacts/location/camera/microphone/media by this app.
* `dumpsys bluetooth_manager` → `STATE_DISCONNECTED`, **no bonded devices**: no watch
  is (or ever was) paired on this phone. The Wearable process was not running.
* `appops get … SYSTEM_ALERT_WINDOW` → mode `default`; the permission grant flag decides
  (preinstalled apps holding SYSTEM_ALERT_WINDOW with mode `default` may draw overlays —
  the standard Android semantics Thraksha's provider mirrors).

**Interpretation:** on this phone the Phase 8 HIGH-RISK flag rested **entirely on
Stage 1 (DECLARED)** evidence. Stage 2 is almost empty (only install-time items such as
QUERY_ALL_PACKAGES and the overlay permission flag), and the OS itself has no record of
any sensitive-capability use. This is precisely the false-positive shape §10 told us to
calibrate against.

## 2. What Phase 8.1 changes for this app — general, not app-specific

No package name was added anywhere (checked by grep and by the §26 unit test
`identicalEvidence_identicalFindings_whateverThePackageName`). The general calibration:

* the generic capability-combination rule now grades on **evidence stage**: HIGH only
  when ≥4 sensitive areas are *currently granted*; declared-but-ungranted breadth is
  MEDIUM "broad declared capability exposure" (`GenericRiskRulesTest.
  declaredOnlyCombination_firesAtMediumExposure` / `grantedCombination_firesHigh`);
* the display status derives from the evidence stages: findings resting solely on
  declarations render **WATCHING**; granted/enabled exposure renders **REVIEW**;
  ADVISED requires suspicious context (verified behavior or a critical baseline
  mismatch); KNOWN THREAT MATCH stays intelligence-only;
* every capability row shows DECLARED / GRANTED / OBSERVED explicitly, with
  "NOT OBSERVABLE" for capabilities Android exposes no usage signal for.

Any app with Wearable-shaped evidence — whatever it is called — moves the same way, and
an app with the same declarations **plus live grants** stays HIGH/REVIEW-elevated.

## 3. State A — watch disconnected (executed)

Procedure: dashboard → SCAN THIS DEVICE → open the Galaxy Wearable card → expand
evidence. Results (also asserted by `Phase81EvidenceInstrumentedTest` on this device,
10/10 green):

* **Declared:** contacts, location, camera(–)/microphone(–) *(as declared in manifest)*,
  media, SMS, phone, overlay, broad package visibility — the full static footprint is
  still shown, nothing is suppressed.
* **Granted:** runtime permissions all read back DENIED from
  `requestedPermissionsFlags`; overlay resolves via the op query + grant flag;
  QUERY_ALL_PACKAGES (install-time) reads GRANTED.
* **Observed:** **no verified observation for any capability.** Rows state
  "NOT OBSERVABLE" (no usage API at Thraksha's privilege) or "no verified observation".
  Nothing is inferred from stages 1–2 (`deviceScan_carriesEvidence_andNeverInventsStage3`).
* **Context:** UNKNOWN — powerful capability set, no baseline for the app type, no
  observed behavior; explicitly *not* auto-suspicion.
* **Classification / status:** generic findings still exist (honest capability
  exposure) but the combination rule now fires MEDIUM →
  classification REVIEW, display **REVIEW** (or WATCHING when no granted/enabled
  exposure beyond declarations remains) — **no longer HIGH-RISK PROFILE**, and never
  ADVISED/KNOWN-THREAT (instrumented-asserted).
* **Threat intelligence:** no match (pack v3, 3,412 active static indicators).

Screenshots: see `temp/screenshots/phase8_1_wearable_card.png`,
`phase8_1_wearable_evidence.png` (recorded during the acceptance run).

## 4. States B (watch connected, idle) and C (legitimate feature use) — NOT PRACTICAL

**Honest limitation, per guide §28 "if practical" / §41 stop conditions:** no Galaxy
Watch is paired with this phone (Bluetooth reports no bonded devices; every runtime
permission is ungranted — the Wearable setup flow, which normally requests them, has
never completed). States B and C cannot be genuinely produced without the physical
watch, and simulating them (e.g. hand-granting permissions via adb to fake a paired
state) would manufacture evidence — exactly what Phase 8.1 forbids.

What the design guarantees for B/C, verified by the equivalent general tests instead:

* pairing would move specific permissions to GRANTED → the evidence rows read the OS
  grant flags live, so GRANTED ✓ appears exactly for what Android reports
  (`requestedVersusGranted_isARealDistinction_onThisDevice` proves the read is live);
* feature use (e.g. a location-backed watch feature) would still show
  **OBSERVED — NOT OBSERVABLE** for location/contacts/etc., because Android exposes no
  per-app usage API at Thraksha's privilege — the honest display does not change, and
  that limitation is the documented, intended behavior
  (temp/PHASE8_1_RUNTIME_OBSERVABILITY.md §2);
* granted breadth ≥4 sensitive areas would re-raise the combination rule to HIGH —
  proven off-device by `grantedCombination_firesHigh` with synthetic granted evidence.

## 5. Distinctions this document is required to make (guide §28)

| Kind | Galaxy Wearable, this phone |
|---|---|
| Static exposure (DECLARED) | Very broad — ~125 declared permissions, overlay, QUERY_ALL_PACKAGES; honestly shown as exposure |
| Granted access (GRANTED) | Essentially none — every runtime permission DENIED; install-time visibility + overlay flag only |
| Observed behavior (OBSERVED) | None — no verified observation; most capabilities NOT OBSERVABLE at Thraksha's privilege, and the OS's own (privileged) op history also records zero use |
| Inferred/unknown | Everything else. Context = UNKNOWN, stated as unknown; no intent invented |

**Conclusion:** the Phase 8 HIGH-RISK flag was overstated for this device's state. Phase
8.1 presents the same underlying facts as capability exposure under WATCH/REVIEW
framing, keeps the full evidence visible, and would legitimately re-escalate on real
granted breadth or (if it ever becomes observable) verified behavior — with zero
app-specific exemptions.
