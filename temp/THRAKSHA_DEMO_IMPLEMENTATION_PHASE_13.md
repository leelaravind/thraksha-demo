

# THRAKSHA — PHASE 13 IMPLEMENTATION GUIDE

## MULTI-DEVICE PRIVATE BETA VALIDATION

## 1. PURPOSE

Phase 13 validates RC2 across real Android devices and normal daily usage.

This phase is about **evidence from real-world use**, not adding features.

Primary goals:

* prove RC2 installs and runs on multiple Android devices;
* compare scanner visibility and findings across devices;
* measure AI performance on newer hardware;
* verify automation behavior across OEM differences;
* measure battery, memory and thermal behavior;
* identify false positives and device-specific limitations;
* validate update/signing continuity;
* gather evidence for a stronger private beta / investor story.

No new major feature work unless a genuine device-compatibility defect blocks validation.

---

## 2. BASELINE

Use the exact RC2 private-alpha APK as the starting artifact.

Record:

* APK SHA-256;
* signer fingerprint;
* version;
* model used;
* runtime version.

Do not silently rebuild RC2 during testing.

If a code fix is required, create RC3 rather than replacing RC2 evidence.

---

## 3. TARGET DEVICES

Use at minimum:

### Device A

Samsung S20 FE

Known baseline/reference device.

### Device B

Samsung S24 Ultra

Primary newer-performance target.

If another Android phone is available later, add it as Device C.

For each device record:

* manufacturer/model;
* Android version/API;
* RAM;
* SoC;
* ABI;
* free storage;
* GPU/NPU information;
* security patch level;
* relevant OEM behavior.

Create:

`temp/phase13/DEVICE_MATRIX.md`

---

## 4. INSTALLATION

On every device:

1. verify RC2 APK SHA;
2. verify signer;
3. install normally;
4. keep Advice Mode / least privilege;
5. do NOT provision Device Owner;
6. provision approved model;
7. verify model SHA;
8. complete real UI onboarding.

Document any device-specific setup differences.

---

## 5. PACKAGE VISIBILITY

On every device compare:

* device ground-truth package count;
* Thraksha discovered count;
* system apps;
* third-party apps;
* owner-installed apps.

Verify scanner can see relevant third-party apps.

Document:

`PHASE13_PACKAGE_VISIBILITY.md`

If visibility differs materially by OEM/API level, investigate before proceeding.

---

## 6. REAL-WORLD SCAN

Run full scan on each device.

Record:

* packages discovered;
* packages fingerprinted;
* scan duration;
* findings;
* known-threat matches;
* REVIEW findings;
* evidence stages;
* unavailable observations;
* false-positive candidates.

Do NOT modify rules solely to make a device “green.”

Create:

`PHASE13_REAL_SCAN_COMPARISON.md`

---

## 7. FALSE-POSITIVE REVIEW

Select legitimate apps flagged REVIEW/HIGH exposure.

For each:

* rule fired;
* declared state;
* granted state;
* observed evidence;
* context;
* whether result is understandable;
* whether severity appears appropriate.

Do not create package-specific allowlists unless separately justified.

Record:

`PHASE13_FALSE_POSITIVE_LOG.md`

---

## 8. AI PERFORMANCE

Use the exact approved on-device model first.

On each device measure:

* model SHA verification;
* cold load;
* warm load;
* memory;
* median inference latency;
* p95 latency;
* repeated inference stability;
* unload/reload;
* thermal behavior.

For S24 Ultra additionally test supported LiteRT-LM backends.

Do not assume GPU/NPU acceleration.

Benchmark:

* CPU;
* GPU/NPU only if genuinely supported.

Create:

`PHASE13_AI_PERFORMANCE.md`

---

## 9. INTENT QUALITY

Run a smaller real-world prompt suite, around 25–40 prompts.

Focus on:

* Meeting;
* Focus;
* Driving;
* explicit durations;
* ambiguous phrases;
* app targets;
* unsupported requests;
* hostile/security bypass requests.

Track:

* routine accuracy;
* parameter accuracy;
* unsafe acceptance;
* clarification behavior;
* latency.

Do not repeat the full Phase 10 model-search exercise unless a regression appears.

---

## 10. AUTOMATION OEM VALIDATION

Run Meeting, Focus and Driving on each device.

Verify:

* DND;
* ringer;
* brightness;
* timeout where applicable;
* app launch;
* preview;
* explicit START;
* ACTIVE;
* STOP & RESTORE;
* exact previous state restoration.

Record OEM-specific differences.

Create:

`PHASE13_AUTOMATION_DEVICE_MATRIX.md`

---

## 11. REBOOT / PROCESS RECOVERY

On each device verify:

* reboot;
* app relaunch;
* audit persistence;
* model availability;
* scanner;
* no unexpected auto-start;
* no leftover automation state.

Test process death during an active routine where safe.

Verify recovery/restore.

---

## 12. NETWORK GUARD

Test Network Guard on each supported device.

Verify:

* activation;
* scoped routing;
* real observed event;
* block behavior;
* disable/restore;
* no unexpected network breakage.

Document OEM limitations.

Do not widen VPN scope merely for testing.

---

## 13. BATTERY / MEMORY / THERMAL

Measure practical behavior under:

* idle app;
* scan;
* model loaded;
* repeated AI;
* Network Guard;
* active routine.

Record:

* memory;
* battery trend;
* thermal status;
* crashes;
* ANRs;
* background-service behavior.

Create:

`PHASE13_RESOURCE_PROFILE.md`

---

## 14. NORMAL-USE PILOT

Use Thraksha normally over several days.

Suggested minimum:

* 3–7 days on the owner’s real phone;
* regular scans;
* AI requests;
* Meeting/Focus/Driving;
* Network Guard where useful;
* reboots;
* permission changes;
* app updates.

Track issues in:

`PHASE13_PILOT_LOG.md`

Each issue:

* device;
* timestamp;
* symptom;
* severity;
* reproduction;
* recovery;
* whether product or environment issue.

---

## 15. UPDATE CONTINUITY

Prove the new private-alpha signing identity works for updates.

Build a no-op or minimal versionCode-incremented test APK only if necessary.

Verify Android accepts update because signer matches.

Do not rotate the signing key.

Document:

`PHASE13_SIGNING_CONTINUITY.md`

---

## 16. INSTALL / UNINSTALL SAFETY

On at least one secondary device verify:

* clean install;
* upgrade;
* uninstall;
* app-local data behavior;
* model removal behavior;
* no persistent automation setting;
* no Device Admin trap if Advice Mode is used.

---

## 17. USER EXPERIENCE

Evaluate manually:

* Protect clarity;
* scan duration;
* finding wording;
* Ask Thraksha wait state;
* plan preview;
* active routine;
* restore;
* Audit;
* Settings;
* error states.

Record confusing wording or unnecessary technical friction.

Do not redesign the product during validation unless an issue is severe.

---

## 18. RC2 MUST REMAIN FROZEN

If no functional defect exists:

RC2 remains unchanged.

If a real defect requires source changes:

STOP RC2 validation for the affected scenario.

Create a documented RC3 fix proposal.

Do not silently patch and continue calling it RC2.

---

## 19. REQUIRED STRUCTURE

Use:

```text
temp/
├── THRAKSHA_DEMO_IMPLEMENTATION_PHASE_13.md
├── THRAKSHA_DEMO_PHASE_13_PROGRESS.md
└── phase13/
    ├── DEVICE_MATRIX.md
    ├── PHASE13_PACKAGE_VISIBILITY.md
    ├── PHASE13_REAL_SCAN_COMPARISON.md
    ├── PHASE13_FALSE_POSITIVE_LOG.md
    ├── PHASE13_AI_PERFORMANCE.md
    ├── PHASE13_AUTOMATION_DEVICE_MATRIX.md
    ├── PHASE13_RESOURCE_PROFILE.md
    ├── PHASE13_PILOT_LOG.md
    ├── PHASE13_SIGNING_CONTINUITY.md
    └── screenshots/
```

---

## 20. PASS CRITERIA

Phase 13 passes when:

* RC2 installs normally on multiple real devices;
* signer continuity is verified;
* relevant third-party apps are visible;
* scanner remains non-destructive;
* false positives are documented/calibrated;
* AI remains bounded/offline;
* automation restores correctly across devices;
* no serious crash/ANR;
* reboot/process recovery works;
* Network Guard behaves as documented;
* resource usage is acceptable;
* real-world pilot produces no blocker;
* known device-specific limitations are documented.

---

## 21. FINAL VERDICT

Final report must state one:

**PASS — PRIVATE BETA VALIDATED**

or

**FAIL — PRIVATE BETA NOT READY**

This is still not commercial-production certification.

---

For your real phone: install **RC2**, verify its SHA first, keep it in **Advice Mode**, do not make it Device Owner, and start with a normal scan before enabling anything else. The signing key you created must also be kept safely backed up because Android update continuity depends on keeping the same signing identity. ([Android Developers][2])

[1]: https://support.google.com/googleplay/android-developer/answer/10158779?hl=en&utm_source=chatgpt.com "Use of the broad package (App) visibility ..."
[2]: https://developer.android.com/studio/publish/app-signing?utm_source=chatgpt.com "Sign your app | Android Studio"
